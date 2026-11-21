package io.github.makeforge.clapi.ui.shared.persistence

import androidx.compose.runtime.mutableStateListOf
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.core.model.KeyValueEntry
import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.core.model.opencollection.CollectionInfo
import io.github.makeforge.clapi.core.model.opencollection.FolderInfo
import io.github.makeforge.clapi.core.model.opencollection.FolderItem
import io.github.makeforge.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.makeforge.clapi.core.storage.opencollection.DefaultCanonicalCollectionRepository
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceMigrationTest {

    @Test
    fun legacy_single_collection_document_is_wrapped_into_primary_collection() {
        val repo = DefaultCanonicalCollectionRepository()
        // Legacy document with 2 folders at the root (no isCollection metadata)
        val legacyDoc = OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(name = "My Legacy API", summary = "Legacy collection summary", version = "0.9.0"),
            items = listOf(
                FolderItem(
                    info = FolderInfo(name = "Auth", type = "folder"),
                    items = emptyList(),
                ),
                FolderItem(
                    info = FolderInfo(name = "Users", type = "folder"),
                    items = emptyList(),
                ),
            ),
        )
        runBlocking { repo.saveGlobalCollection(legacyDoc) }

        WorkspaceRepository.canonicalRepository = repo
        val state = AppState(openDefaultTab = false)
        WorkspaceRepository.load(state)

        // Loaded into 1 primary collection wrapping the 2 legacy folders
        assertEquals(1, state.collections.size)
        val primaryCol = state.collections.first()
        assertTrue(primaryCol.isCollection)
        assertEquals("My Legacy API", primaryCol.name)
        assertEquals("Legacy collection summary", primaryCol.summary)
        assertEquals("0.9.0", primaryCol.version)
        assertEquals(2, primaryCol.children.size)
        assertEquals("Auth", primaryCol.children[0].name)
        assertEquals("Users", primaryCol.children[1].name)
    }

    @Test
    fun multiple_collections_round_trip_preserves_metadata_and_hierarchy() {
        val repo = DefaultCanonicalCollectionRepository()
        WorkspaceRepository.canonicalRepository = repo

        val state = AppState(openDefaultTab = false)
        val col1 = CollectionNode(
            id = "c1",
            name = "Users API",
            kind = RequestKind.COLLECTION,
            isFolder = true,
            summary = "User accounts",
            version = "1.2.0",
            headerEntries = listOf(KeyValueEntry("X-Env", "prod", enabled = true)),
            children = mutableStateListOf(
                CollectionNode("r1", "Get Profile", method = HttpMethodType.GET, url = "https://api.example.com/me")
            )
        )
        val col2 = CollectionNode(
            id = "c2",
            name = "Billing API",
            kind = RequestKind.COLLECTION,
            isFolder = true,
            summary = "Invoicing and charges",
            version = "2.0.0",
            children = mutableStateListOf(
                CollectionNode("r2", "List Invoices", method = HttpMethodType.GET, url = "https://api.example.com/invoices")
            )
        )
        state.collections.addAll(listOf(col1, col2))

        val saved = WorkspaceRepository.save(state)
        assertTrue(saved)

        // Load into a new AppState instance
        val reloadedState = AppState(openDefaultTab = false)
        WorkspaceRepository.load(reloadedState)

        assertEquals(2, reloadedState.collections.size)

        val reloadedCol1 = reloadedState.collections.first { it.name == "Users API" }
        assertTrue(reloadedCol1.isCollection)
        assertEquals("User accounts", reloadedCol1.summary)
        assertEquals("1.2.0", reloadedCol1.version)
        assertEquals(1, reloadedCol1.children.size)
        assertEquals("Get Profile", reloadedCol1.children.first().name)

        val reloadedCol2 = reloadedState.collections.first { it.name == "Billing API" }
        assertTrue(reloadedCol2.isCollection)
        assertEquals("Invoicing and charges", reloadedCol2.summary)
        assertEquals("2.0.0", reloadedCol2.version)
        assertEquals(1, reloadedCol2.children.size)
        assertEquals("List Invoices", reloadedCol2.children.first().name)
    }
}
