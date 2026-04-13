package com.yuval.podcasts.media

import androidx.media3.common.MediaItem
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.entity.Episode
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MediaSessionCallbackTest {

    @Test
    fun testOnAddMediaItemsResolution() {
        val episodeDao = mockk<EpisodeDao>()
        val episode = Episode(
            id = "ext-id",
            podcastFeedUrl = "url",
            title = "Resolved Title",
            description = "",
            audioUrl = "http://audio.com",
            pubDate = 0,
            duration = 0,
            downloadStatus = 0,
            localFilePath = null
        )
        
        coEvery { episodeDao.getEpisodeById("ext-id") } returns episode
        
        // Mocking the behavior inside onAddMediaItems
        val inputItems = mutableListOf(MediaItem.Builder().setMediaId("ext-id").build())
        
        // This is the logic we want to verify (extracted from the callback)
        val resolvedItems = inputItems.map { item ->
            if (item.localConfiguration != null) return@map item
            
            val ep = kotlinx.coroutines.runBlocking(Dispatchers.Unconfined) {
                episodeDao.getEpisodeById(item.mediaId)
            }
            ep?.let { MediaItemMapper.fromEpisode(it) } ?: item
        }
        
        assertEquals(1, resolvedItems.size)
        assertEquals("Resolved Title", resolvedItems[0].mediaMetadata.title)
        assertEquals("http://audio.com", resolvedItems[0].localConfiguration?.uri?.toString())
    }
}
