package com.yuval.podcasts.ui.viewmodel

import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.domain.usecase.EnqueueEpisodeUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: PodcastRepository
    private lateinit var enqueueEpisodeUseCase: EnqueueEpisodeUseCase
    private lateinit var viewModel: HistoryViewModel

    private val historyFlow = MutableStateFlow<List<EpisodeWithPodcast>>(emptyList())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        enqueueEpisodeUseCase = mockk(relaxed = true)
        
        every { repository.playHistory } returns historyFlow
        
        viewModel = HistoryViewModel(repository, enqueueEpisodeUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun history_collectsFromRepository() = runTest {
        val job = launch { viewModel.history.collect {} }
        
        val podcast = Podcast("p1", "Title", "desc", "url", "url")
        val episode = Episode(
            id = "e1", 
            podcastFeedUrl = "p1", 
            title = "Title", 
            description = "desc", 
            audioUrl = "url", 
            imageUrl = null, 
            pubDate = 0L, 
            duration = 1000L, 
            downloadStatus = 0, 
            localFilePath = null, 
            isPlayed = true, 
            lastPlayedPosition = 0L,
            completedAt = 12345L
        )
        
        historyFlow.value = listOf(EpisodeWithPodcast(episode, podcast))
        advanceUntilIdle()
        
        assertEquals(1, viewModel.history.value.size)
        assertEquals("e1", viewModel.history.value[0].episode.id)
        
        job.cancel()
    }
    
    @Test
    fun enqueueEpisode_callsUseCase() = runTest {
        val podcast = Podcast("p1", "Title", "desc", "url", "url")
        val episode = Episode(
            id = "e1", 
            podcastFeedUrl = "p1", 
            title = "Title", 
            description = "desc", 
            audioUrl = "url", 
            imageUrl = null, 
            pubDate = 0L, 
            duration = 1000L, 
            downloadStatus = 0, 
            localFilePath = null, 
            isPlayed = true, 
            lastPlayedPosition = 0L,
            completedAt = 12345L
        )
        val episodeWithPodcast = EpisodeWithPodcast(episode, podcast)
        
        viewModel.enqueueEpisode(episodeWithPodcast)
        advanceUntilIdle()
        
        coVerify { enqueueEpisodeUseCase(episode) }
    }
}
