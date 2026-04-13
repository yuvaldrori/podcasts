package com.yuval.podcasts.data.repository

import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.PodcastDao
import com.yuval.podcasts.data.db.dao.ChapterDao
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.network.PodcastApi
import com.yuval.podcasts.data.network.RssParser
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.InputStream
import com.yuval.podcasts.utils.MainDispatcherRule
import kotlinx.coroutines.test.UnconfinedTestDispatcher

class PodcastRepositoryRefreshTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private lateinit var podcastDao: PodcastDao
    private lateinit var episodeDao: EpisodeDao
    private lateinit var chapterDao: ChapterDao
    private lateinit var remoteDataSource: com.yuval.podcasts.data.network.PodcastRemoteDataSource
    private lateinit var repository: PodcastRepository

    @Before
    fun setup() {
        podcastDao = mockk(relaxed = true)
        episodeDao = mockk(relaxed = true)
        chapterDao = mockk(relaxed = true)
        remoteDataSource = mockk()

        every { podcastDao.getAllPodcasts() } returns flowOf(emptyList())
        every { episodeDao.getUnplayedEpisodesWithPodcast() } returns flowOf(emptyList())
        val queueDao = mockk<com.yuval.podcasts.data.db.dao.QueueDao>(relaxed = true)
        every { queueDao.getQueueEpisodesWithPodcast() } returns flowOf(emptyList())

        repository = DefaultPodcastRepository(
            context = mockk(relaxed = true),
            database = mockk(relaxed = true),
            remoteDataSource = remoteDataSource,
            
            podcastDao = podcastDao,
            episodeDao = episodeDao,
            queueDao = queueDao,
            chapterDao = chapterDao,
            workManager = mockk(relaxed = true),
            localMediaDataSource = mockk(relaxed = true),
            ioDispatcher = mainDispatcherRule.testDispatcher
        )
    }

    @Test
    fun refreshAll_callsFetchForEveryPodcast() = runTest(mainDispatcherRule.testDispatcher) {
        val podcasts = (1..5).map { Podcast("url$it", "title$it", "desc", "img", "web") }
        every { podcastDao.getAllPodcasts() } returns flowOf(podcasts)
        
        // Re-create repository to pick up the updated mock flow
        repository = DefaultPodcastRepository(
            context = mockk(relaxed = true),
            database = mockk(relaxed = true),
            remoteDataSource = remoteDataSource,
            
            podcastDao = podcastDao,
            episodeDao = episodeDao,
            queueDao = mockk(relaxed = true),
            chapterDao = chapterDao,
            workManager = mockk(relaxed = true),
            localMediaDataSource = mockk(relaxed = true),
            ioDispatcher = mainDispatcherRule.testDispatcher
        )

        coEvery { remoteDataSource.fetchPodcastData(any()) } returns Pair(mockk(relaxed = true), emptyList())
        coEvery { podcastDao.insertPodcast(any()) } just Runs
        coEvery { episodeDao.upsertEpisodes(any()) } just Runs

        repository.refreshAll()
        
        coVerify(exactly = 5) { podcastDao.insertPodcast(any()) }
    }
}
