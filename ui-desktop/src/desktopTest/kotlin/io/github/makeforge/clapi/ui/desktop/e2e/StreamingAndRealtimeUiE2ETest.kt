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
import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.model.BodyType
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.core.model.MessageDirection
import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.core.model.opencollection.GraphQLBody
import io.github.makeforge.clapi.core.model.opencollection.GraphQLBodyVariant
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessage
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessageType
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessageVariant
import io.github.makeforge.clapi.core.network.ConnectionState
import io.ktor.http.ContentType
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import io.github.makeforge.clapi.ui.desktop.e2e.support.EmbeddedSampleServerRule
import io.github.makeforge.clapi.ui.desktop.e2e.support.IsolatedWorkspaceRule
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.state.HeaderKind
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.github.makeforge.clapi.ui.shared.state.WorkspaceMode
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Interactive Compose Desktop UI E2E tests covering:
 * - Finite SSE (GET/POST) and OpenAI-compatible LLM streaming inside [MainScreen] / ResponseViewer
 * - [RealtimePanel] (`WorkspaceMode.REALTIME`) smoke rendering
 * - GraphQL POST, GET, variables, and variant switching against embedded `sample-server`
 * - WebSocket live connect, text/JSON echo in timeline, disconnect, and active-tab close cleanup
 *
 * Covers FR-007 & FR-008 (US3).
 */
class StreamingAndRealtimeUiE2ETest {

    companion object {
        @JvmField
        @ClassRule
        val server = EmbeddedSampleServerRule()
    }

    @get:Rule
    val workspace = IsolatedWorkspaceRule()

    @get:Rule
    val composeRule = createComposeRule()

    // ── Finite SSE (GET & POST) ──────────────────────────────────────────────────────────────

    @Test
    fun sse_get_and_post_streams_populate_events_in_ui() {
        val state = workspace.newAppState(openDefaultTab = true)
        val tab = state.activeTab!!
        tab.method = HttpMethodType.GET
        tab.url = "${server.baseUrl}/sse?count=3&delayMs=15"
        tab.headers.add(MutableKeyValue("Accept", "text/event-stream", kind = HeaderKind.USER))

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("send-button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            !tab.isLoading && tab.response != null
        }
        composeRule.waitForIdle()

        assertEquals(200, tab.response?.statusCode)
        assertTrue(
            tab.response!!.streamEvents.size >= 3 || tab.response!!.bodyText.contains("event"),
            "Expected SSE stream events, got events=${tab.response!!.streamEvents.size} body=${tab.response!!.bodyText}",
        )

        // POST /sse echoing body snippet in final event
        val prevEpoch = tab.response!!.executedAtEpochMillis
        composeRule.runOnUiThread {
            tab.method = HttpMethodType.POST
            tab.url = "${server.baseUrl}/sse?count=2&delayMs=10"
            tab.bodyType = BodyType.JSON
            tab.bodyContent = """{"prompt":"sse-post-check"}"""
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("send-button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            !tab.isLoading && (tab.response?.executedAtEpochMillis ?: -1L) != prevEpoch
        }
        assertEquals(200, tab.response?.statusCode)
        val combined = tab.response!!.bodyText + tab.response!!.streamEvents.joinToString("\n")
        assertTrue(combined.contains("sse-post-check") || tab.response!!.streamEvents.isNotEmpty())
    }

    // ── LLM Streaming (assembled preview, metrics, stop mid-stream, earlyClose) ──────────────

    @Test
    fun llm_streaming_assembles_tokens_handles_early_close_and_manual_stop() {
        val state = workspace.newAppState(openDefaultTab = true)
        val tab = state.activeTab!!
        tab.method = HttpMethodType.POST
        tab.url = "${server.baseUrl}/v1/chat/completions?chunkMs=15"
        tab.authType = AuthType.BEARER
        tab.authToken = "test-token"
        tab.bodyType = BodyType.JSON
        tab.bodyContent = """{"model":"gpt-4o-mini","stream":true,"messages":[{"role":"user","content":"Hello"}]}"""

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // 1. Complete LLM stream
        composeRule.onNodeWithTag("send-button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            !tab.isLoading && tab.response != null
        }
        composeRule.waitForIdle()
        assertEquals(200, tab.response?.statusCode)
        val assembled = tab.response!!.assembledText ?: tab.response!!.bodyText
        assertTrue(assembled.isNotBlank(), "Expected non-blank assembled LLM response")

        // 2. Early-close stream preserves partial text without crashing
        val prevEpoch = tab.response!!.executedAtEpochMillis
        composeRule.runOnUiThread {
            tab.url = "${server.baseUrl}/v1/chat/completions?chunkMs=10&earlyClose=true"
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("send-button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            !tab.isLoading && (tab.response?.executedAtEpochMillis ?: -1L) != prevEpoch
        }
        assertNotNull(tab.response)
        assertTrue(
            (tab.response!!.assembledText ?: tab.response!!.bodyText).isNotBlank() ||
                tab.response!!.streamEvents.isNotEmpty(),
            "Early-close stream should preserve partial output",
        )

        // 3. Manual stop mid-stream on slow chunked LLM stream
        composeRule.runOnUiThread {
            tab.url = "${server.baseUrl}/v1/chat/completions?demo=true&chunkMs=500"
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("send-button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { tab.isLoading }
        composeRule.onNodeWithTag("stop-button", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { !tab.isLoading }
        composeRule.onNodeWithTag("send-button", useUnmergedTree = true).assertIsDisplayed()
    }

    // ── RealtimePanel UI smoke check ─────────────────────────────────────────────────────────

    @Test
    fun realtime_panel_renders_without_error_when_switching_workspace_mode() {
        val state = workspace.newAppState(openDefaultTab = true).apply {
            workspaceMode = WorkspaceMode.REALTIME
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("main-screen").assertIsDisplayed()
        composeRule.runOnUiThread { state.workspaceMode = WorkspaceMode.HTTP }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("request-editor").assertIsDisplayed()
    }

    // ── GraphQL UI E2E (POST, GET, Variables, Variants) ──────────────────────────────────────

    @Test
    fun graphql_post_get_and_variant_switching_execute_against_sample_server() {
        val state = workspace.newAppState(openDefaultTab = false)
        val gqlTab = RequestTabState(
            name = "User Query",
            kind = RequestKind.GRAPHQL,
            url = "${server.baseUrl}/api/graphql",
        ).apply {
            graphQlMethod = "POST"
            graphQlQuery = "query GetUser(${"$"}id: ID!) { user(id: ${"$"}id) { id name } }"
            graphQlVariables = """{"id": "42"}"""
            graphQlVariants.addAll(
                listOf(
                    GraphQLBodyVariant(
                        title = "User 42",
                        selected = true,
                        body = GraphQLBody(
                            query = "query GetUser(${"$"}id: ID!) { user(id: ${"$"}id) { id name } }",
                            variables = """{"id": "42"}""",
                        ),
                    ),
                    GraphQLBodyVariant(
                        title = "User 99",
                        selected = false,
                        body = GraphQLBody(
                            query = "query GetOther(${"$"}id: ID!) { user(id: ${"$"}id) { id name } }",
                            variables = """{"id": "99"}""",
                        ),
                    ),
                ),
            )
            activeGraphQLVariantIndex = 0
        }
        state.openTabs.add(gqlTab)
        state.activeTabIndex = 0

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // 1. POST with variant 0 (id = 42)
        composeRule.onNodeWithTag("graphql-send-button", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            !gqlTab.isLoading && gqlTab.response != null
        }
        assertEquals(200, gqlTab.response?.statusCode)
        assertTrue(gqlTab.response!!.bodyText.contains("42"), "Expected variant 0 id=42, got: ${gqlTab.response!!.bodyText}")

        // 2. Switch to variant 1 (id = 99) via UI chip and send
        val epoch1 = gqlTab.response!!.executedAtEpochMillis
        composeRule.onNodeWithTag("graphql-variant-chip-1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(1, gqlTab.activeGraphQLVariantIndex)

        composeRule.onNodeWithTag("graphql-send-button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            !gqlTab.isLoading && (gqlTab.response?.executedAtEpochMillis ?: -1L) != epoch1
        }
        assertEquals(200, gqlTab.response?.statusCode)
        assertTrue(gqlTab.response!!.bodyText.contains("99"), "Expected variant 1 id=99, got: ${gqlTab.response!!.bodyText}")

        // 3. Switch method to GET via UI selector and verify execution completes
        val epoch2 = gqlTab.response!!.executedAtEpochMillis
        composeRule.onNodeWithTag("graphql-method-selector", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("graphql-method-option-GET", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals("GET", gqlTab.graphQlMethod)

        composeRule.onNodeWithTag("graphql-send-button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            !gqlTab.isLoading && (gqlTab.response?.executedAtEpochMillis ?: -1L) != epoch2
        }
        assertNotNull(gqlTab.response)
    }

    // ── WebSocket UI E2E (Connect, Send/Receive Echo, Disconnect, Active Tab Close) ─────────

    @Test
    fun websocket_connect_echo_disconnect_and_active_tab_close_lifecycle() {
        val state = workspace.newAppState(openDefaultTab = false)
        val wsTab = RequestTabState(
            name = "Echo WS",
            kind = RequestKind.WEBSOCKET,
            url = server.wsUrl,
        ).apply {
            webSocketOutgoingPayload = """{"hello":"clapi-ws-e2e"}"""
        }
        state.openTabs.add(wsTab)
        state.activeTabIndex = 0

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Connect via UI button
        composeRule.onNodeWithTag("websocket-connect-button", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            wsTab.webSocketConnectionState == ConnectionState.CONNECTED
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("websocket-disconnect-button", useUnmergedTree = true).assertIsDisplayed()

        // Send JSON message and verify both SENT and echoed RECEIVED appear in timeline
        composeRule.onNodeWithTag("websocket-send-button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            wsTab.webSocketLogMessages.any { it.direction == MessageDirection.SENT } &&
                wsTab.webSocketLogMessages.any {
                    it.direction == MessageDirection.RECEIVED && it.payload.contains("clapi-ws-e2e")
                }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("clapi-ws-e2e", substring = true, useUnmergedTree = true)[0].assertIsDisplayed()

        // Disconnect via UI button
        composeRule.onNodeWithTag("websocket-disconnect-button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            wsTab.webSocketConnectionState == ConnectionState.DISCONNECTED
        }

        // Reconnect and close the tab while CONNECTED to verify clean teardown
        composeRule.onNodeWithTag("websocket-connect-button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            wsTab.webSocketConnectionState == ConnectionState.CONNECTED
        }
        composeRule.onNodeWithTag("tab-close-${wsTab.id}", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertFalse(state.openTabs.any { it.id == wsTab.id })
        composeRule.onNodeWithTag("empty-workspace-view").assertIsDisplayed()
    }

    // ── GraphQL Mutations, Errors Array & Standalone GraphQLPanel ────────────────────────────

    @Test
    fun graphql_mutation_errors_payload_and_standalone_graphql_panel_execute_from_ui() {
        val errPort = ServerSocket(0).use { it.localPort }
        val gqlErrServer = embeddedServer(Netty, port = errPort) {
            routing {
                post("/graphql-errors") {
                    call.respondText(
                        """{"data":null,"errors":[{"message":"Field validation failed","extensions":{"code":"BAD_USER_INPUT"}}]}""",
                        ContentType.Application.Json,
                    )
                }
            }
        }.start(wait = false)

        try {
            val state = workspace.newAppState(openDefaultTab = false)
            val gqlTab = RequestTabState(
                name = "Create User Mutation",
                kind = RequestKind.GRAPHQL,
                url = "${server.baseUrl}/api/graphql",
            ).apply {
                graphQlMethod = "POST"
                graphQlQuery = "mutation CreateUser(${"$"}name: String!) { createUser(name: ${"$"}name) { id name } }"
                graphQlVariables = """{"name": "Ada"}"""
            }
            state.openTabs.add(gqlTab)
            state.activeTabIndex = 0

            composeRule.setContent { MainScreen(state) }
            composeRule.waitForIdle()

            // 1. Execute GraphQL Mutation against sample-server
            composeRule.onNodeWithTag("graphql-send-button", useUnmergedTree = true).performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                !gqlTab.isLoading && gqlTab.response != null
            }
            assertEquals(200, gqlTab.response?.statusCode)
            assertTrue(gqlTab.response!!.bodyText.contains("mutation CreateUser"), gqlTab.response!!.bodyText)
            assertTrue(gqlTab.response!!.bodyText.contains("Ada"), gqlTab.response!!.bodyText)

            // 2. GraphQL 200 OK response containing an "errors" array renders cleanly
            val prevEpoch = gqlTab.response!!.executedAtEpochMillis
            composeRule.runOnUiThread { gqlTab.url = "http://127.0.0.1:$errPort/graphql-errors" }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("graphql-send-button", useUnmergedTree = true).performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                !gqlTab.isLoading && (gqlTab.response?.executedAtEpochMillis ?: -1L) != prevEpoch
            }
            assertEquals(200, gqlTab.response?.statusCode)
            assertTrue(gqlTab.response!!.bodyText.contains("Field validation failed"), gqlTab.response!!.bodyText)
            assertTrue(gqlTab.response!!.bodyText.contains("BAD_USER_INPUT"), gqlTab.response!!.bodyText)

            // 3. Standalone GraphQLPanel (WorkspaceMode.GRAPHQL) executes live query via UI controls
            composeRule.runOnUiThread { state.workspaceMode = WorkspaceMode.GRAPHQL }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("graphql-panel", useUnmergedTree = true).assertIsDisplayed()
            composeRule.onNodeWithTag("graphql-url-input", useUnmergedTree = true).performTextClearance()
            composeRule.onNodeWithTag("graphql-url-input", useUnmergedTree = true)
                .performTextInput("${server.baseUrl}/api/graphql")
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("graphql-execute-button", useUnmergedTree = true).performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                runCatching {
                    composeRule.onAllNodesWithText("GraphQL User", substring = true, useUnmergedTree = true)
                        .fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeRule.onAllNodesWithText("GraphQL User", substring = true, useUnmergedTree = true)[0]
                .assertIsDisplayed()
        } finally {
            runCatching { gqlErrServer.stop(200, 1_000) }
        }
    }

    // ── WebSocket Binary & XML Frames, Message Variants & Handshake Auth Headers ─────────────

    @Test
    fun websocket_binary_xml_variants_and_auth_handshake_headers_execute_from_ui() {
        val capturedAuth = AtomicReference<String?>(null)
        val capturedTrace = AtomicReference<String?>(null)
        val capturedQueryKey = AtomicReference<String?>(null)
        val wsPort = ServerSocket(0).use { it.localPort }
        val customWsServer = embeddedServer(Netty, port = wsPort) {
            install(WebSockets)
            routing {
                webSocket("/ws-auth") {
                    capturedAuth.set(call.request.header("Authorization"))
                    capturedTrace.set(call.request.header("X-Ws-Trace"))
                    capturedQueryKey.set(call.request.queryParameters["api_key"])
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> send(Frame.Text("EchoText:${frame.readText()}"))
                            is Frame.Binary -> send(Frame.Binary(true, frame.data))
                            else -> Unit
                        }
                    }
                }
            }
        }.start(wait = false)

        try {
            val state = workspace.newAppState(openDefaultTab = false)
            val wsTab = RequestTabState(
                name = "Auth & Binary WS",
                kind = RequestKind.WEBSOCKET,
                url = "ws://127.0.0.1:$wsPort/ws-auth",
            ).apply {
                authType = AuthType.BEARER
                authToken = "ws-secret-token"
                headers.add(MutableKeyValue("X-Ws-Trace", "ws-trace-77", kind = HeaderKind.USER))
                webSocketMessageVariants.addAll(
                    listOf(
                        WebSocketMessageVariant(
                            title = "XML Frame",
                            selected = true,
                            message = WebSocketMessage(type = WebSocketMessageType.XML, data = "<event>xml-ws</event>"),
                        ),
                        WebSocketMessageVariant(
                            title = "Binary Frame",
                            selected = false,
                            message = WebSocketMessage(type = WebSocketMessageType.BINARY, data = "bin-payload-42"),
                        ),
                    ),
                )
                activeWebSocketVariantIndex = 0
            }
            state.openTabs.add(wsTab)
            state.activeTabIndex = 0

            composeRule.setContent { MainScreen(state) }
            composeRule.waitForIdle()

            // 1. Connect with Bearer auth + custom header and send XML variant (Frame.Text)
            composeRule.onNodeWithTag("websocket-connect-button", useUnmergedTree = true).performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                wsTab.webSocketConnectionState == ConnectionState.CONNECTED && capturedTrace.get() != null
            }
            assertEquals("Bearer ws-secret-token", capturedAuth.get())
            assertEquals("ws-trace-77", capturedTrace.get())

            composeRule.onNodeWithTag("websocket-send-button", useUnmergedTree = true).performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                wsTab.webSocketLogMessages.any {
                    it.direction == MessageDirection.RECEIVED && it.payload.contains("EchoText:<event>xml-ws</event>")
                }
            }

            // 2. Switch to Binary variant and verify Frame.Binary round-trip
            composeRule.runOnUiThread { wsTab.activeWebSocketVariantIndex = 1 }
            composeRule.waitForIdle()
            assertEquals(WebSocketMessageType.BINARY, wsTab.effectiveWebSocketFormat)

            composeRule.onNodeWithTag("websocket-send-button", useUnmergedTree = true).performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                wsTab.webSocketLogMessages.any {
                    it.direction == MessageDirection.SENT && it.format == "binary" && it.payload == "bin-payload-42"
                } && wsTab.webSocketLogMessages.any {
                    it.direction == MessageDirection.RECEIVED && it.format == "binary" && it.payload == "bin-payload-42"
                }
            }

            // 3. Disconnect, switch to API_KEY in query placement, reconnect and verify query param on handshake
            composeRule.onNodeWithTag("websocket-disconnect-button", useUnmergedTree = true).performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                wsTab.webSocketConnectionState == ConnectionState.DISCONNECTED
            }
            composeRule.runOnUiThread {
                wsTab.authType = AuthType.API_KEY
                wsTab.authApiKey = "api_key"
                wsTab.authApiValue = "ws-query-key"
                wsTab.authApiPlacement = "query"
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("websocket-connect-button", useUnmergedTree = true).performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                wsTab.webSocketConnectionState == ConnectionState.CONNECTED && capturedQueryKey.get() != null
            }
            assertEquals("ws-query-key", capturedQueryKey.get())

            composeRule.onNodeWithTag("websocket-disconnect-button", useUnmergedTree = true).performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                wsTab.webSocketConnectionState == ConnectionState.DISCONNECTED
            }
        } finally {
            runCatching { customWsServer.stop(200, 1_000) }
        }
    }
}
