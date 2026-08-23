package com.yuval.podcasts.media

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.yuval.podcasts.data.db.entity.Episode

object MediaItemMapper {
    fun fromEpisode(
        ep: Episode,
        podcastImageUrl: String? = null,
        podcastTitle: String? = null
    ): MediaItem? {
        return try {
            val uri = ep.playableUri.toUri()
            val artworkUrl = ep.imageUrl ?: podcastImageUrl
            val metadata = MediaMetadata.Builder()
                .setTitle(ep.title)
                .setArtist(podcastTitle ?: ep.podcastFeedUrl)
                .setAlbumTitle(podcastTitle)
                .setDisplayTitle(ep.title)
                .setArtworkUri(artworkUrl?.toUri())
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST)
                .build()
            
            MediaItem.Builder()
                .setMediaId(ep.id)
                .setUri(uri)
                .setMediaMetadata(metadata)
                .build()
        } catch (e: Exception) {
            null
        }
    }
}
