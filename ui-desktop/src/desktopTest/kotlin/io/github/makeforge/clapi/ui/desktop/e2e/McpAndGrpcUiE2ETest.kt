// Modified by MakeForge (2026)
package io.github.makeforge.clapi.ui.desktop.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import io.github.makeforge.clapi.core.model.McpConnectionConfig
import io.github.makeforge.clapi.core.model.McpConnectionState
import io.github.makeforge.clapi.core.model.McpHttpMode
import io.github.makeforge.clapi.core.model.McpSamplingMode
import io.github.makeforge.clapi.core.model.McpTransportType
import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.ui.desktop.e2e.support.EmbeddedGrpcTestServerRule
import io.github.makeforge.clapi.ui.desktop.e2e.support.EmbeddedSampleServerRule
import io.github.makeforge.clapi.ui.desktop.e2e.support.IsolatedWorkspaceRule
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.mcp.McpPendingSampling
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import io.github.makeforge.clapi.ui.shared.state.ProtoFileItemState
import io.github.makeforge.clapi.ui.shared.state.ProtoImportPathState
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Interactive Compose Desktop UI E2E tests covering:
 * - [McpPanel] over Streamable HTTP (`/mcp`) and Legacy HTTP+SSE (`/mcp/sse`), tool invocation,
 *   resources/prompts/activity log inspection, interactive sampling & elicitation callbacks,
 *   disconnect, and active-tab close cleanup.
 * - [GrpcRequestEditor] direct & collection-inherited `.proto` selection, service/method discovery,
 *   live unary execution with metadata/headers/trailers, and gRPC error status handling.
 *
 * Covers FR-008 & FR-009 (US3).
 */
class McpAndGrpcUiE2ETest {

    companion object {
        @JvmField
        @ClassRule
        val server = EmbeddedSampleServerRule()

        @JvmField
        @ClassRule
        val grpcServer = EmbeddedGrpcTestServerRule()
    }

    @get:Rule
    val workspace = IsolatedWorkspaceRule()

    @get:Rule
    val composeRule = createComposeRule()

    // ── MCP Client UI E2E (Streamable HTTP, Tools, Resources, Prompts, Activity Log, Callbacks)

    @Test
    fun mcp_streamable_http_connect_tools_resources_prompts_log_and_disconnect() {
        val state = workspace.newAppState(openDefaultTab = false)
        val mcpTab = RequestTabState(
            name = "Sample MCP",
            kind = RequestKind.MCP,
            url = server.mcpHttpUrl,
        ).apply {
            mcpConfig = McpConnectionConfig(
                url = server.mcpHttpUrl,
                httpMode = McpHttpMode.STREAMABLE_2025_06_18,
            )
        }
        state.openTabs.add(mcpTab)
        state.activeTabIndex = 0
        val session = state.getOrCreateMcpSession(mcpTab.id)

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("mcp-panel", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("mcp-connect", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 15_000) {
            session.connectionState.value == McpConnectionState.CONNECTED &&
                session.tools.value.isNotEmpty()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mcp-status-CONNECTED", useUnmergedTree = true).assertIsDisplayed()

        // Select `echo` tool from list, enter `text` argument via UI form, and click Run
        composeRule.onAllNodesWithText("echo", useUnmergedTree = true)[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mcp-tool-args-text", useUnmergedTree = true).performTextInput("hello-mcp-ui")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mcp-run", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            session.lastToolResult.value?.content?.any { it.text?.contains("hello-mcp-ui") == true } == true
        }

        // Switch through Resources, Prompts, and Activity Log tabs in McpPanel
        composeRule.onAllNodesWithText("Resources", substring = true, useUnmergedTree = true)[0].performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            session.resources.value.isNotEmpty()
        }

        composeRule.onAllNodesWithText("Prompts", substring = true, useUnmergedTree = true)[0].performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            session.prompts.value.isNotEmpty()
        }

        composeRule.onAllNodesWithText("Activity", substring = true, useUnmergedTree = true)[0].performClick()
        composeRule.waitForIdle()
        assertTrue(session.logs.value.isNotEmpty(), "Expected JSON-RPC activity log entries")

        // Disconnect via UI button
        composeRule.onNodeWithTag("mcp-disconnect", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            session.connectionState.value == McpConnectionState.DISCONNECTED
        }
    }

    @Test
    fun mcp_legacy_sse_connect_callbacks_and_active_tab_close() {
        val state = workspace.newAppState(openDefaultTab = false)
        val mcpTab = RequestTabState(
            name = "Legacy MCP",
            kind = RequestKind.MCP,
            url = server.mcpLegacySseUrl,
        ).apply {
            mcpConfig = McpConnectionConfig(
                url = server.mcpLegacySseUrl,
                httpMode = McpHttpMode.LEGACY_2024_11_05,
                samplingMode = McpSamplingMode.MANUAL,
                autoRespondElicitation = false,
            )
        }
        state.openTabs.add(mcpTab)
        state.activeTabIndex = 0
        val session = state.getOrCreateMcpSession(mcpTab.id)

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Connect over Legacy HTTP+SSE
        composeRule.onNodeWithTag("mcp-connect", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            session.connectionState.value == McpConnectionState.CONNECTED &&
                session.tools.value.any { it.name == "trigger_sampling" }
        }

        // Trigger interactive sampling callback via UI and cancel from McpCallbackPane
        composeRule.onAllNodesWithText("trigger_sampling", useUnmergedTree = true)[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mcp-run", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            session.pendingSampling.value is McpPendingSampling.ReviewRequest
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mcp-sampling-cancel", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            session.pendingSampling.value == null &&
                session.lastToolResult.value?.content?.any { it.text?.contains("cancelled") == true } == true
        }

        // Trigger interactive elicitation callback via UI and decline from McpCallbackPane
        composeRule.onAllNodesWithText("trigger_elicitation", useUnmergedTree = true)[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mcp-run", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            session.pendingElicitation.value != null
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mcp-elicit-decline", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            session.pendingElicitation.value == null &&
                session.lastToolResult.value?.content?.any { it.text?.contains("decline") == true } == true
        }

        // Close active MCP tab while connected -> session tears down cleanly
        composeRule.runOnUiThread { mcpTab.markSaved() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tab-close-${mcpTab.id}", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertFalse(state.openTabs.any { it.id == mcpTab.id })
    }

    // ── gRPC UI E2E (Direct & Collection-Inherited Proto, Unary Execution, Metadata, Error) ─

    @Test
    fun grpc_direct_proto_unary_call_and_error_status_execute_through_ui() {
        val state = workspace.newAppState(openDefaultTab = false)
        val grpcTab = RequestTabState(
            name = "Greeter Unary",
            kind = RequestKind.GRPC,
            url = "http://${grpcServer.address}",
        ).apply {
            protoFilePath = grpcServer.protoFilePath
            bodyContent = """{"name": "Clapi UI"}"""
            grpcMetadata.add(MutableKeyValue("x-custom-meta", "ui-meta-42"))
        }
        state.openTabs.add(grpcTab)
        state.activeTabIndex = 0

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Wait for proto parsing & method discovery, then pick SayHello from dropdown
        composeRule.waitUntil(timeoutMillis = 15_000) {
            grpcTab.availableMethods.any { it.fullMethodName == "helloworld.Greeter/SayHello" }
        }
        composeRule.onNodeWithTag("grpc-method-dropdown", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("grpc-method-item-helloworld.Greeter/SayHello", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()
        assertEquals("helloworld.Greeter/SayHello", grpcTab.grpcMethod)

        // Execute live unary RPC via Send button
        composeRule.onNodeWithTag("send-button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            !grpcTab.isLoading && grpcTab.response != null
        }
        composeRule.waitForIdle()

        val okResp = grpcTab.response
        assertNotNull(okResp)
        assertEquals(0, okResp.statusCode)
        assertTrue(okResp.bodyText.contains("Hello, Clapi UI!"), "Unexpected gRPC body: ${okResp.bodyText}")
        assertTrue(okResp.bodyText.contains("ui-meta-42"), "Expected echoed metadata in body: ${okResp.bodyText}")
        assertTrue(
            okResp.headers.any { it.key == "x-grpc-server" && it.value == "EmbeddedGrpcTestServer" },
            "Expected gRPC response headers, got: ${okResp.headers}",
        )

        // Error RPC status (PERMISSION_DENIED) renders cleanly in UI
        val prevEpoch = okResp.executedAtEpochMillis
        composeRule.runOnUiThread { grpcTab.bodyContent = """{"name": "error"}""" }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("send-button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            !grpcTab.isLoading && (grpcTab.response?.executedAtEpochMillis ?: -1L) != prevEpoch
        }
        assertNotNull(grpcTab.response)
        assertTrue(grpcTab.response!!.statusCode != 0, "Expected non-OK gRPC error status code")
    }

    @Test
    fun grpc_collection_inherited_proto_discovers_and_executes_unary_call() {
        val state = workspace.newAppState(openDefaultTab = false)
        state.createNewCollection("gRPC Service Collection")
        val col = state.collections.first()
        state.collections[0] = col.copy(
            protoFiles = listOf(ProtoFileItemState(path = grpcServer.protoFilePath)),
            importPaths = listOf(ProtoImportPathState(path = grpcServer.protoDirPath)),
        )
        state.addGrpcRequestToCollection(col.id)
        val grpcTab = state.activeTab!!
        grpcTab.protoFilePath = grpcServer.protoFilePath
        grpcTab.url = "http://${grpcServer.address}"
        grpcTab.bodyContent = """{"name": "Inherited Proto"}"""

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 15_000) {
            grpcTab.availableMethods.any { it.fullMethodName == "helloworld.Greeter/SayHello" }
        }
        composeRule.runOnUiThread { grpcTab.grpcMethod = "helloworld.Greeter/SayHello" }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("send-button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            !grpcTab.isLoading && grpcTab.response != null
        }
        assertEquals(0, grpcTab.response?.statusCode)
        assertTrue(grpcTab.response!!.bodyText.contains("Hello, Inherited Proto!"))
    }

    // ── MCP AUTO Fallback (Streamable -> Legacy SSE) & STDIO Subprocess in McpPanel ──────────

    @Test
    fun mcp_auto_mode_falls_back_to_legacy_sse_and_stdio_connects_in_ui() {
        // 1. McpHttpMode.AUTO against legacy-only /mcp/sse endpoint falls back automatically
        val state = workspace.newAppState(openDefaultTab = false)
        val autoTab = RequestTabState(
            name = "Auto Fallback MCP",
            kind = RequestKind.MCP,
            url = server.mcpLegacySseUrl,
        ).apply {
            mcpConfig = McpConnectionConfig(
                url = server.mcpLegacySseUrl,
                httpMode = McpHttpMode.AUTO,
            )
        }
        state.openTabs.add(autoTab)
        state.activeTabIndex = 0
        val autoSession = state.getOrCreateMcpSession(autoTab.id)

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("mcp-connect", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            autoSession.connectionState.value == McpConnectionState.CONNECTED &&
                autoSession.tools.value.any { it.name == "echo" }
        }
        assertTrue(
            autoSession.logs.value.any { it.summary.contains("falling back to legacy", ignoreCase = true) },
            "Expected AUTO mode fallback log entry, got: ${autoSession.logs.value.map { it.summary }}",
        )
        composeRule.onNodeWithTag("mcp-disconnect", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            autoSession.connectionState.value == McpConnectionState.DISCONNECTED
        }

        // 2. McpTransportType.STDIO in McpPanel: unconfirmed shows safety error, confirmed connects & executes tool
        val javaBin = ProcessHandle.current().info().command().orElse("java")
        val classpath = System.getProperty("java.class.path")
        val stdioTab = RequestTabState(
            name = "Stdio MCP",
            kind = RequestKind.MCP,
            url = "",
        ).apply {
            mcpConfig = McpConnectionConfig(
                transport = McpTransportType.STDIO,
                command = javaBin,
                args = listOf("-cp", classpath, "io.github.makeforge.clapi.server.ApplicationKt", "--stdio"),
            )
        }
        val stdioSession = state.getOrCreateMcpSession(stdioTab.id)
        composeRule.runOnUiThread {
            state.openTabs.add(stdioTab)
            state.activeTabIndex = 1
        }
        composeRule.waitForIdle()

        // Without confirmStdio, clicking Connect opens the inline STDIO confirmation prompt
        composeRule.onNodeWithTag("mcp-connect", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(McpConnectionState.DISCONNECTED, stdioSession.connectionState.value)
        composeRule.onNodeWithText("Confirm", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()

        // Confirming spawns sample-server --stdio and discovers the echo tool
        composeRule.waitUntil(timeoutMillis = 20_000) {
            stdioSession.connectionState.value == McpConnectionState.CONNECTED &&
                stdioSession.tools.value.any { it.name == "echo" }
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("echo", useUnmergedTree = true)[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mcp-tool-args-text", useUnmergedTree = true).performTextInput("hello-stdio-ui")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mcp-run", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            stdioSession.lastToolResult.value?.content?.any { it.text?.contains("hello-stdio-ui") == true } == true
        }

        // Clean disconnect tears down subprocess
        composeRule.onNodeWithTag("mcp-disconnect", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            stdioSession.connectionState.value == McpConnectionState.DISCONNECTED
        }
    }
}
