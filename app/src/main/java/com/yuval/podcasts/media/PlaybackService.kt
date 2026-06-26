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
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.LibraryResult
import com.google.common.collect.ImmutableList
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
import com.yuval.podcasts.utils.LogManager

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject lateinit var exoPlayer: ExoPlayer
    @Inject lateinit var castPlayer: CastPlayer
    private lateinit var currentPlayer: Player

    @Inject lateinit var episodeDao: EpisodeDao
    @Inject lateinit var queueDao: QueueDao
    @Inject lateinit var removeEpisodeUseCase: RemoveEpisodeUseCase
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var logManager: LogManager
    
    @Inject @IoDispatcher lateinit var ioDispatcher: CoroutineDispatcher
    @Inject @MainDispatcher lateinit var mainDispatcher: CoroutineDispatcher

    private var mediaSession: MediaLibrarySession? = null
    private var lastResumedId: String? = null
    private lateinit var serviceScope: CoroutineScope
    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null
    private var currentAudioSessionId: Int = androidx.media3.common.C.AUDIO_SESSION_ID_UNSET



    private val mediaSessionCallback = object : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(Constants.COMMAND_REWIND_10, Bundle.EMPTY))
                .add(SessionCommand(Constants.COMMAND_SKIP_30, Bundle.EMPTY))
                .build()
            
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .remove(Player.COMMAND_SET_REPEAT_MODE)
                .remove(Player.COMMAND_SET_SHUFFLE_MODE)
                .build()
            
            return MediaSession.ConnectionResult.accept(
                sessionCommands,
                playerCommands
            )
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            val customLayout = listOf(
                CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                    .setDisplayName("Rewind 10s")
                    .setCustomIconResId(android.R.drawable.ic_media_rew)
                    .setSessionCommand(SessionCommand(Constants.COMMAND_REWIND_10, Bundle.EMPTY))
                    .build(),
                CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                    .setDisplayName("Skip 30s")
                    .setCustomIconResId(android.R.drawable.ic_media_ff)
                    .setSessionCommand(SessionCommand(Constants.COMMAND_SKIP_30, Bundle.EMPTY))
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
                Constants.COMMAND_REWIND_10 -> seekBackward(Constants.REWIND_10_MS)
                Constants.COMMAND_SKIP_30 -> seekForward(Constants.SKIP_30_MS)
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

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isStartup: Boolean
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            return serviceScope.async(ioDispatcher) {
                val episodes = queueDao.getQueueEpisodes().first()
                if (episodes.isNotEmpty()) {
                    val currentEp = episodes.first()
                    val mediaItems = episodes.mapNotNull { MediaItemMapper.fromEpisode(it) }
                    MediaSession.MediaItemsWithStartPosition(
                        mediaItems,
                        0, // Starting with the first item in queue
                        currentEp.lastPlayedPosition
                    )
                } else {
                    // Fallback or empty result if no queue
                    MediaSession.MediaItemsWithStartPosition(
                        mutableListOf(),
                        0,
                        0
                    )
                }
            }.asListenableFuture()
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootMetadata = MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build()
            val rootItem = MediaItem.Builder()
                .setMediaId("root")
                .setMediaMetadata(rootMetadata)
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return serviceScope.async(ioDispatcher) {
                val items = when (parentId) {
                    "root" -> {
                        val queueFolderMetadata = MediaMetadata.Builder()
                            .setTitle("Queue")
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                            .build()
                        val queueFolder = MediaItem.Builder()
                            .setMediaId("queue")
                            .setMediaMetadata(queueFolderMetadata)
                            .build()
                        listOf(queueFolder)
                    }
                    "queue" -> {
                        val episodes = queueDao.getQueueEpisodesSync()
                        episodes.mapNotNull { ep ->
                            MediaItemMapper.fromEpisode(ep)
                        }
                    }
                    else -> emptyList()
                }
                LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
            }.asListenableFuture()
        }
    }

    private fun seekForward(ms: Long = Constants.SEEK_FORWARD_MS) {
        currentPlayer.seekForwardBounded(ms)
    }

    private fun seekBackward(ms: Long = Constants.SEEK_BACKWARD_MS) {
        currentPlayer.seekBackwardBounded(ms)
    }

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(mainDispatcher + SupervisorJob())
        

        
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
            
        exoPlayer.setAudioAttributes(audioAttributes, true)
        exoPlayer.setHandleAudioBecomingNoisy(true)
        exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
        castPlayer.repeatMode = Player.REPEAT_MODE_OFF
        
        serviceScope.launch(ioDispatcher) {
            val speed = settingsRepository.getPlaybackSpeed()
            val skipSilence = settingsRepository.isSkipSilenceEnabled()
            withContext(mainDispatcher) {
                exoPlayer.setPlaybackParameters(androidx.media3.common.PlaybackParameters(speed))
                exoPlayer.skipSilenceEnabled = skipSilence
            }
        }
        
        currentPlayer = exoPlayer
            
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
            
        mediaSession = MediaLibrarySession.Builder(this, currentPlayer, mediaSessionCallback)
            .setSessionActivity(pendingIntent)
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
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                setupLoudnessEnhancer(audioSessionId)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                saveCurrentPosition()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    saveCurrentPosition()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val lastId = currentlyPlayingId
                    logManager.i("PlaybackService", "Playback ended. lastId=$lastId")
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
                logManager.i("PlaybackService", "Media item transition. lastId=$lastId, newMediaId=${mediaItem?.mediaId}, reason=$reason")
                
                val isAutoTransition = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                val isRepeatTransition = reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                
                // If it's an automatic transition or a repeat transition, remove the previous/finished item from the queue
                if (lastId != null && (isAutoTransition || isRepeatTransition)) {
                    if (mediaItem == null || lastId != mediaItem.mediaId || isRepeatTransition) {
                        serviceScope.launch(ioDispatcher) {
                            removeEpisodeUseCase(lastId, markAsPlayed = true)
                        }
                    }
                }

                if (mediaItem != null) {
                    currentlyPlayingId = mediaItem.mediaId
                    
                    // If it's an automatic transition, a skip, or playlist change (initial load), check for saved position to resume
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || 
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                        if (lastResumedId != mediaItem.mediaId) {
                            lastResumedId = mediaItem.mediaId
                            serviceScope.launch(ioDispatcher) {
                                val episode = episodeDao.getEpisodeById(mediaItem.mediaId)
                                if (episode != null && episode.lastPlayedPosition > 0) {
                                    withContext(mainDispatcher) {
                                        // Only seek if we are at the start (prevent fighting manual seeks)
                                        if (currentPlayer.currentPosition < Constants.SEEK_POSITION_RESTORATION_THRESHOLD_MS) {
                                            currentPlayer.seekTo(episode.lastPlayedPosition)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    currentlyPlayingId = null
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                logManager.e("PlaybackService", "Player error: ${error.message}", mapOf(
                    "errorCode" to error.errorCodeName,
                    "mediaId" to (currentPlayer.currentMediaItem?.mediaId ?: "none")
                ))
            }
        }
        
        exoPlayer.addListener(listener)
        castPlayer.addListener(listener)

        // Live Silence Toggle
        serviceScope.launch(mainDispatcher) {
            settingsRepository.skipSilenceFlow.collect { enabled ->
                exoPlayer.skipSilenceEnabled = enabled
            }
        }

        // Live Volume Boost Toggle
        serviceScope.launch(mainDispatcher) {
            settingsRepository.volumeBoostFlow.collect { enabled ->
                try {
                    loudnessEnhancer?.setTargetGain(if (enabled) Constants.VOLUME_BOOST_GAIN_MB else 0)
                } catch (e: Exception) {
                    logManager.e("PlaybackService", "Failed to update target gain on LoudnessEnhancer", mapOf("error" to e.message.toString()))
                }
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
    }
    
    private fun setCurrentPlayer(newPlayer: Player) {
        if (currentPlayer == newPlayer) return
        
        // No manual state transfer needed as CastPlayer.setLocalPlayer handles it
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
                        logManager.i("PlaybackService", "Current item $currentMediaId removed from queue, stopping player")
                        currentPlayer.stop()
                        currentPlayer.clearMediaItems()
                        return@withContext
                    }

                    val currentIds = (0 until currentPlayer.mediaItemCount).map { currentPlayer.getMediaItemAt(it).mediaId }
                    val newIds = episodes.map { it.id }
                    
                    if (currentIds != newIds) {
                        // Surgically update the playlist to avoid restarting playback
                        val currentMediaIdInNewIndex = episodes.indexOfFirst { it.id == currentMediaId }
                        val currentMediaIndexInPlayer = currentPlayer.currentMediaItemIndex

                        // 1. Remove items that are no longer in the new list, EXCEPT the currently playing one
                        var i = 0
                        while (i < currentPlayer.mediaItemCount) {
                            val id = currentPlayer.getMediaItemAt(i).mediaId
                            if (id != currentMediaId && !newIds.contains(id)) {
                                currentPlayer.removeMediaItem(i)
                            } else {
                                i++
                            }
                        }

                        // 2. Add new items and reorder to match the new list
                        // Since we kept the current item, we need to move it to its new position or add everything around it
                        // Simplest surgical way:
                        // - Add all items from 'episodes' that aren't in 'currentPlayer' yet
                        // - Then move items to their correct positions
                        
                        val existingIdsInPlayer = (0 until currentPlayer.mediaItemCount).map { currentPlayer.getMediaItemAt(it).mediaId }
                        episodes.forEachIndexed { index, episode ->
                            if (!existingIdsInPlayer.contains(episode.id)) {
                                val newItem = MediaItemMapper.fromEpisode(episode)
                                if (newItem != null) {
                                    currentPlayer.addMediaItem(newItem)
                                }
                            }
                        }

                        // 3. Final reorder check (move items if they are at the wrong index)
                        for (index in episodes.indices) {
                            if (index >= currentPlayer.mediaItemCount) break
                            
                            val expectedId = episodes[index].id
                            val actualId = currentPlayer.getMediaItemAt(index).mediaId
                            if (expectedId != actualId) {
                                // Find where it is and move it
                                for (searchIndex in index + 1 until currentPlayer.mediaItemCount) {
                                    if (currentPlayer.getMediaItemAt(searchIndex).mediaId == expectedId) {
                                        currentPlayer.moveMediaItem(searchIndex, index)
                                        break
                                    }
                                }
                            }
                        }
                    }
                    
                    // 4. Update metadata of items if needed, but safely
                    episodes.forEachIndexed { index, episode ->
                        if (index < currentPlayer.mediaItemCount) {
                            val itemInPlayer = currentPlayer.getMediaItemAt(index)
                            if (itemInPlayer.mediaId == episode.id) {
                                val updatedItem = MediaItemMapper.fromEpisode(episode)
                                if (updatedItem != null && (updatedItem.mediaMetadata != itemInPlayer.mediaMetadata || updatedItem.localConfiguration?.uri != itemInPlayer.localConfiguration?.uri)) {
                                    // Media3's replaceMediaItem on the current index DOES causes a slight pause/restart.
                                    // We only replace if it's NOT the playing one to avoid playback interruption.
                                    if (currentPlayer.currentMediaItemIndex != index) {
                                        currentPlayer.replaceMediaItem(index, updatedItem)
                                    }
                                }
                            }
                        }
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

    private fun setupLoudnessEnhancer(audioSessionId: Int) {
        if (audioSessionId == androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) return

        currentAudioSessionId = audioSessionId
        try {
            loudnessEnhancer?.release()

            serviceScope.launch(mainDispatcher) {
                val enabled = settingsRepository.isVolumeBoostEnabled()
                withContext(mainDispatcher) {
                    if (currentAudioSessionId == audioSessionId) {
                        loudnessEnhancer = android.media.audiofx.LoudnessEnhancer(audioSessionId).apply {
                            setTargetGain(if (enabled) Constants.VOLUME_BOOST_GAIN_MB else 0)
                            setEnabled(true)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logManager.e("PlaybackService", "Failed to setup LoudnessEnhancer", mapOf("error" to e.message.toString()))
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    override fun onDestroy() {
        serviceScope.cancel()
        exoPlayer.release()
        castPlayer.release()
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
