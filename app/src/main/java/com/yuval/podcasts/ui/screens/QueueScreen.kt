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
    
    var queue by remember { mutableStateOf(successState.queue.toList()) }

    val dragDropState = rememberDragDropState(
        lazyListState = lazyListState,
        onMove = { fromIndex, toIndex ->
            queue = queue.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
        },
        onDragEnd = {
            onReorderQueue(queue.map { it.episode.id })
        }
    )

    LaunchedEffect(successState.queue, dragDropState.draggedItemIndex) {
        if (dragDropState.draggedItemIndex == null) {
            queue = successState.queue.toList()
        }
    }

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = {
            val queueTimeRemaining = Formatter.formatRemainingTime(successState.queueTimeRemaining)
            TopAppBar(
                title = { 
                    Column {
                        Text(stringResource(R.string.queue_title))
                        if (queue.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.queue_listening_time, queueTimeRemaining),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {}
            )
        }
    ) { padding ->
        if (queue.isEmpty()) {
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
                            .animateItem()
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
                                        onPlayQueue(queue.map { it.episode }, index, episode.lastPlayedPosition) 
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
                                    )                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
