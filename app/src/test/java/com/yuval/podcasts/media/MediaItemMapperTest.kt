package com.yuval.podcasts.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuval.podcasts.data.db.entity.Episode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MediaItemMapperTest {

    @Test
    fun testFromEpisode() {
        val episode = Episode(
            id = "test-id",
            podcastFeedUrl = "https://feed.url",
            title = "Test Episode",
            description = "Description",
            audioUrl = "https://audio.url",
            pubDate = 123456789L,
            duration = 3600L,
            imageUrl = "https://image.url",
            localFilePath = "/local/path/audio.mp3",
            downloadStatus = 2
        )

        val mediaItem = MediaItemMapper.fromEpisode(episode)
        
        assertNotNull(mediaItem)
        assertEquals("test-id", mediaItem?.mediaId)
        // Media3 Uri handling might vary, checking display title or other metadata is often more reliable
        assertEquals("Test Episode", mediaItem?.mediaMetadata?.title)
        assertEquals("https://feed.url", mediaItem?.mediaMetadata?.artist)
        assertEquals("https://image.url", mediaItem?.mediaMetadata?.artworkUri?.toString())
    }

    @Test
    fun testFromEpisodeNoLocalFile() {
        val episode = Episode(
            id = "test-id",
            podcastFeedUrl = "https://feed.url",
            title = "Test Episode",
            description = "Description",
            audioUrl = "https://audio.url",
            pubDate = 123456789L,
            duration = 3600L,
            imageUrl = null,
            localFilePath = null,
            downloadStatus = 0
        )

        val mediaItem = MediaItemMapper.fromEpisode(episode)
        
        assertNotNull(mediaItem)
        assertEquals("test-id", mediaItem?.mediaId)
    }
}
