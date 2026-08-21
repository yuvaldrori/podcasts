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
    fun testFromEpisodeWithLocalFileExists() {
        val tempFile = java.io.File.createTempFile("temp_audio", ".mp3").apply { deleteOnExit() }
        val episode = Episode(
            id = "test-id",
            podcastFeedUrl = "https://feed.url",
            title = "Test Episode",
            description = "Description",
            audioUrl = "https://audio.url",
            pubDate = 123456789L,
            duration = 3600L,
            imageUrl = "https://image.url",
            localFilePath = tempFile.absolutePath,
            downloadStatus = 2
        )

        val mediaItem = MediaItemMapper.fromEpisode(episode)
        
        assertNotNull(mediaItem)
        assertEquals("test-id", mediaItem?.mediaId)
        assertEquals(tempFile.absolutePath, mediaItem?.requestMetadata?.mediaUri?.toString() ?: mediaItem?.localConfiguration?.uri?.toString())
        assertEquals("Test Episode", mediaItem?.mediaMetadata?.title)
        assertEquals("https://feed.url", mediaItem?.mediaMetadata?.artist)
        assertEquals("https://image.url", mediaItem?.mediaMetadata?.artworkUri?.toString())
        
        tempFile.delete()
    }

    @Test
    fun testFromEpisodeWithLocalFileMissingFallback() {
        val episode = Episode(
            id = "test-id",
            podcastFeedUrl = "https://feed.url",
            title = "Test Episode",
            description = "Description",
            audioUrl = "https://audio.url",
            pubDate = 123456789L,
            duration = 3600L,
            imageUrl = "https://image.url",
            localFilePath = "/nonexistent/path/audio.mp3",
            downloadStatus = 2
        )

        val mediaItem = MediaItemMapper.fromEpisode(episode)
        
        assertNotNull(mediaItem)
        assertEquals("test-id", mediaItem?.mediaId)
        assertEquals("https://audio.url", mediaItem?.requestMetadata?.mediaUri?.toString() ?: mediaItem?.localConfiguration?.uri?.toString())
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
        assertEquals("https://audio.url", mediaItem?.requestMetadata?.mediaUri?.toString() ?: mediaItem?.localConfiguration?.uri?.toString())
        assertEquals(null, mediaItem?.mediaMetadata?.artworkUri)
    }

    @Test
    fun testFromEpisodeWithFallbackToPodcastArtwork() {
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

        val mediaItem = MediaItemMapper.fromEpisode(episode, podcastImageUrl = "https://podcast.image.url")
        
        assertNotNull(mediaItem)
        assertEquals("test-id", mediaItem?.mediaId)
        assertEquals("https://podcast.image.url", mediaItem?.mediaMetadata?.artworkUri?.toString())
    }

    @Test
    fun testFromEpisodeEpisodeArtworkTakesPrecedenceOverPodcastArtwork() {
        val episode = Episode(
            id = "test-id",
            podcastFeedUrl = "https://feed.url",
            title = "Test Episode",
            description = "Description",
            audioUrl = "https://audio.url",
            pubDate = 123456789L,
            duration = 3600L,
            imageUrl = "https://episode.image.url",
            localFilePath = null,
            downloadStatus = 0
        )

        val mediaItem = MediaItemMapper.fromEpisode(episode, podcastImageUrl = "https://podcast.image.url")
        
        assertNotNull(mediaItem)
        assertEquals("test-id", mediaItem?.mediaId)
        assertEquals("https://episode.image.url", mediaItem?.mediaMetadata?.artworkUri?.toString())
    }
}
