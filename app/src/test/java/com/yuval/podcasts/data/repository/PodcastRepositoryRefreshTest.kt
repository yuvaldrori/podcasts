package com.yuval.podcasts.data.repository

import com.yuval.podcasts.data.db.AppDatabase
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.PodcastDao
import com.yuval.podcasts.data.db.dao.ChapterDao
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.db.entity.ParsedPodcast
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.yuval.podcasts.utils.MainDispatcherRule
import com.yuval.podcasts.utils.LogManager
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import androidx.room.withTransaction

class PodcastRepositoryRefreshTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private lateinit var podcastDao: PodcastDao
    private lateinit var episodeDao: EpisodeDao
    private lateinit var chapterDao: ChapterDao
    private lateinit var database: AppDatabase
    private lateinit var remoteDataSource: com.yuval.podcasts.data.network.PodcastRemoteDataSource
    private lateinit var logManager: LogManager
    private lateinit var repository: PodcastRepository

    @Before
    fun setup() {
        podcastDao = mockk(relaxed = true)
        episodeDao = mockk(relaxed = true)
        chapterDao = mockk(relaxed = true)
        database = mockk(relaxed = true)
        remoteDataSource = mockk()
        logManager = mockk(relaxed = true)

        every { podcastDao.getAllPodcasts() } returns flowOf(emptyList())
        every { episodeDao.getUnplayedEpisodesWithPodcast() } returns flowOf(emptyList())
        val queueDao = mockk<com.yuval.podcasts.data.db.dao.QueueDao>(relaxed = true)
        every { queueDao.getQueueEpisodesWithPodcast() } returns flowOf(emptyList())

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
            workManager = mockk(relaxed = true),
            localMediaDataSource = mockk(relaxed = true),
            ioDispatcher = mainDispatcherRule.testDispatcher,
            logManager = logManager
        )
    }

    @Test
    fun refreshAll_callsFetchForEveryPodcast() = runTest(mainDispatcherRule.testDispatcher) {
        val podcasts = (1..5).map { Podcast("url$it", "title$it", "desc", "img", "web") }
        every { podcastDao.getAllPodcasts() } returns flowOf(podcasts)
        
        // Re-create repository to pick up the updated mock flow
        repository = DefaultPodcastRepository(
            database = database,
            remoteDataSource = remoteDataSource,
            
            podcastDao = podcastDao,
            episodeDao = episodeDao,
            queueDao = mockk(relaxed = true),
            chapterDao = chapterDao,
            workManager = mockk(relaxed = true),
            localMediaDataSource = mockk(relaxed = true),
            ioDispatcher = mainDispatcherRule.testDispatcher,
            logManager = logManager
        )

        coEvery { remoteDataSource.fetchPodcastData(any()) } returns ParsedPodcast(mockk(relaxed = true), emptyList())
        coEvery { podcastDao.insertPodcast(any()) } just Runs
        coEvery { episodeDao.upsertEpisodes(any()) } just Runs

        repository.refreshAll()
        
        coVerify(exactly = 5) { podcastDao.insertPodcast(any()) }
    }
}
