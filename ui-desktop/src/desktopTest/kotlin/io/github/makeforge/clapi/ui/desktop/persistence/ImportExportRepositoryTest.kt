package io.github.makeforge.clapi.ui.shared.persistence

import androidx.compose.runtime.mutableStateListOf
import io.github.makeforge.clapi.core.model.BodyType
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import io.github.makeforge.clapi.ui.shared.state.AppStateExamples

class ImportExportRepositoryTest {

    @Test
    fun generateUniqueCollectionName_appendsIncrementingSuffix() {
        val existing = setOf("User API", "User API (1)", "User API (2)")
        val unique = ImportExportNaming.generateUniqueCollectionName("User API", existing)
        assertEquals("User API (3)", unique)
    }

    @Test
    fun generateUniqueEnvironmentName_appendsIncrementingSuffix() {
        val existing = setOf("Dev", "Dev (1)")
        val unique = ImportExportNaming.generateUniqueEnvironmentName("Dev", existing)
        assertEquals("Dev (2)", unique)
    }

    @Test
    fun exportAndImportSingleCollection_roundTripsWithCollectionSchema() {
        val source = AppStateExamples.demo()
        val collection = source.collections.first()

        val content = ImportExportRepository.exportCollectionToString(collection)
        assertTrue(content.contains("\"opencollection\": \"1.0.0\""))
        assertTrue(content.contains("\"info\""))
        assertTrue(content.contains("\"items\""))

        val target = AppState().also { it.collections.clear() }
        val importedName = ImportExportRepository.importCollectionFromString(target, content)

        assertEquals(collection.name, importedName)
        assertEquals(1, target.collections.size)
    }

    @Test
    fun importLegacyClapiCollection_isRejectedAndWritesZeroCollections() {
        val legacyJson = """
            {
              "type": "clapiCollection",
              "version": "1.0",
              "name": "Legacy Col",
              "folders": [],
              "requests": [
                {
                  "name": "Req1",
                  "method": "GET",
                  "url": "https://example.com"
                }
              ]
            }
        """.trimIndent()
        val target = AppState().also { it.collections.clear() }
        val error = assertFailsWith<ImportExportException> {
            ImportExportRepository.importCollectionFromString(target, legacyJson)
        }
        assertTrue(error.message!!.contains("no longer supported"))
        assertEquals(0, target.collections.size)
    }

    @Test
    fun importLegacyClapiWorkspace_isRejectedAndWritesZeroCollections() {
        val legacyWorkspaceJson = """
            {
              "type": "clapiWorkspace",
              "version": "1.0",
              "collections": [],
              "environments": []
            }
        """.trimIndent()
        val target = AppState().also { it.collections.clear() }
        val error = assertFailsWith<ImportExportException> {
            ImportExportRepository.importCollectionFromString(target, legacyWorkspaceJson)
        }
        assertTrue(error.message!!.contains("no longer supported"))
        assertEquals(0, target.collections.size)
    }

    @Test
    fun exportAndImportEnvironment_roundTripsWithEnvironmentSchema() {
        val source = AppStateExamples.demo()
        val environment = source.environments.first()

        val content = ImportExportRepository.exportEnvironmentToString(environment)
        assertTrue(content.contains("\"type\": \"clapiEnvironment\""))
        assertTrue(content.contains("\"variables\""))

        val target = AppState().also { it.environments.clear() }
        val importedName = ImportExportRepository.importEnvironmentFromString(target, content)

        assertEquals(environment.name, importedName)
        assertEquals(1, target.environments.size)
        assertEquals(environment.variables.size, target.environments.first().variables.size)
    }

    @Test(expected = ImportExportException::class)
    fun importCollection_invalidSchema_throws() {
        val json = """
            {
              "type": "notWorkspace",
              "version": "1.0",
              "collections": [],
              "environments": []
            }
        """.trimIndent()

        ImportExportRepository.importCollectionFromString(AppState(), json)
    }

    // ── Large-body export / import (pure serialisation — no PlatformStorage) ───

    @Test
    fun exportCollection_with_large_body_roundtrips_content_exactly() {
        val largeBody = "{\"items\":[" + (0 until 30_000).joinToString(",") { "{\"id\":$it}" } + "]}"

        val request = CollectionNode(
            id = "req-large-body-1",
            name = "Large Body Request",
            isFolder = false,
            method = HttpMethodType.POST,
            url = "https://api.example.com/bulk",
            bodyType = BodyType.JSON,
            bodyContent = largeBody,
            bodyContents = mapOf(BodyType.JSON.name to largeBody),
        )
        val collection = CollectionNode(
            id = "col-large-body-1",
            name = "Large Body Collection",
            isFolder = true,
            children = mutableStateListOf(request),
        )
        val source = AppState(openDefaultTab = false)
        source.collections.add(collection)

        val exported = ImportExportRepository.exportCollectionToString(source.collections.first())

        val importState = AppState(openDefaultTab = false)
        ImportExportRepository.importCollectionFromString(importState, exported)

        val imported = importState.collections.first().children.first()
        assertEquals(
            largeBody.length,
            imported.bodyContent?.length,
            "Large body length must survive collection export/import",
        )
        assertEquals(
            largeBody,
            imported.bodyContent,
            "Large body content must survive collection export/import exactly",
        )
    }

    @Test
    fun importCollectionFromString_preserves_collection_and_folder_node_ids() {
        val source = AppState(openDefaultTab = false)
        val folderId = "folder-persist-1"
        val collId   = "coll-persist-1"
        source.collections.add(
            CollectionNode(
                id = collId, name = "Persist Test", isFolder = true,
                children = mutableStateListOf(
                    CollectionNode(id = folderId, name = "Sub Folder", isFolder = true,
                        children = mutableStateListOf()),
                ),
            )
        )

        val json   = ImportExportRepository.exportCollectionToString(source.collections.first())
        val target = AppState(openDefaultTab = false)
        ImportExportRepository.importCollectionFromString(target, json)

        val importedColl = target.collections.first { it.name == "Persist Test" }
        assertEquals(collId,   importedColl.id,
            "Collection ID must be preserved through collection export/import")

        val importedFolder = importedColl.children.first { it.name == "Sub Folder" }
        assertEquals(folderId, importedFolder.id,
            "Folder ID must be preserved through collection export/import")
    }

    // ── TRACE and CONNECT round-trip ───────────────────────────────────────────

    @Test
    fun `TRACE request survives collection export and import`() {
        val request = CollectionNode(
            id = "req-trace-1",
            name = "Trace Request",
            isFolder = false,
            method = HttpMethodType.TRACE,
            url = "https://example.com/trace",
        )
        val source = AppState(openDefaultTab = false)
        source.collections.add(
            CollectionNode("coll-trace-1", "Trace Collection", isFolder = true,
                children = mutableStateListOf(request))
        )

        val exported = ImportExportRepository.exportCollectionToString(source.collections.first())

        val target = AppState(openDefaultTab = false)
        ImportExportRepository.importCollectionFromString(target, exported)

        val importedRequest = target.collections.first().children.first()
        assertEquals(HttpMethodType.TRACE, importedRequest.method,
            "TRACE method must survive collection export/import round-trip")
    }

    @Test
    fun `CONNECT request survives collection export and import`() {
        val request = CollectionNode(
            id = "req-connect-1",
            name = "Connect Request",
            isFolder = false,
            method = HttpMethodType.CONNECT,
            url = "https://proxy.example.com:443",
        )
        val source = AppState(openDefaultTab = false)
        source.collections.add(
            CollectionNode("coll-connect-1", "Connect Collection", isFolder = true,
                children = mutableStateListOf(request))
        )

        val exported = ImportExportRepository.exportCollectionToString(source.collections.first())

        val target = AppState(openDefaultTab = false)
        ImportExportRepository.importCollectionFromString(target, exported)

        val importedRequest = target.collections.first().children.first()
        assertEquals(HttpMethodType.CONNECT, importedRequest.method,
            "CONNECT method must survive collection export/import round-trip")
    }

    @Test
    fun `importCollectionFromString imports Insomnia v5 YAML collection and environments`() {
        val insomniaYaml = """
            type: collection.insomnia.rest/5.0
            schema_version: "5.1"
            name: Insomnia Billing API
            meta:
              id: wrk_billing
            environments:
              name: Base Environment
              meta:
                id: env_billing_base
              data:
                apiHost: https://billing.example.com
              subEnvironments:
                - name: Sandbox
                  meta:
                    id: env_sandbox
                  data:
                    apiKey: sb_test_123
            collection:
              - name: Invoices
                meta:
                  id: fld_invoices
                children:
                  - name: List Invoices
                    url: "{{apiHost}}/v1/invoices"
                    method: GET
                    meta:
                      id: req_list_invoices
                    headers:
                      - name: Authorization
                        value: "Bearer {{apiKey}}"
                    scripts:
                      preRequest: |
                        insomnia.environment.set("traceId", "tr-999");
                      afterResponse: |
                        insomnia.test("200 OK", () => {
                          insomnia.expect(insomnia.response.status).to.eql(200);
                        });
        """.trimIndent()

        val target = AppState(openDefaultTab = false).apply {
            collections.clear()
            environments.clear()
        }

        val importedName = ImportExportRepository.importCollectionFromString(target, insomniaYaml)
        assertEquals("Insomnia Billing API", importedName)
        assertEquals(1, target.collections.size)

        val col = target.collections.first()
        assertEquals(1, col.children.size)
        val folder = col.children.first()
        assertEquals("Invoices", folder.name)
        assertTrue(folder.isFolder)

        val req = folder.children.first()
        assertEquals("List Invoices", req.name)
        assertEquals(HttpMethodType.GET, req.method)
        assertEquals("{{apiHost}}/v1/invoices", req.url)
        assertTrue(req.preRequestScript?.contains("clapi.environment.set(\"traceId\", \"tr-999\")") == true)
        assertTrue(req.testScript?.contains("clapi.test(\"200 OK\"") == true)

        assertEquals(2, target.environments.size)
        assertTrue(target.environments.any { it.name == "Base Environment" })
        val sandboxEnv = target.environments.first { it.name == "Sandbox" }
        assertTrue(sandboxEnv.variables.any { it.key == "apiHost" && it.value == "https://billing.example.com" })
        assertTrue(sandboxEnv.variables.any { it.key == "apiKey" && it.value == "sb_test_123" })
    }
}
