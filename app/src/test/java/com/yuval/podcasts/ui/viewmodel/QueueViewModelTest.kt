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
    fun removeFromQueue_nonPlayingEpisode_onlyCallsRemoveEpisodeUseCase() = runTest {
        val episodeId = "ep1"
        currentMediaIdFlow.value = "other_ep"
        coEvery { removeEpisodeUseCase(episodeId, true) } returns Unit

        viewModel.removeFromQueue(episodeId)
        advanceUntilIdle()

        coVerify { removeEpisodeUseCase(episodeId, true) }
        verify(exactly = 0) { playerManager.seekToNextMediaItem() }
    }

    @Test
    fun removeFromQueue_playingEpisode_callsSeekToNext() = runTest {
        val episodeId = "ep1"
        currentMediaIdFlow.value = episodeId
        coEvery { removeEpisodeUseCase(episodeId, true) } returns Unit

        viewModel.removeFromQueue(episodeId)
        advanceUntilIdle()

        coVerify { removeEpisodeUseCase(episodeId, true) }
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
    fun commitReorder_callsRepositoryAndClearsManualQueueUponCompletion() = runTest {
        val podcast = Podcast("p1", "T", "D", "I", "W")
        val ep1 = Episode("e1", "p1", "T1", "D1", "A1", null, null, 0L, 3600L, 0, null, false, 0L, null)
        val ep2 = Episode("e2", "p1", "T2", "D2", "A2", null, null, 0L, 7200L, 0, null, false, 0L, null)
        val ep3 = Episode("e3", "p1", "T3", "D3", "A3", null, null, 0L, 1800L, 0, null, false, 0L, null)
        val initialQueue = listOf(EpisodeWithPodcast(ep1, podcast), EpisodeWithPodcast(ep2, podcast))
        val reorderedQueue = listOf(EpisodeWithPodcast(ep2, podcast), EpisodeWithPodcast(ep1, podcast))
        listeningQueueFlow.value = initialQueue

        val uiStateJob = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // Move item manually (swap e1 and e2)
        viewModel.moveItem(0, 1)

        // Assert manual queue is in effect
        var currentState = viewModel.uiState.value as QueueUiState.Success
        assertEquals("e2", currentState.queue[0].episode.id)

        coEvery { repository.reorderQueue(listOf("e2", "e1")) } answers {
            listeningQueueFlow.value = reorderedQueue
        }

        // Commit reorder - should complete cleanly and clear manual dragging state
        viewModel.commitReorder()
        advanceUntilIdle()

        coVerify { repository.reorderQueue(listOf("e2", "e1")) }
        currentState = viewModel.uiState.value as QueueUiState.Success
        assertEquals("e2", currentState.queue[0].episode.id)

        // Emit a subsequent distinct DB update to prove isDragging was actually cleared
        listeningQueueFlow.value = listOf(EpisodeWithPodcast(ep3, podcast), EpisodeWithPodcast(ep2, podcast))
        advanceUntilIdle()

        currentState = viewModel.uiState.value as QueueUiState.Success
        assertEquals("e3", currentState.queue[0].episode.id)

        uiStateJob.cancel()
    }

    @Test
    fun commitReorder_revertsAndClearsManualQueueOnFailure() = runTest {
        val podcast = Podcast("p1", "T", "D", "I", "W")
        val ep1 = Episode("e1", "p1", "T1", "D1", "A1", null, null, 0L, 3600L, 0, null, false, 0L, null)
        val ep2 = Episode("e2", "p1", "T2", "D2", "A2", null, null, 0L, 7200L, 0, null, false, 0L, null)
        val ep3 = Episode("e3", "p1", "T3", "D3", "A3", null, null, 0L, 1800L, 0, null, false, 0L, null)
        val initialQueue = listOf(EpisodeWithPodcast(ep1, podcast), EpisodeWithPodcast(ep2, podcast))
        listeningQueueFlow.value = initialQueue

        val uiStateJob = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // Move item manually (swap e1 and e2)
        viewModel.moveItem(0, 1)

        coEvery { repository.reorderQueue(any()) } throws RuntimeException("Database error")

        viewModel.commitReorder()
        advanceUntilIdle()

        // Reverts back to real listeningQueueFlow order
        var currentState = viewModel.uiState.value as QueueUiState.Success
        assertEquals("e1", currentState.queue[0].episode.id)

        // Subsequent DB emission must flow through (proving isDragging was cleared)
        listeningQueueFlow.value = listOf(EpisodeWithPodcast(ep3, podcast))
        advanceUntilIdle()
        currentState = viewModel.uiState.value as QueueUiState.Success
        assertEquals("e3", currentState.queue[0].episode.id)

        uiStateJob.cancel()
    }

    @Test
    fun downloadProgressMap_exposesProgressFromRepository() = runTest {
        val progressMap = mapOf("ep1" to 45)
        every { repository.downloadProgressMap } returns MutableStateFlow(progressMap)

        val localViewModel = QueueViewModel(repository, playerManager, removeEpisodeUseCase)

        assertEquals(progressMap, localViewModel.downloadProgressMap.value)
    }

    @Test
    fun moveToTop_noCurrentEpisode_movesEpisodeToIndexZero() = runTest {
        val podcast = Podcast("p1", "T", "D", "I", "W")
        val ep1 = Episode("e1", "p1", "T1", "D1", "A1", null, null, 0L, 600L, 0, null, false, 0L, null)
        val ep2 = Episode("e2", "p1", "T2", "D2", "A2", null, null, 0L, 600L, 0, null, false, 0L, null)
        val ep3 = Episode("e3", "p1", "T3", "D3", "A3", null, null, 0L, 600L, 0, null, false, 0L, null)
        val initialQueue = listOf(
            EpisodeWithPodcast(ep1, podcast),
            EpisodeWithPodcast(ep2, podcast),
            EpisodeWithPodcast(ep3, podcast)
        )
        listeningQueueFlow.value = initialQueue
        currentMediaIdFlow.value = null

        val uiStateJob = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        coEvery { repository.reorderQueue(listOf("e3", "e1", "e2")) } answers {
            listeningQueueFlow.value = listOf(
                EpisodeWithPodcast(ep3, podcast),
                EpisodeWithPodcast(ep1, podcast),
                EpisodeWithPodcast(ep2, podcast)
            )
        }

        viewModel.moveToTop("e3")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.reorderQueue(listOf("e3", "e1", "e2")) }
        val state = viewModel.uiState.value as QueueUiState.Success
        assertEquals(listOf("e3", "e1", "e2"), state.queue.map { it.episode.id })

        uiStateJob.cancel()
    }

    @Test
    fun moveToTop_currentEpisodePausedAtTop_placesBelowCurrent() = runTest {
        val podcast = Podcast("p1", "T", "D", "I", "W")
        val ep1 = Episode("e1", "p1", "T1", "D1", "A1", null, null, 0L, 600L, 0, null, false, 0L, null)
        val ep2 = Episode("e2", "p1", "T2", "D2", "A2", null, null, 0L, 600L, 0, null, false, 0L, null)
        val ep3 = Episode("e3", "p1", "T3", "D3", "A3", null, null, 0L, 600L, 0, null, false, 0L, null)
        listeningQueueFlow.value = listOf(
            EpisodeWithPodcast(ep1, podcast),
            EpisodeWithPodcast(ep2, podcast),
            EpisodeWithPodcast(ep3, podcast)
        )
        currentMediaIdFlow.value = "e1"
        every { playerManager.isPlaying } returns MutableStateFlow(false)

        val uiStateJob = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.moveToTop("e3")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.reorderQueue(listOf("e1", "e3", "e2")) }
        val state = viewModel.uiState.value as QueueUiState.Success
        assertEquals(listOf("e1", "e3", "e2"), state.queue.map { it.episode.id })

        uiStateJob.cancel()
    }

    @Test
    fun moveToTop_episodeAboveCurrentEpisode_placesDirectlyBelowCurrent() = runTest {
        val podcast = Podcast("p1", "T", "D", "I", "W")
        val ep1 = Episode("e1", "p1", "T1", "D1", "A1", null, null, 0L, 600L, 0, null, false, 0L, null)
        val ep2 = Episode("e2", "p1", "T2", "D2", "A2", null, null, 0L, 600L, 0, null, false, 0L, null)
        val ep3 = Episode("e3", "p1", "T3", "D3", "A3", null, null, 0L, 600L, 0, null, false, 0L, null)
        val ep4 = Episode("e4", "p1", "T4", "D4", "A4", null, null, 0L, 600L, 0, null, false, 0L, null)
        listeningQueueFlow.value = listOf(
            EpisodeWithPodcast(ep1, podcast),
            EpisodeWithPodcast(ep2, podcast),
            EpisodeWithPodcast(ep3, podcast),
            EpisodeWithPodcast(ep4, podcast)
        )
        currentMediaIdFlow.value = "e3"

        val uiStateJob = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.moveToTop("e1")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.reorderQueue(listOf("e2", "e3", "e1", "e4")) }
        val state = viewModel.uiState.value as QueueUiState.Success
        assertEquals(listOf("e2", "e3", "e1", "e4"), state.queue.map { it.episode.id })

        uiStateJob.cancel()
    }

    @Test
    fun moveToTop_currentEpisodeOrAlreadyAtTarget_isNoOp() = runTest {
        val podcast = Podcast("p1", "T", "D", "I", "W")
        val ep1 = Episode("e1", "p1", "T1", "D1", "A1", null, null, 0L, 600L, 0, null, false, 0L, null)
        val ep2 = Episode("e2", "p1", "T2", "D2", "A2", null, null, 0L, 600L, 0, null, false, 0L, null)
        val ep3 = Episode("e3", "p1", "T3", "D3", "A3", null, null, 0L, 600L, 0, null, false, 0L, null)
        listeningQueueFlow.value = listOf(
            EpisodeWithPodcast(ep1, podcast),
            EpisodeWithPodcast(ep2, podcast),
            EpisodeWithPodcast(ep3, podcast)
        )
        currentMediaIdFlow.value = "e1"

        val uiStateJob = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.moveToTop("e1")
        viewModel.moveToTop("e2")
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.reorderQueue(any()) }
        val state = viewModel.uiState.value as QueueUiState.Success
        assertEquals(listOf("e1", "e2", "e3"), state.queue.map { it.episode.id })

        uiStateJob.cancel()
    }

    @Test
    fun moveToTop_multipleConsecutiveMoves_clustersEpisodesBelowCurrent() = runTest {
        val podcast = Podcast("p1", "T", "D", "I", "W")
        val ep1 = Episode("e1", "p1", "T1", "D1", "A1", null, null, 0L, 600L, 0, null, false, 0L, null)
        val ep2 = Episode("e2", "p1", "T2", "D2", "A2", null, null, 0L, 600L, 0, null, false, 0L, null)
        val ep3 = Episode("e3", "p1", "T3", "D3", "A3", null, null, 0L, 600L, 0, null, false, 0L, null)
        val ep4 = Episode("e4", "p1", "T4", "D4", "A4", null, null, 0L, 600L, 0, null, false, 0L, null)
        listeningQueueFlow.value = listOf(
            EpisodeWithPodcast(ep1, podcast),
            EpisodeWithPodcast(ep2, podcast),
            EpisodeWithPodcast(ep3, podcast),
            EpisodeWithPodcast(ep4, podcast)
        )
        currentMediaIdFlow.value = "e1"

        val uiStateJob = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.moveToTop("e3")
        viewModel.moveToTop("e4")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.reorderQueue(listOf("e1", "e4", "e3", "e2")) }
        val state = viewModel.uiState.value as QueueUiState.Success
        assertEquals(listOf("e1", "e4", "e3", "e2"), state.queue.map { it.episode.id })

        uiStateJob.cancel()
    }

    @Test
    fun moveToTop_revertsOnRepositoryFailure() = runTest {
        val podcast = Podcast("p1", "T", "D", "I", "W")
        val ep1 = Episode("e1", "p1", "T1", "D1", "A1", null, null, 0L, 600L, 0, null, false, 0L, null)
        val ep2 = Episode("e2", "p1", "T2", "D2", "A2", null, null, 0L, 600L, 0, null, false, 0L, null)
        val ep3 = Episode("e3", "p1", "T3", "D3", "A3", null, null, 0L, 600L, 0, null, false, 0L, null)
        listeningQueueFlow.value = listOf(
            EpisodeWithPodcast(ep1, podcast),
            EpisodeWithPodcast(ep2, podcast),
            EpisodeWithPodcast(ep3, podcast)
        )
        currentMediaIdFlow.value = "e1"

        val uiStateJob = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        coEvery { repository.reorderQueue(any()) } throws RuntimeException("Database error")

        viewModel.moveToTop("e3")
        advanceUntilIdle()

        val state = viewModel.uiState.value as QueueUiState.Success
        assertEquals(listOf("e1", "e2", "e3"), state.queue.map { it.episode.id })

        uiStateJob.cancel()
    }
}
