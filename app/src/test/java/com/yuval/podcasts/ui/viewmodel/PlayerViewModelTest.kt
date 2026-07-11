package com.yuval.podcasts.ui.viewmodel

import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.media.PlayerManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain

import com.yuval.podcasts.data.db.entity.Chapter
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.domain.usecase.EnqueueEpisodeUseCase

class PlayerViewModelTest {

    private lateinit var repository: PodcastRepository
    private lateinit var playerManager: PlayerManager
    private lateinit var enqueueEpisodeUseCase: EnqueueEpisodeUseCase
    private lateinit var viewModel: PlayerViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        repository = mockk(relaxed = true)
        playerManager = mockk(relaxed = true)
        enqueueEpisodeUseCase = mockk(relaxed = true)
        
        every { playerManager.isPlaying } returns MutableStateFlow(false)
        every { playerManager.isInitialized } returns MutableStateFlow(true)
        every { playerManager.currentPosition } returns MutableStateFlow(0L)
        
        every { repository.listeningQueue } returns flowOf(emptyList())

        viewModel = PlayerViewModel(repository, playerManager, enqueueEpisodeUseCase, testDispatcher)
    }

    @Test
    fun `playPause delegates to playerManager`() = runTest {
        viewModel.playPause()
        verify { playerManager.togglePlayPause() }
    }

    @Test
    fun `seekForward delegates to playerManager`() = runTest {
        viewModel.seekForward()
        verify { playerManager.seekForward() }
    }

    @Test
    fun `seekBackward delegates to playerManager`() = runTest {
        viewModel.seekBackward()
        verify { playerManager.seekBackward() }
    }

    @Test
    fun `play defers the local file check to the io dispatcher`() = runTest {
        // A StandardTestDispatcher queues work instead of running it inline, so we can observe
        // that the blocking File.exists() check + re-download enqueue are dispatched off the
        // caller's thread rather than executed synchronously inside play().
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val vm = PlayerViewModel(repository, playerManager, enqueueEpisodeUseCase, ioDispatcher)

        // A local episode whose file does not exist on disk -> should be re-enqueued for download.
        val episode = Episode(
            "ep1", "feed", "Title", "Desc", "Audio", null, null, 0L, 0L, 0,
            "/nonexistent/podcasts/ep1.mp3", false, 0L
        )

        vm.play(episode)

        // Since the entire play flow runs on the io dispatcher, playback and file check have not started yet.
        verify(exactly = 0) { playerManager.play(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { enqueueEpisodeUseCase(episode) }

        // Draining the IO dispatcher runs the deferred check (re-enqueueing the missing file) + starts playback.
        advanceUntilIdle()
        verify { playerManager.play(any(), any(), any(), any(), any()) }
        coVerify { enqueueEpisodeUseCase(episode) }
    }

    @Test
    fun `seekToChapter seeks in place when the chapter belongs to the current episode`() = runTest {
        every { playerManager.currentMediaId } returns MutableStateFlow("ep1")
        val vm = PlayerViewModel(repository, playerManager, enqueueEpisodeUseCase, testDispatcher)
        val episode = Episode("ep1", "feed", "Title", "Desc", "Audio", null, null, 0L, 0L, 0, null, false, 0L)
        val chapter = Chapter(episodeId = "ep1", title = "Intro", startTimeMs = 5_000L)

        vm.seekToChapter(episode, chapter)

        verify { playerManager.seekTo(5_000L) }
        verify(exactly = 0) { playerManager.play(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `seekToChapter starts the episode at the offset when a different episode is playing`() = runTest {
        every { playerManager.currentMediaId } returns MutableStateFlow("other_episode")
        val vm = PlayerViewModel(repository, playerManager, enqueueEpisodeUseCase, testDispatcher)
        val episode = Episode("ep1", "feed", "Title", "Desc", "http://host/a.mp3", null, null, 0L, 0L, 0, null, false, 0L)
        val chapter = Chapter(episodeId = "ep1", title = "Intro", startTimeMs = 5_000L)

        vm.seekToChapter(episode, chapter)

        // Should start ep1 from the chapter offset rather than seeking the other episode.
        verify { playerManager.play("ep1", any(), any(), any(), 5_000L) }
        verify(exactly = 0) { playerManager.seekTo(any()) }
    }
}
