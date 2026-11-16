package io.github.makeforge.clapi.ui.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.makeforge.clapi.core.model.McpConnectionState
import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.ui.desktop.preview.MainScreenMcpPreviewDark
import io.github.makeforge.clapi.ui.desktop.preview.MainScreenMcpPreviewLight
import io.github.makeforge.clapi.ui.shared.components.sidebar.CollectionTreeNodeAddMenu
import io.github.makeforge.clapi.ui.shared.state.AppStateExamples
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class McpAddMenuAndPreviewTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun collectionTreeNodeAddMenu_shows_mcp_and_sse_options_and_invokes_callbacks() {
        var mcpAdded = false
        var sseAdded = false
        val dummyNode = CollectionNode(id = "folder-1", name = "Test Folder", isFolder = true)

        composeRule.setContent {
            ClapiTheme {
                Box(Modifier.size(400.dp, 600.dp)) {
                    CollectionTreeNodeAddMenu(
                        expanded = true,
                        onDismissRequest = {},
                        node = dummyNode,
                        onAddFolder = {},
                        onAddRequest = {},
                        onAddGrpcRequest = {},
                        onAddWebSocketRequest = {},
                        onAddGraphQLRequest = {},
                        onAddMcpConnection = { mcpAdded = true },
                        onAddSseRequest = { sseAdded = true },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("folder-menu-new-mcp").assertIsDisplayed()
        composeRule.onNodeWithTag("folder-menu-new-sse").assertIsDisplayed()

        composeRule.onNodeWithTag("folder-menu-new-mcp").performClick()
        assertTrue(mcpAdded, "onAddMcpConnection must be called when clicking new MCP connection")

        composeRule.onNodeWithTag("folder-menu-new-sse").performClick()
        assertTrue(sseAdded, "onAddSseRequest must be called when clicking new SSE request")
    }

    @Test
    fun appStateExamples_mcp_has_connected_state_tools_and_succeeded_response() {
        val state = AppStateExamples.mcp()
        val activeTab = state.activeTab
        assertNotNull(activeTab)
        assertEquals(RequestKind.MCP, activeTab.kind)

        val session = state.getOrCreateMcpSession(activeTab.id)
        assertEquals(McpConnectionState.CONNECTED, session.connectionState.value)
        assertTrue(session.tools.value.isNotEmpty(), "MCP example session should have tools")
        assertEquals(3, session.tools.value.size)

        val lastOp = session.lastOperation.value
        assertNotNull(lastOp)
        assertEquals("execute_sql_query", lastOp.label)
        assertEquals(false, lastOp.isError)

        val response = activeTab.response
        assertNotNull(response)
        assertEquals(200, response.statusCode)
        assertTrue(response.bodyText.contains("Antigravity Core"))
    }

    @Test
    fun mainScreenMcpPreview_renders_connected_mcp_workspace() {
        composeRule.setContent {
            MainScreenMcpPreviewLight()
        }

        composeRule.onNodeWithTag("mcp-workspace").assertIsDisplayed()
        composeRule.onNodeWithTag("mcp-status-CONNECTED").assertIsDisplayed()
        composeRule.onNodeWithTag("mcp-tool-list").assertIsDisplayed()
    }
}
