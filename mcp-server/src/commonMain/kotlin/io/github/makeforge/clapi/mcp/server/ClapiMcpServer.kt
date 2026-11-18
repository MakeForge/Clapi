package io.github.makeforge.clapi.mcp.server

import io.github.makeforge.clapi.core.model.JsonRpcEnvelope
import io.github.makeforge.clapi.core.model.JsonRpcError
import io.github.makeforge.clapi.core.model.JsonRpcErrorCodes
import io.github.makeforge.clapi.core.model.MCP_PROTOCOL_VERSION
import io.github.makeforge.clapi.core.model.MCP_PROTOCOL_VERSION_LEGACY
import io.github.makeforge.clapi.core.model.McpCompleteResult
import io.github.makeforge.clapi.core.model.McpCompletion
import io.github.makeforge.clapi.core.model.McpCompletionsCapability
import io.github.makeforge.clapi.core.model.McpGetPromptResult
import io.github.makeforge.clapi.core.model.McpImplementation
import io.github.makeforge.clapi.core.model.McpInitializeResult
import io.github.makeforge.clapi.core.model.McpListPromptsResult
import io.github.makeforge.clapi.core.model.McpListResourceTemplatesResult
import io.github.makeforge.clapi.core.model.McpListResourcesResult
import io.github.makeforge.clapi.core.model.McpListToolsResult
import io.github.makeforge.clapi.core.model.McpLoggingCapability
import io.github.makeforge.clapi.core.model.McpPrompt
import io.github.makeforge.clapi.core.model.McpPromptsCapability
import io.github.makeforge.clapi.core.model.McpReadResourceResult
import io.github.makeforge.clapi.core.model.McpResourcesCapability
import io.github.makeforge.clapi.core.model.McpServerCapabilities
import io.github.makeforge.clapi.core.model.McpTool
import io.github.makeforge.clapi.core.model.McpToolResult
import io.github.makeforge.clapi.core.model.McpToolsCapability
import io.github.makeforge.clapi.mcp.server.backend.McpWorkspaceBackend
import io.github.makeforge.clapi.mcp.server.prompts.McpPromptRegistry
import io.github.makeforge.clapi.mcp.server.resources.McpResourceRegistry
import io.github.makeforge.clapi.mcp.server.tools.EnvironmentTools
import io.github.makeforge.clapi.mcp.server.tools.ExecutionTools
import io.github.makeforge.clapi.mcp.server.tools.RequestTools
import io.github.makeforge.clapi.mcp.server.tools.SyncImportExportTools
import io.github.makeforge.clapi.mcp.server.tools.WorkspaceTools
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class McpServerSession(
    val id: String = UUID.randomUUID().toString(),
    val subscribedResources: MutableSet<String> = ConcurrentHashMap.newKeySet(),
    var logLevel: String = "info",
)

interface McpToolProvider {
    val tools: List<McpTool>
    suspend fun handleToolCall(toolName: String, args: JsonObject, backend: McpWorkspaceBackend): McpToolResult?
}

interface McpPromptProvider {
    val prompts: List<McpPrompt>
    suspend fun getPrompt(name: String, arguments: Map<String, String>, backend: McpWorkspaceBackend): McpGetPromptResult
}

/**
 * Core JSON-RPC 2.0 MCP Protocol Dispatcher for Clapi.
 *
 * Supports MCP protocol versions `2025-06-18` (default) and `2024-11-05` (legacy),
 * routing requests to [McpWorkspaceBackend], [McpResourceRegistry], tool handlers, and prompt providers.
 */
class ClapiMcpServer(
    val backend: McpWorkspaceBackend,
    val serverName: String = "clapi-mcp-server",
    val serverVersion: String = "1.19.0",
    private val extraToolProviders: List<McpToolProvider> = emptyList(),
    private val promptProvider: McpPromptProvider = McpPromptRegistry,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    val sessions = ConcurrentHashMap<String, McpServerSession>()

    fun getOrCreateSession(sessionId: String? = null): McpServerSession {
        if (!sessionId.isNullOrBlank()) {
            sessions[sessionId]?.let { return it }
        }
        val created = McpServerSession(id = sessionId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString())
        sessions[created.id] = created
        return created
    }

    fun allTools(): List<McpTool> =
        WorkspaceTools.tools +
            RequestTools.tools +
            EnvironmentTools.tools +
            ExecutionTools.tools +
            SyncImportExportTools.tools +
            extraToolProviders.flatMap { it.tools }

    suspend fun handleJsonRpc(
        rawJson: String,
        session: McpServerSession = getOrCreateSession(),
    ): JsonObject? {
        val envelope = try {
            json.decodeFromString(JsonRpcEnvelope.serializer(), rawJson)
        } catch (e: Exception) {
            return encodeError(null, JsonRpcErrorCodes.PARSE_ERROR, "Parse error: ${e.message}")
        }

        val method = envelope.method
        val id = envelope.id
        if (method == null) {
            return if (id != null && id !is JsonNull) {
                encodeError(id, JsonRpcErrorCodes.INVALID_REQUEST, "Invalid JSON-RPC request: missing 'method'")
            } else {
                null
            }
        }

        // Notifications (no id) produce no response
        if (id == null || id is JsonNull) {
            return null
        }

        val params = envelope.params as? JsonObject
        return try {
            when (method) {
                "initialize" -> handleInitialize(id, params)
                "ping" -> encodeResult(id, buildJsonObject {})
                "tools/list" -> handleToolsList(id)
                "tools/call" -> handleToolsCall(id, params)
                "resources/list" -> handleResourcesList(id)
                "resources/templates/list" -> handleResourceTemplatesList(id)
                "resources/read" -> handleResourcesRead(id, params)
                "resources/subscribe" -> {
                    val uri = (params?.get("uri") as? JsonPrimitive)?.contentOrNull.orEmpty()
                    if (uri.isNotBlank()) session.subscribedResources.add(uri)
                    encodeResult(id, buildJsonObject {})
                }
                "resources/unsubscribe" -> {
                    val uri = (params?.get("uri") as? JsonPrimitive)?.contentOrNull.orEmpty()
                    session.subscribedResources.remove(uri)
                    encodeResult(id, buildJsonObject {})
                }
                "prompts/list" -> handlePromptsList(id)
                "prompts/get" -> handlePromptsGet(id, params)
                "completion/complete" -> handleComplete(id, params)
                "logging/setLevel" -> {
                    val level = (params?.get("level") as? JsonPrimitive)?.contentOrNull ?: "info"
                    session.logLevel = level
                    encodeResult(id, buildJsonObject {})
                }
                else -> encodeError(id, JsonRpcErrorCodes.METHOD_NOT_FOUND, "Method not found: $method")
            }
        } catch (e: IllegalArgumentException) {
            encodeError(id, JsonRpcErrorCodes.INVALID_PARAMS, e.message ?: "Invalid parameters")
        } catch (e: Exception) {
            encodeError(id, JsonRpcErrorCodes.INTERNAL_ERROR, e.message ?: "Internal server error")
        }
    }

    private fun handleInitialize(id: JsonElement, params: JsonObject?): JsonObject {
        val requestedVersion = (params?.get("protocolVersion") as? JsonPrimitive)?.contentOrNull
        val negotiatedVersion = if (requestedVersion == MCP_PROTOCOL_VERSION_LEGACY) {
            MCP_PROTOCOL_VERSION_LEGACY
        } else {
            MCP_PROTOCOL_VERSION
        }
        val result = McpInitializeResult(
            protocolVersion = negotiatedVersion,
            capabilities = McpServerCapabilities(
                tools = McpToolsCapability(listChanged = true),
                resources = McpResourcesCapability(subscribe = true, listChanged = true),
                prompts = McpPromptsCapability(listChanged = false),
                logging = McpLoggingCapability(),
                completions = McpCompletionsCapability(),
            ),
            serverInfo = McpImplementation(
                name = serverName,
                version = serverVersion,
                title = "Clapi API Client MCP Server",
            ),
            instructions = "Clapi MCP Server exposing multi-protocol API collections, environments, request execution, response inspection, and zero-exfiltration secret sanitization.",
        )
        return encodeResult(id, json.encodeToJsonElement(McpInitializeResult.serializer(), result))
    }

    private fun handleToolsList(id: JsonElement): JsonObject {
        val result = McpListToolsResult(tools = allTools())
        return encodeResult(id, json.encodeToJsonElement(McpListToolsResult.serializer(), result))
    }

    private suspend fun handleToolsCall(id: JsonElement, params: JsonObject?): JsonObject {
        val toolName = (params?.get("name") as? JsonPrimitive)?.contentOrNull
            ?: return encodeError(id, JsonRpcErrorCodes.INVALID_PARAMS, "Missing required 'name' in tools/call")
        val args = (params["arguments"] as? JsonObject) ?: buildJsonObject {}

        val toolResult: McpToolResult = try {
            WorkspaceTools.handleToolCall(toolName, args, backend)
                ?: RequestTools.handleToolCall(toolName, args, backend)
                ?: EnvironmentTools.handleToolCall(toolName, args, backend)
                ?: ExecutionTools.handleToolCall(toolName, args, backend)
                ?: SyncImportExportTools.handleToolCall(toolName, args, backend)
                ?: extraToolProviders.firstNotNullOfOrNull { it.handleToolCall(toolName, args, backend) }
                ?: return encodeError(id, JsonRpcErrorCodes.INVALID_PARAMS, "Unknown tool: $toolName")
        } catch (e: Exception) {
            WorkspaceTools.errorResult(e.message ?: "Tool execution failed")
        }

        return encodeResult(id, json.encodeToJsonElement(McpToolResult.serializer(), toolResult))
    }

    private fun handleResourcesList(id: JsonElement): JsonObject {
        val result = McpListResourcesResult(resources = McpResourceRegistry.staticResources)
        return encodeResult(id, json.encodeToJsonElement(McpListResourcesResult.serializer(), result))
    }

    private fun handleResourceTemplatesList(id: JsonElement): JsonObject {
        val result = McpListResourceTemplatesResult(resourceTemplates = McpResourceRegistry.resourceTemplates)
        return encodeResult(id, json.encodeToJsonElement(McpListResourceTemplatesResult.serializer(), result))
    }

    private suspend fun handleResourcesRead(id: JsonElement, params: JsonObject?): JsonObject {
        val uri = (params?.get("uri") as? JsonPrimitive)?.contentOrNull
            ?: return encodeError(id, JsonRpcErrorCodes.INVALID_PARAMS, "Missing required 'uri' in resources/read")
        val readResult = McpResourceRegistry.readResource(uri, backend)
        return encodeResult(id, json.encodeToJsonElement(McpReadResourceResult.serializer(), readResult))
    }

    private fun handlePromptsList(id: JsonElement): JsonObject {
        val prompts = promptProvider.prompts
        val result = McpListPromptsResult(prompts = prompts)
        return encodeResult(id, json.encodeToJsonElement(McpListPromptsResult.serializer(), result))
    }

    private suspend fun handlePromptsGet(id: JsonElement, params: JsonObject?): JsonObject {
        val name = (params?.get("name") as? JsonPrimitive)?.contentOrNull
            ?: return encodeError(id, JsonRpcErrorCodes.INVALID_PARAMS, "Missing required 'name' in prompts/get")
        val argsMap = (params["arguments"] as? JsonObject)?.entries?.mapNotNull { (k, v) ->
            (v as? JsonPrimitive)?.contentOrNull?.let { k to it }
        }?.toMap().orEmpty()
        val promptResult = promptProvider.getPrompt(name, argsMap, backend)
        return encodeResult(id, json.encodeToJsonElement(McpGetPromptResult.serializer(), promptResult))
    }

    private suspend fun handleComplete(id: JsonElement, params: JsonObject?): JsonObject {
        val argObj = params?.get("argument") as? JsonObject
        val prefix = (argObj?.get("value") as? JsonPrimitive)?.contentOrNull.orEmpty()
        val tree = runCatching { backend.listTree() }.getOrDefault(emptyList())
        val paths = io.github.makeforge.clapi.mcp.server.backend.NodeLookupResolver.flatten(tree)
            .map { it.slashPath }
            .filter { it.startsWith(prefix, ignoreCase = true) }
            .take(25)
        val result = McpCompleteResult(
            completion = McpCompletion(
                values = paths,
                total = paths.size,
                hasMore = false,
            )
        )
        return encodeResult(id, json.encodeToJsonElement(McpCompleteResult.serializer(), result))
    }

    private fun encodeResult(id: JsonElement, result: JsonElement): JsonObject {
        val env = JsonRpcEnvelope(
            jsonrpc = "2.0",
            id = id,
            result = result,
        )
        return json.encodeToJsonElement(JsonRpcEnvelope.serializer(), env).jsonObject
    }

    private fun encodeError(id: JsonElement?, code: Int, message: String): JsonObject {
        val env = JsonRpcEnvelope(
            jsonrpc = "2.0",
            id = id ?: JsonNull,
            error = JsonRpcError(code = code, message = message),
        )
        return json.encodeToJsonElement(JsonRpcEnvelope.serializer(), env).jsonObject
    }
}
