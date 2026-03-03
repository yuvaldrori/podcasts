package com.yuval.podcasts.media

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.assertEquals

@RunWith(RobolectricTestRunner::class)
class PlayerReplaceTest {
    @Test
    fun testObserveQueueLogic() {
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        
        val itemA = MediaItem.Builder().setMediaId("A").setUri("http://a.com").build()
        val itemB = MediaItem.Builder().setMediaId("B").setUri("http://b.com").build()
        val itemC = MediaItem.Builder().setMediaId("C").setUri("http://c.com").build()
        
        // Initial ExoPlayer state
        player.setMediaItems(listOf(itemA, itemB, itemC), 0, 0L)
        player.prepare()
        
        // User adds D, E and reorders: [A, D, E, B, C]
        // Currently playing: A (index 0)
        
        val itemD = MediaItem.Builder().setMediaId("D").setUri("http://d.com").build()
        val itemE = MediaItem.Builder().setMediaId("E").setUri("http://e.com").build()
        
        val newMediaItems = listOf(itemA, itemD, itemE, itemB, itemC)
        
        val currentMediaId = player.currentMediaItem?.mediaId!!
        val currentInNewIndex = newMediaItems.indexOfFirst { it.mediaId == currentMediaId }
        val currentInPlayerIndex = player.currentMediaItemIndex
        
        if (currentInPlayerIndex < player.mediaItemCount - 1) {
            player.removeMediaItems(currentInPlayerIndex + 1, player.mediaItemCount)
        }
        if (currentInPlayerIndex > 0) {
            player.removeMediaItems(0, currentInPlayerIndex)
        }
        
        val beforeItems = newMediaItems.take(currentInNewIndex)
        if (beforeItems.isNotEmpty()) player.addMediaItems(0, beforeItems)
        
        val afterItems = newMediaItems.drop(currentInNewIndex + 1)
        if (afterItems.isNotEmpty()) player.addMediaItems(currentInNewIndex + 1, afterItems)
        
        assertEquals("A", player.currentMediaItem?.mediaId)
        assertEquals(5, player.mediaItemCount)
        assertEquals("A", player.getMediaItemAt(0).mediaId)
        assertEquals("D", player.getMediaItemAt(1).mediaId)
        assertEquals("E", player.getMediaItemAt(2).mediaId)
        assertEquals("B", player.getMediaItemAt(3).mediaId)
        assertEquals("C", player.getMediaItemAt(4).mediaId)
    }
}
