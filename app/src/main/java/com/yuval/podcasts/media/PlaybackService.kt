package com.yuval.podcasts.media

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.yuval.podcasts.MainActivity
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.QueueDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import com.yuval.podcasts.domain.usecase.RemoveEpisodeUseCase
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    @Inject lateinit var episodeDao: EpisodeDao
    @Inject lateinit var queueDao: QueueDao
    @Inject lateinit var removeEpisodeUseCase: RemoveEpisodeUseCase

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
            
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
            
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(PlaybackResumptionCallback(queueDao, serviceScope))
            .build()

        var currentlyPlayingId: String? = null

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    saveCurrentPosition()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                // If the entire playlist finishes (the last item ends), there is no 'transition' to a new item.
                // We must catch STATE_ENDED to remove the final episode from the queue.
                if (playbackState == Player.STATE_ENDED) {
                    val lastId = currentlyPlayingId
                    if (lastId != null) {
                        serviceScope.launch {
                            removeEpisodeUseCase(lastId, markAsPlayed = true)
                        }
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val prevId = currentlyPlayingId
                currentlyPlayingId = mediaItem?.mediaId
                
                // If it transitioned automatically because the track ended, mark the previous as played
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && prevId != null) {
                    serviceScope.launch {
                        // Mark as played, reset position, delete file and remove from queue
                        removeEpisodeUseCase(prevId, markAsPlayed = true)
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