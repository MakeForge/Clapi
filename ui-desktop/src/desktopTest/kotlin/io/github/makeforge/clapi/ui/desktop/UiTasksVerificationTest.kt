package io.github.makeforge.clapi.ui.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.makeforge.clapi.core.network.VariableResolver
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.components.getAvailableVariables
import io.github.makeforge.clapi.ui.shared.components.variable.VariableDropdownList
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.AppStateExamples
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UiTasksVerificationTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── Task 1: WebSocket in MainScreen does not show ResponseViewer ────────────

    @Test
    fun websocket_screen_shows_editor_and_timeline_without_response_viewer() {
        val state = AppStateExamples.webSocket()
        composeRule.setContent {
            ClapiTheme {
                MainScreen(state)
            }
        }
        composeRule.waitForIdle()

        // WebSocket editor, address bar, timeline, and disconnect button must be displayed
        composeRule.onNodeWithTag("websocket-request-editor").assertIsDisplayed()
        composeRule.onNodeWithTag("websocket-timeline").assertIsDisplayed()
        composeRule.onNodeWithTag("websocket-disconnect-button").assertIsDisplayed()

        // REST response viewer / split pane should NOT be present for WebSocket
        composeRule.onNodeWithTag("response-viewer").assertDoesNotExist()
        composeRule.onNodeWithTag("empty-response-view").assertDoesNotExist()
    }

    // ── Task 2: Sidebar "+" button tooltip "Add" and dropdown without "New" ─────

    @Test
    fun sidebar_add_button_has_add_content_description_and_dropdown_without_new_prefix() {
        val state = AppStateExamples.demo()
        val firstCollectionId = state.collections.first().id
        composeRule.setContent {
            ClapiTheme {
                MainScreen(state)
            }
        }
        composeRule.waitForIdle()

        // Button exists with tag collection-add-<id>
        val addButton = composeRule.onNodeWithTag("collection-add-$firstCollectionId", useUnmergedTree = true)
        addButton.assertIsDisplayed()

        // Open "+" dropdown
        addButton.performClick()
        composeRule.waitForIdle()

        // Dropdown items should simply be "Folder", "HTTP Request", "gRPC Request", etc. (no "New" prefix)
        composeRule.onNodeWithTag("folder-menu-new-folder", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Folder").assertIsDisplayed()
        composeRule.onNodeWithText("HTTP Request").assertIsDisplayed()
        composeRule.onNodeWithText("gRPC Request").assertIsDisplayed()
        composeRule.onNodeWithText("WebSocket Request").assertIsDisplayed()
        composeRule.onNodeWithText("GraphQL Request").assertIsDisplayed()

        // "New Folder" / "New HTTP Request" must not exist in this menu
        composeRule.onNodeWithText("New Folder").assertDoesNotExist()
        composeRule.onNodeWithText("New HTTP Request").assertDoesNotExist()
    }

    // ── Task 3: Switch variable type to secret resets inline value & shows dash hint ─

    @Test
    fun env_variable_type_switch_to_secret_resets_inline_value_and_shows_dash_hint() {
        val state = AppStateExamples.demo().apply {
            environments.first().variables.clear()
            environments.first().variables.add(MutableKeyValue("myVar", "cleartext-value", enabled = true, secret = false))
            openEnvEdit(0)
        }

        composeRule.setContent {
            ClapiTheme {
                MainScreen(state)
            }
        }
        composeRule.waitForIdle()

        // Open Kind dropdown and select "Secret"
        composeRule.onNodeWithTag("env-var-type-toggle-0").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("env-var-type-toggle-0-option-secret").performClick()
        composeRule.waitForIdle()

        // Save dialog
        composeRule.onNodeWithTag("env-save-button").performClick()
        composeRule.waitForIdle()

        val kv = state.environments.first().variables.first()
        assertTrue(kv.secret)
        assertEquals("", kv.value)
    }

    @Test
    fun global_variable_type_switch_to_secret_resets_inline_value_and_shows_dash_hint() {
        val state = AppState().apply {
            globalVariables.add(MutableKeyValue("apiKey", "super-secret-key", enabled = true, secret = false))
            showGlobalVariablesDialog = true
        }

        composeRule.setContent {
            ClapiTheme {
                MainScreen(state)
            }
        }
        composeRule.waitForIdle()

        // Click global-var-type-0 to switch to secret
        composeRule.onNodeWithTag("global-var-type-0").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("-").assertIsDisplayed()

        composeRule.onNodeWithTag("global-vars-save").performClick()
        composeRule.waitForIdle()

        val gv = state.globalVariables.first()
        assertTrue(gv.secret)
        assertEquals("", gv.value)
    }

    // ── Task 4: CTRL+Space dropdown shows dynamic variables ──────────────────────

    @Test
    fun getAvailableVariables_includes_dynamic_variables_at_bottom() {
        val state = AppState()
        val tab = state.activeTab
        val available = getAvailableVariables(state, tab)

        val names = available.map { it.first }
        assertTrue(names.contains("\$timestamp"))
        assertTrue(names.contains("\$isoTimestamp"))
        assertTrue(names.contains("\$randomInt"))
        assertTrue(names.contains("\$randomInt(min, max)"))
        assertTrue(names.contains("\$guid"))

        // Dynamic tokens resolve through VariableResolver
        assertNotNull(VariableResolver.resolveDynamicToken("\$timestamp"))
        assertNotNull(VariableResolver.resolveDynamicToken("\$isoTimestamp"))
        assertNotNull(VariableResolver.resolveDynamicToken("\$randomInt"))
        assertNotNull(VariableResolver.resolveDynamicToken("\$guid"))
    }

    @Test
    fun variable_dropdown_list_displays_dynamic_variables_section() {
        val vars = listOf(
            "baseUrl" to "https://api.example.com",
            "\$timestamp" to "Current Unix epoch milliseconds",
            "\$randomInt" to "Random integer (0..1000)",
        )

        var selectedName: String? = null
        composeRule.setContent {
            ClapiTheme {
                VariableDropdownList(
                    variables = vars,
                    selectedIndex = 1,
                    onSelect = { selectedName = it },
                )
            }
        }
        composeRule.waitForIdle()

        // "Dynamic Variables" section header and DYNAMIC badge must be displayed
        composeRule.onNodeWithText("Dynamic Variables").assertIsDisplayed()
        composeRule.onAllNodesWithText("DYNAMIC", substring = false).onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("{{ \$timestamp }}").assertIsDisplayed()

        // Click dynamic item to select
        composeRule.onNodeWithTag("variable-dropdown-item-\$timestamp").performClick()
        assertEquals("\$timestamp", selectedName)
    }
}
