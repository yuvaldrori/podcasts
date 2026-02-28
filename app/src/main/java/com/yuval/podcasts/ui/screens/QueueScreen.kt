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
import androidx.compose.material.icons.filled.PlayArrow
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
import com.yuval.podcasts.ui.viewmodel.QueueViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    onEpisodeClick: (String) -> Unit,
    viewModel: QueueViewModel = hiltViewModel()
) {
    val dbQueue by viewModel.queue.collectAsStateWithLifecycle()
    var queue by remember { mutableStateOf(emptyList<EpisodeWithPodcast>()) }
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val dragDropState = rememberDragDropState(listState) { fromIndex, toIndex ->
        queue = queue.toMutableList().apply { 
            add(toIndex, removeAt(fromIndex)) 
        }
    }

    LaunchedEffect(dbQueue) { 
        // Only refresh the list from the database if we are NOT currently dragging
        if (dragDropState.draggedItemIndex == null) {
            queue = dbQueue 
        }
    }

    // Periodically update position when playing
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            viewModel.updatePosition()
            delay(1000)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp)
        ) {
            itemsIndexed(queue, key = { _, item -> item.episode.id }) { index, episodeWithPodcast ->
                val isDragging = dragDropState.draggedItemIndex == index
                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
                
                val onDismiss = remember(episodeWithPodcast.episode.id) {
                    { value: SwipeToDismissBoxValue ->
                        if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                            viewModel.removeFromQueue(episodeWithPodcast.episode.id)
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
                        Surface(tonalElevation = elevation) {
                            val clickHandler = remember(episodeWithPodcast.episode.id) { 
                                { onEpisodeClick(episodeWithPodcast.episode.id) } 
                            }
                            EpisodeItem(
                                episode = episodeWithPodcast.episode,
                                modifier = Modifier.clickable(onClick = clickHandler),
                                imageUrl = episodeWithPodcast.podcast.imageUrl,
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { viewModel.play(episodeWithPodcast.episode) }) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .pointerInput(episodeWithPodcast.episode.id) {
                                                    detectVerticalDragGestures(
                                                        onDragStart = { 
                                                            // Dynamically find index to avoid stale captures
                                                            val currentIndex = queue.indexOfFirst { it.episode.id == episodeWithPodcast.episode.id }
                                                            if (currentIndex != -1) {
                                                                dragDropState.onDragStart(currentIndex)
                                                            }
                                                        },
                                                        onDragEnd = { 
                                                            dragDropState.onDragEnd()
                                                            viewModel.reorderQueue(queue.map { it.episode.id })
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
                                                contentDescription = "Reorder"
                                            )
                                        }
                                    }
                                }
                            )
                        }
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
        
    var draggingItemOffset by mutableStateOf(0f)
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