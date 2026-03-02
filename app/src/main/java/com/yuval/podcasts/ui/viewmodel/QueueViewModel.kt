package com.yuval.podcasts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.domain.usecase.RemoveEpisodeUseCase
import com.yuval.podcasts.domain.usecase.SkipToNextEpisodeUseCase
import com.yuval.podcasts.media.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import kotlinx.coroutines.flow.combine

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val playerManager: PlayerManager,
    private val removeEpisodeUseCase: RemoveEpisodeUseCase,
    private val skipToNextEpisodeUseCase: SkipToNextEpisodeUseCase
) : ViewModel() {

    val queue: StateFlow<List<EpisodeWithPodcast>> = repository.listeningQueue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queueTimeRemaining: StateFlow<Long> = combine(
        queue,
        playerManager.playbackSpeed
    ) { currentQueue, speed ->
        val totalMsRemaining = currentQueue.sumOf { item ->
            // duration is in seconds, lastPlayedPosition is in ms
            val durationMs = item.episode.duration * 1000L
            val remainingMs = (durationMs - item.episode.lastPlayedPosition).coerceAtLeast(0L)
            remainingMs
        }
        (totalMsRemaining / speed).toLong()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    fun reorderQueue(newOrderIds: List<String>) {
        viewModelScope.launch {
            repository.reorderQueue(newOrderIds)
        }
    }

    fun removeFromQueue(episodeId: String) {
        viewModelScope.launch {
            // Check if the dismissed episode is currently playing by reading the PlayerManager directly
            val isPlayingDismissed = playerManager.currentMediaId.value == episodeId

            // Actually remove it from the DB and file system
            removeEpisodeUseCase(episodeId, markAsPlayed = false)

            if (isPlayingDismissed) {
                skipToNextEpisodeUseCase()
            }
        }
    }
}
