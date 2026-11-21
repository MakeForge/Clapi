package io.github.makeforge.clapi.qa

import io.github.makeforge.clapi.core.model.BodyType
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.core.model.KeyValueEntry
import io.github.makeforge.clapi.core.model.RequestBody
import io.github.makeforge.clapi.core.model.RequestDefinition
import io.github.makeforge.clapi.core.model.opencollection.CollectionConfig
import io.github.makeforge.clapi.core.model.opencollection.CollectionInfo
import io.github.makeforge.clapi.core.model.opencollection.Description
import io.github.makeforge.clapi.core.model.opencollection.DynamicVariable
import io.github.makeforge.clapi.core.model.opencollection.DynamicVariableParameter
import io.github.makeforge.clapi.core.model.opencollection.Environment
import io.github.makeforge.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.makeforge.clapi.core.model.opencollection.RegularVariable
import io.github.makeforge.clapi.core.model.opencollection.VariableType
import io.github.makeforge.clapi.core.model.opencollection.VariableValue
import io.github.makeforge.clapi.core.network.KtorApiClient
import io.github.makeforge.clapi.core.network.NetworkEvent
import io.github.makeforge.clapi.core.network.RetryPolicy
import io.github.makeforge.clapi.core.network.dynamicvar.DynamicVariableResolutionException
import io.github.makeforge.clapi.core.network.execution.RequestExecutionService
import io.github.makeforge.clapi.core.storage.opencollection.DefaultCanonicalCollectionRepository
import io.github.makeforge.clapi.core.storage.opencollection.ExportFormat
import io.github.makeforge.clapi.testsupport.DummyApiServer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DynamicVariablesE2ETest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private lateinit var server: DummyApiServer
    private lateinit var baseUrl: String

    @BeforeTest
    fun setUp() {
        server = DummyApiServer()
        server.start()
        baseUrl = server.baseUrl()
    }

    @AfterTest
    fun tearDown() {
        server.stop()
    }

    @Test
    fun executesHttpRequestWithParameterizedAndRequestContextDynamicVariables() = runTest {
        val client = KtorApiClient(retryPolicy = RetryPolicy(maxAttempts = 1))
        val service = RequestExecutionService(client)

        val userDynamicVariables = listOf(
            DynamicVariable(
                name = "secondsFromNow",
                script = "return String(1700000000 + Number(args.seconds || 0));",
                parameters = listOf(
                    DynamicVariableParameter(name = "seconds", defaultValue = "5")
                ),
                description = Description("Adds seconds to base epoch"),
            ),
            DynamicVariable(
                name = "formatTag",
                script = "return args.prefix + '-' + args.code;",
                parameters = listOf(
                    DynamicVariableParameter(name = "prefix", defaultValue = "ID"),
                    DynamicVariableParameter(name = "code", defaultValue = "1"),
                ),
            ),
            DynamicVariable(
                name = "requestSig",
                script = "return context.request.method + ':' + context.request.url;",
            ),
            DynamicVariable(
                name = "uniqueNonce",
                script = "return String(Date.now()) + '-' + Math.random().toString(36).substring(2, 10);",
            ),
        )

        val variableLayers = listOf(mapOf("baseUrl" to baseUrl))

        val request = RequestDefinition(
            id = "req-1",
            name = "Create User with Dynamic Vars",
            method = HttpMethodType.POST,
            url = "{{baseUrl}}/users",
            headers = listOf(
                KeyValueEntry("X-Expires-At", "{{secondsFromNow(10)}}"),
                KeyValueEntry("X-Default-Expires", "{{secondsFromNow}}"),
                KeyValueEntry("X-Tag", "{{formatTag(\"ORD\", 42)}}"),
                KeyValueEntry("X-Signature", "{{requestSig}}"),
                KeyValueEntry("X-Nonce", "{{uniqueNonce}}"),
            ),
            body = RequestBody(
                type = BodyType.JSON,
                content = """{"name":"Alice","email":"alice@example.com"}""",
            ),
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
        )

        // Execute twice and verify dynamic variables re-evaluate per request
        val events1 = service.execute(
            request = request,
            variableLayers = variableLayers,
            userDynamicVariables = userDynamicVariables,
        ).toList()
        assertIs<NetworkEvent.Success>(events1.last())

        val events2 = service.execute(
            request = request,
            variableLayers = variableLayers,
            userDynamicVariables = userDynamicVariables,
        ).toList()
        assertIs<NetworkEvent.Success>(events2.last())

        val loggedRequests = server.requestsLog.value.takeLast(2)
        assertEquals(2, loggedRequests.size)

        val firstReq = loggedRequests[0]
        val secondReq = loggedRequests[1]

        assertEquals("1700000010", firstReq.headers["X-Expires-At"])
        assertEquals("1700000005", firstReq.headers["X-Default-Expires"])
        assertEquals("ORD-42", firstReq.headers["X-Tag"])
        assertEquals("POST:$baseUrl/users", firstReq.headers["X-Signature"])

        // Each request execution must evaluate the script fresh
        val nonce1 = firstReq.headers["X-Nonce"]
        val nonce2 = secondReq.headers["X-Nonce"]
        assertTrue(!nonce1.isNullOrBlank())
        assertTrue(!nonce2.isNullOrBlank())
        assertNotEquals(nonce1, nonce2, "Dynamic variable must evaluate fresh per request")
    }

    @Test
    fun failingDynamicVariableScriptHaltsRequestExecutionWithClearError() = runTest {
        val client = KtorApiClient(retryPolicy = RetryPolicy(maxAttempts = 1))
        val service = RequestExecutionService(client)

        val userDynamicVariables = listOf(
            DynamicVariable(
                name = "brokenToken",
                script = "throw new Error('Token generator failed');",
            ),
        )

        val initialServerHitCount = server.requestsLog.value.size

        val request = RequestDefinition(
            id = "req-fail",
            name = "Failing Request",
            method = HttpMethodType.GET,
            url = "{{baseUrl}}/users",
            headers = listOf(
                KeyValueEntry("Authorization", "Bearer {{brokenToken}}"),
            ),
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
        )

        val ex = assertFailsWith<DynamicVariableResolutionException> {
            service.execute(
                request = request,
                variableLayers = listOf(mapOf("baseUrl" to baseUrl)),
                userDynamicVariables = userDynamicVariables,
            ).toList()
        }

        assertTrue(ex.message!!.contains("brokenToken"))
        assertTrue(ex.message!!.contains("Token generator failed"))
        assertEquals(initialServerHitCount, server.requestsLog.value.size, "Server must not be called when dynamic variable throws")
    }

    @Test
    fun openCollectionJsonAndYamlRoundTripPreservesDynamicVariables() = runTest {
        val repo = DefaultCanonicalCollectionRepository(baseDirectory = tempDir.root.absolutePath)

        val doc = OpenCollectionDocument(
            info = CollectionInfo(name = "Dynamic RoundTrip"),
            config = CollectionConfig(
                environments = listOf(
                    Environment(
                        name = "Staging",
                        variables = listOf(
                            RegularVariable(name = "host", value = VariableValue.of("https://staging.example.com")),
                            DynamicVariable(
                                name = "secondsFromNow",
                                script = "return new Date(Date.now() + Number(seconds) * 1000).toISOString();",
                                parameters = listOf(
                                    DynamicVariableParameter(
                                        name = "seconds",
                                        defaultValue = "10",
                                        description = "Offset in seconds",
                                    )
                                ),
                                description = Description("ISO timestamp offset"),
                            ),
                        ),
                    )
                )
            ),
        )

        val exportedJson = repo.exportCollection(doc, ExportFormat.JSON)
        assertTrue(exportedJson.contains("\"secondsFromNow\""))
        assertTrue(exportedJson.contains("\"script\""))

        val reimportedFromJson = repo.importCollection(exportedJson)
        val jsonVars = reimportedFromJson.config?.environments?.first()?.variables.orEmpty()
        assertEquals(2, jsonVars.size)
        val dynFromJson = assertIs<DynamicVariable>(jsonVars[1])
        assertEquals("secondsFromNow", dynFromJson.name)
        assertEquals(1, dynFromJson.parameters.size)
        assertEquals("seconds", dynFromJson.parameters.first().name)
        assertEquals("10", dynFromJson.parameters.first().defaultValue)

        val exportedYaml = repo.exportCollection(reimportedFromJson, ExportFormat.YAML)
        assertTrue(exportedYaml.contains("secondsFromNow"))

        val reimportedFromYaml = repo.importCollection(exportedYaml)
        val yamlVars = reimportedFromYaml.config?.environments?.first()?.variables.orEmpty()
        val dynFromYaml = assertIs<DynamicVariable>(yamlVars[1])
        assertEquals("secondsFromNow", dynFromYaml.name)
        assertEquals(dynFromJson.script, dynFromYaml.script)
        assertEquals(1, dynFromYaml.parameters.size)
        assertEquals("seconds", dynFromYaml.parameters.first().name)
        assertEquals("10", dynFromYaml.parameters.first().defaultValue)
    }

    @Test
    fun executesHttpRequestWithTypedDynamicVariablesInJsonBodySendsNativeJsonTypes() = runTest {
        val client = KtorApiClient(retryPolicy = RetryPolicy(maxAttempts = 1))
        val service = RequestExecutionService(client)

        val userDynamicVariables = listOf(
            DynamicVariable(
                name = "totalCount",
                script = "return 42;",
                type = VariableType.NUMBER,
            ),
            DynamicVariable(
                name = "isPublished",
                script = "return true;",
                type = VariableType.BOOLEAN,
            ),
            DynamicVariable(
                name = "metaInfo",
                script = """return JSON.stringify({ author: "Alice", active: true });""",
                type = VariableType.OBJECT,
            ),
        )

        val variableLayers = listOf(mapOf("baseUrl" to baseUrl))

        val request = RequestDefinition(
            id = "req-typed-body",
            name = "Typed Dynamic Variables in JSON Body",
            method = HttpMethodType.POST,
            url = "{{baseUrl}}/echo-json",
            headers = listOf(
                KeyValueEntry("Content-Type", "application/json"),
            ),
            body = RequestBody(
                type = BodyType.JSON,
                content = """{"count": {{totalCount}}, "published": {{isPublished}}, "meta": {{metaInfo}}}""",
            ),
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
        )

        val events = service.execute(
            request = request,
            variableLayers = variableLayers,
            userDynamicVariables = userDynamicVariables,
        ).toList()

        val successEvent = events.filterIsInstance<NetworkEvent.Success>().first()
        val responseBody = successEvent.response.bodyText
        val responseJson = Json.parseToJsonElement(responseBody).jsonObject

        // Verify the raw wire body arrived as valid unquoted JSON primitives
        val rawWireBody = responseJson["rawBody"]?.jsonPrimitive?.content
        assertEquals("""{"count": 42, "published": true, "meta": {"author":"Alice","active":true}}""", rawWireBody)

        // Verify the server successfully parsed the JSON primitives into native types
        val parsedJson = responseJson["parsed"]?.jsonObject
        assertTrue(parsedJson != null, "Server must have successfully parsed the JSON payload")
        assertEquals("42", parsedJson["count"]?.jsonPrimitive?.content)
        assertEquals("true", parsedJson["published"]?.jsonPrimitive?.content)
        assertEquals("Alice", parsedJson["meta"]?.jsonObject?.get("author")?.jsonPrimitive?.content)
    }
}
