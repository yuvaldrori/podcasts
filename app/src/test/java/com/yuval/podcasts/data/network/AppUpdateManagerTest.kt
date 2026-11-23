package com.yuval.podcasts.data.network

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun buildManager(
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        handler: (Interceptor.Chain) -> Response
    ): AppUpdateManager {
        val context = mockk<Context>()
        every { context.cacheDir } returns tempFolder.root
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> handler(chain) })
            .build()
        return AppUpdateManager(
            context = context,
            okHttpClient = client,
            ioDispatcher = dispatcher
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

    private fun sampleRelease(
        apkUrl: String = "https://github.com/yuvaldrori/podcasts/releases/download/latest/podcasts-abc1234.apk",
        apkFileName: String = "podcasts-abc1234.apk",
        apkSizeBytes: Long = 100L
    ) = ReleaseInfo(
        versionCode = 215,
        commitHash = "abc1234",
        buildTime = "2026-11-22 23:37 UTC",
        commitMessage = "test",
        apkDownloadUrl = apkUrl,
        apkFileName = apkFileName,
        apkSizeBytes = apkSizeBytes
    )

    @Test
    fun checkForUpdate_newerVersionCode_returnsUpdateAvailable() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val manager = buildManager(dispatcher) { chain ->
            jsonResponse(chain.request(), 200, sampleReleaseJson(versionCode = 215))
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
    fun checkForUpdate_branchTargetCommitish_fallsBackToBodyCommitHash() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val json = """
            {
              "tag_name": "latest",
              "target_commitish": "main",
              "published_at": "2026-11-22T23:37:00Z",
              "body": "- **Commit:** `deadbee` (deadbeef123456)\n- **Version Code:** 215",
              "assets": [
                {
                  "name": "podcasts-deadbee.apk",
                  "size": 1000,
                  "browser_download_url": "https://github.com/yuvaldrori/podcasts/releases/download/latest/podcasts-deadbee.apk"
                }
              ]
            }
        """.trimIndent()
        val manager = buildManager(dispatcher) { chain -> jsonResponse(chain.request(), 200, json) }

        val result = manager.checkForUpdate(currentVersionCode = 214)

        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        val release = (result as UpdateCheckResult.UpdateAvailable).release
        assertEquals("deadbee", release.commitHash)
        assertEquals("2026-11-22T23:37:00Z", release.buildTime)
    }

    @Test
    fun checkForUpdate_equalOrOlderVersionCode_returnsUpToDate_andClearsCachedUpdates() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val updatesDir = File(tempFolder.root, "updates").apply { mkdirs() }
        val staleApk = File(updatesDir, "podcasts-old.apk").apply { writeText("old-apk") }
        val jsonWithoutAssets = """
            {
              "tag_name": "latest",
              "target_commitish": "abc1234",
              "published_at": "2026-11-22T23:37:00Z",
              "body": "- **Version Code:** 214",
              "assets": []
            }
        """.trimIndent()
        val manager = buildManager(dispatcher) { chain ->
            jsonResponse(chain.request(), 200, jsonWithoutAssets)
        }

        assertEquals(UpdateCheckResult.UpToDate, manager.checkForUpdate(currentVersionCode = 214))
        assertFalse("Cached APK should be cleared once up to date", staleApk.exists())
        assertEquals(UpdateCheckResult.UpToDate, manager.checkForUpdate(currentVersionCode = 220))
    }

    @Test
    fun checkForUpdate_nullBody_returnsError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val json = """
            {
              "tag_name": "latest",
              "target_commitish": "abc1234",
              "published_at": null,
              "body": null,
              "assets": []
            }
        """.trimIndent()
        val manager = buildManager(dispatcher) { chain -> jsonResponse(chain.request(), 200, json) }

        val result = manager.checkForUpdate(currentVersionCode = 214)
        assertTrue(result is UpdateCheckResult.Error)
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
        val manager = buildManager(dispatcher) { chain -> jsonResponse(chain.request(), 200, json) }

        val result = manager.checkForUpdate(currentVersionCode = 214)
        assertTrue(result is UpdateCheckResult.Error)
    }

    @Test
    fun checkForUpdate_untrustedApkHost_returnsError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val manager = buildManager(dispatcher) { chain ->
            jsonResponse(
                chain.request(),
                200,
                sampleReleaseJson(versionCode = 215, apkUrl = "https://evil.example.com/podcasts.apk")
            )
        }

        val result = manager.checkForUpdate(currentVersionCode = 214)
        assertTrue(result is UpdateCheckResult.Error)
    }

    @Test
    fun checkForUpdate_rawGithubusercontentHost_returnsError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val manager = buildManager(dispatcher) { chain ->
            jsonResponse(
                chain.request(),
                200,
                sampleReleaseJson(
                    versionCode = 215,
                    apkUrl = "https://raw.githubusercontent.com/attacker/repo/main/fake.apk"
                )
            )
        }

        val result = manager.checkForUpdate(currentVersionCode = 214)
        assertTrue(result is UpdateCheckResult.Error)
    }

    @Test
    fun checkForUpdate_additionalMarkdownSectionAfterCommit_doesNotLeakIntoCommitMessage() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val json = """
            {
              "tag_name": "latest",
              "target_commitish": "abc1234567890",
              "published_at": "2026-11-22T23:37:00Z",
              "body": "- **Version Code:** 215\n\n### Latest Commit\nfeat: tighten release flow\n\n### Checksums\nsha256: deadbeef",
              "assets": [
                {
                  "name": "podcasts-abc1234.apk",
                  "size": 1000,
                  "browser_download_url": "https://github.com/yuvaldrori/podcasts/releases/download/latest/podcasts-abc1234.apk"
                }
              ]
            }
        """.trimIndent()
        val manager = buildManager(dispatcher) { chain -> jsonResponse(chain.request(), 200, json) }

        val result = manager.checkForUpdate(currentVersionCode = 214)
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        assertEquals("feat: tighten release flow", (result as UpdateCheckResult.UpdateAvailable).release.commitMessage)
    }

    @Test
    fun checkForUpdate_httpError_returnsError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val manager = buildManager(dispatcher) { chain -> jsonResponse(chain.request(), 500, "{}") }

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
        val updatesDir = File(tempFolder.root, "updates").apply { mkdirs() }
        val staleApk = File(updatesDir, "podcasts-old.apk").apply { writeText("stale") }

        val manager = buildManager(dispatcher) { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Content-Length", payload.size.toString())
                .body(payload.toResponseBody("application/vnd.android.package-archive".toMediaType()))
                .build()
        }

        val release = sampleRelease(
            apkFileName = "../podcasts-abc1234.apk",
            apkSizeBytes = payload.size.toLong()
        )

        val progressValues = mutableListOf<Float?>()
        val downloaded = manager.downloadApk(release) { progressValues.add(it) }

        assertFalse("Stale APK should be removed after successful move", staleApk.exists())
        assertEquals(updatesDir.canonicalPath, downloaded.parentFile?.canonicalPath)
        assertEquals("podcasts-abc1234.apk", downloaded.name)
        assertTrue(downloaded.readBytes().contentEquals(payload))
        assertTrue(progressValues.isNotEmpty())
        assertEquals(1f, progressValues.last()!!, 0.001f)
    }

    @Test
    fun downloadApk_dotfileTraversalName_sanitizesSafely() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val payload = "apk-bytes".toByteArray()
        val manager = buildManager(dispatcher) { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Content-Length", payload.size.toString())
                .body(payload.toResponseBody("application/vnd.android.package-archive".toMediaType()))
                .build()
        }

        val downloaded = manager.downloadApk(
            sampleRelease(apkFileName = "..apk", apkSizeBytes = payload.size.toLong())
        ) {}

        assertEquals("podcasts-update.apk", downloaded.name)
    }

    @Test
    fun downloadApk_untrustedHost_throwsIOException() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val manager = buildManager(dispatcher) {
            throw AssertionError("Network should not be called for untrusted host")
        }

        var failed = false
        try {
            manager.downloadApk(sampleRelease(apkUrl = "https://evil.example.com/podcasts.apk")) {}
        } catch (e: IOException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun downloadApk_midStreamFailureCleansUpTempFile_andPreservesExistingApk() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val updatesDir = File(tempFolder.root, "updates").apply { mkdirs() }
        val existingApk = File(updatesDir, "podcasts-prev.apk").apply { writeText("valid-prev") }
        val tempFileObserved = AtomicBoolean(false)

        val failingBody = object : ResponseBody() {
            override fun contentType(): MediaType = "application/vnd.android.package-archive".toMediaType()
            override fun contentLength(): Long = 1000L
            override fun source(): BufferedSource {
                val failingSource = object : Source {
                    private var chunksSent = 0
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        if (chunksSent == 0) {
                            chunksSent++
                            sink.write(ByteArray(256) { 7 })
                            return 256L
                        }
                        tempFileObserved.set(File(updatesDir, "podcasts-abc1234.apk.tmp").exists())
                        throw IOException("Simulated socket reset mid-stream")
                    }
                    override fun timeout(): Timeout = Timeout.NONE
                    override fun close() {}
                }
                return failingSource.buffer()
            }
        }

        val manager = buildManager(dispatcher) { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(failingBody)
                .build()
        }

        var failed = false
        try {
            manager.downloadApk(sampleRelease(apkSizeBytes = 1000L)) {}
        } catch (e: IOException) {
            failed = true
        }

        assertTrue("Expected IOException on mid-stream failure", failed)
        assertTrue("Temp file should have been created during streaming", tempFileObserved.get())
        assertFalse("Temp file must be deleted on failure", File(updatesDir, "podcasts-abc1234.apk.tmp").exists())
        assertTrue("Existing valid APK must be preserved on failure", existingApk.exists())
    }

    @Test
    fun downloadApk_truncatedStreamThrowsAndCleansUpTempFile() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val partialPayload = ByteArray(100) { 1 }
        val truncatedBody = object : ResponseBody() {
            override fun contentType(): MediaType = "application/vnd.android.package-archive".toMediaType()
            override fun contentLength(): Long = 1000L
            override fun source(): BufferedSource = Buffer().write(partialPayload)
        }

        val manager = buildManager(dispatcher) { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(truncatedBody)
                .build()
        }

        var failed = false
        try {
            manager.downloadApk(sampleRelease(apkSizeBytes = 1000L)) {}
        } catch (e: IOException) {
            failed = true
        }

        assertTrue("Truncated stream should throw IOException", failed)
        val updatesDir = File(tempFolder.root, "updates")
        assertTrue(updatesDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun downloadApk_midStreamCancellationAbortsCallAndCleansUpTempFile() = runTest {
        val updatesDir = File(tempFolder.root, "updates")
        val firstChunkWritten = CountDownLatch(1)
        val callCanceledObserved = AtomicBoolean(false)

        val manager = buildManager(Dispatchers.IO) { chain ->
            val call = chain.call()
            val blockingBody = object : ResponseBody() {
                override fun contentType(): MediaType = "application/vnd.android.package-archive".toMediaType()
                override fun contentLength(): Long = 10_000L
                override fun source(): BufferedSource {
                    val blockingSource = object : Source {
                        private var sentFirst = false
                        override fun read(sink: Buffer, byteCount: Long): Long {
                            if (!sentFirst) {
                                sentFirst = true
                                sink.write(ByteArray(128) { 9 })
                                return 128L
                            }
                            firstChunkWritten.countDown()
                            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                            while (System.nanoTime() < deadline) {
                                if (call.isCanceled()) {
                                    callCanceledObserved.set(true)
                                    throw IOException("Canceled")
                                }
                                Thread.sleep(5)
                            }
                            throw IOException("Timed out waiting for cancellation")
                        }
                        override fun timeout(): Timeout = Timeout.NONE
                        override fun close() {}
                    }
                    return blockingSource.buffer()
                }
            }

            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(blockingBody)
                .build()
        }

        val downloadDeferred = async(Dispatchers.IO) {
            manager.downloadApk(sampleRelease(apkSizeBytes = 10_000L)) {}
        }

        assertTrue("Download should begin streaming", firstChunkWritten.await(5, TimeUnit.SECONDS))
        downloadDeferred.cancel()

        var gotCancellation = false
        try {
            downloadDeferred.await()
        } catch (e: CancellationException) {
            gotCancellation = true
        }

        assertTrue("Expected CancellationException", gotCancellation)
        assertTrue("OkHttp call should be canceled when coroutine cancels mid-stream", callCanceledObserved.get())
        assertTrue("Temp file should be deleted after cancellation", updatesDir.listFiles().orEmpty().isEmpty())
    }
}
