package com.yuval.podcasts.ui.viewmodel

import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.db.entity.Podcast
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

    private val queueFlow = MutableStateFlow<List<EpisodeWithPodcast>>(emptyList())
    private val unplayedFlow = MutableStateFlow<List<EpisodeWithPodcast>>(emptyList())

    private val podcast = Podcast("f", "T", "D", "I", "W")
    private fun ewp(id: String) =
        EpisodeWithPodcast(Episode(id, "f", "T", "D", "A", null, null, 0L, 0L, 0, null, false, 0L), podcast)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        every { repository.listeningQueue } returns queueFlow
        every { repository.unplayedEpisodes } returns unplayedFlow
        viewModel = NavBadgeViewModel(repository)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun counts_reflectQueueAndUnplayedFlows_andUpdateOnChange() = runTest {
        val queueJob = backgroundScope.launch { viewModel.queueCount.collect {} }
        val newJob = backgroundScope.launch { viewModel.newEpisodeCount.collect {} }

        queueFlow.value = listOf(ewp("q1"), ewp("q2"), ewp("q3"))
        unplayedFlow.value = listOf(ewp("n1"), ewp("n2"))
        advanceUntilIdle()

        assertEquals(3, viewModel.queueCount.value)
        assertEquals(2, viewModel.newEpisodeCount.value)

        // Counts must track the flows as the queue / new episodes change.
        queueFlow.value = listOf(ewp("q1"))
        unplayedFlow.value = emptyList()
        advanceUntilIdle()

        assertEquals(1, viewModel.queueCount.value)
        assertEquals(0, viewModel.newEpisodeCount.value)

        queueJob.cancel()
        newJob.cancel()
    }
}
