package io.github.makeforge.clapi.core.storage.exporters

import io.github.makeforge.clapi.core.model.importers.ImportScanResult
import io.github.makeforge.clapi.core.model.importers.SpecFormat
import io.github.makeforge.clapi.core.model.opencollection.CollectionConfig
import io.github.makeforge.clapi.core.model.opencollection.CollectionInfo
import io.github.makeforge.clapi.core.model.opencollection.Environment
import io.github.makeforge.clapi.core.model.opencollection.FolderInfo
import io.github.makeforge.clapi.core.model.opencollection.FolderItem
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestDetails
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestHeader
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestInfo
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestItem
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestParam
import io.github.makeforge.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.makeforge.clapi.core.model.opencollection.RawBody
import io.github.makeforge.clapi.core.model.opencollection.RegularVariable
import io.github.makeforge.clapi.core.model.opencollection.VariableValue
import io.github.makeforge.clapi.core.storage.importers.OpenApiImporter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OpenApiExporterTest {

    @Test
    fun normalizePath_handlesVariousUrlFormats() {
        assertEquals("/users/{id}", OpenApiExporter.normalizePath("{{baseUrl}}/users/:id"))
        assertEquals("/users/{id}", OpenApiExporter.normalizePath("/users/{{id}}"))
        assertEquals("/pets/{petId}", OpenApiExporter.normalizePath("https://api.example.com/pets/:petId?limit=10"))
        assertEquals("/orders", OpenApiExporter.normalizePath("http://localhost:8080/orders?status=active"))
        assertEquals("/", OpenApiExporter.normalizePath(""))
    }

    @Test
    fun exportToYaml_includesPathsAndEnvironments_andRoundtripsWithImporter() = runTest {
        val env1 = Environment(
            name = "Production",
            variables = listOf(
                RegularVariable(name = "baseUrl", value = VariableValue.of("https://api.production.com")),
                RegularVariable(name = "apiKey", value = VariableValue.of("secret_key_123")),
            )
        )
        val env2 = Environment(
            name = "Staging",
            variables = listOf(
                RegularVariable(name = "baseUrl", value = VariableValue.of("https://staging.api.com")),
            )
        )

        val getPetsRequest = HttpRequestItem(
            info = HttpRequestInfo(name = "List Pets", type = "http"),
            http = HttpRequestDetails(
                method = "GET",
                url = "{{baseUrl}}/pets?limit=20",
                headers = listOf(HttpRequestHeader("Accept", "application/json")),
                params = listOf(HttpRequestParam("limit", "20", type = "query")),
            )
        )

        val createPetRequest = HttpRequestItem(
            info = HttpRequestInfo(name = "Create Pet", type = "http"),
            http = HttpRequestDetails(
                method = "POST",
                url = "{{baseUrl}}/pets",
                headers = listOf(HttpRequestHeader("X-Api-Key", "my-key")),
                body = RawBody(type = "json", data = """{"name":"Fido","tag":"dog"}"""),
            )
        )

        val getPetByIdRequest = HttpRequestItem(
            info = HttpRequestInfo(name = "Get Pet", type = "http"),
            http = HttpRequestDetails(
                method = "GET",
                url = "{{baseUrl}}/pets/:petId",
                params = listOf(HttpRequestParam("petId", "123", type = "path")),
            )
        )

        val petsFolder = FolderItem(
            info = FolderInfo(name = "Pets", type = "folder"),
            items = listOf(getPetsRequest, createPetRequest, getPetByIdRequest),
        )

        val doc = OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(name = "Petstore API", summary = "A sample petstore API"),
            config = CollectionConfig(environments = listOf(env1, env2)),
            items = listOf(petsFolder),
        )

        val yaml = OpenApiExporter.exportToYaml(doc)

        // Verify OpenAPI YAML structure
        assertTrue(yaml.contains("openapi: 3.0.3"))
        assertTrue(yaml.contains("title: Petstore API"))
        assertTrue(yaml.contains("https://api.production.com"))
        assertTrue(yaml.contains("https://staging.api.com"))
        assertTrue(yaml.contains("x-environments:"))
        assertTrue(yaml.contains("name: Production"))
        assertTrue(yaml.contains("apiKey: secret_key_123"))
        assertTrue(yaml.contains("/pets:"))
        assertTrue(yaml.contains("/pets/{petId}:"))
        assertTrue(yaml.contains("summary: List Pets"))
        assertTrue(yaml.contains("summary: Create Pet"))
        assertTrue(yaml.contains("tags:"))
        assertTrue(yaml.contains("- Pets"))
        assertTrue(yaml.contains("application/json"))

        // Roundtrip test with OpenApiImporter
        val importer = OpenApiImporter()
        val scanResult = importer.scanContent(yaml)
        val success = assertIs<ImportScanResult.Success>(scanResult)

        assertEquals(SpecFormat.OPENAPI_V3, success.format)
        assertEquals("Petstore API", success.specTitle)
        assertEquals(1, success.folders.size)
        assertEquals(3, success.folders.first().requestCount)

        // Environments should be imported
        assertEquals(2, success.environments.size)
        val scannedEnv1 = success.environments.first { it.name == "Production" }
        assertEquals(2, scannedEnv1.variableCount)
        val scannedEnv2 = success.environments.first { it.name == "Staging" }
        assertEquals(1, scannedEnv2.variableCount)
    }
}
