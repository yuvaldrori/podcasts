package com.yuval.podcasts.data.network

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun buildManager(
        testDispatcher: kotlinx.coroutines.CoroutineDispatcher,
        handler: (okhttp3.Request) -> Response
    ): AppUpdateManager {
        val context = mockk<Context>()
        every { context.cacheDir } returns tempFolder.root
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> handler(chain.request()) })
            .build()
        return AppUpdateManager(
            context = context,
            okHttpClient = client,
            ioDispatcher = testDispatcher
        )
    }

    private fun jsonResponse(request: okhttp3.Request, code: Int, json: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Error")
            .body(json.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun sampleReleaseJson(
        versionCode: Int = 215,
        commitSha: String = "abc1234567890abcdef",
        apkName: String = "podcasts-abc1234.apk",
        apkUrl: String = "https://github.com/yuvaldrori/podcasts/releases/download/latest/podcasts-abc1234.apk",
        apkSize: Long = 7_500_000L
    ): String = """
        {
          "tag_name": "latest",
          "target_commitish": "$commitSha",
          "published_at": "2026-11-22T23:37:00Z",
          "body": "Automated release build from `main`.\n\n- **Commit:** `${commitSha.take(7)}` ($commitSha)\n- **Build Time:** 2026-11-22 23:37 UTC\n- **Version Code:** $versionCode\n\n### Latest Commit\nfeat: add in-app update checker",
          "assets": [
            {
              "name": "$apkName",
              "size": $apkSize,
              "browser_download_url": "$apkUrl"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun checkForUpdate_newerVersionCode_returnsUpdateAvailable() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val manager = buildManager(dispatcher) { req ->
            jsonResponse(req, 200, sampleReleaseJson(versionCode = 215))
        }

        val result = manager.checkForUpdate(currentVersionCode = 214)

        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        val release = (result as UpdateCheckResult.UpdateAvailable).release
        assertEquals(215, release.versionCode)
        assertEquals("abc1234", release.commitHash)
        assertEquals("2026-11-22 23:37 UTC", release.buildTime)
        assertEquals("feat: add in-app update checker", release.commitMessage)
        assertEquals("podcasts-abc1234.apk", release.apkFileName)
        assertEquals(7_500_000L, release.apkSizeBytes)
        assertEquals(
            "https://github.com/yuvaldrori/podcasts/releases/download/latest/podcasts-abc1234.apk",
            release.apkDownloadUrl
        )
    }

    @Test
    fun checkForUpdate_equalOrOlderVersionCode_returnsUpToDate() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val manager = buildManager(dispatcher) { req ->
            jsonResponse(req, 200, sampleReleaseJson(versionCode = 214))
        }

        assertEquals(UpdateCheckResult.UpToDate, manager.checkForUpdate(currentVersionCode = 214))
        assertEquals(UpdateCheckResult.UpToDate, manager.checkForUpdate(currentVersionCode = 220))
    }

    @Test
    fun checkForUpdate_missingApkAsset_returnsError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val json = """
            {
              "tag_name": "latest",
              "target_commitish": "abc1234",
              "published_at": "2026-11-22T23:37:00Z",
              "body": "- **Version Code:** 215",
              "assets": []
            }
        """.trimIndent()
        val manager = buildManager(dispatcher) { req -> jsonResponse(req, 200, json) }

        val result = manager.checkForUpdate(currentVersionCode = 214)
        assertTrue(result is UpdateCheckResult.Error)
    }

    @Test
    fun checkForUpdate_httpError_returnsError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val manager = buildManager(dispatcher) { req -> jsonResponse(req, 500, "{}") }

        val result = manager.checkForUpdate(currentVersionCode = 214)
        assertTrue(result is UpdateCheckResult.Error)
    }

    @Test
    fun checkForUpdate_networkException_returnsError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val manager = buildManager(dispatcher) { throw IOException("No network") }

        val result = manager.checkForUpdate(currentVersionCode = 214)
        assertTrue(result is UpdateCheckResult.Error)
        assertTrue((result as UpdateCheckResult.Error).message.contains("No network"))
    }

    @Test
    fun downloadApk_streamsToUpdatesDir_reportsProgress_andCleansOldApks() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val payload = ByteArray(128 * 1024) { (it % 251).toByte() }
        val updatesDir = java.io.File(tempFolder.root, "updates").apply { mkdirs() }
        val staleApk = java.io.File(updatesDir, "podcasts-old.apk").apply { writeText("stale") }

        val manager = buildManager(dispatcher) { req ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Content-Length", payload.size.toString())
                .body(payload.toResponseBody("application/vnd.android.package-archive".toMediaType()))
                .build()
        }

        val release = ReleaseInfo(
            versionCode = 215,
            commitHash = "abc1234",
            buildTime = "2026-11-22 23:37 UTC",
            commitMessage = "test",
            apkDownloadUrl = "https://github.com/yuvaldrori/podcasts/releases/download/latest/podcasts-abc1234.apk",
            apkFileName = "../podcasts-abc1234.apk",
            apkSizeBytes = payload.size.toLong()
        )

        val progressValues = mutableListOf<Float>()
        val downloaded = manager.downloadApk(release) { progressValues.add(it) }

        assertFalse("Stale APK should be removed", staleApk.exists())
        assertEquals(updatesDir.canonicalPath, downloaded.parentFile?.canonicalPath)
        assertEquals("podcasts-abc1234.apk", downloaded.name)
        assertTrue(downloaded.readBytes().contentEquals(payload))
        assertTrue(progressValues.isNotEmpty())
        assertEquals(1f, progressValues.last(), 0.001f)
    }

    @Test
    fun downloadApk_failureCleansUpTempFile() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val manager = buildManager(dispatcher) { req ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(503)
                .message("Unavailable")
                .body("".toResponseBody())
                .build()
        }

        val release = ReleaseInfo(
            versionCode = 215,
            commitHash = "abc1234",
            buildTime = "2026-11-22 23:37 UTC",
            commitMessage = "test",
            apkDownloadUrl = "https://github.com/yuvaldrori/podcasts/releases/download/latest/podcasts-abc1234.apk",
            apkFileName = "podcasts-abc1234.apk",
            apkSizeBytes = 100L
        )

        var failed = false
        try {
            manager.downloadApk(release) {}
        } catch (e: IOException) {
            failed = true
        } catch (e: CancellationException) {
            throw e
        }

        assertTrue(failed)
        val updatesDir = java.io.File(tempFolder.root, "updates")
        assertTrue(updatesDir.listFiles().orEmpty().isEmpty())
    }
}
