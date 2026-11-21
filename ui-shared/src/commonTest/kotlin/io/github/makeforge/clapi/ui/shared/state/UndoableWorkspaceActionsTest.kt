package io.github.makeforge.clapi.ui.shared.state

import androidx.compose.runtime.mutableStateListOf
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.ui.shared.components.sidebar.captureNodePlacements
import io.github.makeforge.clapi.ui.shared.components.sidebar.deleteFolderFromCollections
import io.github.makeforge.clapi.ui.shared.components.sidebar.extractNodeById
import io.github.makeforge.clapi.ui.shared.components.sidebar.findNodePlacement
import io.github.makeforge.clapi.ui.shared.components.sidebar.moveRequestInCollections
import io.github.makeforge.clapi.ui.shared.components.sidebar.moveRequestsToCollection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UndoableWorkspaceActionsTest {

    private fun sampleState(): AppState {
        val state = AppState(openDefaultTab = false)
        state.collections.clear()
        val req1 = CollectionNode(id = "req-1", name = "Get Users", isFolder = false, method = HttpMethodType.GET)
        val req2 = CollectionNode(id = "req-2", name = "Create User", isFolder = false, method = HttpMethodType.POST)
        val nestedReq = CollectionNode(id = "req-3", name = "Refresh Token", isFolder = false, method = HttpMethodType.POST)
        val subFolder = CollectionNode(
            id = "folder-auth",
            name = "Auth",
            isFolder = true,
            children = mutableStateListOf(nestedReq),
        )
        val rootCollection = CollectionNode(
            id = "col-1",
            name = "API",
            isFolder = true,
            children = mutableStateListOf(req1, req2, subFolder),
        )
        state.collections.add(rootCollection)
        return state
    }

    @Test
    fun delete_folder_with_nested_children_and_undo_restores_full_subtree() {
        val state = sampleState()
        val placement = findNodePlacement(state.collections, "folder-auth")!!
        val deleted = deleteFolderFromCollections(state.collections, "folder-auth")!!
        UndoableWorkspaceActions.showDeletedNodesSnackbar(
            state,
            listOf(DeletedNodeSnapshot(deleted, placement.parentId, placement.index)),
        )

        assertNull(findNodePlacement(state.collections, "folder-auth"))
        assertNotNull(state.snackbar.current)
        assertTrue(state.snackbar.current!!.message.contains("Auth"))

        state.snackbar.triggerAction(state.snackbar.current!!.id)

        val restoredPlacement = findNodePlacement(state.collections, "folder-auth")
        assertEquals(placement, restoredPlacement)
        assertNotNull(findNodePlacement(state.collections, "req-3"))
        assertNull(state.snackbar.current)
    }

    @Test
    fun delete_request_and_undo_restores_original_index() {
        val state = sampleState()
        val placement = findNodePlacement(state.collections, "req-1")!!
        val removed = extractNodeById(state.collections, "req-1")!!
        UndoableWorkspaceActions.showDeletedNodesSnackbar(
            state,
            listOf(DeletedNodeSnapshot(removed, placement.parentId, placement.index)),
        )

        assertEquals(listOf("req-2", "folder-auth"), state.collections.first().children.map { it.id })

        state.snackbar.triggerAction(state.snackbar.current!!.id)

        assertEquals(listOf("req-1", "req-2", "folder-auth"), state.collections.first().children.map { it.id })
    }

    @Test
    fun move_request_to_another_folder_and_undo_restores_original_parent_and_index() {
        val state = sampleState()
        val before = captureNodePlacements(state.collections, listOf("req-1"))
        assertTrue(moveRequestsToCollection(state.collections, listOf("req-1"), "folder-auth"))
        UndoableWorkspaceActions.showMovedNodesSnackbar(state, before, listOf("Get Users"))

        assertEquals("folder-auth", findNodePlacement(state.collections, "req-1")?.parentId)
        assertNotNull(state.snackbar.current)

        state.snackbar.triggerAction(state.snackbar.current!!.id)

        assertEquals(before.first(), findNodePlacement(state.collections, "req-1"))
    }

    @Test
    fun noop_move_does_not_trigger_snackbar() {
        val state = sampleState()
        val before = captureNodePlacements(state.collections, listOf("req-1"))
        // No actual move performed
        UndoableWorkspaceActions.showMovedNodesSnackbar(state, before, listOf("Get Users"))

        assertNull(state.snackbar.current)
    }

    @Test
    fun move_request_down_and_undo_restores_order() {
        val state = sampleState()
        val before = captureNodePlacements(state.collections, listOf("req-1"))
        assertTrue(moveRequestInCollections(state.collections, "req-1", 1))
        UndoableWorkspaceActions.showMovedNodesSnackbar(state, before, listOf("Get Users"))

        assertEquals(1, findNodePlacement(state.collections, "req-1")?.index)
        state.snackbar.triggerAction(state.snackbar.current!!.id)
        assertEquals(0, findNodePlacement(state.collections, "req-1")?.index)
    }

    @Test
    fun rename_and_undo_restores_previous_name_while_noop_is_suppressed() {
        val state = sampleState()
        // No-op rename
        UndoableWorkspaceActions.showRenamedItemSnackbar(state, "Get Users", "Get Users") { true }
        assertNull(state.snackbar.current)

        // Real rename
        state.renameRequestEverywhere("req-1", "List All Users")
        UndoableWorkspaceActions.showRenamedItemSnackbar(state, "Get Users", "List All Users") {
            state.renameRequestEverywhere("req-1", "Get Users")
            true
        }
        assertNotNull(state.snackbar.current)
        assertTrue(state.snackbar.current!!.message.contains("Get Users"))
        assertTrue(state.snackbar.current!!.message.contains("List All Users"))

        state.snackbar.triggerAction(state.snackbar.current!!.id)
        assertEquals("Get Users", UndoableWorkspaceActions.findNodeNameById(state.collections, "req-1"))
    }

    @Test
    fun delete_environment_and_undo_restores_env_and_selection() {
        val state = AppState(openDefaultTab = false)
        state.environments.clear()
        state.environments.add(EnvState(name = "Dev"))
        state.environments.add(EnvState(name = "Prod"))
        state.selectedEnvIndex = 1

        val removed = state.environments.removeAt(1)
        state.selectedEnvIndex = 0
        UndoableWorkspaceActions.showDeletedEnvironmentSnackbar(
            state,
            DeletedEnvironmentSnapshot(removed, index = 1, previousSelectedEnvIndex = 1),
        )

        assertEquals(listOf("Dev"), state.environments.map { it.name })
        state.snackbar.triggerAction(state.snackbar.current!!.id)
        assertEquals(listOf("Dev", "Prod"), state.environments.map { it.name })
        assertEquals(1, state.selectedEnvIndex)
    }
}
