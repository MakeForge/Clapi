package io.github.makeforge.clapi.ui.shared.persistence

import androidx.compose.runtime.mutableStateListOf
import io.github.makeforge.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.makeforge.clapi.core.storage.opencollection.CollectionStorageDriver
import io.github.makeforge.clapi.core.storage.opencollection.CollectionValidationException
import io.github.makeforge.clapi.core.storage.opencollection.DefaultCanonicalCollectionRepository
import io.github.makeforge.clapi.core.storage.opencollection.StorageLocation
import io.github.makeforge.clapi.core.storage.opencollection.ValidationIssue
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PersistenceErrorReportingTest {

    private class RejectingStorageDriver : CollectionStorageDriver {
        var lastWrittenLocation: StorageLocation? = null

        override suspend fun read(location: StorageLocation): OpenCollectionDocument =
            error("not used")

        override suspend fun write(location: StorageLocation, document: OpenCollectionDocument) {
            lastWrittenLocation = location
            throw CollectionValidationException(
                "OpenCollection schema validation failed: \$.items[0].type: not allowed",
                listOf(ValidationIssue("\$.items[0].type", "not allowed")),
            )
        }

        override suspend fun delete(location: StorageLocation) = Unit
    }

    @Test
    fun saveReportsValidationFailureAndReturnsFalse() {
        val state = AppState(openDefaultTab = false)
        state.collections.add(
            CollectionNode(id = "col-1", name = "Broken", isFolder = true, children = mutableStateListOf())
        )
        val driver = RejectingStorageDriver()
        val previous = WorkspaceRepository.canonicalRepository
        WorkspaceRepository.canonicalRepository = DefaultCanonicalCollectionRepository(
            storageDriver = driver
        )
        try {
            val saved = WorkspaceRepository.save(state)

            assertFalse(saved)
            assertEquals(StorageLocation.MemoryKey("workspace.json"), driver.lastWrittenLocation)
            assertTrue(state.showErrorDialog)
            assertEquals("Save error", state.errorDialogTitle)
            assertTrue(state.errorDialogMessage.contains("failed validation"))
            assertTrue(state.errorDialogMessage.contains("\$.items[0].type"))
        } finally {
            WorkspaceRepository.canonicalRepository = previous
        }
    }

    @Test
    fun importRejectsInvalidOpenCollectionWithUserFacingMessage() {
        val state = AppState(openDefaultTab = false)
        val invalid = """{"opencollection":"2.0.0","info":{"name":"Bad"},"items":[]}"""

        val error = assertFailsWith<ImportExportException> {
            ImportExportRepository.importCollectionFromString(state, invalid)
        }

        assertTrue(error.message!!.startsWith("The OpenCollection document is invalid."))
        assertTrue(error.message!!.contains("opencollection"))
        assertTrue(state.collections.isEmpty())
    }
}
