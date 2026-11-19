package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

/**
 * Bounds of a tab chip within the horizontally scrollable row layout coordinates.
 */
data class TabBounds(
    val index: Int,
    val startX: Float,
    val width: Float,
) {
    val endX: Float get() = startX + width
    val centerX: Float get() = startX + width / 2f
}

/**
 * State holder managing drag-and-drop horizontal reordering of open request tabs.
 */
class RequestTabsDragState {
    var draggedTabIndex by mutableStateOf<Int?>(null)
    var draggedTabId by mutableStateOf<String?>(null)
    var dragOffsetX by mutableStateOf(0f)
    var dropTargetIndex by mutableStateOf<Int?>(null)

    val tabBounds = mutableStateMapOf<Int, TabBounds>()

    val isDragging: Boolean get() = draggedTabIndex != null

    fun onDragStart(index: Int, tabId: String) {
        draggedTabIndex = index
        draggedTabId = tabId
        dragOffsetX = 0f
        dropTargetIndex = index
    }

    fun onDragDelta(deltaX: Float) {
        val fromIndex = draggedTabIndex ?: return
        dragOffsetX += deltaX

        val fromBounds = tabBounds[fromIndex] ?: return
        val currentCenter = fromBounds.centerX + dragOffsetX

        val sortedTabs = tabBounds.values.sortedBy { it.index }
        if (sortedTabs.size <= 1) return

        var target = fromIndex

        if (currentCenter < fromBounds.centerX) {
            // Dragging LEFT: check tabs to the left
            for (i in (fromIndex - 1) downTo 0) {
                val currentTab = sortedTabs.getOrNull(i) ?: continue
                val nextTab = sortedTabs.getOrNull(i + 1) ?: continue
                val mid = (currentTab.centerX + nextTab.centerX) / 2f
                if (currentCenter <= mid) {
                    target = i
                } else {
                    break
                }
            }
        } else if (currentCenter > fromBounds.centerX) {
            // Dragging RIGHT: check tabs to the right
            for (i in (fromIndex + 1)..sortedTabs.lastIndex) {
                val prevTab = sortedTabs.getOrNull(i - 1) ?: continue
                val currentTab = sortedTabs.getOrNull(i) ?: continue
                val mid = (prevTab.centerX + currentTab.centerX) / 2f
                if (currentCenter >= mid) {
                    target = i
                } else {
                    break
                }
            }
        }

        dropTargetIndex = target
    }

    fun showDropIndicatorLeft(index: Int): Boolean {
        if (!isDragging) return false
        val from = draggedTabIndex ?: return false
        val target = dropTargetIndex ?: return false
        if (target == from) return false
        return target < from && target == index
    }

    fun showDropIndicatorRight(index: Int): Boolean {
        if (!isDragging) return false
        val from = draggedTabIndex ?: return false
        val target = dropTargetIndex ?: return false
        if (target == from) return false
        return target > from && target == index
    }

    fun onDragEnd(onMove: (fromIndex: Int, toIndex: Int) -> Unit) {
        val from = draggedTabIndex
        val to = dropTargetIndex
        if (from != null && to != null && from != to) {
            onMove(from, to)
        }
        reset()
    }

    fun reset() {
        draggedTabIndex = null
        draggedTabId = null
        dragOffsetX = 0f
        dropTargetIndex = null
    }
}

/**
 * Modifier detecting horizontal drag gestures for reordering tabs.
 * Uses pointer lock via `horizontalDrag` to track movement reliably across
 * adjacent tabs and boundaries without being intercepted by child components.
 * Only triggers on primary mouse button press past drag slop.
 */
fun Modifier.draggableTab(
    enabled: Boolean = true,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
): Modifier {
    if (!enabled) return this
    return this.pointerInput(enabled) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!currentEvent.buttons.isPrimaryPressed || currentEvent.buttons.isSecondaryPressed) {
                return@awaitEachGesture
            }
            var overSlop = 0f
            val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                change.consume()
                overSlop = over
            }
            if (drag != null) {
                onDragStart()
                val initialDelta = drag.position.x - down.position.x
                onDragDelta(if (initialDelta != 0f) initialDelta else overSlop)
                val success = horizontalDrag(drag.id) { change ->
                    val dx = change.positionChange().x
                    change.consume()
                    if (dx != 0f) {
                        onDragDelta(dx)
                    }
                }
                if (success) {
                    onDragEnd()
                } else {
                    onDragCancel()
                }
            }
        }
    }
}
