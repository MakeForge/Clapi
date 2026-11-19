package io.github.makeforge.clapi.ui.desktop.aot

import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.model.BodyType
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.core.model.KeyValueEntry
import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.core.model.ResponseDefinition
import io.github.makeforge.clapi.core.model.ResponseMetrics
import io.github.makeforge.clapi.core.network.VariableResolver
import io.github.makeforge.clapi.core.network.dynamicvar.DynamicVariableRegistry
import io.github.makeforge.clapi.ui.shared.i18n.AppLanguage
import io.github.makeforge.clapi.ui.shared.i18n.I18nProvider
import io.github.makeforge.clapi.ui.shared.platform.currentTimeMillis
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.BottomTab
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import io.github.makeforge.clapi.ui.shared.state.RequestEditorTab
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.github.makeforge.clapi.ui.shared.state.ResponseTab
import io.github.makeforge.clapi.ui.shared.state.WorkspaceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Executes a simulated user journey during the AOT training run (`-Dnucleus.aot.mode=training`).
 *
 * HotSpot Ahead-Of-Time (AOT) cache profiling captures method execution, bytecodes, and class
 * layouts only for paths actually exercised during the training session. This runner systematically
 * walks key views, editors, dialogs, and serialization models so that subsequent production
 * launches experience instantaneous, warm-start performance across all common features.
 */
object AotTrainingRunner {

    /**
     * Executes all training scenarios sequentially, allowing Compose and the JVM to profile
     * each component hierarchy before the automatic exit timer fires.
     */
    suspend fun runTrainingPass(state: AppState) {
        runCatching {
            // Scenario 1: Preload foundational singletons, dynamic variable generators, and localization bundles.
            preloadFoundations()

            // Delay briefly to allow initial Compose layout passes to settle.
            delay(500.milliseconds)

            // Scenario 2: Exercise HTTP request editor sub-tabs, CodeMirror syntax highlighting, and auth forms.
            val httpTabId = exerciseHttpRequestEditor(state)

            // Scenario 3: Exercise response viewer, status metrics, and response body tab variations.
            exerciseResponseViewer(state)

            // Scenario 4: Exercise specialized protocol editors (GraphQL explorer and WebSocket composer).
            val extraTabIds = exerciseSpecializedProtocols(state)

            // Scenario 5: Exercise workspace mode switching (HTTP <-> Realtime <-> GraphQL) and bottom diagnostics.
            exerciseWorkspaceModesAndPanels(state)

            // Scenario 6: Exercise sidebar search filtering and collection tree expansions.
            exerciseSidebarInteractions(state)

            // Scenario 7: Open and dismiss modal overlays (Settings, Global Variables, and Help dialogs).
            exerciseModalDialogs(state)

            // Scenario 8: Clean up training tabs to ensure no synthetic data persists into saved workspaces.
            cleanupTrainingTabs(state, listOf(httpTabId) + extraTabIds)
        }
    }

    /**
     * Scenario 1: Foundation Singletons & I18n Preloading.
     * Preloads static translation dictionaries and initializes dynamic variable generators
     * so runtime variable lookups do not incur first-time initialization latency.
     */
    private suspend fun preloadFoundations() = withContext(Dispatchers.Default) {
        AppLanguage.entries.forEach { lang ->
            I18nProvider(lang).get("app_name")
        }
        VariableResolver.resolve("https://example.com/{{endpoint}}?t={{${'$'}timestamp}}", listOf(mapOf("endpoint" to "api")))
        val registry = DynamicVariableRegistry()
        registry.extractTokens(listOf("{{${'$'}guid}}", "{{${'$'}timestamp}}", "{{${'$'}randomInt}}"))
    }

    /**
     * Scenario 2: HTTP Request Workspace & Editor Tabs.
     * Mounts an HTTP request tab and cycles through all editor sub-tabs:
     * - Params: Key-value query parameters table
     * - Headers: Header rows and system header rules
     * - Body: JSON body editor (compiles and warms CodeMirror lexer, fold map, and syntax highlighter)
     * - Auth: Bearer and Basic authentication input fields
     * - Scripts: Pre-request script and post-request test assertions editors
     * - Variables: Tab-scoped variable mappings
     */
    private suspend fun exerciseHttpRequestEditor(state: AppState): String {
        state.addTab(
            name = "AOT HTTP Warmup",
            method = HttpMethodType.POST,
            url = "https://httpbin.org/post?page=1",
        )
        val tab = state.activeTab ?: return ""
        val tabId = tab.id

        // Params & Headers
        tab.selectedEditorTab = RequestEditorTab.PARAMS
        tab.params.add(MutableKeyValue("filter", "active"))
        delay(150.milliseconds)

        tab.selectedEditorTab = RequestEditorTab.HEADERS
        tab.headers.add(MutableKeyValue("X-Custom-Header", "TrainingValue"))
        delay(150.milliseconds)

        // JSON Body: Triggers code editor layout, document model, IdleLexer, and syntax tokenization
        tab.selectedEditorTab = RequestEditorTab.BODY
        tab.bodyType = BodyType.JSON
        tab.bodyContent = """
            {
              "requestId": "${tab.id}",
              "action": "aot_profile_warmup",
              "enabled": true,
              "items": [1, 2, 3]
            }
        """.trimIndent()
        delay(200.milliseconds)

        // Auth Form: Exercises Bearer and Basic credential forms
        tab.selectedEditorTab = RequestEditorTab.AUTH
        tab.authType = AuthType.BEARER
        tab.authToken = "bearer-training-token"
        delay(150.milliseconds)
        tab.authType = AuthType.BASIC
        tab.authUsername = "user"
        tab.authPassword = "password"
        delay(150.milliseconds)

        // Scripting & Assertions: Exercises pre-request and test script editors
        tab.selectedEditorTab = RequestEditorTab.PRE_REQUEST
        tab.preRequestScript = "// Warm up pre-request editor\nclapi.variables.set('test', 123);"
        delay(150.milliseconds)

        tab.selectedEditorTab = RequestEditorTab.TESTS
        tab.testScript = "// Warm up post-request tests\nclapi.assert(response.status === 200);"
        delay(150.milliseconds)

        tab.selectedEditorTab = RequestEditorTab.VARIABLES
        delay(150.milliseconds)

        return tabId
    }

    /**
     * Scenario 3: Response Viewer & Performance Metrics.
     * Attaches a synthetic HTTP response to the active tab to pre-warm the response viewer components,
     * including syntax-highlighted response body rendering, headers table, raw payload viewer,
     * and network timing metrics charts.
     */
    private suspend fun exerciseResponseViewer(state: AppState) {
        val tab = state.activeTab ?: return

        tab.response = ResponseDefinition(
            requestId = tab.id,
            statusCode = 200,
            statusText = "OK",
            headers = listOf(
                KeyValueEntry("Content-Type", "application/json; charset=utf-8"),
                KeyValueEntry("X-Response-Time", "42ms"),
            ),
            cookies = emptyList(),
            bodyText = """{"status": "ok", "recordsProcessed": 42, "cached": true}""",
            contentType = "application/json",
            executedAtEpochMillis = currentTimeMillis(),
            metrics = ResponseMetrics(
                statusCode = 200,
                responseTimeMs = 42L,
                responseSizeBytes = 256L,
                dnsMs = 5L,
                connectMs = 8L,
                tlsMs = 12L,
                serverMs = 10L,
                downloadMs = 7L,
                ttfbMs = 35L,
            ),
        )

        // Cycle through response viewer modes
        tab.responseTab = ResponseTab.BODY
        delay(150.milliseconds)
        tab.responseTab = ResponseTab.HEADERS
        delay(150.milliseconds)
        tab.responseTab = ResponseTab.TIMING
        delay(150.milliseconds)
        tab.responseTab = ResponseTab.RAW
        delay(150.milliseconds)
        tab.responseTab = ResponseTab.BODY
    }

    /**
     * Scenario 4: Multi-Protocol Request Workspaces.
     * Mounts dedicated GraphQL and WebSocket tabs to compile and warm protocol-specific editors
     * (query syntax highlight, connection management, message stream viewers).
     */
    private suspend fun exerciseSpecializedProtocols(state: AppState): List<String> {
        val extraIds = mutableListOf<String>()

        // GraphQL Tab: query editor and variables panel
        val gqlTab = RequestTabState(
            name = "AOT GraphQL Warmup",
            kind = RequestKind.GRAPHQL,
            url = "https://api.example.com/graphql",
        )
        gqlTab.graphQlQuery = "query GetUser { user(id: 1) { id name email } }"
        state.openTabs.add(gqlTab)
        state.activeTabIndex = state.openTabs.lastIndex
        extraIds.add(gqlTab.id)
        delay(200.milliseconds)

        // WebSocket Tab: message composer and events view
        val wsTab = RequestTabState(
            name = "AOT WebSocket Warmup",
            kind = RequestKind.WEBSOCKET,
            url = "wss://echo.websocket.org",
        )
        state.openTabs.add(wsTab)
        state.activeTabIndex = state.openTabs.lastIndex
        extraIds.add(wsTab.id)
        delay(200.milliseconds)

        return extraIds
    }

    /**
     * Scenario 5: Workspace Modes & Bottom Diagnostics Panel.
     * Cycles top-level workspace modes (HTTP, Realtime, GraphQL) and expands the bottom diagnostics
     * panel across Console, Test Results, and Network Event Logs.
     */
    private suspend fun exerciseWorkspaceModesAndPanels(state: AppState) {
        state.workspaceMode = WorkspaceMode.REALTIME
        delay(200.milliseconds)
        state.workspaceMode = WorkspaceMode.GRAPHQL
        delay(200.milliseconds)
        state.workspaceMode = WorkspaceMode.HTTP
        delay(200.milliseconds)

        // Bottom panel diagnostics tabs
        state.bottomPanelExpanded = true
        state.selectedBottomTab = BottomTab.CONSOLE
        delay(150.milliseconds)
        state.selectedBottomTab = BottomTab.TEST_RESULTS
        delay(150.milliseconds)
        state.selectedBottomTab = BottomTab.LOGS
        delay(150.milliseconds)
        state.bottomPanelExpanded = false
    }

    /**
     * Scenario 6: Sidebar & Collections Navigation.
     * Exercises sidebar filtering, search query handling, and tree section toggles.
     */
    private suspend fun exerciseSidebarInteractions(state: AppState) {
        state.sidebarSearchQuery = "warmup"
        delay(150.milliseconds)
        state.sidebarSearchQuery = ""
        state.historyExpanded = true
        delay(100.milliseconds)
        state.historyExpanded = false
        state.environmentsExpanded = true
        delay(100.milliseconds)
        state.environmentsExpanded = false
    }

    /**
     * Scenario 7: Application Overlays & Dialogs.
     * Opens and dismisses primary application modal dialogs so their layout cards, section lists,
     * and preference components are fully loaded and JIT-compiled into the cache.
     */
    private suspend fun exerciseModalDialogs(state: AppState) {
        // Settings Dialog (General, Editor, Theme, Language, Network, Secrets)
        state.showSettingsDialog = true
        delay(350.milliseconds)
        state.showSettingsDialog = false
        delay(150.milliseconds)

        // Global Variables Dialog
        state.showGlobalVariablesDialog = true
        delay(200.milliseconds)
        state.showGlobalVariablesDialog = false
        delay(150.milliseconds)

        // Help & Shortcuts Dialog
        state.showHelpDialog = true
        delay(200.milliseconds)
        state.showHelpDialog = false
        delay(150.milliseconds)
    }

    /**
     * Scenario 8: Clean Teardown.
     * Removes all synthetic tabs created during training so the workspace remains pristine.
     */
    private fun cleanupTrainingTabs(state: AppState, tabIds: List<String>) {
        state.closeTabsByIds(tabIds.toSet())
    }
}
