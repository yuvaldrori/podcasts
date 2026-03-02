package com.yuval.podcasts.ui.viewmodel

import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.repository.PodcastRepository
import kotlinx.coroutines.launch
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.db.entity.Podcast
import kotlinx.coroutines.test.advanceUntilIdle
import com.yuval.podcasts.media.PlayerManager
import com.yuval.podcasts.utils.MainDispatcherRule
import com.yuval.podcasts.domain.usecase.RemoveEpisodeUseCase
import com.yuval.podcasts.domain.usecase.SkipToNextEpisodeUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class QueueViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: PodcastRepository
    private lateinit var playerManager: PlayerManager
    private lateinit var removeEpisodeUseCase: RemoveEpisodeUseCase
    private lateinit var skipToNextEpisodeUseCase: SkipToNextEpisodeUseCase
    private lateinit var viewModel: QueueViewModel

    @Before
    fun setup() {
        repository = mockk()
        playerManager = mockk()
        removeEpisodeUseCase = mockk()
        skipToNextEpisodeUseCase = mockk()

        every { repository.listeningQueue } returns flowOf(emptyList())
        every { playerManager.currentMediaId } returns MutableStateFlow(null)
        every { playerManager.playbackSpeed } returns MutableStateFlow(1f)

        viewModel = QueueViewModel(repository, playerManager, removeEpisodeUseCase, skipToNextEpisodeUseCase)
    }

    @Test
    fun reorderQueue_callsRepository() = runTest {
        val newOrder = listOf("ep2", "ep1")
        coEvery { repository.reorderQueue(newOrder) } returns Unit
        
        viewModel.reorderQueue(newOrder)

        coVerify { repository.reorderQueue(newOrder) }
    }


    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun removeFromQueue_nonPlayingEpisode_onlyCallsRepository() = runTest {
        val ep1 = Episode("ep1", "feed", "E1", "D", "audio1", null, 0L, 0L, 0, null, false, 0L)
        coEvery { removeEpisodeUseCase("ep2", false) } returns Unit
        every { repository.getEpisodeByIdFlow("ep1") } returns flowOf(ep1)
        
        every { playerManager.currentMediaId } returns MutableStateFlow("ep1")
        viewModel = QueueViewModel(repository, playerManager, removeEpisodeUseCase, skipToNextEpisodeUseCase)
        
        val job2 = backgroundScope.launch { viewModel.queue.collect {} }
        advanceUntilIdle()

        viewModel.removeFromQueue("ep2")
        advanceUntilIdle()

        coVerify { removeEpisodeUseCase("ep2", false) }
        verify(exactly = 0) { skipToNextEpisodeUseCase() }
        job2.cancel()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun removeFromQueue_playingEpisode_callsSkipToNext() = runTest {
        val podcast = Podcast("feed", "Title", "Desc", "Img", "Web")
        val ep1 = Episode("ep1", "feed", "E1", "D", "audio1", null, 0L, 0L, 0, null, false, 0L)
        val ep2 = Episode("ep2", "feed", "E2", "D", "audio2", null, 0L, 0L, 0, null, false, 0L)
        
        coEvery { removeEpisodeUseCase("ep1", false) } returns Unit
        every { skipToNextEpisodeUseCase() } returns Unit
        every { repository.getEpisodeByIdFlow("ep1") } returns flowOf(ep1)
        every { playerManager.currentMediaId } returns MutableStateFlow("ep1")
        
        val queueList = listOf(EpisodeWithPodcast(ep1, podcast), EpisodeWithPodcast(ep2, podcast))
        every { repository.listeningQueue } returns flowOf(queueList)

        viewModel = QueueViewModel(repository, playerManager, removeEpisodeUseCase, skipToNextEpisodeUseCase)
        
        val job2 = backgroundScope.launch { viewModel.queue.collect {} }
        advanceUntilIdle()

        viewModel.removeFromQueue("ep1")
        advanceUntilIdle()

        coVerify { removeEpisodeUseCase("ep1", false) }
        verify { skipToNextEpisodeUseCase() }
        job2.cancel()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun queueTimeRemaining_calculatedCorrectlyWithPlaybackSpeed() = runTest {
        val podcast = Podcast("feed", "Title", "Desc", "Img", "Web")
        // Three episodes, 3 hours each (10,800 seconds each). 
        // Note: one episode has been partially played (1 hour in).
        val ep1 = Episode("ep1", "feed", "E1", "D", "audio", null, 0L, 10800L, 0, null, false, 3600000L) // 2 hours remaining
        val ep2 = Episode("ep2", "feed", "E2", "D", "audio", null, 0L, 10800L, 0, null, false, 0L) // 3 hours remaining
        val ep3 = Episode("ep3", "feed", "E3", "D", "audio", null, 0L, 10800L, 0, null, false, 0L) // 3 hours remaining
        
        val queueList = listOf(EpisodeWithPodcast(ep1, podcast), EpisodeWithPodcast(ep2, podcast), EpisodeWithPodcast(ep3, podcast))
        
        every { repository.listeningQueue } returns flowOf(queueList)
        
        // Setup player manager to have 2X speed
        val playbackSpeedFlow = MutableStateFlow(2f)
        every { playerManager.playbackSpeed } returns playbackSpeedFlow

        viewModel = QueueViewModel(repository, playerManager, removeEpisodeUseCase, skipToNextEpisodeUseCase)
        
        val job = backgroundScope.launch { viewModel.queueTimeRemaining.collect {} }
        advanceUntilIdle()

        // Total remaining time: 8 hours. At 2x speed, that's 4 hours.
        // 4 hours in milliseconds = 14,400,000 ms
        assertEquals(14400000L, viewModel.queueTimeRemaining.value)
        
        // Change speed to 1x
        playbackSpeedFlow.value = 1f
        advanceUntilIdle()
        
        // 8 hours in milliseconds = 28,800,000 ms
        assertEquals(28800000L, viewModel.queueTimeRemaining.value)
        
        job.cancel()
    }
}
