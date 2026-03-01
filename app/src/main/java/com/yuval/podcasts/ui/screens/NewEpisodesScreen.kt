package com.yuval.podcasts.ui.screens
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.yuval.podcasts.ui.components.EpisodeItem
import com.yuval.podcasts.ui.viewmodel.FeedsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEpisodesScreen(
    onEpisodeClick: (String) -> Unit,
    viewModel: FeedsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val pullToRefreshState = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()



    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Episodes") },
                actions = {
                    IconButton(onClick = { viewModel.dismissAll() }) {
                        Icon(Icons.Default.Clear, contentDescription = "Dismiss All")
                    }
                    IconButton(onClick = { viewModel.refreshAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refreshAll() },
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(
                    items = uiState.unplayedEpisodes,
                    key = { it.episode.id }
                ) { episodeWithPodcast ->
                    val onDismiss = remember(episodeWithPodcast.episode.id) {
                        { value: SwipeToDismissBoxValue ->
                            when (value) {
                                SwipeToDismissBoxValue.EndToStart -> {
                                    viewModel.dismissEpisode(episodeWithPodcast.episode)
                                    true
                                }
                                SwipeToDismissBoxValue.StartToEnd -> {
                                    viewModel.addToQueue(episodeWithPodcast.episode)
                                    true
                                }
                                else -> false
                            }
                        }
                    }
                    val state = rememberSwipeToDismissBoxState(
                        confirmValueChange = onDismiss
                    )
                    SwipeToDismissBox(
                        state = state,
                        backgroundContent = {
                            val color = when (state.dismissDirection) {
                                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surface
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 4.dp)
                                    .background(color),
                            ) {
                                // Background colors handled by Box
                            }
                        },
                        content = {
                            val clickHandler = remember(episodeWithPodcast.episode.id) { { onEpisodeClick(episodeWithPodcast.episode.id) } }
                            EpisodeItem(
                                episode = episodeWithPodcast.episode,
                                modifier = Modifier.clickable(onClick = clickHandler),
                                imageUrl = episodeWithPodcast.podcast.imageUrl,
                                trailingContent = {
                                    Row {
                                        val onDismissClick = remember(episodeWithPodcast.episode.id) { { viewModel.dismissEpisode(episodeWithPodcast.episode) } }
                                        val onAddClick = remember(episodeWithPodcast.episode.id) { { viewModel.addToQueue(episodeWithPodcast.episode) } }
                                        IconButton(onClick = onDismissClick) {
                                            Icon(Icons.Default.Delete, contentDescription = "Dismiss")
                                        }
                                        IconButton(onClick = onAddClick) {
                                            Icon(Icons.Default.Add, contentDescription = "Add to Queue")
                                        }
                                    }
                                }
                            )
                        }
                    )
                }
            }


        }
    }
}
