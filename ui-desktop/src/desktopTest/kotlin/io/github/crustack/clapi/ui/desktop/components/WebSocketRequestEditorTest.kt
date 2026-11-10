package io.github.crustack.clapi.ui.desktop.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.crustack.clapi.core.model.MessageDirection
import io.github.crustack.clapi.core.model.RequestKind
import io.github.crustack.clapi.core.model.WebSocketLogMessage
import io.github.crustack.clapi.core.model.opencollection.WebSocketMessage
import io.github.crustack.clapi.core.model.opencollection.WebSocketMessageType
import io.github.crustack.clapi.core.model.opencollection.WebSocketMessageVariant
import io.github.crustack.clapi.core.network.ConnectionState
import io.github.crustack.clapi.ui.shared.components.WebSocketRequestEditor
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import io.github.crustack.clapi.ui.shared.state.WebSocketEditorTab
import io.github.crustack.clapi.ui.shared.theme.ClapiTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebSocketRequestEditorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun webSocketEditor_rendersTopBarAndTabs() {
        val state = AppState(withDemoData = false)
        val tab = RequestTabState(
            name = "Test WS",
            url = "ws://localhost:8080/ws",
            kind = RequestKind.WEBSOCKET,
        )

        composeRule.setContent {
            ClapiTheme {
                WebSocketRequestEditor(tab = tab, state = state, onSave = {})
            }
        }

        composeRule.onNodeWithTag("websocket-url-input", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("websocket-connect-button", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("websocket-tab-message", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("websocket-tab-headers", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("websocket-tab-settings", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("websocket-timeline", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun webSocketEditor_switchTabs() {
        val state = AppState(withDemoData = false)
        val tab = RequestTabState(
            name = "Test WS",
            url = "ws://localhost:8080/ws",
            kind = RequestKind.WEBSOCKET,
        )

        composeRule.setContent {
            ClapiTheme {
                WebSocketRequestEditor(tab = tab, state = state, onSave = {})
            }
        }

        composeRule.onNodeWithTag("websocket-tab-settings", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(WebSocketEditorTab.SETTINGS, tab.selectedWebSocketTab)

        composeRule.onNodeWithTag("websocket-tab-headers", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(WebSocketEditorTab.HEADERS, tab.selectedWebSocketTab)
    }

    @Test
    fun webSocketEditor_addVariant_updatesVariantsList() {
        val state = AppState(withDemoData = false)
        val tab = RequestTabState(
            name = "Test WS",
            url = "ws://localhost:8080/ws",
            kind = RequestKind.WEBSOCKET,
        )

        composeRule.setContent {
            ClapiTheme {
                WebSocketRequestEditor(tab = tab, state = state, onSave = {})
            }
        }

        assertEquals(0, tab.webSocketMessageVariants.size)

        // Click Add Variant
        composeRule.onNodeWithTag("websocket-add-variant", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(1, tab.webSocketMessageVariants.size)
        assertEquals("Default", tab.webSocketMessageVariants.first().title)

        // Add second variant
        composeRule.onNodeWithTag("websocket-add-variant", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(2, tab.webSocketMessageVariants.size)
        assertEquals(1, tab.activeWebSocketVariantIndex)
    }

    @Test
    fun webSocketEditor_timeline_displaysMessagesAndClears() {
        val state = AppState(withDemoData = false)
        val tab = RequestTabState(
            name = "Test WS",
            url = "ws://localhost:8080/ws",
            kind = RequestKind.WEBSOCKET,
        )

        tab.webSocketLogMessages.add(
            WebSocketLogMessage(
                id = "1",
                timestamp = 1000L,
                direction = MessageDirection.SENT,
                format = "json",
                payload = """{"action":"ping"}""",
            )
        )
        tab.webSocketLogMessages.add(
            WebSocketLogMessage(
                id = "2",
                timestamp = 2000L,
                direction = MessageDirection.RECEIVED,
                format = "json",
                payload = """{"action":"pong"}""",
            )
        )

        composeRule.setContent {
            ClapiTheme {
                WebSocketRequestEditor(tab = tab, state = state, onSave = {})
            }
        }

        composeRule.onNodeWithText("↑ SENT", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("↓ RECEIVED", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("""{"action":"ping"}""", useUnmergedTree = true).assertIsDisplayed()

        // Clear timeline
        composeRule.onNodeWithTag("websocket-clear-log", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertTrue(tab.webSocketLogMessages.isEmpty())
    }
}
