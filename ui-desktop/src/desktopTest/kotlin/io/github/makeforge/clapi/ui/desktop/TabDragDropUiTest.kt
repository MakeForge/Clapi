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
}
