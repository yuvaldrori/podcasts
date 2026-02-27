package com.yuval.podcasts.media

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.QueueDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    @Inject lateinit var episodeDao: EpisodeDao
    @Inject lateinit var queueDao: QueueDao

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    saveCurrentPosition()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val mediaItem = player.currentMediaItem ?: return
                    val episodeId = mediaItem.mediaId
                    
                    serviceScope.launch {
                        // Mark as played and reset position
                        episodeDao.updatePlaybackStatus(episodeId, true)
                        episodeDao.updateLastPlayedPosition(episodeId, 0L)

                        // Delete local file and remove from queue
                        val episode = episodeDao.getEpisodeById(episodeId)
                        episode?.localFilePath?.let { path ->
                            val file = File(path)
                            if (file.exists()) {
                                file.delete()
                            }
                        }
                        
                        // Update status to 0 (Not Downloaded) and clear path
                        episodeDao.updateDownloadStatus(episodeId, 0, null)
                        
                        // Remove from Queue
                        queueDao.removeFromQueue(episodeId)
                        
                        // Auto-play next
                        val nextEpisode = queueDao.getNextEpisode()
                        if (nextEpisode != null) {
                            withContext(Dispatchers.Main) {
                                val uri = nextEpisode.localFilePath ?: nextEpisode.audioUrl
                                val nextMediaItem = MediaItem.Builder()
                                    .setMediaId(nextEpisode.id)
                                    .setUri(uri)
                                    .build()
                                player.setMediaItem(nextMediaItem)
                                if (nextEpisode.lastPlayedPosition > 0) {
                                    player.seekTo(nextEpisode.lastPlayedPosition)
                                }
                                player.prepare()
                                player.play()
                            }
                        }
                    }
                }
            }
        })

        // Periodic position saving
        serviceScope.launch(Dispatchers.Main) {
            while (true) {
                kotlinx.coroutines.delay(15000)
                if (player.isPlaying) {
                    saveCurrentPosition()
                }
            }
        }
    }

    private fun saveCurrentPosition() {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        val position = player.currentPosition
        serviceScope.launch(Dispatchers.IO) {
            episodeDao.updateLastPlayedPosition(mediaId, position)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}