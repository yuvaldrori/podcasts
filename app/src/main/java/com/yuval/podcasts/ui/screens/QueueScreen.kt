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
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    isPlaying: Boolean,
    currentMediaId: String?,
    onEpisodeClick: (String) -> Unit,
    onRemoveFromQueue: (String) -> Unit,
    onReorderQueue: (List<String>) -> Unit,
    onPlayQueue: (List<Episode>, Int, Long) -> Unit
) {
    if (uiState is QueueUiState.Loading) {
        LoadingBox()
        return
    }

    val successState = uiState as QueueUiState.Success
    val lazyListState = rememberLazyListState()
    
    val dragDropState = rememberDragDropState(lazyListState, onMove = { fromIndex, toIndex ->
        val newList = successState.queue.toMutableList()
        val item = newList.removeAt(fromIndex)
        newList.add(toIndex, item)
        onReorderQueue(newList.map { it.episode.id })
    })

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = {
            val queueTimeRemaining = Formatter.formatRemainingTime(successState.queueTimeRemaining)
            TopAppBar(
                title = { 
                    Column {
                        Text(stringResource(R.string.history_title).replace("History", "Queue"))
                        if (successState.queue.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.queue_listening_time, queueTimeRemaining),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (successState.queue.isNotEmpty() && !isPlaying) {
                        IconButton(onClick = { onPlayQueue(successState.queue.map { it.episode }, 0, 0) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play))
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (successState.queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.empty_queue_message),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag("queue_list"),
                contentPadding = PaddingValues(16.dp)
            ) {
                itemsIndexed(successState.queue, key = { _, item -> item.episode.id }) { index, episodeWithPodcast ->
                    val episode = episodeWithPodcast.episode
                    val isDragging = dragDropState.draggedItemIndex == index
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
                    
                    val dismissState = rememberSwipeToDismissBoxState()
                    LaunchedEffect(dismissState.currentValue) {
                        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                            onRemoveFromQueue(episode.id)
                        }
                    }

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                if (isDragging) {
                                    translationY = dragDropState.draggingItemOffset
                                }
                                shadowElevation = elevation.toPx()
                            }
                            .animateItem(), 
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 4.dp)
                                    .background(MaterialTheme.colorScheme.errorContainer),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.remove_from_queue_action),
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        },
                        content = {
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
                                    Icon(
                                        imageVector = Icons.Default.Reorder,
                                        contentDescription = stringResource(R.string.reorder),
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .dragContainer(index, dragDropState)
                                    )
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}
