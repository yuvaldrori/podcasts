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
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yuval.podcasts.ui.components.EpisodeItem
import com.yuval.podcasts.ui.viewmodel.FeedsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEpisodesScreen(
    onEpisodeClick: (String) -> Unit,
    viewModel: FeedsViewModel = hiltViewModel()
) {
    val episodes by viewModel.unplayedEpisodes.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    val pullToRefreshState = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()

    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refreshAll()
        }
    }

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            pullToRefreshState.endRefresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Episodes") },
                actions = {
                    IconButton(onClick = { viewModel.dismissAll() }) {
                        Icon(Icons.Default.Clear, contentDescription = "Dismiss All")
                    }
                    IconButton(onClick = { 
                        coroutineScope.launch {
                            pullToRefreshState.startRefresh()
                        }
                     }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(
                    items = episodes,
                    key = { it.episode.id }
                ) { episodeWithPodcast ->
                    val state = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
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
                            EpisodeItem(
                                episode = episodeWithPodcast.episode,
                                modifier = Modifier.clickable { onEpisodeClick(episodeWithPodcast.episode.id) },
                                imageUrl = episodeWithPodcast.podcast.imageUrl,
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = { viewModel.dismissEpisode(episodeWithPodcast.episode) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Dismiss")
                                        }
                                        IconButton(onClick = { viewModel.addToQueue(episodeWithPodcast.episode) }) {
                                            Icon(Icons.Default.Add, contentDescription = "Add to Queue")
                                        }
                                    }
                                }
                            )
                        }
                    )
                }
            }

            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}
