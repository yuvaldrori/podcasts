package com.yuval.podcasts.media

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.db.entity.Episode
import java.util.Locale

object MediaItemMapper {
    fun inferAudioMimeType(urlOrPath: String?): String {
        if (urlOrPath.isNullOrBlank()) return MimeTypes.AUDIO_MPEG
        val cleanPath = urlOrPath.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
        return when {
            cleanPath.endsWith(".m4a") || cleanPath.endsWith(".mp4") -> MimeTypes.AUDIO_MP4
            cleanPath.endsWith(".aac") -> MimeTypes.AUDIO_AAC
            cleanPath.endsWith(".ogg") || cleanPath.endsWith(".oga") || cleanPath.endsWith(".opus") -> MimeTypes.AUDIO_OGG
            cleanPath.endsWith(".wav") -> MimeTypes.AUDIO_WAV
            cleanPath.endsWith(".flac") -> MimeTypes.AUDIO_FLAC
            else -> MimeTypes.AUDIO_MPEG
        }
    }

    fun fromEpisode(
        ep: Episode,
        podcastImageUrl: String? = null,
        podcastTitle: String? = null
    ): MediaItem? {
        return try {
            val uri = ep.playableUri.toUri()
            val artworkUrl = ep.imageUrl ?: podcastImageUrl
            val extras = Bundle().apply {
                if (ep.audioUrl.startsWith("http://", ignoreCase = true) ||
                    ep.audioUrl.startsWith("https://", ignoreCase = true)
                ) {
                    putString(Constants.EXTRA_REMOTE_AUDIO_URL, ep.audioUrl)
                }
            }
            val metadata = MediaMetadata.Builder()
                .setTitle(ep.title)
                .setArtist(podcastTitle ?: ep.podcastFeedUrl)
                .setAlbumTitle(podcastTitle)
                .setDisplayTitle(ep.title)
                .setArtworkUri(artworkUrl?.toUri())
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                .setExtras(extras)
                .build()

            val mimeType = inferAudioMimeType(ep.audioUrl.ifBlank { ep.playableUri })

            MediaItem.Builder()
                .setMediaId(ep.id)
                .setUri(uri)
                .setMimeType(mimeType)
                .setMediaMetadata(metadata)
                .build()
        } catch (e: Exception) {
            null
        }
    }
}
