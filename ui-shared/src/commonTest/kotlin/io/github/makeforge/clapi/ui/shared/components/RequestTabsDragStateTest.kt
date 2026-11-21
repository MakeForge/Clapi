package io.github.makeforge.clapi.ui.shared.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestTabsDragStateTest {

    private fun setupThreeTabs(dragState: RequestTabsDragState) {
        dragState.tabBounds[0] = TabBounds(index = 0, startX = 0f, width = 100f)
        dragState.tabBounds[1] = TabBounds(index = 1, startX = 100f, width = 100f)
        dragState.tabBounds[2] = TabBounds(index = 2, startX = 200f, width = 100f)
    }

    @Test
    fun initialState_isNotDragging() {
        val dragState = RequestTabsDragState()
        assertFalse(dragState.isDragging)
        assertNull(dragState.draggedTabIndex)
        assertNull(dragState.draggedTabId)
        assertEquals(0f, dragState.dragOffsetX)
        assertNull(dragState.dropTargetIndex)
    }

    @Test
    fun onDragStart_initializesDrag() {
        val dragState = RequestTabsDragState()
        dragState.onDragStart(index = 1, tabId = "tab-abc")

        assertTrue(dragState.isDragging)
        assertEquals(1, dragState.draggedTabIndex)
        assertEquals("tab-abc", dragState.draggedTabId)
        assertEquals(0f, dragState.dragOffsetX)
        assertEquals(1, dragState.dropTargetIndex)
    }

    @Test
    fun draggingRight_pastMidpointWithNextTab_targetsNextTab() {
        val dragState = RequestTabsDragState()
        setupThreeTabs(dragState)

        dragState.onDragStart(index = 0, tabId = "tab-0")
        // Tab 0 centerX = 50. Tab 1 centerX = 150. Midpoint = 100.
        // Dragging by +60 moves center to 110 >= 100 -> target is Tab 1.
        dragState.onDragDelta(60f)

        assertEquals(1, dragState.dropTargetIndex)
        assertTrue(dragState.showDropIndicatorRight(1))
        assertFalse(dragState.showDropIndicatorLeft(1))
    }

    @Test
    fun draggingRight_pastMidpointWithLastTab_targetsLastTab() {
        val dragState = RequestTabsDragState()
        setupThreeTabs(dragState)

        dragState.onDragStart(index = 0, tabId = "tab-0")
        // Tab 1 centerX = 150. Tab 2 centerX = 250. Midpoint = 200.
        // Dragging by +160 moves center to 210 >= 200 -> target is Tab 2.
        dragState.onDragDelta(160f)

        assertEquals(2, dragState.dropTargetIndex)
        assertTrue(dragState.showDropIndicatorRight(2))
    }

    @Test
    fun draggingLeft_pastMidpointWithPreviousTab_targetsPreviousTab() {
        val dragState = RequestTabsDragState()
        setupThreeTabs(dragState)

        dragState.onDragStart(index = 2, tabId = "tab-2")
        // Tab 2 centerX = 250. Tab 1 centerX = 150. Midpoint = 200.
        // Dragging by -60 moves center to 190 <= 200 -> target is Tab 1.
        dragState.onDragDelta(-60f)

        assertEquals(1, dragState.dropTargetIndex)
        assertTrue(dragState.showDropIndicatorLeft(1))
        assertFalse(dragState.showDropIndicatorRight(1))
    }

    @Test
    fun draggingLeft_pastMidpointWithFirstTab_targetsFirstTab() {
        val dragState = RequestTabsDragState()
        setupThreeTabs(dragState)

        dragState.onDragStart(index = 2, tabId = "tab-2")
        // Tab 1 centerX = 150. Tab 0 centerX = 50. Midpoint = 100.
        // Dragging by -160 moves center to 90 <= 100 -> target is Tab 0.
        dragState.onDragDelta(-160f)

        assertEquals(0, dragState.dropTargetIndex)
        assertTrue(dragState.showDropIndicatorLeft(0))
    }

    @Test
    fun smallDrag_whereCenterDoesNotCrossMidpoint_showsNoDropIndicator() {
        val dragState = RequestTabsDragState()
        setupThreeTabs(dragState)

        dragState.onDragStart(index = 0, tabId = "tab-0")
        // Tab 0 centerX = 50. Midpoint with Tab 1 is 100.
        // Dragging by +20 moves center to 70 < 100 -> stays target 0.
        dragState.onDragDelta(20f)

        assertEquals(0, dragState.dropTargetIndex)
        assertFalse(dragState.showDropIndicatorRight(0))
        assertFalse(dragState.showDropIndicatorLeft(0))
    }

    @Test
    fun onDragEnd_invokesOnMove_whenDestinationDiffers() {
        val dragState = RequestTabsDragState()
        setupThreeTabs(dragState)

        dragState.onDragStart(index = 0, tabId = "tab-0")
        dragState.onDragDelta(160f) // moves to index 2

        var movedFrom: Int? = null
        var movedTo: Int? = null

        dragState.onDragEnd { from, to ->
            movedFrom = from
            movedTo = to
        }

        assertEquals(0, movedFrom)
        assertEquals(2, movedTo)
        assertFalse(dragState.isDragging)
        assertNull(dragState.draggedTabIndex)
    }

    @Test
    fun onDragEnd_doesNotInvokeOnMove_whenDroppedAtSamePosition() {
        val dragState = RequestTabsDragState()
        setupThreeTabs(dragState)

        dragState.onDragStart(index = 1, tabId = "tab-1")
        dragState.onDragDelta(5f) // tiny movement, remains index 1

        var moveCalled = false
        dragState.onDragEnd { _, _ -> moveCalled = true }

        assertFalse(moveCalled)
        assertFalse(dragState.isDragging)
    }

    @Test
    fun reset_clearsAllState() {
        val dragState = RequestTabsDragState()
        setupThreeTabs(dragState)

        dragState.onDragStart(index = 0, tabId = "tab-0")
        dragState.onDragDelta(150f)
        dragState.reset()

        assertFalse(dragState.isDragging)
        assertNull(dragState.draggedTabIndex)
        assertNull(dragState.draggedTabId)
        assertEquals(0f, dragState.dragOffsetX)
        assertNull(dragState.dropTargetIndex)
    }
}
