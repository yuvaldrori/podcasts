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
import com.yuval.podcasts.data.db.entity.QueueState
import com.yuval.podcasts.utils.MainDispatcherRule
import com.yuval.podcasts.utils.LogManager
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import com.yuval.podcasts.data.network.PodcastApi
import com.yuval.podcasts.data.network.RssParser
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PodcastRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: AppDatabase
    private lateinit var podcastApi: PodcastApi
    private lateinit var rssParser: RssParser
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
        podcastApi = mockk()
        rssParser = mockk()
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
            podcastApi = podcastApi,
            rssParser = rssParser,
            podcastDao = podcastDao,
            episodeDao = episodeDao,
            queueDao = queueDao,
            chapterDao = chapterDao,
            workManager = workManager,
            downloadProgressTracker = DownloadProgressTracker(),
            ioDispatcher = Dispatchers.Unconfined,
            logManager = logManager
        )
    }

    @Test
    fun fetchAndStorePodcast_success_insertsToDb() = runBlocking {
        val feedUrl = "http://example.com/feed"
        val podcast = Podcast(feedUrl, "Title", "Desc", "Img", "Web")
        val episodes = listOf(NetworkEpisodeWithChapters(com.yuval.podcasts.data.db.entity.NetworkEpisode("ep1", feedUrl, "Ep1", "Desc", "audio", null, null, 0L, 0L), emptyList()))
        stubFetchPodcastData(feedUrl, ParsedPodcast(podcast, episodes))

        repository.fetchAndStorePodcast(feedUrl)

        coVerify { podcastDao.insertPodcast(match { it.feedUrl == feedUrl }) }
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
        stubFetchPodcastData(feedUrl, ParsedPodcast(podcast, episodes))

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
        stubFetchPodcastData(feedUrl, ParsedPodcast(podcast, networkEpisodes))

        repository.fetchAndStorePodcast(feedUrl)

        coVerify { episodeDao.syncNetworkEpisodes(match { it.size == 1 && it[0].id == "ep1" }) }
        coVerify(exactly = 0) { episodeDao.insertEpisodes(any()) }
    }

    @Test
    fun fetchAndStorePodcast_runsOnIoDispatcher_preventsNetworkOnMainThread() = runBlocking {
        val feedUrl = "http://example.com/feed"
        
        val podcast = Podcast(feedUrl, "Title", "Desc", "Img", "Web")
        val episodes = listOf(NetworkEpisodeWithChapters(com.yuval.podcasts.data.db.entity.NetworkEpisode("ep1", feedUrl, "Ep1", "Desc", "audio", null, null, 0L, 0L), emptyList()))
        
        stubFetchPodcastData(feedUrl, ParsedPodcast(podcast, episodes))

        repository.fetchAndStorePodcast(feedUrl)

        coVerify { podcastApi.withRssStreamConditional<Int>(eq(feedUrl), any(), any(), any()) }
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
        coEvery { episodeDao.markAllUnplayedAsPlayed(any()) } returns Unit
        
        repository.markAllAsPlayed()
        
        coVerify { episodeDao.markAllUnplayedAsPlayed(any()) }
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
    fun requeueEpisode_insertsInTransactionReindexesPositionsAndResetsPlaybackStatus() = runTest {
        val ep1 = testEpisode("ep1", "feedA", 1000L)
        val ep3 = testEpisode("ep3", "feedC", 3000L)
        coEvery { queueDao.getQueueEpisodesSync() } returns listOf(ep1, ep3)

        val newEp = testEpisode("ep2", "feedB", 2000L)

        repository.requeueEpisode(newEp)

        coVerify(exactly = 1) { database.withTransaction(any()) }
        coVerify(exactly = 1) {
            queueDao.updateQueue(
                listOf(
                    QueueState("ep1", 0),
                    QueueState("ep2", 1),
                    QueueState("ep3", 2)
                )
            )
        }
        coVerify(exactly = 1) { episodeDao.updatePlaybackStatus("ep2", false, null) }
    }

    @Test
    fun requeueEpisode_forwardsCurrentlyPlayingEpisodeId() = runTest {
        val epB1 = testEpisode("epB1", "feedB", 3000L)
        val epC1 = testEpisode("epC1", "feedC", 4000L)
        coEvery { queueDao.getQueueEpisodesSync() } returns listOf(epB1, epC1)

        val epA1 = testEpisode("epA1", "feedA", 1000L)

        repository.requeueEpisode(epA1, currentlyPlayingEpisodeId = "epB1")

        coVerify(exactly = 1) { database.withTransaction(any()) }
        coVerify(exactly = 1) {
            queueDao.updateQueue(
                listOf(
                    QueueState("epB1", 0),
                    QueueState("epA1", 1),
                    QueueState("epC1", 2)
                )
            )
        }
        coVerify(exactly = 1) { episodeDao.updatePlaybackStatus("epA1", false, null) }
    }

    @Test
    fun requeueEpisode_alreadyInQueue_doesNotModifyQueueButResetsPlaybackStatus() = runTest {
        val epA1 = testEpisode("epA1", "feedA", 1000L)
        val epB1 = testEpisode("epB1", "feedB", 2000L)
        coEvery { queueDao.getQueueEpisodesSync() } returns listOf(epA1, epB1)

        repository.requeueEpisode(epA1)
        repository.requeueEpisode(epA1, currentlyPlayingEpisodeId = "epA1")

        coVerify(exactly = 0) { queueDao.updateQueue(any()) }
        coVerify(exactly = 2) { episodeDao.updatePlaybackStatus("epA1", false, null) }
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

    @Test
    fun fetchAndStorePodcast_when304NotModified_returnsZeroAndSkipsSync() = runBlocking {
        val feedUrl = "http://example.com/feed"
        val existingPodcast = Podcast(feedUrl, "Title", "Desc", "Img", "Web", etag = "\"etag_123\"", lastModified = "lm_123")
        coEvery { podcastDao.getPodcast(feedUrl) } returns existingPodcast

        coEvery { podcastApi.withRssStreamConditional<Int>(eq(feedUrl), eq("\"etag_123\""), eq("lm_123"), any()) } coAnswers {
            val block = arg<suspend (com.yuval.podcasts.data.network.RssFetchResult) -> Int>(3)
            block(com.yuval.podcasts.data.network.RssFetchResult.NotModified)
        }

        val count = repository.fetchAndStorePodcast(feedUrl)

        assertEquals(0, count)
        coVerify(exactly = 0) { episodeDao.syncNetworkEpisodes(any()) }
        coVerify(exactly = 0) { chapterDao.updateChaptersBulk(any(), any()) }
    }

    @Test
    fun fetchAndStorePodcast_when200Ok_updatesEtagAndLastModifiedInDb() = runBlocking {
        val feedUrl = "http://example.com/feed"
        val podcast = Podcast(feedUrl, "Title", "Desc", "Img", "Web", etag = "\"new_etag\"", lastModified = "new_lm")
        val episodes = listOf(NetworkEpisodeWithChapters(com.yuval.podcasts.data.db.entity.NetworkEpisode("ep1", feedUrl, "Ep1", "Desc", "audio", null, null, 0L, 0L), emptyList()))
        
        stubFetchPodcastData(feedUrl, ParsedPodcast(podcast, episodes), etag = "\"new_etag\"", lastModified = "new_lm")

        repository.fetchAndStorePodcast(feedUrl)

        coVerify { podcastDao.insertPodcast(match { it.etag == "\"new_etag\"" && it.lastModified == "new_lm" }) }
        coVerify { episodeDao.syncNetworkEpisodes(any()) }
    }

    @Test
    fun fetchAndStorePodcast_whenForceRefreshTrue_bypassesEtagAndLastModified() = runBlocking {
        val feedUrl = "http://example.com/feed"
        val existingPodcast = Podcast(feedUrl, "Title", "Desc", "Img", "Web", etag = "\"etag_123\"", lastModified = "lm_123")
        coEvery { podcastDao.getPodcast(feedUrl) } returns existingPodcast

        val podcast = Podcast(feedUrl, "Title", "Desc", "Img", "Web", etag = "\"new_etag\"", lastModified = "new_lm")
        val episodes = listOf(NetworkEpisodeWithChapters(com.yuval.podcasts.data.db.entity.NetworkEpisode("ep1", feedUrl, "Ep1", "Desc", "audio", null, null, 0L, 0L), emptyList()))
        
        stubFetchPodcastData(feedUrl, ParsedPodcast(podcast, episodes), etag = "\"new_etag\"", lastModified = "new_lm")

        repository.fetchAndStorePodcast(feedUrl, forceRefresh = true)

        coVerify { podcastApi.withRssStreamConditional<Int>(eq(feedUrl), isNull(), isNull(), any()) }
    }

    @Test
    fun fetchAndStorePodcast_when200OkWithNullHeaders_clearsExistingEtagAndLastModified() = runBlocking {
        val feedUrl = "http://example.com/feed"
        val existingPodcast = Podcast(feedUrl, "Title", "Desc", "Img", "Web", etag = "\"old_etag\"", lastModified = "old_lm")
        coEvery { podcastDao.getPodcast(feedUrl) } returns existingPodcast

        val podcast = Podcast(feedUrl, "Title", "Desc", "Img", "Web")
        val episodes = listOf(NetworkEpisodeWithChapters(com.yuval.podcasts.data.db.entity.NetworkEpisode("ep1", feedUrl, "Ep1", "Desc", "audio", null, null, 0L, 0L), emptyList()))
        
        // 200 OK with null etag and null lastModified
        stubFetchPodcastData(feedUrl, ParsedPodcast(podcast, episodes), etag = null, lastModified = null)

        repository.fetchAndStorePodcast(feedUrl)

        // Must insert podcast with etag = null and lastModified = null, not old_etag/old_lm
        coVerify { podcastDao.insertPodcast(match { it.etag == null && it.lastModified == null }) }
    }

    @Test
    fun fetchAndStorePodcast_whenLocalSubscription_skipsNetworkAndReturnsZero() = runBlocking {
        val count = repository.fetchAndStorePodcast(com.yuval.podcasts.data.Constants.LOCAL_PODCAST_FEED_URL)

        assertEquals(0, count)
        coVerify(exactly = 0) { podcastApi.withRssStreamConditional<Int>(any(), any(), any(), any()) }
        coVerify(exactly = 0) { podcastDao.insertPodcast(any()) }
    }

    @Test
    fun refreshPodcasts_filtersOutLocalSubscription() = runBlocking {
        val feedUrl = "http://example.com/feed"
        val podcast = Podcast(feedUrl, "Title", "Desc", "Img", "Web")
        val episodes = listOf(NetworkEpisodeWithChapters(com.yuval.podcasts.data.db.entity.NetworkEpisode("ep1", feedUrl, "Ep1", "Desc", "audio", null, null, 0L, 0L), emptyList()))
        stubFetchPodcastData(feedUrl, ParsedPodcast(podcast, episodes))
        coEvery { episodeDao.syncNetworkEpisodes(any()) } returns 1

        val progressUpdates = mutableListOf<Pair<Int, Int>>()
        val totalNew = repository.refreshPodcasts(
            urls = listOf(com.yuval.podcasts.data.Constants.LOCAL_PODCAST_FEED_URL, feedUrl),
            forceRefresh = false,
            onProgress = { current, total -> progressUpdates.add(current to total) }
        )

        assertEquals(1, totalNew)
        assertEquals(listOf(1 to 1), progressUpdates)
        coVerify(exactly = 0) {
            podcastApi.withRssStreamConditional<Int>(
                eq(com.yuval.podcasts.data.Constants.LOCAL_PODCAST_FEED_URL),
                any(),
                any(),
                any()
            )
        }
        verify(exactly = 0) { logManager.e(any(), match { it.contains(com.yuval.podcasts.data.Constants.LOCAL_PODCAST_FEED_URL) }, any()) }
    }

    private fun testEpisode(id: String, feedUrl: String, pubDate: Long): Episode =
        Episode(id, feedUrl, "Title-$id", "Desc", "http://example.com/$id.mp3", null, null, pubDate, 0L, 0, null, false, 0L)

    private fun stubFetchPodcastData(feedUrl: String, parsed: ParsedPodcast, etag: String? = null, lastModified: String? = null) {
        val mockInputStream = mockk<InputStream>(relaxed = true)
        coEvery { podcastApi.withRssStream<ParsedPodcast>(eq(feedUrl), any()) } coAnswers {
            val block = secondArg<suspend (InputStream) -> ParsedPodcast>()
            block(mockInputStream)
        }
        coEvery { podcastApi.withRssStreamConditional<Int>(eq(feedUrl), any(), any(), any()) } coAnswers {
            val block = arg<suspend (com.yuval.podcasts.data.network.RssFetchResult) -> Int>(3)
            block(com.yuval.podcasts.data.network.RssFetchResult.Success(mockInputStream, etag, lastModified))
        }
        every { rssParser.parse(mockInputStream, feedUrl) } returns parsed
    }
}
