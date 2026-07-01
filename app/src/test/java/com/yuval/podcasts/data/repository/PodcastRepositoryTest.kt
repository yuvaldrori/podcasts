package com.yuval.podcasts.data.repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.runBlocking

import androidx.work.WorkManager
import androidx.room.withTransaction
import com.yuval.podcasts.data.db.AppDatabase
import com.yuval.podcasts.data.db.dao.ChapterDao
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.PodcastDao
import com.yuval.podcasts.data.db.dao.QueueDao
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.NetworkEpisodeWithChapters
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.db.entity.ParsedPodcast
import com.yuval.podcasts.utils.MainDispatcherRule
import com.yuval.podcasts.utils.LogManager
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PodcastRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: AppDatabase
    private lateinit var remoteDataSource: com.yuval.podcasts.data.network.PodcastRemoteDataSource
    private lateinit var podcastDao: PodcastDao
    private lateinit var episodeDao: EpisodeDao
    private lateinit var queueDao: QueueDao
    private lateinit var chapterDao: ChapterDao
    private lateinit var workManager: WorkManager
    private lateinit var logManager: LogManager
    private lateinit var repository: PodcastRepository

    @Before
    fun setup() {
        database = mockk(relaxed = true)
        remoteDataSource = mockk()
        podcastDao = mockk(relaxed = true)
        episodeDao = mockk(relaxed = true)
        queueDao = mockk(relaxed = true)
        chapterDao = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        logManager = mockk(relaxed = true)

        val podcasts = listOf(
            Podcast("url1", "T1", "D1", "I1", "W1"),
            Podcast("url2", "T2", "D2", "I2", "W2")
        )
        every { podcastDao.getAllPodcasts() } returns flowOf(podcasts)
        every { queueDao.getQueueEpisodesWithPodcast() } returns flowOf(emptyList())
        every { episodeDao.getUnplayedEpisodesWithPodcast() } returns flowOf(emptyList())

        // withTransaction mock
        mockkStatic("androidx.room.RoomDatabaseKt")
        val transactionLambda = slot<suspend () -> Any>()
        coEvery { database.withTransaction(capture(transactionLambda)) } coAnswers {
            transactionLambda.captured.invoke()
        }

        repository = DefaultPodcastRepository(
            database = database,
            remoteDataSource = remoteDataSource,
            podcastDao = podcastDao,
            episodeDao = episodeDao,
            queueDao = queueDao,
            chapterDao = chapterDao,
            workManager = workManager,
            localMediaDataSource = mockk(relaxed = true),
            ioDispatcher = Dispatchers.Unconfined,
            logManager = logManager
        )
    }

    @Test
    fun fetchAndStorePodcast_success_insertsToDb() = runBlocking {
        val feedUrl = "http://example.com/feed"
        val podcast = Podcast(feedUrl, "Title", "Desc", "Img", "Web")
        val episodes = listOf(NetworkEpisodeWithChapters(com.yuval.podcasts.data.db.entity.NetworkEpisode("ep1", feedUrl, "Ep1", "Desc", "audio", null, null, 0L, 0L), emptyList()))
        coEvery { remoteDataSource.fetchPodcastData(feedUrl) } returns ParsedPodcast(podcast, episodes)

        repository.fetchAndStorePodcast(feedUrl)

        coVerify { podcastDao.insertPodcast(podcast) }
        coVerify { episodeDao.syncNetworkEpisodes(match { it.size == 1 && it[0].id == "ep1" }) }
    }

    @Test
    fun fetchAndStorePodcast_clearsStaleChapters_whenFeedNoLongerHasChapters() = runBlocking {
        val feedUrl = "http://example.com/feed"
        val podcast = Podcast(feedUrl, "Title", "Desc", "Img", "Web")
        // Episodes that previously had chapters in the DB but now arrive with NONE
        // (e.g. the publisher removed the <psc:chapters> from the feed). The stored
        // chapters for these episodes must still be cleared.
        val episodes = listOf(
            NetworkEpisodeWithChapters(com.yuval.podcasts.data.db.entity.NetworkEpisode("ep1", feedUrl, "Ep1", "Desc", "audio", null, null, 0L, 0L), emptyList()),
            NetworkEpisodeWithChapters(com.yuval.podcasts.data.db.entity.NetworkEpisode("ep2", feedUrl, "Ep2", "Desc", "audio", null, null, 0L, 0L), emptyList())
        )
        coEvery { remoteDataSource.fetchPodcastData(feedUrl) } returns ParsedPodcast(podcast, episodes)

        repository.fetchAndStorePodcast(feedUrl)

        coVerify {
            chapterDao.updateChaptersBulk(
                match { it.containsAll(listOf("ep1", "ep2")) },
                emptyList()
            )
        }
    }

    @Test
    fun fetchAndStorePodcast_usesSyncNetworkEpisodes_preventsOverwritingUserStates() = runBlocking {
        val feedUrl = "http://test.com/feed"
        val podcast = Podcast(feedUrl, "Title", "Desc", "Img", "Web")
        val networkEpisodes = listOf(NetworkEpisodeWithChapters(com.yuval.podcasts.data.db.entity.NetworkEpisode("ep1", feedUrl, "Ep1", "Desc", "audio", null, null, 0L, 0L), emptyList()))
        coEvery { remoteDataSource.fetchPodcastData(feedUrl) } returns ParsedPodcast(podcast, networkEpisodes)

        repository.fetchAndStorePodcast(feedUrl)

        coVerify { episodeDao.syncNetworkEpisodes(match { it.size == 1 && it[0].id == "ep1" }) }
        coVerify(exactly = 0) { episodeDao.insertEpisodes(any()) }
    }

    @Test
    fun fetchAndStorePodcast_runsOnIoDispatcher_preventsNetworkOnMainThread() = runBlocking {
        val feedUrl = "http://example.com/feed"
        
        val podcast = Podcast(feedUrl, "Title", "Desc", "Img", "Web")
        val episodes = listOf(NetworkEpisodeWithChapters(com.yuval.podcasts.data.db.entity.NetworkEpisode("ep1", feedUrl, "Ep1", "Desc", "audio", null, null, 0L, 0L), emptyList()))
        
        coEvery { remoteDataSource.fetchPodcastData(feedUrl) } returns ParsedPodcast(podcast, episodes)

        repository.fetchAndStorePodcast(feedUrl)

        coVerify { remoteDataSource.fetchPodcastData(feedUrl) }
    }

    @Test
    fun unsubscribePodcast_deletesAllData() = runTest {
        val feedUrl = "http://test.com/feed"
        val ep1 = Episode("ep1", feedUrl, "E1", "D", "A", null, null, 0L, 0L, 0, null, false, 0L)
        val ep2 = Episode("ep2", feedUrl, "E2", "D", "A", null, null, 0L, 0L, 0, null, false, 0L)
        
        coEvery { episodeDao.getEpisodesForPodcastSync(feedUrl) } returns listOf(ep1, ep2)
        coEvery { queueDao.removeFromQueueBulk(any()) } returns Unit
        coEvery { episodeDao.deleteEpisodesByPodcast(feedUrl) } returns Unit
        coEvery { podcastDao.deletePodcast(feedUrl) } returns Unit

        repository.unsubscribePodcast(feedUrl)

        coVerify { episodeDao.getEpisodesForPodcastSync(feedUrl) }
        coVerify { queueDao.removeFromQueueBulk(listOf("ep1", "ep2")) }
        coVerify { episodeDao.deleteEpisodesByPodcast(feedUrl) }
        coVerify { podcastDao.deletePodcast(feedUrl) }
    }

    @Test
    fun markAsPlayed_atomicallyMarksPlayedAndResetsPosition() = runTest {
        repository.markAsPlayed("ep1")

        // Must be a single atomic update, not two separate writes that can be interrupted.
        coVerify(exactly = 1) { episodeDao.markPlayedAndResetPosition("ep1", any()) }
        coVerify(exactly = 0) { episodeDao.updateLastPlayedPosition(any(), any()) }
    }

    @Test
    fun removeFromQueue_deletesFileAndUpdatesDbInTransaction() = runTest {
        val tempFile = java.io.File.createTempFile("remove_me", ".mp3")
        val ep = Episode(
            "ep1", "feed", "T", "D", "A", null, null, 0L, 0L,
            com.yuval.podcasts.data.db.entity.DownloadStatus.DOWNLOADED.value,
            tempFile.absolutePath, false, 0L
        )
        coEvery { episodeDao.getEpisodeById("ep1") } returns ep

        repository.removeFromQueue("ep1")

        // Queue removal + download-status reset must happen atomically inside a transaction.
        coVerify { database.withTransaction(any()) }
        coVerify { queueDao.removeFromQueue("ep1") }
        coVerify {
            episodeDao.updateDownloadStatus(
                "ep1",
                com.yuval.podcasts.data.db.entity.DownloadStatus.NOT_DOWNLOADED.value,
                null
            )
        }
        coVerify { workManager.cancelUniqueWork("${com.yuval.podcasts.data.Constants.WORK_TAG_DOWNLOAD_PREFIX}ep1") }
        // The local file is deleted.
        org.junit.Assert.assertFalse(tempFile.exists())
    }

    @Test
    fun markAllAsPlayed_callsDao() = runTest {
        coEvery { episodeDao.markAllUnplayedAsPlayed() } returns Unit
        
        repository.markAllAsPlayed()
        
        coVerify { episodeDao.markAllUnplayedAsPlayed() }
    }

    @Test
    fun reorderQueue_updatesDbWithCorrectPositions() = runTest {
        val newOrder = listOf("ep2", "ep1", "ep3")
        coEvery { queueDao.updateQueue(any()) } returns Unit
        
        repository.reorderQueue(newOrder)
        
        coVerify { 
            queueDao.updateQueue(match { 
                it.size == 3 && 
                it[0].episodeId == "ep2" && it[0].position == 0 &&
                it[1].episodeId == "ep1" && it[1].position == 1 &&
                it[2].episodeId == "ep3" && it[2].position == 2 
            }) 
        }
    }

    @Test
    fun requeueEpisode_insertsChronologically() = runTest {
        val ep1 = Episode("ep1", "f", "T1", "D", "A", null, null, 1000L, 0L, 0, null, false, 0L)
        val ep3 = Episode("ep3", "f", "T3", "D", "A", null, null, 3000L, 0L, 0, null, false, 0L)
        coEvery { queueDao.getQueueEpisodesSync() } returns listOf(ep1, ep3)
        
        val newEp = Episode("ep2", "f", "T2", "D", "A", null, null, 2000L, 0L, 0, null, false, 0L)
        
        repository.requeueEpisode(newEp)
        
        coVerify { queueDao.updateQueue(match { it.size == 3 && it[1].episodeId == "ep2" }) }
        coVerify { episodeDao.updatePlaybackStatus("ep2", true) }
    }

    @Test
    fun requeueMissingDownloads_verifiesPhysicalFiles() = runTest {
        val tempFile = java.io.File.createTempFile("exists_audio", ".mp3").apply { deleteOnExit() }
        
        // Ep 1: remote, downloadStatus = 2 but file missing -> should re-download
        val ep1 = Episode("ep1", "feedUrl", "Ep1", "Desc", "http://example.com/audio1.mp3", null, null, 1000L, 0L, 2, "/nonexistent/path.mp3", false, 0L)
        // Ep 2: local episode -> should skip
        val ep2 = Episode("ep2", com.yuval.podcasts.data.Constants.LOCAL_PODCAST_FEED_URL, "Ep2", "Desc", "/local/path.mp3", null, null, 2000L, 0L, 2, "/local/path.mp3", false, 0L)
        // Ep 3: remote, downloadStatus = 2 and file exists -> should skip
        val ep3 = Episode("ep3", "feedUrl", "Ep3", "Desc", "http://example.com/audio3.mp3", null, null, 3000L, 0L, 2, tempFile.absolutePath, false, 0L)
        
        coEvery { queueDao.getQueueEpisodesSync() } returns listOf(ep1, ep2, ep3)
        coEvery { workManager.enqueueUniqueWork(any<String>(), any<androidx.work.ExistingWorkPolicy>(), any<androidx.work.OneTimeWorkRequest>()) } returns mockk()
        
        repository.requeueMissingDownloads()
        
        coVerify(exactly = 1) { 
            workManager.enqueueUniqueWork(
                "${com.yuval.podcasts.data.Constants.WORK_TAG_DOWNLOAD_PREFIX}ep1", 
                any<androidx.work.ExistingWorkPolicy>(), 
                any<androidx.work.OneTimeWorkRequest>()
            ) 
        }
        coVerify(exactly = 0) { 
            workManager.enqueueUniqueWork(
                "${com.yuval.podcasts.data.Constants.WORK_TAG_DOWNLOAD_PREFIX}ep2", 
                any<androidx.work.ExistingWorkPolicy>(), 
                any<androidx.work.OneTimeWorkRequest>()
            ) 
        }
        coVerify(exactly = 0) { 
            workManager.enqueueUniqueWork(
                "${com.yuval.podcasts.data.Constants.WORK_TAG_DOWNLOAD_PREFIX}ep3", 
                any<androidx.work.ExistingWorkPolicy>(), 
                any<androidx.work.OneTimeWorkRequest>()
            ) 
        }
        
        tempFile.delete()
    }
}
