package com.yuval.podcasts.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yuval.podcasts.R
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.ui.components.EpisodeItem
import com.yuval.podcasts.ui.components.LoadingBox
import com.yuval.podcasts.ui.components.RefreshableContent
import com.yuval.podcasts.ui.components.emptyStateItem
import com.yuval.podcasts.ui.viewmodel.FeedsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEpisodesScreen(
    uiState: FeedsUiState,
    downloadProgressMap: Map<String, Int> = emptyMap(),
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

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val successState = uiState as? FeedsUiState.Success
    val episodesData = successState?.unplayedEpisodes ?: emptyList()
    val errorMessage = successState?.errorMessage
    val isRefreshing = successState?.isRefreshing ?: false
    val refreshProgress = successState?.refreshProgress

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
        RefreshableContent(
            isRefreshing = isRefreshing,
            refreshProgress = refreshProgress,
            onRefresh = onRefreshAll,
            modifier = Modifier.padding(padding)
        ) {
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
                            val clickHandler = remember(episode.id, onEpisodeClick) { { onEpisodeClick(episode.id) } }
                            EpisodeItem(
                                episode = episode,
                                modifier = Modifier.clickable(onClick = clickHandler),
                                imageUrl = podcast.imageUrl,
                                downloadProgress = downloadProgressMap[episode.id] ?: 0,
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
