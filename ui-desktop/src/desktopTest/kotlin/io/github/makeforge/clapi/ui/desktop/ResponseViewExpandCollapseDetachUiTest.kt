package io.github.makeforge.clapi.ui.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.makeforge.clapi.core.model.KeyValueEntry
import io.github.makeforge.clapi.core.model.ResponseDefinition
import io.github.makeforge.clapi.core.model.ResponseMetrics
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.components.ResponseViewer
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.github.makeforge.clapi.ui.shared.state.ResponseDisplayMode
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResponseViewExpandCollapseDetachUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun response_viewer_renders_more_options_button() {
        val state = AppState()
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("response-viewer").assertIsDisplayed()
        composeRule.onNodeWithTag("response-more-options-button").assertIsDisplayed()
    }

    @Test
    fun clicking_more_options_shows_collapse_and_detach_options() {
        val state = AppState()
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("response-more-options-button").performClick()

        composeRule.onNodeWithTag("response-menu-collapse").assertIsDisplayed()
        composeRule.onNodeWithTag("response-menu-detach").assertIsDisplayed()
    }

    @Test
    fun clicking_collapse_collapses_response_and_shows_collapsed_strip() {
        val state = AppState()
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("response-more-options-button").performClick()
        composeRule.onNodeWithTag("response-menu-collapse").performClick()

        assertEquals(ResponseDisplayMode.COLLAPSED, state.responseDisplayMode)
        assertTrue(state.isResponseCollapsed)
        assertFalse(state.isResponseDetached)

        composeRule.onNodeWithTag("response-viewer").assertDoesNotExist()
        composeRule.onNodeWithTag("collapsed-response-strip").assertIsDisplayed()
        composeRule.onNodeWithTag("expand-response-button", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("collapsed-response-text", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun clicking_expand_icon_restores_response_view() {
        val state = AppState().apply { collapseResponse() }
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("collapsed-response-strip").assertIsDisplayed()
        composeRule.onNodeWithTag("response-viewer").assertDoesNotExist()

        composeRule.onNodeWithTag("expand-response-button", useUnmergedTree = true).performClick()

        assertEquals(ResponseDisplayMode.EXPANDED, state.responseDisplayMode)
        assertFalse(state.isResponseCollapsed)
        assertFalse(state.isResponseDetached)

        composeRule.onNodeWithTag("response-viewer").assertIsDisplayed()
        composeRule.onNodeWithTag("collapsed-response-strip").assertDoesNotExist()
    }

    @Test
    fun clicking_detach_sets_detached_state() {
        val state = AppState()
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("response-more-options-button").performClick()
        composeRule.onNodeWithTag("response-menu-detach").performClick()

        assertEquals(ResponseDisplayMode.DETACHED, state.responseDisplayMode)
        assertTrue(state.isResponseDetached)
        assertFalse(state.isResponseCollapsed)

        // In main screen, the split response-viewer is not displayed when detached
        composeRule.onNodeWithTag("response-viewer").assertDoesNotExist()
    }

    @Test
    fun detached_response_viewer_renders_reattach_and_close() {
        val state = AppState()
        val tab = state.activeTab ?: RequestTabState().also { state.openTabs.add(it) }

        composeRule.setContent {
            ResponseViewer(
                tab = tab,
                state = state,
                isDetached = true,
            )
        }

        composeRule.onNodeWithTag("response-more-options-button").performClick()

        composeRule.onNodeWithTag("response-menu-reattach").assertIsDisplayed()
        composeRule.onNodeWithTag("response-menu-close").assertIsDisplayed()
    }

    @Test
    fun reattach_restores_response_view_in_main_screen() {
        val state = AppState().apply { detachResponse() }
        val tab = state.activeTab ?: RequestTabState().also { state.openTabs.add(it) }

        composeRule.setContent {
            ResponseViewer(
                tab = tab,
                state = state,
                isDetached = true,
            )
        }

        composeRule.onNodeWithTag("response-more-options-button").performClick()
        composeRule.onNodeWithTag("response-menu-reattach").performClick()

        assertEquals(ResponseDisplayMode.EXPANDED, state.responseDisplayMode)
        assertFalse(state.isResponseDetached)
        assertFalse(state.isResponseCollapsed)
    }

    @Test
    fun close_detached_response_collapses_response_view() {
        val state = AppState().apply { detachResponse() }
        val tab = state.activeTab ?: RequestTabState().also { state.openTabs.add(it) }

        composeRule.setContent {
            ResponseViewer(
                tab = tab,
                state = state,
                isDetached = true,
            )
        }

        composeRule.onNodeWithTag("response-more-options-button").performClick()
        composeRule.onNodeWithTag("response-menu-close").performClick()

        assertEquals(ResponseDisplayMode.COLLAPSED, state.responseDisplayMode)
        assertFalse(state.isResponseDetached)
        assertTrue(state.isResponseCollapsed)
    }

    @Test
    fun populated_response_status_bar_has_more_options_and_can_collapse() {
        val state = AppState()
        val tab = state.activeTab ?: RequestTabState().also { state.openTabs.add(it) }
        tab.response = ResponseDefinition(
            requestId = "test-req",
            statusCode = 200,
            statusText = "OK",
            headers = listOf(KeyValueEntry("Content-Type", "application/json")),
            cookies = emptyList(),
            bodyText = """{"result": "ok"}""",
            contentType = "application/json",
            executedAtEpochMillis = System.currentTimeMillis(),
            metrics = ResponseMetrics(200, 50, 16),
        )

        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("response-status-bar").assertIsDisplayed()
        composeRule.onNodeWithTag("response-more-options-button").assertIsDisplayed()

        composeRule.onNodeWithTag("response-more-options-button").performClick()
        composeRule.onNodeWithTag("response-menu-collapse").performClick()

        assertEquals(ResponseDisplayMode.COLLAPSED, state.responseDisplayMode)
        assertTrue(state.isResponseCollapsed)
        composeRule.onNodeWithTag("collapsed-response-strip").assertIsDisplayed()
    }
}
