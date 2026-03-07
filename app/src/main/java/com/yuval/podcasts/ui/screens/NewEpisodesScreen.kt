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
import androidx.compose.ui.unit.dp
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.ui.components.EpisodeItem
import com.yuval.podcasts.ui.viewmodel.FeedsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEpisodesScreen(
    uiState: FeedsUiState,
    onEpisodeClick: (String) -> Unit,
    onRefreshAll: () -> Unit,
    onDismissAll: () -> Unit,
    onDismissEpisode: (Episode) -> Unit,
    onAddToQueue: (Episode) -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Episodes") },
                actions = {
                    IconButton(onClick = onDismissAll) {
                        Icon(Icons.Default.Clear, contentDescription = "Dismiss All")
                    }
                    IconButton(onClick = onRefreshAll) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefreshAll,
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
                    val episode = episodeWithPodcast.episode
                    val podcast = episodeWithPodcast.podcast
                    
                    val onDismiss = remember(episode.id) {
                        { value: SwipeToDismissBoxValue ->
                            when (value) {
                                SwipeToDismissBoxValue.EndToStart -> {
                                    onDismissEpisode(episode)
                                    true
                                }
                                SwipeToDismissBoxValue.StartToEnd -> {
                                    onAddToQueue(episode)
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
                            val clickHandler = remember(episode.id) { { onEpisodeClick(episode.id) } }
                            EpisodeItem(
                                episode = episode,
                                modifier = Modifier.clickable(onClick = clickHandler),
                                imageUrl = podcast.imageUrl,
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = { onDismissEpisode(episode) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Dismiss")
                                        }
                                        IconButton(onClick = { onAddToQueue(episode) }) {
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
