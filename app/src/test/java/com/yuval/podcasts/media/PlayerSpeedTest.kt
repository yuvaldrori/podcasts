package com.yuval.podcasts.media

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerSpeedTest {

    /**
     * ExoPlayer retains PlaybackParameters across stop() + clearMediaItems() + prepare().
     * This is intentional: the app treats playback speed as a persistent user preference
     * (stored in _playbackSpeed StateFlow in PlayerManager), not something that resets per-episode.
     */
    @Test
    fun exoPlayer_retainsPlaybackSpeedAfterStop() {
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        player.playbackParameters = PlaybackParameters(2.0f)
        assertEquals(2.0f, player.playbackParameters.speed)

        player.stop()
        player.clearMediaItems()

        player.setMediaItem(MediaItem.Builder().setUri("http://example.com/audio.mp3").build())
        player.prepare()

        // ExoPlayer does NOT reset speed on stop — the app relies on this to persist user speed
        assertEquals(2.0f, player.playbackParameters.speed)
    }
}
