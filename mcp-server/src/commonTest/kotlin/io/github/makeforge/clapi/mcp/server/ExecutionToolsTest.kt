package io.github.makeforge.clapi.mcp.server

import io.github.makeforge.clapi.core.model.KeyValueEntry
import io.github.makeforge.clapi.core.model.McpReadResourceResult
import io.github.makeforge.clapi.core.model.RequestDefinition
import io.github.makeforge.clapi.core.model.ResponseDefinition
import io.github.makeforge.clapi.core.model.ResponseMetrics
import io.github.makeforge.clapi.core.network.ApiClient
import io.github.makeforge.clapi.core.network.NetworkEvent
import io.github.makeforge.clapi.core.network.VariableResolver
import io.github.makeforge.clapi.mcp.server.backend.ExecuteRequestInput
import io.github.makeforge.clapi.mcp.server.backend.HeadlessWorkspaceBackend
import io.github.makeforge.clapi.mcp.server.backend.InspectResponseInput
import io.github.makeforge.clapi.mcp.server.backend.ManageCollectionInput
import io.github.makeforge.clapi.mcp.server.backend.RunTestSuiteInput
import io.github.makeforge.clapi.mcp.server.backend.SanitizedConsoleEntryDto
import io.github.makeforge.clapi.mcp.server.backend.UpsertRequestInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExecutionToolsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private class FakeApiClient(
        private val handler: (RequestDefinition, List<Map<String, String>>) -> ResponseDefinition
    ) : ApiClient {
        override fun execute(
            request: RequestDefinition,
            variableLayers: List<Map<String, String>>,
        ): Flow<NetworkEvent> = flow {
            emit(NetworkEvent.Started(request.id, 1000L))
            emit(NetworkEvent.Success(handler(request, variableLayers)))
        }
    }

    @Test
    fun `executes request with scripts truncates large responses and supports JSONPath inspection and test suites`() = runTest {
        val largeItemsJson = buildString {
            append("""{"items":[""")
            for (i in 0 until 200) {
                if (i > 0) append(",")
                append("""{"id":$i,"title":"ItemNumber-$i-${"x".repeat(30)}"}""")
            }
            append("""],"envUsed":""")
        }

        val fakeClient = FakeApiClient { req, layers ->
            val resolvedUrl = VariableResolver.resolve(req.url, layers, removeUnresolved = false)
            val isBinary = resolvedUrl.endsWith("/binary")
            val body = if (isBinary) {
                "raw-binary-bytes"
            } else {
                val envVal = VariableResolver.resolve("{{targetEnv}}", layers, removeUnresolved = false)
                "$largeItemsJson\"$envVal\"}"
            }
            ResponseDefinition(
                requestId = req.id,
                statusCode = 200,
                statusText = "OK",
                headers = listOf(KeyValueEntry("Content-Type", if (isBinary) "image/png" else "application/json")),
                cookies = emptyList(),
                bodyText = body,
                contentType = if (isBinary) "image/png" else "application/json",
                executedAtEpochMillis = 123456789L,
                metrics = ResponseMetrics(
                    statusCode = 200,
                    responseTimeMs = 15L,
                    responseSizeBytes = body.encodeToByteArray().size.toLong(),
                ),
            )
        }

        val backend = HeadlessWorkspaceBackend(apiClient = fakeClient)
        val server = ClapiMcpServer(backend = backend)

        backend.manageCollection(ManageCollectionInput(action = "create", name = "Catalog API"))

        val listItemsReq = backend.upsertRequest(
            UpsertRequestInput(
                parentId = "Catalog API",
                name = "List Items",
                kind = "http",
                method = "GET",
                url = "https://api.example.com/items",
                preRequestScript = "console.log('Executing List Items pre-request script');",
                testScript = """
                    clapi.test("Status is 200", function() {
                        clapi.expect(clapi.response.code).to.equal(200);
                    });
                    clapi.test("Has 200 items", function() {
                        var data = clapi.response.json();
                        clapi.expect(data.items.length).to.equal(200);
                    });
                """.trimIndent(),
            )
        )

        val binaryReq = backend.upsertRequest(
            UpsertRequestInput(
                parentId = "Catalog API",
                name = "Download Logo",
                kind = "http",
                method = "GET",
                url = "https://api.example.com/binary",
            )
        )

        // 1. Execute request with transient variable and default 4KB budget
        val execResult = backend.executeRequest(
            ExecuteRequestInput(
                requestId = "Catalog API/List Items",
                transientVariables = mapOf("targetEnv" to "staging-override"),
                maxBodyLength = 4096,
            )
        )
        assertEquals(200, execResult.statusCode)
        assertTrue(execResult._truncated)
        assertFalse(execResult._binary)
        assertTrue(execResult._totalBytes > 4096)
        assertEquals(4096, execResult.bodyPreview.encodeToByteArray().size)
        assertNotNull(execResult._hint)
        assertEquals(2, execResult.assertions.size)
        assertTrue(execResult.assertions.all { it.passed })

        // 2. Inspect cached response via JSONPath without re-executing
        val inspectedItem = backend.inspectResponse(
            InspectResponseInput(
                requestId = listItemsReq.id,
                jsonPath = "$.items[199].id",
            )
        )
        assertEquals("199", inspectedItem.content)

        val inspectedEnv = backend.inspectResponse(
            InspectResponseInput(
                requestId = "Catalog API/List Items",
                jsonPath = "$.envUsed",
            )
        )
        assertEquals("staging-override", inspectedEnv.content)

        // 3. Execute binary request and verify binary summarization
        val binResult = backend.executeRequest(ExecuteRequestInput(requestId = binaryReq.id))
        assertTrue(binResult._binary)
        assertFalse(binResult._truncated)
        assertTrue(binResult.bodyPreview.contains("Binary payload: image/png"))

        // 4. Read clapi://responses/{requestId}/latest and clapi://logs/console resources
        val latestRes = server.handleJsonRpc(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 10)
                put("method", "resources/read")
                put("params", buildJsonObject {
                    put("uri", "clapi://responses/${listItemsReq.id}/latest")
                })
            }.toString()
        )
        val latestContents = json.decodeFromJsonElement(McpReadResourceResult.serializer(), latestRes!!["result"]!!)
        assertTrue(latestContents.contents.first().text!!.contains("\"_truncated\": true"))

        val logsRes = server.handleJsonRpc(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 11)
                put("method", "resources/read")
                put("params", buildJsonObject {
                    put("uri", "clapi://logs/console")
                })
            }.toString()
        )
        val logsContents = json.decodeFromJsonElement(McpReadResourceResult.serializer(), logsRes!!["result"]!!)
        val logsList = json.decodeFromString(ListSerializer(SanitizedConsoleEntryDto.serializer()), logsContents.contents.first().text!!)
        assertTrue(logsList.any { it.message.contains("Executing List Items pre-request script") })

        // 5. Run test suite on Catalog API collection
        val suiteResult = backend.runTestSuite(
            RunTestSuiteInput(targetId = "Catalog API")
        )
        assertEquals(2, suiteResult.totalRequests)
        assertEquals(2, suiteResult.passedRequests)
        assertEquals(0, suiteResult.failedRequests)
        assertEquals(2, suiteResult.totalAssertions)
        assertEquals(2, suiteResult.passedAssertions)
    }
}
