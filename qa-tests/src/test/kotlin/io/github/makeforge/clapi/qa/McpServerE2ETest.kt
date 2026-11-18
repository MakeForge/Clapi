package io.github.makeforge.clapi.qa

import io.github.makeforge.clapi.core.model.McpConnectionConfig
import io.github.makeforge.clapi.core.model.McpHttpMode
import io.github.makeforge.clapi.core.network.mcp.McpClient
import io.github.makeforge.clapi.mcp.server.ClapiMcpServer
import io.github.makeforge.clapi.mcp.server.backend.ExecutionResultDto
import io.github.makeforge.clapi.mcp.server.backend.HeadlessWorkspaceBackend
import io.github.makeforge.clapi.mcp.server.backend.InspectResponseResultDto
import io.github.makeforge.clapi.mcp.server.backend.MASKED_SECRET_PLACEHOLDER
import io.github.makeforge.clapi.mcp.server.backend.REDACTED_SECRET_PLACEHOLDER
import io.github.makeforge.clapi.mcp.server.backend.SanitizedEnvironmentDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedRequestDto
import io.github.makeforge.clapi.mcp.server.backend.TestSuiteResultDto
import io.github.makeforge.clapi.mcp.server.transport.KtorMcpServerHost
import io.github.makeforge.clapi.testsupport.DummyApiServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class McpServerE2ETest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var dummyApiServer: DummyApiServer
    private lateinit var mcpHost: KtorMcpServerHost
    private lateinit var httpClient: HttpClient

    @Before
    fun setUp() {
        dummyApiServer = DummyApiServer()
        dummyApiServer.start()

        val backend = HeadlessWorkspaceBackend(baseDirectory = tempFolder.root.absolutePath)
        val clapiMcpServer = ClapiMcpServer(backend = backend)
        mcpHost = KtorMcpServerHost(
            mcpServer = clapiMcpServer,
            host = "127.0.0.1",
            requestedPort = 0,
        )
        mcpHost.start()

        httpClient = HttpClient(CIO) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            }
        }
    }

    @After
    fun tearDown() {
        runCatching { httpClient.close() }
        runCatching { mcpHost.stop() }
        runCatching { dummyApiServer.stop() }
    }

    @Test
    fun `full MCP client-to-server workflow over Streamable HTTP with zero secret exfiltration`() = runBlocking {
        val client = McpClient(this, httpClient, callTimeoutMs = 15_000)
        val initResult = client.connect(
            McpConnectionConfig(
                url = mcpHost.endpointUrl,
                httpMode = McpHttpMode.STREAMABLE_2025_06_18,
            )
        )
        assertEquals("clapi-mcp-server", initResult.serverInfo.name)
        assertNotNull(client.sessionId)

        // 1. Verify all 17 tools are listed
        val tools = client.listTools()
        assertEquals(17, tools.size)

        // 2. Create Environment and write-only Secret Variable + Regular Variable
        val secretToken = "e2e-super-secret-bearer-token-98765"
        client.callTool(
            "clapi_upsert_environment",
            buildJsonObject {
                put("name", "E2E-Prod")
                put("color", "#E91E63")
            }
        )
        client.callTool(
            "clapi_set_variable",
            buildJsonObject {
                put("scope", "environment")
                put("target", "E2E-Prod")
                put("key", "baseUrl")
                put("kind", "regular")
                put("variableType", "string")
                put("value", dummyApiServer.baseUrl())
            }
        )
        val setSecretRes = client.callTool(
            "clapi_set_variable",
            buildJsonObject {
                put("scope", "environment")
                put("target", "E2E-Prod")
                put("key", "apiSecret")
                put("kind", "secret")
                put("variableType", "string")
                put("value", secretToken)
            }
        )
        assertFalse(setSecretRes.isError)
        assertFalse(setSecretRes.content.single().text!!.contains(secretToken))

        client.callTool(
            "clapi_set_active_environment",
            buildJsonObject { put("name", "E2E-Prod") }
        )

        // Read clapi://environments/E2E-Prod resource and verify secret is masked
        val envResource = client.readResource("clapi://environments/E2E-Prod")
        val envDto = json.decodeFromString(SanitizedEnvironmentDto.serializer(), envResource.contents.single().text!!)
        val secretVarDto = envDto.variables.first { it.key == "apiSecret" }
        assertEquals(MASKED_SECRET_PLACEHOLDER, secretVarDto.value)
        assertTrue(secretVarDto.secret)
        assertTrue(secretVarDto.isSet)

        // 3. Create Collection & Author Request pointing to DummyApiServer echo endpoint
        client.callTool(
            "clapi_manage_collection",
            buildJsonObject {
                put("action", "create")
                put("name", "E2E Suite")
            }
        )

        val upsertReqRes = client.callTool(
            "clapi_upsert_request",
            buildJsonObject {
                put("parentId", "E2E Suite")
                put("name", "Echo Headers & Body")
                put("kind", "http")
                put("method", "POST")
                put("url", "{{baseUrl}}/graphql")
                put("headers", buildJsonArray {
                    add(buildJsonObject {
                        put("key", "Authorization")
                        put("value", "Bearer {{apiSecret}}")
                    })
                    add(buildJsonObject {
                        put("key", "Content-Type")
                        put("value", "application/json")
                    })
                })
                put("bodyType", "JSON")
                put("bodyContent", """{"query": "token={{apiSecret}}"}""")
                put(
                    "testScript",
                    """
                    clapi.test("Status is 200", function() {
                        clapi.expect(clapi.response.code).to.equal(200);
                    });
                    """.trimIndent()
                )
            }
        )
        assertFalse(upsertReqRes.isError)
        val createdReq = json.decodeFromString(SanitizedRequestDto.serializer(), upsertReqRes.content.single().text!!)
        assertEquals("E2E Suite/Echo Headers & Body", createdReq.slashPath)

        // 4. Execute Request against DummyApiServer /graphql (which echoes the resolved secret back in $.data.echo!)
        val execRes = client.callTool(
            "clapi_execute_request",
            buildJsonObject {
                put("requestId", "E2E Suite/Echo Headers & Body")
            }
        )
        assertFalse(execRes.isError)
        val execText = execRes.content.single().text!!
        // Verify ZERO exfiltration of secretToken even when echoed by the server
        assertFalse(execText.contains(secretToken))
        assertTrue(execText.contains(REDACTED_SECRET_PLACEHOLDER))
        val execDto = json.decodeFromString(ExecutionResultDto.serializer(), execText)
        assertEquals(200, execDto.statusCode)
        assertTrue(execDto.assertions.all { it.passed })

        // 5. Inspect cached response via JSONPath
        val inspectItemsRes = client.callTool(
            "clapi_inspect_response",
            buildJsonObject {
                put("requestId", createdReq.id)
                put("jsonPath", "$.data.users[1].name")
            }
        )
        val inspectDto = json.decodeFromString(InspectResponseResultDto.serializer(), inspectItemsRes.content.single().text!!)
        assertEquals("Bob", inspectDto.content)

        val inspectTokenRes = client.callTool(
            "clapi_inspect_response",
            buildJsonObject {
                put("requestId", createdReq.id)
                put("jsonPath", "$.data.echo")
            }
        )
        val inspectTokenDto = json.decodeFromString(InspectResponseResultDto.serializer(), inspectTokenRes.content.single().text!!)
        assertEquals("token=$REDACTED_SECRET_PLACEHOLDER", inspectTokenDto.content)

        // 6. Run Test Suite on "E2E Suite"
        val suiteRes = client.callTool(
            "clapi_run_test_suite",
            buildJsonObject {
                put("targetId", "E2E Suite")
            }
        )
        val suiteDto = json.decodeFromString(TestSuiteResultDto.serializer(), suiteRes.content.single().text!!)
        assertEquals(1, suiteDto.totalRequests)
        assertEquals(1, suiteDto.passedRequests)
        assertEquals(1, suiteDto.passedAssertions)

        // 7. Retrieve built-in debug_api_failure prompt
        val promptRes = client.getPrompt(
            "debug_api_failure",
            mapOf("requestId" to "E2E Suite/Echo Headers & Body"),
        )
        val promptBody = promptRes.messages.single().content.text!!
        assertTrue(promptBody.contains("Echo Headers & Body"))
        assertFalse(promptBody.contains(secretToken))

        client.disconnect()
    }
}
