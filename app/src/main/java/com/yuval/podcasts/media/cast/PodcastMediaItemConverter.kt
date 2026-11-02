package com.yuval.podcasts.media.cast

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.MediaQueueItem
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.media.MediaItemMapper

@UnstableApi
class PodcastMediaItemConverter(
    private val defaultConverter: DefaultMediaItemConverter = DefaultMediaItemConverter()
) : MediaItemConverter {

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val localUri = mediaItem.localConfiguration?.uri?.toString()
        val remoteAudioUrl = mediaItem.mediaMetadata.extras
            ?.getString(Constants.EXTRA_REMOTE_AUDIO_URL)
            ?.takeIf {
                it.startsWith("http://", ignoreCase = true) ||
                    it.startsWith("https://", ignoreCase = true)
            }

        val castUrl = remoteAudioUrl ?: localUri
        val mimeType = mediaItem.localConfiguration?.mimeType
            ?: MediaItemMapper.inferAudioMimeType(castUrl)

        val castMetadata = mediaItem.mediaMetadata.buildUpon()
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .build()

        val castItemBuilder = mediaItem.buildUpon()
            .setMimeType(mimeType)
            .setMediaMetadata(castMetadata)

        if (castUrl != null) {
            castItemBuilder.setUri(castUrl.toUri())
        }

        val queueItem = defaultConverter.toMediaQueueItem(castItemBuilder.build())
        queueItem.media?.customData?.apply {
            if (localUri != null) {
                put(KEY_LOCAL_URI, localUri)
            }
            if (remoteAudioUrl != null) {
                put(KEY_REMOTE_AUDIO_URL, remoteAudioUrl)
            }
        }
        return queueItem
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        val baseItem = defaultConverter.toMediaItem(mediaQueueItem)
        val customData = mediaQueueItem.media?.customData
        val localUri = customData?.optString(KEY_LOCAL_URI)?.takeIf { it.isNotEmpty() }
        val remoteAudioUrl = customData?.optString(KEY_REMOTE_AUDIO_URL)?.takeIf { it.isNotEmpty() }

        val extras = Bundle(baseItem.mediaMetadata.extras ?: Bundle.EMPTY).apply {
            if (remoteAudioUrl != null) {
                putString(Constants.EXTRA_REMOTE_AUDIO_URL, remoteAudioUrl)
            }
        }

        val updatedMetadata = baseItem.mediaMetadata.buildUpon()
            .setDisplayTitle(baseItem.mediaMetadata.title)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .setExtras(extras)
            .build()

        val itemBuilder = baseItem.buildUpon()
            .setMediaMetadata(updatedMetadata)

        if (localUri != null) {
            itemBuilder.setUri(localUri.toUri())
        }

        return itemBuilder.build()
    }

    companion object {
        private const val KEY_LOCAL_URI = "podcastLocalUri"
        private const val KEY_REMOTE_AUDIO_URL = "podcastRemoteAudioUrl"
    }
}
