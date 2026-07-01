package com.yuval.podcasts.ui.screens

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class DragDropStateTest {

    private fun item(index: Int, key: String, offset: Int, size: Int = 100): LazyListItemInfo =
        mockk {
            every { this@mockk.index } returns index
            every { this@mockk.key } returns key
            every { this@mockk.offset } returns offset
            every { this@mockk.size } returns size
        }

    /** A LazyListState whose visible items are the given fakes (uniform 100px rows). */
    private fun stateWith(items: List<LazyListItemInfo>): LazyListState {
        val layoutInfo = mockk<LazyListLayoutInfo> { every { visibleItemsInfo } returns items }
        return mockk<LazyListState> { every { this@mockk.layoutInfo } returns layoutInfo }
    }

    @Test
    fun onDrag_singleStepJump_pinsOffsetToFinger() {
        val moves = mutableListOf<Pair<Int, Int>>()
        val listState = stateWith(
            listOf(item(0, "a", 0), item(1, "b", 100), item(2, "c", 200))
        )
        val drag = DragDropState(listState, onMove = { from, to -> moves.add(from to to) })

        drag.onDragStart("a")
        // Finger moves down 60px: center = 0 + 60 + 50 = 110 -> lands in item "b" [100,200].
        drag.onDrag(60f)

        assertEquals(listOf(0 to 1), moves)
        // Item "b" (index 1) sits at layout offset 100; to stay pinned to the finger (at +60)
        // the translation must be 60 - 100 = -40.
        assertEquals(-40f, drag.draggingItemOffset, 0.001f)
    }

    @Test
    fun onDrag_multiItemJump_doesNotDrift() {
        val moves = mutableListOf<Pair<Int, Int>>()
        val listState = stateWith(
            listOf(item(0, "a", 0), item(1, "b", 100), item(2, "c", 200), item(3, "d", 300))
        )
        val drag = DragDropState(listState, onMove = { from, to -> moves.add(from to to) })

        drag.onDragStart("a")
        // A fast drag of 250px in one event: center = 0 + 250 + 50 = 300, which lands in item
        // "c" (index 2) -> a two-position jump.
        drag.onDrag(250f)

        assertEquals(listOf(0 to 2), moves)
        // After moving to index 2 the dragged item's layout offset becomes 200. To stay pinned to
        // the finger (at +250) the translation must be 250 - 200 = 50. The old single-item-size
        // compensation produced 150 here (a full row of drift).
        assertEquals(50f, drag.draggingItemOffset, 0.001f)
    }
}
