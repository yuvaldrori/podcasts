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
        val episodeDao = mockk<EpisodeDao>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val logManager = mockk<LogManager>(relaxed = true)
        val player = mockk<Player>(relaxed = true)

        val testDispatcher = UnconfinedTestDispatcher(testScheduler)

        val listener = PlaybackServicePlayerListener(
            removeEpisodeUseCase = removeEpisodeUseCase,
            episodeDao = episodeDao,
            settingsRepository = settingsRepository,
            logManager = logManager,
            serviceScope = this,
            ioDispatcher = testDispatcher,
            mainDispatcher = testDispatcher,
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
        val episodeDao = mockk<EpisodeDao>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val logManager = mockk<LogManager>(relaxed = true)
        val player = mockk<Player>(relaxed = true)

        coEvery { episodeDao.getEpisodeById(episodeId) } returns dummyEpisode
        every { player.currentPosition } returns 0L

        val testDispatcher = UnconfinedTestDispatcher(testScheduler)

        val listener = PlaybackServicePlayerListener(
            removeEpisodeUseCase = removeEpisodeUseCase,
            episodeDao = episodeDao,
            settingsRepository = settingsRepository,
            logManager = logManager,
            serviceScope = this,
            ioDispatcher = testDispatcher,
            mainDispatcher = testDispatcher,
            getCurrentPlayer = { player }
        )

        val mediaItem = MediaItem.Builder().setMediaId(episodeId).build()
        listener.onMediaItemTransition(mediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)

        verify { player.seekTo(lastPosition) }
    }

    @Test
    fun playerListener_onMediaItemTransition_repeatReason_removesEpisode() = runTest {
        val removeEpisodeUseCase = mockk<RemoveEpisodeUseCase>(relaxed = true)
        val episodeDao = mockk<EpisodeDao>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val logManager = mockk<LogManager>(relaxed = true)
        val player = mockk<Player>(relaxed = true)

        val testDispatcher = UnconfinedTestDispatcher(testScheduler)

        val listener = PlaybackServicePlayerListener(
            removeEpisodeUseCase = removeEpisodeUseCase,
            episodeDao = episodeDao,
            settingsRepository = settingsRepository,
            logManager = logManager,
            serviceScope = this,
            ioDispatcher = testDispatcher,
            mainDispatcher = testDispatcher,
            getCurrentPlayer = { player },
            initialCurrentlyPlayingId = "episode_123"
        )

        val mediaItem = MediaItem.Builder().setMediaId("episode_123").build()
        listener.onMediaItemTransition(mediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)

        coVerify(exactly = 1) { removeEpisodeUseCase("episode_123", true) }
    }
}
