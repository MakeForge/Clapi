package io.github.crustack.clapi.ui.desktop.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.crustack.clapi.core.model.RequestKind
import io.github.crustack.clapi.ui.shared.MainScreen
import io.github.crustack.clapi.ui.shared.state.AppState
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SidebarMultipleCollectionsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displays_collections_header_and_adds_new_collection() {
        val state = AppState(withDemoData = false, openDefaultTab = false)
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("sidebar-collections-header").assertIsDisplayed()

        // Click '+' to add collection
        composeRule.onNodeWithTag("sidebar-add-collection-button").performClick()
        composeRule.waitForIdle()

        assertEquals(1, state.collections.size)
        val created = state.collections.first()
        assertTrue(created.isCollection)
        assertEquals("New Collection", created.name)

        // Verify collection node is displayed in sidebar
        composeRule.onNodeWithTag("collection-node-${created.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("collection-icon-${created.id}", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun clicking_collection_node_opens_dedicated_collection_tab() {
        val state = AppState(withDemoData = false, openDefaultTab = false).apply {
            collectionsExpanded = true
        }
        val col = state.createNewCollection("Payments API")
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("collection-node-${col.id}").performClick()
        composeRule.waitForIdle()

        assertTrue(state.openTabs.any { it.kind == RequestKind.COLLECTION && it.id == col.id })
        assertEquals(RequestKind.COLLECTION, state.activeTab?.kind)
        assertEquals("Payments API", state.activeTab?.name)
        composeRule.onNodeWithTag("collection-editor").assertIsDisplayed()
    }

    @Test
    fun toggling_collections_header_expands_and_collapses_list() {
        val state = AppState(withDemoData = false, openDefaultTab = false).apply {
            collectionsExpanded = true
        }
        val col = state.createNewCollection("Orders API")
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("collection-node-${col.id}").assertIsDisplayed()

        // Click header to collapse
        composeRule.onNodeWithTag("sidebar-collections-header").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("collection-node-${col.id}").assertDoesNotExist()

        // Click header again to expand
        composeRule.onNodeWithTag("sidebar-collections-header").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("collection-node-${col.id}").assertIsDisplayed()
    }

    @Test
    fun inline_renaming_collection_to_existing_name_auto_disambiguates() {
        val state = AppState(withDemoData = false, openDefaultTab = false).apply {
            collectionsExpanded = true
        }
        val col1 = state.createNewCollection("Test API")
        val col2 = state.createNewCollection("Other API")

        // Disambiguate logic used by inline rename:
        val disambiguated = state.disambiguateCollectionName("Test API", col2.id)
        assertEquals("Test API (1)", disambiguated)
        state.renameCollection(col2.id, disambiguated)
        assertEquals("Test API (1)", state.collections.first { it.id == col2.id }.name)
    }
}
