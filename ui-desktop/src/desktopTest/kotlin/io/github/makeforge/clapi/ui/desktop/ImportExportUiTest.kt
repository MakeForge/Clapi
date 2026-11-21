package io.github.makeforge.clapi.ui.desktop

import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.state.AppState

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import io.github.makeforge.clapi.ui.shared.state.AppStateExamples

class ImportExportUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun collections_section_has_import_button() {
        composeRule.setContent { MainScreen() }
        composeRule.onNodeWithTag("collection-import-button", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun environments_section_has_import_button() {
        composeRule.setContent { MainScreen() }
        composeRule.onNodeWithTag("environment-import-button", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun collection_actions_menu_shows_folder_actions_and_collection_actions() {
        composeRule.setContent { MainScreen(AppStateExamples.demo()) }

        composeRule.onNodeWithTag("collection-actions-c1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Add Folder").assertIsDisplayed()
        composeRule.onNodeWithText("Add Request").assertIsDisplayed()
        composeRule.onNodeWithText("Export Folder").assertIsDisplayed()
        composeRule.onNodeWithText("Duplicate Folder").assertIsDisplayed()
        composeRule.onNodeWithText("Rename").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun environment_actions_menu_shows_duplicate_delete_and_not_export() {
        composeRule.setContent { MainScreen(AppStateExamples.demo()) }

        composeRule.onNodeWithTag("env-actions-Development", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Export Environment").assertDoesNotExist()
        composeRule.onNodeWithText("Duplicate Environment").assertIsDisplayed()
        composeRule.onNodeWithText("Delete Environment").assertIsDisplayed()
    }

    @Test
    fun settings_data_section_omits_workspace_backup_actions() {
        composeRule.setContent { MainScreen() }

        composeRule.onNodeWithTag("settings-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Data").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("clear-history-action", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Export workspace").assertDoesNotExist()
        composeRule.onNodeWithText("Import workspace").assertDoesNotExist()
    }
}
