package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import io.github.makeforge.clapi.ui.shared.components.sidebar.SidebarDragState
import io.github.makeforge.clapi.ui.shared.components.sidebar.TreeNodeHitArea
import io.github.makeforge.clapi.ui.shared.components.sidebar.resolveFolderDropTarget
import io.github.makeforge.clapi.ui.shared.components.sidebar.resolveRequestDropTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SidebarDragAndTooltipTest {

    @Test
    fun tooltipPositionProvider_placesTallPopupStrictlyBelowOrAboveAnchorWithoutOverlap() {
        val provider = TooltipPopupPositionProvider(gapPx = 6, marginPx = 8)
        val windowSize = IntSize(width = 800, height = 600)
        val tallPopup = IntSize(width = 320, height = 140)

        // Anchor in upper half -> popup sits strictly below anchor
        val upperAnchor = IntRect(left = 200, top = 100, right = 260, bottom = 124)
        val belowPos = provider.calculatePosition(upperAnchor, windowSize, LayoutDirection.Ltr, tallPopup)
        assertTrue(
            belowPos.y >= upperAnchor.bottom + 6,
            "Expected popup top (${belowPos.y}) to be below anchor bottom (${upperAnchor.bottom} + 6)",
        )

        // Anchor near bottom -> popup flips strictly above anchor
        val bottomAnchor = IntRect(left = 200, top = 540, right = 260, bottom = 564)
        val abovePos = provider.calculatePosition(bottomAnchor, windowSize, LayoutDirection.Ltr, tallPopup)
        assertTrue(
            abovePos.y + tallPopup.height <= bottomAnchor.top - 6,
            "Expected popup bottom (${abovePos.y + tallPopup.height}) to be above anchor top (${bottomAnchor.top} - 6)",
        )

        // Horizontal clamping near left & right edges
        val leftAnchor = IntRect(left = 0, top = 100, right = 40, bottom = 124)
        val leftPos = provider.calculatePosition(leftAnchor, windowSize, LayoutDirection.Ltr, tallPopup)
        assertEquals(8, leftPos.x)

        val rightAnchor = IntRect(left = 760, top = 100, right = 800, bottom = 124)
        val rightPos = provider.calculatePosition(rightAnchor, windowSize, LayoutDirection.Ltr, tallPopup)
        assertEquals(800 - 320 - 8, rightPos.x)
    }

    @Test
    fun resolveRequestDropTarget_returnsNullWhenHoveringBackOverOwnOriginalPosition() {
        val areas = listOf(
            TreeNodeHitArea("c1", isFolder = true, parentCollectionId = null, top = 0f, bottom = 30f, label = "Users"),
            TreeNodeHitArea("r1", isFolder = false, parentCollectionId = "c1", top = 30f, bottom = 60f, label = "Req 1"),
            TreeNodeHitArea("r2", isFolder = false, parentCollectionId = "c1", top = 60f, bottom = 90f, label = "Req 2"),
            TreeNodeHitArea("r3", isFolder = false, parentCollectionId = "c1", top = 90f, bottom = 120f, label = "Req 3"),
        )

        // Dragging r1 away to r3 resolves r3
        val awayTarget = resolveRequestDropTarget(areas, listOf("r1"), cursorY = 110f)
        assertNotNull(awayTarget)
        assertEquals("r3", awayTarget.requestId)
        assertTrue(awayTarget.insertAfter)

        // Dragging r1 back over r1's own bounds must return null (not snap to parent folder c1!)
        assertNull(resolveRequestDropTarget(areas, listOf("r1"), cursorY = 42f))

        // Dragging multiple selected requests [r1, r3] back over r1 OR r3 returns null
        assertNull(resolveRequestDropTarget(areas, listOf("r1", "r3"), cursorY = 45f))
        assertNull(resolveRequestDropTarget(areas, listOf("r1", "r3"), cursorY = 105f))
    }

    @Test
    fun resolveFolderDropTarget_returnsNullWhenHoveringBackOverOwnOriginalPosition() {
        val areas = listOf(
            TreeNodeHitArea("f1", isFolder = true, parentCollectionId = "c1", top = 30f, bottom = 60f, label = "F1"),
            TreeNodeHitArea("f2", isFolder = true, parentCollectionId = "c1", top = 60f, bottom = 90f, label = "F2"),
            TreeNodeHitArea("f3", isFolder = true, parentCollectionId = "c1", top = 90f, bottom = 120f, label = "F3"),
        )

        val awayTarget = resolveFolderDropTarget(areas, listOf("f1"), "c1", cursorY = 110f) { true }
        assertNotNull(awayTarget)
        assertEquals("f3", awayTarget.targetFolderId)

        assertNull(resolveFolderDropTarget(areas, listOf("f1"), "c1", cursorY = 45f) { true })
        assertNull(resolveFolderDropTarget(areas, listOf("f1", "f3"), "c1", cursorY = 45f) { true })
        assertNull(resolveFolderDropTarget(areas, listOf("f1", "f3"), "c1", cursorY = 105f) { true })
    }

    @Test
    fun sidebarDragState_cancelDragClearsAllDragAndDropFields() {
        val dragState = SidebarDragState().apply {
            draggedRequestId = "r1"
            dragOffsetY = 50f
            dragCursorAbsY = 120f
            dropTargetCollectionId = "c1"
            dropTargetRequestId = "r3"
            dropInsertAfter = true
            draggedCollectionId = "c1"
            dropCollectionTargetId = "c2"
            dropCollectionInsertAfter = true
        }
        assertTrue(dragState.isDragging)

        dragState.cancelDrag()

        assertFalse(dragState.isDragging)
        assertNull(dragState.draggedRequestId)
        assertNull(dragState.dropTargetRequestId)
        assertNull(dragState.dropTargetCollectionId)
        assertNull(dragState.draggedCollectionId)
        assertNull(dragState.dropCollectionTargetId)
        assertFalse(dragState.dropInsertAfter)
        assertFalse(dragState.dropCollectionInsertAfter)
    }
}
