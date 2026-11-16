package io.github.makeforge.clapi.core.storage.importers

import io.github.makeforge.clapi.core.model.importers.ImportScanResult
import io.github.makeforge.clapi.core.model.importers.SpecFormat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SpecificationImporterTest {

    private fun loadResource(path: String): String {
        val stream = SpecificationImporterTest::class.java.getResourceAsStream(path)
            ?: error("Resource not found: $path")
        return stream.bufferedReader().use { it.readText() }
    }

    @Test
    fun openCollectionImporter_scansValidOpenCollection() = runTest {
        val content = loadResource("/fixtures/sample-opencollection.yaml")
        val importer = OpenCollectionImporter()

        val result = importer.scanContent(content)

        val success = assertIs<ImportScanResult.Success>(result)
        assertEquals(SpecFormat.OPENCOLLECTION, success.format)
        assertEquals("Sample OpenCollection", success.specTitle)
        assertEquals(1, success.folders.size)
        val folder = success.folders.first()
        assertEquals("Sample OpenCollection", folder.name)
        assertEquals(1, folder.requestCount)
        assertEquals(1, success.environments.size)
        assertEquals("Production", success.environments.first().name)
    }

    @Test
    fun openCollectionImporter_failsOnInvalidContent() = runTest {
        val importer = OpenCollectionImporter()
        val result = importer.scanContent("random: invalid: content:")

        assertIs<ImportScanResult.Failure>(result)
    }

    @Test
    fun openApiImporter_scansValidOpenApiV3() = runTest {
        val content = loadResource("/fixtures/petstore-openapi-v3.yaml")
        val importer = OpenApiImporter()

        val result = importer.scanContent(content)

        val success = assertIs<ImportScanResult.Success>(result)
        assertEquals(SpecFormat.OPENAPI_V3, success.format)
        assertEquals("Swagger Petstore", success.specTitle)
        assertTrue(success.folders.isNotEmpty())
        val rootFolder = success.folders.first()
        assertEquals("Swagger Petstore", rootFolder.name)
        // Check requests are mapped
        assertTrue(rootFolder.requestCount >= 2)
        // Check environment with baseUrl
        assertTrue(success.environments.isNotEmpty())
        val env = success.environments.first()
        assertTrue(env.environment.variables.any { it.name == "baseUrl" })
    }

    @Test
    fun openApiImporter_rejectsSwaggerV2() = runTest {
        val content = loadResource("/fixtures/swagger-v2.yaml")
        val importer = OpenApiImporter()

        val result = importer.scanContent(content)

        val failure = assertIs<ImportScanResult.Failure>(result)
        assertTrue(failure.message.contains("Swagger 2.0") || failure.message.contains("OpenAPI v3"))
    }

    @Test
    fun openApiImporter_failsOnInvalidContent() = runTest {
        val importer = OpenApiImporter()
        val result = importer.scanContent("invalid non-spec content")

        assertIs<ImportScanResult.Failure>(result)
    }

    @Test
    fun compositeImporter_scansBothOpenApiAndOpenCollection() = runTest {
        val importer = SpecificationImporter.default()

        val openColContent = loadResource("/fixtures/sample-opencollection.yaml")
        val openColResult = importer.scanContent(openColContent)
        val openColSuccess = assertIs<ImportScanResult.Success>(openColResult)
        assertEquals(SpecFormat.OPENCOLLECTION, openColSuccess.format)

        val openApiContent = loadResource("/fixtures/petstore-openapi-v3.yaml")
        val openApiResult = importer.scanContent(openApiContent)
        val openApiSuccess = assertIs<ImportScanResult.Success>(openApiResult)
        assertEquals(SpecFormat.OPENAPI_V3, openApiSuccess.format)

        val swaggerV2Content = loadResource("/fixtures/swagger-v2.yaml")
        val swaggerV2Result = importer.scanContent(swaggerV2Content)
        assertIs<ImportScanResult.Failure>(swaggerV2Result)
    }
}
