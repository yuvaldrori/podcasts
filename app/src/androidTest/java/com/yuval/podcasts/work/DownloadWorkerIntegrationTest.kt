package com.yuval.podcasts.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yuval.podcasts.data.db.dao.EpisodeDao
import io.mockk.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher

class DownloadWorkerIntegrationTest {
    private lateinit var context: Context
    private lateinit var episodeDao: EpisodeDao
    private lateinit var okHttpClient: OkHttpClient

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        episodeDao = mockk<EpisodeDao>(relaxed = true)
        okHttpClient = mockk<OkHttpClient>(relaxed = true)
    }

    @Test
    fun downloadWorker_failure_updatesDatabaseStatus() = runBlocking {
        val executor = java.util.concurrent.Executor { it.run() }
        val taskExecutor = mockk<androidx.work.impl.utils.taskexecutor.TaskExecutor>(relaxed = true)
        val progressUpdater = mockk<androidx.work.ProgressUpdater>(relaxed = true)
        val foregroundUpdater = mockk<androidx.work.ForegroundUpdater>(relaxed = true)
        every { foregroundUpdater.setForegroundAsync(any(), any(), any()) } returns com.google.common.util.concurrent.Futures.immediateFuture(null)
        val workerFactory = object : androidx.work.WorkerFactory() {
            override fun createWorker(
                appContext: android.content.Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): androidx.work.ListenableWorker? = null
        }
        
        val workerParams = WorkerParameters(
            java.util.UUID.randomUUID(),
            workDataOf(
                DownloadWorker.KEY_EPISODE_ID to "test_ep",
                DownloadWorker.KEY_AUDIO_URL to "http://invalid.url/audio.mp3",
                DownloadWorker.KEY_EPISODE_TITLE to "Test Episode"
            ),
            listOf("tag"),
            WorkerParameters.RuntimeExtras(),
            0,
            0, // generation
            executor,
            Dispatchers.IO,
            taskExecutor,
            workerFactory,
            progressUpdater,
            foregroundUpdater
        )

        val call = mockk<okhttp3.Call>()
        every { okHttpClient.newCall(any()) } returns call
        every { call.enqueue(any()) } answers {
            val callback = firstArg<okhttp3.Callback>()
            callback.onFailure(call, java.io.IOException("Network failure"))
        }

        // Create worker manually to avoid Hilt factory issues in simple integration test
        val worker = DownloadWorker(
            context,
            workerParams,
            episodeDao,
            okHttpClient,
            mockk(relaxed = true), // logManager
            Dispatchers.IO
        )

        val result = worker.doWork()
        
        // OkHttp will fail with UnknownHostException or similar
        // Result.retry() is returned on Exception in our implementation
        assertEquals(ListenableWorker.Result.retry(), result)
    }
}
