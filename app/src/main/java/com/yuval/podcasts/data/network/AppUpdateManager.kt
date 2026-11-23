package com.yuval.podcasts.data.network

import android.content.Context
import com.yuval.podcasts.BuildConfig
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

data class ReleaseInfo(
    val versionCode: Int,
    val commitHash: String,
    val buildTime: String,
    val commitMessage: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

@Serializable
private data class GitHubReleaseDto(
    @SerialName("target_commitish") val targetCommitish: String = "",
    @SerialName("published_at") val publishedAt: String = "",
    val body: String = "",
    val assets: List<GitHubAssetDto> = emptyList()
)

@Serializable
private data class GitHubAssetDto(
    val name: String = "",
    val size: Long = 0L,
    @SerialName("browser_download_url") val browserDownloadUrl: String = ""
)

@Singleton
class AppUpdateManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    suspend fun checkForUpdate(
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        releaseUrl: String = Constants.GITHUB_LATEST_RELEASE_API_URL
    ): UpdateCheckResult = withContext(ioDispatcher) {
        try {
            val request = Request.Builder()
                .url(releaseUrl)
                .header("Accept", "application/vnd.github+json")
                .build()

            val bodyString = okHttpClient.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateCheckResult.Error("HTTP ${response.code}")
                }
                response.body.string()
            }

            val dto = json.decodeFromString<GitHubReleaseDto>(bodyString)
            val remoteVersionCode = VERSION_CODE_REGEX.find(dto.body)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: return@withContext UpdateCheckResult.Error("Release metadata missing version code")

            if (remoteVersionCode <= currentVersionCode) {
                clearCachedUpdates()
                return@withContext UpdateCheckResult.UpToDate
            }

            val apkAsset = dto.assets.firstOrNull {
                it.name.endsWith(".apk", ignoreCase = true) &&
                    isTrustedApkUrl(it.browserDownloadUrl)
            } ?: return@withContext UpdateCheckResult.Error("Release APK asset not found")

            val commitHash = dto.targetCommitish.trim()
                .takeIf { HEX_SHA_REGEX.matches(it) }
                ?.take(SHORT_SHA_LENGTH)
                ?: COMMIT_HASH_REGEX.find(dto.body)?.groupValues?.getOrNull(1)
                ?: "unknown"

            val buildTime = BUILD_TIME_REGEX.find(dto.body)
                ?.groupValues?.getOrNull(1)?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: dto.publishedAt
            val commitMessage = LATEST_COMMIT_REGEX.find(dto.body)
                ?.groupValues?.getOrNull(1)?.trim()
                ?: ""

            UpdateCheckResult.UpdateAvailable(
                ReleaseInfo(
                    versionCode = remoteVersionCode,
                    commitHash = commitHash,
                    buildTime = buildTime,
                    commitMessage = commitMessage,
                    apkDownloadUrl = apkAsset.browserDownloadUrl,
                    apkFileName = sanitizeApkFileName(apkAsset.name),
                    apkSizeBytes = apkAsset.size
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: e::class.java.simpleName)
        }
    }

    suspend fun downloadApk(
        release: ReleaseInfo,
        onProgress: (Float?) -> Unit
    ): File = withContext(ioDispatcher) {
        if (!isTrustedApkUrl(release.apkDownloadUrl)) {
            throw IOException("Refusing to download APK from untrusted URL")
        }

        val updatesDir = File(context.cacheDir, Constants.UPDATE_CACHE_DIR_NAME).apply { mkdirs() }
        val safeFileName = sanitizeApkFileName(release.apkFileName)
        val tempFile = File(updatesDir, "$safeFileName.tmp")
        val targetFile = File(updatesDir, safeFileName)
        if (targetFile.canonicalFile.parentFile != updatesDir.canonicalFile) {
            throw IOException("Invalid APK target path")
        }

        val request = Request.Builder()
            .url(release.apkDownloadUrl)
            .build()
        val call = okHttpClient.newCall(request)

        try {
            coroutineScope {
                val scopeJob = currentCoroutineContext().job
                val cancelWatcher = launch(Dispatchers.Unconfined) {
                    try {
                        awaitCancellation()
                    } finally {
                        if (scopeJob.isCancelled) {
                            call.cancel()
                        }
                    }
                }
                try {
                    call.await().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException("HTTP ${response.code}")
                        }
                        val body = response.body
                        val headerContentLength = body.contentLength()
                        val totalBytes = headerContentLength.takeIf { it > 0L } ?: release.apkSizeBytes
                        val buffer = ByteArray(Constants.DOWNLOAD_BUFFER_SIZE_BYTES)
                        var downloadedBytes = 0L
                        var lastReportedProgress = -1f

                        if (totalBytes <= 0L) {
                            onProgress(null)
                        }

                        body.byteStream().use { input ->
                            FileOutputStream(tempFile).use { output ->
                                while (true) {
                                    ensureActive()
                                    val bytesRead = input.read(buffer)
                                    if (bytesRead == -1) break
                                    output.write(buffer, 0, bytesRead)
                                    downloadedBytes += bytesRead

                                    if (totalBytes > 0L) {
                                        val progress = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                        if (progress - lastReportedProgress >= PROGRESS_REPORT_STEP || progress >= 1f) {
                                            lastReportedProgress = progress
                                            onProgress(progress)
                                        }
                                    }
                                }
                                output.flush()
                            }
                        }

                        if (downloadedBytes <= 0L) {
                            throw IOException("Downloaded APK is empty")
                        }
                        if (headerContentLength > 0L && downloadedBytes != headerContentLength) {
                            throw IOException("Incomplete APK download ($downloadedBytes / $headerContentLength bytes)")
                        }
                        if (totalBytes > 0L && lastReportedProgress < 1f) {
                            onProgress(1f)
                        }
                    }
                } finally {
                    cancelWatcher.cancel()
                }
            }

            Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            updatesDir.listFiles()?.forEach { file ->
                if (file != targetFile) {
                    file.delete()
                }
            }
            targetFile
        } catch (e: Throwable) {
            tempFile.delete()
            if (e !is CancellationException && !currentCoroutineContext().isActive) {
                throw CancellationException("APK download cancelled", e)
            }
            throw e
        }
    }

    private fun clearCachedUpdates() {
        val updatesDir = File(context.cacheDir, Constants.UPDATE_CACHE_DIR_NAME)
        if (updatesDir.isDirectory) {
            updatesDir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun isTrustedApkUrl(url: String): Boolean {
        val httpUrl = url.toHttpUrlOrNull() ?: return false
        if (httpUrl.scheme != "https") return false
        val host = httpUrl.host.lowercase()
        return (host == "github.com" && httpUrl.encodedPath.startsWith(TRUSTED_RELEASE_PATH_PREFIX)) ||
            host == "objects.githubusercontent.com" ||
            host.endsWith(".githubusercontent.com")
    }

    private fun sanitizeApkFileName(rawName: String): String {
        val baseName = File(rawName).name
        val cleaned = INVALID_FILENAME_CHARS_REGEX.replace(baseName, "").trimStart('.')
        return if (cleaned.endsWith(".apk", ignoreCase = true) && cleaned.length > 4) {
            cleaned
        } else {
            "podcasts-update.apk"
        }
    }

    private companion object {
        const val SHORT_SHA_LENGTH = 7
        const val PROGRESS_REPORT_STEP = 0.01f
        const val TRUSTED_RELEASE_PATH_PREFIX = "/yuvaldrori/podcasts/releases/download/"
        val HEX_SHA_REGEX = Regex("^[0-9a-fA-F]{7,40}$")
        val INVALID_FILENAME_CHARS_REGEX = Regex("[^a-zA-Z0-9._-]")
        val VERSION_CODE_REGEX = Regex("""\*\*Version Code:\*\*\s*(\d+)""")
        val COMMIT_HASH_REGEX = Regex("""\*\*Commit:\*\*\s*`([0-9a-fA-F]+)`""")
        val BUILD_TIME_REGEX = Regex("""\*\*Build Time:\*\*\s*(.+)""")
        val LATEST_COMMIT_REGEX = Regex("""### Latest Commit\s*\n([\s\S]+)""")
    }
}
