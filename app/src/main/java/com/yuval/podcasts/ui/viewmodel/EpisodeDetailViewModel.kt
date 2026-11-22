package com.yuval.podcasts.ui.viewmodel

import android.text.Html
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.db.entity.Chapter
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.di.IoDispatcher
import com.yuval.podcasts.domain.usecase.EnqueueEpisodeUseCase
import com.yuval.podcasts.ui.navigation.EpisodeDetailScreenRoute
import com.yuval.podcasts.ui.utils.toAnnotatedString
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface EpisodeDetailUiState {
    object Loading : EpisodeDetailUiState
    data class Success(
        val episodeWithPodcast: EpisodeWithPodcast,
        val isInQueue: Boolean,
        val chapters: List<Chapter>,
        val annotatedDescription: AnnotatedString
    ) : EpisodeDetailUiState
    object Error : EpisodeDetailUiState
}

@HiltViewModel
class EpisodeDetailViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val enqueueEpisodeUseCase: EnqueueEpisodeUseCase,
    savedStateHandle: SavedStateHandle,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val episodeId = savedStateHandle.toRoute<EpisodeDetailScreenRoute>().episodeId

    private val episodeWithParsedDescription = repository.getEpisodeWithPodcastFlow(episodeId)
        .scan(Triple<EpisodeWithPodcast?, String?, AnnotatedString>(null, null, AnnotatedString(""))) { prev, episodeData ->
            val rawDescription = episodeData?.episode?.description
            val annotated = when {
                rawDescription == null -> AnnotatedString("")
                rawDescription == prev.second -> prev.third
                else -> withContext(ioDispatcher) {
                    Html.fromHtml(rawDescription, Html.FROM_HTML_MODE_COMPACT).toAnnotatedString()
                }
            }
            Triple(episodeData, rawDescription, annotated)
        }
        .drop(1)

    private val isInQueueFlow = repository.listeningQueue
        .map { queue -> queue.any { it.episode.id == episodeId } }
        .distinctUntilChanged()

    val uiState: StateFlow<EpisodeDetailUiState> = combine(
        episodeWithParsedDescription,
        isInQueueFlow,
        repository.getChapters(episodeId)
    ) { (episodeData, _, annotatedDescription), isInQueue, chapters ->
        if (episodeData == null) {
            EpisodeDetailUiState.Error
        } else {
            EpisodeDetailUiState.Success(
                episodeWithPodcast = episodeData,
                isInQueue = isInQueue,
                chapters = chapters,
                annotatedDescription = annotatedDescription
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(Constants.FLOW_STOP_TIMEOUT_MS),
        initialValue = EpisodeDetailUiState.Loading
    )

    fun addToQueue(episode: Episode) {
        viewModelScope.launch {
            enqueueEpisodeUseCase(episode)
        }
    }
}
