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
import com.yuval.podcasts.data.repository.SettingsRepository
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
    private lateinit var workInfosLiveData: MutableLiveData<List<WorkInfo>>
    private lateinit var viewModel: FeedsViewModel

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        enqueueEpisodeUseCase = mockk(relaxed = true)
        refreshAllPodcastsUseCase = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        
        every { repository.allPodcasts } returns flowOf(emptyList())
        every { repository.unplayedEpisodes } returns flowOf(emptyList())
        
        workInfosLiveData = MutableLiveData<List<WorkInfo>>(emptyList())
        every { workManager.getWorkInfosForUniqueWorkLiveData(any()) } returns workInfosLiveData

        viewModel = FeedsViewModel(
            repository,
            enqueueEpisodeUseCase,
            refreshAllPodcastsUseCase,
            workManager,
            mockk(relaxed = true), // logManager
            DefaultMessageDelegate()
        )
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun refreshAll_success_updatesIsRefreshing() = runTest {
        val job = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        
        every { refreshAllPodcastsUseCase.invoke() } returns Unit

        viewModel.refreshAll()
        
        // Simulate WorkManager starting
        val runningWorkInfo = mockk<WorkInfo>()
        every { runningWorkInfo.state } returns WorkInfo.State.RUNNING
        every { runningWorkInfo.progress } returns androidx.work.Data.EMPTY
        workInfosLiveData.value = listOf(runningWorkInfo)
        
        advanceUntilIdle()
        
        var state = viewModel.uiState.value as FeedsUiState.Success
        assertTrue("isRefreshing should be true while work is running", state.isRefreshing)
        assertNull("refreshProgress should be null if not reported", state.refreshProgress)

        // Simulate Progress
        val progressData = androidx.work.workDataOf(com.yuval.podcasts.data.Constants.WORK_KEY_PROGRESS to 5, com.yuval.podcasts.data.Constants.WORK_KEY_TOTAL to 10)
        every { runningWorkInfo.progress } returns progressData
        workInfosLiveData.value = listOf(runningWorkInfo)
        
        advanceUntilIdle()
        state = viewModel.uiState.value as FeedsUiState.Success
        assertEquals(5 to 10, state.refreshProgress)

        // Simulate WorkManager finishing
        val successWorkInfo = mockk<WorkInfo>()
        every { successWorkInfo.state } returns WorkInfo.State.SUCCEEDED
        workInfosLiveData.value = listOf(successWorkInfo)

        advanceUntilIdle()

        state = viewModel.uiState.value as FeedsUiState.Success
        assertFalse("isRefreshing should be false after work completes", state.isRefreshing)
        
        job.cancel()
    }
}
