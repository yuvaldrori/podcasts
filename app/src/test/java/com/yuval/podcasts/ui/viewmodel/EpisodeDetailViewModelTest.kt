package com.yuval.podcasts.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.utils.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EpisodeDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: PodcastRepository
    private lateinit var enqueueEpisodeUseCase: com.yuval.podcasts.domain.usecase.EnqueueEpisodeUseCase
    private lateinit var savedStateHandle: SavedStateHandle

    @Before
    fun setup() {
        repository = mockk()
        enqueueEpisodeUseCase = mockk()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun init_loadsEpisodeState() = runTest {
        val episode = Episode("ep1", "url", "title", "desc", "url", null, 0L, 0L, 0, null, false, 0L)
        val podcast = Podcast("url", "podTitle", "podDesc", "img", "web")
        val episodeWithPodcast = EpisodeWithPodcast(episode, podcast)

        every { repository.getEpisodeWithPodcastFlow("ep1") } returns flowOf(episodeWithPodcast)
        every { repository.listeningQueue } returns flowOf(emptyList())
        
        savedStateHandle = SavedStateHandle(mapOf("episodeId" to "ep1"))
        val viewModel = EpisodeDetailViewModel(repository, enqueueEpisodeUseCase, savedStateHandle)
        
        // Start collection to trigger stateIn
        val job = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(episodeWithPodcast, viewModel.uiState.value.episodeWithPodcast)
        job.cancel()
    }

    @Test
    fun addToQueue_callsRepository() = runTest {
        val episode = Episode("ep1", "url", "title", "desc", "url", null, 0L, 0L, 0, null, false, 0L)
        coEvery { enqueueEpisodeUseCase(episode) } returns Unit
        every { repository.getEpisodeWithPodcastFlow(any()) } returns flowOf(null)
        every { repository.listeningQueue } returns flowOf(emptyList())

        savedStateHandle = SavedStateHandle(mapOf("episodeId" to "ep1"))
        val viewModel = EpisodeDetailViewModel(repository, enqueueEpisodeUseCase, savedStateHandle)

        viewModel.addToQueue(episode)

        coVerify { enqueueEpisodeUseCase(episode) }
    }

    @Test
    fun isInQueue_isTrueWhenEpisodeIsInQueue() = runTest {
        val episode = Episode("ep1", "url", "title", "desc", "url", null, 0L, 0L, 0, null, false, 0L)
        val podcast = Podcast("url", "podTitle", "podDesc", "img", "web")
        val episodeWithPodcast = EpisodeWithPodcast(episode, podcast)

        every { repository.getEpisodeWithPodcastFlow(any()) } returns flowOf(episodeWithPodcast)
        every { repository.listeningQueue } returns flowOf(listOf(episodeWithPodcast))

        savedStateHandle = SavedStateHandle(mapOf("episodeId" to "ep1"))
        val viewModel = EpisodeDetailViewModel(repository, enqueueEpisodeUseCase, savedStateHandle)

        val job = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.isInQueue)
        job.cancel()
    }

    @Test
    fun isInQueue_isFalseWhenEpisodeIsNotInQueue() = runTest {
        val episodeInQueue = Episode("ep2", "url", "title", "desc", "url", null, 0L, 0L, 0, null, false, 0L)
        val podcast = Podcast("url", "podTitle", "podDesc", "img", "web")

        every { repository.getEpisodeWithPodcastFlow(any()) } returns flowOf(null)
        every { repository.listeningQueue } returns flowOf(listOf(EpisodeWithPodcast(episodeInQueue, podcast)))

        savedStateHandle = SavedStateHandle(mapOf("episodeId" to "ep1"))
        val viewModel = EpisodeDetailViewModel(repository, enqueueEpisodeUseCase, savedStateHandle)

        val job = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isInQueue)
        job.cancel()
    }
}
