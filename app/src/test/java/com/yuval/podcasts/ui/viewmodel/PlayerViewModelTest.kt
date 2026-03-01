package com.yuval.podcasts.ui.viewmodel

import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.media.PlayerManager
import com.yuval.podcasts.utils.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: PodcastRepository
    private lateinit var playerManager: PlayerManager
    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setup() {
        repository = mockk()
        playerManager = mockk()

        every { playerManager.isPlaying } returns MutableStateFlow(false)
        every { playerManager.currentPosition } returns MutableStateFlow(0L)
        every { playerManager.duration } returns MutableStateFlow(0L)
        every { playerManager.playbackSpeed } returns MutableStateFlow(1f)
        every { playerManager.currentMediaId } returns MutableStateFlow(null)
        every { playerManager.initialize() } returns Unit

        viewModel = PlayerViewModel(repository, playerManager)
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
}
