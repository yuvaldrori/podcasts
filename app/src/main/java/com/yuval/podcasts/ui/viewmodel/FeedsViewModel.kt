package com.yuval.podcasts.ui.viewmodel

import androidx.lifecycle.asFlow
import com.yuval.podcasts.ui.utils.UiText
import kotlinx.coroutines.flow.update
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

@Immutable
sealed interface FeedsUiState {
    object Loading : FeedsUiState
    data class Success(
        val podcasts: ImmutableList<Podcast> = persistentListOf(),
        val unplayedEpisodes: ImmutableList<EpisodeWithPodcast> = persistentListOf(),
        val isRefreshing: Boolean = false,
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
        workManager.getWorkInfosForUniqueWorkLiveData("sync_all_podcasts").asFlow(),
        errorMessage
    ) { podcasts, episodes, workInfos, error ->
        val isRefreshing = workInfos.any { it.state == androidx.work.WorkInfo.State.RUNNING || it.state == androidx.work.WorkInfo.State.ENQUEUED } == true
        FeedsUiState.Success(
            podcasts = podcasts.toImmutableList(),
            unplayedEpisodes = episodes.toImmutableList(),
            isRefreshing = isRefreshing,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(Constants.FLOW_STOP_TIMEOUT_MS),
        initialValue = FeedsUiState.Loading
    )

    fun refreshPodcast(feedUrl: String) {
        viewModelScope.launch {
            try {
                repository.fetchAndStorePodcast(feedUrl)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                showError(UiText.StringResource(R.string.error_refresh_podcast, e.message ?: ""))
            }
        }
    }

    fun refreshAll() {
        refreshAllPodcastsUseCase()
    }

    fun addToQueue(episode: Episode) {
        viewModelScope.launch {
            enqueueEpisodeUseCase(episode)
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

    fun unsubscribePodcast(feedUrl: String) {
        viewModelScope.launch {
            repository.unsubscribePodcast(feedUrl)
        }
    }
}
