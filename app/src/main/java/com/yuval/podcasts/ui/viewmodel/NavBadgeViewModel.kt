package com.yuval.podcasts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.repository.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Supplies the episode counts shown as badges on the bottom-navigation buttons:
 * the number of episodes in the queue and the number of new (unplayed) episodes.
 */
@HiltViewModel
class NavBadgeViewModel @Inject constructor(
    repository: PodcastRepository
) : ViewModel() {

    val queueCount: StateFlow<Int> = repository.listeningQueue
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.FLOW_STOP_TIMEOUT_MS), 0)

    val newEpisodeCount: StateFlow<Int> = repository.unplayedEpisodes
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.FLOW_STOP_TIMEOUT_MS), 0)
}
