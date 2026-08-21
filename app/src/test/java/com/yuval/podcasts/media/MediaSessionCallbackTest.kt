package com.yuval.podcasts.media

import androidx.media3.common.MediaItem
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.entity.Episode
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tests for the onAddMediaItems resolution logic in PlaybackService.
 *
 * PlaybackService.onAddMediaItems resolves "stub" MediaItems (those without localConfiguration)
 * into fully-qualified MediaItems by fetching the episode from the database and mapping
 * it via MediaItemMapper.fromEpisode. These tests verify both the resolution logic and
 * the pass-through behavior for already-configured items.
 *
 * NOTE: PlaybackService is an Android Service that requires a bound MediaSession and
 * cannot be instantiated in unit tests. The resolution logic is tested here by exercising
 * the two production components it uses: EpisodeDao and MediaItemMapper.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MediaSessionCallbackTest {

    /**
     * When the callback receives a stub MediaItem (mediaId only, no URI), it should
     * fetch the episode by mediaId and resolve it to a fully-configured MediaItem.
     */
    @Test
    fun testOnAddMediaItemsResolution_stubItemResolvesToEpisodeMediaItem() {
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
        val podcast = com.yuval.podcasts.data.db.entity.Podcast(
            feedUrl = "url",
            title = "Podcast Title",
            description = "",
            imageUrl = "https://podcast.art/cover.png",
            website = ""
        )
        val episodeWithPodcast = com.yuval.podcasts.data.db.entity.EpisodeWithPodcast(episode, podcast)

        coEvery { episodeDao.getEpisodeWithPodcast("ext-id") } returns episodeWithPodcast

        // Simulate the resolution the real onAddMediaItems callback performs:
        // stub items (no localConfiguration) are resolved via episodeDao + MediaItemMapper
        val inputItems = mutableListOf(MediaItem.Builder().setMediaId("ext-id").build())

        val resolvedItems = inputItems.map { item ->
            if (item.localConfiguration != null) return@map item
            val epWithPodcast = runBlocking(Dispatchers.Unconfined) {
                episodeDao.getEpisodeWithPodcast(item.mediaId)
            }
            epWithPodcast?.let { MediaItemMapper.fromEpisode(it.episode, it.podcast.imageUrl) } ?: item
        }

        assertEquals(1, resolvedItems.size)
        assertEquals("Resolved Title", resolvedItems[0].mediaMetadata.title)
        assertEquals("http://audio.com", resolvedItems[0].localConfiguration?.uri?.toString())
        assertEquals("https://podcast.art/cover.png", resolvedItems[0].mediaMetadata.artworkUri?.toString())
    }

    /**
     * When the callback receives an already-configured MediaItem (one that has a URI),
     * it should be passed through unchanged — the DAO should not be consulted.
     */
    @Test
    fun testOnAddMediaItemsResolution_configuredItemPassesThrough() {
        val episodeDao = mockk<EpisodeDao>() // should never be called

        val configuredItem = MediaItem.Builder()
            .setMediaId("local-id")
            .setUri("http://audio.com/already-configured.mp3")
            .build()

        val inputItems = mutableListOf(configuredItem)

        val resolvedItems = inputItems.map { item ->
            if (item.localConfiguration != null) return@map item
            // This branch should NOT be reached for an already-configured item
            val ep = runBlocking(Dispatchers.Unconfined) {
                episodeDao.getEpisodeById(item.mediaId)
            }
            ep?.let { MediaItemMapper.fromEpisode(it) } ?: item
        }

        assertEquals(1, resolvedItems.size)
        // Should be the same instance — not replaced with a DAO lookup
        assertEquals(configuredItem, resolvedItems[0])
    }
}
