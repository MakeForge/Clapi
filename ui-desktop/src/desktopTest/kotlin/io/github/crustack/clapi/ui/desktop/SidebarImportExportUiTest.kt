package io.github.crustack.clapi.ui.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.crustack.clapi.ui.shared.MainScreen
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.EnvState
import io.github.crustack.clapi.ui.shared.state.MutableKeyValue
import org.junit.Rule
import org.junit.Test
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
}
