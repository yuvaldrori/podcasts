package com.yuval.podcasts.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.ProgressUpdater
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.taskexecutor.TaskExecutor
import androidx.work.workDataOf
import com.google.common.util.concurrent.Futures
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.entity.DownloadStatus
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executor

@RunWith(AndroidJUnit4::class)
class DownloadWorkerIntegrationTest {
    private lateinit var context: Context
    private lateinit var episodeDao: EpisodeDao
    private lateinit var okHttpClient: OkHttpClient

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        episodeDao = mockk(relaxed = true)
        okHttpClient = mockk(relaxed = true)
    }

    @Test
    fun downloadWorker_failure_updatesDatabaseStatusAndReturnsRetry() = runBlocking {
        val executor = Executor { it.run() }
        val taskExecutor = mockk<TaskExecutor>(relaxed = true)
        val progressUpdater = mockk<ProgressUpdater>(relaxed = true)
        val foregroundUpdater = mockk<ForegroundUpdater>(relaxed = true)
        every { foregroundUpdater.setForegroundAsync(any(), any(), any()) } returns Futures.immediateFuture(null)

        val workerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker? = null
        }

        val episodeId = "test_ep"
        val workerParams = WorkerParameters(
            UUID.randomUUID(),
            workDataOf(
                DownloadWorker.KEY_EPISODE_ID to episodeId,
                DownloadWorker.KEY_AUDIO_URL to "http://invalid.url/audio.mp3",
                DownloadWorker.KEY_EPISODE_TITLE to "Test Episode"
            ),
            listOf("tag"),
            WorkerParameters.RuntimeExtras(),
            0,
            0,
            executor,
            Dispatchers.IO,
            taskExecutor,
            workerFactory,
            progressUpdater,
            foregroundUpdater
        )

        val call = mockk<Call>()
        every { okHttpClient.newCall(any()) } returns call
        every { call.enqueue(any()) } answers {
            val callback = firstArg<Callback>()
            callback.onFailure(call, IOException("Network failure"))
        }

        val worker = DownloadWorker(
            context,
            workerParams,
            episodeDao,
            okHttpClient,
            mockk(relaxed = true),
            Dispatchers.IO,
            com.yuval.podcasts.data.repository.DownloadProgressTracker()
        )

        val result = worker.doWork()

        // Network IOException triggers Result.retry() and reverts DB status from DOWNLOADING to NOT_DOWNLOADED
        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 1) {
            episodeDao.updateDownloadStatus(episodeId, DownloadStatus.DOWNLOADING.value, null)
        }
        coVerify(exactly = 1) {
            episodeDao.updateDownloadStatus(episodeId, DownloadStatus.NOT_DOWNLOADED.value, null)
        }
    }
}
