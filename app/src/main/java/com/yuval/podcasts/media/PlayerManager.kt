package com.yuval.podcasts.media

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.yuval.podcasts.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private var controllerFuture: ListenableFuture<MediaBrowser>? = null
    private var controller: MediaBrowser? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(settingsRepository.getPlaybackSpeed())
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _currentMediaId = MutableStateFlow<String?>(null)
    val currentMediaId: StateFlow<String?> = _currentMediaId.asStateFlow()

    fun initialize() {
        if (controllerFuture != null) return

        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaBrowser.Builder(context, sessionToken).buildAsync()
        
        future.addListener({
            controller = future.get()
            setupControllerListener()
        }, MoreExecutors.directExecutor())
        
        controllerFuture = future
    }

    private fun setupControllerListener() {
        val player = controller ?: return

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _playbackSpeed.value = playbackParameters.speed
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _duration.value = player.duration.coerceAtLeast(0L)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _currentMediaId.value = mediaItem?.mediaId
            }
        })

        // Initial states
        _isPlaying.value = player.isPlaying
        val defaultSpeed = settingsRepository.getPlaybackSpeed()
        player.setPlaybackParameters(PlaybackParameters(defaultSpeed))
        _playbackSpeed.value = defaultSpeed
        _duration.value = player.duration.coerceAtLeast(0L)
        _currentMediaId.value = player.currentMediaItem?.mediaId

        // If the player is totally empty on startup, trigger the Media3 onPlaybackResumption callback
        // This will allow the service to read the database queue and push it down to us natively.
        if (player.mediaItemCount == 0) {
            player.prepare()
        }
    }

    fun play(mediaId: String, uri: String, startPositionMs: Long = 0L) {
        _currentMediaId.value = mediaId
        controller?.let {
            val mediaItem = MediaItem.Builder()
                .setMediaId(mediaId)
                .setUri(uri)
                .build()
            it.setMediaItem(mediaItem)
            if (startPositionMs > 0) {
                it.seekTo(startPositionMs)
            }
            it.prepare()
            it.play()
        }
    }

    fun playQueue(episodes: List<com.yuval.podcasts.data.db.entity.Episode>, startIndex: Int, startPositionMs: Long = 0L) {
        if (episodes.isEmpty() || startIndex !in episodes.indices) return
        val currentEp = episodes[startIndex]
        _currentMediaId.value = currentEp.id
        
        controller?.let {
            val mediaItems = episodes.map { ep ->
                val uri = ep.localFilePath ?: ep.audioUrl
                MediaItem.Builder()
                    .setMediaId(ep.id)
                    .setUri(uri)
                    .build()
            }
            it.setMediaItems(mediaItems, startIndex, startPositionMs)
            it.prepare()
            it.play()
        }
    }

    fun togglePlayPause() {
        controller?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun seekForward(ms: Long = 30000L) {
        controller?.let {
            val newPosition = (it.currentPosition + ms).coerceAtMost(it.duration.coerceAtLeast(0L))
            it.seekTo(newPosition)
            _currentPosition.value = newPosition
        }
    }

    fun seekBackward(ms: Long = 30000L) {
        controller?.let {
            val newPosition = (it.currentPosition - ms).coerceAtLeast(0L)
            it.seekTo(newPosition)
            _currentPosition.value = newPosition
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        settingsRepository.savePlaybackSpeed(speed)
        controller?.setPlaybackParameters(PlaybackParameters(speed))
        _playbackSpeed.value = speed
    }

    fun seekToNextMediaItem() {
        controller?.let {
            if (it.hasNextMediaItem()) {
                it.seekToNextMediaItem()
            } else {
                stopAndClear()
            }
        }
    }

    fun stopAndClear() {
        controller?.let {
            it.stop()
            it.clearMediaItems()
        }
        _currentMediaId.value = null
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
    }

    fun release() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
    }

    // Call this periodically from the UI to update the progress bar
    fun updatePosition() {
        controller?.let {
            _currentPosition.value = it.currentPosition
        }
    }
}