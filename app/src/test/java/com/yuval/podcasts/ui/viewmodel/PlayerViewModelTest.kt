package com.yuval.podcasts.ui.viewmodel

import com.yuval.podcasts.data.db.entity.Chapter
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.domain.usecase.EnqueueEpisodeUseCase
import com.yuval.podcasts.media.PlayerManager
import com.yuval.podcasts.utils.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: PodcastRepository
    private lateinit var playerManager: PlayerManager
    private lateinit var enqueueEpisodeUseCase: EnqueueEpisodeUseCase
    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        playerManager = mockk(relaxed = true)
        enqueueEpisodeUseCase = mockk(relaxed = true)

        every { playerManager.isPlaying } returns MutableStateFlow(false)
        every { playerManager.isInitialized } returns MutableStateFlow(true)
        every { playerManager.currentPosition } returns MutableStateFlow(0L)
        every { repository.listeningQueue } returns flowOf(emptyList())

        viewModel = PlayerViewModel(
            repository,
            playerManager,
            enqueueEpisodeUseCase,
            mainDispatcherRule.testDispatcher,
            mainDispatcherRule.testDispatcher
        )
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
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val vm = PlayerViewModel(repository, playerManager, enqueueEpisodeUseCase, ioDispatcher, mainDispatcherRule.testDispatcher)

        val episode = Episode(
            "ep1", "feed", "Title", "Desc", "Audio", null, null, 0L, 0L, 0,
            "/nonexistent/podcasts/ep1.mp3", false, 0L
        )

        vm.play(episode)

        verify(exactly = 0) { playerManager.play(any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { enqueueEpisodeUseCase(episode) }

        advanceUntilIdle()
        verify { playerManager.play(any(), any(), any(), any(), any(), any(), any()) }
        coVerify { enqueueEpisodeUseCase(episode) }
    }

    @Test
    fun `seekToChapter seeks in place when the chapter belongs to the current episode`() = runTest {
        every { playerManager.currentMediaId } returns MutableStateFlow("ep1")
        val vm = PlayerViewModel(repository, playerManager, enqueueEpisodeUseCase, mainDispatcherRule.testDispatcher, mainDispatcherRule.testDispatcher)
        val episode = Episode("ep1", "feed", "Title", "Desc", "Audio", null, null, 0L, 0L, 0, null, false, 0L)
        val chapter = Chapter(episodeId = "ep1", title = "Intro", startTimeMs = 5_000L)

        vm.seekToChapter(episode, chapter)
        advanceUntilIdle()

        verify { playerManager.seekTo(5_000L) }
        verify(exactly = 0) { playerManager.play(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `seekToChapter starts the episode at the offset when a different episode is playing`() = runTest {
        every { playerManager.currentMediaId } returns MutableStateFlow("other_episode")
        val vm = PlayerViewModel(repository, playerManager, enqueueEpisodeUseCase, mainDispatcherRule.testDispatcher, mainDispatcherRule.testDispatcher)
        val episode = Episode("ep1", "feed", "Title", "Desc", "http://host/a.mp3", null, null, 0L, 0L, 0, null, false, 0L)
        val chapter = Chapter(episodeId = "ep1", title = "Intro", startTimeMs = 5_000L)

        vm.seekToChapter(episode, chapter)
        advanceUntilIdle()

        verify { playerManager.play("ep1", any(), any(), any(), 5_000L, any(), any()) }
        verify(exactly = 0) { playerManager.seekTo(any()) }
    }

    @Test
    fun `play falls back to podcast artwork when episode imageUrl is null`() = runTest {
        val episode = Episode("ep1", "feed1", "Title", "Desc", "http://host/a.mp3", null, null, 0L, 0L, 0, null, false, 0L)
        val podcast = Podcast("feed1", "Podcast Title", "Desc", "https://podcast.art/cover.png", "https://web.url")
        coEvery { repository.getPodcast("feed1") } returns podcast

        viewModel.play(episode)
        advanceUntilIdle()

        verify {
            playerManager.play(
                mediaId = "ep1",
                uri = "http://host/a.mp3",
                title = "Title",
                imageUrl = "https://podcast.art/cover.png",
                startPositionMs = 0L,
                artist = "Podcast Title",
                albumTitle = "Podcast Title"
            )
        }
    }
}
