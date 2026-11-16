package io.github.makeforge.clapi.ui.shared.persistence

import androidx.compose.runtime.mutableStateListOf
import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.core.model.KeyValueEntry
import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.core.storage.opencollection.ExportFormat
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MultiCollectionImportExportTest {

    @Test
    fun exportCollectionsToDirectory_writes_one_file_per_selected_collection() {
        val col1 = CollectionNode(
            id = "c1",
            name = "Users API",
            kind = RequestKind.COLLECTION,
            isFolder = true,
            summary = "User management",
            version = "1.0.0",
            headerEntries = listOf(KeyValueEntry("X-Api-Key", "k1", enabled = true)),
            children = mutableStateListOf(
                CollectionNode("r1", "Get Users", method = HttpMethodType.GET, url = "https://example.com/users")
            )
        )
        val col2 = CollectionNode(
            id = "c2",
            name = "Orders API",
            kind = RequestKind.COLLECTION,
            isFolder = true,
            summary = "Order processing",
            version = "2.0.0",
            children = mutableStateListOf(
                CollectionNode("r2", "Place Order", method = HttpMethodType.POST, url = "https://example.com/orders")
            )
        )

        val json1 = ImportExportRepository.exportCollectionToString(col1, ExportFormat.JSON)
        val json2 = ImportExportRepository.exportCollectionToString(col2, ExportFormat.JSON)

        assertTrue(json1.contains("\"name\": \"Users API\""))
        assertTrue(json1.contains("\"summary\": \"User management\""))
        assertTrue(json1.contains("\"version\": \"1.0.0\""))
        assertTrue(json1.contains("Get Users"))

        assertTrue(json2.contains("\"name\": \"Orders API\""))
        assertTrue(json2.contains("\"summary\": \"Order processing\""))
        assertTrue(json2.contains("Place Order"))
    }

    @Test
    fun importCollectionFromString_creates_distinct_collection_with_disambiguation() {
        val state = AppState(withDemoData = false, openDefaultTab = false)
        state.createNewCollection("Payments API")

        val colToImport = CollectionNode(
            id = "incoming",
            name = "Payments API",
            kind = RequestKind.COLLECTION,
            isFolder = true,
            summary = "Incoming billing service",
            version = "3.0.0",
            children = mutableStateListOf(
                CollectionNode("r1", "Charge Card", method = HttpMethodType.POST, url = "https://payments.example.com/charge")
            )
        )
        val incomingJson = ImportExportRepository.exportCollectionToString(colToImport, ExportFormat.JSON)

        val importedName = ImportExportRepository.importCollectionFromString(state, incomingJson)

        assertEquals("Payments API (1)", importedName)
        assertEquals(2, state.collections.size)

        val importedCol = state.collections.firstOrNull { it.name == "Payments API (1)" }
        assertNotNull(importedCol)
        assertTrue(importedCol.isCollection)
        assertEquals("Incoming billing service", importedCol.summary)
        assertEquals("3.0.0", importedCol.version)
        assertEquals(1, importedCol.children.size)
        assertEquals("Charge Card", importedCol.children.first().name)
    }

    @Test
    fun sanitizeFileName_removes_invalid_characters() {
        val sanitized = ImportExportRepository.sanitizeFileName("My API: / Special * Name?")
        assertEquals("My API_ _ Special _ Name_", sanitized)
    }
}
