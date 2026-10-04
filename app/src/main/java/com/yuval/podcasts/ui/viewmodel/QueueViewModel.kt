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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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

    val downloadProgressMap: StateFlow<Map<String, Int>> = repository.downloadProgressMap

    private val _queue = MutableStateFlow<ImmutableList<EpisodeWithPodcast>?>(null)

    private var isDragging = false
    private var reorderJob: Job? = null
    private val isReordering: Boolean
        get() = isDragging || reorderJob?.isActive == true

    init {
        viewModelScope.launch {
            repository.listeningQueue.collect { dbQueue ->
                if (!isReordering) {
                    _queue.value = dbQueue.toImmutableList()
                }
            }
        }
    }

    // Only re-emits when the queue itself changes — NOT on every playback-position tick — so
    // the queue list doesn't recompose once per second during playback.
    val uiState: StateFlow<QueueUiState> = _queue
        .filterNotNull()
        .map { QueueUiState.Success(it) as QueueUiState }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.FLOW_STOP_TIMEOUT_MS), QueueUiState.Loading)

    private data class PlayerPlaybackStats(
        val speed: Float,
        val currentMediaId: String?,
        val currentPosition: Long,
        val duration: Long
    )

    private val playerPlaybackStatsFlow = combine(
        playerManager.playbackSpeed,
        playerManager.currentMediaId,
        playerManager.currentPosition,
        playerManager.duration
    ) { speed, currentId, currentPos, duration ->
        PlayerPlaybackStats(speed, currentId, currentPos, duration)
    }

    // Header "time remaining" ticks with the playback position but carries only a Long, so
    // position updates recompose the header text without touching the list.
    val queueTimeRemaining: StateFlow<Long> = combine(
        _queue.filterNotNull(),
        playerPlaybackStatsFlow
    ) { queue, stats ->
        // If speed is non-positive, don't compute remaining time.
        if (stats.speed <= 0f) return@combine 0L

        val totalMsRemaining = queue.sumOf { item ->
            if (item.episode.id == stats.currentMediaId && stats.duration > 0) {
                (stats.duration - stats.currentPosition).coerceAtLeast(0L)
            } else {
                val durationMs = item.episode.duration.seconds.inWholeMilliseconds
                (durationMs - item.episode.lastPlayedPosition).coerceAtLeast(0L)
            }
        }
        (totalMsRemaining / stats.speed).toLong()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.FLOW_STOP_TIMEOUT_MS), 0L)

    fun moveItem(fromIndex: Int, toIndex: Int) {
        reorderJob?.cancel()
        isDragging = true
        _queue.update { current ->
            val list = current ?: return@update null
            if (fromIndex !in list.indices || toIndex !in list.indices) return@update list
            list.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }.toImmutableList()
        }
    }

    fun commitReorder() {
        isDragging = false
        val currentQueue = _queue.value ?: return
        val targetIds = currentQueue.map { item -> item.episode.id }
        reorderJob?.cancel()
        reorderJob = viewModelScope.launch {
            try {
                repository.reorderQueue(targetIds)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _queue.value = repository.listeningQueue.first().toImmutableList()
            }
        }
    }

    fun reorderQueue(newOrderIds: List<String>) {
        isDragging = false
        reorderJob?.cancel()
        _queue.update { current ->
            if (current == null) return@update null
            val itemMap = current.associateBy { it.episode.id }
            val reordered = newOrderIds.mapNotNull { itemMap[it] }
            val remaining = current.filter { it.episode.id !in newOrderIds }
            (reordered + remaining).toImmutableList()
        }
        reorderJob = viewModelScope.launch {
            try {
                repository.reorderQueue(newOrderIds)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _queue.value = repository.listeningQueue.first().toImmutableList()
            }
        }
    }

    fun removeFromQueue(episodeId: String) {
        viewModelScope.launch {
            val isPlayingDismissed = playerManager.currentMediaId.value == episodeId
            removeEpisodeUseCase(episodeId, markAsPlayed = true)
            if (isPlayingDismissed && playerManager.currentMediaId.value == episodeId) {
                playerManager.seekToNextMediaItem()
            }
        }
    }
}
