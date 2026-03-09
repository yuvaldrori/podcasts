package com.yuval.podcasts.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*

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
