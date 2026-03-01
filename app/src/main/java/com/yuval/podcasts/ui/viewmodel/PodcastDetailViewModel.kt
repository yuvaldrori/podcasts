package com.yuval.podcasts.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.domain.usecase.EnqueueEpisodeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.net.URLDecoder

@HiltViewModel
class PodcastDetailViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val enqueueEpisodeUseCase: EnqueueEpisodeUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val feedUrl = savedStateHandle.get<String>("feedUrl")?.let { URLDecoder.decode(it, "UTF-8") }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val episodes: StateFlow<List<Episode>> = flowOf(feedUrl)
        .flatMapLatest { url ->
            if (url != null) {
                repository.getEpisodes(url)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addToQueue(episode: Episode) {
        viewModelScope.launch {
            enqueueEpisodeUseCase(episode)
        }
    }
}
