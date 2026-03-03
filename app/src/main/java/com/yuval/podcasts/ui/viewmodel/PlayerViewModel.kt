package com.yuval.podcasts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.media.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

import kotlinx.coroutines.flow.first

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val playerManager: PlayerManager
) : ViewModel() {

    val isPlaying = playerManager.isPlaying
    val currentPosition = playerManager.currentPosition
    val duration = playerManager.duration
    val playbackSpeed = playerManager.playbackSpeed
    val currentMediaId = playerManager.currentMediaId

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentlyPlayingEpisode: StateFlow<Episode?> = currentMediaId.flatMapLatest { id ->
        if (id == null) flowOf(null)
        else repository.getEpisodeByIdFlow(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        playerManager.initialize()

        // Auto-load the queue on fresh startup if nothing is playing
        viewModelScope.launch {
            // Wait until the MediaBrowser is fully connected and initialized
            playerManager.isInitialized.first { it }
            
            if (currentMediaId.value == null) {
                val queue = repository.listeningQueue.first()
                if (queue.isNotEmpty()) {
                    val firstEp = queue.first().episode
                    playerManager.prepareQueue(queue.map { it.episode }, 0, firstEp.lastPlayedPosition)
                }
            }
        }

        viewModelScope.launch {
            isPlaying.collectLatest { playing ->
                while (playing) {
                    playerManager.updatePosition()
                    delay(1000)
                }
            }
        }
    }

    fun playPause() {
        playerManager.togglePlayPause()
    }

    fun play(episode: Episode) {
        val uri = episode.localFilePath ?: episode.audioUrl
        playerManager.play(episode.id, uri, episode.lastPlayedPosition)
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
        val currentSpeed = playbackSpeed.value
        val newSpeed = if (currentSpeed >= 2f) 1f else 2f
        setSpeed(newSpeed)
    }

    fun seekForward() {
        playerManager.seekForward()
    }

    fun seekBackward() {
        playerManager.seekBackward()
    }

    fun updatePosition() {
        playerManager.updatePosition()
    }
}
