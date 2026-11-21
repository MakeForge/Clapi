package io.github.makeforge.clapi.mcp.server

import io.github.makeforge.clapi.core.model.McpGetPromptResult
import io.github.makeforge.clapi.core.model.McpInitializeResult
import io.github.makeforge.clapi.core.model.McpListPromptsResult
import io.github.makeforge.clapi.core.model.McpListResourceTemplatesResult
import io.github.makeforge.clapi.core.model.McpListResourcesResult
import io.github.makeforge.clapi.core.model.McpListToolsResult
import io.github.makeforge.clapi.mcp.server.backend.HeadlessWorkspaceBackend
import io.github.makeforge.clapi.mcp.server.backend.ManageCollectionInput
import io.github.makeforge.clapi.mcp.server.backend.UpsertRequestInput
import io.github.makeforge.clapi.mcp.server.transport.StdioMcpServerRunner
import io.github.makeforge.clapi.mcp.server.transport.installClapiMcpRoutes
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClapiMcpServerProtocolTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `exposes all 18 tools 3 static resources 4 resource templates and 3 prompts`() = runTest {
        val backend = HeadlessWorkspaceBackend()
        backend.manageCollection(ManageCollectionInput(action = "create", name = "Demo"))
        val req = backend.upsertRequest(
            UpsertRequestInput(
                parentId = "Demo",
                name = "Get Users",
                kind = "http",
                method = "GET",
                url = "https://api.example.com/users",
            )
        )
        val server = ClapiMcpServer(backend = backend)

        // 1. Initialize
        val initResp = server.handleJsonRpc(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}"""
        )
        val initRes = json.decodeFromJsonElement(McpInitializeResult.serializer(), initResp!!["result"]!!)
        assertEquals("2025-06-18", initRes.protocolVersion)
        assertNotNull(initRes.capabilities.tools)
        assertNotNull(initRes.capabilities.resources)
        assertNotNull(initRes.capabilities.prompts)

        // 2. Tools list (18 tools including clapi_subscribe_rabbitmq)
        val toolsResp = server.handleJsonRpc("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")
        val toolsRes = json.decodeFromJsonElement(McpListToolsResult.serializer(), toolsResp!!["result"]!!)
        assertEquals(18, toolsRes.tools.size)

        // 3. Static resources & templates
        val resResp = server.handleJsonRpc("""{"jsonrpc":"2.0","id":3,"method":"resources/list"}""")
        val resList = json.decodeFromJsonElement(McpListResourcesResult.serializer(), resResp!!["result"]!!)
        assertEquals(3, resList.resources.size)

        val tplResp = server.handleJsonRpc("""{"jsonrpc":"2.0","id":4,"method":"resources/templates/list"}""")
        val tplList = json.decodeFromJsonElement(McpListResourceTemplatesResult.serializer(), tplResp!!["result"]!!)
        assertEquals(4, tplList.resourceTemplates.size)

        // 4. Prompts list & get
        val promptsResp = server.handleJsonRpc("""{"jsonrpc":"2.0","id":5,"method":"prompts/list"}""")
        val promptsList = json.decodeFromJsonElement(McpListPromptsResult.serializer(), promptsResp!!["result"]!!)
        assertEquals(3, promptsList.prompts.size)

        for (promptName in listOf("debug_api_failure", "generate_api_tests")) {
            val getPromptResp = server.handleJsonRpc(
                buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", 6)
                    put("method", "prompts/get")
                    put("params", buildJsonObject {
                        put("name", promptName)
                        put("arguments", buildJsonObject {
                            put("requestId", req.id)
                        })
                    })
                }.toString()
            )
            val promptResult = json.decodeFromJsonElement(McpGetPromptResult.serializer(), getPromptResp!!["result"]!!)
            assertTrue(promptResult.messages.isNotEmpty())
            assertTrue(promptResult.messages.first().content.text!!.contains("Get Users"))
        }

        val curlPromptResp = server.handleJsonRpc(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 7)
                put("method", "prompts/get")
                put("params", buildJsonObject {
                    put("name", "curl_to_clapi_request")
                    put("arguments", buildJsonObject {
                        put("curlCommand", "curl -X POST https://api.example.com/items -d '{\"a\":1}'")
                        put("targetCollection", "Demo")
                    })
                })
            }.toString()
        )
        val curlPrompt = json.decodeFromJsonElement(McpGetPromptResult.serializer(), curlPromptResp!!["result"]!!)
        assertTrue(curlPrompt.messages.first().content.text!!.contains("clapi_upsert_request"))
    }

    @Test
    fun `stdio stream loop frames newline delimited JSON-RPC cleanly`() = runTest {
        val backend = HeadlessWorkspaceBackend()
        val server = ClapiMcpServer(backend = backend)

        val inputLines = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}""",
            """{"jsonrpc":"2.0","method":"notifications/initialized"}""",
            """{"jsonrpc":"2.0","id":2,"method":"ping"}""",
        ).joinToString("\n") + "\n"

        val input = ByteArrayInputStream(inputLines.toByteArray(StandardCharsets.UTF_8))
        val output = ByteArrayOutputStream()

        StdioMcpServerRunner.runStreamLoop(input, output, server)

        val outLines = output.toString(StandardCharsets.UTF_8.name())
            .lines()
            .filter { it.isNotBlank() }
        // Only 2 responses (notification produces no response line)
        assertEquals(2, outLines.size)
        val line1 = json.parseToJsonElement(outLines[0]).jsonObject
        val line2 = json.parseToJsonElement(outLines[1]).jsonObject
        assertNotNull(line1["result"])
        assertNotNull(line2["result"])
    }

    @Test
    fun `Ktor MCP route supports Streamable HTTP session lifecycle`() = testApplication {
        val backend = HeadlessWorkspaceBackend()
        val mcpServer = ClapiMcpServer(backend = backend)
        application {
            routing {
                installClapiMcpRoutes(mcpServer)
            }
        }

        // 1. Initialize and receive Mcp-Session-Id header
        val initHttpResp = client.post("/mcp") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}""")
        }
        assertEquals(HttpStatusCode.OK, initHttpResp.status)
        val sessionId = initHttpResp.headers["Mcp-Session-Id"]
        assertNotNull(sessionId)

        // 2. Notification returns 202 Accepted
        val notifResp = client.post("/mcp") {
            header("Mcp-Session-Id", sessionId)
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")
        }
        assertEquals(HttpStatusCode.Accepted, notifResp.status)

        // 3. Call tools/list within session
        val toolsHttpResp = client.post("/mcp") {
            header("Mcp-Session-Id", sessionId)
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")
        }
        assertEquals(HttpStatusCode.OK, toolsHttpResp.status)
        assertTrue(toolsHttpResp.bodyAsText().contains("clapi_execute_request"))

        // 4. Delete session
        val delResp = client.delete("/mcp") {
            header("Mcp-Session-Id", sessionId)
        }
        assertEquals(HttpStatusCode.NoContent, delResp.status)

        // Subsequent call with deleted session returns 404
        val afterDelResp = client.post("/mcp") {
            header("Mcp-Session-Id", sessionId)
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc":"2.0","id":3,"method":"ping"}""")
        }
        assertEquals(HttpStatusCode.NotFound, afterDelResp.status)
    }
}
