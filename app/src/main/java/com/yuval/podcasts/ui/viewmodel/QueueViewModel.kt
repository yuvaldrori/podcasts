package com.yuval.podcasts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.db.entity.QueueState
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.media.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val playerManager: PlayerManager
) : ViewModel() {

    val queue: StateFlow<List<EpisodeWithPodcast>> = repository.listeningQueue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    }

    fun playPause() {
        playerManager.togglePlayPause()
    }

    fun play(episode: Episode) {
        val uri = episode.localFilePath ?: episode.audioUrl
        playerManager.play(episode.id, uri, episode.lastPlayedPosition)
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

    fun reorderQueue(newOrderIds: List<String>) {
        viewModelScope.launch {
            repository.reorderQueue(newOrderIds)
        }
    }

    fun removeFromQueue(episodeId: String) {
        viewModelScope.launch {
            // Check if the dismissed episode is currently playing
            val isPlayingDismissed = currentlyPlayingEpisode.value?.id == episodeId
            val currentQueueState = queue.value

            // Actually remove it from the DB and file system
            repository.removeFromQueue(episodeId)

            if (isPlayingDismissed) {
                // Find what was immediately after the removed episode in the old state
                val currentIndex = currentQueueState.indexOfFirst { it.episode.id == episodeId }
                val hasNext = currentIndex != -1 && currentIndex + 1 < currentQueueState.size
                
                if (hasNext) {
                    val nextEpisode = currentQueueState[currentIndex + 1].episode
                    play(nextEpisode)
                } else {
                    playerManager.stopAndClear()
                }
            }
        }
    }

    
}