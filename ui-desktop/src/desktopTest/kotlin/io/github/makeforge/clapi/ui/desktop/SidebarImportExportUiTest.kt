package io.github.makeforge.clapi.ui.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SidebarImportExportUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sidebarButtonBar_displaysImportAndExportButtons() {
        val state = AppState()
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("sidebar-button-bar", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("collection-import-button", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("sidebar-export-button", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun importButton_opensImportDialog() {
        val state = AppState()
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("collection-import-button", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("import-specification-dialog").assertIsDisplayed()
        assertTrue(state.showImportSpecificationDialog)
    }

    @Test
    fun exportButton_showsDropdownMenuWithOptions() {
        val state = AppState()
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("sidebar-export-button", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("sidebar-export-menu").assertIsDisplayed()
        composeRule.onNodeWithTag("export-opencollection-yaml-item").assertIsDisplayed()
        composeRule.onNodeWithTag("export-openapi-yaml-item").assertIsDisplayed()
    }

    @Test
    fun environmentActions_doesNotContainExportButton() {
        val state = AppState().apply {
            environments.add(
                EnvState(
                    name = "TestEnv",
                    variables = listOf(MutableKeyValue("k", "v", enabled = true))
                )
            )
            environmentsExpanded = true
        }
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("env-actions-TestEnv", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("export-environment").assertDoesNotExist()
    }

    @Test
    fun environmentRow_editButtonIsDirectlyLeftOfActionsButton() {
        val state = AppState().apply {
            environments.add(
                EnvState(
                    name = "TestEnv",
                    variables = listOf(MutableKeyValue("k", "v", enabled = true))
                )
            )
            environmentsExpanded = true
        }
        composeRule.setContent { MainScreen(state) }

        val editNode = composeRule.onNodeWithTag("env-edit-TestEnv", useUnmergedTree = true)
        val actionsNode = composeRule.onNodeWithTag("env-actions-TestEnv", useUnmergedTree = true)

        editNode.assertIsDisplayed()
        actionsNode.assertIsDisplayed()

        val editBounds = editNode.getUnclippedBoundsInRoot()
        val actionsBounds = actionsNode.getUnclippedBoundsInRoot()

        assertTrue(editBounds.right <= actionsBounds.left, "Edit button should be placed to the left of the actions button")

        editNode.performClick()
        composeRule.waitForIdle()
        assertEquals(0, state.editingEnvIndex)
    }

    @Test
    fun environmentRow_editButtonShowsTooltipOnHover() {
        val state = AppState().apply {
            environments.add(
                EnvState(
                    name = "TestEnv",
                    variables = listOf(MutableKeyValue("k", "v", enabled = true))
                )
            )
            environmentsExpanded = true
        }
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("env-edit-TestEnv", useUnmergedTree = true).performMouseInput {
            moveTo(androidx.compose.ui.geometry.Offset(10f, 10f))
        }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Edit Environment", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun addCollectionButton_showsTooltipOnHover() {
        val state = AppState()
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("sidebar-add-collection-button", useUnmergedTree = true).performMouseInput {
            moveTo(androidx.compose.ui.geometry.Offset(10f, 10f))
        }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Add Collection", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun environmentImportButton_displaysDownloadIcon() {
        val state = AppState()
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("environment-import-button", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("environment-import-icon", useUnmergedTree = true).assertIsDisplayed()
    }
}
