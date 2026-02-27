package com.yuval.podcasts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.repository.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EpisodeDetailViewModel @Inject constructor(
    private val repository: PodcastRepository
) : ViewModel() {

    private val _episode = MutableStateFlow<EpisodeWithPodcast?>(null)
    val episode: StateFlow<EpisodeWithPodcast?> = _episode.asStateFlow()

    fun loadEpisode(id: String) {
        viewModelScope.launch {
            repository.getEpisodeWithPodcastFlow(id).collect {
                _episode.value = it
            }
        }
    }

    fun addToQueue(episode: Episode) {
        viewModelScope.launch {
            repository.enqueueEpisode(episode)
        }
    }
}
