package com.yuval.podcasts.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.QueueDao
import com.yuval.podcasts.data.db.entity.DownloadStatus
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
    fun doWork_deletesOrphanedFilesAndKeepsQueuedDownloadedAndActivePartFiles() = runBlocking {
        val queuedEpisodeId = "queued_ep_1"
        val downloadedEpisodeId = "downloaded_only_ep_2"
        val downloadingEpisodeId = "downloading_ep_3"

        val downloadsDir = StorageUtils.getDownloadsDir(context)
        downloadsDir.mkdirs()

        val queuedFile = File(downloadsDir, StorageUtils.getFileName(queuedEpisodeId)).apply { createNewFile() }
        val downloadedFile = File(downloadsDir, StorageUtils.getFileName(downloadedEpisodeId)).apply { createNewFile() }
        val activePartFile = File(downloadsDir, "${StorageUtils.getFileName(downloadingEpisodeId)}.part").apply { createNewFile() }
        val orphanFile = File(downloadsDir, "orphan_ep.mp3").apply { createNewFile() }
        val orphanPartFile = File(downloadsDir, "orphan_ep_2.mp3.part").apply { createNewFile() }

        val queuedEpisode = Episode(
            id = queuedEpisodeId, podcastFeedUrl = "feed", title = "Queued Ep", description = "desc",
            audioUrl = "http://valid.com/1", imageUrl = null, episodeWebLink = null, pubDate = 0L,
            duration = 1000L, downloadStatus = DownloadStatus.DOWNLOADED.value,
            localFilePath = queuedFile.absolutePath, isPlayed = false, lastPlayedPosition = 0L, completedAt = null
        )
        val downloadedEpisode = Episode(
            id = downloadedEpisodeId, podcastFeedUrl = "feed", title = "Downloaded Ep", description = "desc",
            audioUrl = "http://valid.com/2", imageUrl = null, episodeWebLink = null, pubDate = 0L,
            duration = 1000L, downloadStatus = DownloadStatus.DOWNLOADED.value,
            localFilePath = downloadedFile.absolutePath, isPlayed = false, lastPlayedPosition = 0L, completedAt = null
        )
        val downloadingEpisode = Episode(
            id = downloadingEpisodeId, podcastFeedUrl = "feed", title = "Downloading Ep", description = "desc",
            audioUrl = "http://valid.com/3", imageUrl = null, episodeWebLink = null, pubDate = 0L,
            duration = 1000L, downloadStatus = DownloadStatus.DOWNLOADING.value,
            localFilePath = null, isPlayed = false, lastPlayedPosition = 0L, completedAt = null
        )

        coEvery { queueDao.getQueueEpisodesSync() } returns listOf(queuedEpisode)
        coEvery { episodeDao.getDownloadedOrDownloadingEpisodes() } returns listOf(downloadedEpisode, downloadingEpisode)

        val worker = TestListenableWorkerBuilder<CleanupWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
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
        assertTrue("Queued episode file should remain on disk", queuedFile.exists())
        assertTrue("Non-queued downloaded episode file should remain on disk", downloadedFile.exists())
        assertTrue("Active downloading .part file should remain on disk", activePartFile.exists())
        assertFalse("Orphan .mp3 file should be deleted", orphanFile.exists())
        assertFalse("Orphan .part file should be deleted", orphanPartFile.exists())
    }
}
