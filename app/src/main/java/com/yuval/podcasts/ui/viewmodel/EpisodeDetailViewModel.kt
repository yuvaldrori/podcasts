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

data class EpisodeDetailUiState(
    val episodeWithPodcast: EpisodeWithPodcast? = null,
    val isInQueue: Boolean = false,
    val isLoading: Boolean = true
)

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
            if (id != null) repository.getEpisodeWithPodcastFlow(id) else flowOf(null)
        },
        repository.listeningQueue
    ) { episodeData, queue ->
        EpisodeDetailUiState(
            episodeWithPodcast = episodeData,
            isInQueue = episodeData?.let { data -> queue.any { it.episode.id == data.episode.id } } ?: false,
            isLoading = episodeData == null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EpisodeDetailUiState()
    )

    fun addToQueue(episode: Episode) {
        viewModelScope.launch {
            enqueueEpisodeUseCase(episode)
        }
    }
}
