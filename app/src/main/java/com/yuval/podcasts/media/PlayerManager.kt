package com.yuval.podcasts.media

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.yuval.podcasts.data.repository.SettingsRepository
import com.yuval.podcasts.data.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.yuval.podcasts.di.MainDispatcher
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.guava.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

import com.yuval.podcasts.utils.LogManager

@Singleton
class PlayerManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    @param:MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    private val logManager: LogManager
) {
    private val scope = CoroutineScope(mainDispatcher + SupervisorJob())

    private var controllerFuture: ListenableFuture<MediaBrowser>? = null
    private var controller: MediaBrowser? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    private val positionTrigger = MutableStateFlow(0L)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentPosition: StateFlow<Long> = combine(_isPlaying, positionTrigger) { isPlaying, _ -> isPlaying }
        .flatMapLatest { isPlaying ->
            flow {
                emit(controller?.currentPosition ?: _currentPosition.value)
                while (isPlaying) {
                    delay(1.seconds)
                    emit(controller?.currentPosition ?: _currentPosition.value)
                }
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(Constants.FLOW_STOP_TIMEOUT_MS), 0L)

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _currentMediaId = MutableStateFlow<String?>(null)
    val currentMediaId: StateFlow<String?> = _currentMediaId.asStateFlow()



    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private suspend fun awaitController(): MediaBrowser? {
        return try {
            controller ?: controllerFuture?.await()
        } catch (e: Exception) {
            logManager.e("PlayerManager", "Failed to await MediaBrowser controller", mapOf("error" to e.message.toString()))
            null
        }
    }

    init {
        scope.launch {
            settingsRepository.playbackSpeedFlow.collect { speed ->
                _playbackSpeed.value = speed
                controller?.setPlaybackParameters(PlaybackParameters(speed))
            }
        }
    }

    fun initialize() {
        if (controllerFuture != null) return
        logManager.i("PlayerManager", "Initializing MediaBrowser")

        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaBrowser.Builder(context, sessionToken).buildAsync()
        controllerFuture = future

        scope.launch {
            try {
                controller = future.await()
                setupControllerListener()
                _isInitialized.value = true
                logManager.i("PlayerManager", "MediaBrowser initialized")
            } catch (e: Exception) {
                logManager.e("PlayerManager", "Failed to initialize MediaBrowser", mapOf("error" to e.message.toString()))
            }
        }
    }

    private fun setupControllerListener() {
        val player = controller ?: return

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.update { isPlaying }
                logManager.i("PlayerManager", "isPlaying changed to $isPlaying")
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _playbackSpeed.update { playbackParameters.speed }
                logManager.i("PlayerManager", "Playback speed changed to ${playbackParameters.speed}")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                logManager.i("PlayerManager", "Playback state changed to $playbackState")
                if (playbackState == Player.STATE_ENDED) {
                    stopAndClear()
                } else {
                    val d = player.duration
                    if (d != androidx.media3.common.C.TIME_UNSET) {
                        _duration.update { d }
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _currentMediaId.update { mediaItem?.mediaId }
                _duration.update { player.duration.coerceAtLeast(0L) }
                logManager.i("PlayerManager", "Media item transition to ${mediaItem?.mediaId}, reason $reason")
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                _currentPosition.update { newPosition.positionMs }
                positionTrigger.update { newPosition.positionMs }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                logManager.e("PlayerManager", "Playback error: ${error.message}", mapOf(
                    "errorCode" to error.errorCodeName,
                    "mediaId" to (player.currentMediaItem?.mediaId ?: "none")
                ))
            }
        })

        // Initial states
        _isPlaying.update { player.isPlaying }
        val currentSpeed = _playbackSpeed.value
        player.setPlaybackParameters(PlaybackParameters(currentSpeed))
        _duration.update { player.duration.coerceAtLeast(0L) }
        _currentMediaId.update { player.currentMediaItem?.mediaId }
        _currentPosition.update { player.currentPosition }
    }

    fun play(mediaId: String, uri: String, title: String? = null, imageUrl: String? = null, startPositionMs: Long = 0L) {
        _currentMediaId.update { mediaId }
        _currentPosition.update { startPositionMs }
        scope.launch {
            val browser = awaitController()
            browser?.let {
                val metadata = MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtworkUri(imageUrl?.toUri())
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setMediaId(mediaId)
                    .setUri(uri)
                    .setMediaMetadata(metadata)
                    .build()
                it.setMediaItem(mediaItem)
                if (startPositionMs > 0) {
                    it.seekTo(startPositionMs)
                }
                it.prepare()
                logManager.i("PlayerManager", "Starting playback for $mediaId")
                it.play()
            }
        }
    }

    fun prepareQueue(episodes: List<com.yuval.podcasts.data.db.entity.Episode>, startIndex: Int, startPositionMs: Long = 0L) {
        if (episodes.isEmpty() || startIndex !in episodes.indices) return
        val currentEp = episodes[startIndex]
        _currentMediaId.update { currentEp.id }
        _currentPosition.update { startPositionMs }

        scope.launch {
            val browser = awaitController()
            browser?.let {
                val mediaItems = mapEpisodesToMediaItems(episodes)
                it.setMediaItems(mediaItems, startIndex, startPositionMs)
                it.prepare()
            }
        }
    }

    fun playQueue(episodes: List<com.yuval.podcasts.data.db.entity.Episode>, startIndex: Int, startPositionMs: Long = 0L) {
        if (episodes.isEmpty() || startIndex !in episodes.indices) return
        val currentEp = episodes[startIndex]

        _currentMediaId.update { currentEp.id }
        _currentPosition.update { startPositionMs }

        scope.launch {
            val browser = awaitController()
            browser?.let {
                val mediaItems = mapEpisodesToMediaItems(episodes)
                it.setMediaItems(mediaItems, startIndex, startPositionMs)
                it.prepare()
                logManager.i("PlayerManager", "Starting queue playback at $startIndex")
                it.play()
            }
        }
    }

    private fun mapEpisodesToMediaItems(episodes: List<com.yuval.podcasts.data.db.entity.Episode>): List<MediaItem> {
        return episodes.mapNotNull { MediaItemMapper.fromEpisode(it) }
    }

    fun play() {
        scope.launch {
            val browser = awaitController()
            browser?.let {
                logManager.i("PlayerManager", "Resuming playback")
                it.play()
            }
        }
    }

    fun pause() {
        scope.launch {
            val browser = awaitController()
            browser?.let {
                logManager.i("PlayerManager", "Pausing playback")
                it.pause()
            }
        }
    }

    fun togglePlayPause() {
        scope.launch {
            val browser = awaitController()
            browser?.let {
                if (it.playWhenReady) {
                    logManager.i("PlayerManager", "Pausing playback")
                    it.pause()
                } else {
                    logManager.i("PlayerManager", "Resuming playback")
                    it.play()
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        _currentPosition.update { positionMs }
        scope.launch {
            val browser = awaitController()
            browser?.seekTo(positionMs)
        }
    }

    fun seekForward(ms: Long = Constants.SEEK_FORWARD_MS) {
        scope.launch {
            val browser = awaitController()
            browser?.let { b ->
                b.seekForwardBounded(ms)
                _currentPosition.update { b.currentPosition }
            }
        }
    }

    fun seekBackward(ms: Long = Constants.SEEK_BACKWARD_MS) {
        scope.launch {
            val browser = awaitController()
            browser?.let { b ->
                b.seekBackwardBounded(ms)
                _currentPosition.update { b.currentPosition }
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.update { speed }
        scope.launch {
            settingsRepository.savePlaybackSpeed(speed)
            val browser = awaitController()
            browser?.setPlaybackParameters(PlaybackParameters(speed))
        }
    }

    fun seekToNextMediaItem() {
        scope.launch {
            val browser = awaitController()
            browser?.let {
                if (it.hasNextMediaItem()) {
                    it.seekToNextMediaItem()
                } else {
                    stopAndClear()
                }
            }
        }
    }

    fun stopAndClear() {
        _currentMediaId.update { null }
        _isPlaying.update { false }
        _currentPosition.update { 0L }
        _duration.update { 0L }

        scope.launch {
            val browser = awaitController()
            browser?.let {
                it.stop()
                it.clearMediaItems()
            }
        }
    }

    fun release() {
        scope.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        _isInitialized.update { false }
    }
}
