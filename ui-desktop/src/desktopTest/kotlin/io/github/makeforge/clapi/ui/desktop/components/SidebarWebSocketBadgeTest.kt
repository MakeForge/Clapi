package io.github.makeforge.clapi.ui.desktop.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.components.WebSocketBadge
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SidebarWebSocketBadgeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun webSocketBadge_rendersText() {
        composeRule.setContent {
            ClapiTheme {
                WebSocketBadge(compact = true)
            }
        }
        composeRule.onNodeWithText("WS").assertIsDisplayed()
    }

    @Test
    fun folderAddButton_showsHttpGrpcAndWebSocketOptionsInContextMenu() {
        val state = AppState(withDemoData = true)
        val firstCollectionId = state.collections.first().id
        composeRule.setContent {
            ClapiTheme {
                MainScreen(state)
            }
        }

        composeRule.onNodeWithTag("collection-add-$firstCollectionId", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("folder-menu-new-http", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("folder-menu-new-grpc", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("folder-menu-new-websocket", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun selectingNewWebSocketRequest_createsWebSocketNodeAndDisplaysBadgesInSidebarAndTab() {
        val state = AppState(withDemoData = true)
        val rootFolder = state.collections.first()
        val beforeCount = rootFolder.children.size

        composeRule.setContent {
            ClapiTheme {
                MainScreen(state)
            }
        }

        composeRule.onNodeWithTag("collection-add-${rootFolder.id}", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("folder-menu-new-websocket", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(beforeCount + 1, rootFolder.children.size)
        val createdNode: CollectionNode = rootFolder.children.last()
        assertEquals(RequestKind.WEBSOCKET, createdNode.kind)
        assertTrue(createdNode.name.startsWith("New Request"))

        // Verify sidebar WebSocket badge is displayed
        composeRule.onNodeWithTag("websocket-badge-${createdNode.id}", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals("WS")

        // Verify tab WebSocket badge is displayed
        composeRule.onNodeWithTag("tab-websocket-badge-${createdNode.id}", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals("WS")
    }
}
