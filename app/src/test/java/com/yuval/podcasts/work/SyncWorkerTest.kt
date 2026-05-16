package com.yuval.podcasts.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.utils.LogManager
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {
    private lateinit var context: Context
    private lateinit var repository: PodcastRepository
    private lateinit var logManager: LogManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = mockk(relaxed = true)
        logManager = mockk(relaxed = true)
    }

    @Test
    fun `doWork calls fetchAndStorePodcast for each podcast`() = runBlocking {
        val podcasts = listOf(
            Podcast("url1", "T1", "D1", "I1", "W1"),
            Podcast("url2", "T2", "D2", "I2", "W2")
        )
        every { repository.allPodcasts } returns flowOf(podcasts)
        
        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: androidx.work.WorkerParameters
                ): ListenableWorker? {
                    return SyncWorker(appContext, workerParameters, repository, logManager)
                }
            })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { repository.fetchAndStorePodcast("url1") }
        coVerify(exactly = 1) { repository.fetchAndStorePodcast("url2") }
        coVerify(exactly = 1) { repository.requeueMissingDownloads() }
    }

    @Test
    fun `doWork handles individual podcast failures and continues`() = runBlocking {
        val podcasts = listOf(
            Podcast("url1", "T1", "D1", "I1", "W1"),
            Podcast("url2", "T2", "D2", "I2", "W2")
        )
        every { repository.allPodcasts } returns flowOf(podcasts)
        coEvery { repository.fetchAndStorePodcast("url1") } throws Exception("Network error")
        
        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: androidx.work.WorkerParameters
                ): ListenableWorker? {
                    return SyncWorker(appContext, workerParameters, repository, logManager)
                }
            })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { repository.fetchAndStorePodcast("url1") }
        coVerify(exactly = 1) { repository.fetchAndStorePodcast("url2") }
        verify { logManager.e("SyncWorker", any(), any()) }
    }
}
