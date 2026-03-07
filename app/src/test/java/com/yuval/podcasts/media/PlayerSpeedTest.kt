package com.yuval.podcasts.media

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerSpeedTest {

    @Test
    fun testPlayerStopResetsSpeed() {
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        player.playbackParameters = PlaybackParameters(2.0f)
        assertEquals(2.0f, player.playbackParameters.speed)
        
        player.stop()
        player.clearMediaItems()
        
        player.setMediaItem(MediaItem.Builder().setUri("http://example.com/audio.mp3").build())
        player.prepare()
        
        assertEquals(2.0f, player.playbackParameters.speed) // CHECK HERE
    }
}
