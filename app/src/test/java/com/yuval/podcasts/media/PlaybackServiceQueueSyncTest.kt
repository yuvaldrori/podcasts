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
    fun testSurgicalObserveQueueLogic() {
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        
        val itemA = MediaItem.Builder().setMediaId("A").setUri("http://a.com").build()
        val itemB = MediaItem.Builder().setMediaId("B").setUri("http://b.com").build()
        val itemC = MediaItem.Builder().setMediaId("C").setUri("http://c.com").build()
        
        // Initial ExoPlayer state
        player.setMediaItems(listOf(itemA, itemB, itemC), 0, 0L)
        player.prepare()
        
        // Simulation: User adds D, E and reorders: [D, E, A, B, C]
        // Currently playing: A (initially index 0)
        
        val itemD = MediaItem.Builder().setMediaId("D").setUri("http://d.com").build()
        val itemE = MediaItem.Builder().setMediaId("E").setUri("http://e.com").build()
        
        val newList = listOf(itemD, itemE, itemA, itemB, itemC)
        val newIds = newList.map { it.mediaId }
        val currentMediaId = player.currentMediaItem?.mediaId!!

        // 1. Remove items that are no longer in the new list, EXCEPT the currently playing one
        for (i in player.mediaItemCount - 1 downTo 0) {
            val id = player.getMediaItemAt(i).mediaId
            if (id != currentMediaId && !newIds.contains(id)) {
                player.removeMediaItem(i)
            }
        }

        // 2. Add new items
        val existingIdsInPlayer = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        newList.forEachIndexed { index, item ->
            if (!existingIdsInPlayer.contains(item.mediaId)) {
                player.addMediaItem(index, item)
            }
        }

        // 3. Move items to correct positions
        for (index in newList.indices) {
            val expectedId = newList[index].mediaId
            val actualId = player.getMediaItemAt(index).mediaId
            if (expectedId != actualId) {
                for (searchIndex in index + 1 until player.mediaItemCount) {
                    if (player.getMediaItemAt(searchIndex).mediaId == expectedId) {
                        player.moveMediaItem(searchIndex, index)
                        break
                    }
                }
            }
        }
        
        assertEquals("A", player.currentMediaItem?.mediaId)
        assertEquals(5, player.mediaItemCount)
        assertEquals("D", player.getMediaItemAt(0).mediaId)
        assertEquals("E", player.getMediaItemAt(1).mediaId)
        assertEquals("A", player.getMediaItemAt(2).mediaId)
        assertEquals("B", player.getMediaItemAt(3).mediaId)
        assertEquals("C", player.getMediaItemAt(4).mediaId)
    }
}
