package com.yuval.podcasts.ui.viewmodel

import com.yuval.podcasts.data.repository.PodcastRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavBadgeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: PodcastRepository
    private lateinit var viewModel: NavBadgeViewModel

    private val queueCountFlow = MutableStateFlow(0)
    private val unplayedCountFlow = MutableStateFlow(0)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        every { repository.queueCount } returns queueCountFlow
        every { repository.unplayedEpisodeCount } returns unplayedCountFlow
        viewModel = NavBadgeViewModel(repository)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun counts_reflectQueueAndUnplayedCountFlows_andUpdateOnChange() = runTest {
        val queueJob = backgroundScope.launch { viewModel.queueCount.collect {} }
        val newJob = backgroundScope.launch { viewModel.newEpisodeCount.collect {} }

        queueCountFlow.value = 3
        unplayedCountFlow.value = 2
        advanceUntilIdle()

        assertEquals(3, viewModel.queueCount.value)
        assertEquals(2, viewModel.newEpisodeCount.value)

        queueCountFlow.value = 1
        unplayedCountFlow.value = 0
        advanceUntilIdle()

        assertEquals(1, viewModel.queueCount.value)
        assertEquals(0, viewModel.newEpisodeCount.value)

        queueJob.cancel()
        newJob.cancel()
    }
}
