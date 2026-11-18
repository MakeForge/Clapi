package io.github.makeforge.clapi.mcp.server.resources

import io.github.makeforge.clapi.core.model.McpReadResourceResult
import io.github.makeforge.clapi.core.model.McpResource
import io.github.makeforge.clapi.core.model.McpResourceContents
import io.github.makeforge.clapi.core.model.McpResourceTemplate
import io.github.makeforge.clapi.mcp.server.backend.EnvironmentsOverviewDto
import io.github.makeforge.clapi.mcp.server.backend.ExecutionResultDto
import io.github.makeforge.clapi.mcp.server.backend.McpWorkspaceBackend
import io.github.makeforge.clapi.mcp.server.backend.NodeNotFoundException
import io.github.makeforge.clapi.mcp.server.backend.SanitizedCollectionDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedConsoleEntryDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedEnvironmentDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedRequestDto
import io.github.makeforge.clapi.mcp.server.backend.WorkspaceTreeNodeDto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object McpResourceRegistry {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    val staticResources: List<McpResource> = listOf(
        McpResource(
            uri = "clapi://tree",
            name = "Workspace Tree",
            title = "Clapi Workspace Tree",
            description = "Hierarchical summary of all collections, folders, and requests (UUIDs, names, types, HTTP methods, and slash paths).",
            mimeType = "application/json",
        ),
        McpResource(
            uri = "clapi://environments",
            name = "Environments Overview",
            title = "Clapi Environments & Global Variables",
            description = "Active environment selection, sanitized global variables, and list of environments with inheritance metadata.",
            mimeType = "application/json",
        ),
        McpResource(
            uri = "clapi://logs/console",
            name = "Script & Execution Console Logs",
            title = "Clapi Console Logs",
            description = "Recent sanitized pre-request and post-request script console logs and execution diagnostics.",
            mimeType = "application/json",
        ),
    )

    val resourceTemplates: List<McpResourceTemplate> = listOf(
        McpResourceTemplate(
            uriTemplate = "clapi://collections/{collectionId}",
            name = "Collection Details",
            title = "Clapi Collection Details",
            description = "Sanitized collection structure, default auth, headers, variables, scripts, and protobuf configuration by collection UUID or name.",
            mimeType = "application/json",
        ),
        McpResourceTemplate(
            uriTemplate = "clapi://requests/{requestId}",
            name = "Request Details",
            title = "Clapi Request Definition",
            description = "Complete sanitized request definition (HTTP, gRPC, GraphQL, or WebSocket) by request UUID or slash path.",
            mimeType = "application/json",
        ),
        McpResourceTemplate(
            uriTemplate = "clapi://environments/{envName}",
            name = "Environment Projection",
            title = "Clapi Environment Effective Variables",
            description = "Effective resolved non-secret variables (with inheritance and override metadata) and external secret declarations for an environment.",
            mimeType = "application/json",
        ),
        McpResourceTemplate(
            uriTemplate = "clapi://responses/{requestId}/latest",
            name = "Latest Execution Snapshot",
            title = "Latest Request Execution Response",
            description = "Latest cached execution response snapshot for a request (sanitized headers, truncated body preview, timing metrics, and test assertion results).",
            mimeType = "application/json",
        ),
    )

    suspend fun readResource(
        uri: String,
        backend: McpWorkspaceBackend,
    ): McpReadResourceResult {
        val trimmed = uri.trim()
        val payloadText: String = when {
            trimmed == "clapi://tree" -> {
                val tree = backend.listTree()
                json.encodeToString(ListSerializer(WorkspaceTreeNodeDto.serializer()), tree)
            }
            trimmed == "clapi://environments" -> {
                val envs = backend.listEnvironments(includeVariables = true)
                json.encodeToString(EnvironmentsOverviewDto.serializer(), envs)
            }
            trimmed == "clapi://logs/console" -> {
                val logs = backend.getConsoleLogs(limit = 100)
                json.encodeToString(ListSerializer(SanitizedConsoleEntryDto.serializer()), logs)
            }
            trimmed.startsWith("clapi://collections/") -> {
                val rawId = decodeUriParam(trimmed.removePrefix("clapi://collections/"))
                val col = backend.getCollection(rawId)
                json.encodeToString(SanitizedCollectionDto.serializer(), col)
            }
            trimmed.startsWith("clapi://requests/") -> {
                val rawId = decodeUriParam(trimmed.removePrefix("clapi://requests/"))
                val req = backend.getRequest(rawId)
                json.encodeToString(SanitizedRequestDto.serializer(), req)
            }
            trimmed.startsWith("clapi://environments/") -> {
                val envName = decodeUriParam(trimmed.removePrefix("clapi://environments/"))
                val env = backend.getEnvironmentDetails(envName)
                json.encodeToString(SanitizedEnvironmentDto.serializer(), env)
            }
            trimmed.startsWith("clapi://responses/") && trimmed.endsWith("/latest") -> {
                val rawId = decodeUriParam(
                    trimmed.removePrefix("clapi://responses/").removeSuffix("/latest")
                )
                val latest = backend.getLatestResponse(rawId)
                    ?: throw NodeNotFoundException(rawId, "cached response snapshot")
                json.encodeToString(ExecutionResultDto.serializer(), latest)
            }
            else -> throw IllegalArgumentException("Unknown MCP resource URI: '$uri'")
        }

        return McpReadResourceResult(
            contents = listOf(
                McpResourceContents(
                    uri = trimmed,
                    mimeType = "application/json",
                    text = payloadText,
                )
            )
        )
    }

    private fun decodeUriParam(raw: String): String =
        runCatching {
            URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
        }.getOrDefault(raw)
}
