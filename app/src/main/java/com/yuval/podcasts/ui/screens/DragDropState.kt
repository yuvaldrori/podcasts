package com.yuval.podcasts.ui.screens

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

class DragDropState(
    private val lazyListState: LazyListState,
    private val onMove: (Int, Int) -> Unit,
    private val onDragEndAction: () -> Unit = {}
) {
    var draggedItemIndex by mutableStateOf<Int?>(null)
        private set
        
    var draggingItemOffset by mutableFloatStateOf(0f)
        private set

    fun onDragStart(key: Any) {
        lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == key }
            ?.let { item ->
                draggedItemIndex = item.index
                draggingItemOffset = 0f
            }
    }

    fun onDrag(dragAmount: Float) {
        val currentIndex = draggedItemIndex ?: return
        val currentItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex } ?: return

        draggingItemOffset += dragAmount

        // Calculate the visual center of the dragged item
        val center = currentItem.offset + draggingItemOffset + currentItem.size / 2f
        
        // Find the item that currently occupies the space where our center is
        val targetItem = lazyListState.layoutInfo.visibleItemsInfo.find { item ->
            item.index != currentIndex && 
            center in item.offset.toFloat()..(item.offset + item.size).toFloat()
        }

        if (targetItem != null) {
            val targetIndex = targetItem.index
            onMove(currentIndex, targetIndex)
            draggedItemIndex = targetIndex

            // Adjust the offset to counteract the layout shift caused by the move, keeping the
            // item visually pinned to the finger. Compensate by the difference between the
            // dragged item's current and target layout offsets so this stays correct when a fast
            // drag jumps several positions at once (and for non-uniform item heights) rather than
            // assuming a single item-height shift.
            draggingItemOffset += (currentItem.offset - targetItem.offset).toFloat()
        }
    }

    fun onDragEnd() {
        draggedItemIndex = null
        draggingItemOffset = 0f
        onDragEndAction()
    }
}

@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    onMove: (Int, Int) -> Unit,
    onDragEnd: () -> Unit = {}
): DragDropState {
    return remember(lazyListState) {
        DragDropState(lazyListState, onMove, onDragEnd)
    }
}

fun Modifier.dragContainer(
    itemKey: Any,
    dragDropState: DragDropState
): Modifier {
    return this.pointerInput(itemKey, dragDropState) {
        detectDragGestures(
            onDragStart = { dragDropState.onDragStart(itemKey) },
            onDrag = { change, dragAmount ->
                change.consume()
                dragDropState.onDrag(dragAmount.y)
            },
            onDragEnd = { dragDropState.onDragEnd() },
            onDragCancel = { dragDropState.onDragEnd() }
        )
    }
}
