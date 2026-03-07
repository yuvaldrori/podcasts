package com.yuval.podcasts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeedsUiState(
    val podcasts: List<Podcast> = emptyList(),
    val unplayedEpisodes: List<EpisodeWithPodcast> = emptyList(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class FeedsViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val enqueueEpisodeUseCase: EnqueueEpisodeUseCase,
    private val refreshPodcastUseCase: RefreshPodcastUseCase,
    private val refreshAllPodcastsUseCase: RefreshAllPodcastsUseCase,
    private val markEpisodeAsPlayedUseCase: MarkEpisodeAsPlayedUseCase,
    private val markAllAsPlayedUseCase: MarkAllAsPlayedUseCase,
    private val unsubscribePodcastUseCase: UnsubscribePodcastUseCase
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<FeedsUiState> = combine(
        repository.allPodcasts,
        repository.unplayedEpisodes,
        _isRefreshing,
        _errorMessage
    ) { podcasts, episodes, isRefreshing, error ->
        FeedsUiState(
            podcasts = podcasts,
            unplayedEpisodes = episodes,
            isRefreshing = isRefreshing,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FeedsUiState()
    )

    fun refreshPodcast(feedUrl: String) {
        viewModelScope.launch {
            try {
                refreshPodcastUseCase(feedUrl)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _errorMessage.value = "Failed to refresh podcast: ${e.message}"
            }
        }
    }

    fun refreshAll() {
        // Enqueue the worker. The UI will update when the DB updates via flows.
        _isRefreshing.value = true
        refreshAllPodcastsUseCase()
        // We'll reset the refreshing state quickly here, a real app might observe WorkInfo
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            _isRefreshing.value = false
        }
    }

    fun addToQueue(episode: Episode) {
        viewModelScope.launch {
            enqueueEpisodeUseCase(episode)
        }
    }

    fun getEpisodesForPodcast(feedUrl: String) = repository.getEpisodes(feedUrl)

    fun dismissEpisode(episode: Episode) {
        viewModelScope.launch {
            markEpisodeAsPlayedUseCase(episode.id)
        }
    }

    fun dismissAll() {
        viewModelScope.launch {
            markAllAsPlayedUseCase()
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun unsubscribePodcast(feedUrl: String) {
        viewModelScope.launch {
            unsubscribePodcastUseCase(feedUrl)
        }
    }
}
