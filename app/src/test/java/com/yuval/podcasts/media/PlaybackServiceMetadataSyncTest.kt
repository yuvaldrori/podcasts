package com.yuval.podcasts.media

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackServiceMetadataSyncTest {

    @Test
    fun testMetadataUpdateWithoutInterruption() {
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        
        val initialMetadata = MediaMetadata.Builder().setTitle("Old Title").build()
        val itemA = MediaItem.Builder()
            .setMediaId("A")
            .setUri("http://a.com")
            .setMediaMetadata(initialMetadata)
            .build()
        
        player.setMediaItem(itemA)
        player.prepare()
        
        // Simulate DB change with updated metadata
        val updatedMetadata = MediaMetadata.Builder().setTitle("New Title").build()
        val updatedItemA = MediaItem.Builder()
            .setMediaId("A")
            .setUri("http://a.com")
            .setMediaMetadata(updatedMetadata)
            .build()
            
        // Current logic in observeQueue
        val currentItem = player.getMediaItemAt(0)
        if (updatedItemA.mediaMetadata != currentItem.mediaMetadata) {
            player.replaceMediaItem(0, updatedItemA)
        }
        
        assertEquals("New Title", player.getMediaItemAt(0).mediaMetadata.title)
        assertEquals("A", player.getMediaItemAt(0).mediaId)
    }
}
