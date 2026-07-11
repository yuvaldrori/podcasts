package com.yuval.podcasts.ui.screens

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yuval.podcasts.R
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.ui.components.EpisodeItem
import com.yuval.podcasts.ui.components.LoadingBox
import com.yuval.podcasts.ui.components.RefreshProgressIndicator
import com.yuval.podcasts.ui.components.emptyStateItem
import com.yuval.podcasts.ui.viewmodel.FeedsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEpisodesScreen(
    uiState: FeedsUiState,
    onEpisodeClick: (String) -> Unit,
    onRefreshAll: () -> Unit,
    onDismissAll: () -> Unit,
    onDismissEpisode: (Episode) -> Unit,
    onAddToQueue: (Episode) -> Unit,
    onClearError: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    if (uiState is FeedsUiState.Loading) {
        LoadingBox()
        return
    }

    val pullToRefreshState = rememberPullToRefreshState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // State hoisting for swipe-to-dismiss
    val episodesData = (uiState as? FeedsUiState.Success)?.unplayedEpisodes ?: emptyList()
    val errorMessage = (uiState as? FeedsUiState.Success)?.errorMessage

    LaunchedEffect(errorMessage) {
        errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error.asString(context))
            onClearError()
        }
    }
    
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
        val successState = uiState as? FeedsUiState.Success
        val isRefreshing = successState?.isRefreshing ?: false
        val refreshProgress = successState?.refreshProgress

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefreshAll,
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            indicator = {}
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                RefreshProgressIndicator(isRefreshing = isRefreshing, refreshProgress = refreshProgress)
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("episode_list"),
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = contentPadding.calculateBottomPadding() + 16.dp,
                        start = 16.dp,
                        end = 16.dp
                    )
                ) {
                    if (episodesData.isEmpty()) {
                        emptyStateItem(R.string.empty_new_episodes_message)
                    } else {
                        items(
                            items = episodesData,
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
    }
}
