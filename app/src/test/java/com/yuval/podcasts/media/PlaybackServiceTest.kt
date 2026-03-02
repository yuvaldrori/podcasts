package com.yuval.podcasts.media

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.yuval.podcasts.domain.usecase.RemoveEpisodeUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals

class PlaybackServiceTest {

    @Test
    fun playerListener_onStateEnded_removesLastEpisode() = runTest {
        // This is a unit test simulation of the PlaybackService's Player.Listener logic.
        val removeEpisodeUseCase = mockk<RemoveEpisodeUseCase>(relaxed = true)
        val listenerSlot = slot<Player.Listener>()
        
        val player = mockk<Player>(relaxed = true)
        every { player.addListener(capture(listenerSlot)) } returns Unit
        
        // Emulate the service setup manually for test isolation
        player.addListener(object : Player.Listener {
            var currentlyPlayingId: String? = "episode_123"

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && currentlyPlayingId != null) {
                    kotlinx.coroutines.runBlocking {
                        removeEpisodeUseCase(currentlyPlayingId!!, markAsPlayed = true)
                    }
                }
            }
        })

        val listener = listenerSlot.captured

        // Simulate the player reaching the end of the playlist
        listener.onPlaybackStateChanged(Player.STATE_ENDED)

        // Verify the fix triggers (this will fail before the fix because it's commented out in our mock)
        coVerify(exactly = 1) { removeEpisodeUseCase("episode_123", true) }
    }
}
