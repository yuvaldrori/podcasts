package com.yuval.podcasts.media

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.yuval.podcasts.data.db.entity.Episode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackServiceMetadataSyncTest {

    @Test
    fun testMetadataUpdateWithoutInterruption() {
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        val service = PlaybackService()

        val initialMetadata = MediaMetadata.Builder().setTitle("Old Title").build()
        val itemA = MediaItem.Builder()
            .setMediaId("A")
            .setUri("http://a.com")
            .setMediaMetadata(initialMetadata)
            .build()

        player.setMediaItem(itemA)
        player.prepare()

        // Current playing item is itemA (index 0).
        // Let's add item B so we can verify updating a non-currently-playing item in queue metadata:
        val itemB = MediaItem.Builder().setMediaId("B").setUri("http://b.com").setMediaMetadata(MediaMetadata.Builder().setTitle("Old B").build()).build()
        val epA = Episode("A", "feed", "Old Title", "desc", "http://a.com", null, null, 0L, 1000L, 0, null, false, 0L, null)
        val epB = Episode("B", "feed", "New Title B", "desc", "http://b.com", null, null, 0L, 1000L, 0, null, false, 0L, null)

        player.setMediaItems(listOf(itemA, itemB), 0, 0L)

        // Updated metadata for item B
        val updatedMetadataB = MediaMetadata.Builder().setTitle("New Title B").build()
        val updatedItemB = MediaItem.Builder()
            .setMediaId("B")
            .setUri("http://b.com")
            .setMediaMetadata(updatedMetadataB)
            .build()

        service.updatePlayerFromQueue(player, listOf(epA, epB), listOf(itemA, updatedItemB))

        assertEquals("New Title B", player.getMediaItemAt(1).mediaMetadata.title)
        assertEquals("B", player.getMediaItemAt(1).mediaId)
    }
}
