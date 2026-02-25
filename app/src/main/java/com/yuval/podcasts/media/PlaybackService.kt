package com.yuval.podcasts.media

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
import kotlinx.coroutines.launch
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
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val mediaItem = player.currentMediaItem ?: return
                    val episodeId = mediaItem.mediaId
                    
                    serviceScope.launch {
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
                    }
                }
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}