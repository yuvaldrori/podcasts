package com.yuval.podcasts.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.ui.components.EpisodeItem
import com.yuval.podcasts.ui.viewmodel.PlayerViewModel
import com.yuval.podcasts.ui.viewmodel.QueueViewModel
import com.yuval.podcasts.ui.viewmodel.QueueUiState
import kotlinx.coroutines.delay

import androidx.compose.ui.res.stringResource
import com.yuval.podcasts.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    uiState: QueueUiState,
    isPlaying: Boolean,
    currentMediaId: String?,
    onEpisodeClick: (String) -> Unit,
    onRemoveFromQueue: (String) -> Unit,
    onReorderQueue: (List<String>) -> Unit,
    onPlayQueue: (List<com.yuval.podcasts.data.db.entity.Episode>, Int, Long) -> Unit
) {
    if (uiState is QueueUiState.Loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    
    val successState = uiState as QueueUiState.Success
    val dbQueue = successState.queue
    var queue by remember { mutableStateOf(emptyList<EpisodeWithPodcast>()) }

    val queueTimeRemainingMs = successState.queueTimeRemaining

    val listState = rememberLazyListState()
    val dragDropState = rememberDragDropState(listState) { fromIndex, toIndex ->
        queue = queue.toMutableList().apply { 
            add(toIndex, removeAt(fromIndex)) 
        }
    }

    LaunchedEffect(dbQueue) { 
        if (dragDropState.draggedItemIndex == null) {
            queue = dbQueue 
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (queue.isNotEmpty()) {
            Text(
                text = stringResource(R.string.queue_listening_time, formatQueueTime(queueTimeRemainingMs)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = 16.dp, 
                end = 16.dp, 
                top = if (queue.isNotEmpty()) 0.dp else 16.dp, 
                bottom = 16.dp
            )
        ) {
            itemsIndexed(queue, key = { _, item -> item.episode.id }) { index, episodeWithPodcast ->
                val isDragging = dragDropState.draggedItemIndex == index
                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
                
                val onDismiss = remember(episodeWithPodcast.episode.id) {
                    { value: SwipeToDismissBoxValue ->
                        if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                            onRemoveFromQueue(episodeWithPodcast.episode.id)
                            true
                        } else {
                            false
                        }
                    }
                }
                
                SwipeToDismissBox(
                    state = rememberSwipeToDismissBoxState(
                        confirmValueChange = onDismiss
                    ),
                    modifier = Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            if (isDragging) {
                                translationY = dragDropState.draggingItemOffset
                            }
                            shadowElevation = elevation.toPx()
                        }
                        .animateItem(), // Native Compose 1.7+ feature for layout animations
                    backgroundContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 4.dp),
                        )
                    },
                    content = {
                        val clickHandler = remember(episodeWithPodcast.episode.id) { 
                            { onEpisodeClick(episodeWithPodcast.episode.id) } 
                        }
                        val isCurrentlyPlaying = episodeWithPodcast.episode.id == currentMediaId
                        EpisodeItem(
                            episode = episodeWithPodcast.episode,
                            modifier = Modifier.clickable(onClick = clickHandler),
                            imageUrl = episodeWithPodcast.podcast.imageUrl,
                            containerColor = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { 
                                        val episodes = queue.map { it.episode }
                                        val startIndex = episodes.indexOfFirst { it.id == episodeWithPodcast.episode.id }
                                        if (startIndex != -1) {
                                            onPlayQueue(episodes, startIndex, episodeWithPodcast.episode.lastPlayedPosition)
                                        }
                                    }) {
                                        Icon(
                                            imageVector = if (isCurrentlyPlaying && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isCurrentlyPlaying && isPlaying) stringResource(R.string.pause) else stringResource(R.string.play)
                                        )
                                    }
                                    IconButton(onClick = { onRemoveFromQueue(episodeWithPodcast.episode.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .pointerInput(episodeWithPodcast.episode.id) {
                                                detectVerticalDragGestures(
                                                    onDragStart = { 
                                                        val currentIndex = queue.indexOfFirst { it.episode.id == episodeWithPodcast.episode.id }
                                                        if (currentIndex != -1) {
                                                            dragDropState.onDragStart(currentIndex)
                                                        }
                                                    },
                                                    onDragEnd = { 
                                                        dragDropState.onDragEnd()
                                                        onReorderQueue(queue.map { it.episode.id })
                                                    },
                                                    onDragCancel = { dragDropState.onDragEnd() },
                                                    onVerticalDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragDropState.onDrag(dragAmount)
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.DragHandle,
                                            contentDescription = stringResource(R.string.reorder)
                                        )
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

class DragDropState(
    private val lazyListState: LazyListState,
    private val onMove: (Int, Int) -> Unit
) {
    var draggedItemIndex by mutableStateOf<Int?>(null)
        private set
        
    var draggingItemOffset by mutableFloatStateOf(0f)
        private set

    fun onDragStart(index: Int) {
        draggedItemIndex = index
        draggingItemOffset = 0f
    }

    fun onDrag(dragAmount: Float) {
        val currentIndex = draggedItemIndex ?: return
        val currentItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex } ?: return

        draggingItemOffset += dragAmount

        // Check if our center crosses the bounds of another item
        val center = currentItem.offset + draggingItemOffset + currentItem.size / 2f
        
        val targetItem = lazyListState.layoutInfo.visibleItemsInfo.find { item ->
            item.index != currentIndex && 
            center in item.offset.toFloat()..(item.offset + item.size).toFloat()
        }

        if (targetItem != null) {
            val targetIndex = targetItem.index
            onMove(currentIndex, targetIndex)
            draggedItemIndex = targetIndex
            
            // Adjust the offset to counteract the layout shift caused by the swap
            val offsetAdjustment = if (targetIndex > currentIndex) -targetItem.size else targetItem.size
            draggingItemOffset += offsetAdjustment.toFloat()
        }
    }

    fun onDragEnd() {
        draggedItemIndex = null
        draggingItemOffset = 0f
    }
}

@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    onMove: (Int, Int) -> Unit
): DragDropState {
    return remember(lazyListState) {
        DragDropState(lazyListState, onMove)
    }
}

private fun formatQueueTime(ms: Long): String {
    if (ms <= 0) return "0 mins"
    val minutes = (ms / (1000 * 60)) % 60
    val hours = ms / (1000 * 60 * 60)
    
    return when {
        hours > 0 && minutes > 0 -> "$hours hrs $minutes mins"
        hours > 0 -> "$hours hrs"
        else -> "$minutes mins"
    }
}
