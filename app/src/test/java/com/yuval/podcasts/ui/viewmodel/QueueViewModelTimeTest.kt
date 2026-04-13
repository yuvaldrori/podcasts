package com.yuval.podcasts.ui.viewmodel

import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.domain.usecase.*
import com.yuval.podcasts.media.PlayerManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModelTimeTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private lateinit var repository: PodcastRepository
    private lateinit var playerManager: PlayerManager
    private lateinit var removeEpisodeUseCase: RemoveEpisodeUseCase

    private lateinit var viewModel: QueueViewModel

    private val queueFlow = MutableStateFlow<List<EpisodeWithPodcast>>(emptyList())
    private val playbackSpeedFlow = MutableStateFlow(1.0f)
    private val currentMediaIdFlow = MutableStateFlow<String?>(null)
    private val currentPositionFlow = MutableStateFlow(0L)
    private val durationFlow = MutableStateFlow(0L)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        repository = mockk(relaxed = true)
        playerManager = mockk(relaxed = true)
        removeEpisodeUseCase = mockk(relaxed = true)

        every { repository.listeningQueue } returns queueFlow
        every { playerManager.playbackSpeed } returns playbackSpeedFlow
        every { playerManager.currentMediaId } returns currentMediaIdFlow
        every { playerManager.currentPosition } returns currentPositionFlow
        every { playerManager.duration } returns durationFlow

        viewModel = QueueViewModel(
            repository,
            playerManager,
            removeEpisodeUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testQueueTimeRemaining_usesPlayerManagerLiveDataForCurrentEpisode() = runTest {
        // Collect the flow to trigger the combine block
        val job = launch {
            viewModel.uiState.collect {}
        }
        
        // DB says duration is 80 minutes (4800s), position is 55 mins (3300000ms)
        val podcast = Podcast("p1", "Title", "desc", "url", "url")
        val episode = Episode(
            id = "e1", 
            podcastFeedUrl = "p1", 
            title = "Title", 
            description = "desc", 
            audioUrl = "url", 
            imageUrl = null, 
            episodeWebLink = null, 
            pubDate = 0L, 
            duration = 4800L, 
            downloadStatus = 0, 
            localFilePath = null, 
            isPlayed = false, 
            lastPlayedPosition = 3300000L,
            completedAt = null,
            localId = 0L
        )
        queueFlow.value = listOf(EpisodeWithPodcast(episode, podcast))
        
        // PlayerManager says duration is 85 minutes (5100000ms), position is 55:24 (3324000ms), speed 2x
        currentMediaIdFlow.value = "e1"
        durationFlow.value = 5100000L
        currentPositionFlow.value = 3324000L
        playbackSpeedFlow.value = 2.0f
        
        // Let flow combine run
        advanceUntilIdle()
        
        // Should calculate:
        // Remaining real = 5100000 - 3324000 = 1776000 ms
        // Speed 2x => 1776000 / 2 = 888000 ms
        val remaining = (viewModel.uiState.value as QueueUiState.Success).queueTimeRemaining
        assertEquals(888000L, remaining)
        
        job.cancel()
    }
}
