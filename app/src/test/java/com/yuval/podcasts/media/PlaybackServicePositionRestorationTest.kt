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
class PlaybackServicePositionRestorationTest {

    @Test
    fun onMediaItemTransition_whenCurrentPlayerItemDiffersFromTransitionItem_doesNotSeek() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)

        val player = mockk<Player>(relaxed = true)
        val removeEpisodeUseCase = mockk<RemoveEpisodeUseCase>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val logManager = mockk<LogManager>(relaxed = true)

        val mediaItemA = MediaItem.Builder().setMediaId("epA").build()
        val mediaItemB = MediaItem.Builder().setMediaId("epB").build()

        val episodeA = Episode("epA", "feedUrl", "Title A", "Desc", "http://audioA.mp3", null, null, 0L, 1000L, 0, null, false, 30000L, null)

        // Player is already on Track B
        every { player.currentMediaItem } returns mediaItemB
        every { player.currentPosition } returns 0L

        val listener = PlaybackServicePlayerListener(
            removeEpisodeUseCase = removeEpisodeUseCase,
            settingsRepository = settingsRepository,
            logManager = logManager,
            serviceScope = this,
            ioDispatcher = testDispatcher,
            getCurrentPlayer = { player }
        )
        listener.updateCachedPositions(listOf(episodeA))

        // Trigger transition for Track A while currentMediaItem is Track B
        listener.onMediaItemTransition(mediaItemA, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)

        // Must NOT seek track B to any position
        verify(exactly = 0) { player.seekTo(any<Long>()) }
    }
}
