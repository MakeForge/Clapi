package io.github.makeforge.clapi.ui.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.state.AppState
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TabDragDropUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dragging_tab_right_reorders_tabs_in_state() {
        val state = AppState().apply {
            openTabs[0].name = "Tab A"
            addTab(name = "Tab B", method = HttpMethodType.POST)
            addTab(name = "Tab C", method = HttpMethodType.PUT)
        }
        val tabA = state.openTabs[0]
        val tabB = state.openTabs[1]
        val tabC = state.openTabs[2]

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Drag Tab A horizontally to the right across Tab B
        composeRule.onNodeWithTag("tab-chip-${tabA.id}").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(100f, 0f))
            release()
        }
        composeRule.waitForIdle()

        // Tab A should now be at index 1, Tab B at index 0, Tab C at index 2
        assertEquals(tabB.id, state.openTabs[0].id)
        assertEquals(tabA.id, state.openTabs[1].id)
        assertEquals(tabC.id, state.openTabs[2].id)
        assertEquals(1, state.activeTabIndex)
    }

    @Test
    fun dragging_middle_tab_right_reorders_to_end() {
        val state = AppState().apply {
            openTabs[0].name = "Tab A"
            addTab(name = "Tab B", method = HttpMethodType.POST)
            addTab(name = "Tab C", method = HttpMethodType.PUT)
        }
        val tabA = state.openTabs[0]
        val tabB = state.openTabs[1]
        val tabC = state.openTabs[2]

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Drag Tab B horizontally to the right across Tab C
        composeRule.onNodeWithTag("tab-chip-${tabB.id}").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(120f, 0f))
            release()
        }
        composeRule.waitForIdle()

        // Tab B should now be at index 2 (last), Tab C at index 1, Tab A at index 0
        assertEquals(tabA.id, state.openTabs[0].id)
        assertEquals(tabC.id, state.openTabs[1].id)
        assertEquals(tabB.id, state.openTabs[2].id)
        assertEquals(2, state.activeTabIndex)
    }

    @Test
    fun dragging_tab_left_reorders_tabs_in_state() {
        val state = AppState().apply {
            openTabs[0].name = "Tab A"
            addTab(name = "Tab B", method = HttpMethodType.POST)
            addTab(name = "Tab C", method = HttpMethodType.PUT)
        }
        val tabA = state.openTabs[0]
        val tabB = state.openTabs[1]
        val tabC = state.openTabs[2]

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Drag Tab C horizontally to the left past Tab A
        composeRule.onNodeWithTag("tab-chip-${tabC.id}").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(-320f, 0f))
            release()
        }
        composeRule.waitForIdle()

        // Tab C should now be at index 0, Tab A at index 1, Tab B at index 2
        assertEquals(tabC.id, state.openTabs[0].id)
        assertEquals(tabA.id, state.openTabs[1].id)
        assertEquals(tabB.id, state.openTabs[2].id)
        assertEquals(0, state.activeTabIndex)
    }

    @Test
    fun dragging_tab_to_end_reorders_to_last_index() {
        val state = AppState().apply {
            openTabs[0].name = "Tab A"
            addTab(name = "Tab B", method = HttpMethodType.POST)
            addTab(name = "Tab C", method = HttpMethodType.PUT)
        }
        val tabA = state.openTabs[0]
        val tabB = state.openTabs[1]
        val tabC = state.openTabs[2]

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Drag Tab A far to the right past all tabs
        composeRule.onNodeWithTag("tab-chip-${tabA.id}").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(500f, 0f))
            release()
        }
        composeRule.waitForIdle()

        // Tab A should now be at the last index (2)
        assertEquals(tabB.id, state.openTabs[0].id)
        assertEquals(tabC.id, state.openTabs[1].id)
        assertEquals(tabA.id, state.openTabs[2].id)
        assertEquals(2, state.activeTabIndex)
    }

    @Test
    fun click_without_drag_activates_tab_without_reordering() {
        val state = AppState().apply {
            openTabs[0].name = "Tab A"
            addTab(name = "Tab B", method = HttpMethodType.POST)
            activeTabIndex = 0
        }
        val tabB = state.openTabs[1]

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Click Tab B
        composeRule.onNodeWithTag("tab-chip-${tabB.id}").performClick()
        composeRule.waitForIdle()

        assertEquals(1, state.activeTabIndex)
        assertEquals("Tab A", state.openTabs[0].name)
        assertEquals("Tab B", state.openTabs[1].name)
    }

    @Test
    fun click_with_micro_jitter_does_not_drag() {
        val state = AppState().apply {
            openTabs[0].name = "Tab A"
            addTab(name = "Tab B", method = HttpMethodType.POST)
            activeTabIndex = 0
        }
        val tabB = state.openTabs[1]

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Move 1px micro-jitter during click
        composeRule.onNodeWithTag("tab-chip-${tabB.id}").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(1f, 0f))
            release()
        }
        composeRule.waitForIdle()

        // Tab B activated and not reordered
        assertEquals(1, state.activeTabIndex)
        assertEquals("Tab A", state.openTabs[0].name)
        assertEquals("Tab B", state.openTabs[1].name)
    }

    @Test
    fun closing_tab_via_close_button_does_not_drag() {
        val state = AppState().apply {
            openTabs[0].name = "Tab A"
            addTab(name = "Tab B", method = HttpMethodType.POST)
            addTab(name = "Tab C", method = HttpMethodType.PUT)
            activeTabIndex = 1
        }
        val tabB = state.openTabs[1]

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Click close icon on Tab B
        composeRule.onNodeWithTag("tab-close-${tabB.id}").performClick()
        composeRule.waitForIdle()

        assertEquals(2, state.openTabs.size)
        assertFalse(state.openTabs.any { it.id == tabB.id })
    }

    @Test
    fun right_click_opens_context_menu_without_drag() {
        val state = AppState().apply {
            openTabs[0].name = "Tab A"
            addTab(name = "Tab B", method = HttpMethodType.POST)
        }
        val tabA = state.openTabs[0]

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("tab-chip-${tabA.id}").performMouseInput {
            rightClick()
        }
        composeRule.waitForIdle()

        // Context menu item should be shown
        composeRule.onNodeWithTag("tab-ctx-close").assertExists()
    }

    private fun createFiveTabsState(): AppState = AppState().apply {
        openTabs[0].name = "Tab 0"
        addTab(name = "Tab 1", method = HttpMethodType.POST)
        addTab(name = "Tab 2", method = HttpMethodType.PUT)
        addTab(name = "Tab 3", method = HttpMethodType.DELETE)
        addTab(name = "Tab 4", method = HttpMethodType.PATCH)
    }

    @Test
    fun scenario1_drag_leftmost_1_to_right() {
        val state = createFiveTabsState()
        val (t0, t1, t2, t3, t4) = state.openTabs.map { it.id }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Drag t0 (index 0) 1 to the right across t1
        val t0Center = composeRule.onNodeWithTag("tab-chip-$t0").fetchSemanticsNode().boundsInRoot.center
        val t1Center = composeRule.onNodeWithTag("tab-chip-$t1").fetchSemanticsNode().boundsInRoot.center
        composeRule.onNodeWithTag("tab-chip-$t0").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(t1Center.x - t0Center.x + 20f, 0f))
            release()
        }
        composeRule.waitForIdle()

        assertEquals(listOf(t1, t0, t2, t3, t4), state.openTabs.map { it.id })
    }

    @Test
    fun scenario2_drag_leftmost_to_other_end() {
        val state = createFiveTabsState()
        val (t0, t1, t2, t3, t4) = state.openTabs.map { it.id }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Drag t0 (index 0) all the way to the other end (past t4)
        val t0Center = composeRule.onNodeWithTag("tab-chip-$t0").fetchSemanticsNode().boundsInRoot.center
        val t4Center = composeRule.onNodeWithTag("tab-chip-$t4").fetchSemanticsNode().boundsInRoot.center
        composeRule.onNodeWithTag("tab-chip-$t0").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(t4Center.x - t0Center.x + 50f, 0f))
            release()
        }
        composeRule.waitForIdle()

        assertEquals(listOf(t1, t2, t3, t4, t0), state.openTabs.map { it.id })
    }

    @Test
    fun scenario3_drag_rightmost_1_to_left() {
        val state = createFiveTabsState()
        val (t0, t1, t2, t3, t4) = state.openTabs.map { it.id }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Drag t4 (index 4) 1 to the left across t3
        val t4Center = composeRule.onNodeWithTag("tab-chip-$t4").fetchSemanticsNode().boundsInRoot.center
        val t3Center = composeRule.onNodeWithTag("tab-chip-$t3").fetchSemanticsNode().boundsInRoot.center
        composeRule.onNodeWithTag("tab-chip-$t4").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(t3Center.x - t4Center.x - 20f, 0f))
            release()
        }
        composeRule.waitForIdle()

        assertEquals(listOf(t0, t1, t2, t4, t3), state.openTabs.map { it.id })
    }

    @Test
    fun scenario4_drag_rightmost_to_other_end() {
        val state = createFiveTabsState()
        val (t0, t1, t2, t3, t4) = state.openTabs.map { it.id }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Drag t4 (index 4) all the way to the other end (past t0)
        val t4Center = composeRule.onNodeWithTag("tab-chip-$t4").fetchSemanticsNode().boundsInRoot.center
        val t0Center = composeRule.onNodeWithTag("tab-chip-$t0").fetchSemanticsNode().boundsInRoot.center
        composeRule.onNodeWithTag("tab-chip-$t4").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(t0Center.x - t4Center.x - 50f, 0f))
            release()
        }
        composeRule.waitForIdle()

        assertEquals(listOf(t4, t0, t1, t2, t3), state.openTabs.map { it.id })
    }

    @Test
    fun scenario5_drag_middle_1_to_left() {
        val state = createFiveTabsState()
        val (t0, t1, t2, t3, t4) = state.openTabs.map { it.id }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Drag t2 (index 2) 1 to the left across t1
        val t2Center = composeRule.onNodeWithTag("tab-chip-$t2").fetchSemanticsNode().boundsInRoot.center
        val t1Center = composeRule.onNodeWithTag("tab-chip-$t1").fetchSemanticsNode().boundsInRoot.center
        composeRule.onNodeWithTag("tab-chip-$t2").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(t1Center.x - t2Center.x - 20f, 0f))
            release()
        }
        composeRule.waitForIdle()

        assertEquals(listOf(t0, t2, t1, t3, t4), state.openTabs.map { it.id })
    }

    @Test
    fun scenario6_drag_middle_1_to_right() {
        val state = createFiveTabsState()
        val (t0, t1, t2, t3, t4) = state.openTabs.map { it.id }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Drag t2 (index 2) 1 to the right across t3
        val t2Center = composeRule.onNodeWithTag("tab-chip-$t2").fetchSemanticsNode().boundsInRoot.center
        val t3Center = composeRule.onNodeWithTag("tab-chip-$t3").fetchSemanticsNode().boundsInRoot.center
        composeRule.onNodeWithTag("tab-chip-$t2").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(t3Center.x - t2Center.x + 20f, 0f))
            release()
        }
        composeRule.waitForIdle()

        assertEquals(listOf(t0, t1, t3, t2, t4), state.openTabs.map { it.id })
    }

    @Test
    fun scenario7_drag_second_last_2_to_left() {
        val state = createFiveTabsState()
        val (t0, t1, t2, t3, t4) = state.openTabs.map { it.id }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Drag t3 (index 3) 2 to the left past t1
        val t3Center = composeRule.onNodeWithTag("tab-chip-$t3").fetchSemanticsNode().boundsInRoot.center
        val t1Center = composeRule.onNodeWithTag("tab-chip-$t1").fetchSemanticsNode().boundsInRoot.center
        composeRule.onNodeWithTag("tab-chip-$t3").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(t1Center.x - t3Center.x - 20f, 0f))
            release()
        }
        composeRule.waitForIdle()

        assertEquals(listOf(t0, t3, t1, t2, t4), state.openTabs.map { it.id })
    }

    @Test
    fun scenario8_drag_second_2_to_right() {
        val state = createFiveTabsState()
        val (t0, t1, t2, t3, t4) = state.openTabs.map { it.id }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Drag t1 (index 1) 2 to the right past t3
        val t1Center = composeRule.onNodeWithTag("tab-chip-$t1").fetchSemanticsNode().boundsInRoot.center
        val t3Center = composeRule.onNodeWithTag("tab-chip-$t3").fetchSemanticsNode().boundsInRoot.center
        composeRule.onNodeWithTag("tab-chip-$t1").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(t3Center.x - t1Center.x + 20f, 0f))
            release()
        }
        composeRule.waitForIdle()

        assertEquals(listOf(t0, t2, t3, t1, t4), state.openTabs.map { it.id })
    }

    @Test
    fun scenario9_chained_reordering_sequence() {
        val state = createFiveTabsState()
        val t0 = state.openTabs[0].id
        val t1 = state.openTabs[1].id
        val t2 = state.openTabs[2].id
        val t3 = state.openTabs[3].id
        val t4 = state.openTabs[4].id

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        fun dragTabTo(sourceId: String, targetId: String, extraDelta: Float = 0f) {
            val sourceCenter = composeRule.onNodeWithTag("tab-chip-$sourceId").fetchSemanticsNode().boundsInRoot.center
            val targetCenter = composeRule.onNodeWithTag("tab-chip-$targetId").fetchSemanticsNode().boundsInRoot.center
            val deltaX = targetCenter.x - sourceCenter.x
            val extra = if (extraDelta != 0f) extraDelta else if (deltaX > 0) 15f else -15f
            composeRule.onNodeWithTag("tab-chip-$sourceId").performMouseInput {
                moveTo(center)
                press()
                moveBy(Offset(deltaX + extra, 2f))
                release()
            }
            composeRule.waitForIdle()
        }

        // 1. Drag left-most 1 to the right (t0 past t1)
        dragTabTo(t0, t1)
        assertEquals(listOf(t1, t0, t2, t3, t4), state.openTabs.map { it.id })

        // 2. Drag left-most to the other end (t1 all the way past t4)
        dragTabTo(t1, t4, extraDelta = 50f)
        assertEquals(listOf(t0, t2, t3, t4, t1), state.openTabs.map { it.id })

        // 3. Drag right-most 1 to the left (t1 before t4)
        dragTabTo(t1, t4)
        assertEquals(listOf(t0, t2, t3, t1, t4), state.openTabs.map { it.id })

        // 4. Drag right-most to the other end (t4 before t0)
        dragTabTo(t4, t0, extraDelta = -50f)
        assertEquals(listOf(t4, t0, t2, t3, t1), state.openTabs.map { it.id })

        // 5. Drag middle tab 1 to the left (middle is t2 at index 2, drag before t0)
        dragTabTo(t2, t0)
        assertEquals(listOf(t4, t2, t0, t3, t1), state.openTabs.map { it.id })

        // 6. Drag middle tab 1 to the right (middle is t0 at index 2, drag past t3)
        dragTabTo(t0, t3)
        assertEquals(listOf(t4, t2, t3, t0, t1), state.openTabs.map { it.id })

        // 7. Drag second-last 2 to the left (second last is t0 at index 3, drag to t2 at index 1)
        dragTabTo(t0, t2)
        assertEquals(listOf(t4, t0, t2, t3, t1), state.openTabs.map { it.id })

        // 8. Drag 2nd tab 2 to the right (2nd is t0 at index 1, drag to t3 at index 3)
        dragTabTo(t0, t3)
        assertEquals(listOf(t4, t2, t3, t0, t1), state.openTabs.map { it.id })
    }
}
