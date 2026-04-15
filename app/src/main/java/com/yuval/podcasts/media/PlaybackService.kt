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
import androidx.media3.session.CommandButton
import android.os.Bundle
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.yuval.podcasts.MainActivity
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.QueueDao
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.media3.common.MediaMetadata
import com.yuval.podcasts.data.Constants
import kotlinx.coroutines.guava.asListenableFuture

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
    private var lastResumedId: String? = null
    private lateinit var serviceScope: CoroutineScope

    private val mediaSessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand("REWIND_10", Bundle.EMPTY))
                .add(SessionCommand("SKIP_30", Bundle.EMPTY))
                .build()
            
            return MediaSession.ConnectionResult.accept(
                sessionCommands,
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
            )
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            val customLayout = listOf(
                CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                    .setDisplayName("Rewind 10s")
                    .setCustomIconResId(android.R.drawable.ic_media_rew)
                    .setSessionCommand(SessionCommand("REWIND_10", Bundle.EMPTY))
                    .build(),
                CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                    .setDisplayName("Skip 30s")
                    .setCustomIconResId(android.R.drawable.ic_media_ff)
                    .setSessionCommand(SessionCommand("SKIP_30", Bundle.EMPTY))
                    .build()
            )
            mediaSession?.setCustomLayout(controller, customLayout)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                "REWIND_10" -> seekBackward(10000L)
                "SKIP_30" -> seekForward(30000L)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

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

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            return serviceScope.async(ioDispatcher) {
                val resolvedItems = mediaItems.map { item ->
                    if (item.localConfiguration != null) return@map item
                    
                    val episode = episodeDao.getEpisodeById(item.mediaId)
                    episode?.let { MediaItemMapper.fromEpisode(it) } ?: item
                }.toMutableList()
                resolvedItems
            }.asListenableFuture()
        }
    }

    private fun seekForward(ms: Long = Constants.SEEK_FORWARD_MS) {
        val newPosition = (currentPlayer.currentPosition + ms).coerceAtMost(currentPlayer.duration.coerceAtLeast(0L))
        currentPlayer.seekTo(newPosition)
    }

    private fun seekBackward(ms: Long = Constants.SEEK_BACKWARD_MS) {
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
        exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
        castPlayer.repeatMode = Player.REPEAT_MODE_OFF
        
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

        @Suppress("DEPRECATION")
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
                    android.util.Log.d("PlaybackService", "Playback ended. lastId=$lastId")
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
                android.util.Log.d("PlaybackService", "Media item transition. lastId=$lastId, newMediaId=${mediaItem?.mediaId}, reason=$reason")
                
                if (lastId != null && mediaItem != null && lastId != mediaItem.mediaId && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    serviceScope.launch(ioDispatcher) {
                         removeEpisodeUseCase(lastId, markAsPlayed = true)
                    }
                }

                if (mediaItem != null) {
                    currentlyPlayingId = mediaItem.mediaId
                    
                    // If it's an automatic transition or a skip, check for saved position to resume
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                        if (lastResumedId != mediaItem.mediaId) {
                            lastResumedId = mediaItem.mediaId
                            serviceScope.launch(ioDispatcher) {
                                val episode = episodeDao.getEpisodeById(mediaItem.mediaId)
                                if (episode != null && episode.lastPlayedPosition > 0) {
                                    withContext(mainDispatcher) {
                                        // Only seek if we are at the start (prevent fighting manual seeks)
                                        if (currentPlayer.currentPosition < 2000) {
                                            currentPlayer.seekTo(episode.lastPlayedPosition)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        exoPlayer.addListener(listener)
        castPlayer.addListener(listener)

        // Live Silence Toggle
        serviceScope.launch(mainDispatcher) {
            settingsRepository.skipSilenceFlow().collect { enabled ->
                exoPlayer.skipSilenceEnabled = enabled
            }
        }

        // Initialize state from queue for playback resumption
        serviceScope.launch(mainDispatcher) {
            val episodes = queueDao.getQueueEpisodes().first()
            if (episodes.isNotEmpty() && currentPlayer.mediaItemCount == 0) {
                val currentEp = episodes.first()
                val mediaItems = episodes.mapNotNull { ep ->
                    MediaItemMapper.fromEpisode(ep)
                }
                if (mediaItems.isNotEmpty()) {
                    currentPlayer.setMediaItems(mediaItems)
                    currentPlayer.seekTo(0, currentEp.lastPlayedPosition)
                    currentPlayer.prepare()
                }
            }
        }

        observeQueue()

        serviceScope.launch(mainDispatcher) {
            while (true) {
                kotlinx.coroutines.delay(Constants.SAVE_POSITION_INTERVAL_MS)
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
            previousPlayer.pause()
            previousPlayer.clearMediaItems()
        }
        
        currentPlayer = newPlayer
        mediaSession?.player = newPlayer
    }

    private fun observeQueue() {
        serviceScope.launch {
            queueDao.getQueueEpisodes().distinctUntilChanged().collect { episodes ->
                withContext(mainDispatcher) {
                    if (episodes.isEmpty()) {
                        if (currentPlayer.mediaItemCount > 0) {
                            currentPlayer.stop()
                            currentPlayer.clearMediaItems()
                        }
                        return@withContext
                    }

                    val currentMediaId = currentPlayer.currentMediaItem?.mediaId
                    
                    if (currentMediaId == null) {
                        // If nothing is playing, just set the items
                        val mediaItems = episodes.mapNotNull { MediaItemMapper.fromEpisode(it) }
                        currentPlayer.setMediaItems(mediaItems)
                        return@withContext
                    }

                    val currentInNewIndex = episodes.indexOfFirst { it.id == currentMediaId }
                    if (currentInNewIndex == -1) {
                        android.util.Log.d("PlaybackService", "Current item removed from queue, stopping")
                        currentPlayer.stop()
                        currentPlayer.clearMediaItems()
                        return@withContext
                    }

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

                    // After removal, current item is at index 0
                    val newMediaItems = episodes.mapNotNull { ep ->
                        MediaItemMapper.fromEpisode(ep)
                    }
                    
                    val updatedCurrentItem = newMediaItems[currentInNewIndex]
                    val existingCurrentItem = currentPlayer.getMediaItemAt(0)
                    if (updatedCurrentItem.mediaMetadata != existingCurrentItem.mediaMetadata) {
                        currentPlayer.replaceMediaItem(0, updatedCurrentItem)
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
