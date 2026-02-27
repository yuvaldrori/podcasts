package com.yuval.podcasts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.QueueState
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.media.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val playerManager: PlayerManager
) : ViewModel() {

    val queue: StateFlow<List<Episode>> = repository.listeningQueue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isPlaying = playerManager.isPlaying
    val currentPosition = playerManager.currentPosition
    val duration = playerManager.duration
    val playbackSpeed = playerManager.playbackSpeed

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

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}