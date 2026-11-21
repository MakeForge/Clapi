package io.github.makeforge.clapi.mcp.server.tools

import io.github.makeforge.clapi.core.model.McpTool
import io.github.makeforge.clapi.core.model.McpToolResult
import io.github.makeforge.clapi.mcp.server.backend.ExportCollectionInput
import io.github.makeforge.clapi.mcp.server.backend.ExportResultDto
import io.github.makeforge.clapi.mcp.server.backend.GitSyncInput
import io.github.makeforge.clapi.mcp.server.backend.GitSyncResultDto
import io.github.makeforge.clapi.mcp.server.backend.ImportSpecInput
import io.github.makeforge.clapi.mcp.server.backend.McpWorkspaceBackend
import io.github.makeforge.clapi.mcp.server.backend.SanitizedCollectionDto
import io.github.makeforge.clapi.mcp.server.tools.WorkspaceTools.okResult
import io.github.makeforge.clapi.mcp.server.tools.WorkspaceTools.optString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object SyncImportExportTools {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    val tools: List<McpTool> = listOf(
        McpTool(
            name = "clapi_import_specification",
            title = "Import API Specification",
            description = "Imports an OpenAPI 3.0+, OpenCollection 1.0.0 (YAML/JSON), or Insomnia v4/v5 specification from rawContent or filePath as a new Clapi collection.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("rawContent", buildJsonObject {
                        put("type", "string")
                        put("description", "Raw YAML or JSON specification content.")
                    })
                    put("filePath", buildJsonObject {
                        put("type", "string")
                        put("description", "Local filesystem path to the specification file.")
                    })
                    put("collectionNameOverride", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional custom name for the imported collection.")
                    })
                })
            },
        ),
        McpTool(
            name = "clapi_export_collection",
            title = "Export Clapi Collection",
            description = "Exports a collection in sanitized OpenCollection (YAML/JSON) or OpenAPI 3.0.3 (YAML/JSON) format with all secret values removed.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("required", buildJsonArray { add(JsonPrimitive("collectionId")) })
                put("properties", buildJsonObject {
                    put("collectionId", buildJsonObject {
                        put("type", "string")
                        put("description", "Collection UUID or name.")
                    })
                    put("format", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add(JsonPrimitive("opencollection-yaml"))
                            add(JsonPrimitive("opencollection-json"))
                            add(JsonPrimitive("openapi-yaml"))
                            add(JsonPrimitive("openapi-json"))
                        })
                        put("description", "Export format: 'opencollection-yaml' (default), 'opencollection-json', 'openapi-yaml', or 'openapi-json'.")
                    })
                })
            },
        ),
        McpTool(
            name = "clapi_git_sync",
            title = "Git Synchronize Collection",
            description = "Performs Git synchronization operations ('status', 'commit', 'pull', or 'push') on a Git-backed collection.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("required", buildJsonArray {
                    add(JsonPrimitive("collectionId"))
                    add(JsonPrimitive("action"))
                })
                put("properties", buildJsonObject {
                    put("collectionId", buildJsonObject {
                        put("type", "string")
                        put("description", "Collection UUID or name.")
                    })
                    put("action", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add(JsonPrimitive("status"))
                            add(JsonPrimitive("commit"))
                            add(JsonPrimitive("pull"))
                            add(JsonPrimitive("push"))
                        })
                        put("description", "Git action: 'status', 'commit', 'pull', or 'push'.")
                    })
                    put("commitMessage", buildJsonObject {
                        put("type", "string")
                        put("description", "Commit message (required when action='commit').")
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
            "clapi_import_specification" -> {
                val input = ImportSpecInput(
                    rawContent = args.optString("rawContent"),
                    filePath = args.optString("filePath"),
                    collectionNameOverride = args.optString("collectionNameOverride"),
                )
                val col = backend.importSpecification(input)
                okResult(json.encodeToJsonElement(SanitizedCollectionDto.serializer(), col))
            }
            "clapi_export_collection" -> {
                val colId = args.optString("collectionId")
                    ?: throw IllegalArgumentException("Missing required argument 'collectionId'.")
                val format = args.optString("format") ?: "opencollection-yaml"
                val res = backend.exportCollection(ExportCollectionInput(collectionId = colId, format = format))
                okResult(json.encodeToJsonElement(ExportResultDto.serializer(), res))
            }
            "clapi_git_sync" -> {
                val colId = args.optString("collectionId")
                    ?: throw IllegalArgumentException("Missing required argument 'collectionId'.")
                val action = args.optString("action")
                    ?: throw IllegalArgumentException("Missing required argument 'action'.")
                val msg = args.optString("commitMessage")
                val res = backend.gitSync(GitSyncInput(collectionId = colId, action = action, commitMessage = msg))
                okResult(json.encodeToJsonElement(GitSyncResultDto.serializer(), res))
            }
            else -> null
        }
    }
}
