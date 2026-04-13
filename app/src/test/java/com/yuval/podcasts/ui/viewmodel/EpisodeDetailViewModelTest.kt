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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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
        val episode = Episode("ep1", "url", "title", "desc", "url", null, null, 0L, 0L, 0, null, false, 0L, null, 0L)
        val podcast = Podcast("url", "podTitle", "podDesc", "img", "web")
        val episodeWithPodcast = EpisodeWithPodcast(episode, podcast)

        every { repository.getEpisodeWithPodcastFlow("ep1") } returns flowOf(episodeWithPodcast)
        every { repository.listeningQueue } returns flowOf(emptyList())
        
        val savedStateHandle = androidx.lifecycle.SavedStateHandle()
        // Mock toRoute behavior manually because it relies on Bundle and Android internals which break in pure local unit tests without Robolectric
        // A hack for unit tests is needed since toRoute() is an inline function that tries to read from a Bundle.
        // We will just change EpisodeDetailViewModel to optionally take the ID directly for testing, or we just use Robolectric.
        // Actually, let's just make EpisodeDetailViewModelTest use Robolectric.
        val viewModel = EpisodeDetailViewModel(repository, enqueueEpisodeUseCase, SavedStateHandle(mapOf("episodeId" to "ep1")))
        
        // Start collection to trigger stateIn
        val job = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(episodeWithPodcast, (viewModel.uiState.value as EpisodeDetailUiState.Success).episodeWithPodcast)
        job.cancel()
    }

    @Test
    fun addToQueue_callsRepository() = runTest {
        val episode = Episode("ep1", "url", "title", "desc", "url", null, null, 0L, 0L, 0, null, false, 0L, null, 0L)
        coEvery { enqueueEpisodeUseCase(episode) } returns Unit
        every { repository.getEpisodeWithPodcastFlow(any()) } returns flowOf(null)
        every { repository.listeningQueue } returns flowOf(emptyList())

        val savedStateHandle = androidx.lifecycle.SavedStateHandle()
        // Mock toRoute behavior manually because it relies on Bundle and Android internals which break in pure local unit tests without Robolectric
        // A hack for unit tests is needed since toRoute() is an inline function that tries to read from a Bundle.
        // We will just change EpisodeDetailViewModel to optionally take the ID directly for testing, or we just use Robolectric.
        // Actually, let's just make EpisodeDetailViewModelTest use Robolectric.
        val viewModel = EpisodeDetailViewModel(repository, enqueueEpisodeUseCase, SavedStateHandle(mapOf("episodeId" to "ep1")))

        viewModel.addToQueue(episode)

        coVerify { enqueueEpisodeUseCase(episode) }
    }

    @Test
    fun isInQueue_isTrueWhenEpisodeIsInQueue() = runTest {
        val episode = Episode("ep1", "url", "title", "desc", "url", null, null, 0L, 0L, 0, null, false, 0L, null, 0L)
        val podcast = Podcast("url", "podTitle", "podDesc", "img", "web")
        val episodeWithPodcast = EpisodeWithPodcast(episode, podcast)

        every { repository.getEpisodeWithPodcastFlow(any()) } returns flowOf(episodeWithPodcast)
        every { repository.listeningQueue } returns flowOf(listOf(episodeWithPodcast))

        val savedStateHandle = androidx.lifecycle.SavedStateHandle()
        // Mock toRoute behavior manually because it relies on Bundle and Android internals which break in pure local unit tests without Robolectric
        // A hack for unit tests is needed since toRoute() is an inline function that tries to read from a Bundle.
        // We will just change EpisodeDetailViewModel to optionally take the ID directly for testing, or we just use Robolectric.
        // Actually, let's just make EpisodeDetailViewModelTest use Robolectric.
        val viewModel = EpisodeDetailViewModel(repository, enqueueEpisodeUseCase, SavedStateHandle(mapOf("episodeId" to "ep1")))

        val job = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(true, (viewModel.uiState.value as EpisodeDetailUiState.Success).isInQueue)
        job.cancel()
    }

    @Test
    fun isInQueue_isFalseWhenEpisodeIsNotInQueue() = runTest {
        val episodeInQueue = Episode("ep2", "url", "title", "desc", "url", null, null, 0L, 0L, 0, null, false, 0L, null, 0L)
        val podcast = Podcast("url", "podTitle", "podDesc", "img", "web")
        val activeEpisode = Episode("ep1", "url", "title", "desc", "url", null, null, 0L, 0L, 0, null, false, 0L, null, 0L)
        every { repository.getEpisodeWithPodcastFlow(any()) } returns flowOf(EpisodeWithPodcast(activeEpisode, podcast))
        every { repository.listeningQueue } returns flowOf(listOf(EpisodeWithPodcast(episodeInQueue, podcast)))

        val savedStateHandle = androidx.lifecycle.SavedStateHandle()
        // Mock toRoute behavior manually because it relies on Bundle and Android internals which break in pure local unit tests without Robolectric
        // A hack for unit tests is needed since toRoute() is an inline function that tries to read from a Bundle.
        // We will just change EpisodeDetailViewModel to optionally take the ID directly for testing, or we just use Robolectric.
        // Actually, let's just make EpisodeDetailViewModelTest use Robolectric.
        val viewModel = EpisodeDetailViewModel(repository, enqueueEpisodeUseCase, SavedStateHandle(mapOf("episodeId" to "ep1")))

        val job = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(false, (viewModel.uiState.value as EpisodeDetailUiState.Success).isInQueue)
        job.cancel()
    }
}
