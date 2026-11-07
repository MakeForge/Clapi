package io.github.crustack.clapi.core.model.opencollection

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenCollectionModelTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testDocumentSerializationRoundTrip() {
        val doc = OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(
                name = "Test API",
                version = "1.0.0",
                summary = "A test collection"
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
                    info = FolderInfo(name = "Users"),
                    items = listOf(
                        HttpRequestItem(
                            info = HttpRequestInfo(name = "Get Users"),
                            http = HttpRequestDetails(
                                method = "GET",
                                url = "{{baseUrl}}/users",
                                headers = listOf(HttpRequestHeader(name = "Accept", value = "application/json")),
                                params = listOf(HttpRequestParam(name = "limit", value = "10", type = "query")),
                                auth = AuthBearer(token = "test-token")
                            )
                        )
                    )
                )
            ),
            extensions = mapOf(
                "clapi" to JsonObject(mapOf("authType" to JsonPrimitive("jwt")))
            )
        )

        val encoded = json.encodeToString(OpenCollectionDocument.serializer(), doc)
        assertTrue(encoded.contains("\"opencollection\":\"1.0.0\""))
        assertTrue(encoded.contains("\"secret\":true"))
        // Secret variable must NOT have a value field
        assertFalse(encoded.contains("\"apiKey\":{\"value\""))
        assertFalse(encoded.contains("\"apiKey\",\"value\""))

        val decoded = json.decodeFromString(OpenCollectionDocument.serializer(), encoded)
        assertEquals("1.0.0", decoded.opencollection)
        assertEquals("Test API", decoded.info.name)
        assertEquals(1, decoded.items.size)

        val folder = assertIs<FolderItem>(decoded.items[0])
        assertEquals("Users", folder.info.name)
        assertEquals("folder", folder.info.type)
        assertEquals(1, folder.items.size)

        val request = assertIs<HttpRequestItem>(folder.items[0])
        assertEquals("Get Users", request.info.name)
        assertEquals("http", request.info.type)
        assertEquals("GET", request.http.method)
        assertEquals("{{baseUrl}}/users", request.http.url)

        val bearerAuth = assertIs<AuthBearer>(request.http.auth)
        assertEquals("test-token", bearerAuth.token)

        val env = decoded.config?.environments?.firstOrNull()
        assertNotNull(env)
        assertEquals(2, env.variables.size)
        val regular = assertIs<RegularVariable>(env.variables[0])
        assertEquals("https://api.example.com", regular.value?.asString)
        val secret = assertIs<SecretVariable>(env.variables[1])
        assertEquals("apiKey", secret.name)
        assertTrue(secret.secret)
    }

    @Test
    fun testAuthInheritSerialization() {
        val details = HttpRequestDetails(
            method = "GET",
            url = "https://api.example.com",
            auth = AuthInherit
        )
        val encoded = json.encodeToString(HttpRequestDetails.serializer(), details)
        assertTrue(encoded.contains("\"auth\":\"inherit\""))

        val decoded = json.decodeFromString(HttpRequestDetails.serializer(), encoded)
        assertEquals(AuthInherit, decoded.auth)
    }

    @Test
    fun testDescriptionAndDocumentationPolymorphism() {
        // String format
        val jsonStr = """{"content":"Simple desc","type":"text/plain"}"""
        val descObj = json.decodeFromString(Description.serializer(), jsonStr)
        assertEquals("Simple desc", descObj.content)

        val primitiveJson = "\"Simple desc string\""
        val descPrimitive = json.decodeFromString(Description.serializer(), primitiveJson)
        assertEquals("Simple desc string", descPrimitive.content)

        val docPrimitive = json.decodeFromString(Documentation.serializer(), "\"Markdown docs\"")
        assertEquals("Markdown docs", docPrimitive.content)
    }

    @Test
    fun testSecretVariableNeverSerializesValue() {
        val secret = SecretVariable(name = "token", type = "string")
        val encoded = json.encodeToString(VariableEntrySerializer, secret)
        assertFalse(encoded.contains("\"value\""))
        assertTrue(encoded.contains("\"secret\":true"))
    }
}
