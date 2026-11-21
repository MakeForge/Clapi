package io.github.makeforge.clapi.ui.desktop

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.ui.shared.components.sidebar.Sidebar
import io.github.makeforge.clapi.ui.shared.platform.resetPointerDoubleClickState
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SidebarMultiSelectionUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun resetDoubleClickTracker() {
        resetPointerDoubleClickState()
    }

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

    private fun nestedSampleState(): AppState {
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
                    CollectionNode(
                        id = "sub1",
                        name = "Admin",
                        isFolder = true,
                        children = mutableStateListOf(
                            CollectionNode("r3", "Ban user", method = HttpMethodType.DELETE, url = "/users/1/ban"),
                        ),
                    ),
                ),
            )
        )
        state.collections.add(
            CollectionNode(
                id = "c2",
                name = "Auth API",
                isFolder = true,
                children = mutableStateListOf(
                    CollectionNode("r4", "Login", method = HttpMethodType.POST, url = "/auth/login"),
                    CollectionNode("r5", "Logout", method = HttpMethodType.POST, url = "/auth/logout"),
                ),
            )
        )
        state.collectionExpandedState["c1"] = true
        state.collectionExpandedState["sub1"] = true
        state.collectionExpandedState["c2"] = true
        return state
    }

    private fun ctrlClickTag(tag: String) {
        composeRule.onNodeWithTag("sidebar", useUnmergedTree = true).performKeyInput { keyDown(Key.CtrlLeft) }
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).performMouseInput { click() }
        composeRule.onNodeWithTag("sidebar", useUnmergedTree = true).performKeyInput { keyUp(Key.CtrlLeft) }
        composeRule.waitForIdle()
    }

    private fun shiftClickTag(tag: String) {
        composeRule.onNodeWithTag("sidebar", useUnmergedTree = true).performKeyInput { keyDown(Key.ShiftLeft) }
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).performMouseInput { click() }
        composeRule.onNodeWithTag("sidebar", useUnmergedTree = true).performKeyInput { keyUp(Key.ShiftLeft) }
        composeRule.waitForIdle()
    }

    private fun ctrlShiftClickTag(tag: String) {
        composeRule.onNodeWithTag("sidebar", useUnmergedTree = true).performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.ShiftLeft)
        }
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).performMouseInput { click() }
        composeRule.onNodeWithTag("sidebar", useUnmergedTree = true).performKeyInput {
            keyUp(Key.ShiftLeft)
            keyUp(Key.CtrlLeft)
        }
        composeRule.waitForIdle()
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

    @Test
    fun ctrlClick_onRequestNameText_inAnotherFolder_selectsBothWithoutDoubleToggle() {
        val state = nestedSampleState()
        composeRule.setContent { Sidebar(state) }

        // Select r1 normally by clicking its text
        composeRule.onNodeWithTag("request-name-r1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("r1"), state.selectedRequestIds)
        assertEquals(1, state.openTabs.size)

        // Hold Ctrl and click r4's text in another folder (c2)
        ctrlClickTag("request-name-r4")
        assertEquals(listOf("r1", "r4"), state.selectedRequestIds)
        assertTrue(state.selectedFolderIds.isEmpty())
        // Ctrl+Click must NOT open a new tab
        assertEquals(1, state.openTabs.size)

        // Hold Ctrl and click r3 inside nested subfolder sub1
        ctrlClickTag("request-name-r3")
        assertEquals(listOf("r1", "r4", "r3"), state.selectedRequestIds)

        // Hold Ctrl and click r4 again -> deselects r4 only
        ctrlClickTag("request-name-r4")
        assertEquals(listOf("r1", "r3"), state.selectedRequestIds)
    }

    @Test
    fun ctrlClick_requestAcrossFolders_thenCtrlClickFolder_switchesToFolderSelectionCleanly() {
        val state = nestedSampleState()
        composeRule.setContent { Sidebar(state) }

        composeRule.onNodeWithTag("request-name-r1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        ctrlClickTag("request-name-r4")
        assertEquals(listOf("r1", "r4"), state.selectedRequestIds)

        // Now Ctrl+Click folder c2 (on its text) -> clears request selection and selects folder c2
        ctrlClickTag("folder-name-c2")
        assertTrue(state.selectedRequestIds.isEmpty())
        assertEquals(listOf("c2"), state.selectedFolderIds)
        // Did not open a folder tab because Ctrl was held
        assertEquals(1, state.openTabs.size)

        // Ctrl+Click folder c1 -> both folders selected
        ctrlClickTag("folder-name-c1")
        assertEquals(listOf("c2", "c1"), state.selectedFolderIds)

        // Ctrl+Click folder c2 again -> deselects c2
        ctrlClickTag("folder-name-c2")
        assertEquals(listOf("c1"), state.selectedFolderIds)
    }

    @Test
    fun ctrlClick_requestAcrossFolders_thenPlainClickFolder_clearsRequestsAndOpensFolder() {
        val state = nestedSampleState()
        composeRule.setContent { Sidebar(state) }

        composeRule.onNodeWithTag("request-name-r1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        ctrlClickTag("request-name-r4")
        assertEquals(listOf("r1", "r4"), state.selectedRequestIds)

        // Plain click on folder c2 clears request selection, selects c2, and opens folder tab
        composeRule.onNodeWithTag("folder-name-c2", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertTrue(state.selectedRequestIds.isEmpty())
        assertEquals(listOf("c2"), state.selectedFolderIds)
        assertEquals(2, state.openTabs.size)
        assertEquals("c2", state.activeTab?.id)
    }

    @Test
    fun shiftClick_acrossFolders_andCollapsedSubfolder_selectsOnlyVisibleRange() {
        val state = nestedSampleState()
        composeRule.setContent { Sidebar(state) }

        // Select r1, then Shift+Click r5 across folders c1 -> sub1 -> c2
        composeRule.onNodeWithTag("request-name-r1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        shiftClickTag("request-name-r5")
        assertEquals(listOf("r1", "r2", "r3", "r4", "r5"), state.selectedRequestIds)

        // Collapse sub1 so r3 is hidden, then Shift+Click r4 from r1
        composeRule.onNodeWithTag("folder-toggle-sub1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("request-name-r1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        shiftClickTag("request-name-r4")
        assertEquals(listOf("r1", "r2", "r4"), state.selectedRequestIds)
    }

    @Test
    fun ctrlShiftClick_extendsExistingSelectionWithRange() {
        val state = nestedSampleState()
        composeRule.setContent { Sidebar(state) }

        composeRule.onNodeWithTag("request-name-r1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        ctrlClickTag("request-name-r3")
        assertEquals(listOf("r1", "r3"), state.selectedRequestIds)

        // Ctrl+Shift+Click r5 extends from anchor r3..r5 while keeping r1
        ctrlShiftClickTag("request-name-r5")
        assertEquals(listOf("r1", "r3", "r4", "r5"), state.selectedRequestIds)
    }

    @Test
    fun rapidCtrlClicks_doNotTriggerInlineRename() {
        val state = sampleState()
        composeRule.setContent { Sidebar(state) }

        composeRule.onNodeWithTag("request-name-r1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // Two rapid Ctrl+Clicks on r2 toggle it on and off without entering rename mode
        ctrlClickTag("request-name-r2")
        assertEquals(listOf("r1", "r2"), state.selectedRequestIds)
        ctrlClickTag("request-name-r2")
        assertEquals(listOf("r1"), state.selectedRequestIds)
        composeRule.onAllNodesWithTag("request-rename-input-r2", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun ctrlClick_onActionsOrFolderToggle_doesNotCorruptMultiSelection() {
        val state = nestedSampleState()
        composeRule.setContent { Sidebar(state) }

        composeRule.onNodeWithTag("request-name-r1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        ctrlClickTag("request-name-r2")
        assertEquals(listOf("r1", "r2"), state.selectedRequestIds)

        // Ctrl+Click folder chevron collapses c2 without clearing request selection
        ctrlClickTag("folder-toggle-c2")
        assertEquals(listOf("r1", "r2"), state.selectedRequestIds)
        assertTrue(state.selectedFolderIds.isEmpty())

        // Ctrl+Click the ... button on r1 keeps r1 & r2 selected and opens multi-select menu
        ctrlClickTag("collection-actions-r1")
        assertEquals(listOf("r1", "r2"), state.selectedRequestIds)
        composeRule.onNodeWithTag("collection-menu-multi-duplicate", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("collection-menu-multi-delete", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun multiSelectParentAndSubfolder_duplicateAndDelete_handleHierarchyWithoutCorruption() {
        val state = nestedSampleState()
        state.openRequest("r3", "Ban user", HttpMethodType.DELETE, "/users/1/ban")
        composeRule.setContent { Sidebar(state) }

        // Select parent c1 and nested subfolder sub1
        ctrlClickTag("folder-name-c1")
        ctrlClickTag("folder-name-sub1")
        assertEquals(listOf("c1", "sub1"), state.selectedFolderIds)

        // Multi-duplicate: c1 is duplicated (including its child sub1), original c1 keeps only 1 subfolder
        composeRule.onNodeWithTag("collection-actions-c1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("collection-menu-multi-duplicate", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(3, state.collections.size)
        val originalC1 = state.collections.first { it.id == "c1" }
        assertEquals(1, originalC1.children.count { it.isFolder })

        // Now multi-delete c1 and sub1
        composeRule.onNodeWithTag("collection-actions-c1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("collection-menu-multi-delete", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertTrue(state.collections.none { it.id == "c1" })
        assertTrue(state.openTabs.isEmpty())
        assertTrue(state.selectedFolderIds.isEmpty())
        assertNull(state.lastSelectedSidebarItemId)
    }

    @Test
    fun complexCrossFolderMixedSelectionAndMultiDeleteSequence_remainsConsistent() {
        val state = nestedSampleState()
        composeRule.setContent { Sidebar(state) }

        // 1. Click r1 -> Ctrl+Click r4 (other folder) -> Ctrl+Click r3 (nested subfolder)
        composeRule.onNodeWithTag("request-name-r1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        ctrlClickTag("request-name-r4")
        ctrlClickTag("request-name-r3")
        assertEquals(listOf("r1", "r4", "r3"), state.selectedRequestIds)

        // 2. Deselect r4, then Shift+Click r2 (anchor is r3 -> selects r2, r3)
        ctrlClickTag("request-name-r4")
        assertEquals(listOf("r1", "r3"), state.selectedRequestIds)
        shiftClickTag("request-name-r2")
        assertEquals(listOf("r2", "r3"), state.selectedRequestIds)

        // 3. Switch to folders with Ctrl+Click on c2 and sub1
        ctrlClickTag("folder-name-c2")
        ctrlClickTag("folder-name-sub1")
        assertTrue(state.selectedRequestIds.isEmpty())
        assertEquals(listOf("c2", "sub1"), state.selectedFolderIds)

        // 4. Plain click on c1, then plain click on r5, then Ctrl+Click r1 across folders
        composeRule.onNodeWithTag("folder-name-c1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("c1"), state.selectedFolderIds)

        composeRule.onNodeWithTag("request-name-r5", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("r5"), state.selectedRequestIds)
        assertTrue(state.selectedFolderIds.isEmpty())

        ctrlClickTag("request-name-r1")
        assertEquals(listOf("r5", "r1"), state.selectedRequestIds)

        // 5. Delete both selected requests across different folders via context menu
        composeRule.onNodeWithTag("collection-actions-r5", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("collection-menu-multi-delete", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertTrue(state.selectedRequestIds.isEmpty())
        assertNull(state.lastSelectedSidebarItemId)
        assertTrue(state.collections[0].children.none { it.id == "r1" })
        assertTrue(state.collections[1].children.none { it.id == "r5" })
        // Remaining requests untouched
        assertTrue(state.collections[0].children.any { it.id == "r2" })
        assertTrue(state.collections[1].children.any { it.id == "r4" })
    }
}
