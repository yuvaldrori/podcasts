package com.yuval.podcasts.media

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch

@RunWith(RobolectricTestRunner::class)
class PlayerStopPlayTest {

    @Test
    fun testStopAndPlay() {
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        
        player.setMediaItem(MediaItem.Builder().setUri("http://example.com/audio.mp3").build())
        player.prepare()
        player.play()
        
        player.stop()
        player.clearMediaItems()
        
        player.setMediaItem(MediaItem.Builder().setUri("http://example.com/audio2.mp3").build())
        player.prepare()
        player.play()
        
        assertEquals(Player.STATE_BUFFERING, player.playbackState)
    }
}
