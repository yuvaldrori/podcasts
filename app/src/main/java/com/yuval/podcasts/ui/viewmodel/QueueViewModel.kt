package com.yuval.podcasts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.domain.usecase.RemoveEpisodeUseCase
import com.yuval.podcasts.media.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface QueueUiState {
    object Loading : QueueUiState
    data class Success(
        val queue: ImmutableList<EpisodeWithPodcast>,
        val queueTimeRemaining: Long
    ) : QueueUiState
}

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val playerManager: PlayerManager,
    private val removeEpisodeUseCase: RemoveEpisodeUseCase
) : ViewModel() {

    val uiState: StateFlow<QueueUiState> = combine(
        repository.listeningQueue,
        playerManager.playbackSpeed,
        playerManager.currentMediaId,
        playerManager.currentPosition,
        playerManager.duration
    ) { currentQueue, speed, currentId, currentPos, currentDur ->
        val totalMsRemaining = currentQueue.sumOf { item ->
            if (item.episode.id == currentId && currentDur > 0) {
                (currentDur - currentPos).coerceAtLeast(0L)
            } else {
                val durationMs = item.episode.duration * 1000L
                (durationMs - item.episode.lastPlayedPosition).coerceAtLeast(0L)
            }
        }
        val remaining = if (speed > 0f) (totalMsRemaining / speed).toLong() else 0L
        QueueUiState.Success(currentQueue.toImmutableList(), remaining)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QueueUiState.Loading)

    fun reorderQueue(newOrderIds: List<String>) {
        viewModelScope.launch {
            repository.reorderQueue(newOrderIds)
        }
    }

    fun removeFromQueue(episodeId: String) {
        viewModelScope.launch {
            val isPlayingDismissed = playerManager.currentMediaId.value == episodeId
            removeEpisodeUseCase(episodeId, markAsPlayed = false)
            if (isPlayingDismissed) {
                playerManager.seekToNextMediaItem()
            }
        }
    }
}
