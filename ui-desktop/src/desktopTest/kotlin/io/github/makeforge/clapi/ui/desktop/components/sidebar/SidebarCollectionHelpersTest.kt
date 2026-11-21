// Modified by MakeForge (2026)
package io.github.makeforge.clapi.ui.shared.components.sidebar

import androidx.compose.runtime.mutableStateListOf
import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.model.BodyType
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.core.model.KeyValueEntry
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for sidebar collection-manipulation helper functions.
 * These functions are package-private in Sidebar.kt so we test them via a test
 * file in the same package.
 */
class SidebarCollectionHelpersTest {

    private fun sampleCollections(): MutableList<CollectionNode> = mutableStateListOf(
        CollectionNode("c1", "Users API", isFolder = true, children = mutableStateListOf(
            CollectionNode("r1", "Get all users", method = HttpMethodType.GET, url = "/users"),
            CollectionNode("r2", "Create user", method = HttpMethodType.POST, url = "/users"),
            CollectionNode("r3", "Update user", method = HttpMethodType.PUT, url = "/users/1"),
        )),
        CollectionNode("c2", "Auth", isFolder = true, children = mutableStateListOf(
            CollectionNode("r4", "Login", method = HttpMethodType.POST, url = "/auth/login"),
        )),
    )

    // ── deleteRequestFromCollections ──

    @Test
    fun deleteRequest_removes_request_from_parent() {
        val collections = sampleCollections()
        val result = deleteRequestFromCollections(collections, "r2")
        assertTrue(result)
        assertEquals(2, collections[0].children.size)
        assertNull(collections[0].children.firstOrNull { it.id == "r2" })
    }

    @Test
    fun deleteRequest_returns_false_for_nonexistent_id() {
        val collections = sampleCollections()
        assertFalse(deleteRequestFromCollections(collections, "nonexistent"))
    }

    // ── renameRequestInCollections ──

    @Test
    fun renameRequest_changes_name() {
        val collections = sampleCollections()
        renameRequestInCollections(collections, "r1", "List Users v2")
        assertEquals("List Users v2", collections[0].children.first().name)
    }

    @Test
    fun renameRequest_no_op_for_nonexistent_id() {
        val collections = sampleCollections()
        renameRequestInCollections(collections, "nonexistent", "New Name")
        // Original names unchanged
        assertEquals("Get all users", collections[0].children.first().name)
    }

    // ── moveRequestInCollections ──

    @Test
    fun moveRequest_down_swaps_with_next_sibling() {
        val collections = sampleCollections()
        val result = moveRequestInCollections(collections, "r1", 1)
        assertTrue(result)
        assertEquals("r2", collections[0].children[0].id)
        assertEquals("r1", collections[0].children[1].id)
    }

    @Test
    fun moveRequest_up_swaps_with_previous_sibling() {
        val collections = sampleCollections()
        val result = moveRequestInCollections(collections, "r2", -1)
        assertTrue(result)
        assertEquals("r2", collections[0].children[0].id)
        assertEquals("r1", collections[0].children[1].id)
    }

    @Test
    fun moveRequest_at_top_cannot_move_up() {
        val collections = sampleCollections()
        val result = moveRequestInCollections(collections, "r1", -1)
        assertFalse(result)
        assertEquals("r1", collections[0].children[0].id)
    }

    @Test
    fun moveRequest_at_bottom_cannot_move_down() {
        val collections = sampleCollections()
        val result = moveRequestInCollections(collections, "r3", 1)
        assertFalse(result)
        assertEquals("r3", collections[0].children[2].id)
    }

    @Test
    fun moveRequest_beforeRequest_reorders_correctly() {
        val collections = sampleCollections()
        val result = moveRequestBeforeRequest(collections, "r3", "r1")
        assertTrue(result)
        assertEquals("r3", collections[0].children[0].id)
        assertEquals("r1", collections[0].children[1].id)
    }

    @Test
    fun moveRequest_afterRequest_places_source_after_target() {
        val collections = sampleCollections()
        // Move r1 to after r3 (last in collection)
        val result = moveRequestAfterRequest(collections, "r1", "r3")
        assertTrue(result)
        assertEquals("r2", collections[0].children[0].id)
        assertEquals("r3", collections[0].children[1].id)
        assertEquals("r1", collections[0].children[2].id)
    }

    @Test
    fun moveRequest_afterRequest_moves_down_by_one() {
        val collections = sampleCollections()
        // Move r1 after r2 — should end up in position 1
        val result = moveRequestAfterRequest(collections, "r1", "r2")
        assertTrue(result)
        assertEquals(3, collections[0].children.size)
        assertEquals("r2", collections[0].children[0].id)
        assertEquals("r1", collections[0].children[1].id)
        assertEquals("r3", collections[0].children[2].id)
    }

    @Test
    fun moveRequest_afterRequest_returns_false_for_nonexistent_source() {
        val collections = sampleCollections()
        assertFalse(moveRequestAfterRequest(collections, "nonexistent", "r1"))
    }

    @Test
    fun moveRequest_toDifferentCollection_moves_node() {
        val collections = sampleCollections()
        val result = moveRequestToCollection(collections, "r1", "c2")
        assertTrue(result)
        assertEquals(2, collections[0].children.size)
        assertEquals(2, collections[1].children.size)
        assertEquals("r1", collections[1].children.last().id)
    }

    @Test
    fun addSubfolderInCollections_creates_nested_folder_with_unique_name() {
        val collections = sampleCollections()
        val parentId = collections.first().id

        val first = addSubfolderInCollections(collections, parentId, "Folder A")
        val second = addSubfolderInCollections(collections, parentId, "Folder A")

        assertNotNull(first)
        assertNotNull(second)
        assertEquals("Folder A", first.name)
        assertEquals("Folder A (1)", second.name)
        assertTrue(collections.first().children.any { it.id == first.id && it.isFolder })
        assertTrue(collections.first().children.any { it.id == second.id && it.isFolder })
    }

    @Test
    fun renameFolderInCollections_renames_nested_folder() {
        val collections = sampleCollections()
        val nested = addSubfolderInCollections(collections, "c1", "Nested") ?: error("Expected folder")

        val renamed = renameFolderInCollections(collections, nested.id, "Nested Renamed")

        assertTrue(renamed)
        assertTrue(collections.first().children.any { it.id == nested.id && it.name == "Nested Renamed" })
    }

    @Test
    fun deleteFolderFromCollections_removes_folder_and_children() {
        val collections = sampleCollections()
        val nested = addSubfolderInCollections(collections, "c1", "To Delete") ?: error("Expected folder")
        nested.children.add(CollectionNode("r9", "Nested Request", method = HttpMethodType.GET, url = "/nested"))

        val deleted = deleteFolderFromCollections(collections, nested.id)

        assertNotNull(deleted)
        assertEquals(nested.id, deleted.id)
        assertFalse(collections.first().children.any { it.id == nested.id })
    }

    @Test
    fun collectAllRequestIds_includes_folder_itself_and_nested_items() {
        val collections = sampleCollections()
        val nested = addSubfolderInCollections(collections, "c1", "Folder 1") ?: error("Expected folder")
        val subnested = addSubfolderInCollections(collections, nested.id, "Folder 2") ?: error("Expected folder")
        subnested.children.add(CollectionNode("r9", "Nested Request", method = HttpMethodType.GET, url = "/nested"))

        val ids = collectAllRequestIds(nested)
        assertTrue(ids.contains(nested.id), "Should include the target folder id")
        assertTrue(ids.contains(subnested.id), "Should include subfolder id")
        assertTrue(ids.contains("r9"), "Should include nested request id")
    }

    @Test
    fun countRequestsInFolder_counts_deep_nested_requests() {
        val collections = sampleCollections()
        val root = collections.first()
        val nested = addSubfolderInCollections(collections, root.id, "Nested") ?: error("Expected folder")
        nested.children.add(CollectionNode("r9", "Nested Request", method = HttpMethodType.GET, url = "/nested"))

        val count = countRequestsInFolder(root)

        assertEquals(4, count)
    }

    // ── duplicateRequestInCollections ──

    @Test
    fun duplicateRequest_creates_copy_next_to_original() {
        val collections = sampleCollections()
        val result = duplicateRequestInCollections(collections, "r1")
        assertNotNull(result)
        assertTrue(result.contains("Copy"))
        assertEquals(4, collections[0].children.size)
        assertEquals(result, collections[0].children[1].name)
    }

    @Test
    fun duplicateRequest_returns_null_for_nonexistent_id() {
        val collections = sampleCollections()
        assertNull(duplicateRequestInCollections(collections, "nonexistent"))
    }

    @Test
    fun duplicateRequest_copies_auth_fields() {
        val collections = mutableStateListOf(
            CollectionNode(
                "c1", "API", isFolder = true, children = mutableStateListOf(
                    CollectionNode(
                        "r1", "Login", method = HttpMethodType.POST, url = "/auth",
                        authType = AuthType.BEARER,
                        authToken = "tok123",
                        authUsername = "user",
                        authPassword = "pass",
                        authApiKey = "X-Key",
                        authApiValue = "myval",
                        authApiPlacement = "query",
                        queryEntries = listOf(
                            KeyValueEntry("x", "1"),
                            KeyValueEntry("x", "2", enabled = false, secret = true),
                        ),
                        headerEntries = listOf(KeyValueEntry("X-Secret", "value", secret = true)),
                    )
                )
            )
        )
        duplicateRequestInCollections(collections, "r1")
        val dup = collections[0].children[1]
        assertEquals(AuthType.BEARER, dup.authType)
        assertEquals("tok123", dup.authToken)
        assertEquals("user", dup.authUsername)
        assertEquals("pass", dup.authPassword)
        assertEquals("X-Key", dup.authApiKey)
        assertEquals("myval", dup.authApiValue)
        assertEquals("query", dup.authApiPlacement)
        assertEquals(listOf("1", "2"), dup.queryEntries?.map { it.value })
        assertEquals(false, dup.queryEntries?.last()?.enabled)
        assertEquals(true, dup.queryEntries?.last()?.secret)
        assertEquals(true, dup.headerEntries?.single()?.secret)
    }

    @Test
    fun duplicateRequest_copies_body_fields() {
        val collections = mutableStateListOf(
            CollectionNode(
                "c1", "API", isFolder = true, children = mutableStateListOf(
                    CollectionNode(
                        "r1", "Create", method = HttpMethodType.POST, url = "/items",
                        bodyType = BodyType.JSON,
                        bodyContent = """{"key":"value"}""",
                        bodyContents = mapOf("JSON" to """{"key":"value"}""", "RAW" to "raw body"),
                    )
                )
            )
        )
        duplicateRequestInCollections(collections, "r1")
        val dup = collections[0].children[1]
        assertEquals(BodyType.JSON, dup.bodyType)
        assertEquals("""{"key":"value"}""", dup.bodyContent)
        assertEquals("""{"key":"value"}""", dup.bodyContents["JSON"])
        assertEquals("raw body", dup.bodyContents["RAW"])
    }

    @Test
    fun duplicateRequest_copies_user_headers() {
        val collections = mutableStateListOf(
            CollectionNode(
                "c1", "API", isFolder = true, children = mutableStateListOf(
                    CollectionNode(
                        "r1", "Get", method = HttpMethodType.GET, url = "/items",
                        userHeaders = listOf("Content-Type" to "application/json", "Accept" to "*/*"),
                    )
                )
            )
        )
        duplicateRequestInCollections(collections, "r1")
        val dup = collections[0].children[1]
        assertEquals(2, dup.userHeaders.size)
        assertEquals("Content-Type" to "application/json", dup.userHeaders[0])
        assertEquals("Accept" to "*/*", dup.userHeaders[1])
    }

    @Test
    fun duplicateRequest_copies_scripts() {
        // Bug 1 (duplicate request): preRequestScript and testScript must be copied.
        val collections = mutableStateListOf(
            CollectionNode(
                "c1", "API", isFolder = true, children = mutableStateListOf(
                    CollectionNode(
                        "r1", "Scripted", method = HttpMethodType.GET, url = "/items",
                        preRequestScript = "pm.variables.set(\"ts\", Date.now());",
                        testScript = "pm.test(\"status\", () => pm.response.to.have.status(200));",
                    )
                )
            )
        )
        duplicateRequestInCollections(collections, "r1")
        val dup = collections[0].children[1]
        assertEquals(
            "pm.variables.set(\"ts\", Date.now());",
            dup.preRequestScript,
            "preRequestScript must be copied to the duplicate",
        )
        assertEquals(
            "pm.test(\"status\", () => pm.response.to.have.status(200));",
            dup.testScript,
            "testScript must be copied to the duplicate",
        )
    }

    @Test
    fun duplicateRequest_gets_unique_id() {
        // Each duplicate must have its own UUID — sharing an id would corrupt state.
        val collections = mutableStateListOf(
            CollectionNode(
                "c1", "API", isFolder = true, children = mutableStateListOf(
                    CollectionNode("r1", "Get", method = HttpMethodType.GET, url = "/items"),
                )
            )
        )
        duplicateRequestInCollections(collections, "r1")
        val original = collections[0].children[0]
        val dup      = collections[0].children[1]
        assertTrue(dup.id.isNotBlank(), "duplicate id must not be blank")
        assertTrue(dup.id != original.id, "duplicate must have a different id from the original")
    }

    // ── filterCollectionNode ──

    @Test
    fun filterCollectionNode_returns_matching_children() {
        val collection = sampleCollections()[0]
        val filtered = filterCollectionNode(collection, "update")
        assertNotNull(filtered)
        assertEquals(1, filtered.children.size)
        assertEquals("Update user", filtered.children[0].name)
    }

    @Test
    fun filterCollectionNode_returns_null_when_no_match() {
        val collection = sampleCollections()[0]
        val filtered = filterCollectionNode(collection, "zzz-no-match")
        assertNull(filtered)
    }

    @Test
    fun filterCollectionNode_returns_all_for_empty_query() {
        val collection = sampleCollections()[0]
        val filtered = filterCollectionNode(collection, "")
        assertNotNull(filtered)
        assertEquals(3, filtered.children.size)
    }

    // ── moveCollectionBeforeCollection (Bug 3 — collection drag-to-reorder) ──

    private fun threeCollections(): MutableList<CollectionNode> = mutableStateListOf(
        CollectionNode("c1", "Alpha",  isFolder = true, children = mutableStateListOf()),
        CollectionNode("c2", "Beta",   isFolder = true, children = mutableStateListOf()),
        CollectionNode("c3", "Gamma",  isFolder = true, children = mutableStateListOf()),
    )

    @Test
    fun moveCollectionBefore_moves_last_before_first() {
        // Bug 3 BEFORE FIX: function did not exist → compile error.
        // AFTER FIX: the function exists and produces the correct order.
        val collections = threeCollections()
        val moved = moveCollectionBeforeCollection(collections, "c3", "c1")
        assertTrue(moved)
        assertEquals(listOf("c3", "c1", "c2"), collections.map { it.id })
    }

    @Test
    fun moveCollectionBefore_moves_first_before_last() {
        val collections = threeCollections()
        val moved = moveCollectionBeforeCollection(collections, "c1", "c3")
        assertTrue(moved)
        assertEquals(listOf("c2", "c1", "c3"), collections.map { it.id })
    }

    @Test
    fun moveCollectionBefore_returns_false_for_same_source_and_target() {
        val collections = threeCollections()
        assertFalse(moveCollectionBeforeCollection(collections, "c1", "c1"))
        assertEquals(listOf("c1", "c2", "c3"), collections.map { it.id })
    }

    @Test
    fun moveCollectionBefore_returns_false_for_nonexistent_id() {
        val collections = threeCollections()
        assertFalse(moveCollectionBeforeCollection(collections, "nonexistent", "c1"))
    }

    @Test
    fun moveCollectionAfter_moves_first_after_last() {
        val collections = threeCollections()
        val moved = moveCollectionAfterCollection(collections, "c1", "c3")
        assertTrue(moved)
        assertEquals(listOf("c2", "c3", "c1"), collections.map { it.id })
    }

    @Test
    fun moveCollectionAfter_moves_last_after_first() {
        val collections = threeCollections()
        val moved = moveCollectionAfterCollection(collections, "c3", "c1")
        assertTrue(moved)
        assertEquals(listOf("c1", "c3", "c2"), collections.map { it.id })
    }

    @Test
    fun moveCollectionAfter_returns_false_for_same_source_and_target() {
        val collections = threeCollections()
        assertFalse(moveCollectionAfterCollection(collections, "c2", "c2"))
    }

    @Test
    fun folder_terminology_unique_naming_uses_folder_label() {
        val names = setOf("New Folder")
        val unique = io.github.makeforge.clapi.ui.shared.persistence.ImportExportNaming.generateUniqueCollectionName("New Folder", names)
        assertEquals("New Folder (1)", unique)
    }

    @Test
    fun i18n_folder_terminology_resolves_folders_in_en_and_fallback() {
        val i18n = io.github.makeforge.clapi.ui.shared.i18n.I18nProvider()
        assertEquals("Folders", i18n.get("collections"))
        assertEquals("Folders", i18n.get("folders"))
        assertEquals("New Folder", i18n.get("new_collection"))
        assertEquals("New Folder", i18n.get("new_folder"))
        assertEquals("Export Folder", i18n.get("export_collection"))
        assertEquals("Export Folder", i18n.get("export_folder"))
        assertEquals("Import Folder", i18n.get("import_collection"))
        assertEquals("Import Folder", i18n.get("import_folder"))
        assertEquals("Go to Folder", i18n.get("go_to_collection"))
        assertEquals("Go to Folder", i18n.get("go_to_folder"))
        assertEquals("Folder actions", i18n.get("collection_actions"))
        assertEquals("Folder actions", i18n.get("folder_actions"))
        assertEquals("Duplicate Folder", i18n.get("duplicate_collection"))
        assertEquals("Duplicate Folder", i18n.get("duplicate_folder"))
    }

    // ── Multi-item drag-to-reorder tests ──

    @Test
    fun moveRequestsBeforeRequest_multipleRequests_reordersInOrder() {
        val collections = sampleCollections()
        // c1 has r1, r2, r3. Move r2 and r3 before r1
        val moved = moveRequestsBeforeRequest(collections, listOf("r2", "r3"), "r1")
        assertTrue(moved)
        assertEquals(listOf("r2", "r3", "r1"), collections[0].children.map { it.id })
    }

    @Test
    fun moveRequestsAfterRequest_multipleRequests_reordersInOrder() {
        val collections = sampleCollections()
        // c1 has r1, r2, r3. Move r1 and r2 after r3
        val moved = moveRequestsAfterRequest(collections, listOf("r1", "r2"), "r3")
        assertTrue(moved)
        assertEquals(listOf("r3", "r1", "r2"), collections[0].children.map { it.id })
    }

    @Test
    fun moveRequestsToCollection_multipleRequests_movesAll() {
        val collections = sampleCollections()
        // Move r1 and r3 from c1 into c2
        val moved = moveRequestsToCollection(collections, listOf("r1", "r3"), "c2")
        assertTrue(moved)
        assertEquals(listOf("r2"), collections[0].children.map { it.id })
        assertEquals(listOf("r4", "r1", "r3"), collections[1].children.map { it.id })
    }

    @Test
    fun moveRequests_returnsFalse_whenTargetIsInGroupOrEmpty() {
        val collections = sampleCollections()
        assertFalse(moveRequestsBeforeRequest(collections, listOf("r1", "r2"), "r1"))
        assertFalse(moveRequestsAfterRequest(collections, listOf("r1", "r2"), "r2"))
        assertFalse(moveRequestsBeforeRequest(collections, emptyList(), "r1"))
        assertFalse(moveRequestsToCollection(collections, emptyList(), "c2"))
    }

    @Test
    fun moveFoldersBeforeFolder_multipleFolders_reordersInOrder() {
        val collections = threeCollections()
        // c1, c2, c3. Move c2 and c3 before c1
        val moved = moveFoldersBeforeFolder(collections, listOf("c2", "c3"), "c1")
        assertTrue(moved)
        assertEquals(listOf("c2", "c3", "c1"), collections.map { it.id })
    }

    @Test
    fun moveFoldersAfterFolder_multipleFolders_reordersInOrder() {
        val collections = threeCollections()
        // c1, c2, c3. Move c1 and c2 after c3
        val moved = moveFoldersAfterFolder(collections, listOf("c1", "c2"), "c3")
        assertTrue(moved)
        assertEquals(listOf("c3", "c1", "c2"), collections.map { it.id })
    }

    @Test
    fun moveFolders_returnsFalse_whenTargetIsInGroupOrDescendant() {
        val collections = sampleCollections()
        val nested = addSubfolderInCollections(collections, "c1", "Nested") ?: error("Folder")
        // Target is in group
        assertFalse(moveFoldersBeforeFolder(collections, listOf("c1"), "c1"))
        // Target is descendant of group
        assertFalse(moveFoldersBeforeFolder(collections, listOf("c1"), nested.id))
    }

    @Test
    fun duplicateFolderInCollections_duplicatesRootFolder() {
        val collections = sampleCollections()
        val duplicatedName = duplicateFolderInCollections(collections, "c1")
        assertNotNull(duplicatedName)
        assertEquals(3, collections.size)
        assertEquals("c1", collections[0].id)
        assertEquals(duplicatedName, collections[1].name)
        assertEquals(3, collections[1].children.size)
    }

    @Test
    fun duplicateFolderInCollections_duplicatesNestedFolder() {
        val collections = sampleCollections()
        val nested = addSubfolderInCollections(collections, "c1", "Sub") ?: error("Folder")
        val duplicatedName = duplicateFolderInCollections(collections, nested.id)
        assertNotNull(duplicatedName)
        val c1Children = collections[0].children
        assertEquals(5, c1Children.size) // r1, r2, r3, Sub, Sub (1)
        assertEquals(duplicatedName, c1Children.last().name)
    }

    @Test
    fun getVisibleSidebarNodes_respectsExpandedStateAndFilter() {
        val collections = sampleCollections()
        val expandedState = mapOf("c1" to true, "c2" to false)

        val visibleNodes = getVisibleSidebarNodes(collections, expandedState)
        val visibleIds = visibleNodes.map { it.id }

        // c1 is expanded so c1, r1, r2, r3 are visible. c2 is collapsed so only c2 is visible.
        assertTrue(visibleIds.contains("c1"))
        assertTrue(visibleIds.contains("r1"))
        assertTrue(visibleIds.contains("r2"))
        assertTrue(visibleIds.contains("r3"))
        assertTrue(visibleIds.contains("c2"))
        assertFalse(visibleIds.contains("r4")) // in collapsed c2

        val folderIds = getVisibleFolderIds(collections, expandedState)
        assertEquals(listOf("c1", "c2"), folderIds)

        val requestIds = getVisibleRequestIds(collections, expandedState)
        assertEquals(listOf("r1", "r2", "r3"), requestIds)
    }

    @Test
    fun selectSidebar_ctrlClick_togglesSelection() {
        val state = io.github.makeforge.clapi.ui.shared.state.AppState()
        state.selectSidebarRequest("r1", isCtrlPressed = true)
        assertEquals(listOf("r1"), state.selectedRequestIds)

        state.selectSidebarRequest("r2", isCtrlPressed = true)
        assertEquals(listOf("r1", "r2"), state.selectedRequestIds)

        // Ctrl-clicking already selected item removes it
        state.selectSidebarRequest("r1", isCtrlPressed = true)
        assertEquals(listOf("r2"), state.selectedRequestIds)
    }

    @Test
    fun selectSidebar_cannotSelectFoldersAndRequestsAtSameTime() {
        val state = io.github.makeforge.clapi.ui.shared.state.AppState()
        state.selectSidebarRequest("r1", isCtrlPressed = true)
        state.selectSidebarRequest("r2", isCtrlPressed = true)
        assertEquals(listOf("r1", "r2"), state.selectedRequestIds)

        // Selecting a folder clears request selection
        state.selectSidebarFolder("c1", isCtrlPressed = true)
        assertTrue(state.selectedRequestIds.isEmpty())
        assertEquals(listOf("c1"), state.selectedFolderIds)

        // Selecting a request clears folder selection
        state.selectSidebarRequest("r3", isCtrlPressed = true)
        assertTrue(state.selectedFolderIds.isEmpty())
        assertEquals(listOf("r3"), state.selectedRequestIds)
    }

    @Test
    fun selectSidebar_shiftClick_selectsRange() {
        val state = io.github.makeforge.clapi.ui.shared.state.AppState()
        val visibleRequests = listOf("r1", "r2", "r3", "r4", "r5")

        // First click without modifier sets anchor
        state.selectSidebarRequest("r2", isCtrlPressed = false, isShiftPressed = false, visibleRequests = visibleRequests)
        assertEquals(listOf("r2"), state.selectedRequestIds)

        // Shift click on r4 selects r2, r3, r4
        state.selectSidebarRequest("r4", isCtrlPressed = false, isShiftPressed = true, visibleRequests = visibleRequests)
        assertEquals(listOf("r2", "r3", "r4"), state.selectedRequestIds)

        // Shift click with no previous selection selects only the clicked item
        state.clearSidebarSelection()
        state.selectSidebarRequest("r3", isCtrlPressed = false, isShiftPressed = true, visibleRequests = visibleRequests)
        assertEquals(listOf("r3"), state.selectedRequestIds)
    }

    @Test
    fun selectSidebar_shiftClick_reverseRange() {
        val state = io.github.makeforge.clapi.ui.shared.state.AppState()
        val visibleRequests = listOf("r1", "r2", "r3", "r4", "r5")

        state.selectSidebarRequest("r4", isCtrlPressed = false, isShiftPressed = false, visibleRequests = visibleRequests)
        state.selectSidebarRequest("r1", isCtrlPressed = false, isShiftPressed = true, visibleRequests = visibleRequests)
        assertEquals(listOf("r1", "r2", "r3", "r4"), state.selectedRequestIds)
    }

    @Test
    fun selectSidebar_ctrlShiftClick_extendsExistingSelectionWithoutDuplicates() {
        val state = io.github.makeforge.clapi.ui.shared.state.AppState()
        val visibleRequests = listOf("r1", "r2", "r3", "r4", "r5")

        state.selectSidebarRequest("r1", isCtrlPressed = false, isShiftPressed = false, visibleRequests = visibleRequests)
        state.selectSidebarRequest("r3", isCtrlPressed = true, isShiftPressed = false, visibleRequests = visibleRequests)
        state.selectSidebarRequest("r5", isCtrlPressed = true, isShiftPressed = true, visibleRequests = visibleRequests)

        assertEquals(listOf("r1", "r3", "r4", "r5"), state.selectedRequestIds)
    }

    @Test
    fun pruneSidebarSelection_removesDeletedIdsAndUpdatesAnchor() {
        val state = io.github.makeforge.clapi.ui.shared.state.AppState()
        state.selectSidebarRequest("r1", isCtrlPressed = true)
        state.selectSidebarRequest("r2", isCtrlPressed = true)
        assertEquals("r2", state.lastSelectedSidebarItemId)

        state.pruneSidebarSelection(listOf("r2"))
        assertEquals(listOf("r1"), state.selectedRequestIds)
        assertEquals("r1", state.lastSelectedSidebarItemId)

        state.pruneSidebarSelection(listOf("r1"))
        assertTrue(state.selectedRequestIds.isEmpty())
        assertNull(state.lastSelectedSidebarItemId)
    }

    @Test
    fun pruneRedundantDescendantFolders_dropsChildWhenParentSelected() {
        val collections = sampleCollections()
        val sub = addSubfolderInCollections(collections, "c1", "Sub") ?: error("Expected subfolder")
        val deep = addSubfolderInCollections(collections, sub.id, "Deep") ?: error("Expected deep subfolder")

        val pruned = pruneRedundantDescendantFolders(collections, listOf("c1", sub.id, deep.id, "c2", "c1"))
        assertEquals(listOf("c1", "c2"), pruned)
    }

    @Test
    fun moveFoldersBeforeFolder_withParentAndChildSelected_movesParentWithoutDataLoss() {
        val collections = sampleCollections()
        val sub = addSubfolderInCollections(collections, "c1", "Sub") ?: error("Expected subfolder")

        val moved = moveFoldersAfterFolder(collections, listOf("c1", sub.id), "c2")
        assertTrue(moved)
        assertEquals(listOf("c2", "c1"), collections.map { it.id })
        assertTrue(collections[1].children.any { it.id == sub.id })
    }

    @Test
    fun moveRequestsBeforeRequest_withStaleIdOrMissingTarget_doesNotLoseExistingRequests() {
        val collections = sampleCollections()

        // Stale ID in list -> must return false and keep r1 intact
        assertFalse(moveRequestsBeforeRequest(collections, listOf("r1", "nonexistent"), "r3"))
        assertEquals(listOf("r1", "r2", "r3"), collections[0].children.map { it.id })

        // Missing target -> must return false and keep r1, r2 intact
        assertFalse(moveRequestsBeforeRequest(collections, listOf("r1", "r2"), "nonexistent"))
        assertEquals(listOf("r1", "r2", "r3"), collections[0].children.map { it.id })

        // Same for moveRequestsAfterRequest and moveRequestsToCollection
        assertFalse(moveRequestsAfterRequest(collections, listOf("r1", "nonexistent"), "r3"))
        assertEquals(listOf("r1", "r2", "r3"), collections[0].children.map { it.id })
        assertFalse(moveRequestsToCollection(collections, listOf("r1", "nonexistent"), "c2"))
        assertEquals(listOf("r1", "r2", "r3"), collections[0].children.map { it.id })
    }
}
