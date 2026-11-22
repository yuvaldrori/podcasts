package com.yuval.podcasts.media

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.yuval.podcasts.data.db.entity.Episode
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackServiceMetadataSyncTest {

    @Test
    fun testMetadataUpdateWithoutInterruption() {
        val realPlayer = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        try {
            val player = spyk(realPlayer)
            val service = PlaybackService()

            val itemA = MediaItem.Builder()
                .setMediaId("A")
                .setUri("http://a.com")
                .setMediaMetadata(MediaMetadata.Builder().setTitle("Old Title A").build())
                .build()
            val itemB = MediaItem.Builder()
                .setMediaId("B")
                .setUri("http://b.com")
                .setMediaMetadata(MediaMetadata.Builder().setTitle("Old Title B").build())
                .build()

            player.setMediaItems(listOf(itemA, itemB), 0, 0L)
            player.prepare()

            val epA = Episode("A", "feed", "New Title A", "desc", "http://a.com", null, null, 0L, 1000L, 0, null, false, 0L, null)
            val epB = Episode("B", "feed", "New Title B", "desc", "http://b.com", null, null, 0L, 1000L, 0, null, false, 0L, null)

            // Attempt to update both playing item A and non-playing item B
            val updatedItemA = MediaItem.Builder()
                .setMediaId("A")
                .setUri("http://a.com")
                .setMediaMetadata(MediaMetadata.Builder().setTitle("New Title A").build())
                .build()
            val updatedItemB = MediaItem.Builder()
                .setMediaId("B")
                .setUri("http://b.com")
                .setMediaMetadata(MediaMetadata.Builder().setTitle("New Title B").build())
                .build()

            service.updatePlayerFromQueue(player, listOf(epA, epB), listOf(updatedItemA, updatedItemB))

            // Non-playing item B is updated surgically
            assertEquals("New Title B", player.getMediaItemAt(1).mediaMetadata.title)
            assertEquals("B", player.getMediaItemAt(1).mediaId)
            // Currently playing item A is NOT replaced (avoiding playback interruption)
            assertEquals(0, player.currentMediaItemIndex)
            assertEquals("A", player.currentMediaItem?.mediaId)
            assertEquals("Old Title A", player.getMediaItemAt(0).mediaMetadata.title)
            verify(exactly = 0) { player.replaceMediaItem(0, any()) }
            verify(exactly = 1) { player.replaceMediaItem(1, updatedItemB) }
            // Must not rebuild the entire playlist via setMediaItems
            verify(exactly = 0) { player.setMediaItems(any<List<MediaItem>>()) }
            verify(exactly = 0) { player.setMediaItems(any<List<MediaItem>>(), any<Boolean>()) }
        } finally {
            realPlayer.release()
        }
    }
}
