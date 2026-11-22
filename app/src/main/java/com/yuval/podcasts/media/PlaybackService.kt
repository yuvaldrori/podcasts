package com.yuval.podcasts.media

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.core.content.IntentCompat
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.yuval.podcasts.MainActivity
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.QueueDao
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.repository.SettingsRepository
import com.yuval.podcasts.di.IoDispatcher
import com.yuval.podcasts.di.MainDispatcher
import com.yuval.podcasts.domain.usecase.RemoveEpisodeUseCase
import com.yuval.podcasts.utils.LogManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.asListenableFuture
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject lateinit var exoPlayer: ExoPlayer
    @Inject lateinit var castPlayer: dagger.Lazy<CastPlayer>
    @Volatile private var isCastInitialized = false
    private lateinit var currentPlayer: Player

    @Inject lateinit var episodeDao: EpisodeDao
    @Inject lateinit var queueDao: QueueDao
    @Inject lateinit var removeEpisodeUseCase: RemoveEpisodeUseCase
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var logManager: LogManager

    @Inject @IoDispatcher lateinit var ioDispatcher: CoroutineDispatcher
    @Inject @MainDispatcher lateinit var mainDispatcher: CoroutineDispatcher

    private var mediaSession: MediaLibrarySession? = null
    private lateinit var serviceScope: CoroutineScope
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var loudnessEnhancerJob: Job? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
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

            return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
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
                mediaItems.map { item ->
                    if (item.localConfiguration != null) return@map item
                    episodeDao.getEpisodeWithPodcast(item.mediaId)
                        ?.let(MediaItemMapper::fromEpisodeWithPodcast)
                        ?: item
                }.toMutableList()
            }.asListenableFuture()
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isStartup: Boolean
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            return serviceScope.async(ioDispatcher) {
                val queueWithPodcast = queueDao.getQueueEpisodesWithPodcast().first()
                buildResumptionItems(queueWithPodcast)
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
                        queueDao.getQueueEpisodesWithPodcastSync()
                            .mapNotNull(MediaItemMapper::fromEpisodeWithPodcast)
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
                exoPlayer.setPlaybackParameters(PlaybackParameters(speed))
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

        scheduleLazyCastInit(listener)
        observeSkipSilence(serviceScope, mainDispatcher, settingsRepository, exoPlayer)
        observeVolumeBoost()
        startPeriodicPositionSaver()
        observeQueue()
    }

    private fun scheduleLazyCastInit(listener: PlaybackServicePlayerListener) {
        serviceScope.launch {
            delay(Constants.CAST_INIT_DELAY_MS)
            withContext(mainDispatcher) {
                if (!serviceScope.isActive) return@withContext
                try {
                    val player = castPlayer.get()
                    initializeCastPlayer(
                        castPlayerInstance = player,
                        exoPlayer = exoPlayer,
                        listener = listener,
                        onPlayerSwitched = { setCurrentPlayer(it) },
                        logManager = logManager
                    )
                    isCastInitialized = true
                } catch (e: Exception) {
                    logManager.e("PlaybackService", "Failed to initialize CastPlayer lazily", mapOf("error" to e.message.toString()))
                }
            }
        }
    }

    private fun observeVolumeBoost() {
        serviceScope.launch(mainDispatcher) {
            settingsRepository.volumeBoostFlow.collect { enabled ->
                try {
                    loudnessEnhancer?.setTargetGain(if (enabled) Constants.VOLUME_BOOST_GAIN_MB else 0)
                } catch (e: Exception) {
                    logManager.e("PlaybackService", "Failed to update target gain on LoudnessEnhancer", mapOf("error" to e.message.toString()))
                }
            }
        }
    }

    private fun startPeriodicPositionSaver() {
        serviceScope.launch(mainDispatcher) {
            while (isActive) {
                delay(Constants.SAVE_POSITION_INTERVAL_MS)
                if (currentPlayer.isPlaying) {
                    saveCurrentPosition()
                }
            }
        }
    }

    private fun setCurrentPlayer(newPlayer: Player) {
        if (currentPlayer == newPlayer) return
        currentPlayer = newPlayer
        mediaSession?.player = newPlayer
    }

    private suspend fun buildResumptionItems(
        queueWithPodcast: List<EpisodeWithPodcast>
    ): MediaSession.MediaItemsWithStartPosition {
        if (queueWithPodcast.isEmpty()) {
            return MediaSession.MediaItemsWithStartPosition(mutableListOf(), 0, 0L)
        }
        val mapped = queueWithPodcast.mapNotNull { item ->
            MediaItemMapper.fromEpisodeWithPodcast(item)?.let { item.episode to it }
        }
        if (mapped.isEmpty()) {
            return MediaSession.MediaItemsWithStartPosition(mutableListOf(), 0, 0L)
        }
        val lastPlayedId = settingsRepository.getLastPlayedEpisodeId()
        val startIndex = mapped.indexOfFirst { (ep, _) -> ep.id == lastPlayedId }.takeIf { it != -1 } ?: 0
        val currentEp = mapped[startIndex].first
        return MediaSession.MediaItemsWithStartPosition(
            mapped.map { it.second },
            startIndex,
            currentEp.lastPlayedPosition
        )
    }

    private fun observeQueue() {
        serviceScope.launch {
            var isInitialEmission = true
            queueDao.getQueueEpisodesWithPodcast().distinctUntilChanged().collect { queueWithPodcast ->
                val episodes = queueWithPodcast.map { it.episode }
                playerListener?.updateCachedPositions(episodes)

                if (isInitialEmission) {
                    isInitialEmission = false
                    if (queueWithPodcast.isNotEmpty() && currentPlayer.mediaItemCount == 0) {
                        val resumption = withContext(ioDispatcher) { buildResumptionItems(queueWithPodcast) }
                        if (resumption.mediaItems.isNotEmpty()) {
                            withContext(mainDispatcher) {
                                if (currentPlayer.mediaItemCount == 0) {
                                    currentPlayer.setMediaItems(resumption.mediaItems)
                                    currentPlayer.seekTo(resumption.startIndex, resumption.startPositionMs)
                                    currentPlayer.prepare()
                                }
                            }
                            return@collect
                        }
                    }
                }

                val mediaItems = withContext(ioDispatcher) {
                    queueWithPodcast.mapNotNull(MediaItemMapper::fromEpisodeWithPodcast)
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
        QueuePlaylistSynchronizer.sync(currentPlayer, episodes, mediaItems, logManager)
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
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return

        currentAudioSessionId = audioSessionId
        loudnessEnhancerJob?.cancel()
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = null
        } catch (e: Exception) {
            logManager.e("PlaybackService", "Failed to release previous LoudnessEnhancer", mapOf("error" to e.message.toString()))
        }

        loudnessEnhancerJob = serviceScope.launch(ioDispatcher) {
            val enabled = settingsRepository.isVolumeBoostEnabled()
            withContext(mainDispatcher) {
                if (currentAudioSessionId != audioSessionId) return@withContext
                try {
                    loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                        setTargetGain(if (enabled) Constants.VOLUME_BOOST_GAIN_MB else 0)
                        setEnabled(true)
                    }
                } catch (e: Exception) {
                    logManager.e("PlaybackService", "Failed to setup LoudnessEnhancer", mapOf("error" to e.message.toString()))
                }
            }
        }
    }

    internal fun initializeCastPlayer(
        castPlayerInstance: CastPlayer,
        exoPlayer: ExoPlayer,
        listener: PlaybackServicePlayerListener,
        onPlayerSwitched: (Player) -> Unit,
        logManager: LogManager
    ) {
        castPlayerInstance.repeatMode = Player.REPEAT_MODE_OFF
        @Suppress("DEPRECATION")
        castPlayerInstance.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() {
                logManager.i("PlaybackService", "Cast session available")
            }
            override fun onCastSessionUnavailable() {
                logManager.i("PlaybackService", "Cast session unavailable")
            }
        })
        @Suppress("DEPRECATION")
        if (castPlayerInstance.isCastSessionAvailable && exoPlayer.mediaItemCount > 0 && castPlayerInstance.mediaItemCount == 0) {
            val items = (0 until exoPlayer.mediaItemCount).map { exoPlayer.getMediaItemAt(it) }
            val startIndex = exoPlayer.currentMediaItemIndex.coerceAtLeast(0)
            val startPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            val playWhenReady = exoPlayer.playWhenReady
            castPlayerInstance.setMediaItems(items, startIndex, startPosition)
            castPlayerInstance.playWhenReady = playWhenReady
            if (exoPlayer.playbackState != Player.STATE_IDLE) {
                castPlayerInstance.prepare()
            }
            exoPlayer.stop()
        }
        exoPlayer.removeListener(listener)
        castPlayerInstance.addListener(listener)
        onPlayerSwitched(castPlayerInstance)
        logManager.i("PlaybackService", "CastPlayer lazily initialized")
    }

    internal fun releasePlayers(
        exoPlayer: ExoPlayer,
        castPlayerProvider: dagger.Lazy<CastPlayer>,
        isCastInitialized: Boolean
    ) {
        if (isCastInitialized) {
            castPlayerProvider.get().release()
        } else {
            exoPlayer.release()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    override fun onDestroy() {
        serviceScope.cancel()
        playerListener = null
        releasePlayers(exoPlayer, castPlayer, isCastInitialized)
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
