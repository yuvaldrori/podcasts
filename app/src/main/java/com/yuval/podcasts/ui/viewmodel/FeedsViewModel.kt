package com.yuval.podcasts.ui.viewmodel

import androidx.lifecycle.asFlow
import com.yuval.podcasts.ui.utils.UiText
import com.yuval.podcasts.R
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.yuval.podcasts.utils.LogManager
import androidx.work.WorkInfo

@Immutable
sealed interface FeedsUiState {
    object Loading : FeedsUiState
    data class Success(
        val podcasts: ImmutableList<Podcast> = persistentListOf(),
        val unplayedEpisodes: ImmutableList<EpisodeWithPodcast> = persistentListOf(),
        val isRefreshing: Boolean = false,
        val refreshProgress: Pair<Int, Int>? = null,
        val errorMessage: UiText? = null
    ) : FeedsUiState
}

@HiltViewModel
class FeedsViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val enqueueEpisodeUseCase: EnqueueEpisodeUseCase,
    private val refreshAllPodcastsUseCase: RefreshAllPodcastsUseCase,
    private val workManager: androidx.work.WorkManager,
    messageDelegate: MessageDelegate
) : ViewModel(), MessageDelegate by messageDelegate {

    val uiState: StateFlow<FeedsUiState> = combine(
        repository.allPodcasts,
        repository.unplayedEpisodes,
        workManager.getWorkInfosForUniqueWorkLiveData(Constants.WORK_NAME_SYNC_ALL).asFlow(),
        errorMessage
    ) { podcasts, episodes, workInfos, error ->
        val activeWorkInfo = workInfos.find { 
            it.state == WorkInfo.State.RUNNING || 
            it.state == WorkInfo.State.ENQUEUED 
        }
        val isRefreshing = activeWorkInfo != null
        
        val progress = if (activeWorkInfo != null) {
            val current = activeWorkInfo.progress.getInt(Constants.WORK_KEY_PROGRESS, 0)
            val total = activeWorkInfo.progress.getInt(Constants.WORK_KEY_TOTAL, 0)
            if (total > 0) current to total else null
        } else null

        FeedsUiState.Success(
            podcasts = podcasts.toImmutableList(),
            unplayedEpisodes = episodes.toImmutableList(),
            isRefreshing = isRefreshing,
            refreshProgress = progress,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(Constants.FLOW_STOP_TIMEOUT_MS),
        initialValue = FeedsUiState.Loading
    )

    fun refreshAll() {
        refreshAllPodcastsUseCase()
    }

    fun addToQueue(episode: Episode) {
        viewModelScope.launch {
            enqueueEpisodeUseCase(episode)
        }
    }

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

    fun unsubscribePodcast(feedUrl: String) {
        viewModelScope.launch {
            repository.unsubscribePodcast(feedUrl)
        }
    }
}
