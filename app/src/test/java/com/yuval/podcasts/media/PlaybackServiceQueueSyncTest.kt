package com.yuval.podcasts.media

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.yuval.podcasts.data.db.entity.Episode
import org.junit.Assert.assertEquals
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
        assertEquals(5, player.mediaItemCount)
        assertEquals("D", player.getMediaItemAt(0).mediaId)
        assertEquals("E", player.getMediaItemAt(1).mediaId)
        assertEquals("A", player.getMediaItemAt(2).mediaId)
        assertEquals("B", player.getMediaItemAt(3).mediaId)
        assertEquals("C", player.getMediaItemAt(4).mediaId)
    }

    @Test
    fun testRemovePlayingEpisode_transitionsToNextItem() {
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        val service = PlaybackService()

        val epA = createEpisode("A")
        val epB = createEpisode("B")
        val epC = createEpisode("C")

        val itemA = MediaItem.Builder().setMediaId("A").setUri("http://a.com").build()
        val itemB = MediaItem.Builder().setMediaId("B").setUri("http://b.com").build()
        val itemC = MediaItem.Builder().setMediaId("C").setUri("http://c.com").build()

        player.setMediaItems(listOf(itemA, itemB, itemC), 0, 0L)
        player.prepare()

        val newEpisodes = listOf(epB, epC)
        val newMediaItems = listOf(itemB, itemC)

        service.updatePlayerFromQueue(player, newEpisodes, newMediaItems)

        assertEquals(2, player.mediaItemCount)
        assertEquals("B", player.currentMediaItem?.mediaId)
    }
}
