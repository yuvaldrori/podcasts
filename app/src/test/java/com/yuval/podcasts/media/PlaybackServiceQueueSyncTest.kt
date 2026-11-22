package com.yuval.podcasts.media

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.db.entity.Episode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackServiceQueueSyncTest {

    private fun createEpisode(id: String): Episode {
        return Episode(
            id = id, podcastFeedUrl = "feed", title = "Ep $id", description = "desc", audioUrl = "http://$id.com",
            imageUrl = null, episodeWebLink = null, pubDate = 0L, duration = 1000L, downloadStatus = 0,
            localFilePath = null, isPlayed = false, lastPlayedPosition = 0L, completedAt = null
        )
    }

    @Test
    fun testSurgicalObserveQueueLogic() {
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        try {
            val service = PlaybackService()

            val epA = createEpisode("A")
            val epB = createEpisode("B")
            val epC = createEpisode("C")

            val itemA = MediaItem.Builder().setMediaId("A").setUri("http://a.com").build()
            val itemB = MediaItem.Builder().setMediaId("B").setUri("http://b.com").build()
            val itemC = MediaItem.Builder().setMediaId("C").setUri("http://c.com").build()

            player.setMediaItems(listOf(itemA, itemB, itemC), 0, 0L)
            player.prepare()

            val epD = createEpisode("D")
            val epE = createEpisode("E")
            val itemD = MediaItem.Builder().setMediaId("D").setUri("http://d.com").build()
            val itemE = MediaItem.Builder().setMediaId("E").setUri("http://e.com").build()

            val newEpisodes = listOf(epD, epE, epA, epB, epC)
            val newMediaItems = listOf(itemD, itemE, itemA, itemB, itemC)

            service.updatePlayerFromQueue(player, newEpisodes, newMediaItems)

            assertEquals("A", player.currentMediaItem?.mediaId)
            assertEquals(2, player.currentMediaItemIndex)
            assertEquals(5, player.mediaItemCount)
            assertEquals("D", player.getMediaItemAt(0).mediaId)
            assertEquals("E", player.getMediaItemAt(1).mediaId)
            assertEquals("A", player.getMediaItemAt(2).mediaId)
            assertEquals("B", player.getMediaItemAt(3).mediaId)
            assertEquals("C", player.getMediaItemAt(4).mediaId)
        } finally {
            player.release()
        }
    }

    @Test
    fun testRemovePlayingEpisode_transitionsToNextItem() {
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        try {
            val service = PlaybackService()

            val epA = createEpisode("A")
            val epB = createEpisode("B")
            val epC = createEpisode("C")

            val itemA = MediaItem.Builder().setMediaId("A").setUri("http://a.com").build()
            val itemB = MediaItem.Builder().setMediaId("B").setUri("http://b.com").build()
            val itemC = MediaItem.Builder().setMediaId("C").setUri("http://c.com").build()

            // Start playing middle item B (index 1) so removing B must advance to C (index 1 in [A, C]),
            // proving it transitions to the next episode rather than resetting to index 0 (A).
            player.setMediaItems(listOf(itemA, itemB, itemC), 1, 0L)
            player.prepare()
            player.play()

            val newEpisodes = listOf(epA, epC)
            val newMediaItems = listOf(itemA, itemC)

            service.updatePlayerFromQueue(player, newEpisodes, newMediaItems)

            assertEquals(2, player.mediaItemCount)
            assertEquals("C", player.currentMediaItem?.mediaId)
            assertEquals(1, player.currentMediaItemIndex)
            assertEquals(0L, player.currentPosition)
            assertTrue("Removing playing episode should keep playback running on the next episode", player.playWhenReady)
        } finally {
            player.release()
        }
    }

    @Test
    fun updatePlayerFromQueue_whenCasting_doesNotReplaceMediaItemForDownloadedFilesOrMetadataEquivalence() {
        val service = PlaybackService()
        val castPlayer = mockk<androidx.media3.cast.CastPlayer>(relaxed = true)

        @Suppress("DEPRECATION")
        every { castPlayer.isCastSessionAvailable } returns true
        every { castPlayer.mediaItemCount } returns 2
        every { castPlayer.currentMediaItemIndex } returns 0

        val currentItem = MediaItem.Builder()
            .setMediaId("A")
            .setUri("https://remote.com/a.mp3")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Ep A").build())
            .build()

        val castItemB = MediaItem.Builder()
            .setMediaId("B")
            .setUri("https://remote.com/b.mp3")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Ep B").build())
            .build()

        every { castPlayer.currentMediaItem } returns currentItem
        every { castPlayer.getMediaItemAt(0) } returns currentItem
        every { castPlayer.getMediaItemAt(1) } returns castItemB

        val epA = createEpisode("A")
        val epB = createEpisode("B").copy(
            localFilePath = "/data/user/0/com.yuval.podcasts/files/episode_b.mp3",
            downloadStatus = 2
        )

        val updatedItemA = currentItem
        val extrasB = Bundle().apply {
            putString(Constants.EXTRA_REMOTE_AUDIO_URL, "https://remote.com/b.mp3")
        }
        val updatedItemB = MediaItem.Builder()
            .setMediaId("B")
            .setUri("/data/user/0/com.yuval.podcasts/files/episode_b.mp3")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Ep B")
                    .setExtras(extrasB)
                    .build()
            )
            .build()

        service.updatePlayerFromQueue(castPlayer, listOf(epA, epB), listOf(updatedItemA, updatedItemB))

        verify(exactly = 0) { castPlayer.replaceMediaItem(any(), any()) }
    }

    @Test
    fun updatePlayerFromQueue_whenMediaItemsOmitsUnmappableEpisode_alignsRemainingItemsById() {
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        try {
            val service = PlaybackService()
            val epA = createEpisode("A")
            val epBad = createEpisode("BAD")
            val epB = createEpisode("B")

            val itemA = MediaItem.Builder().setMediaId("A").setUri("http://a.com").build()
            val itemB = MediaItem.Builder().setMediaId("B").setUri("http://b.com")
                .setMediaMetadata(MediaMetadata.Builder().setTitle("Old B").build())
                .build()
            val updatedItemB = MediaItem.Builder().setMediaId("B").setUri("http://b.com")
                .setMediaMetadata(MediaMetadata.Builder().setTitle("New B").build())
                .build()

            player.setMediaItems(listOf(itemA, itemB), 0, 0L)
            player.prepare()

            // Simulate epBad failing MediaItemMapper (absent from mediaItems list)
            service.updatePlayerFromQueue(player, listOf(epA, epBad, epB), listOf(itemA, updatedItemB))

            assertEquals(2, player.mediaItemCount)
            assertEquals("A", player.getMediaItemAt(0).mediaId)
            assertEquals("B", player.getMediaItemAt(1).mediaId)
            assertEquals("New B", player.getMediaItemAt(1).mediaMetadata.title)
        } finally {
            player.release()
        }
    }
}
