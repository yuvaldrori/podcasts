package com.yuval.podcasts.work

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.work.ForegroundInfo
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.ProgressUpdater
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.common.util.concurrent.Futures
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.entity.DownloadStatus
import com.yuval.podcasts.utils.StorageUtils
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * Exercises the streaming body loop of [DownloadWorker].
 *
 * The progress/foreground updaters are stubbed to return already-completed futures so the
 * worker's internal `setProgress`/`setForeground` await calls resolve immediately on the JVM,
 * and `createForegroundInfo` is stubbed so the test never touches Android resource/notification
 * APIs (which Robolectric can't resolve against this app's API-37 target).
 */
@RunWith(RobolectricTestRunner::class)
class DownloadWorkerStreamTest {

    private lateinit var context: Context
    private lateinit var episodeDao: EpisodeDao
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var workerParams: WorkerParameters
    private lateinit var progressUpdater: ProgressUpdater

    private val episodeId = "test_ep"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        episodeDao = mockk(relaxed = true)
        okHttpClient = mockk(relaxed = true)

        progressUpdater = mockk()
        every { progressUpdater.updateProgress(any(), any(), any()) } returns Futures.immediateFuture(null)

        val foregroundUpdater = mockk<ForegroundUpdater>()
        every { foregroundUpdater.setForegroundAsync(any(), any(), any()) } returns Futures.immediateFuture(null)

        workerParams = mockk(relaxed = true)
        every { workerParams.id } returns UUID.randomUUID()
        every { workerParams.foregroundUpdater } returns foregroundUpdater
        every { workerParams.progressUpdater } returns progressUpdater
        every { workerParams.inputData } returns workDataOf(
            DownloadWorker.KEY_EPISODE_ID to episodeId,
            DownloadWorker.KEY_AUDIO_URL to "https://example.com/audio.mp3",
            DownloadWorker.KEY_EPISODE_TITLE to "Test Episode"
        )

        // Make sure no stale file from a previous run interferes.
        StorageUtils.getFileForEpisode(context, episodeId).delete()
    }

    /** Stubs the HTTP call to deliver a successful response carrying [bodyBytes]. */
    private fun stubSuccessfulResponse(bodyBytes: ByteArray) {
        val call = mockk<Call>(relaxed = true)
        val callbackSlot = slot<Callback>()
        every { okHttpClient.newCall(any()) } returns call
        every { call.enqueue(capture(callbackSlot)) } answers {
            val response = Response.Builder()
                .request(Request.Builder().url("https://example.com/audio.mp3").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(bodyBytes.toResponseBody("audio/mpeg".toMediaTypeOrNull()))
                .build()
            callbackSlot.captured.onResponse(call, response)
        }
    }

    private fun createWorker(): DownloadWorker {
        val worker = spyk(
            DownloadWorker(
                context,
                workerParams,
                episodeDao,
                okHttpClient,
                mockk(relaxed = true), // logManager
                Dispatchers.IO,
                com.yuval.podcasts.data.repository.DownloadProgressTracker()
            )
        )
        // Avoid building a real notification (and the resource lookups it needs).
        val foregroundInfo = ForegroundInfo(
            Constants.NOTIFICATION_ID_DOWNLOAD,
            mockk<Notification>(relaxed = true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        every { worker.createForegroundInfo(any(), any(), any()) } returns foregroundInfo
        return worker
    }

    @Test
    fun downloadWorker_whenStoppedMidStream_revertsDownloadStatusAndDeletesPartFile() = runBlocking {
        stubSuccessfulResponse(ByteArray(1024))

        val worker = createWorker()
        every { worker.isStopped } returns true

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)

        coVerify(exactly = 1) {
            episodeDao.updateDownloadStatus(episodeId, DownloadStatus.DOWNLOADING.value, null)
        }
        coVerify(exactly = 1) {
            episodeDao.updateDownloadStatus(episodeId, DownloadStatus.NOT_DOWNLOADED.value, null)
        }
        val outputFile = StorageUtils.getFileForEpisode(context, episodeId)
        val partFile = java.io.File("${outputFile.absolutePath}.part")
        org.junit.Assert.assertFalse("Final output file must not exist after mid-stream stop", outputFile.exists())
        org.junit.Assert.assertFalse("Partial download file must be cleaned up after mid-stream stop", partFile.exists())
    }

    @Test
    fun downloadWorker_throttlesProgressUpdates_byElapsedTime() = runBlocking {
        val bodySize = Constants.DOWNLOAD_BUFFER_SIZE_BYTES * 100
        stubSuccessfulResponse(ByteArray(bodySize))

        val worker = createWorker()
        every { worker.isStopped } returns false

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        val outputFile = StorageUtils.getFileForEpisode(context, episodeId)
        org.junit.Assert.assertTrue("Downloaded file must exist on success", outputFile.exists())
        assertEquals(bodySize.toLong(), outputFile.length())

        // Must emit at least 1 progress update (proving progress reporting is active),
        // while staying far below the 100 distinct percentage steps (proving time throttling).
        coVerify(atLeast = 1, atMost = 25) {
            progressUpdater.updateProgress(any(), any(), any())
        }
    }

    @Test
    fun downloadWorker_whenCancelledDuringRequest_revertsStatusAndClearsProgress() = runBlocking {
        val tracker = com.yuval.podcasts.data.repository.DownloadProgressTracker()
        tracker.updateProgress(episodeId, 35)

        val requestStarted = java.util.concurrent.CountDownLatch(1)
        val call = mockk<Call>(relaxed = true)
        every { okHttpClient.newCall(any()) } returns call
        every { call.enqueue(any()) } answers {
            requestStarted.countDown()
            // Hold the request open until cancelled
        }

        val worker = spyk(
            DownloadWorker(
                context,
                workerParams,
                episodeDao,
                okHttpClient,
                mockk(relaxed = true),
                Dispatchers.IO,
                tracker
            )
        )
        val foregroundInfo = ForegroundInfo(
            Constants.NOTIFICATION_ID_DOWNLOAD,
            mockk<Notification>(relaxed = true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        every { worker.createForegroundInfo(any(), any(), any()) } returns foregroundInfo

        val job = launch(Dispatchers.IO) {
            worker.doWork()
        }
        org.junit.Assert.assertTrue(requestStarted.await(5, java.util.concurrent.TimeUnit.SECONDS))
        job.cancel()
        job.join()

        coVerify(exactly = 1) {
            episodeDao.updateDownloadStatus(episodeId, DownloadStatus.NOT_DOWNLOADED.value, null)
        }
        assertEquals(emptyMap<String, Int>(), tracker.progressMap.value)
    }
}
