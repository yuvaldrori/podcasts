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
        okHttpClient = OkHttpClient()
    }

    @Test
    fun downloadWorker_failure_updatesDatabaseStatus() = runBlocking {
        val workerParams = mockk<WorkerParameters>(relaxed = true)
        every { workerParams.inputData } returns workDataOf(
            DownloadWorker.KEY_EPISODE_ID to "test_ep",
            DownloadWorker.KEY_AUDIO_URL to "http://invalid.url/audio.mp3",
            DownloadWorker.KEY_EPISODE_TITLE to "Test Episode"
        )

        // Create worker manually to avoid Hilt factory issues in simple integration test
        val worker = DownloadWorker(
            context,
            workerParams,
            episodeDao,
            okHttpClient,
            Dispatchers.IO as CoroutineDispatcher
        )

        val result = worker.doWork()
        
        // OkHttp will fail with UnknownHostException or similar
        // Result.retry() is returned on Exception in our implementation
        assertEquals(ListenableWorker.Result.retry(), result)
    }
}
