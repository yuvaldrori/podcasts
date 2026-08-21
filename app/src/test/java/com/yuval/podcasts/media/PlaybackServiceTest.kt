package com.yuval.podcasts.media

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.repository.SettingsRepository
import com.yuval.podcasts.domain.usecase.RemoveEpisodeUseCase
import com.yuval.podcasts.utils.LogManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackServiceTest {

    @Test
    fun playerListener_onStateEnded_removesLastEpisode() = runTest {
        val removeEpisodeUseCase = mockk<RemoveEpisodeUseCase>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val logManager = mockk<LogManager>(relaxed = true)
        val player = mockk<Player>(relaxed = true)

        val testDispatcher = UnconfinedTestDispatcher(testScheduler)

        val listener = PlaybackServicePlayerListener(
            removeEpisodeUseCase = removeEpisodeUseCase,
            settingsRepository = settingsRepository,
            logManager = logManager,
            serviceScope = this,
            ioDispatcher = testDispatcher,
            getCurrentPlayer = { player },
            initialCurrentlyPlayingId = "episode_123"
        )

        listener.onPlaybackStateChanged(Player.STATE_ENDED)

        coVerify(exactly = 1) { removeEpisodeUseCase("episode_123", true) }
    }

    @Test
    fun playerListener_onMediaItemTransition_autoReason_seeksToLastPosition() = runTest {
        val lastPosition = 5000L
        val episodeId = "test_episode"

        val dummyEpisode = Episode(
            id = episodeId,
            podcastFeedUrl = "feed",
            title = "Ep 1",
            description = "Desc",
            audioUrl = "url",
            imageUrl = null,
            episodeWebLink = null,
            pubDate = 0L,
            duration = 1000,
            downloadStatus = 0,
            localFilePath = null,
            isPlayed = false,
            lastPlayedPosition = lastPosition,
            completedAt = null
        )

        val removeEpisodeUseCase = mockk<RemoveEpisodeUseCase>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val logManager = mockk<LogManager>(relaxed = true)
        val player = mockk<Player>(relaxed = true)

        every { player.currentPosition } returns 0L

        val testDispatcher = UnconfinedTestDispatcher(testScheduler)

        val listener = PlaybackServicePlayerListener(
            removeEpisodeUseCase = removeEpisodeUseCase,
            settingsRepository = settingsRepository,
            logManager = logManager,
            serviceScope = this,
            ioDispatcher = testDispatcher,
            getCurrentPlayer = { player }
        )
        listener.updateCachedPositions(listOf(dummyEpisode))

        val mediaItem = MediaItem.Builder().setMediaId(episodeId).build()
        listener.onMediaItemTransition(mediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)

        verify { player.seekTo(lastPosition) }
    }

    @Test
    fun playerListener_onMediaItemTransition_repeatReason_removesEpisode() = runTest {
        val removeEpisodeUseCase = mockk<RemoveEpisodeUseCase>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val logManager = mockk<LogManager>(relaxed = true)
        val player = mockk<Player>(relaxed = true)

        val testDispatcher = UnconfinedTestDispatcher(testScheduler)

        val listener = PlaybackServicePlayerListener(
            removeEpisodeUseCase = removeEpisodeUseCase,
            settingsRepository = settingsRepository,
            logManager = logManager,
            serviceScope = this,
            ioDispatcher = testDispatcher,
            getCurrentPlayer = { player },
            initialCurrentlyPlayingId = "episode_123"
        )

        val mediaItem = MediaItem.Builder().setMediaId("episode_123").build()
        listener.onMediaItemTransition(mediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)

        coVerify(exactly = 1) { removeEpisodeUseCase("episode_123", true) }
    }

    @Test
    fun playerListener_onMediaItemTransition_whenCached_seeksSynchronouslyBeforeCoroutineExecution() = runTest {
        val lastPosition = 15000L
        val episodeId = "cached_episode"
        val removeEpisodeUseCase = mockk<RemoveEpisodeUseCase>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val logManager = mockk<LogManager>(relaxed = true)
        val player = mockk<Player>(relaxed = true)

        val standardDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)

        val dummyEpisode = Episode(
            id = episodeId,
            podcastFeedUrl = "feed",
            title = "Ep Cached",
            description = "Desc",
            audioUrl = "url",
            imageUrl = null,
            episodeWebLink = null,
            pubDate = 0L,
            duration = 1000,
            downloadStatus = 0,
            localFilePath = null,
            isPlayed = false,
            lastPlayedPosition = lastPosition,
            completedAt = null
        )

        every { player.currentPosition } returns 0L

        val listener = PlaybackServicePlayerListener(
            removeEpisodeUseCase = removeEpisodeUseCase,
            settingsRepository = settingsRepository,
            logManager = logManager,
            serviceScope = this,
            ioDispatcher = standardDispatcher,
            getCurrentPlayer = { player }
        )

        listener.updateCachedPositions(listOf(dummyEpisode))

        val mediaItem = MediaItem.Builder().setMediaId(episodeId).build()

        // Transition to media item with standardDispatcher where coroutines haven't executed yet
        listener.onMediaItemTransition(mediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)

        // With caching, seek occurs synchronously without needing testScheduler.advanceUntilIdle()
        verify(exactly = 1) { player.seekTo(lastPosition) }
    }

    @Test
    fun playerListener_onIsPlayingChanged_whenBufferingWithPlayWhenReady_doesNotSavePosition() = runTest {
        val removeEpisodeUseCase = mockk<RemoveEpisodeUseCase>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val logManager = mockk<LogManager>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        val onSavePosition = mockk<(String?, Long?) -> Unit>(relaxed = true)

        every { player.playWhenReady } returns true
        every { player.playbackState } returns Player.STATE_BUFFERING
        every { player.currentMediaItem } returns MediaItem.Builder().setMediaId("ep1").build()
        every { player.currentPosition } returns 5000L

        val testDispatcher = UnconfinedTestDispatcher(testScheduler)

        val listener = PlaybackServicePlayerListener(
            removeEpisodeUseCase = removeEpisodeUseCase,
            settingsRepository = settingsRepository,
            logManager = logManager,
            serviceScope = this,
            ioDispatcher = testDispatcher,
            getCurrentPlayer = { player },
            onSavePosition = onSavePosition
        )

        listener.onIsPlayingChanged(false)

        verify(exactly = 0) { onSavePosition(any(), any()) }
    }

    @Test
    fun playerListener_onIsPlayingChanged_whenPausedWithPlayWhenReadyFalse_savesPosition() = runTest {
        val removeEpisodeUseCase = mockk<RemoveEpisodeUseCase>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val logManager = mockk<LogManager>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        val onSavePosition = mockk<(String?, Long?) -> Unit>(relaxed = true)

        every { player.playWhenReady } returns false
        every { player.playbackState } returns Player.STATE_READY
        every { player.currentMediaItem } returns MediaItem.Builder().setMediaId("ep1").build()
        every { player.currentPosition } returns 5000L

        val testDispatcher = UnconfinedTestDispatcher(testScheduler)

        val listener = PlaybackServicePlayerListener(
            removeEpisodeUseCase = removeEpisodeUseCase,
            settingsRepository = settingsRepository,
            logManager = logManager,
            serviceScope = this,
            ioDispatcher = testDispatcher,
            getCurrentPlayer = { player },
            onSavePosition = onSavePosition
        )

        listener.onIsPlayingChanged(false)

        verify(exactly = 1) { onSavePosition(null, null) }
    }
}



