package com.yuval.podcasts.data.repository

import android.content.Context
import androidx.work.WorkManager
import com.yuval.podcasts.data.db.AppDatabase
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.PodcastDao
import com.yuval.podcasts.data.db.dao.QueueDao
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.db.entity.QueueState
import com.yuval.podcasts.data.network.PodcastApi
import com.yuval.podcasts.data.network.RssParser
import com.yuval.podcasts.data.opml.OpmlManager
import com.yuval.podcasts.utils.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.test.runTest
import retrofit2.Response

class PodcastRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var podcastApi: PodcastApi
    private lateinit var rssParser: RssParser
    private lateinit var opmlManager: OpmlManager
    private lateinit var podcastDao: PodcastDao
    private lateinit var episodeDao: EpisodeDao
    private lateinit var queueDao: QueueDao
    private lateinit var workManager: WorkManager
    private lateinit var repository: PodcastRepository

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        database = mockk(relaxed = true)
        podcastApi = mockk()
        rssParser = mockk()
        opmlManager = mockk()
        podcastDao = mockk(relaxed = true)
        episodeDao = mockk(relaxed = true)
        queueDao = mockk(relaxed = true)
        workManager = mockk(relaxed = true)

        val podcasts = listOf(
            Podcast("url1", "T1", "D1", "I1", "W1"),
            Podcast("url2", "T2", "D2", "I2", "W2")
        )
        every { podcastDao.getAllPodcasts() } returns flowOf(podcasts)
        every { queueDao.getQueueEpisodesWithPodcast() } returns flowOf(emptyList())
        every { episodeDao.getUnplayedEpisodesWithPodcast() } returns flowOf(emptyList())

        repository = PodcastRepository(
            context = context,
            database = database,
            podcastApi = podcastApi,
            rssParser = rssParser,
            opmlManager = opmlManager,
            podcastDao = podcastDao,
            episodeDao = episodeDao,
            queueDao = queueDao,
            workManager = workManager
        )
    }

    @Test
    fun fetchAndStorePodcast_success_insertsToDb() = runTest {
        val feedUrl = "http://example.com/feed"
        val responseBody = "mock xml".toResponseBody()
        val mockResponse = Response.success(responseBody)
        
        coEvery { podcastApi.fetchRss(feedUrl) } returns mockResponse
        
        val podcast = Podcast(feedUrl, "Title", "Desc", "Img", "Web")
        val episodes = listOf(Episode("ep1", feedUrl, "Ep1", "Desc", "audio", null, 0L, 0L, 0, null, false, 0L))
        coEvery { rssParser.parse(any(), feedUrl) } returns Pair(podcast, episodes)

        repository.fetchAndStorePodcast(feedUrl)

        coVerify { podcastDao.insertPodcast(podcast) }
        coVerify { episodeDao.insertEpisodes(episodes) }
    }

    @Test
    fun refreshAll_callsFetchForEveryPodcast() = runTest {
        val podcasts = listOf(
            Podcast("url1", "T1", "D1", "I1", "W1"),
            Podcast("url2", "T2", "D2", "I2", "W2")
        )
        every { podcastDao.getAllPodcasts() } returns flowOf(podcasts)
        
        val responseBody = "mock xml".toResponseBody()
        val mockResponse = Response.success(responseBody)
        coEvery { podcastApi.fetchRss(any()) } returns mockResponse
        coEvery { rssParser.parse(any(), any()) } returns Pair(podcasts[0], emptyList())

        repository.refreshAll()

        coVerify { podcastApi.fetchRss("url1") }
        coVerify { podcastApi.fetchRss("url2") }
    }

    @Test
    fun enqueueEpisode_updatesQueueAndWorkManager() = runTest {
        val episode = Episode("ep1", "feed", "Ep1", "Desc", "audio", null, 0L, 0L, 0, null, false, 0L)
        val currentQueue = listOf(QueueState("ep2", 0))
        every { queueDao.getQueue() } returns flowOf(currentQueue)
        
        repository.enqueueEpisode(episode)

        coVerify { queueDao.updateQueue(match { it.size == 2 && it.find { state -> state.episodeId == "ep1" }?.position == 0 }) }
        coVerify { episodeDao.updatePlaybackStatus("ep1", true) }
        io.mockk.verify { workManager.enqueueUniqueWork(any<String>(), any<androidx.work.ExistingWorkPolicy>(), any<androidx.work.OneTimeWorkRequest>()) }
    }


    @Test
    fun unsubscribePodcast_deletesAllData() = runTest {
        val feedUrl = "http://test.com/feed"
        val ep1 = Episode("ep1", feedUrl, "E1", "D", "A", null, 0L, 0L, 0, null, false, 0L)
        val ep2 = Episode("ep2", feedUrl, "E2", "D", "A", null, 0L, 0L, 0, null, false, 0L)
        
        coEvery { episodeDao.getEpisodesForPodcastSync(feedUrl) } returns listOf(ep1, ep2)
        coEvery { queueDao.removeFromQueue(any()) } returns Unit
        coEvery { episodeDao.deleteEpisodesByPodcast(feedUrl) } returns Unit
        coEvery { podcastDao.deletePodcast(feedUrl) } returns Unit

        repository.unsubscribePodcast(feedUrl)

        coVerify { episodeDao.getEpisodesForPodcastSync(feedUrl) }
        coVerify { queueDao.removeFromQueue("ep1") }
        coVerify { queueDao.removeFromQueue("ep2") }
        coVerify { episodeDao.deleteEpisodesByPodcast(feedUrl) }
        coVerify { podcastDao.deletePodcast(feedUrl) }
    }


    @Test
    fun removeFromQueue_removesFromDbAndDeletesFile() = runTest {
        val episodeId = "ep1"
        val mockEpisode = Episode(episodeId, "feed", "T", "D", "A", null, 0L, 0L, 2, "/fake/path/file.mp3", false, 0L)
        
        coEvery { queueDao.removeFromQueue(episodeId) } returns Unit
        coEvery { episodeDao.getEpisodeById(episodeId) } returns mockEpisode
        coEvery { episodeDao.updateDownloadStatus(episodeId, 0, null) } returns Unit

        repository.removeFromQueue(episodeId)

        coVerify { queueDao.removeFromQueue(episodeId) }
        coVerify { episodeDao.getEpisodeById(episodeId) }
        coVerify { episodeDao.updateDownloadStatus(episodeId, 0, null) }
        // File deletion is harder to verify without mocking File, but we verify the DB updates
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
}
