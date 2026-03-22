package com.yuval.podcasts.media

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.yuval.podcasts.data.db.dao.QueueDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future

import androidx.media3.common.util.UnstableApi

class PlaybackResumptionCallback(
    private val queueDao: QueueDao,
    private val scope: CoroutineScope
) : MediaSession.Callback {

    @UnstableApi
    @Deprecated("Overriding deprecated Media3 member as part of current implementation")
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        return scope.future {
            try {
                val episodes = queueDao.getQueueEpisodes().first()
                if (episodes.isEmpty()) {
                    // Log gracefully instead of throwing, which silently breaks the MediaSession pipeline
                    android.util.Log.d("PlaybackResumption", "No items in queue to resume")
                    return@future MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
                }

                val currentEp = episodes.first()
                val mediaItems = episodes.mapNotNull { ep ->
                    try {
                        val uriStr = ep.localFilePath ?: ep.audioUrl
                        val uri = Uri.parse(uriStr)
                        
                        // Media3 requires MediaMetadata on resumption to populate the lock screen properly
                        val metadata = MediaMetadata.Builder()
                            .setTitle(ep.title)
                            .setArtist(ep.podcastFeedUrl) // Fallback since we don't store podcast title in Episode
                            .setDisplayTitle(ep.title)
                            .setArtworkUri(ep.imageUrl?.let { Uri.parse(it) })
                            .build()

                        MediaItem.Builder()
                            .setMediaId(ep.id)
                            .setUri(uri)
                            .setMediaMetadata(metadata)
                            .build()
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        android.util.Log.e("PlaybackResumption", "Failed to map episode ${ep.id}", e)
                        null // Skip malformed items rather than crashing the whole queue
                    }
                }

                if (mediaItems.isEmpty()) {
                    return@future MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
                }

                MediaSession.MediaItemsWithStartPosition(
                    mediaItems,
                    0, // Start at the first item in the queue
                    currentEp.lastPlayedPosition
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("PlaybackResumption", "Fatal error during resumption", e)
                // Return empty instead of throwing to prevent internal Media3 crash
                MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
            }
        }
    }
}
