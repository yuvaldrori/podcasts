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

import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import android.view.KeyEvent

import kotlinx.coroutines.withContext

import androidx.media3.common.MediaMetadata

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    @Inject lateinit var episodeDao: EpisodeDao
    @Inject lateinit var queueDao: QueueDao
    @Inject lateinit var removeEpisodeUseCase: RemoveEpisodeUseCase

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val mediaSessionCallback = object : MediaSession.Callback {
        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            val ke = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            if (ke != null && ke.action == KeyEvent.ACTION_DOWN) {
                when (ke.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        // Double tap on many headphones
                        seekForward()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        // Triple tap on many headphones
                        seekBackward()
                        return true
                    }
                }
            }
            return super.onMediaButtonEvent(session, controllerInfo, intent)
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            return PlaybackResumptionCallback(queueDao, serviceScope)
                .onPlaybackResumption(mediaSession, controller)
        }
    }

    private fun seekForward(ms: Long = 30000L) {
        val newPosition = (player.currentPosition + ms).coerceAtMost(player.duration.coerceAtLeast(0L))
        player.seekTo(newPosition)
    }

    private fun seekBackward(ms: Long = 15000L) {
        val newPosition = (player.currentPosition - ms).coerceAtLeast(0L)
        player.seekTo(newPosition)
    }

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
            .setCallback(mediaSessionCallback)
            .build()

        var currentlyPlayingId: String? = null

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    saveCurrentPosition()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
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
                
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && prevId != null) {
                    serviceScope.launch {
                        removeEpisodeUseCase(prevId, markAsPlayed = true)
                    }
                }
            }
        })

        observeQueue()

        serviceScope.launch(Dispatchers.Main) {
            while (true) {
                kotlinx.coroutines.delay(15000)
                if (player.isPlaying) {
                    saveCurrentPosition()
                }
            }
        }
    }

    private fun observeQueue() {
        serviceScope.launch {
            queueDao.getQueueEpisodes().collect { episodes ->
                withContext(Dispatchers.Main) {
                    val currentMediaId = player.currentMediaItem?.mediaId ?: return@withContext
                    
                    val currentInNewIndex = episodes.indexOfFirst { it.id == currentMediaId }
                    if (currentInNewIndex == -1) return@withContext

                    val currentIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
                    val newIds = episodes.map { it.id }
                    if (currentIds == newIds) {
                        return@withContext
                    }

                    val currentInPlayerIndex = player.currentMediaItemIndex

                    // Rebuild the ExoPlayer playlist around the currently playing item
                    // to ensure seamless playback without resetting position
                    if (currentInPlayerIndex < player.mediaItemCount - 1) {
                        player.removeMediaItems(currentInPlayerIndex + 1, player.mediaItemCount)
                    }
                    if (currentInPlayerIndex > 0) {
                        player.removeMediaItems(0, currentInPlayerIndex)
                    }

                    val newMediaItems = episodes.map { ep ->
                        val uri = android.net.Uri.parse(ep.localFilePath ?: ep.audioUrl)
                        val metadata = MediaMetadata.Builder()
                            .setTitle(ep.title)
                            .setArtist(ep.podcastFeedUrl)
                            .setDisplayTitle(ep.title)
                            .setArtworkUri(ep.imageUrl?.let { android.net.Uri.parse(it) })
                            .build()
                        MediaItem.Builder()
                            .setMediaId(ep.id)
                            .setUri(uri)
                            .setMediaMetadata(metadata)
                            .build()
                    }

                    val beforeItems = newMediaItems.take(currentInNewIndex)
                    if (beforeItems.isNotEmpty()) {
                        player.addMediaItems(0, beforeItems)
                    }
                    
                    val afterItems = newMediaItems.drop(currentInNewIndex + 1)
                    if (afterItems.isNotEmpty()) {
                        player.addMediaItems(currentInNewIndex + 1, afterItems)
                    }
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