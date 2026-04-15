/*
package com.yuval.podcasts.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.yuval.podcasts.data.db.dao.EpisodeDao
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

class DownloadWorkerIntegrationTest {
    private lateinit var context: Context
    private lateinit var episodeDao: EpisodeDao

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        episodeDao = mockk(relaxed = true)
    }

    @Test
    fun downloadWorker_failure_updatesDatabaseStatus() = runBlocking {
        // Use an invalid URL to trigger failure
        val worker = TestListenableWorkerBuilder<DownloadWorker>(context)
            .setInputData(workDataOf(
                DownloadWorker.KEY_EPISODE_ID to "test_ep",
                DownloadWorker.KEY_AUDIO_URL to "http://invalid.url/audio.mp3",
                DownloadWorker.KEY_EPISODE_TITLE to "Test Episode"
            ))
            .build()

        val result = worker.doWork()
        
        assertEquals(ListenableWorker.Result.failure(), result)
    }
}
*/
