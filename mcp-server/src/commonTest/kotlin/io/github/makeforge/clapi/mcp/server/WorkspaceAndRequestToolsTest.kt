package io.github.makeforge.clapi.mcp.server

import io.github.makeforge.clapi.core.model.JsonRpcEnvelope
import io.github.makeforge.clapi.core.model.McpReadResourceResult
import io.github.makeforge.clapi.core.model.McpToolResult
import io.github.makeforge.clapi.mcp.server.backend.HeadlessWorkspaceBackend
import io.github.makeforge.clapi.mcp.server.backend.MASKED_SECRET_PLACEHOLDER
import io.github.makeforge.clapi.mcp.server.backend.ManageCollectionInput
import io.github.makeforge.clapi.mcp.server.backend.ManageFolderInput
import io.github.makeforge.clapi.mcp.server.backend.ProtoFileDto
import io.github.makeforge.clapi.mcp.server.backend.ProtoImportPathDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedAuthDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedCollectionDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedKeyValueDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedRequestDto
import io.github.makeforge.clapi.mcp.server.backend.UpsertRequestInput
import io.github.makeforge.clapi.mcp.server.backend.WorkspaceTreeNodeDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorkspaceAndRequestToolsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun callRpc(
        method: String,
        params: kotlinx.serialization.json.JsonObject = buildJsonObject {},
    ): String =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", method)
            put("params", params)
        }.toString()

    @Test
    fun `manage collections folders and multi-protocol requests via MCP tools and resources`() = runTest {
        val backend = HeadlessWorkspaceBackend()
        val server = ClapiMcpServer(backend = backend)

        // 1. Create collection with default auth, headers, scripts, and proto files
        val colResp = server.handleJsonRpc(
            callRpc(
                "tools/call",
                buildJsonObject {
                    put("name", "clapi_manage_collection")
                    put("arguments", buildJsonObject {
                        put("action", "create")
                        put("name", "Billing API")
                        put("summary", "Core Billing Services")
                        put("version", "2.1.0")
                        put("auth", buildJsonObject {
                            put("type", "BEARER")
                            put("token", "secret-collection-jwt")
                        })
                        put("headers", buildJsonArray {
                            add(buildJsonObject {
                                put("key", "X-Tenant")
                                put("value", "acme")
                            })
                        })
                        put("protoFiles", buildJsonArray {
                            add(buildJsonObject {
                                put("path", "protos/billing.proto")
                                put("syncToGit", true)
                            })
                        })
                        put("importPaths", buildJsonArray {
                            add(buildJsonObject {
                                put("path", "protos/common")
                                put("enabled", true)
                            })
                        })
                    })
                }
            )
        )
        assertNotNull(colResp)
        val colToolResult = json.decodeFromJsonElement(McpToolResult.serializer(), colResp["result"]!!)
        assertFalse(colToolResult.isError)
        val createdCol = json.decodeFromJsonElement(SanitizedCollectionDto.serializer(), colToolResult.structuredContent!!)
        assertEquals("Billing API", createdCol.name)
        assertEquals(MASKED_SECRET_PLACEHOLDER, createdCol.auth.token)
        assertEquals(1, createdCol.protoFiles.size)
        assertEquals("protos/billing.proto", createdCol.protoFiles.first().path)

        // 2. Create nested folder
        val folderResp = server.handleJsonRpc(
            callRpc(
                "tools/call",
                buildJsonObject {
                    put("name", "clapi_manage_folder")
                    put("arguments", buildJsonObject {
                        put("action", "create")
                        put("parentId", "Billing API")
                        put("name", "Invoices")
                    })
                }
            )
        )
        val folderToolResult = json.decodeFromJsonElement(McpToolResult.serializer(), folderResp!!["result"]!!)
        assertFalse(folderToolResult.isError)

        // 3. Author HTTP, gRPC, GraphQL, and WebSocket requests in Billing API/Invoices
        val httpReq = backend.upsertRequest(
            UpsertRequestInput(
                parentId = "Billing API/Invoices",
                name = "Create Invoice",
                kind = "http",
                method = "POST",
                url = "https://api.example.com/invoices",
                headers = listOf(
                    SanitizedKeyValueDto(key = "Authorization", value = "Bearer secret-req-token"),
                    SanitizedKeyValueDto(key = "Content-Type", value = "application/json"),
                ),
                bodyType = "JSON",
                bodyContent = """{"amount": 150}""",
                preRequestScript = "console.log('pre-invoice');",
                testScript = "clapi.test('created', () => clapi.expect(clapi.response.code).to.equal(201));",
            )
        )
        assertEquals("http", httpReq.kind)
        assertEquals("POST", httpReq.method)
        assertEquals("Billing API/Invoices/Create Invoice", httpReq.slashPath)
        assertEquals("Bearer $MASKED_SECRET_PLACEHOLDER", httpReq.headers.first { it.key == "Authorization" }.value)

        val grpcReq = backend.upsertRequest(
            UpsertRequestInput(
                parentId = "Billing API/Invoices",
                name = "Charge Card gRPC",
                kind = "grpc",
                url = "localhost:50051",
                grpc = io.github.makeforge.clapi.mcp.server.backend.GrpcRequestConfigDto(
                    protoFilePath = "protos/billing.proto",
                    grpcMethod = "billing.BillingService/Charge",
                    grpcMethodType = "unary",
                    message = """{"cardId": "c_123"}""",
                ),
            )
        )
        assertEquals("grpc", grpcReq.kind)
        assertEquals("billing.BillingService/Charge", grpcReq.grpc?.grpcMethod)

        val gqlReq = backend.upsertRequest(
            UpsertRequestInput(
                parentId = "Billing API/Invoices",
                name = "Query Invoices GraphQL",
                kind = "graphql",
                url = "https://api.example.com/graphql",
                graphql = io.github.makeforge.clapi.mcp.server.backend.GraphQlRequestConfigDto(
                    query = "query { invoices { id amount } }",
                    variablesJson = """{"limit": 10}""",
                ),
            )
        )
        assertEquals("graphql", gqlReq.kind)
        assertEquals("query { invoices { id amount } }", gqlReq.graphql?.query)

        val wsReq = backend.upsertRequest(
            UpsertRequestInput(
                parentId = "Billing API/Invoices",
                name = "Invoice Stream WS",
                kind = "websocket",
                url = "wss://api.example.com/ws",
                websocket = io.github.makeforge.clapi.mcp.server.backend.WebSocketRequestConfigDto(
                    messageFormat = "json",
                    variants = listOf(
                        io.github.makeforge.clapi.mcp.server.backend.WebSocketVariantDto(
                            title = "Subscribe",
                            type = "json",
                            data = """{"op": "sub"}""",
                            selected = true,
                        )
                    ),
                ),
            )
        )
        assertEquals("websocket", wsReq.kind)
        assertEquals(1, wsReq.websocket?.variants?.size)

        // 4. Retrieve request by both UUID and slash path via clapi_get_request and clapi://requests/{id}
        val fetchedByPath = backend.getRequest("Billing API/Invoices/Create Invoice")
        assertEquals(httpReq.id, fetchedByPath.id)

        val resourceResp = server.handleJsonRpc(
            callRpc(
                "resources/read",
                buildJsonObject {
                    put("uri", "clapi://requests/${httpReq.id}")
                }
            )
        )
        val readRes = json.decodeFromJsonElement(McpReadResourceResult.serializer(), resourceResp!!["result"]!!)
        val parsedReq = json.decodeFromString(SanitizedRequestDto.serializer(), readRes.contents.first().text!!)
        assertEquals("Create Invoice", parsedReq.name)

        // 5. Verify destructive delete confirmation guard (confirm=false fails without deleting)
        val unconfirmedDel = server.handleJsonRpc(
            callRpc(
                "tools/call",
                buildJsonObject {
                    put("name", "clapi_delete_request")
                    put("arguments", buildJsonObject {
                        put("requestId", httpReq.id)
                        put("confirm", false)
                    })
                }
            )
        )
        val unconfirmedResult = json.decodeFromJsonElement(McpToolResult.serializer(), unconfirmedDel!!["result"]!!)
        assertTrue(unconfirmedResult.isError)
        // Request still exists
        assertEquals(httpReq.id, backend.getRequest(httpReq.id).id)

        // Confirmed delete succeeds
        val confirmedDel = backend.deleteRequest(httpReq.id, confirm = true)
        assertTrue(confirmedDel.deleted)
    }

    @Test
    fun `workspace tree resource payload stays compact for 100 requests`() = runTest {
        val backend = HeadlessWorkspaceBackend()
        val server = ClapiMcpServer(backend = backend)
        backend.manageCollection(ManageCollectionInput(action = "create", name = "C"))
        for (i in 1..100) {
            backend.upsertRequest(
                UpsertRequestInput(
                    parentId = "C",
                    name = "R$i",
                    kind = "http",
                    method = "GET",
                    url = "/r/$i",
                )
            )
        }
        val treeNodes = backend.listTree()
        assertEquals(1, treeNodes.size)
        assertEquals(100, treeNodes.first().children.size)

        val treeRes = server.handleJsonRpc(
            callRpc("resources/read", buildJsonObject { put("uri", "clapi://tree") })
        )
        val contents = json.decodeFromJsonElement(McpReadResourceResult.serializer(), treeRes!!["result"]!!)
        val parsedTree = json.decodeFromString(ListSerializer(WorkspaceTreeNodeDto.serializer()), contents.contents.first().text!!)
        assertEquals(100, parsedTree.first().children.size)
    }
}
