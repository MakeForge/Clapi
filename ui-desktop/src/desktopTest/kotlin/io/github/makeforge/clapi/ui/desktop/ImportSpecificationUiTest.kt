package io.github.makeforge.clapi.ui.desktop

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.components.ImportSpecificationDialog
import io.github.makeforge.clapi.ui.shared.platform.copyToClipboard
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportSpecificationUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleOpenApi = """
        openapi: "3.0.3"
        info:
          title: "Test Petstore"
          version: "1.0.0"
        servers:
          - url: "https://api.example.com/v1"
        paths:
          /pets:
            get:
              summary: "List pets"
              responses:
                '200':
                  description: "ok"
    """.trimIndent()

    @Test
    fun importDialog_notShownByDefault() {
        composeRule.setContent { MainScreen(AppState()) }
        composeRule.onNodeWithTag("import-specification-dialog").assertDoesNotExist()
    }

    @Test
    fun importDialog_opensViaSidebarButton() {
        val state = AppState()
        composeRule.setContent { MainScreen(state) }
        composeRule.onNodeWithTag("collection-import-button", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("import-specification-dialog").assertIsDisplayed()
        assertTrue(state.showImportSpecificationDialog)
    }

    @Test
    fun importDialog_closesViaCancelButton() {
        val state = AppState().apply { showImportSpecificationDialog = true }
        composeRule.setContent { MainScreen(state) }
        composeRule.onNodeWithTag("import-specification-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("import-cancel-button", useUnmergedTree = true).performClick()
        assertFalse(state.showImportSpecificationDialog)
    }

    @Test
    fun importDialog_switchesBetweenTabs() {
        val state = AppState().apply { showImportSpecificationDialog = true }
        composeRule.setContent { MainScreen(state) }

        // Initial tab is File
        composeRule.onNodeWithTag("import-dropzone").assertIsDisplayed()

        // Switch to Clipboard
        composeRule.onNodeWithTag("import-tab-clipboard", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("import-dropzone").assertDoesNotExist()

        // Switch back to File
        composeRule.onNodeWithTag("import-tab-file", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("import-dropzone").assertIsDisplayed()
    }

    @Test
    fun importDialog_scanClipboardOpenApi_andConfirmImport() {
        copyToClipboard(sampleOpenApi)

        val state = AppState().apply { showImportSpecificationDialog = true }
        composeRule.setContent {
            ImportSpecificationDialog(
                state = state,
                onDismiss = { state.showImportSpecificationDialog = false }
            )
        }

        // Switch to Clipboard tab
        composeRule.onNodeWithTag("import-tab-clipboard", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // Scan button should be enabled
        composeRule.onNodeWithTag("import-scan-button", useUnmergedTree = true).performClick()

        composeRule.waitUntil(5000) {
            composeRule.onAllNodesWithTag("import-format-badge").fetchSemanticsNodes().isNotEmpty()
        }

        // Format badge should appear in Step 2
        composeRule.onNodeWithTag("import-format-badge").assertIsDisplayed()

        // Click Import
        composeRule.onNodeWithTag("import-confirm-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertFalse(state.showImportSpecificationDialog)
        assertTrue(state.collections.any { it.name == "Test Petstore" })
        assertTrue(state.environments.any { it.name.contains("Petstore") })
    }

    @Test
    fun importDialog_cancelInStep2_doesNotMutateState() {
        copyToClipboard(sampleOpenApi)

        val state = AppState().apply { showImportSpecificationDialog = true }
        composeRule.setContent {
            ImportSpecificationDialog(
                state = state,
                onDismiss = { state.showImportSpecificationDialog = false }
            )
        }

        composeRule.onNodeWithTag("import-tab-clipboard", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("import-scan-button", useUnmergedTree = true).performClick()

        composeRule.waitUntil(5000) {
            composeRule.onAllNodesWithTag("import-format-badge").fetchSemanticsNodes().isNotEmpty()
        }

        // Step 2 cancel button
        composeRule.onNodeWithTag("import-cancel-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertFalse(state.showImportSpecificationDialog)
        assertTrue(state.collections.isEmpty())
        assertTrue(state.environments.isEmpty())
    }

    @Test
    fun importDialog_deselectAllItems_disablesConfirmButton() {
        copyToClipboard(sampleOpenApi)

        val state = AppState().apply { showImportSpecificationDialog = true }
        composeRule.setContent {
            ImportSpecificationDialog(
                state = state,
                onDismiss = { state.showImportSpecificationDialog = false }
            )
        }

        composeRule.onNodeWithTag("import-tab-clipboard", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("import-scan-button", useUnmergedTree = true).performClick()

        composeRule.waitUntil(5000) {
            composeRule.onAllNodesWithTag("import-format-badge").fetchSemanticsNodes().isNotEmpty()
        }

        // Initially enabled because items are selected by default
        composeRule.onNodeWithTag("import-confirm-button", useUnmergedTree = true).assertIsEnabled()

        // Find folder and environment checkboxes and deselect them
        val folderCheckNode = composeRule.onAllNodes(hasTestTagStartingWith("import-folder-check-"), useUnmergedTree = true)
            .fetchSemanticsNodes().first()
        val folderId = folderCheckNode.config[SemanticsProperties.TestTag].removePrefix("import-folder-check-")
        composeRule.onNodeWithTag("import-folder-check-$folderId", useUnmergedTree = true).performClick()

        val envCheckNodes = composeRule.onAllNodes(hasTestTagStartingWith("import-env-check-"), useUnmergedTree = true)
            .fetchSemanticsNodes()
        if (envCheckNodes.isNotEmpty()) {
            val envId = envCheckNodes.first().config[SemanticsProperties.TestTag].removePrefix("import-env-check-")
            composeRule.onNodeWithTag("import-env-check-$envId", useUnmergedTree = true).performClick()
        }

        composeRule.waitForIdle()

        // Now confirm button should be disabled
        composeRule.onNodeWithTag("import-confirm-button", useUnmergedTree = true).assertIsNotEnabled()

        // Re-select folder
        composeRule.onNodeWithTag("import-folder-check-$folderId", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // Should be enabled again
        composeRule.onNodeWithTag("import-confirm-button", useUnmergedTree = true).assertIsEnabled()
    }

    @Test
    fun importDialog_errorBanner_onInvalidContent() {
        copyToClipboard("invalid non-yaml non-openapi content: {{{{")

        val state = AppState().apply { showImportSpecificationDialog = true }
        composeRule.setContent {
            ImportSpecificationDialog(
                state = state,
                onDismiss = { state.showImportSpecificationDialog = false }
            )
        }

        composeRule.onNodeWithTag("import-tab-clipboard", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("import-scan-button", useUnmergedTree = true).performClick()

        composeRule.waitUntil(5000) {
            composeRule.onAllNodesWithTag("import-scan-error").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("import-scan-error").assertIsDisplayed()
        // Stays on Step 1
        composeRule.onNodeWithTag("import-tab-clipboard").assertIsDisplayed()
    }

    private fun hasTestTagStartingWith(prefix: String) =
        SemanticsMatcher("testTag starts with $prefix") {
            it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith(prefix)
        }
}
