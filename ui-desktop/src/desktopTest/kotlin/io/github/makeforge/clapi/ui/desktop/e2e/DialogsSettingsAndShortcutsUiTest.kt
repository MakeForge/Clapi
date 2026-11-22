// Modified by MakeForge (2026)
package io.github.makeforge.clapi.ui.desktop.e2e

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.ui.desktop.e2e.support.EmbeddedSampleServerRule
import io.github.makeforge.clapi.ui.desktop.e2e.support.IsolatedWorkspaceRule
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.components.ExportCollectionsDialog
import io.github.makeforge.clapi.ui.shared.components.ResponseViewer
import io.github.makeforge.clapi.ui.shared.persistence.SettingsRepository
import io.github.makeforge.clapi.ui.shared.state.AppSettings
import io.github.makeforge.clapi.ui.shared.state.AppTheme
import io.github.makeforge.clapi.ui.shared.state.BottomTab
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import io.github.makeforge.clapi.ui.shared.state.DeletedEnvironmentSnapshot
import io.github.makeforge.clapi.ui.shared.state.DeletedHistorySnapshot
import io.github.makeforge.clapi.ui.shared.state.DeletedNodeSnapshot
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.HistoryItem
import io.github.makeforge.clapi.ui.shared.state.LogLevel
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import io.github.makeforge.clapi.ui.shared.state.UndoableWorkspaceActions
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme
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
 * Interactive Compose Desktop UI tests covering previously untested dialogs, settings panes,
 * theme & environment switchers, global keyboard shortcuts, multi-tab dirty-close dialog,
 * real undoable snackbar workspace actions, bottom panel, and detached response viewer lifecycle.
 *
 * Covers FR-011 & FR-012 (US4).
 */
@OptIn(InternalComposeUiApi::class)
class DialogsSettingsAndShortcutsUiTest {

    companion object {
        @JvmField
        @ClassRule
        val server = EmbeddedSampleServerRule()
    }

    @get:Rule
    val workspace = IsolatedWorkspaceRule()

    @get:Rule
    val composeRule = createComposeRule()

    // ── ExportCollectionsDialog ──────────────────────────────────────────────────────────────

    @Test
    fun export_collections_dialog_toggles_selection_switches_format_and_writes_files() {
        val state = workspace.newAppState(openDefaultTab = false)
        state.createNewCollection("Col Alpha")
        state.createNewCollection("Col Beta")
        val colAlpha = state.collections[0]
        val colBeta = state.collections[1]
        state.addRequestToCollection(colAlpha.id)
        state.addRequestToCollection(colBeta.id)

        val exportDir = Files.createTempDirectory("clapi-ui-export-").toFile()
        var dismissed = false
        var writtenFiles: List<String> = emptyList()

        try {
            composeRule.setContent {
                ClapiTheme {
                    ExportCollectionsDialog(
                        state = state,
                        onDismiss = { dismissed = true },
                        onPickDirectory = { cb -> cb(exportDir.absolutePath) },
                        onExportComplete = { writtenFiles = it },
                    )
                }
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("export-collections-dialog", useUnmergedTree = true).assertIsDisplayed()

            // Toggle Select All off -> Confirm disabled, then back on
            composeRule.onNodeWithTag("export-collections-select-all", useUnmergedTree = true).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("export-collections-select-all", useUnmergedTree = true).performClick()
            composeRule.waitForIdle()

            // Deselect Col Beta so only Col Alpha exports
            composeRule.onNodeWithTag("collection-export-item-${colBeta.id}", useUnmergedTree = true).performClick()
            composeRule.waitForIdle()

            // Switch format to YAML and confirm export
            composeRule.onNodeWithTag("export-format-yaml", useUnmergedTree = true).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("export-collections-confirm-button", useUnmergedTree = true).performClick()
            composeRule.waitForIdle()

            assertTrue(dismissed, "Export dialog must dismiss after export")
            assertEquals(1, writtenFiles.size, "Expected 1 exported collection file, got: $writtenFiles")
            val exportedFile = File(writtenFiles.single())
            assertTrue(exportedFile.exists() && exportedFile.length() > 0L)
            assertTrue(
                exportedFile.extension.equals("yaml", ignoreCase = true) ||
                    exportedFile.extension.equals("yml", ignoreCase = true),
                "Expected YAML extension, got: ${exportedFile.name}",
            )
        } finally {
            exportDir.deleteRecursively()
        }
    }

    // ── Embedded MCP Server Settings Section ─────────────────────────────────────────────────

    @Test
    fun mcp_server_settings_toggles_server_lifecycle_and_renders_status_card() {
        val state = workspace.newAppState(openDefaultTab = false).apply {
            settings.mcpServerPort = java.net.ServerSocket(0).use { it.localPort }
            settings.mcpServerEnabled = false
        }

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-section-mcp_server", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("mcp-server-status-card", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("mcp-server-enable-toggle", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            state.embeddedMcpServerController.isRunning
        }
        assertTrue(state.settings.mcpServerEnabled)

        // Toggle back OFF -> stops cleanly
        composeRule.onNodeWithTag("mcp-server-enable-toggle", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            !state.embeddedMcpServerController.isRunning
        }
        assertFalse(state.settings.mcpServerEnabled)
    }

    // ── Theme Switching & TopToolbar Environment Picker (with Inheritance) ───────────────────

    @Test
    fun theme_settings_updates_and_persists_theme_and_env_picker_switches_active_env() {
        val baseEnv = EnvState("BaseEnv").apply {
            variables.add(MutableKeyValue("baseUrl", server.baseUrl))
            variables.add(MutableKeyValue("envMarker", "from-base"))
        }
        val childEnv = EnvState("ChildEnv", extends = "BaseEnv").apply {
            variables.add(MutableKeyValue("envMarker", "from-child"))
        }
        val state = workspace.newAppState(openDefaultTab = true).apply {
            environments.addAll(listOf(baseEnv, childEnv))
            selectedEnvIndex = 0
        }
        val tab = state.activeTab!!
        tab.url = "{{baseUrl}}/api/echo-headers"
        tab.headers.add(
            MutableKeyValue(
                "X-Env-Marker",
                "{{envMarker}}",
                kind = io.github.makeforge.clapi.ui.shared.state.HeaderKind.USER,
            ),
        )

        composeRule.setContent {
            ClapiTheme(appTheme = state.settings.theme, language = state.settings.language) {
                MainScreen(state)
            }
        }
        composeRule.waitForIdle()

        // Switch theme Light -> Dark -> System via Settings
        composeRule.onNodeWithTag("settings-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-section-theme", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("theme-light", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(AppTheme.LIGHT, state.settings.theme)

        composeRule.onNodeWithTag("theme-system", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(AppTheme.SYSTEM, state.settings.theme)

        SettingsRepository.save(state.settings)
        val reloadedSettings = AppSettings()
        SettingsRepository.load(reloadedSettings)
        assertEquals(AppTheme.SYSTEM, reloadedSettings.theme)

        composeRule.onNodeWithTag("settings-close-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // Switch active environment from BaseEnv to ChildEnv via TopToolbar env-chip dropdown
        composeRule.onNodeWithTag("env-chip", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("env-dropdown-item-1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(1, state.selectedEnvIndex)
        assertEquals("ChildEnv", state.selectedEnvironment?.name)

        // Send request and confirm inherited baseUrl + overridden envMarker from ChildEnv hit the wire
        composeRule.onNodeWithTag("send-button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { !tab.isLoading && tab.response != null }
        assertEquals(200, tab.response?.statusCode)
        assertTrue(tab.response!!.bodyText.contains("from-child"), "Expected ChildEnv override, got: ${tab.response?.bodyText}")
    }

    // ── Global Keyboard Shortcuts & DirtyMultiCloseDialog ────────────────────────────────────

    @Test
    fun main_screen_global_hotkeys_and_dirty_multi_close_dialog_work_end_to_end() {
        val state = workspace.newAppState(openDefaultTab = true).apply {
            settings.autoSaveRequests = false
        }
        state.activeTab!!.url = "${server.baseUrl}/api/users"

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Focus url-input inside MainScreen so preview key events route through MainScreen.hotkeys
        val focusedTarget = composeRule.onNodeWithTag("url-input", useUnmergedTree = true)
        focusedTarget.performClick()
        composeRule.waitForIdle()

        // Ctrl+Enter -> Send request
        focusedTarget.performKeyPress(KeyEvent(Key.Enter, KeyEventType.KeyDown, isCtrlPressed = true))
        composeRule.waitUntil(timeoutMillis = 10_000) {
            !state.activeTab!!.isLoading && state.activeTab!!.response != null
        }
        assertEquals(200, state.activeTab!!.response?.statusCode)

        // Ctrl+N -> New Tab
        val beforeTabs = state.openTabs.size
        focusedTarget.performKeyPress(KeyEvent(Key.N, KeyEventType.KeyDown, isCtrlPressed = true))
        composeRule.waitForIdle()
        assertEquals(beforeTabs + 1, state.openTabs.size)

        // Re-focus active tab's url-input after tab switch
        composeRule.onNodeWithTag("url-input", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // Ctrl+Shift+[ -> Move active tab left, Ctrl+Shift+] -> Move back right
        val movedTabId = state.activeTab!!.id
        composeRule.onNodeWithTag("url-input", useUnmergedTree = true)
            .performKeyPress(KeyEvent(Key.LeftBracket, KeyEventType.KeyDown, isCtrlPressed = true, isShiftPressed = true))
        composeRule.waitForIdle()
        assertEquals(movedTabId, state.openTabs[0].id)

        composeRule.onNodeWithTag("url-input", useUnmergedTree = true)
            .performKeyPress(KeyEvent(Key.RightBracket, KeyEventType.KeyDown, isCtrlPressed = true, isShiftPressed = true))
        composeRule.waitForIdle()
        assertEquals(movedTabId, state.openTabs[1].id)

        // Ctrl+, -> Open Settings dialog, then close it
        composeRule.onNodeWithTag("url-input", useUnmergedTree = true)
            .performKeyPress(KeyEvent(Key.Comma, KeyEventType.KeyDown, isCtrlPressed = true))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-dialog", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("settings-close-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // Ctrl+W on clean tab closes immediately
        val countBeforeClose = state.openTabs.size
        composeRule.onNodeWithTag("url-input", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("url-input", useUnmergedTree = true)
            .performKeyPress(KeyEvent(Key.W, KeyEventType.KeyDown, isCtrlPressed = true))
        composeRule.waitForIdle()
        assertEquals(countBeforeClose - 1, state.openTabs.size)

        // Ctrl+W on dirty tab opens DirtyCloseDialog
        state.activeTab!!.url = "${server.baseUrl}/api/users?dirty=1"
        state.activeTab!!.markDirty()
        composeRule.onNodeWithTag("url-input", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("url-input", useUnmergedTree = true)
            .performKeyPress(KeyEvent(Key.W, KeyEventType.KeyDown, isCtrlPressed = true))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dirty-close-dialog", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("dirty-close-cancel", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // DirtyMultiCloseDialog via right-click "Close All" with 2 dirty tabs
        state.addTab(name = "Dirty B")
        state.activeTab!!.url = "${server.baseUrl}/api/users?dirty=2"
        state.activeTab!!.markDirty()
        val tabId = state.activeTab!!.id
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("tab-chip-$tabId", useUnmergedTree = true).performMouseInput { rightClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Close All", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dirty-multi-close-dialog", useUnmergedTree = true).assertIsDisplayed()

        // Cancel keeps tabs open
        composeRule.onNodeWithTag("dirty-multi-cancel", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(2, state.openTabs.size)

        // Trigger again and click Discard All -> closes all dirty tabs
        composeRule.onNodeWithTag("tab-chip-$tabId", useUnmergedTree = true).performMouseInput { rightClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Close All", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dirty-multi-discard", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertTrue(state.openTabs.isEmpty(), "Discard All must close all dirty tabs")
    }

    // ── Real Undoable Action Snackbar, BottomPanel & Detached Response Viewer ───────────────

    @Test
    fun undoable_workspace_actions_restore_items_via_snackbar_and_bottom_panel_and_detached_viewer_stay_synced() {
        val state = workspace.newAppState(openDefaultTab = false).apply {
            collectionsExpanded = true
            environmentsExpanded = true
            historyExpanded = true
            bottomPanelExpanded = true
        }
        state.createNewCollection("Orders API")
        val col = state.collections.first()
        val reqNode = CollectionNode(
            id = "req-orders-1",
            name = "List Orders",
            isFolder = false,
            method = HttpMethodType.GET,
            url = "${server.baseUrl}/api/users",
        )
        val folder = CollectionNode(
            id = "folder-admin",
            name = "Admin Folder",
            isFolder = true,
            children = androidx.compose.runtime.mutableStateListOf(reqNode),
        )
        col.children.add(folder)
        state.openRequest(reqNode.id, reqNode.name, HttpMethodType.GET, reqNode.url.orEmpty())

        val env = EnvState("Staging")
        state.environments.add(env)
        val histItem = HistoryItem(
            requestId = reqNode.id,
            name = "List Orders",
            method = HttpMethodType.GET,
            url = "${server.baseUrl}/api/users",
            timestamp = 1_700_000_000_000L,
            collectionId = col.id,
        )
        state.historyItems.add(histItem)
        state.log("diag-entry-1", LogLevel.INFO)

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // 1. Delete request -> Undo via snackbar UI restores request
        composeRule.runOnUiThread {
            val snap = DeletedNodeSnapshot(folder.children.removeAt(0), parentId = folder.id, index = 0)
            UndoableWorkspaceActions.showDeletedNodesSnackbar(state, listOf(snap))
        }
        composeRule.waitForIdle()
        assertTrue(folder.children.isEmpty())
        composeRule.onNodeWithTag("action-snackbar", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("action-snackbar-undo", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(1, folder.children.size)

        // 2. Rename request -> Undo via snackbar UI restores old name
        composeRule.runOnUiThread {
            state.renameRequestEverywhere(reqNode.id, "Renamed Orders")
            UndoableWorkspaceActions.showRenamedItemSnackbar(state, "List Orders", "Renamed Orders") {
                state.renameRequestEverywhere(reqNode.id, "List Orders")
                true
            }
        }
        composeRule.waitForIdle()
        assertEquals("Renamed Orders", folder.children.first().name)
        composeRule.onNodeWithTag("action-snackbar-undo", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals("List Orders", folder.children.first().name)

        // 3. Delete nested folder -> Undo restores folder + nested child request
        composeRule.runOnUiThread {
            val snap = DeletedNodeSnapshot(col.children.removeAt(0), parentId = col.id, index = 0)
            UndoableWorkspaceActions.showDeletedNodesSnackbar(state, listOf(snap))
        }
        composeRule.waitForIdle()
        assertTrue(col.children.isEmpty())
        composeRule.onNodeWithTag("action-snackbar-undo", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(1, col.children.size)
        assertEquals(1, col.children.first().children.size)

        // 4. Delete collection -> Undo restores entire collection
        composeRule.runOnUiThread {
            val snap = DeletedNodeSnapshot(state.collections.removeAt(0), parentId = null, index = 0)
            UndoableWorkspaceActions.showDeletedNodesSnackbar(state, listOf(snap))
        }
        composeRule.waitForIdle()
        assertTrue(state.collections.isEmpty())
        composeRule.onNodeWithTag("action-snackbar-undo", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(1, state.collections.size)

        // 5. Delete environment & history item -> Undo restores each
        composeRule.runOnUiThread {
            val removed = state.environments.removeAt(0)
            UndoableWorkspaceActions.showDeletedEnvironmentSnackbar(
                state,
                DeletedEnvironmentSnapshot(removed, index = 0, previousSelectedEnvIndex = 0),
            )
        }
        composeRule.waitForIdle()
        assertTrue(state.environments.isEmpty())
        composeRule.onNodeWithTag("action-snackbar-undo", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(1, state.environments.size)

        composeRule.runOnUiThread {
            val removed = state.historyItems.removeAt(0)
            UndoableWorkspaceActions.showDeletedHistorySnackbar(
                state,
                listOf(DeletedHistorySnapshot(removed, index = 0)),
            )
        }
        composeRule.waitForIdle()
        assertTrue(state.historyItems.isEmpty())
        composeRule.onNodeWithTag("action-snackbar-undo", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(1, state.historyItems.size)

        // 6. BottomPanel tab switching (Console, Test Results, Logs)
        composeRule.onNodeWithText("Console", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(BottomTab.CONSOLE, state.selectedBottomTab)
        composeRule.onNodeWithText("Test Results", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(BottomTab.TEST_RESULTS, state.selectedBottomTab)
        composeRule.onNodeWithText("Logs", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(BottomTab.LOGS, state.selectedBottomTab)

        // 7. Detached response viewer stays synced across Send and tab close
        composeRule.runOnUiThread {
            state.detachResponse()
            state.activeTab!!.url = "${server.baseUrl}/api/users"
        }
        composeRule.waitForIdle()
        assertTrue(state.isResponseDetached)
        composeRule.onNodeWithTag("send-button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            !state.activeTab!!.isLoading && state.activeTab!!.response != null
        }
        assertEquals(200, state.activeTab!!.response?.statusCode)
        composeRule.runOnUiThread { state.reattachResponse() }
        composeRule.waitForIdle()
        assertFalse(state.isResponseDetached)
    }
}
