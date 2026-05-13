package com.yuval.podcasts.ui.viewmodel

import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.lifecycle.asFlow
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.domain.usecase.*
import com.yuval.podcasts.utils.MainDispatcherRule
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import androidx.lifecycle.MutableLiveData

class FeedsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val instantTaskExecutorRule = androidx.arch.core.executor.testing.InstantTaskExecutorRule()

    private lateinit var repository: PodcastRepository
    private lateinit var enqueueEpisodeUseCase: EnqueueEpisodeUseCase
    private lateinit var refreshAllPodcastsUseCase: RefreshAllPodcastsUseCase
    private lateinit var workManager: WorkManager
    private lateinit var viewModel: FeedsViewModel

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        enqueueEpisodeUseCase = mockk(relaxed = true)
        refreshAllPodcastsUseCase = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        
        every { repository.allPodcasts } returns flowOf(emptyList())
        every { repository.unplayedEpisodes } returns flowOf(emptyList())
        
        val workInfosLiveData = MutableLiveData<List<WorkInfo>>(emptyList())
        every { workManager.getWorkInfosForUniqueWorkLiveData(any()) } returns workInfosLiveData

        viewModel = FeedsViewModel(
            repository,
            enqueueEpisodeUseCase,
            refreshAllPodcastsUseCase,
            workManager,
            DefaultMessageDelegate()
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
        
        val state = viewModel.uiState.value
        assertTrue("State should be Success but was $state", state is FeedsUiState.Success)
        val successState = state as FeedsUiState.Success
        
        assertFalse(successState.isRefreshing)
        assertNull(successState.errorMessage)
        verify { refreshAllPodcastsUseCase.invoke() }
        
        job.cancel()
    }
}
