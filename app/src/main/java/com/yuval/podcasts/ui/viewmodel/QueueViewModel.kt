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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@Immutable
sealed interface QueueUiState {
    object Loading : QueueUiState
    data class Success(
        val queue: ImmutableList<EpisodeWithPodcast>
    ) : QueueUiState
}

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val playerManager: PlayerManager,
    private val removeEpisodeUseCase: RemoveEpisodeUseCase
) : ViewModel() {

    private val _manualQueue = MutableStateFlow<ImmutableList<EpisodeWithPodcast>?>(null)

    // The effective queue = the in-progress manual reorder, or the persisted queue.
    private val effectiveQueue: kotlinx.coroutines.flow.Flow<ImmutableList<EpisodeWithPodcast>> = combine(
        repository.listeningQueue,
        _manualQueue
    ) { current, manual -> manual ?: current.toImmutableList() }

    // Only re-emits when the queue itself changes — NOT on every playback-position tick — so
    // the queue list doesn't recompose once per second during playback.
    val uiState: StateFlow<QueueUiState> = effectiveQueue
        .map { QueueUiState.Success(it) as QueueUiState }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.FLOW_STOP_TIMEOUT_MS), QueueUiState.Loading)

    // Header "time remaining" ticks with the playback position but carries only a Long, so
    // position updates recompose the header text without touching the list.
    @Suppress("UNCHECKED_CAST")
    val queueTimeRemaining: StateFlow<Long> = combine(
        effectiveQueue,
        _manualQueue,
        playerManager.playbackSpeed,
        playerManager.currentMediaId,
        playerManager.currentPosition,
        playerManager.duration
    ) { args: Array<Any?> ->
        val queue = args[0] as ImmutableList<EpisodeWithPodcast>
        val manualQueue = args[1] as? ImmutableList<EpisodeWithPodcast>
        val speed = args[2] as Float
        val currentId = args[3] as? String
        val currentPos = args[4] as Long
        val currentDur = args[5] as Long

        // If speed is non-positive, don't compute remaining time.
        if (speed <= 0f) return@combine 0L

        val activeQueue = manualQueue ?: queue
        val totalMsRemaining = activeQueue.sumOf { item ->
            if (item.episode.id == currentId && currentDur > 0) {
                (currentDur - currentPos).coerceAtLeast(0L)
            } else {
                val durationMs = item.episode.duration.seconds.inWholeMilliseconds
                (durationMs - item.episode.lastPlayedPosition).coerceAtLeast(0L)
            }
        }
        (totalMsRemaining / speed).toLong()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.FLOW_STOP_TIMEOUT_MS), 0L)

    fun moveItem(fromIndex: Int, toIndex: Int) {
        val currentSuccess = uiState.value as? QueueUiState.Success ?: return
        val currentList = _manualQueue.value ?: currentSuccess.queue
        val newList = currentList.toMutableList().apply {
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
            if (isPlayingDismissed && playerManager.currentMediaId.value == episodeId) {
                playerManager.seekToNextMediaItem()
            }
        }
    }
}
