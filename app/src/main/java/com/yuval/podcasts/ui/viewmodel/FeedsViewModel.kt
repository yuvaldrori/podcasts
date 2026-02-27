package com.yuval.podcasts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.repository.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedsViewModel @Inject constructor(
    private val repository: PodcastRepository
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val podcasts: StateFlow<List<Podcast>> = repository.allPodcasts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unplayedEpisodes: StateFlow<List<Episode>> = repository.unplayedEpisodes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refreshPodcast(feedUrl: String) {
        viewModelScope.launch {
            try {
                repository.fetchAndStorePodcast(feedUrl)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to refresh podcast: ${e.message}"
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            try {
                repository.refreshAll()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to refresh all podcasts: ${e.message}"
            }
        }
    }

    fun addToQueue(episode: Episode) {
        viewModelScope.launch {
            repository.enqueueEpisode(episode)
        }
    }

    fun getEpisodesForPodcast(feedUrl: String) = repository.getEpisodes(feedUrl)

    fun dismissEpisode(episode: Episode) {
        viewModelScope.launch {
            repository.markAsPlayed(episode.id)
        }
    }

    fun dismissAll() {
        viewModelScope.launch {
            repository.markAllAsPlayed()
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}