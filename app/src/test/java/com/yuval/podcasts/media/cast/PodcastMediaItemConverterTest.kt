package com.yuval.podcasts.media.cast

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.cast.MediaMetadata as CastMediaMetadata
import com.yuval.podcasts.data.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PodcastMediaItemConverterTest {

    private val converter = PodcastMediaItemConverter()

    @Test
    fun toMediaQueueItem_withLocalFileAndRemoteUrlExtra_usesRemoteUrlForCastContentUrl() {
        val localPath = "/data/user/0/com.yuval.podcasts/files/podcasts/episode_123.mp3"
        val remoteUrl = "https://cdn.example.com/podcasts/episode_123.mp3"
        val extras = Bundle().apply {
            putString(Constants.EXTRA_REMOTE_AUDIO_URL, remoteUrl)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle("Test Episode")
            .setArtist("Test Podcast")
            .setAlbumTitle("Test Podcast")
            .setArtworkUri("https://cdn.example.com/art.jpg".toUri())
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .setExtras(extras)
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId("ep-123")
            .setUri(localPath.toUri())
            .setMimeType(MimeTypes.AUDIO_MPEG)
            .setMediaMetadata(metadata)
            .build()

        val queueItem = converter.toMediaQueueItem(mediaItem)
        val mediaInfo = queueItem.media

        assertNotNull(mediaInfo)
        assertEquals(remoteUrl, mediaInfo?.contentUrl)
        assertEquals(MimeTypes.AUDIO_MPEG, mediaInfo?.contentType)
        assertEquals(CastMediaMetadata.MEDIA_TYPE_MUSIC_TRACK, mediaInfo?.metadata?.mediaType)
    }

    @Test
    fun toMediaItem_roundTrip_restoresLocalFileUriAndRemoteUrlExtra() {
        val localPath = "/data/user/0/com.yuval.podcasts/files/podcasts/episode_123.mp3"
        val remoteUrl = "https://cdn.example.com/podcasts/episode_123.mp3"
        val extras = Bundle().apply {
            putString(Constants.EXTRA_REMOTE_AUDIO_URL, remoteUrl)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle("Test Episode")
            .setArtist("Test Podcast")
            .setAlbumTitle("Test Podcast")
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .setExtras(extras)
            .build()

        val originalItem = MediaItem.Builder()
            .setMediaId("ep-123")
            .setUri(localPath.toUri())
            .setMimeType(MimeTypes.AUDIO_MPEG)
            .setMediaMetadata(metadata)
            .build()

        val queueItem = converter.toMediaQueueItem(originalItem)
        val restoredItem = converter.toMediaItem(queueItem)

        assertEquals("ep-123", restoredItem.mediaId)
        assertEquals(localPath, restoredItem.localConfiguration?.uri?.toString())
        assertEquals(MimeTypes.AUDIO_MPEG, restoredItem.localConfiguration?.mimeType)
        assertEquals(
            remoteUrl,
            restoredItem.mediaMetadata.extras?.getString(Constants.EXTRA_REMOTE_AUDIO_URL)
        )
        assertEquals(
            MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE,
            restoredItem.mediaMetadata.mediaType
        )
    }

    @Test
    fun toMediaQueueItem_withNullMimeType_infersAudioMimeTypeFromUrl() {
        val remoteUrl = "https://cdn.example.com/podcasts/episode_123.m4a?token=abc"
        val metadata = MediaMetadata.Builder()
            .setTitle("M4A Episode")
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId("ep-m4a")
            .setUri(remoteUrl.toUri())
            .setMediaMetadata(metadata)
            .build()

        val queueItem = converter.toMediaQueueItem(mediaItem)
        val mediaInfo = queueItem.media

        assertNotNull(mediaInfo)
        assertEquals(remoteUrl, mediaInfo?.contentUrl)
        assertEquals(MimeTypes.AUDIO_MP4, mediaInfo?.contentType)
        assertEquals(CastMediaMetadata.MEDIA_TYPE_MUSIC_TRACK, mediaInfo?.metadata?.mediaType)
    }
}
