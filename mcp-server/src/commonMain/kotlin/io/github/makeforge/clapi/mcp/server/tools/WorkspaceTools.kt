package io.github.makeforge.clapi.mcp.server.tools

import io.github.makeforge.clapi.core.model.McpContent
import io.github.makeforge.clapi.core.model.McpTool
import io.github.makeforge.clapi.core.model.McpToolResult
import io.github.makeforge.clapi.mcp.server.backend.DeleteResultDto
import io.github.makeforge.clapi.mcp.server.backend.ManageCollectionInput
import io.github.makeforge.clapi.mcp.server.backend.ManageFolderInput
import io.github.makeforge.clapi.mcp.server.backend.McpWorkspaceBackend
import io.github.makeforge.clapi.mcp.server.backend.ProtoFileDto
import io.github.makeforge.clapi.mcp.server.backend.ProtoImportPathDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedAuthDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedCollectionDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedFolderDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedKeyValueDto
import io.github.makeforge.clapi.mcp.server.backend.WorkspaceTreeNodeDto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object WorkspaceTools {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    val tools: List<McpTool> = listOf(
        McpTool(
            name = "clapi_list_tree",
            title = "List Clapi Workspace Tree",
            description = "Returns a concise hierarchical overview of collections, folders, and requests with UUIDs, HTTP methods, and slash paths.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("collection", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional collection UUID or name filter.")
                    })
                    put("kind", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional request protocol filter: 'http', 'grpc', 'graphql', or 'websocket'.")
                    })
                    put("query", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional case-insensitive search substring on request/folder name or URL.")
                    })
                })
            },
        ),
        McpTool(
            name = "clapi_manage_collection",
            title = "Manage Clapi Collection",
            description = "Creates, updates, or deletes a collection and its collection-level defaults (auth, headers, scripts, and protobuf files/import paths). Deleting requires confirm=true.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("required", buildJsonArray { add(JsonPrimitive("action")) })
                put("properties", buildJsonObject {
                    put("action", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add(JsonPrimitive("create"))
                            add(JsonPrimitive("update"))
                            add(JsonPrimitive("delete"))
                        })
                        put("description", "Action to perform: 'create', 'update', or 'delete'.")
                    })
                    put("collectionId", buildJsonObject {
                        put("type", "string")
                        put("description", "Collection UUID or name (required for 'update' and 'delete').")
                    })
                    put("name", buildJsonObject {
                        put("type", "string")
                        put("description", "Collection display name (required for 'create', optional rename for 'update').")
                    })
                    put("summary", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional collection summary or description.")
                    })
                    put("version", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional collection version string.")
                    })
                    put("auth", buildJsonObject {
                        put("type", "object")
                        put("description", "Optional collection-level default authentication configuration.")
                    })
                    put("headers", buildJsonObject {
                        put("type", "array")
                        put("description", "Optional collection-level default headers [{key, value, enabled, secret, description}].")
                    })
                    put("preRequestScript", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional collection-level pre-request JavaScript code.")
                    })
                    put("testScript", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional collection-level post-request test JavaScript code.")
                    })
                    put("protoFiles", buildJsonObject {
                        put("type", "array")
                        put("description", "Optional collection-level .proto files [{path, syncToGit}].")
                    })
                    put("importPaths", buildJsonObject {
                        put("type", "array")
                        put("description", "Optional collection-level protobuf import paths [{path, enabled, syncToGit}].")
                    })
                    put("confirm", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Must be true when action='delete'.")
                    })
                })
            },
        ),
        McpTool(
            name = "clapi_manage_folder",
            title = "Manage Clapi Folder",
            description = "Creates, updates, moves, or deletes a folder within a collection. Deleting requires confirm=true.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("required", buildJsonArray { add(JsonPrimitive("action")) })
                put("properties", buildJsonObject {
                    put("action", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add(JsonPrimitive("create"))
                            add(JsonPrimitive("update"))
                            add(JsonPrimitive("delete"))
                        })
                    })
                    put("folderId", buildJsonObject {
                        put("type", "string")
                        put("description", "Target folder UUID or slash path (required for 'update' and 'delete').")
                    })
                    put("parentId", buildJsonObject {
                        put("type", "string")
                        put("description", "Parent collection or folder UUID/slash path (required for 'create', optional move target for 'update').")
                    })
                    put("name", buildJsonObject {
                        put("type", "string")
                        put("description", "Folder name (required for 'create', optional rename for 'update').")
                    })
                    put("auth", buildJsonObject {
                        put("type", "object")
                        put("description", "Optional folder-level default authentication configuration.")
                    })
                    put("headers", buildJsonObject {
                        put("type", "array")
                        put("description", "Optional folder-level default headers [{key, value, enabled, secret, description}].")
                    })
                    put("preRequestScript", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional folder-level pre-request JavaScript code.")
                    })
                    put("testScript", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional folder-level post-request test JavaScript code.")
                    })
                    put("confirm", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Must be true when action='delete'.")
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
            "clapi_list_tree" -> {
                val collection = args.optString("collection")
                val kind = args.optString("kind")
                val query = args.optString("query")
                val tree = backend.listTree(collection, kind, query)
                val payload = json.encodeToJsonElement(ListSerializer(WorkspaceTreeNodeDto.serializer()), tree)
                okResult(payload)
            }
            "clapi_manage_collection" -> {
                val action = args.optString("action")?.trim()?.lowercase()
                    ?: throw IllegalArgumentException("Missing required argument 'action'.")
                if (action == "delete") {
                    val collectionId = args.optString("collectionId") ?: args.optString("name")
                        ?: throw IllegalArgumentException("'collectionId' is required when action='delete'.")
                    val confirm = args.optBoolean("confirm") ?: false
                    val del = backend.deleteCollection(collectionId, confirm)
                    okResult(json.encodeToJsonElement(DeleteResultDto.serializer(), del))
                } else {
                    val input = ManageCollectionInput(
                        action = action,
                        collectionId = args.optString("collectionId"),
                        name = args.optString("name"),
                        summary = args.optString("summary"),
                        version = args.optString("version"),
                        auth = args["auth"]?.let { parseAuthDto(it) },
                        headers = args["headers"]?.let { parseHeadersList(it) },
                        preRequestScript = args.optString("preRequestScript"),
                        testScript = args.optString("testScript"),
                        protoFiles = args["protoFiles"]?.let { parseProtoFiles(it) },
                        importPaths = args["importPaths"]?.let { parseImportPaths(it) },
                        confirm = args.optBoolean("confirm") ?: false,
                    )
                    val col = backend.manageCollection(input)
                    okResult(json.encodeToJsonElement(SanitizedCollectionDto.serializer(), col))
                }
            }
            "clapi_manage_folder" -> {
                val action = args.optString("action")?.trim()?.lowercase()
                    ?: throw IllegalArgumentException("Missing required argument 'action'.")
                if (action == "delete") {
                    val folderId = args.optString("folderId") ?: args.optString("name")
                        ?: throw IllegalArgumentException("'folderId' is required when action='delete'.")
                    val confirm = args.optBoolean("confirm") ?: false
                    val del = backend.deleteFolder(folderId, confirm)
                    okResult(json.encodeToJsonElement(DeleteResultDto.serializer(), del))
                } else {
                    val input = ManageFolderInput(
                        action = action,
                        folderId = args.optString("folderId"),
                        parentId = args.optString("parentId"),
                        name = args.optString("name"),
                        auth = args["auth"]?.let { parseAuthDto(it) },
                        headers = args["headers"]?.let { parseHeadersList(it) },
                        preRequestScript = args.optString("preRequestScript"),
                        testScript = args.optString("testScript"),
                        confirm = args.optBoolean("confirm") ?: false,
                    )
                    val folder = backend.manageFolder(input)
                    okResult(json.encodeToJsonElement(SanitizedFolderDto.serializer(), folder))
                }
            }
            else -> null
        }
    }

    internal fun parseAuthDto(element: JsonElement): SanitizedAuthDto {
        val obj = element as? JsonObject ?: return SanitizedAuthDto()
        return SanitizedAuthDto(
            type = obj.optString("type") ?: "NONE",
            username = obj.optString("username"),
            password = obj.optString("password"),
            token = obj.optString("token"),
            apiKey = obj.optString("apiKey") ?: obj.optString("key"),
            apiValue = obj.optString("apiValue") ?: obj.optString("value"),
            apiPlacement = obj.optString("apiPlacement") ?: obj.optString("placement"),
            oauth2Flow = obj.optString("oauth2Flow") ?: obj.optString("flow"),
            oauth2AccessTokenUrl = obj.optString("oauth2AccessTokenUrl") ?: obj.optString("accessTokenUrl"),
            oauth2RefreshTokenUrl = obj.optString("oauth2RefreshTokenUrl") ?: obj.optString("refreshTokenUrl"),
            oauth2AuthorizationUrl = obj.optString("oauth2AuthorizationUrl") ?: obj.optString("authorizationUrl"),
            oauth2RedirectUri = obj.optString("oauth2RedirectUri") ?: obj.optString("redirectUri"),
            oauth2ClientId = obj.optString("oauth2ClientId") ?: obj.optString("clientId"),
            oauth2ClientSecret = obj.optString("oauth2ClientSecret") ?: obj.optString("clientSecret"),
            oauth2CredentialsPlacement = obj.optString("oauth2CredentialsPlacement"),
            oauth2Username = obj.optString("oauth2Username"),
            oauth2Password = obj.optString("oauth2Password"),
            oauth2Scope = obj.optString("oauth2Scope") ?: obj.optString("scope"),
        )
    }

    internal fun parseHeadersList(element: JsonElement): List<SanitizedKeyValueDto> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val key = obj.optString("key") ?: obj.optString("name") ?: return@mapNotNull null
            SanitizedKeyValueDto(
                key = key,
                value = obj.optString("value").orEmpty(),
                enabled = obj.optBoolean("enabled") ?: (obj.optBoolean("disabled") != true),
                secret = obj.optBoolean("secret") ?: false,
                description = obj.optString("description").orEmpty(),
            )
        }
    }

    private fun parseProtoFiles(element: JsonElement): List<ProtoFileDto> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull?.let { ProtoFileDto(path = it) }
                is JsonObject -> {
                    val path = item.optString("path") ?: return@mapNotNull null
                    ProtoFileDto(path = path, syncToGit = item.optBoolean("syncToGit") ?: false)
                }
                else -> null
            }
        }
    }

    private fun parseImportPaths(element: JsonElement): List<ProtoImportPathDto> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull?.let { ProtoImportPathDto(path = it) }
                is JsonObject -> {
                    val path = item.optString("path") ?: return@mapNotNull null
                    ProtoImportPathDto(
                        path = path,
                        enabled = item.optBoolean("enabled") ?: true,
                        syncToGit = item.optBoolean("syncToGit") ?: false,
                    )
                }
                else -> null
            }
        }
    }

    internal fun okResult(structured: JsonElement): McpToolResult =
        McpToolResult(
            content = listOf(McpContent(type = "text", text = json.encodeToString(JsonElement.serializer(), structured))),
            isError = false,
            structuredContent = structured,
        )

    internal fun errorResult(message: String): McpToolResult =
        McpToolResult(
            content = listOf(McpContent(type = "text", text = message)),
            isError = true,
        )

    internal fun JsonObject.optString(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    internal fun JsonObject.optBoolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull
}
