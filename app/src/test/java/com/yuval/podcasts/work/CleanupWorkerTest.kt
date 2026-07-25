package com.yuval.podcasts.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.QueueDao
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.utils.LogManager
import com.yuval.podcasts.utils.StorageUtils
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CleanupWorkerTest {

    private lateinit var context: Context
    private lateinit var queueDao: QueueDao
    private lateinit var episodeDao: EpisodeDao
    private lateinit var logManager: LogManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        queueDao = mockk(relaxed = true)
        episodeDao = mockk(relaxed = true)
        logManager = mockk(relaxed = true)
    }

    @Test
    fun doWork_deletesOrphanedFilesAndKeepsValidEpisodes() = runBlocking {
        val validEpisodeId = "valid_ep_1"
        val validFileName = StorageUtils.getFileName(validEpisodeId)
        val orphanFileName = "orphan_ep.mp3"

        val downloadsDir = StorageUtils.getDownloadsDir(context)
        downloadsDir.mkdirs()

        val validFile = File(downloadsDir, validFileName)
        validFile.createNewFile()

        val orphanFile = File(downloadsDir, orphanFileName)
        orphanFile.createNewFile()

        val validEpisode = Episode(
            id = validEpisodeId, podcastFeedUrl = "feed", title = "Valid Ep", description = "desc",
            audioUrl = "http://valid.com", imageUrl = null, episodeWebLink = null, pubDate = 0L,
            duration = 1000L, downloadStatus = 2, localFilePath = validFile.absolutePath, isPlayed = false,
            lastPlayedPosition = 0L, completedAt = null
        )

        coEvery { queueDao.getQueueEpisodesSync() } returns listOf(validEpisode)
        coEvery { episodeDao.getDownloadedOrDownloadingEpisodes() } returns emptyList()

        val worker = TestListenableWorkerBuilder<CleanupWorker>(context)
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return CleanupWorker(
                        appContext,
                        workerParameters,
                        queueDao,
                        episodeDao,
                        logManager,
                        Dispatchers.Unconfined
                    )
                }
            })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue("Valid file should remain on disk", validFile.exists())
        assertFalse("Orphan file should be deleted", orphanFile.exists())
    }
}
