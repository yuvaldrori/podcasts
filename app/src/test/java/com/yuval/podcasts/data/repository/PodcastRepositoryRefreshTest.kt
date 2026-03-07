package com.yuval.podcasts.data.repository

import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.PodcastDao
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.network.PodcastApi
import com.yuval.podcasts.data.network.RssParser
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.InputStream
import com.yuval.podcasts.utils.MainDispatcherRule

import kotlinx.coroutines.test.StandardTestDispatcher

class PodcastRepositoryRefreshTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private lateinit var podcastDao: PodcastDao
    private lateinit var episodeDao: EpisodeDao
    private lateinit var podcastApi: PodcastApi
    private lateinit var rssParser: RssParser
    private lateinit var repository: PodcastRepository

    @Before
    fun setup() {
        podcastDao = mockk(relaxed = true)
        episodeDao = mockk(relaxed = true)
        podcastApi = mockk()
        rssParser = mockk()

        every { podcastDao.getAllPodcasts() } returns flowOf(emptyList())
        every { episodeDao.getUnplayedEpisodesWithPodcast() } returns flowOf(emptyList())
        val queueDao = mockk<com.yuval.podcasts.data.db.dao.QueueDao>(relaxed = true)
        every { queueDao.getQueueEpisodesWithPodcast() } returns flowOf(emptyList())

        repository = DefaultPodcastRepository(
            context = mockk(relaxed = true),
            database = mockk(relaxed = true),
            podcastApi = podcastApi,
            rssParser = rssParser,
            podcastDao = podcastDao,
            episodeDao = episodeDao,
            queueDao = queueDao,
            workManager = mockk(relaxed = true)
        )
    }

    @Test
    fun refreshAll_executesConcurrentlyWithLimit() = runTest(mainDispatcherRule.testDispatcher) {
        val podcasts = (1..10).map { Podcast("url$it", "title$it", "desc", "img", "web") }
        every { podcastDao.getAllPodcasts() } returns flowOf(podcasts)
        
        val mockInputStream: InputStream = mockk()
        every { mockInputStream.close() } returns Unit
        
        coEvery { podcastApi.fetchRss(any()) } coAnswers {
            delay(100) // Simulate network delay
            mockInputStream
        }
        every { rssParser.parse(any(), any()) } returns Pair(mockk(relaxed = true), emptyList())
        coEvery { podcastDao.insertPodcast(any()) } returns Unit
        coEvery { episodeDao.upsertEpisodes(any()) } returns Unit

        val startTime = System.currentTimeMillis()
        repository.refreshAll()
        val endTime = System.currentTimeMillis()
        
        val totalTime = endTime - startTime
        
        // If sequential, 10 * 100ms = 1000ms.
        // If parallel with a concurrency limit of 4, it should take ~300ms.
        assertTrue("Refresh should be faster than sequential execution (took ${totalTime}ms)", totalTime in 1..900)
    }
}
