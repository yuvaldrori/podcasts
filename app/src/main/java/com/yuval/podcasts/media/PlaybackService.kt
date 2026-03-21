package com.yuval.podcasts.media

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.yuval.podcasts.MainActivity
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.QueueDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import com.yuval.podcasts.domain.usecase.RemoveEpisodeUseCase
import com.yuval.podcasts.data.repository.SettingsRepository
import com.yuval.podcasts.di.IoDispatcher
import com.yuval.podcasts.di.MainDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

import androidx.core.content.IntentCompat
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

    @Inject lateinit var exoPlayer: ExoPlayer
    @Inject lateinit var castPlayer: CastPlayer
    private lateinit var currentPlayer: Player

    @Inject lateinit var episodeDao: EpisodeDao
    @Inject lateinit var queueDao: QueueDao
    @Inject lateinit var removeEpisodeUseCase: RemoveEpisodeUseCase
    @Inject lateinit var settingsRepository: SettingsRepository
    
    @Inject @IoDispatcher lateinit var ioDispatcher: CoroutineDispatcher
    @Inject @MainDispatcher lateinit var mainDispatcher: CoroutineDispatcher

    private var mediaSession: MediaSession? = null
    private lateinit var serviceScope: CoroutineScope

    private val mediaSessionCallback = object : MediaSession.Callback {
        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            val keyEvent = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            if (keyEvent?.action == KeyEvent.ACTION_DOWN) {
                when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        seekForward()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
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
        val newPosition = (currentPlayer.currentPosition + ms).coerceAtMost(currentPlayer.duration.coerceAtLeast(0L))
        currentPlayer.seekTo(newPosition)
    }

    private fun seekBackward(ms: Long = 15000L) {
        val newPosition = (currentPlayer.currentPosition - ms).coerceAtLeast(0L)
        currentPlayer.seekTo(newPosition)
    }

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(ioDispatcher + SupervisorJob())
        
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
            
        exoPlayer.setAudioAttributes(audioAttributes, true)
        exoPlayer.setHandleAudioBecomingNoisy(true)
        
        val defaultSpeed = settingsRepository.getPlaybackSpeed()
        exoPlayer.setPlaybackParameters(androidx.media3.common.PlaybackParameters(defaultSpeed))
        
        currentPlayer = exoPlayer
            
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
            
        mediaSession = MediaSession.Builder(this, currentPlayer)
            .setSessionActivity(pendingIntent)
            .setCallback(mediaSessionCallback)
            .build()

        castPlayer.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() {
                setCurrentPlayer(castPlayer)
            }
            override fun onCastSessionUnavailable() {
                setCurrentPlayer(exoPlayer)
            }
        })

        var currentlyPlayingId: String? = null

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    saveCurrentPosition()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val lastId = currentlyPlayingId
                    if (lastId != null) {
                        serviceScope.launch(ioDispatcher) {
                            removeEpisodeUseCase(lastId, markAsPlayed = true)
                        }
                        currentlyPlayingId = null
                    }
                }
            }
            
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val lastId = currentlyPlayingId
                if (lastId != null && mediaItem != null && lastId != mediaItem.mediaId && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    serviceScope.launch(ioDispatcher) {
                         removeEpisodeUseCase(lastId, markAsPlayed = true)
                    }
                }
                if (mediaItem != null) {
                    currentlyPlayingId = mediaItem.mediaId
                }
            }
        }
        
        exoPlayer.addListener(listener)
        castPlayer.addListener(listener)

        observeQueue()

        serviceScope.launch(mainDispatcher) {
            while (true) {
                kotlinx.coroutines.delay(15000)
                if (currentPlayer.isPlaying) {
                    saveCurrentPosition()
                }
            }
        }
    }
    
    private fun setCurrentPlayer(newPlayer: Player) {
        if (currentPlayer == newPlayer) return
        
        val previousPlayer = currentPlayer
        val playbackState = previousPlayer.playbackState
        
        // Transfer state if applicable
        if (playbackState != Player.STATE_ENDED && playbackState != Player.STATE_IDLE) {
            val windowIndex = previousPlayer.currentMediaItemIndex
            val positionMs = previousPlayer.currentPosition
            val isPlaying = previousPlayer.isPlaying
            
            previousPlayer.pause()
            previousPlayer.clearMediaItems()
            
            // Note: In a complete implementation, we'd transfer the full media items list here.
            // Since our queue logic is reactive, we might need a small delay or rely on observeQueue 
            // rebuilding it, but transferring the active item helps cast.
        }
        
        currentPlayer = newPlayer
        mediaSession?.player = newPlayer
    }

    private fun observeQueue() {
        serviceScope.launch {
            queueDao.getQueueEpisodes().collect { episodes ->
                withContext(mainDispatcher) {
                    val currentMediaId = currentPlayer.currentMediaItem?.mediaId ?: return@withContext
                    
                    val currentInNewIndex = episodes.indexOfFirst { it.id == currentMediaId }
                    if (currentInNewIndex == -1) return@withContext

                    val currentIds = (0 until currentPlayer.mediaItemCount).map { currentPlayer.getMediaItemAt(it).mediaId }
                    val newIds = episodes.map { it.id }
                    if (currentIds == newIds) {
                        return@withContext
                    }

                    val currentInPlayerIndex = currentPlayer.currentMediaItemIndex

                    // Rebuild the playlist around the currently playing item
                    if (currentInPlayerIndex < currentPlayer.mediaItemCount - 1) {
                        currentPlayer.removeMediaItems(currentInPlayerIndex + 1, currentPlayer.mediaItemCount)
                    }
                    if (currentInPlayerIndex > 0) {
                        currentPlayer.removeMediaItems(0, currentInPlayerIndex)
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
                        currentPlayer.addMediaItems(0, beforeItems)
                    }
                    
                    val afterItems = newMediaItems.drop(currentInNewIndex + 1)
                    if (afterItems.isNotEmpty()) {
                        currentPlayer.addMediaItems(currentInNewIndex + 1, afterItems)
                    }
                }
            }
        }
    }

    private fun saveCurrentPosition() {
        val mediaId = currentPlayer.currentMediaItem?.mediaId ?: return
        val position = currentPlayer.currentPosition
        serviceScope.launch(ioDispatcher) {
            episodeDao.updateLastPlayedPosition(mediaId, position)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            exoPlayer.release()
            castPlayer.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
