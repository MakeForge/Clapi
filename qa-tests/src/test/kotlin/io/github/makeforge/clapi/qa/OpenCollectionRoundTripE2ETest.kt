package io.github.makeforge.clapi.qa

import io.github.makeforge.clapi.core.model.opencollection.ClapiExtensionData
import io.github.makeforge.clapi.core.model.opencollection.FolderItem
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestItem
import io.github.makeforge.clapi.core.model.opencollection.WebSocketRequestItem
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessagePayload
import io.github.makeforge.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.makeforge.clapi.core.model.opencollection.getClapiExtensionData
import io.github.makeforge.clapi.core.model.opencollection.withClapiExtensionData
import io.github.makeforge.clapi.core.storage.opencollection.DefaultCanonicalCollectionRepository
import io.github.makeforge.clapi.core.storage.opencollection.ExportFormat
import io.github.makeforge.clapi.core.storage.opencollection.GLOBAL_COLLECTION_FILENAME
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
            },
            {
              "info": {"name": "WebSocket Echo", "type": "websocket"},
              "websocket": {
                "url": "{{baseUrl}}/ws",
                "headers": [{"name": "X-Custom", "value": "custom-val"}],
                "message": [
                  {
                    "title": "Ping",
                    "selected": true,
                    "message": {"type": "json", "data": "{\"ping\":true}"}
                  }
                ],
                "auth": {"type": "bearer", "token": "ws-token"}
              },
              "settings": {
                "timeout": 5000,
                "keepAliveInterval": 2000
              },
              "docs": "WS Docs"
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
        assertEquals(listOf("Users", "Health", "WebSocket Echo"), doc.items.map { it.info.name })
        assertEquals("Round Trip", doc.info.name)
        val wsItem = assertIs<WebSocketRequestItem>(doc.items[2])
        assertEquals("{{baseUrl}}/ws", wsItem.websocket.url)
        assertEquals("WS Docs", wsItem.docs)
        val variants = assertIs<WebSocketMessagePayload.Variants>(wsItem.websocket.message)
        assertEquals(1, variants.variants.size)
        assertEquals("Ping", variants.variants[0].title)
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
        assertEquals(listOf("Users", "Health", "WebSocket Echo"), reloaded.items.map { it.info.name })
        assertEquals("Round Trip", reloaded.info.name)
        assertEquals("{{baseUrl}}/users", listUsersUrl(reloaded))

        val edited = withListUsersUrl(reloaded, "{{baseUrl}}/v2/users")
        DefaultCanonicalCollectionRepository(baseDirectory = dir).saveCollection(edited)

        val file = File(dir, GLOBAL_COLLECTION_FILENAME)
        assertTrue(file.exists(), "Global collection file must be written to disk")

        val afterEdit = DefaultCanonicalCollectionRepository(baseDirectory = dir)
            .getCollection("Round Trip")
        assertNotNull(afterEdit)
        assertEquals(listOf("Users", "Health", "WebSocket Echo"), afterEdit.items.map { it.info.name })
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

    @Test
    fun multipleCollectionsRoundTripPreservesIndependentCollections() = runBlocking {
        val dir = tempDir.root.absolutePath
        val repo = DefaultCanonicalCollectionRepository(baseDirectory = dir)

        val col1 = repo.importCollection(fixture)
        val fixture2 = fixture.replace("Round Trip", "Second Collection")
        val col2 = repo.importCollection(fixture2)

        val freshRepo = DefaultCanonicalCollectionRepository(baseDirectory = dir)
        val loaded1 = freshRepo.getCollection("Round Trip")
        val loaded2 = freshRepo.getCollection("Second Collection")

        assertNotNull(loaded1)
        assertNotNull(loaded2)
        assertEquals("Round Trip", loaded1.info.name)
        assertEquals("Second Collection", loaded2.info.name)
    }

    @Test
    fun graphqlRoundTripPreservesBothSingleAndVariantBodies() = runBlocking {
        val dir = tempDir.root.absolutePath
        val repo = DefaultCanonicalCollectionRepository(baseDirectory = dir)

        val fixtureCandidates = listOf(
            File("qa-tests/fixtures/graphql-test-collection.json"),
            File("fixtures/graphql-test-collection.json"),
            File("../qa-tests/fixtures/graphql-test-collection.json"),
        )
        val fixtureFile = fixtureCandidates.firstOrNull { it.exists() }
            ?: error("graphql-test-collection.json fixture not found")
        val fixtureGql = fixtureFile.readText()
        val imported = repo.importCollection(fixtureGql)

        assertEquals("GraphQL Test Suite", imported.info.name)
        assertEquals(2, imported.items.size)

        val singleReq = assertIs<io.github.makeforge.clapi.core.model.opencollection.GraphQLRequestItem>(imported.items[0])
        assertEquals("Single Body GraphQL Query", singleReq.info.name)
        assertEquals("POST", singleReq.graphql.method)
        assertEquals("{{baseUrl}}/graphql", singleReq.graphql.url)
        val singleBody = assertIs<io.github.makeforge.clapi.core.model.opencollection.GraphQLBodyPayload.Single>(singleReq.graphql.body)
        assertTrue(singleBody.body.query.contains("GetUser"))
        assertEquals("{\n  \"id\": \"123\"\n}", singleBody.body.variables)
        assertEquals("# User Details Query\nFetches user details by ID.", singleReq.docs)

        val multiReq = assertIs<io.github.makeforge.clapi.core.model.opencollection.GraphQLRequestItem>(imported.items[1])
        assertEquals("Multi Variant GraphQL Operations", multiReq.info.name)
        val variantsPayload = assertIs<io.github.makeforge.clapi.core.model.opencollection.GraphQLBodyPayload.Variants>(multiReq.graphql.body)
        assertEquals(2, variantsPayload.variants.size)
        assertEquals("All Users", variantsPayload.variants[0].title)
        assertEquals(true, variantsPayload.variants[0].selected)
        assertEquals("User by ID", variantsPayload.variants[1].title)

        // Save and reload
        repo.saveCollection(imported)
        val reloaded = DefaultCanonicalCollectionRepository(baseDirectory = dir).getCollection("GraphQL Test Suite")
        assertNotNull(reloaded)
        assertEquals(imported.items, reloaded.items)

        // YAML export and import
        val yaml = repo.exportCollection(reloaded, ExportFormat.YAML)
        val fromYaml = repo.importCollection(yaml)
        assertEquals(imported.items, fromYaml.items)

        // JSON export and import
        val jsonStr = repo.exportCollection(reloaded, ExportFormat.JSON)
        val fromJson = repo.importCollection(jsonStr)
        assertEquals(imported.items, fromJson.items)
    }
}
