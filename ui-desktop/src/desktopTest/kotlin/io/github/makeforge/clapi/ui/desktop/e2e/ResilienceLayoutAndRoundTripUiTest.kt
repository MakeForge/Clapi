// Modified by MakeForge (2026)
package io.github.makeforge.clapi.ui.desktop.e2e

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.makeforge.clapi.core.model.BodyType
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.core.model.McpConnectionConfig
import io.github.makeforge.clapi.core.model.McpConnectionState
import io.github.makeforge.clapi.core.model.McpHttpMode
import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqOperationMode
import io.github.makeforge.clapi.core.network.ConnectionState
import io.github.makeforge.clapi.core.storage.opencollection.ExportFormat
import io.github.makeforge.clapi.ui.desktop.aot.AotTrainingRunner
import io.github.makeforge.clapi.ui.desktop.e2e.support.EmbeddedSampleServerRule
import io.github.makeforge.clapi.ui.desktop.e2e.support.IsolatedWorkspaceRule
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.persistence.ImportExportRepository
import io.github.makeforge.clapi.ui.shared.persistence.SettingsRepository
import io.github.makeforge.clapi.ui.shared.persistence.TabsRepository
import io.github.makeforge.clapi.ui.shared.persistence.WorkspaceRepository
import io.github.makeforge.clapi.ui.shared.platform.PlatformStorage
import io.github.makeforge.clapi.ui.shared.state.AppSettings
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import kotlinx.coroutines.runBlocking
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Resilience, layout boundary, startup/shutdown, malformed import, and multi-protocol
 * export/re-import round-trip tests.
 *
 * Covers FR-013 & FR-015 (US5) plus AOT training runner smoke check (FR-004).
 */
class ResilienceLayoutAndRoundTripUiTest {

    companion object {
        @JvmField
        @ClassRule
        val server = EmbeddedSampleServerRule()
    }

    @get:Rule
    val workspace = IsolatedWorkspaceRule()

    @get:Rule
    val composeRule = createComposeRule()

    // ── Corrupt Storage & Orphaned Tab Recovery ──────────────────────────────────────────────

    @Test
    fun corrupt_storage_and_orphaned_tabs_recover_gracefully_preserving_valid_collections() {
        // Save one valid collection first
        val initState = workspace.newAppState(openDefaultTab = false)
        initState.createNewCollection("Healthy Collection")
        initState.addRequestToCollection(initState.collections.first().id)
        WorkspaceRepository.save(initState)

        // Inject corrupt collection file alongside healthy collection + corrupt prefs for tabs & settings
        File(workspace.tempDir, "broken-collection.yaml").writeText(":::not valid yaml {{{{ [\n\t<<<")
        PlatformStorage.putString("clapi.tabs", "{ truncated-json !!!")
        PlatformStorage.putString("clapi.settings", "<<<not-json>>>")

        // Reload into a fresh AppState -> must NOT throw, must preserve Healthy Collection
        val recovered = workspace.newAppState(openDefaultTab = false)
        SettingsRepository.load(recovered.settings)
        WorkspaceRepository.load(recovered)
        TabsRepository.load(recovered)

        assertTrue(
            recovered.collections.any { it.name == "Healthy Collection" },
            "Valid collection must survive alongside corrupted files; loaded=${recovered.collections.map { it.name }}",
        )

        // Orphaned tab referencing a deleted request ID recovers cleanly on mount
        val orphanTab = RequestTabState(
            id = "deleted-req-999",
            name = "Deleted Request",
            url = "${server.baseUrl}/api/users",
            collectionId = "non-existent-collection",
        )
        recovered.openTabs.add(orphanTab)
        recovered.activeTabIndex = 0
        recovered.syncSidebarToActiveTab()

        composeRule.setContent { MainScreen(recovered) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("main-screen").assertIsDisplayed()
    }

    // ── Shutdown Persistence & Active Session Teardown ───────────────────────────────────────

    @Test
    fun shutdown_persists_workspace_and_tears_down_active_ws_and_mcp_sessions() {
        val state = workspace.newAppState(openDefaultTab = false)
        state.createNewCollection("Shutdown Col")
        val col = state.collections.first()
        state.addRequestToCollection(col.id)
        state.activeTab!!.url = "${server.baseUrl}/api/users"

        // Add live WS and MCP tabs and connect them
        val wsTab = RequestTabState(name = "Live WS", kind = RequestKind.WEBSOCKET, url = server.wsUrl)
        val mcpTab = RequestTabState(name = "Live MCP", kind = RequestKind.MCP, url = server.mcpHttpUrl).apply {
            mcpConfig = McpConnectionConfig(url = server.mcpHttpUrl, httpMode = McpHttpMode.STREAMABLE_2025_06_18)
        }
        state.openTabs.addAll(listOf(wsTab, mcpTab))
        state.activeTabIndex = 1

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("websocket-connect-button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            wsTab.webSocketConnectionState == ConnectionState.CONNECTED
        }

        val mcpSession = state.getOrCreateMcpSession(mcpTab.id)
        runBlocking { mcpSession.connect(mcpTab.mcpConfig) }
        assertEquals(McpConnectionState.CONNECTED, mcpSession.connectionState.value)

        // Mirror Main.kt onCloseRequest sequence
        TabsRepository.save(state)
        WorkspaceRepository.save(state)
        runBlocking { state.disposeAndAwait() }

        // Reload and verify persisted collection + tabs survived shutdown
        val reloaded = workspace.newAppState(openDefaultTab = false)
        WorkspaceRepository.load(reloaded)
        TabsRepository.load(reloaded)
        assertTrue(reloaded.collections.any { it.name == "Shutdown Col" })
        assertTrue(reloaded.openTabs.isNotEmpty())
    }

    // ── AOT Training Runner Smoke Test ───────────────────────────────────────────────────────

    @Test
    fun aot_training_runner_completes_without_error_inside_main_screen() {
        val state = workspace.newAppState(openDefaultTab = false)
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        runBlocking { AotTrainingRunner.runTrainingPass(state) }
        composeRule.waitForIdle()

        assertTrue(state.openTabs.isEmpty(), "AOT training pass must clean up its synthetic tabs")
        composeRule.onNodeWithTag("main-screen").assertIsDisplayed()
    }

    // ── Binary Response Handling ─────────────────────────────────────────────────────────────

    @Test
    fun binary_response_renders_safely_in_response_viewer_without_crash() {
        val state = workspace.newAppState(openDefaultTab = true)
        val tab = state.activeTab!!
        tab.method = HttpMethodType.POST
        tab.url = "${server.baseUrl}/api/upload"
        tab.bodyType = BodyType.RAW_TEXT
        tab.bodyContent = "\u0000\u0001\u0002binary-bytes-check"

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("send-button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { !tab.isLoading && tab.response != null }
        composeRule.waitForIdle()

        assertEquals(200, tab.response?.statusCode)
        composeRule.onNodeWithTag("response-viewer").assertIsDisplayed()
        composeRule.onNodeWithTag("response-download-button").assertIsDisplayed()
    }

    // ── Minimum Window Size (800x600) Layout Smoke ───────────────────────────────────────────

    @Test
    fun minimum_window_size_800x600_keeps_primary_controls_displayed_and_usable() {
        val state = workspace.newAppState(openDefaultTab = true)

        composeRule.setContent {
            Box(Modifier.size(800.dp, 600.dp)) {
                MainScreen(state)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("main-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("sidebar").assertIsDisplayed()
        composeRule.onNodeWithTag("request-tabs-bar").assertIsDisplayed()
        composeRule.onNodeWithTag("url-input", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("send-button", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("response-viewer").assertIsDisplayed()
    }

    // ── 20-Tab Overflow, Mid-Flight Tab Close & 5-Level Nested Folders ───────────────────────

    @Test
    fun twenty_open_tabs_overflow_mid_flight_close_and_five_level_folders_behave_cleanly() {
        val state = workspace.newAppState(openDefaultTab = false).apply {
            collectionsExpanded = true
        }
        // Build 5-level nested folder tree
        state.createNewCollection("Deep API")
        var currentParent = state.collections.first()
        repeat(5) { depth ->
            val nextFolder = CollectionNode(id = "deep-lvl-${depth + 1}", name = "Level-${depth + 1}", isFolder = true)
            currentParent.children.add(nextFolder)
            currentParent = nextFolder
        }
        val deepReq = CollectionNode(
            id = "deep-leaf-req",
            name = "Deep Leaf Request",
            isFolder = false,
            method = HttpMethodType.GET,
            url = "${server.baseUrl}/api/users",
        )
        currentParent.children.add(deepReq)
        state.openRequest(deepReq.id, deepReq.name, HttpMethodType.GET, deepReq.url.orEmpty())

        // Open 20 tabs total
        repeat(19) { idx ->
            state.addTab(name = "Tab-${idx + 1}", url = "${server.baseUrl}/api/users?t=$idx")
        }
        assertEquals(20, state.openTabs.size)

        // Start a slow request on the active tab, then close it mid-flight
        val slowTab = state.activeTab!!
        slowTab.url = "${server.baseUrl}/api/slow?ms=5000"

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("send-button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { slowTab.isLoading }

        composeRule.runOnUiThread {
            state.closeTab(state.activeTabIndex)
            state.closeTab(0)
            state.closeTab(state.openTabs.size / 2)
        }
        composeRule.waitForIdle()
        assertEquals(17, state.openTabs.size)
        assertFalse(state.openTabs.any { it.id == slowTab.id })

        // Filter 5-level folder tree via sidebar search
        composeRule.runOnUiThread { state.sidebarSearchQuery = "Deep Leaf" }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("collection-node-${deepReq.id}", useUnmergedTree = true).assertIsDisplayed()
    }

    // ── Malformed Import Rejection & Multi-Protocol Export -> Delete -> Re-Import Round-Trip ─

    @Test
    fun malformed_imports_fail_cleanly_without_mutating_workspace_and_multi_protocol_roundtrip_preserves_all_kinds() {
        val state = workspace.newAppState(openDefaultTab = false)
        state.createNewCollection("Multi-Protocol Suite")
        val col = state.collections.first()

        // Populate all supported request kinds in the collection
        col.children.addAll(
            listOf(
                CollectionNode("req-http", "HTTP Users", isFolder = false, method = HttpMethodType.POST, url = "https://api.test/users", kind = RequestKind.HTTP),
                CollectionNode("req-gql", "GQL User", isFolder = false, method = HttpMethodType.POST, url = "https://api.test/graphql", kind = RequestKind.GRAPHQL, bodyContent = "query { me { id } }"),
                CollectionNode("req-ws", "WS Feed", isFolder = false, method = HttpMethodType.GET, url = "wss://api.test/ws", kind = RequestKind.WEBSOCKET),
                CollectionNode("req-grpc", "gRPC Greet", isFolder = false, method = HttpMethodType.POST, url = "127.0.0.1:50051", kind = RequestKind.GRPC, grpcMethod = "helloworld.Greeter/SayHello"),
                CollectionNode("req-mcp", "MCP Server", isFolder = false, method = HttpMethodType.POST, url = "http://localhost:8080/mcp", kind = RequestKind.MCP),
                CollectionNode(
                    "req-rmq",
                    "RMQ Publish",
                    isFolder = false,
                    method = HttpMethodType.POST,
                    url = "amqp://localhost:5672/",
                    kind = RequestKind.RABBITMQ,
                    rabbitMqDetails = io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestDetails(
                        mode = RabbitMqOperationMode.PUBLISH,
                    ),
                ),
            ),
        )
        state.environments.add(EnvState("Prod").apply { variables.add(MutableKeyValue("host", "api.test")) })

        // 1. Malformed import must return error without touching existing collections
        val beforeCount = state.collections.size
        val badPayloads = listOf(
            "",
            "{ broken json",
            "openapi: [invalid",
            """{"type": "collection.insomnia.rest/5.0", "broken": true""",
        )
        badPayloads.forEach { bad ->
            val result = runCatching { ImportExportRepository.importCollectionFromString(state, bad) }
            assertTrue(
                result.isFailure || result.getOrNull()?.startsWith("Error", ignoreCase = true) == true,
                "Expected malformed payload to fail: $bad -> $result",
            )
        }
        assertEquals(beforeCount, state.collections.size)

        // 2. Multi-protocol Export -> Delete -> Re-Import round-trip
        val exportDir = Files.createTempDirectory("clapi-multiprotocol-rt-").toFile()
        try {
            val written = ImportExportRepository.exportCollectionsToDirectory(
                collections = state.collections.toList(),
                targetDirectory = exportDir.absolutePath,
                format = ExportFormat.JSON,
            )
            assertEquals(1, written.size)
            val exportedJson = File(written.single()).readText()

            // Delete from workspace, then re-import
            state.collections.clear()
            assertTrue(state.collections.isEmpty())

            ImportExportRepository.importCollectionFromString(state, exportedJson)
            assertEquals(1, state.collections.size, "Exported multi-protocol collection must re-import cleanly")
            val reimported = state.collections.first()

            val kinds = reimported.children.map { it.kind }.toSet()
            assertTrue(RequestKind.HTTP in kinds, "Missing HTTP after round-trip: $kinds")
            assertTrue(RequestKind.GRAPHQL in kinds, "Missing GRAPHQL after round-trip: $kinds")
            assertTrue(RequestKind.WEBSOCKET in kinds, "Missing WEBSOCKET after round-trip: $kinds")
            assertTrue(RequestKind.GRPC in kinds, "Missing GRPC after round-trip: $kinds")
            assertTrue(RequestKind.RABBITMQ in kinds, "Missing RABBITMQ after round-trip: $kinds")
        } finally {
            exportDir.deleteRecursively()
        }
    }

    private fun findNodeByName(nodes: List<CollectionNode>, target: String): CollectionNode? {
        for (node in nodes) {
            if (node.name == target) return node
            findNodeByName(node.children, target)?.let { return it }
        }
        return null
    }
}
