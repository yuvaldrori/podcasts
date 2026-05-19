package com.yuval.podcasts.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.domain.usecase.RemoveEpisodeUseCase
import com.yuval.podcasts.media.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@Immutable
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

    private val _manualQueue = MutableStateFlow<ImmutableList<EpisodeWithPodcast>?>(null)

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<QueueUiState> = combine(
        repository.listeningQueue,
        _manualQueue,
        playerManager.playbackSpeed,
        playerManager.currentMediaId,
        playerManager.currentPosition,
        playerManager.duration
    ) { args: Array<Any?> ->
        val currentQueue = args[0] as List<EpisodeWithPodcast>
        val manualQueue = args[1] as? ImmutableList<EpisodeWithPodcast>
        val speed = args[2] as Float
        val currentId = args[3] as? String
        val currentPos = args[4] as Long
        val currentDur = args[5] as Long

        val effectiveQueue: ImmutableList<EpisodeWithPodcast> = manualQueue ?: currentQueue.toImmutableList()
        val totalMsRemaining = effectiveQueue.sumOf { item ->
            if (item.episode.id == currentId && currentDur > 0) {
                (currentDur - currentPos).coerceAtLeast(0L)
            } else {
                val durationMs = item.episode.duration.seconds.inWholeMilliseconds
                (durationMs - item.episode.lastPlayedPosition).coerceAtLeast(0L)
            }
        }
        val remaining = if (speed > 0f) (totalMsRemaining / speed).toLong() else 0L
        QueueUiState.Success(effectiveQueue, remaining)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.FLOW_STOP_TIMEOUT_MS), QueueUiState.Loading)

    fun moveItem(fromIndex: Int, toIndex: Int) {
        val currentSuccess = uiState.value as? QueueUiState.Success ?: return
        val newList = currentSuccess.queue.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }.toImmutableList()
        _manualQueue.value = newList
    }

    fun commitReorder() {
        val manual = _manualQueue.value ?: return
        viewModelScope.launch {
            repository.reorderQueue(manual.map { item -> item.episode.id })
            _manualQueue.value = null
        }
    }

    fun reorderQueue(newOrderIds: List<String>) {
        viewModelScope.launch {
            repository.reorderQueue(newOrderIds)
            _manualQueue.value = null
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
