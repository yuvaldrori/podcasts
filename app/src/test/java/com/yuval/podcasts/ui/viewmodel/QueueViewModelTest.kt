package com.yuval.podcasts.ui.viewmodel

import com.yuval.podcasts.data.Constants
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
    private lateinit var viewModel: QueueViewModel

    private val listeningQueueFlow = MutableStateFlow<List<EpisodeWithPodcast>>(emptyList())
    private val currentMediaIdFlow = MutableStateFlow<String?>(null)
    private val playbackSpeedFlow = MutableStateFlow(1f)
    private val currentPositionFlow = MutableStateFlow(0L)
    private val durationFlow = MutableStateFlow(0L)

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        playerManager = mockk(relaxed = true)
        removeEpisodeUseCase = mockk(relaxed = true)

        every { repository.listeningQueue } returns listeningQueueFlow
        every { playerManager.currentMediaId } returns currentMediaIdFlow
        every { playerManager.playbackSpeed } returns playbackSpeedFlow
        every { playerManager.currentPosition } returns currentPositionFlow
        every { playerManager.duration } returns durationFlow

        viewModel = QueueViewModel(
            repository, 
            playerManager, 
            removeEpisodeUseCase
        )
    }

    @Test
    fun reorderQueue_callsRepository() = runTest {
        val newOrder = listOf("ep2", "ep1")
        coEvery { repository.reorderQueue(newOrder) } returns Unit
        
        viewModel.reorderQueue(newOrder)

        coVerify { repository.reorderQueue(newOrder) }
    }

    @Test
    fun removeFromQueue_nonPlayingEpisode_onlyCallsRemoveEpisodeUseCase() = runTest {
        val episodeId = "ep1"
        currentMediaIdFlow.value = "other_ep"
        coEvery { removeEpisodeUseCase(episodeId, false) } returns Unit

        viewModel.removeFromQueue(episodeId)

        coVerify { removeEpisodeUseCase(episodeId, false) }
        verify(exactly = 0) { playerManager.seekToNextMediaItem() }
    }

    @Test
    fun removeFromQueue_playingEpisode_callsSeekToNext() = runTest {
        val episodeId = "ep1"
        currentMediaIdFlow.value = episodeId
        coEvery { removeEpisodeUseCase(episodeId, false) } returns Unit

        viewModel.removeFromQueue(episodeId)

        coVerify { removeEpisodeUseCase(episodeId, false) }
        verify { playerManager.seekToNextMediaItem() }
    }

    @Test
    fun queueTimeRemaining_calculatedCorrectlyWithPlaybackSpeed() = runTest {
        val podcast = Podcast("p1", "T", "D", "I", "W")
        val ep1 = Episode("e1", "p1", "T1", "D1", "A1", null, null, 0L, 3600L, 0, null, false, 0L, null) // 1 hour
        val ep2 = Episode("e2", "p1", "T2", "D2", "A2", null, null, 0L, 7200L, 0, null, false, 0L, null) // 2 hours
        
        val queue = listOf(EpisodeWithPodcast(ep1, podcast), EpisodeWithPodcast(ep2, podcast))
        listeningQueueFlow.value = queue
        
        // Use a job to collect the time-remaining flow
        val job = backgroundScope.launch { viewModel.queueTimeRemaining.collect {} }

        // Test 1x speed
        playbackSpeedFlow.value = 1f
        advanceUntilIdle()
        // (3600 + 7200) * 1000 = 10,800,000 ms = 3 hours
        assertEquals(10800000L, viewModel.queueTimeRemaining.value)

        // Test 2x speed
        playbackSpeedFlow.value = 2f
        advanceUntilIdle()
        // 10,800,000 / 2 = 5,400,000 ms = 1.5 hours
        assertEquals(5400000L, viewModel.queueTimeRemaining.value)

        job.cancel()
    }

    @Test
    fun commitReorder_retainsManualQueueDuringDelay() = runTest {
        val podcast = Podcast("p1", "T", "D", "I", "W")
        val ep1 = Episode("e1", "p1", "T1", "D1", "A1", null, null, 0L, 3600L, 0, null, false, 0L, null)
        val ep2 = Episode("e2", "p1", "T2", "D2", "A2", null, null, 0L, 7200L, 0, null, false, 0L, null)
        val initialQueue = listOf(EpisodeWithPodcast(ep1, podcast), EpisodeWithPodcast(ep2, podcast))
        listeningQueueFlow.value = initialQueue

        val uiStateJob = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        
        // Move item manually (swap e1 and e2)
        viewModel.moveItem(0, 1)
        
        // Assert manual queue is in effect
        var currentState = viewModel.uiState.value as QueueUiState.Success
        assertEquals("e2", currentState.queue[0].episode.id)

        // Commit reorder - this launches a coroutine with reorderQueue and delay(Constants.QUEUE_REORDER_COMMIT_DELAY_MS)
        viewModel.commitReorder()

        // Room has NOT committed yet, but even if database write happened, delay is pending.
        // Therefore, the manual override should STILL be active.
        currentState = viewModel.uiState.value as QueueUiState.Success
        assertEquals("e2", currentState.queue[0].episode.id)

        // Now advance virtual time by half the delay duration - the delay is still pending
        testScheduler.advanceTimeBy(Constants.QUEUE_REORDER_COMMIT_DELAY_MS / 2)
        currentState = viewModel.uiState.value as QueueUiState.Success
        assertEquals("e2", currentState.queue[0].episode.id)

        // Now advance virtual time to finish the delay (remaining half + 10ms margin)
        testScheduler.advanceTimeBy(Constants.QUEUE_REORDER_COMMIT_DELAY_MS / 2 + 10)
        
        // The delay finished, setting manual override to null, so it falls back to the database queue
        // (which hasn't emitted new order yet in this test, so it goes back to initialQueue e1)
        currentState = viewModel.uiState.value as QueueUiState.Success
        assertEquals("e1", currentState.queue[0].episode.id)

        uiStateJob.cancel()
    }
}
