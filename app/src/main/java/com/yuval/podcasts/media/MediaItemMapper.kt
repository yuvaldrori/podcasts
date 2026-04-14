package com.yuval.podcasts.media

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.yuval.podcasts.data.db.entity.Episode

object MediaItemMapper {
    fun fromEpisode(ep: Episode): MediaItem? {
        return try {
            val uri = (ep.localFilePath ?: ep.audioUrl).toUri()
            val metadata = MediaMetadata.Builder()
                .setTitle(ep.title)
                .setArtist(ep.podcastFeedUrl)
                .setDisplayTitle(ep.title)
                .setArtworkUri(ep.imageUrl?.toUri())
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
