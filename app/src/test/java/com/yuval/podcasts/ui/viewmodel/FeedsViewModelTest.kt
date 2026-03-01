package com.yuval.podcasts.ui.viewmodel

import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.utils.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FeedsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: PodcastRepository
    private lateinit var enqueueEpisodeUseCase: com.yuval.podcasts.domain.usecase.EnqueueEpisodeUseCase
    private lateinit var viewModel: FeedsViewModel

    @Before
    fun setup() {
        repository = mockk()
        enqueueEpisodeUseCase = mockk()
        // Default returns for flows to avoid initialization errors
        every { repository.allPodcasts } returns flowOf(emptyList())
        every { repository.unplayedEpisodes } returns flowOf(emptyList())
        
        viewModel = FeedsViewModel(repository, enqueueEpisodeUseCase)
    }

    @Test
    fun refreshAll_success_updatesIsRefreshing() = runTest {
        coEvery { repository.refreshAll() } returns Unit

        assertFalse(viewModel.uiState.value.isRefreshing)
        viewModel.refreshAll()
        
        // UnconfinedTestDispatcher runs immediately, so we just check final states
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertNull(viewModel.uiState.value.errorMessage)
        coVerify { repository.refreshAll() }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun refreshAll_failure_setsErrorMessage() = runTest {
        val errorMessage = "Network Error"
        coEvery { repository.refreshAll() } throws Exception(errorMessage)

        val job = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.refreshAll()
        advanceUntilIdle()

        assertEquals("Failed to refresh all podcasts: $errorMessage", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isRefreshing)
        job.cancel()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun clearError_resetsErrorMessage() = runTest {
        val errorMessage = "Network Error"
        coEvery { repository.refreshAll() } throws Exception(errorMessage)

        val job = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.refreshAll()
        advanceUntilIdle()
        assertEquals("Failed to refresh all podcasts: $errorMessage", viewModel.uiState.value.errorMessage)
        
        viewModel.clearError()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)
        job.cancel()
    }

    @Test
    fun addToQueue_callsRepository() = runTest {
        val episode = Episode(
            id = "ep1",
            podcastFeedUrl = "url",
            title = "title",
            description = "desc",
            audioUrl = "url",
            imageUrl = null,
            pubDate = 0L,
            duration = 0L,
            downloadStatus = 0,
            localFilePath = null,
            isPlayed = false,
            lastPlayedPosition = 0L
        )
        coEvery { enqueueEpisodeUseCase(episode) } returns Unit

        viewModel.addToQueue(episode)

        coVerify { enqueueEpisodeUseCase(episode) }
    }

    @Test
    fun dismissEpisode_callsRepository() = runTest {
        val episode = Episode(
            id = "ep1",
            podcastFeedUrl = "url",
            title = "title",
            description = "desc",
            audioUrl = "url",
            imageUrl = null,
            pubDate = 0L,
            duration = 0L,
            downloadStatus = 0,
            localFilePath = null,
            isPlayed = false,
            lastPlayedPosition = 0L
        )
        coEvery { repository.markAsPlayed("ep1") } returns Unit

        viewModel.dismissEpisode(episode)

        coVerify { repository.markAsPlayed("ep1") }
    }

    @Test
    fun dismissAll_callsRepository() = runTest {
        coEvery { repository.markAllAsPlayed() } returns Unit

        viewModel.dismissAll()

        coVerify { repository.markAllAsPlayed() }
    }
}
