package com.yuval.podcasts.media

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.repository.SettingsRepository
import com.yuval.podcasts.domain.usecase.RemoveEpisodeUseCase
import com.yuval.podcasts.utils.LogManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaybackServiceResumeTest {

    private fun createEpisode(id: String, lastPlayedPosition: Long) = Episode(
        id = id,
        podcastFeedUrl = "feed",
        title = "Ep 1",
        description = "Desc",
        audioUrl = "http://example.com/audio.mp3",
        imageUrl = null,
        episodeWebLink = null,
        pubDate = 0L,
        duration = 1000,
        downloadStatus = 0,
        localFilePath = null,
        isPlayed = false,
        lastPlayedPosition = lastPlayedPosition,
        completedAt = null
    )

    @Test
    fun onMediaItemTransition_seekTriggered_whenPositionIsAtStart() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val player = mockk<Player>(relaxed = true)
        val episodeId = "test_episode"
        val lastPosition = 5000L
        val mediaItem = MediaItem.Builder().setMediaId(episodeId).build()

        every { player.currentMediaItem } returns mediaItem
        every { player.currentPosition } returns 0L

        val listener = PlaybackServicePlayerListener(
            removeEpisodeUseCase = mockk<RemoveEpisodeUseCase>(relaxed = true),
            settingsRepository = mockk<SettingsRepository>(relaxed = true),
            logManager = mockk<LogManager>(relaxed = true),
            serviceScope = this,
            ioDispatcher = testDispatcher,
            getCurrentPlayer = { player }
        )
        listener.updateCachedPositions(listOf(createEpisode(episodeId, lastPosition)))

        listener.onMediaItemTransition(mediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)

        verify(exactly = 1) { player.seekTo(lastPosition) }
    }

    @Test
    fun onMediaItemTransition_noSeek_whenPlayerAlreadyPastThreshold() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val player = mockk<Player>(relaxed = true)
        val episodeId = "test_episode"
        val lastPosition = 5000L
        val mediaItem = MediaItem.Builder().setMediaId(episodeId).build()

        every { player.currentMediaItem } returns mediaItem
        every { player.currentPosition } returns 3000L

        val listener = PlaybackServicePlayerListener(
            removeEpisodeUseCase = mockk<RemoveEpisodeUseCase>(relaxed = true),
            settingsRepository = mockk<SettingsRepository>(relaxed = true),
            logManager = mockk<LogManager>(relaxed = true),
            serviceScope = this,
            ioDispatcher = testDispatcher,
            getCurrentPlayer = { player }
        )
        listener.updateCachedPositions(listOf(createEpisode(episodeId, lastPosition)))

        listener.onMediaItemTransition(mediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)

        verify(exactly = 0) { player.seekTo(any<Long>()) }
    }
}
