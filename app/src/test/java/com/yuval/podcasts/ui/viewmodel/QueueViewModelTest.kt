package com.yuval.podcasts.ui.viewmodel

import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.repository.PodcastRepository
import kotlinx.coroutines.launch
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.db.entity.Podcast
import kotlinx.coroutines.test.advanceUntilIdle
import com.yuval.podcasts.media.PlayerManager
import com.yuval.podcasts.utils.MainDispatcherRule
import com.yuval.podcasts.domain.usecase.*
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
    private lateinit var reorderQueueUseCase: ReorderQueueUseCase
    private lateinit var viewModel: QueueViewModel

    private val listeningQueueFlow = MutableStateFlow<List<EpisodeWithPodcast>>(emptyList())
    private val currentMediaIdFlow = MutableStateFlow<String?>(null)
    private val playbackSpeedFlow = MutableStateFlow(1f)
    private val currentPositionFlow = MutableStateFlow(0L)
    private val durationFlow = MutableStateFlow(0L)

    @Before
    fun setup() {
        repository = mockk()
        playerManager = mockk()
        removeEpisodeUseCase = mockk()
        skipToNextEpisodeUseCase = mockk()
        reorderQueueUseCase = mockk(relaxed = true)

        every { repository.listeningQueue } returns listeningQueueFlow
        every { playerManager.currentMediaId } returns currentMediaIdFlow
        every { playerManager.playbackSpeed } returns playbackSpeedFlow
        every { playerManager.currentPosition } returns currentPositionFlow
        every { playerManager.duration } returns durationFlow

        viewModel = QueueViewModel(
            repository, 
            playerManager, 
            removeEpisodeUseCase, 
            skipToNextEpisodeUseCase, 
            reorderQueueUseCase
        )
    }

    @Test
    fun reorderQueue_callsUseCase() = runTest {
        val newOrder = listOf("ep2", "ep1")
        coEvery { reorderQueueUseCase(newOrder) } returns Unit
        
        viewModel.reorderQueue(newOrder)

        coVerify { reorderQueueUseCase(newOrder) }
    }

    @Test
    fun removeFromQueue_nonPlayingEpisode_onlyCallsRepository() = runTest {
        val episodeId = "ep1"
        currentMediaIdFlow.value = "other_ep"
        coEvery { removeEpisodeUseCase(episodeId, false) } returns Unit

        viewModel.removeFromQueue(episodeId)

        coVerify { removeEpisodeUseCase(episodeId, false) }
        coVerify(exactly = 0) { skipToNextEpisodeUseCase() }
    }

    @Test
    fun removeFromQueue_playingEpisode_callsSkipToNext() = runTest {
        val episodeId = "ep1"
        currentMediaIdFlow.value = episodeId
        coEvery { removeEpisodeUseCase(episodeId, false) } returns Unit
        coEvery { skipToNextEpisodeUseCase() } returns Unit

        viewModel.removeFromQueue(episodeId)

        coVerify { removeEpisodeUseCase(episodeId, false) }
        coVerify { skipToNextEpisodeUseCase() }
    }

    @Test
    fun queueTimeRemaining_calculatedCorrectlyWithPlaybackSpeed() = runTest {
        val podcast = Podcast("p1", "T", "D", "I", "W")
        val ep1 = Episode("e1", "p1", "T1", "D1", "A1", null, 0L, 3600L, 0, null, false, 0L) // 1 hour
        val ep2 = Episode("e2", "p1", "T2", "D2", "A2", null, 0L, 7200L, 0, null, false, 0L) // 2 hours
        
        val queue = listOf(EpisodeWithPodcast(ep1, podcast), EpisodeWithPodcast(ep2, podcast))
        listeningQueueFlow.value = queue
        
        // Use a job to collect uiState
        val job = backgroundScope.launch { viewModel.uiState.collect {} }

        // Test 1x speed
        playbackSpeedFlow.value = 1f
        advanceUntilIdle()
        // (3600 + 7200) * 1000 = 10,800,000 ms = 3 hours
        assertEquals(10800000L, (viewModel.uiState.value as QueueUiState.Success).queueTimeRemaining)

        // Test 2x speed
        playbackSpeedFlow.value = 2f
        advanceUntilIdle()
        // 10,800,000 / 2 = 5,400,000 ms = 1.5 hours
        assertEquals(5400000L, (viewModel.uiState.value as QueueUiState.Success).queueTimeRemaining)
        
        job.cancel()
    }
}
