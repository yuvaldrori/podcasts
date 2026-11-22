package com.yuval.podcasts.media

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
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

    fun buildMediaItem(
        mediaId: String,
        playableUri: String,
        remoteAudioUrl: String? = null,
        title: String? = null,
        artist: String? = null,
        albumTitle: String? = null,
        artworkUrl: String? = null
    ): MediaItem {
        val extras = Bundle().apply {
            val remote = remoteAudioUrl ?: playableUri
            if (remote.startsWith("http://", ignoreCase = true) ||
                remote.startsWith("https://", ignoreCase = true)
            ) {
                putString(Constants.EXTRA_REMOTE_AUDIO_URL, remote)
            }
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setDisplayTitle(title)
            .setArtist(artist)
            .setAlbumTitle(albumTitle)
            .setArtworkUri(artworkUrl?.toUri())
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .setExtras(extras)
            .build()

        val mimeType = inferAudioMimeType(remoteAudioUrl?.ifBlank { playableUri } ?: playableUri)

        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(playableUri.toUri())
            .setMimeType(mimeType)
            .setMediaMetadata(metadata)
            .build()
    }

    fun fromEpisode(
        ep: Episode,
        podcastImageUrl: String? = null,
        podcastTitle: String? = null
    ): MediaItem? {
        return try {
            buildMediaItem(
                mediaId = ep.id,
                playableUri = ep.resolvePlayableUri(),
                remoteAudioUrl = ep.audioUrl,
                title = ep.title,
                artist = podcastTitle ?: ep.podcastFeedUrl,
                albumTitle = podcastTitle,
                artworkUrl = ep.imageUrl ?: podcastImageUrl
            )
        } catch (_: Exception) {
            null
        }
    }

    fun fromEpisodeWithPodcast(item: EpisodeWithPodcast): MediaItem? =
        fromEpisode(item.episode, item.podcast.imageUrl, item.podcast.title)
}
