package com.yuval.podcasts.ui.screens

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.ui.components.EpisodeItem
import com.yuval.podcasts.ui.viewmodel.FeedsUiState

import androidx.compose.ui.res.stringResource
import com.yuval.podcasts.R

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity

import com.yuval.podcasts.ui.components.LoadingBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEpisodesScreen(
    uiState: FeedsUiState,
    onEpisodeClick: (String) -> Unit,
    onRefreshAll: () -> Unit,
    onDismissAll: () -> Unit,
    onDismissEpisode: (Episode) -> Unit,
    onAddToQueue: (Episode) -> Unit,
    onClearError: () -> Unit = {}
) {
    if (uiState is FeedsUiState.Loading) {
        LoadingBox()
        return
    }

    val pullToRefreshState = rememberPullToRefreshState()
    val snackbarHostState = remember { SnackbarHostState() }

    // State hoisting for swipe-to-dismiss
    val episodes = (uiState as? FeedsUiState.Success)?.unplayedEpisodes ?: emptyList()
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_episodes_title)) },
                actions = {
                    IconButton(onClick = onDismissAll) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.dismiss_all))
                    }
                    IconButton(onClick = onRefreshAll) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        }
    ) { padding ->
        val isRefreshing = (uiState as? FeedsUiState.Success)?.isRefreshing ?: false
        val episodes = (uiState as? FeedsUiState.Success)?.unplayedEpisodes ?: emptyList()

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefreshAll,
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("episode_list"),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(
                    items = episodes,
                    key = { it.episode.id }
                ) { episodeWithPodcast ->
                    val episode = episodeWithPodcast.episode
                    val podcast = episodeWithPodcast.podcast

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                    ) {
                        val clickHandler = remember(episode.id) { { onEpisodeClick(episode.id) } }
                        EpisodeItem(
                            episode = episode,
                            modifier = Modifier.clickable(onClick = clickHandler),
                            imageUrl = podcast.imageUrl,
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { onDismissEpisode(episode) }) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.dismiss))
                                    }
                                    IconButton(onClick = { onAddToQueue(episode) }) {
                                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_to_queue))
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
