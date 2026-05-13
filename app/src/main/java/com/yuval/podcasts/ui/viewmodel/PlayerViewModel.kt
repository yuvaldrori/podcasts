package com.yuval.podcasts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.media.PlayerManager
import com.yuval.podcasts.utils.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val isConnected: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val currentEpisode: Episode? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val playerManager: PlayerManager,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PlayerUiState> = combine(
        playerManager.isPlaying,
        playerManager.isInitialized,
        playerManager.currentPosition,
        playerManager.duration,
        playerManager.playbackSpeed,
        playerManager.currentMediaId.flatMapLatest { id ->
            if (id == null) flowOf(null) else repository.getEpisodeByIdFlow(id)
        }
    ) { values ->
        val isPlaying = values[0] as Boolean
        val isInitialized = values[1] as Boolean
        val currentPosition = values[2] as Long
        val playerDuration = values[3] as Long
        val playbackSpeed = values[4] as Float
        val currentEpisode = values[5] as Episode?

        val finalDuration = if (playerDuration > 0) playerDuration else (currentEpisode?.duration?.seconds?.inWholeMilliseconds ?: 0L)
        
        PlayerUiState(
            isPlaying = isPlaying,
            isConnected = isInitialized,
            currentPosition = currentPosition,
            duration = finalDuration,
            playbackSpeed = playbackSpeed,
            currentEpisode = currentEpisode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(Constants.FLOW_STOP_TIMEOUT_MS),
        initialValue = PlayerUiState()
    )

    init {
        playerManager.initialize()
    }

    fun playPause() {
        playerManager.togglePlayPause()
    }

    fun play(episode: Episode) {
        val uri = episode.localFilePath ?: episode.audioUrl
        playerManager.play(
            mediaId = episode.id,
            uri = uri,
            title = episode.title,
            imageUrl = episode.imageUrl,
            startPositionMs = episode.lastPlayedPosition
        )
    }

    fun playQueue(episodes: List<Episode>, startIndex: Int, startPositionMs: Long = 0L) {
        playerManager.playQueue(episodes, startIndex, startPositionMs)
    }

    fun seekTo(position: Long) {
        playerManager.seekTo(position)
    }

    fun setSpeed(speed: Float) {
        playerManager.setPlaybackSpeed(speed)
    }

    fun toggleSpeed() {
        val currentSpeed = playerManager.playbackSpeed.value
        val newSpeed = if (currentSpeed >= 2f) 1f else 2f
        setSpeed(newSpeed)
    }

    fun seekForward() {
        playerManager.seekForward()
    }

    fun seekBackward() {
        playerManager.seekBackward()
    }

    fun seekToChapter(chapter: com.yuval.podcasts.data.db.entity.Chapter) {
        playerManager.seekTo(chapter.startTimeMs)
    }
}
