package io.github.crustack.clapi.ui.desktop.importers

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.crustack.clapi.ui.shared.components.ImportSpecificationDialog
import io.github.crustack.clapi.ui.shared.platform.copyToClipboard
import io.github.crustack.clapi.ui.shared.state.AppState
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportClipboardUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleOpenCollection = """
        opencollection: "1.0.0"
        info:
          name: "Clipboard OpenCollection"
          version: "1.0.0"
        items:
          - info:
              name: "Health Check"
              type: "http"
            http:
              method: "GET"
              url: "https://api.example.com/health"
    """.trimIndent()

    @Test
    fun emptyClipboard_showsEmptyMessage_andDisablesScan() {
        copyToClipboard("")

        val state = AppState().apply { showImportSpecificationDialog = true }
        composeRule.setContent {
            ImportSpecificationDialog(
                state = state,
                onDismiss = { state.showImportSpecificationDialog = false }
            )
        }

        composeRule.onNodeWithTag("import-tab-clipboard", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("import-clipboard-empty").assertIsDisplayed()
        composeRule.onNodeWithTag("import-scan-button").assertIsNotEnabled()
    }

    @Test
    fun validOpenCollectionClipboard_scansAndImportsSuccessfully() {
        copyToClipboard(sampleOpenCollection)

        val state = AppState().apply { showImportSpecificationDialog = true }
        composeRule.setContent {
            ImportSpecificationDialog(
                state = state,
                onDismiss = { state.showImportSpecificationDialog = false }
            )
        }

        composeRule.onNodeWithTag("import-tab-clipboard", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("import-clipboard-content").assertIsDisplayed()

        composeRule.onNodeWithTag("import-scan-button", useUnmergedTree = true).performClick()

        composeRule.waitUntil(5000) {
            composeRule.onAllNodesWithTag("import-format-badge").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("import-confirm-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertFalse(state.showImportSpecificationDialog)
        assertEquals(1, state.collections.size)
        assertEquals("Clipboard OpenCollection", state.collections.first().name)
    }

    @Test
    fun invalidClipboardContent_displaysErrorBanner_andRemainsOnStep1() {
        copyToClipboard("this is definitely not a valid OpenAPI or OpenCollection spec")

        val state = AppState().apply { showImportSpecificationDialog = true }
        composeRule.setContent {
            ImportSpecificationDialog(
                state = state,
                onDismiss = { state.showImportSpecificationDialog = false }
            )
        }

        composeRule.onNodeWithTag("import-tab-clipboard", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("import-scan-button", useUnmergedTree = true).performClick()

        composeRule.waitUntil(5000) {
            composeRule.onAllNodesWithTag("import-scan-error").fetchSemanticsNodes().isNotEmpty()
        }

        // Error banner is displayed
        composeRule.onNodeWithTag("import-scan-error").assertIsDisplayed()

        // Still on Step 1 (tabs are still visible)
        composeRule.onNodeWithTag("import-tab-clipboard").assertIsDisplayed()
        assertTrue(state.showImportSpecificationDialog)
    }
}
