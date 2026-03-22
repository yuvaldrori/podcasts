package com.yuval.podcasts.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.domain.usecase.EnqueueEpisodeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.navigation.toRoute
import com.yuval.podcasts.ui.navigation.EpisodeDetailScreenRoute

import android.content.Context
import com.yuval.podcasts.R
import com.yuval.podcasts.data.Constants

sealed interface EpisodeDetailUiState {
    object Loading : EpisodeDetailUiState
    data class Success(
        val episodeWithPodcast: EpisodeWithPodcast,
        val isInQueue: Boolean
    ) : EpisodeDetailUiState
    object Error : EpisodeDetailUiState
}

@HiltViewModel
class EpisodeDetailViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val enqueueEpisodeUseCase: EnqueueEpisodeUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val episodeId = savedStateHandle.toRoute<EpisodeDetailScreenRoute>().episodeId

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<EpisodeDetailUiState> = combine(
        flowOf(episodeId).flatMapLatest { id ->
            repository.getEpisodeWithPodcastFlow(id)
        },
        repository.listeningQueue
    ) { episodeData, queue ->
        if (episodeData == null) {
            EpisodeDetailUiState.Error
        } else {
            EpisodeDetailUiState.Success(
                episodeWithPodcast = episodeData,
                isInQueue = queue.any { it.episode.id == episodeData.episode.id }
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EpisodeDetailUiState.Loading
    )

    fun addToQueue(episode: Episode) {
        viewModelScope.launch {
            enqueueEpisodeUseCase(episode)
        }
    }
}
