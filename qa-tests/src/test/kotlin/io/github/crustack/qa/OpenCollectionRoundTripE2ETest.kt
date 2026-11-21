package io.github.crustack.qa

import io.github.crustack.core.model.opencollection.ClapiExtensionData
import io.github.crustack.core.model.opencollection.FolderItem
import io.github.crustack.core.model.opencollection.HttpRequestItem
import io.github.crustack.core.model.opencollection.OpenCollectionDocument
import io.github.crustack.core.model.opencollection.getClapiExtensionData
import io.github.crustack.core.model.opencollection.withClapiExtensionData
import io.github.crustack.core.storage.opencollection.DefaultCanonicalCollectionRepository
import io.github.crustack.core.storage.opencollection.ExportFormat
import io.github.crustack.core.storage.opencollection.GLOBAL_COLLECTION_FILENAME
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies that an OpenCollection document survives save/load and YAML export/import cycles
 * with its structure, item order, document-level extensions, and Clapi extension data intact.
 */
class OpenCollectionRoundTripE2ETest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val fixture = """
        {
          "opencollection": "1.0.0",
          "info": {"name": "Round Trip", "version": "1.0.0"},
          "config": {
            "environments": [
              {"name": "Dev", "variables": [{"name": "baseUrl", "value": "https://dev.example.com"}]}
            ]
          },
          "items": [
            {
              "info": {"name": "Users", "type": "folder"},
              "items": [
                {
                  "info": {"name": "List Users", "type": "http"},
                  "http": {
                    "method": "GET",
                    "url": "{{baseUrl}}/users",
                    "auth": {"type": "bearer", "token": "token-value"}
                  }
                }
              ]
            },
            {
              "info": {"name": "Health", "type": "http"},
              "http": {"method": "GET", "url": "{{baseUrl}}/health"}
            }
          ],
          "extensions": {"clapi": {"authType": "bearer"}},
          "x-vendor": {"tag": "keep-me"}
        }
    """.trimIndent()

    private fun listUsersUrl(doc: OpenCollectionDocument): String {
        val folder = assertIs<FolderItem>(doc.items[0])
        return assertIs<HttpRequestItem>(folder.items[0]).http.url
    }

    private fun withListUsersUrl(doc: OpenCollectionDocument, url: String): OpenCollectionDocument {
        val folder = assertIs<FolderItem>(doc.items[0])
        val request = assertIs<HttpRequestItem>(folder.items[0])
        val editedFolder = folder.copy(items = listOf(request.copy(http = request.http.copy(url = url))))
        return doc.copy(items = listOf(editedFolder) + doc.items.drop(1))
    }

    private fun assertPreservedStructure(doc: OpenCollectionDocument) {
        assertEquals(listOf("Users", "Health"), doc.items.map { it.info.name })
        assertEquals("Round Trip", doc.info.name)
        assertEquals(
            Json.parseToJsonElement("""{"tag":"keep-me"}"""),
            doc.extensions["x-vendor"],
            "Unknown root-level vendor key must survive",
        )
        assertEquals("bearer", doc.extensions.getClapiExtensionData()?.authType)
    }

    @Test
    fun jsonRoundTripAcrossRepositoryInstancesPreservesEdits() = runBlocking {
        val dir = tempDir.root.absolutePath
        val imported = DefaultCanonicalCollectionRepository(baseDirectory = dir).importCollection(fixture)
        assertPreservedStructure(imported)

        val reloaded = DefaultCanonicalCollectionRepository(baseDirectory = dir)
            .getCollection("Round Trip")
        assertNotNull(reloaded, "Collection must be readable from disk by a fresh repository")
        assertEquals(listOf("Users", "Health"), reloaded.items.map { it.info.name })
        assertEquals("Round Trip", reloaded.info.name)
        assertEquals("{{baseUrl}}/users", listUsersUrl(reloaded))

        val edited = withListUsersUrl(reloaded, "{{baseUrl}}/v2/users")
        DefaultCanonicalCollectionRepository(baseDirectory = dir).saveCollection(edited)

        val file = File(dir, GLOBAL_COLLECTION_FILENAME)
        assertTrue(file.exists(), "Global collection file must be written to disk")

        val afterEdit = DefaultCanonicalCollectionRepository(baseDirectory = dir)
            .getCollection("Round Trip")
        assertNotNull(afterEdit)
        assertEquals(listOf("Users", "Health"), afterEdit.items.map { it.info.name })
        assertEquals("{{baseUrl}}/v2/users", listUsersUrl(afterEdit))
    }

    @Test
    fun yamlRoundTripPreservesStructureAndExtensions() = runBlocking {
        val source = DefaultCanonicalCollectionRepository().importCollection(fixture)
        val edited = withListUsersUrl(source, "{{baseUrl}}/v2/users").copy(
            extensions = source.extensions.withClapiExtensionData(ClapiExtensionData(authType = "bearer")),
        )

        val repo = DefaultCanonicalCollectionRepository()
        val yaml = repo.exportCollection(edited, ExportFormat.YAML)

        val reimported = DefaultCanonicalCollectionRepository().importCollection(yaml)
        assertPreservedStructure(reimported)
        assertEquals("{{baseUrl}}/v2/users", listUsersUrl(reimported))
        assertEquals(edited.items, reimported.items, "Items must be identical after YAML round-trip")
        assertEquals(edited.extensions, reimported.extensions, "Extensions must be identical after YAML round-trip")
    }

    @Test
    fun jsonExportImportIsIdempotent() = runBlocking {
        val repo = DefaultCanonicalCollectionRepository()
        val first = repo.importCollection(fixture)
        val exported = repo.exportCollection(first, ExportFormat.JSON)
        val second = DefaultCanonicalCollectionRepository().importCollection(exported)

        assertEquals(first.items, second.items)
        assertEquals(first.extensions, second.extensions)
        val reexported = DefaultCanonicalCollectionRepository().exportCollection(second, ExportFormat.JSON)
        assertEquals(Json.parseToJsonElement(exported), Json.parseToJsonElement(reexported))
    }
}
