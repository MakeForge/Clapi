package io.github.makeforge.clapi.mcp.server.tools

import io.github.makeforge.clapi.core.model.McpTool
import io.github.makeforge.clapi.core.model.McpToolResult
import io.github.makeforge.clapi.mcp.server.backend.DEFAULT_MAX_BODY_LENGTH
import io.github.makeforge.clapi.mcp.server.backend.ExecuteRequestInput
import io.github.makeforge.clapi.mcp.server.backend.ExecutionResultDto
import io.github.makeforge.clapi.mcp.server.backend.InspectResponseInput
import io.github.makeforge.clapi.mcp.server.backend.InspectResponseResultDto
import io.github.makeforge.clapi.mcp.server.backend.McpWorkspaceBackend
import io.github.makeforge.clapi.mcp.server.backend.RunTestSuiteInput
import io.github.makeforge.clapi.mcp.server.backend.TestSuiteResultDto
import io.github.makeforge.clapi.mcp.server.tools.WorkspaceTools.okResult
import io.github.makeforge.clapi.mcp.server.tools.WorkspaceTools.optBoolean
import io.github.makeforge.clapi.mcp.server.tools.WorkspaceTools.optString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

object ExecutionTools {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    val tools: List<McpTool> = listOf(
        McpTool(
            name = "clapi_execute_request",
            title = "Execute Clapi Request",
            description = "Executes a saved HTTP, gRPC, or GraphQL request with full variable interpolation, pre-request scripts, and post-request test assertions. Returns status, timing metrics, sanitized headers, a budgeted body preview (default 4KB), and test results.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("required", buildJsonArray { add(JsonPrimitive("requestId")) })
                put("properties", buildJsonObject {
                    put("requestId", buildJsonObject {
                        put("type", "string")
                        put("description", "Request UUID or slash path (e.g., 'Collection/Folder/RequestName').")
                    })
                    put("environmentName", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional environment name override for this execution.")
                    })
                    put("transientVariables", buildJsonObject {
                        put("type", "object")
                        put("description", "Optional key-value map of non-persisted variables taking highest precedence for this execution.")
                    })
                    put("maxBodyLength", buildJsonObject {
                        put("type", "integer")
                        put("description", "Maximum UTF-8 bytes returned in bodyPreview (default 4096, clamped to 256..65536).")
                    })
                })
            },
        ),
        McpTool(
            name = "clapi_inspect_response",
            title = "Inspect Cached Response",
            description = "Inspects the cached sanitized response of a previously executed request via a JSONPath query and/or UTF-8 byteOffset and length pagination without re-running the network request.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("required", buildJsonArray { add(JsonPrimitive("requestId")) })
                put("properties", buildJsonObject {
                    put("requestId", buildJsonObject {
                        put("type", "string")
                        put("description", "Request UUID or slash path.")
                    })
                    put("jsonPath", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional JSONPath expression (e.g., '$.data.items[0].id' or '$.users[*].email').")
                    })
                    put("byteOffset", buildJsonObject {
                        put("type", "integer")
                        put("description", "UTF-8 byte offset into the response body or JSONPath result (default 0).")
                    })
                    put("length", buildJsonObject {
                        put("type", "integer")
                        put("description", "Maximum UTF-8 bytes to return (default 4096, clamped to 256..65536).")
                    })
                })
            },
        ),
        McpTool(
            name = "clapi_run_test_suite",
            title = "Run Clapi Test Suite",
            description = "Executes all HTTP, gRPC, and GraphQL requests in a collection or folder sequentially and returns the aggregated test assertion pass/fail matrix.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("required", buildJsonArray { add(JsonPrimitive("targetId")) })
                put("properties", buildJsonObject {
                    put("targetId", buildJsonObject {
                        put("type", "string")
                        put("description", "Collection or folder UUID or slash path.")
                    })
                    put("environmentName", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional environment name override.")
                    })
                    put("stopOnFirstFailure", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Stop executing remaining requests after the first failed request or assertion (default false).")
                    })
                })
            },
        ),
    )

    suspend fun handleToolCall(
        toolName: String,
        args: JsonObject,
        backend: McpWorkspaceBackend,
    ): McpToolResult? {
        return when (toolName) {
            "clapi_execute_request" -> {
                val requestId = args.optString("requestId")
                    ?: throw IllegalArgumentException("Missing required argument 'requestId'.")
                val envName = args.optString("environmentName")
                val transientVars = (args["transientVariables"] as? JsonObject)?.entries?.mapNotNull { (k, v) ->
                    (v as? JsonPrimitive)?.contentOrNull?.let { k to it }
                }?.toMap().orEmpty()
                val maxLen = (args["maxBodyLength"] as? JsonPrimitive)?.intOrNull ?: DEFAULT_MAX_BODY_LENGTH
                val result = backend.executeRequest(
                    ExecuteRequestInput(
                        requestId = requestId,
                        environmentName = envName,
                        transientVariables = transientVars,
                        maxBodyLength = maxLen,
                    )
                )
                okResult(json.encodeToJsonElement(ExecutionResultDto.serializer(), result))
            }
            "clapi_inspect_response" -> {
                val requestId = args.optString("requestId")
                    ?: throw IllegalArgumentException("Missing required argument 'requestId'.")
                val jsonPath = args.optString("jsonPath")
                val byteOffset = (args["byteOffset"] as? JsonPrimitive)?.intOrNull ?: 0
                val length = (args["length"] as? JsonPrimitive)?.intOrNull ?: DEFAULT_MAX_BODY_LENGTH
                val result = backend.inspectResponse(
                    InspectResponseInput(
                        requestId = requestId,
                        jsonPath = jsonPath,
                        byteOffset = byteOffset,
                        length = length,
                    )
                )
                okResult(json.encodeToJsonElement(InspectResponseResultDto.serializer(), result))
            }
            "clapi_run_test_suite" -> {
                val targetId = args.optString("targetId")
                    ?: throw IllegalArgumentException("Missing required argument 'targetId'.")
                val envName = args.optString("environmentName")
                val stopOnFirstFailure = args.optBoolean("stopOnFirstFailure") ?: false
                val result = backend.runTestSuite(
                    RunTestSuiteInput(
                        targetId = targetId,
                        environmentName = envName,
                        stopOnFirstFailure = stopOnFirstFailure,
                    )
                )
                okResult(json.encodeToJsonElement(TestSuiteResultDto.serializer(), result))
            }
            else -> null
        }
    }
}
