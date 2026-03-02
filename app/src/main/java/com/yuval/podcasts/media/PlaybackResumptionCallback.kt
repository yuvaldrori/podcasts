package com.yuval.podcasts.media

import androidx.media3.common.MediaItem
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.yuval.podcasts.data.db.dao.QueueDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future

class PlaybackResumptionCallback(
    private val queueDao: QueueDao,
    private val scope: CoroutineScope
) : MediaSession.Callback {

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        return scope.future {
            val episodes = queueDao.getQueueEpisodes().first()
            if (episodes.isEmpty()) {
                throw UnsupportedOperationException("No items in queue to resume")
            }

            val currentEp = episodes.first()
            val mediaItems = episodes.map { ep ->
                val uri = ep.localFilePath ?: ep.audioUrl
                MediaItem.Builder()
                    .setMediaId(ep.id)
                    .setUri(uri)
                    .build()
            }

            MediaSession.MediaItemsWithStartPosition(
                mediaItems,
                0, // Start at the first item in the queue
                currentEp.lastPlayedPosition
            )
        }
    }
}
