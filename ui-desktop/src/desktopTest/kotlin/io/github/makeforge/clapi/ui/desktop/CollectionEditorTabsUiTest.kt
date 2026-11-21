package io.github.makeforge.clapi.ui.desktop

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.state.AppState
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import io.github.makeforge.clapi.ui.shared.state.AppStateExamples

class CollectionEditorTabsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun collection_editor_tabs_are_ordered_with_info_first() {
        val state = AppStateExamples.demo()
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Open collection editor for c1
        composeRule.onNodeWithTag("collection-node-c1").performClick()
        composeRule.waitForIdle()

        assertEquals(RequestKind.COLLECTION, state.activeTab?.kind)

        // Verify tabs exist
        composeRule.onNodeWithTag("collection-tab-info").assertIsDisplayed()
        composeRule.onNodeWithTag("collection-tab-variables").assertIsDisplayed()
        composeRule.onNodeWithTag("collection-tab-headers").assertIsDisplayed()
        composeRule.onNodeWithTag("collection-tab-auth").assertIsDisplayed()
        composeRule.onNodeWithTag("collection-tab-pre-request").assertIsDisplayed()
        composeRule.onNodeWithTag("collection-tab-tests").assertIsDisplayed()
        composeRule.onNodeWithTag("collection-tab-protobuf").assertIsDisplayed()

        // Info panel is displayed by default
        composeRule.onNodeWithTag("collection-info-panel").assertIsDisplayed()
        composeRule.onNodeWithTag("collection-info-name").assertIsDisplayed()
        composeRule.onNodeWithTag("collection-info-summary").assertIsDisplayed()
        composeRule.onNodeWithTag("collection-info-version").assertIsDisplayed()
    }

    @Test
    fun info_tab_metadata_editing_and_save_syncs_to_collection_node() {
        val state = AppStateExamples.demo()
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("collection-node-c1").performClick()
        composeRule.waitForIdle()

        val tab = state.activeTab!!

        // Edit Name, Summary, Version
        composeRule.onNodeWithTag("collection-info-name").performTextClearance()
        composeRule.onNodeWithTag("collection-info-name").performTextInput("Updated Users API")
        composeRule.onNodeWithTag("collection-info-summary").performTextInput("Comprehensive user management endpoints")
        composeRule.onNodeWithTag("collection-info-version").performTextInput("2.1.0")
        composeRule.waitForIdle()

        assertTrue(tab.isDirty)
        composeRule.onNodeWithTag("collection-save-button").assertIsEnabled()

        // Save
        composeRule.onNodeWithTag("collection-save-button").performClick()
        composeRule.waitForIdle()

        assertFalse(tab.isDirty)
        val colNode = state.collections.first { it.id == "c1" }
        assertEquals("Updated Users API", colNode.name)
        assertEquals("Comprehensive user management endpoints", colNode.summary)
        assertEquals("2.1.0", colNode.version)
    }

    @Test
    fun switching_between_request_defaults_tabs_in_collection_editor() {
        val state = AppStateExamples.demo()
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("collection-node-c1").performClick()
        composeRule.waitForIdle()

        // Navigate to Headers
        composeRule.onNodeWithTag("collection-tab-headers").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("collection-info-panel").assertDoesNotExist()

        // Navigate to Auth
        composeRule.onNodeWithTag("collection-tab-auth").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auth-chip-none").assertIsDisplayed()
        // Inherit must not be available for root collections
        composeRule.onAllNodesWithTag("auth-chip-inherit").assertCountEquals(0)

        // Navigate to Protobuf
        composeRule.onNodeWithTag("collection-tab-protobuf").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("collection-protobuf-panel").assertIsDisplayed()
    }
}
