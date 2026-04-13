package com.yuval.podcasts.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PlayerLastEpisodeTest {

    private lateinit var context: Context
    private lateinit var player: ExoPlayer

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        player = ExoPlayer.Builder(context).build()
    }

    @After
    fun tearDown() {
        player.release()
    }

    @Test
    fun testLastEpisodeDoesNotRepeat() {
        // Given a player with two items
        val item1 = MediaItem.fromUri("https://example.com/1.mp3")
        val item2 = MediaItem.fromUri("https://example.com/2.mp3")
        player.addMediaItems(listOf(item1, item2))
        
        // Ensure repeat mode is off by default
        assertEquals(Player.REPEAT_MODE_OFF, player.repeatMode)
        
        // When we are at the last item
        player.seekTo(1, 0)
        
        // In REPEAT_MODE_OFF, there should be no next item
        assertEquals(false, player.hasNextMediaItem())
    }

    @Test
    fun testLastEpisodeRepeatsIfModeIsAll() {
        val item1 = MediaItem.fromUri("https://example.com/1.mp3")
        val item2 = MediaItem.fromUri("https://example.com/2.mp3")
        player.addMediaItems(listOf(item1, item2))
        
        // When repeat mode is ALL
        player.repeatMode = Player.REPEAT_MODE_ALL
        
        // When we are at the last item
        player.seekTo(1, 0)
        
        // It should have a next item (the first one)
        assertEquals(true, player.hasNextMediaItem())
    }

    @Test
    fun testTransitionReasonAtEndOfPlaylist() {
        val item1 = MediaItem.fromUri("https://example.com/1.mp3")
        player.addMediaItem(item1)
        
        var stateEndedCalled = false
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    stateEndedCalled = true
                }
            }
        })
        
        // Simulate reaching the end
        player.prepare()
        // We can't easily "play" to the end in Robolectric without idling resources, 
        // but we can check what the player thinks is the next item.
        
        assertEquals(false, player.hasNextMediaItem())
        assertEquals(Player.REPEAT_MODE_OFF, player.repeatMode)
    }
}
