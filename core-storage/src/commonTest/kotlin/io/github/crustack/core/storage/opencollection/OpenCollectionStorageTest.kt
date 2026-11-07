package io.github.crustack.core.storage.opencollection

import io.github.crustack.core.model.opencollection.AuthBearer
import io.github.crustack.core.model.opencollection.CollectionConfig
import io.github.crustack.core.model.opencollection.CollectionInfo
import io.github.crustack.core.model.opencollection.Environment
import io.github.crustack.core.model.opencollection.FolderInfo
import io.github.crustack.core.model.opencollection.FolderItem
import io.github.crustack.core.model.opencollection.HttpRequestDetails
import io.github.crustack.core.model.opencollection.HttpRequestHeader
import io.github.crustack.core.model.opencollection.HttpRequestInfo
import io.github.crustack.core.model.opencollection.HttpRequestItem
import io.github.crustack.core.model.opencollection.OpenCollectionDocument
import io.github.crustack.core.model.opencollection.RegularVariable
import io.github.crustack.core.model.opencollection.SecretVariable
import io.github.crustack.core.model.opencollection.VariableValue
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OpenCollectionStorageTest {

    private val validator = OpenCollectionSchemaValidator()

    private fun createSampleDocument(): OpenCollectionDocument {
        return OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(
                name = "Sample Collection",
                version = "1.0.0"
            ),
            bundled = true,
            config = CollectionConfig(
                environments = listOf(
                    Environment(
                        name = "Production",
                        variables = listOf(
                            RegularVariable(name = "baseUrl", value = VariableValue.of("https://api.example.com")),
                            SecretVariable(name = "apiKey", type = "string")
                        )
                    )
                )
            ),
            items = listOf(
                FolderItem(
                    info = FolderInfo(name = "Users", type = "folder"),
                    items = listOf(
                        HttpRequestItem(
                            info = HttpRequestInfo(name = "List Users", type = "http"),
                            http = HttpRequestDetails(
                                method = "GET",
                                url = "{{baseUrl}}/users",
                                headers = listOf(HttpRequestHeader(name = "Accept", value = "application/json")),
                                auth = AuthBearer(token = "bearer-token")
                            )
                        )
                    )
                )
            )
        )
    }

    @Test
    fun testValidDocumentPassesSchemaValidation() {
        val doc = createSampleDocument()
        val json = OpenCollectionSerializer.serializeJson(doc)

        val issues = validator.validate(json)
        assertTrue(issues.isEmpty(), "Expected valid document to have 0 validation issues, but got: $issues")

        validator.validateOrThrow(json)
    }

    @Test
    fun testInvalidDocumentFailsSchemaValidation() {
        // Invalid opencollection version (must match schema or type)
        val invalidJson = """
            {
                "opencollection": 123,
                "info": {
                    "name": "Invalid"
                }
            }
        """.trimIndent()

        val issues = validator.validate(invalidJson)
        assertFalse(issues.isEmpty(), "Expected validation issues for invalid opencollection version")
        assertTrue(issues.any { it.path.contains("opencollection") })

        assertFailsWith<CollectionValidationException> {
            validator.validateOrThrow(invalidJson)
        }
    }

    @Test
    fun testJsonSerializationAndDeserializationRoundTrip() {
        val original = createSampleDocument()
        val serializedJson = OpenCollectionSerializer.serializeJson(original)

        val deserialized = OpenCollectionSerializer.deserializeJson(serializedJson)
        assertEquals(original.opencollection, deserialized.opencollection)
        assertEquals(original.info.name, deserialized.info.name)
        assertEquals(1, deserialized.items.size)

        val folder = assertIs<FolderItem>(deserialized.items[0])
        assertEquals("Users", folder.info.name)
        assertEquals(1, folder.items.size)

        val request = assertIs<HttpRequestItem>(folder.items[0])
        assertEquals("List Users", request.info.name)
        assertEquals("GET", request.http.method)
    }

    @Test
    fun testYamlSerializationAndDeserializationRoundTrip() {
        val original = createSampleDocument()
        val serializedYaml = OpenCollectionSerializer.serializeYaml(original)
        assertTrue(serializedYaml.isNotEmpty())

        val deserialized = OpenCollectionSerializer.deserializeYaml(serializedYaml)
        assertEquals(original.opencollection, deserialized.opencollection)
        assertEquals(original.info.name, deserialized.info.name)
        assertEquals(1, deserialized.items.size)

        val folder = assertIs<FolderItem>(deserialized.items[0])
        assertEquals("Users", folder.info.name)
        assertEquals(1, folder.items.size)

        val request = assertIs<HttpRequestItem>(folder.items[0])
        assertEquals("List Users", request.info.name)
        assertEquals("GET", request.http.method)
    }

    @Test
    fun testUnknownFieldCaptureIntoExtensions() {
        val jsonWithUnknownFields = """
            {
                "opencollection": "1.0.0",
                "info": {
                    "name": "Ext Collection"
                },
                "customTopLevelField": "preservedValue",
                "items": [
                    {
                        "info": {
                            "name": "Folder",
                            "type": "folder"
                        },
                        "customFolderField": 42,
                        "items": [
                            {
                                "info": {
                                    "name": "Request",
                                    "type": "http"
                                },
                                "http": {
                                    "method": "GET",
                                    "url": "https://example.com"
                                },
                                "customRequestField": true
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val doc = OpenCollectionSerializer.deserializeJson(jsonWithUnknownFields)
        // Check document-level extension
        assertEquals(JsonPrimitive("preservedValue"), doc.extensions["customTopLevelField"])

        // Check folder-level extension
        val folder = assertIs<FolderItem>(doc.items[0])
        assertEquals(JsonPrimitive(42), folder.extensions["customFolderField"])

        // Check request-level extension
        val request = assertIs<HttpRequestItem>(folder.items[0])
        assertEquals(JsonPrimitive(true), request.extensions["customRequestField"])
    }

    @Test
    fun testSecretVariableNeverHasValueInSerializedOutput() {
        val docWithSecret = OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(name = "Secrets Test"),
            config = CollectionConfig(
                environments = listOf(
                    Environment(
                        name = "Production",
                        variables = listOf(
                            SecretVariable(name = "apiKey", type = "string")
                        )
                    )
                )
            )
        )
        val json = OpenCollectionSerializer.serializeJson(docWithSecret)

        assertFalse(json.contains("\"value\":"), "Serialized JSON must not contain value for secret variables")
        assertTrue(json.contains("\"secret\": true") || json.contains("\"secret\":true"))

        val yaml = OpenCollectionSerializer.serializeYaml(docWithSecret)
        assertFalse(yaml.contains("value:"), "Serialized YAML must not contain value for secret variables")
    }

    @Test
    fun testItemOrderingIsPreserved() {
        val doc = OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(name = "Ordering Test"),
            items = listOf(
                HttpRequestItem(
                    info = HttpRequestInfo(name = "First"),
                    http = HttpRequestDetails(method = "GET", url = "https://example.com/1")
                ),
                HttpRequestItem(
                    info = HttpRequestInfo(name = "Second"),
                    http = HttpRequestDetails(method = "POST", url = "https://example.com/2")
                ),
                FolderItem(
                    info = FolderInfo(name = "Third"),
                    items = listOf(
                        HttpRequestItem(
                            info = HttpRequestInfo(name = "Nested A"),
                            http = HttpRequestDetails(method = "GET", url = "https://example.com/3a")
                        ),
                        HttpRequestItem(
                            info = HttpRequestInfo(name = "Nested B"),
                            http = HttpRequestDetails(method = "GET", url = "https://example.com/3b")
                        )
                    )
                )
            )
        )

        val json = OpenCollectionSerializer.serializeJson(doc)
        val decoded = OpenCollectionSerializer.deserializeJson(json)

        assertEquals("First", decoded.items[0].info.name)
        assertEquals("Second", decoded.items[1].info.name)
        assertEquals("Third", decoded.items[2].info.name)

        val folder = assertIs<FolderItem>(decoded.items[2])
        assertEquals("Nested A", folder.items[0].info.name)
        assertEquals("Nested B", folder.items[1].info.name)
    }

    @Test
    fun testBundledStorageDriverMemoryKeyRoundTrip() = kotlinx.coroutines.test.runTest {
        val driver = BundledCollectionStorageDriver()
        val doc = createSampleDocument()
        val location = StorageLocation.MemoryKey("test-key")

        driver.write(location, doc)
        val readDoc = driver.read(location)

        assertEquals(doc.info.name, readDoc.info.name)
        assertEquals(1, readDoc.items.size)

        driver.delete(location)
        assertFailsWith<StorageReadException> {
            driver.read(location)
        }
    }

    @Test
    fun testBundledStorageDriverInvalidDocumentFailsPreSave() = kotlinx.coroutines.test.runTest {
        val driver = BundledCollectionStorageDriver()
        // Invalid opencollection version "2.0.0" (schema requires 1.0.0)
        val invalidDoc = createSampleDocument().copy(opencollection = "2.0.0")
        val location = StorageLocation.MemoryKey("invalid-key")

        assertFailsWith<CollectionValidationException> {
            driver.write(location, invalidDoc)
        }
    }

    @Test
    fun testCanonicalCollectionRepositoryCrud() = kotlinx.coroutines.test.runTest {
        val repository = DefaultCanonicalCollectionRepository()
        val doc = createSampleDocument()

        repository.saveCollection(doc)
        val fetched = repository.getCollection(doc.info.name)
        assertEquals(doc.info.name, fetched?.info?.name)

        // Export JSON
        val exportedJson = repository.exportCollection(doc, ExportFormat.JSON)
        assertTrue(exportedJson.contains("\"opencollection\": \"1.0.0\""))

        // Export YAML
        val exportedYaml = repository.exportCollection(doc, ExportFormat.YAML)
        assertTrue(exportedYaml.contains("opencollection: 1.0.0") || exportedYaml.contains("opencollection: \"1.0.0\""))

        // Import
        val imported = repository.importCollection(exportedJson)
        // Since name already existed, it should get a unique name
        assertEquals("Sample Collection (1)", imported.info.name)

        // Delete
        repository.deleteCollection(doc.info.name)
        val afterDelete = repository.getCollection(doc.info.name)
        assertEquals(null, afterDelete)
    }
}

