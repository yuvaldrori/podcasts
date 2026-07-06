package com.yuval.podcasts.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material3.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.yuval.podcasts.R
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.ui.components.EpisodeItem
import com.yuval.podcasts.ui.viewmodel.QueueUiState
import com.yuval.podcasts.ui.components.LoadingBox
import com.yuval.podcasts.ui.utils.Formatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    uiState: QueueUiState,
    queueTimeRemaining: Long,
    isPlaying: Boolean,
    currentMediaId: String?,
    isRefreshing: Boolean = false,
    refreshProgress: Pair<Int, Int>? = null,
    onRefreshAll: () -> Unit = {},
    onEpisodeClick: (String) -> Unit,
    onRemoveFromQueue: (String) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onCommitReorder: () -> Unit,
    onPlayQueue: (List<Episode>, Int, Long) -> Unit,
    onPause: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    if (uiState is QueueUiState.Loading) {
        LoadingBox()
        return
    }

    val successState = uiState as QueueUiState.Success
    val lazyListState = rememberLazyListState()
    
    val queue = successState.queue

    val dragDropState = rememberDragDropState(
        lazyListState = lazyListState,
        onMove = onMoveItem,
        onDragEnd = onCommitReorder
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.queue_title))
                },
                actions = {
                    Text(
                        text = stringResource(R.string.queue_listening_time, Formatter.formatRemainingTime(queueTimeRemaining)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        }
    ) { padding ->
        val pullToRefreshState = rememberPullToRefreshState()
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
                if (refreshProgress != null) {
                    val (current, total) = refreshProgress
                    LinearProgressIndicator(
                        progress = { if (total > 0) current.toFloat() / total.toFloat() else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                } else if (isRefreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                }
                
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .testTag("queue_list"),
                    contentPadding = PaddingValues(
                        top = 16.dp,
                        bottom = contentPadding.calculateBottomPadding() + 16.dp,
                        start = 16.dp,
                        end = 16.dp
                    )
                ) {
                if (queue.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxHeight()
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.empty_queue_message),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(32.dp)
                            )
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(1.dp))
                    }
                } else {
                    itemsIndexed(queue, key = { _, item -> item.episode.id }) { index, episodeWithPodcast ->
                        val episode = episodeWithPodcast.episode
                        val isDragging = dragDropState.draggedItemIndex == index
                        val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    if (isDragging) {
                                        translationY = dragDropState.draggingItemOffset
                                    }
                                    shadowElevation = elevation.toPx()
                                }
                                .let {
                                    if (isDragging) it else it.animateItem()
                                }
                        ) {
                            val isCurrent = episode.id == currentMediaId
                            val containerColor = if (isCurrent) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }

                            val clickHandler = remember(episode.id) { { onEpisodeClick(episode.id) } }
                            EpisodeItem(
                                episode = episode,
                                modifier = Modifier.clickable(onClick = clickHandler),
                                imageUrl = episodeWithPodcast.podcast.imageUrl,
                                containerColor = containerColor,
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val isThisEpisodePlaying = isPlaying && currentMediaId == episode.id
                                        IconButton(onClick = { 
                                            if (isThisEpisodePlaying) {
                                                onPause()
                                            } else {
                                                onPlayQueue(queue.map { it.episode }, index, episode.lastPlayedPosition)
                                            }
                                        }) {
                                            val playIcon = if (isThisEpisodePlaying) Icons.Default.Pause else Icons.Default.PlayArrow
                                            val description = stringResource(if (isThisEpisodePlaying) R.string.pause else R.string.play)
                                            Icon(playIcon, contentDescription = description)
                                        }
                                        IconButton(onClick = { onRemoveFromQueue(episode.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove_from_queue_action))
                                        }
                                        Icon(
                                            imageVector = Icons.Default.Reorder,
                                            contentDescription = stringResource(R.string.reorder),
                                            modifier = Modifier
                                                .padding(8.dp)
                                                .testTag("reorder_handle_${episode.id}")
                                                .dragContainer(episode.id, dragDropState)
                                        )
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
