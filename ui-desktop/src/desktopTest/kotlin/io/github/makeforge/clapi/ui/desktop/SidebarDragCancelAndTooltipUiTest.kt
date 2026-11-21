package io.github.makeforge.clapi.ui.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.components.HoverTooltip
import io.github.makeforge.clapi.ui.shared.state.AppStateExamples
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class SidebarDragCancelAndTooltipUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── Bug 1: External secret error tooltip positioning & anti-flicker ──

    @Test
    fun multilineErrorHoverTooltip_doesNotOverlapBadgeOrFlicker() {
        val multilineError = (1..6).joinToString("\n") { "Connection refused to secret manager endpoint line $it" }

        composeRule.setContent {
            ClapiTheme {
                Box(modifier = Modifier.size(600.dp, 400.dp).padding(40.dp), contentAlignment = Alignment.TopCenter) {
                    HoverTooltip(
                        tooltip = multilineError,
                        tooltipTag = "env-secret-row-0-match-count-tooltip",
                        maxLines = 10,
                    ) {
                        Text(
                            text = "Error",
                            modifier = Modifier.testTag("env-secret-row-0-match-count"),
                        )
                    }
                }
            }
        }

        val badge = composeRule.onNodeWithTag("env-secret-row-0-match-count", useUnmergedTree = true)
        badge.assertIsDisplayed()
        badge.performMouseInput {
            enter(center)
            moveTo(center)
        }
        composeRule.mainClock.advanceTimeBy(350L)
        composeRule.waitForIdle()

        val tooltip = composeRule.onNodeWithTag("env-secret-row-0-match-count-tooltip", useUnmergedTree = true)
        tooltip.assertIsDisplayed()

        val badgeBounds = badge.getUnclippedBoundsInRoot()
        val tooltipBounds = tooltip.getUnclippedBoundsInRoot()
        val overlapsVertically = tooltipBounds.top < badgeBounds.bottom && tooltipBounds.bottom > badgeBounds.top
        assertFalse(
            overlapsVertically,
            "Error tooltip ($tooltipBounds) must not overlap the Error badge ($badgeBounds)",
        )

        // Keep pointer on badge and advance clock multiple times: tooltip must stay visible without flickering.
        repeat(4) {
            composeRule.mainClock.advanceTimeBy(250L)
            composeRule.waitForIdle()
            tooltip.assertIsDisplayed()
        }
    }

    // ── Bug 2: End-to-end UI tests for drag back to origin & ESC cancel ──

    @Test
    fun draggingRequestAwayAndBackOverOriginalPosition_retainsOriginalOrder() {
        val state = AppStateExamples.demo()
        state.collectionExpandedState["c1"] = true
        val originalOrder = state.collections.first().children.map { it.id }
        assertTrue(originalOrder.size >= 3, "Demo collection needs at least 3 requests")
        val firstId = originalOrder[0]
        val thirdId = originalOrder[2]

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        val firstBounds = composeRule.onNodeWithTag("collection-node-$firstId", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val thirdBounds = composeRule.onNodeWithTag("collection-node-$thirdId", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val dy = with(composeRule.density) { (thirdBounds.top - firstBounds.top).toPx() }

        composeRule.onNodeWithTag("collection-node-$firstId", useUnmergedTree = true).performMouseInput {
            moveTo(center)
            press()
            // Drag down past third item
            moveBy(Offset(0f, dy + 8f))
            // Drag back over original row
            moveBy(Offset(0f, -(dy + 8f)))
            release()
        }
        composeRule.waitForIdle()

        assertEquals(originalOrder, state.collections.first().children.map { it.id })
    }

    @Test
    fun draggingMultipleRequestsBackOverOriginalPosition_retainsOriginalOrder() {
        val state = AppStateExamples.demo()
        state.collectionExpandedState["c1"] = true
        val originalOrder = state.collections.first().children.map { it.id }
        assertTrue(originalOrder.size >= 3)
        val firstId = originalOrder[0]
        val thirdId = originalOrder[2]
        // Multi-select non-contiguous items [firstId, thirdId]
        state.selectedRequestIds.clear()
        state.selectedRequestIds.addAll(listOf(firstId, thirdId))

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        val firstBounds = composeRule.onNodeWithTag("collection-node-$firstId", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val thirdBounds = composeRule.onNodeWithTag("collection-node-$thirdId", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val dy = with(composeRule.density) { (thirdBounds.top - firstBounds.top).toPx() }

        composeRule.onNodeWithTag("collection-node-$firstId", useUnmergedTree = true).performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(0f, dy + 12f))
            moveBy(Offset(0f, -(dy + 12f)))
            release()
        }
        composeRule.waitForIdle()

        assertEquals(originalOrder, state.collections.first().children.map { it.id })
    }

    @Test
    fun pressingEscapeDuringSidebarRequestDrag_cancelsDragAndRetainsOriginalOrder() {
        val state = AppStateExamples.demo()
        state.collectionExpandedState["c1"] = true
        val originalOrder = state.collections.first().children.map { it.id }
        assertTrue(originalOrder.size >= 3)
        val firstId = originalOrder[0]
        val thirdId = originalOrder[2]

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        val firstBounds = composeRule.onNodeWithTag("collection-node-$firstId", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val thirdBounds = composeRule.onNodeWithTag("collection-node-$thirdId", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val dy = with(composeRule.density) { (thirdBounds.top - firstBounds.top).toPx() }

        // Start dragging first item down onto third item (without releasing!)
        composeRule.onNodeWithTag("collection-node-$firstId", useUnmergedTree = true).performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(0f, dy + 8f))
        }
        composeRule.waitForIdle()

        // Press Escape while dragging -> should cancel drag immediately
        composeRule.onNodeWithTag("main-screen", useUnmergedTree = true).performKeyInput {
            pressKey(Key.Escape)
        }
        composeRule.waitForIdle()

        // Drop indicators must be cleared
        composeRule.onAllNodesWithTag("drop-indicator-above-$thirdId", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithTag("drop-indicator-below-$thirdId", useUnmergedTree = true).assertCountEquals(0)

        // Release mouse button -> item must remain in its original position
        composeRule.onNodeWithTag("collection-node-$firstId", useUnmergedTree = true).performMouseInput {
            release()
        }
        composeRule.waitForIdle()

        assertEquals(originalOrder, state.collections.first().children.map { it.id })
    }

    @Test
    fun pressingEscapeDuringSidebarCollectionDrag_cancelsDragAndRetainsOriginalOrder() {
        val state = AppStateExamples.demo()
        val originalOrder = state.collections.map { it.id }
        assertTrue(originalOrder.size >= 2)
        val firstCol = originalOrder[0]
        val secondCol = originalOrder[1]

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        val firstBounds = composeRule.onNodeWithTag("collection-node-$firstCol", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val secondBounds = composeRule.onNodeWithTag("collection-node-$secondCol", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val dy = with(composeRule.density) { (secondBounds.top - firstBounds.top).toPx() }

        composeRule.onNodeWithTag("collection-node-$firstCol", useUnmergedTree = true).performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(0f, dy + 8f))
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("main-screen", useUnmergedTree = true).performKeyInput {
            pressKey(Key.Escape)
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("collection-drop-indicator-above-$secondCol", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithTag("collection-drop-indicator-below-$secondCol", useUnmergedTree = true).assertCountEquals(0)

        composeRule.onNodeWithTag("collection-node-$firstCol", useUnmergedTree = true).performMouseInput {
            release()
        }
        composeRule.waitForIdle()

        assertEquals(originalOrder, state.collections.map { it.id })
    }

    @Test
    fun collectionsHeader_hasExpandAndCollapseAllButtonsLeftOfAddWithTooltips() {
        val state = AppStateExamples.demo()
        val root = state.collections.first()
        val sub = io.github.makeforge.clapi.ui.shared.components.sidebar.addSubfolderInCollections(state.collections, root.id, "NestedSub")
            ?: error("Folder creation failed")
        state.collectionExpandedState[root.id] = true
        state.collectionExpandedState[sub.id] = true

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        val expandBtn = composeRule.onNodeWithTag("sidebar-collections-expand-all-button", useUnmergedTree = true)
        val collapseBtn = composeRule.onNodeWithTag("sidebar-collections-collapse-all-button", useUnmergedTree = true)
        val addBtn = composeRule.onNodeWithTag("sidebar-add-collection-button", useUnmergedTree = true)

        expandBtn.assertIsDisplayed()
        collapseBtn.assertIsDisplayed()
        addBtn.assertIsDisplayed()

        val expandBounds = expandBtn.getUnclippedBoundsInRoot()
        val collapseBounds = collapseBtn.getUnclippedBoundsInRoot()
        val addBounds = addBtn.getUnclippedBoundsInRoot()
        assertTrue(expandBounds.right <= collapseBounds.left + 1.dp, "Expand All should be left of Collapse All")
        assertTrue(collapseBounds.right <= addBounds.left + 1.dp, "Collapse All should be left of Add Collection")

        // Hover Expand All -> tooltip appears, then exit hover before next interaction
        expandBtn.performMouseInput {
            enter(center)
        }
        composeRule.mainClock.advanceTimeBy(300L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sidebar-collections-expand-all-tooltip", useUnmergedTree = true).assertIsDisplayed()
        expandBtn.performMouseInput {
            exit(Offset(-50f, -50f))
        }
        composeRule.waitForIdle()

        // Hover Collapse All -> tooltip appears, then exit hover
        collapseBtn.performMouseInput {
            enter(center)
        }
        composeRule.mainClock.advanceTimeBy(300L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sidebar-collections-collapse-all-tooltip", useUnmergedTree = true).assertIsDisplayed()
        collapseBtn.performMouseInput {
            exit(Offset(-50f, -50f))
        }
        composeRule.waitForIdle()

        // Click Collapse All -> collapses all folders in all collections
        collapseBtn.performClick()
        composeRule.waitForIdle()
        assertEquals(false, state.collectionExpandedState[root.id])
        assertEquals(false, state.collectionExpandedState[sub.id])

        // Click Expand All -> expands all folders in all collections
        expandBtn.performClick()
        composeRule.waitForIdle()
        assertEquals(true, state.collectionExpandedState[root.id])
        assertEquals(true, state.collectionExpandedState[sub.id])
    }

    @Test
    fun pressingEscape_clearsSidebarRequestAndFolderSelection() {
        val state = AppStateExamples.demo()
        state.collectionExpandedState["c1"] = true

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Click a request row (selecting + focusing it), then extend selection
        composeRule.onNodeWithTag("collection-node-r1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        state.selectedRequestIds.add("r2")
        state.lastSelectedSidebarItemId = "r2"
        assertTrue(state.hasSidebarSelection)

        composeRule.onNodeWithTag("collection-node-r1", useUnmergedTree = true).performKeyInput {
            pressKey(Key.Escape)
        }
        composeRule.waitForIdle()

        assertFalse(state.hasSidebarSelection)
        assertTrue(state.selectedRequestIds.isEmpty())
        assertTrue(state.selectedFolderIds.isEmpty())

        // Also clears folder selection
        composeRule.onNodeWithTag("collection-node-c1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        state.selectedFolderIds.add("c2")
        state.lastSelectedSidebarItemId = "c2"
        assertTrue(state.hasSidebarSelection)

        composeRule.onNodeWithTag("collection-node-c1", useUnmergedTree = true).performKeyInput {
            pressKey(Key.Escape)
        }
        composeRule.waitForIdle()

        assertFalse(state.hasSidebarSelection)
        assertTrue(state.selectedFolderIds.isEmpty())
    }
}
