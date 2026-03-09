package com.yuval.podcasts.ui.viewmodel

import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.domain.usecase.*
import com.yuval.podcasts.utils.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import app.cash.turbine.test
import kotlinx.coroutines.test.advanceTimeBy
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
    private lateinit var enqueueEpisodeUseCase: EnqueueEpisodeUseCase
    private lateinit var refreshAllPodcastsUseCase: RefreshAllPodcastsUseCase
    private lateinit var viewModel: FeedsViewModel

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        enqueueEpisodeUseCase = mockk(relaxed = true)
        refreshAllPodcastsUseCase = mockk(relaxed = true)
        
        every { repository.allPodcasts } returns flowOf(emptyList())
        every { repository.unplayedEpisodes } returns flowOf(emptyList())
        
        viewModel = FeedsViewModel(
            repository,
            enqueueEpisodeUseCase,
            refreshAllPodcastsUseCase
        )
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun refreshAll_success_updatesIsRefreshing() = runTest {
        // Collect the flow to trigger stateIn
        val job = backgroundScope.launch { viewModel.uiState.collect {} }
        
        every { refreshAllPodcastsUseCase.invoke() } returns Unit

        viewModel.refreshAll()
        advanceUntilIdle()
        
        assertFalse((viewModel.uiState.value as FeedsUiState.Success).isRefreshing)
        assertNull((viewModel.uiState.value as FeedsUiState.Success).errorMessage)
        io.mockk.verify { refreshAllPodcastsUseCase.invoke() }
        
        job.cancel()
    }
}
