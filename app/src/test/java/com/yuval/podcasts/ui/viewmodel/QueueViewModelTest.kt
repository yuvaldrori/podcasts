package com.yuval.podcasts.ui.viewmodel

import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.repository.PodcastRepository
import kotlinx.coroutines.launch
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.db.entity.Podcast
import kotlinx.coroutines.test.advanceUntilIdle
import com.yuval.podcasts.media.PlayerManager
import com.yuval.podcasts.utils.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class QueueViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: PodcastRepository
    private lateinit var playerManager: PlayerManager
    private lateinit var viewModel: QueueViewModel

    @Before
    fun setup() {
        repository = mockk()
        playerManager = mockk()

        every { repository.listeningQueue } returns flowOf(emptyList())
        
        every { playerManager.isPlaying } returns MutableStateFlow(false)
        every { playerManager.currentPosition } returns MutableStateFlow(0L)
        every { playerManager.duration } returns MutableStateFlow(0L)
        every { playerManager.playbackSpeed } returns MutableStateFlow(1f)
        every { playerManager.currentMediaId } returns MutableStateFlow(null)
        every { playerManager.initialize() } returns Unit

        viewModel = QueueViewModel(repository, playerManager)
    }

    @Test
    fun initialization_callsPlayerManagerInit() {
        verify { playerManager.initialize() }
    }

    @Test
    fun playPause_togglesPlayerManager() {
        every { playerManager.togglePlayPause() } returns Unit
        viewModel.playPause()
        verify { playerManager.togglePlayPause() }
    }

    @Test
    fun play_localFilePath_callsPlayerManager() {
        every { playerManager.play(any(), any(), any()) } returns Unit
        val episode = Episode(
            id = "ep1",
            podcastFeedUrl = "url",
            title = "title",
            description = "desc",
            audioUrl = "remoteUrl",
            imageUrl = null,
            pubDate = 0L,
            duration = 0L,
            downloadStatus = 0,
            localFilePath = "localUrl",
            isPlayed = false,
            lastPlayedPosition = 1000L
        )

        viewModel.play(episode)

        verify { playerManager.play("ep1", "localUrl", 1000L) }
    }

    @Test
    fun play_noLocalFilePath_callsPlayerManager() {
        every { playerManager.play(any(), any(), any()) } returns Unit
        val episode = Episode(
            id = "ep1",
            podcastFeedUrl = "url",
            title = "title",
            description = "desc",
            audioUrl = "remoteUrl",
            imageUrl = null,
            pubDate = 0L,
            duration = 0L,
            downloadStatus = 0,
            localFilePath = null, // No local path
            isPlayed = false,
            lastPlayedPosition = 1000L
        )

        viewModel.play(episode)

        verify { playerManager.play("ep1", "remoteUrl", 1000L) }
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
        coEvery { repository.removeFromQueue("ep2") } returns Unit
        every { repository.getEpisodeByIdFlow("ep1") } returns flowOf(ep1)
        
        every { playerManager.currentMediaId } returns MutableStateFlow("ep1")
        viewModel = QueueViewModel(repository, playerManager)
        
        val job2 = backgroundScope.launch { viewModel.queue.collect {} }
        advanceUntilIdle()

        viewModel.removeFromQueue("ep2")
        advanceUntilIdle()

        coVerify { repository.removeFromQueue("ep2") }
        verify(exactly = 0) { playerManager.stopAndClear() }
        verify(exactly = 0) { playerManager.play(any(), any(), any()) }
        job2.cancel()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun removeFromQueue_playingEpisode_withNext_playsNext() = runTest {
        val podcast = Podcast("feed", "Title", "Desc", "Img", "Web")
        val ep1 = Episode("ep1", "feed", "E1", "D", "audio1", null, 0L, 0L, 0, null, false, 0L)
        val ep2 = Episode("ep2", "feed", "E2", "D", "audio2", null, 0L, 0L, 0, null, false, 0L)
        
        coEvery { repository.removeFromQueue("ep1") } returns Unit
        every { repository.getEpisodeByIdFlow("ep1") } returns flowOf(ep1)
        every { playerManager.play(any(), any(), any()) } returns Unit
        every { playerManager.currentMediaId } returns MutableStateFlow("ep1")
        
        val queueList = listOf(EpisodeWithPodcast(ep1, podcast), EpisodeWithPodcast(ep2, podcast))
        every { repository.listeningQueue } returns flowOf(queueList)

        viewModel = QueueViewModel(repository, playerManager)
        
        val job2 = backgroundScope.launch { viewModel.queue.collect {} }
        advanceUntilIdle()

        viewModel.removeFromQueue("ep1")
        advanceUntilIdle()

        coVerify { repository.removeFromQueue("ep1") }
        verify { playerManager.play("ep2", "audio2", 0L) }
        verify(exactly = 0) { playerManager.stopAndClear() }
        job2.cancel()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun removeFromQueue_playingEpisode_noNext_stopsPlayer() = runTest {
        val podcast = Podcast("feed", "Title", "Desc", "Img", "Web")
        val ep1 = Episode("ep1", "feed", "E1", "D", "audio1", null, 0L, 0L, 0, null, false, 0L)

        coEvery { repository.removeFromQueue("ep1") } returns Unit
        every { repository.getEpisodeByIdFlow("ep1") } returns flowOf(ep1)
        every { playerManager.stopAndClear() } returns Unit
        every { playerManager.currentMediaId } returns MutableStateFlow("ep1")
        
        val queueList = listOf(EpisodeWithPodcast(ep1, podcast))
        every { repository.listeningQueue } returns flowOf(queueList)

        viewModel = QueueViewModel(repository, playerManager)
        
        val job2 = backgroundScope.launch { viewModel.queue.collect {} }
        advanceUntilIdle()

        viewModel.removeFromQueue("ep1")
        advanceUntilIdle()

        coVerify { repository.removeFromQueue("ep1") }
        verify { playerManager.stopAndClear() }
        verify(exactly = 0) { playerManager.play(any(), any(), any()) }
        job2.cancel()
    }
}
