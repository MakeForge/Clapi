package io.github.makeforge.clapi.ui.desktop

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.ui.shared.components.sidebar.Sidebar
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SidebarMultiSelectionUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleState(): AppState {
        val state = AppState(openDefaultTab = false)
        state.settings.confirmBeforeDelete = false
        state.collectionsExpanded = true
        state.collections.clear()
        state.collections.add(
            CollectionNode(
                id = "c1",
                name = "Users API",
                isFolder = true,
                children = mutableStateListOf(
                    CollectionNode("r1", "Get all users", method = HttpMethodType.GET, url = "/users"),
                    CollectionNode("r2", "Create user", method = HttpMethodType.POST, url = "/users"),
                    CollectionNode("r3", "Update user", method = HttpMethodType.PUT, url = "/users/1"),
                )
            )
        )
        state.collections.add(
            CollectionNode(
                id = "c2",
                name = "Auth API",
                isFolder = true,
                children = mutableStateListOf(
                    CollectionNode("r4", "Login", method = HttpMethodType.POST, url = "/auth/login"),
                )
            )
        )
        state.collectionExpandedState["c1"] = true
        state.collectionExpandedState["c2"] = true
        return state
    }

    @Test
    fun leftClick_withoutModifiers_opensTab_andSetsSingleSelection() {
        val state = sampleState()
        composeRule.setContent { Sidebar(state) }

        composeRule.onNodeWithTag("collection-node-r1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // Tab is opened
        assertEquals(1, state.openTabs.size)
        assertEquals("r1", state.openTabs.first().id)
        // Only r1 is selected in sidebar
        assertEquals(listOf("r1"), state.selectedRequestIds)
        assertTrue(state.selectedFolderIds.isEmpty())
    }

    @Test
    fun contextMenu_whenMultipleRequestsSelected_showsOnlyDuplicateAndDelete() {
        val state = sampleState()
        // Pre-select two requests
        state.selectedRequestIds.addAll(listOf("r1", "r2"))
        state.lastSelectedSidebarItemId = "r2"

        composeRule.setContent { Sidebar(state) }

        // Right-click on r1 (which is selected and multiple are selected)
        composeRule.onNodeWithTag("collection-actions-r1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // Context menu items: Duplicate and Delete should be displayed
        composeRule.onNodeWithTag("collection-menu-multi-duplicate", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("collection-menu-multi-delete", useUnmergedTree = true).assertIsDisplayed()

        // Single-item options like "rename" or "move_up" should NOT exist in multi-selection context menu
        composeRule.onAllNodesWithTag("collection-menu-rename", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithTag("collection-menu-add-request", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun contextMenu_multiDuplicate_duplicatesAllSelectedRequests() {
        val state = sampleState()
        state.selectedRequestIds.addAll(listOf("r1", "r2"))
        state.lastSelectedSidebarItemId = "r2"

        composeRule.setContent { Sidebar(state) }

        composeRule.onNodeWithTag("collection-actions-r1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("collection-menu-multi-duplicate", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // c1 had 3 requests, now duplicated r1 and r2 -> 5 requests
        assertEquals(5, state.collections[0].children.size)
        val names = state.collections[0].children.map { it.name }
        assertTrue(names.any { it.contains("Get all users Copy") })
        assertTrue(names.any { it.contains("Create user Copy") })
    }

    @Test
    fun contextMenu_multiDelete_deletesAllSelectedRequestsAndClosesTabs() {
        val state = sampleState()
        // Open tabs for r1 and r2
        state.openRequest("r1", "Get all users", HttpMethodType.GET, "/users")
        state.openRequest("r2", "Create user", HttpMethodType.POST, "/users")
        assertEquals(2, state.openTabs.size)

        state.selectedRequestIds.addAll(listOf("r1", "r2"))
        state.lastSelectedSidebarItemId = "r2"

        composeRule.setContent { Sidebar(state) }

        composeRule.onNodeWithTag("collection-actions-r1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("collection-menu-multi-delete", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // Requests deleted from collection
        assertEquals(1, state.collections[0].children.size)
        assertEquals("r3", state.collections[0].children.first().id)
        // Tabs closed
        assertTrue(state.openTabs.isEmpty())
        // Selection cleared
        assertTrue(state.selectedRequestIds.isEmpty())
    }

    @Test
    fun contextMenu_whenMultipleFoldersSelected_showsOnlyDuplicateAndDelete() {
        val state = sampleState()
        state.selectedFolderIds.addAll(listOf("c1", "c2"))
        state.lastSelectedSidebarItemId = "c2"

        composeRule.setContent { Sidebar(state) }

        composeRule.onNodeWithTag("collection-node-c1", useUnmergedTree = true).performMouseInput {
            rightClick()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("collection-menu-multi-duplicate", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("collection-menu-multi-delete", useUnmergedTree = true).assertIsDisplayed()

        // Folder single-item actions like "add_folder" or "export_folder" should NOT be shown
        composeRule.onAllNodesWithTag("collection-menu-add-folder", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun contextMenu_multiDelete_deletesAllSelectedFolders() {
        val state = sampleState()
        state.openRequest("r1", "Get all users", HttpMethodType.GET, "/users")
        state.selectedFolderIds.addAll(listOf("c1", "c2"))
        state.lastSelectedSidebarItemId = "c2"

        composeRule.setContent { Sidebar(state) }

        composeRule.onNodeWithTag("collection-node-c1", useUnmergedTree = true).performMouseInput {
            rightClick()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("collection-menu-multi-delete", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertTrue(state.collections.isEmpty())
        assertTrue(state.openTabs.isEmpty())
        assertTrue(state.selectedFolderIds.isEmpty())
    }

    @Test
    fun contextMenu_multiDuplicate_duplicatesAllSelectedFolders() {
        val state = sampleState()
        state.selectedFolderIds.addAll(listOf("c1", "c2"))
        state.lastSelectedSidebarItemId = "c2"

        composeRule.setContent { Sidebar(state) }

        composeRule.onNodeWithTag("collection-node-c1", useUnmergedTree = true).performMouseInput {
            rightClick()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("collection-menu-multi-duplicate", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // 2 folders initially, now duplicated c1 and c2 -> 4 folders
        assertEquals(4, state.collections.size)
        val names = state.collections.map { it.name }
        assertTrue(names.any { it.contains("Users API (1)") })
        assertTrue(names.any { it.contains("Auth API (1)") })
    }
}
