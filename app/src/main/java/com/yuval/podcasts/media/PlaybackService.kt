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
import kotlinx.coroutines.Job
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.utils.LogManager
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
import kotlinx.coroutines.isActive
import androidx.media3.common.MediaMetadata
import com.yuval.podcasts.data.Constants
import kotlinx.coroutines.guava.asListenableFuture

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject lateinit var exoPlayer: ExoPlayer
    @Inject lateinit var castPlayer: dagger.Lazy<CastPlayer>
    private var isCastInitialized = false
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
    private var playerListener: PlaybackServicePlayerListener? = null



    private val mediaSessionCallback = object : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
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
            if (handleMediaButtonIntent(intent, { seekForward() }, { seekBackward() })) {
                return true
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
                    
                    val episodeWithPodcast = episodeDao.getEpisodeWithPodcast(item.mediaId)
                    episodeWithPodcast?.let { MediaItemMapper.fromEpisode(it.episode, it.podcast.imageUrl, it.podcast.title) } ?: item
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
                val queueWithPodcast = queueDao.getQueueEpisodesWithPodcast().first()
                val lastPlayedId = settingsRepository.getLastPlayedEpisodeId()
                if (queueWithPodcast.isNotEmpty()) {
                    var startIndex = queueWithPodcast.indexOfFirst { it.episode.id == lastPlayedId }
                    if (startIndex == -1) startIndex = 0
                    val currentEp = queueWithPodcast[startIndex].episode
                    val mediaItems = queueWithPodcast.mapNotNull { MediaItemMapper.fromEpisode(it.episode, it.podcast.imageUrl, it.podcast.title) }
                    MediaSession.MediaItemsWithStartPosition(
                        mediaItems,
                        startIndex,
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
                .setMediaId(Constants.MEDIA_LIBRARY_ROOT_ID)
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
                    Constants.MEDIA_LIBRARY_ROOT_ID -> {
                        val queueFolderMetadata = MediaMetadata.Builder()
                            .setTitle("Queue")
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                            .build()
                        val queueFolder = MediaItem.Builder()
                            .setMediaId(Constants.MEDIA_LIBRARY_QUEUE_ID)
                            .setMediaMetadata(queueFolderMetadata)
                            .build()
                        listOf(queueFolder)
                    }
                    Constants.MEDIA_LIBRARY_QUEUE_ID -> {
                        val episodes = queueDao.getQueueEpisodesWithPodcastSync()
                        episodes.mapNotNull { epWithPodcast ->
                            MediaItemMapper.fromEpisode(epWithPodcast.episode, epWithPodcast.podcast.imageUrl, epWithPodcast.podcast.title)
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

        val listener = PlaybackServicePlayerListener(
            removeEpisodeUseCase = removeEpisodeUseCase,
            settingsRepository = settingsRepository,
            logManager = logManager,
            serviceScope = serviceScope,
            ioDispatcher = ioDispatcher,
            getCurrentPlayer = { currentPlayer },
            setupLoudnessEnhancer = { setupLoudnessEnhancer(it) },
            onSavePosition = { id, pos ->
                if (id != null && pos != null) {
                    saveCurrentPosition(id, pos)
                } else {
                    saveCurrentPosition()
                }
            }
        )
        playerListener = listener
        
        exoPlayer.addListener(listener)

        serviceScope.launch {
            // Delay CastPlayer initialization to allow main thread to remain fully responsive
            // during app startup and focus processing.
            kotlinx.coroutines.delay(Constants.CAST_INIT_DELAY_MS)
            withContext(mainDispatcher) {
                try {
                    val player = castPlayer.get()
                    player.repeatMode = Player.REPEAT_MODE_OFF
                    @Suppress("DEPRECATION")
                    player.setSessionAvailabilityListener(object : SessionAvailabilityListener {
                        override fun onCastSessionAvailable() {
                            setCurrentPlayer(player)
                        }
                        override fun onCastSessionUnavailable() {
                            setCurrentPlayer(exoPlayer)
                        }
                    })
                    player.addListener(listener)
                    isCastInitialized = true
                    logManager.i("PlaybackService", "CastPlayer lazily initialized")
                } catch (e: Exception) {
                    logManager.e("PlaybackService", "Failed to initialize CastPlayer lazily", mapOf("error" to e.message.toString()))
                }
            }
        }

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
            val queueWithPodcast = queueDao.getQueueEpisodesWithPodcast().first()
            if (queueWithPodcast.isNotEmpty() && currentPlayer.mediaItemCount == 0) {
                val lastPlayedId = settingsRepository.getLastPlayedEpisodeId()
                var startIndex = queueWithPodcast.indexOfFirst { it.episode.id == lastPlayedId }
                if (startIndex == -1) startIndex = 0
                val currentEp = queueWithPodcast[startIndex].episode
                val mediaItems = withContext(ioDispatcher) {
                    queueWithPodcast.mapNotNull { ep -> MediaItemMapper.fromEpisode(ep.episode, ep.podcast.imageUrl, ep.podcast.title) }
                }
                if (mediaItems.isNotEmpty()) {
                    currentPlayer.setMediaItems(mediaItems)
                    currentPlayer.seekTo(startIndex, currentEp.lastPlayedPosition)
                    currentPlayer.prepare()
                }
            }
        }

        // Periodic position saver
        serviceScope.launch(mainDispatcher) {
            while (isActive) {
                kotlinx.coroutines.delay(Constants.SAVE_POSITION_INTERVAL_MS)
                if (currentPlayer.isPlaying) {
                    saveCurrentPosition()
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
            queueDao.getQueueEpisodesWithPodcast().distinctUntilChanged().collect { queueWithPodcast ->
                val episodes = queueWithPodcast.map { it.episode }
                playerListener?.updateCachedPositions(episodes)
                val mediaItems = withContext(ioDispatcher) {
                    queueWithPodcast.mapNotNull { MediaItemMapper.fromEpisode(it.episode, it.podcast.imageUrl, it.podcast.title) }
                }
                withContext(mainDispatcher) {
                    updatePlayerFromQueue(currentPlayer, episodes, mediaItems, logManager)
                }
            }
        }
    }

    internal fun handleMediaButtonIntent(
        intent: Intent,
        onSeekForward: () -> Unit,
        onSeekBackward: () -> Unit
    ): Boolean {
        val keyEvent = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        if (keyEvent?.action == KeyEvent.ACTION_DOWN) {
            when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    onSeekForward()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    onSeekBackward()
                    return true
                }
            }
        }
        return false
    }

    internal fun observeSkipSilence(
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher,
        settingsRepository: SettingsRepository,
        exoPlayer: ExoPlayer
    ): Job {
        return scope.launch(dispatcher) {
            settingsRepository.skipSilenceFlow.collect { enabled ->
                exoPlayer.skipSilenceEnabled = enabled
            }
        }
    }

    internal fun updatePlayerFromQueue(
        currentPlayer: Player,
        episodes: List<Episode>,
        mediaItems: List<MediaItem>,
        logManager: LogManager? = null
    ) {
        if (episodes.isEmpty()) {
            if (currentPlayer.mediaItemCount > 0) {
                currentPlayer.stop()
                currentPlayer.clearMediaItems()
            }
            return
        }

        val currentMediaId = currentPlayer.currentMediaItem?.mediaId

        if (currentMediaId == null) {
            currentPlayer.setMediaItems(mediaItems)
            return
        }

        val newIds = episodes.map { it.id }

        val currentInNewIndex = episodes.indexOfFirst { it.id == currentMediaId }
        if (currentInNewIndex == -1) {
            val playerIds = (0 until currentPlayer.mediaItemCount).map { currentPlayer.getMediaItemAt(it).mediaId }
            val hasQueueItems = playerIds.any { newIds.contains(it) }
            if (!hasQueueItems) {
                return
            }

            var nextEpisodeId: String? = null
            val currentIndex = currentPlayer.currentMediaItemIndex
            for (i in currentIndex + 1 until currentPlayer.mediaItemCount) {
                val id = currentPlayer.getMediaItemAt(i).mediaId
                if (newIds.contains(id)) {
                    nextEpisodeId = id
                    break
                }
            }

            if (nextEpisodeId != null) {
                val nextIndexInNew = episodes.indexOfFirst { it.id == nextEpisodeId }
                if (nextIndexInNew != -1) {
                    logManager?.i("PlaybackService", "Current item $currentMediaId removed from queue, transitioning to next item $nextEpisodeId")
                    currentPlayer.setMediaItems(mediaItems, nextIndexInNew, 0L)
                    currentPlayer.prepare()
                    currentPlayer.play()
                    return
                }
            }

            logManager?.i("PlaybackService", "Current item $currentMediaId removed from queue and no next item found, stopping player")
            currentPlayer.stop()
            currentPlayer.clearMediaItems()
            return
        }

        val currentIds = (0 until currentPlayer.mediaItemCount).map { currentPlayer.getMediaItemAt(it).mediaId }

        if (currentIds != newIds) {
            var i = 0
            while (i < currentPlayer.mediaItemCount) {
                val id = currentPlayer.getMediaItemAt(i).mediaId
                if (id != currentMediaId && !newIds.contains(id)) {
                    currentPlayer.removeMediaItem(i)
                } else {
                    i++
                }
            }

            val existingIdsInPlayer = (0 until currentPlayer.mediaItemCount).map { currentPlayer.getMediaItemAt(it).mediaId }
            episodes.forEachIndexed { index, episode ->
                if (!existingIdsInPlayer.contains(episode.id)) {
                    val newItem = mediaItems.getOrNull(index)
                    if (newItem != null) {
                        currentPlayer.addMediaItem(newItem)
                    }
                }
            }

            for (index in episodes.indices) {
                if (index >= currentPlayer.mediaItemCount) break

                val expectedId = episodes[index].id
                val actualId = currentPlayer.getMediaItemAt(index).mediaId
                if (expectedId != actualId) {
                    for (searchIndex in index + 1 until currentPlayer.mediaItemCount) {
                        if (currentPlayer.getMediaItemAt(searchIndex).mediaId == expectedId) {
                            currentPlayer.moveMediaItem(searchIndex, index)
                            break
                        }
                    }
                }
            }
        }

        episodes.forEachIndexed { index, episode ->
            if (index < currentPlayer.mediaItemCount) {
                val itemInPlayer = currentPlayer.getMediaItemAt(index)
                if (itemInPlayer.mediaId == episode.id) {
                    val updatedItem = mediaItems.getOrNull(index)
                    if (updatedItem != null && (updatedItem.mediaMetadata != itemInPlayer.mediaMetadata || updatedItem.localConfiguration?.uri != itemInPlayer.localConfiguration?.uri)) {
                        if (currentPlayer.currentMediaItemIndex != index) {
                            currentPlayer.replaceMediaItem(index, updatedItem)
                        }
                    }
                }
            }
        }
    }

    private fun saveCurrentPosition(mediaId: String, position: Long) {
        playerListener?.updateCachedPosition(mediaId, position)
        serviceScope.launch(ioDispatcher) {
            episodeDao.updateLastPlayedPosition(mediaId, position)
        }
    }

    private fun saveCurrentPosition() {
        val mediaId = currentPlayer.currentMediaItem?.mediaId ?: return
        val position = currentPlayer.currentPosition
        saveCurrentPosition(mediaId, position)
    }

    private fun setupLoudnessEnhancer(audioSessionId: Int) {
        if (audioSessionId == androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) return

        currentAudioSessionId = audioSessionId
        try {
            loudnessEnhancer?.release()

            serviceScope.launch(mainDispatcher) {
                try {
                    val enabled = settingsRepository.isVolumeBoostEnabled()
                    if (currentAudioSessionId == audioSessionId) {
                        loudnessEnhancer = android.media.audiofx.LoudnessEnhancer(audioSessionId).apply {
                            setTargetGain(if (enabled) Constants.VOLUME_BOOST_GAIN_MB else 0)
                            setEnabled(true)
                        }
                    }
                } catch (e: Exception) {
                    logManager.e("PlaybackService", "Failed to setup LoudnessEnhancer inside coroutine", mapOf("error" to e.message.toString()))
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
        playerListener = null
        exoPlayer.release()
        if (isCastInitialized) {
            castPlayer.get().release()
        }
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
