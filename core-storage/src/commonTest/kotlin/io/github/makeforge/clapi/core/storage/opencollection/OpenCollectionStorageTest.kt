package io.github.makeforge.clapi.core.storage.opencollection

import io.github.makeforge.clapi.core.model.McpConnectionConfig
import io.github.makeforge.clapi.core.model.McpTransportType
import io.github.makeforge.clapi.core.model.opencollection.AuthBearer
import io.github.makeforge.clapi.core.model.opencollection.ClapiExtensionData
import io.github.makeforge.clapi.core.model.opencollection.CollectionConfig
import io.github.makeforge.clapi.core.model.opencollection.CollectionInfo
import io.github.makeforge.clapi.core.model.opencollection.Environment
import io.github.makeforge.clapi.core.model.opencollection.FolderInfo
import io.github.makeforge.clapi.core.model.opencollection.FolderItem
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestDetails
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestHeader
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestInfo
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestItem
import io.github.makeforge.clapi.core.model.opencollection.McpConnectionConfigDto
import io.github.makeforge.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.makeforge.clapi.core.model.opencollection.RegularVariable
import io.github.makeforge.clapi.core.model.opencollection.SecretVariable
import io.github.makeforge.clapi.core.model.opencollection.VariableValue
import io.github.makeforge.clapi.core.model.opencollection.getClapiExtensionData
import io.github.makeforge.clapi.core.model.opencollection.withClapiExtensionData
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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
    fun testClapiMcpBlockSurvivesJsonAndYamlRoundTrip() {
        val mcpData = ClapiExtensionData(
            mcp = listOf(
                McpConnectionConfigDto(
                    id = "mcp-1",
                    name = "Local server",
                    config = McpConnectionConfig(
                        transport = McpTransportType.STDIO,
                        command = "npx",
                        args = listOf("-y", "8080", "true", "1e3"),
                        env = mapOf("PORT" to "8080", "DEBUG" to "false", "VERSION" to "1.50", "PAD" to "007")
                    )
                )
            )
        )
        val original = createSampleDocument().copy(
            extensions = emptyMap<String, JsonElement>().withClapiExtensionData(mcpData)
        )

        val restoredFromJson = OpenCollectionSerializer.deserializeJson(OpenCollectionSerializer.serializeJson(original))
        val restoredFromYaml = OpenCollectionSerializer.deserializeYaml(OpenCollectionSerializer.serializeYaml(original))

        assertEquals(mcpData, restoredFromJson.extensions.getClapiExtensionData())
        assertEquals(mcpData, restoredFromYaml.extensions.getClapiExtensionData())
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
    fun testSecretVariableIsWrittenAsSecretEvenWhenFlagIsFalse() {
        val doc = OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(name = "Secret Flag Test"),
            config = CollectionConfig(
                environments = listOf(
                    Environment(
                        name = "Production",
                        variables = listOf(SecretVariable(name = "apiKey", secret = false))
                    )
                )
            )
        )
        val reloaded = OpenCollectionSerializer.deserializeJson(OpenCollectionSerializer.serializeJson(doc))
        val variable = reloaded.config?.environments?.single()?.variables?.single()

        assertTrue(variable is SecretVariable)
        assertTrue((variable as SecretVariable).secret)
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
        val globalDoc = repository.loadGlobalCollection()
        assertEquals(1, globalDoc?.items?.size)
        val observed = repository.observeCollections().first()
        assertEquals(listOf(doc.info.name), observed.map { it.info.name })
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
        assertEquals(listOf("Sample Collection", "Sample Collection (1)"), repository.observeCollections().first().map { it.info.name })

        // Delete
        repository.deleteCollection(doc.info.name)
        val afterDelete = repository.getCollection(doc.info.name)
        assertEquals(null, afterDelete)
        assertEquals(listOf("Sample Collection (1)"), repository.observeCollections().first().map { it.info.name })
    }

    private fun validSerializedSample(): String = OpenCollectionSerializer.serializeJson(createSampleDocument())

    @Test
    fun testValidatorReportsUnsupportedVersionWithPath() {
        val json = validSerializedSample().replace("\"opencollection\": \"1.0.0\"", "\"opencollection\": \"2.0.0\"")

        val ex = assertFailsWith<CollectionValidationException> { validator.validateOrThrow(json) }

        val issue = ex.issues.single { it.path == "$.opencollection" }
        assertTrue(issue.message.contains("2.0.0"), "issue=$issue")
        assertTrue(ex.message.orEmpty().contains("$.opencollection"), "message=${ex.message}")
    }

    @Test
    fun testValidatorReportsFolderWithHttpTypeWithPath() {
        val json = validSerializedSample().replace("\"type\": \"folder\"", "\"type\": \"http\"")

        val ex = assertFailsWith<CollectionValidationException> { validator.validateOrThrow(json) }

        assertTrue(ex.issues.any { it.path.contains("items") }, "issues=${ex.issues}")
    }

    @Test
    fun testValidatorReportsUnknownAuthTypeWithPath() {
        val json = validSerializedSample().replace("\"type\": \"bearer\"", "\"type\": \"kerberos\"")

        val ex = assertFailsWith<CollectionValidationException> { validator.validateOrThrow(json) }

        assertTrue(ex.issues.any { it.path.contains("auth") }, "issues=${ex.issues}")
    }

    @Test
    fun testRejectedWriteKeepsPreviousStoredDocument() = kotlinx.coroutines.test.runTest {
        val driver = BundledCollectionStorageDriver()
        val location = StorageLocation.MemoryKey("keep-key")
        driver.write(location, createSampleDocument())
        val invalidDoc = createSampleDocument().copy(opencollection = "2.0.0", info = CollectionInfo(name = "Changed", version = "1.0.0"))

        assertFailsWith<CollectionValidationException> { driver.write(location, invalidDoc) }

        assertEquals("Sample Collection", driver.read(location).info.name)
    }

    @Test
    fun testFolderExtensionsKeyIsRejectedBySchema() {
        val json = """
            {
                "opencollection": "1.0.0",
                "info": { "name": "Workspace" },
                "items": [
                    {
                        "info": { "name": "Collection A", "type": "folder" },
                        "extensions": { "id": "id-a" },
                        "items": []
                    }
                ]
            }
        """.trimIndent()

        val issues = validator.validate(json)
        assertFalse(issues.isEmpty(), "Schema must reject an extensions key on a folder item: $issues")
    }

    @Test
    fun testTopLevelFolderIdsSurviveSaveAndReload() = kotlinx.coroutines.test.runTest {
        val original = OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(name = "Workspace"),
            items = listOf(
                FolderItem(
                    info = FolderInfo(name = "Alpha", type = "folder"),
                    extensions = mapOf("id" to JsonPrimitive("id-alpha")),
                ),
                FolderItem(
                    info = FolderInfo(name = "Beta", type = "folder"),
                    extensions = mapOf("id" to JsonPrimitive("id-beta")),
                    items = listOf(
                        FolderItem(
                            info = FolderInfo(name = "Nested", type = "folder"),
                            extensions = mapOf("id" to JsonPrimitive("id-nested")),
                        )
                    ),
                ),
            ),
        )

        val json = OpenCollectionSerializer.serializeJson(original)
        validator.validateOrThrow(json)
        val root = OpenCollectionSerializer.json.parseToJsonElement(json).jsonObject
        val firstItem = root["items"]!!.jsonArray[0].jsonObject
        assertFalse(firstItem.containsKey("extensions"), "Folder item must not contain extensions: $firstItem")
        val folderIds = root["extensions"]!!.jsonObject["folderIds"]!!.jsonObject
        assertEquals(JsonPrimitive("id-alpha"), folderIds["0"])
        assertEquals(JsonPrimitive("id-beta"), folderIds["1"])

        val driver = BundledCollectionStorageDriver()
        val location = StorageLocation.MemoryKey(GLOBAL_COLLECTION_FILENAME)
        driver.write(location, original)
        val reloaded = driver.read(location)

        val alpha = assertIs<FolderItem>(reloaded.items[0])
        val beta = assertIs<FolderItem>(reloaded.items[1])
        val nested = assertIs<FolderItem>(beta.items[0])
        assertEquals("id-alpha", (alpha.extensions["id"] as JsonPrimitive).content)
        assertEquals("id-beta", (beta.extensions["id"] as JsonPrimitive).content)
        assertEquals("id-nested", (nested.extensions["id"] as JsonPrimitive).content)
        assertFalse(reloaded.extensions.containsKey(OpenCollectionSerializer.FOLDER_IDS_KEY))

        val repository = DefaultCanonicalCollectionRepository(storageDriver = driver)
        val observed = repository.observeCollections().first()
        assertEquals(listOf("id-alpha", "id-beta"), observed.map { (it.extensions["id"] as JsonPrimitive).content })

        val reopened = DefaultCanonicalCollectionRepository(storageDriver = driver).loadGlobalCollection()
        val reopenedAlpha = assertIs<FolderItem>(reopened!!.items[0])
        val reopenedBeta = assertIs<FolderItem>(reopened.items[1])
        assertEquals("id-alpha", (reopenedAlpha.extensions["id"] as JsonPrimitive).content)
        assertEquals("id-beta", (reopenedBeta.extensions["id"] as JsonPrimitive).content)
    }
}

