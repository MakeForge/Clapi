package io.github.makeforge.clapi.ui.desktop.importers

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import io.github.makeforge.clapi.ui.shared.components.ImportSpecificationDialog
import io.github.makeforge.clapi.ui.shared.platform.copyToClipboard
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import io.github.makeforge.clapi.ui.shared.state.EnvState
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportConflictResolutionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleOpenApi = """
        openapi: "3.0.3"
        info:
          title: "My Existing Service"
          version: "1.0.0"
        servers:
          - url: "https://api.example.com"
        paths:
          /ping:
            get:
              summary: "Ping operation"
              responses:
                '200':
                  description: "pong"
    """.trimIndent()

    @Test
    fun conflictBadge_displayedWhenNameCollides_andDisappearsOnRename() {
        val state = AppState().apply {
            showImportSpecificationDialog = true
            collections.add(
                CollectionNode(
                    id = "existing-1",
                    name = "My Existing Service",
                    isFolder = true,
                )
            )
        }

        copyToClipboard(sampleOpenApi)

        composeRule.setContent {
            ImportSpecificationDialog(
                state = state,
                onDismiss = { state.showImportSpecificationDialog = false }
            )
        }

        // Switch to Clipboard and Scan
        composeRule.onNodeWithTag("import-tab-clipboard", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("import-scan-button", useUnmergedTree = true).performClick()

        composeRule.waitUntil(5000) {
            composeRule.onAllNodesWithTag("import-format-badge").fetchSemanticsNodes().isNotEmpty()
        }

        val nameNode = composeRule.onAllNodes(hasTestTagStartingWith("import-folder-name-"), useUnmergedTree = true)
            .fetchSemanticsNodes().first()
        val candidateId = nameNode.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].removePrefix("import-folder-name-")

        composeRule.onNodeWithTag("import-folder-conflict-$candidateId").assertIsDisplayed()

        // Rename the folder to avoid conflict
        composeRule.onNodeWithTag("import-folder-name-$candidateId").performTextReplacement("My Unique Service")
        composeRule.waitForIdle()

        // Conflict badge should disappear
        composeRule.onNodeWithTag("import-folder-conflict-$candidateId").assertDoesNotExist()

        // Rename back to conflicting name
        composeRule.onNodeWithTag("import-folder-name-$candidateId").performTextReplacement("My Existing Service")
        composeRule.waitForIdle()

        // Conflict badge reappears
        composeRule.onNodeWithTag("import-folder-conflict-$candidateId").assertIsDisplayed()
    }

    @Test
    fun selectiveImport_excludesUncheckedItems() {
        val state = AppState().apply {
            showImportSpecificationDialog = true
        }

        copyToClipboard(sampleOpenApi)

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

        val nameNode = composeRule.onAllNodes(hasTestTagStartingWith("import-folder-name-"), useUnmergedTree = true)
            .fetchSemanticsNodes().first()
        val candidateId = nameNode.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].removePrefix("import-folder-name-")

        // Uncheck the folder
        composeRule.onNodeWithTag("import-folder-check-$candidateId", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // If environment exists, uncheck all environments
        val envNodes = composeRule.onAllNodes(hasTestTagStartingWith("import-env-check-"), useUnmergedTree = true)
            .fetchSemanticsNodes()
        envNodes.forEach { node ->
            val envId = node.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].removePrefix("import-env-check-")
            composeRule.onNodeWithTag("import-env-check-$envId", useUnmergedTree = true).performClick()
        }
        composeRule.waitForIdle()

        // Import button should be disabled when nothing is selected
        composeRule.onNodeWithTag("import-confirm-button").assertIsNotEnabled()

        // Re-check the folder
        composeRule.onNodeWithTag("import-folder-check-$candidateId", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("import-confirm-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertFalse(state.showImportSpecificationDialog)
        assertEquals(1, state.collections.size)
        assertEquals("My Existing Service", state.collections.first().name)
        // No environments should have been imported since all envs were unchecked
        assertEquals(0, state.environments.size)
    }

    @Test
    fun overwriteLogic_replacesExistingCollection_withoutDuplicating() {
        val state = AppState().apply {
            showImportSpecificationDialog = true
            collections.add(
                CollectionNode(
                    id = "orig-id",
                    name = "My Existing Service",
                    isFolder = true,
                )
            )
        }

        copyToClipboard(sampleOpenApi)

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

        // Confirm import with conflicting name
        composeRule.onNodeWithTag("import-confirm-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // Total collection count must remain 1 (overwritten, not duplicated)
        assertEquals(1, state.collections.size)
        val imported = state.collections.first()
        assertEquals("My Existing Service", imported.name)
        assertTrue(imported.children.isNotEmpty())
    }

    private fun hasTestTagStartingWith(prefix: String) =
        androidx.compose.ui.test.SemanticsMatcher("testTag starts with $prefix") {
            it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { "" }.startsWith(prefix)
        }
}
