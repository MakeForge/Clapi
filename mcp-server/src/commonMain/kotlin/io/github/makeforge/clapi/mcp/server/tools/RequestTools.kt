package io.github.makeforge.clapi.mcp.server.tools

import io.github.makeforge.clapi.core.model.McpTool
import io.github.makeforge.clapi.core.model.McpToolResult
import io.github.makeforge.clapi.core.model.opencollection.DynamicVariableParameter
import io.github.makeforge.clapi.mcp.server.backend.DeleteResultDto
import io.github.makeforge.clapi.mcp.server.backend.FormDataEntryDto
import io.github.makeforge.clapi.mcp.server.backend.GraphQlRequestConfigDto
import io.github.makeforge.clapi.mcp.server.backend.GraphQlVariantDto
import io.github.makeforge.clapi.mcp.server.backend.GrpcMetadataDto
import io.github.makeforge.clapi.mcp.server.backend.GrpcRequestConfigDto
import io.github.makeforge.clapi.mcp.server.backend.McpWorkspaceBackend
import io.github.makeforge.clapi.mcp.server.backend.SanitizedRequestDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedVariableDto
import io.github.makeforge.clapi.mcp.server.backend.UpsertRequestInput
import io.github.makeforge.clapi.mcp.server.backend.WebSocketRequestConfigDto
import io.github.makeforge.clapi.mcp.server.backend.WebSocketVariantDto
import io.github.makeforge.clapi.mcp.server.tools.WorkspaceTools.okResult
import io.github.makeforge.clapi.mcp.server.tools.WorkspaceTools.optBoolean
import io.github.makeforge.clapi.mcp.server.tools.WorkspaceTools.optString
import io.github.makeforge.clapi.mcp.server.tools.WorkspaceTools.parseAuthDto
import io.github.makeforge.clapi.mcp.server.tools.WorkspaceTools.parseHeadersList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object RequestTools {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    val tools: List<McpTool> = listOf(
        McpTool(
            name = "clapi_get_request",
            title = "Get Clapi Request",
            description = "Retrieves full sanitized configuration of an HTTP, gRPC, GraphQL, or WebSocket request by UUID or slash path.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("required", buildJsonArray { add(JsonPrimitive("requestId")) })
                put("properties", buildJsonObject {
                    put("requestId", buildJsonObject {
                        put("type", "string")
                        put("description", "Request UUID or slash path (e.g., 'Collection/Folder/RequestName').")
                    })
                })
            },
        ),
        McpTool(
            name = "clapi_upsert_request",
            title = "Create or Update Clapi Request",
            description = "Creates or updates an HTTP, gRPC, GraphQL, or WebSocket request with full protocol-specific configuration, headers, auth, body, and pre/post scripts.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("requestId", buildJsonObject {
                        put("type", "string")
                        put("description", "Existing request UUID or slash path to update, or 'Collection/Folder/RequestName' to create.")
                    })
                    put("parentId", buildJsonObject {
                        put("type", "string")
                        put("description", "Target collection or folder UUID/slash path when creating or moving a request.")
                    })
                    put("name", buildJsonObject {
                        put("type", "string")
                        put("description", "Request display name (required when creating a new request).")
                    })
                    put("kind", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add(JsonPrimitive("http"))
                            add(JsonPrimitive("grpc"))
                            add(JsonPrimitive("graphql"))
                            add(JsonPrimitive("websocket"))
                        })
                        put("description", "Request protocol kind: 'http' (default), 'grpc', 'graphql', or 'websocket'.")
                    })
                    put("summary", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional request description or documentation.")
                    })
                    put("method", buildJsonObject {
                        put("type", "string")
                        put("description", "HTTP method: GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD, TRACE, CONNECT.")
                    })
                    put("url", buildJsonObject {
                        put("type", "string")
                        put("description", "Request URL or gRPC host:port endpoint.")
                    })
                    put("queryParams", buildJsonObject {
                        put("type", "array")
                        put("description", "Query parameter entries [{key, value, enabled, description}].")
                    })
                    put("headers", buildJsonObject {
                        put("type", "array")
                        put("description", "Header entries [{key, value, enabled, secret, description}].")
                    })
                    put("auth", buildJsonObject {
                        put("type", "object")
                        put("description", "Authentication config {type: NONE|BASIC|BEARER|API_KEY|OAUTH2|JWT|INHERIT, ...}. Passing '********' preserves existing stored secret.")
                    })
                    put("bodyType", buildJsonObject {
                        put("type", "string")
                        put("description", "HTTP body type: NONE, JSON, XML, HTML, JAVASCRIPT, FORM_DATA, X_WWW_FORM_URLENCODED, RAW_TEXT, BINARY, GRAPHQL.")
                    })
                    put("bodyContent", buildJsonObject {
                        put("type", "string")
                        put("description", "Raw request body text or payload.")
                    })
                    put("formDataEntries", buildJsonObject {
                        put("type", "array")
                        put("description", "Multipart form-data or urlencoded rows [{key, type: TEXT|FILE, value, description, enabled}].")
                    })
                    put("variables", buildJsonObject {
                        put("type", "array")
                        put("description", "Request-scoped variables [{key, value, kind, variableType, enabled}].")
                    })
                    put("preRequestScript", buildJsonObject {
                        put("type", "string")
                        put("description", "Pre-request JavaScript source code.")
                    })
                    put("testScript", buildJsonObject {
                        put("type", "string")
                        put("description", "Post-request test JavaScript source code (e.g., clapi.test / clapi.expect).")
                    })
                    put("grpc", buildJsonObject {
                        put("type", "object")
                        put("description", "gRPC configuration {protoFilePath, grpcMethod, grpcMethodType: unary|client-streaming|server-streaming|bidi-streaming, metadata: [{name, value, disabled}], message}.")
                    })
                    put("graphql", buildJsonObject {
                        put("type", "object")
                        put("description", "GraphQL configuration {method, query, operationName, variablesJson, variants: [{title, query, variables, selected}]}.")
                    })
                    put("websocket", buildJsonObject {
                        put("type", "object")
                        put("description", "WebSocket configuration {messageFormat: text|json|xml|binary, timeout, keepAliveInterval, variants: [{title, type, data, selected}]}.")
                    })
                })
            },
        ),
        McpTool(
            name = "clapi_delete_request",
            title = "Delete Clapi Request",
            description = "Deletes a request by UUID or slash path. Requires confirm=true.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("required", buildJsonArray {
                    add(JsonPrimitive("requestId"))
                    add(JsonPrimitive("confirm"))
                })
                put("properties", buildJsonObject {
                    put("requestId", buildJsonObject {
                        put("type", "string")
                        put("description", "Request UUID or slash path to delete.")
                    })
                    put("confirm", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Must be true to confirm deletion.")
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
            "clapi_get_request" -> {
                val requestId = args.optString("requestId")
                    ?: throw IllegalArgumentException("Missing required argument 'requestId'.")
                val req = backend.getRequest(requestId)
                okResult(json.encodeToJsonElement(SanitizedRequestDto.serializer(), req))
            }
            "clapi_upsert_request" -> {
                val input = UpsertRequestInput(
                    requestId = args.optString("requestId"),
                    parentId = args.optString("parentId"),
                    name = args.optString("name"),
                    kind = args.optString("kind"),
                    summary = args.optString("summary"),
                    method = args.optString("method"),
                    url = args.optString("url"),
                    queryParams = args["queryParams"]?.let { parseHeadersList(it) },
                    headers = args["headers"]?.let { parseHeadersList(it) },
                    auth = args["auth"]?.let { parseAuthDto(it) },
                    bodyType = args.optString("bodyType"),
                    bodyContent = args.optString("bodyContent"),
                    formDataEntries = args["formDataEntries"]?.let { parseFormDataEntries(it) },
                    variables = args["variables"]?.let { parseVariablesList(it) },
                    preRequestScript = args.optString("preRequestScript"),
                    testScript = args.optString("testScript"),
                    grpc = args["grpc"]?.let { parseGrpcConfig(it) },
                    graphql = args["graphql"]?.let { parseGraphQlConfig(it) },
                    websocket = args["websocket"]?.let { parseWebSocketConfig(it) },
                )
                val saved = backend.upsertRequest(input)
                okResult(json.encodeToJsonElement(SanitizedRequestDto.serializer(), saved))
            }
            "clapi_delete_request" -> {
                val requestId = args.optString("requestId")
                    ?: throw IllegalArgumentException("Missing required argument 'requestId'.")
                val confirm = args.optBoolean("confirm") ?: false
                val del = backend.deleteRequest(requestId, confirm)
                okResult(json.encodeToJsonElement(DeleteResultDto.serializer(), del))
            }
            else -> null
        }
    }

    private fun parseFormDataEntries(element: JsonElement): List<FormDataEntryDto> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val key = obj.optString("key") ?: obj.optString("name") ?: return@mapNotNull null
            FormDataEntryDto(
                key = key,
                type = obj.optString("type") ?: "TEXT",
                value = obj.optString("value").orEmpty(),
                description = obj.optString("description").orEmpty(),
                enabled = obj.optBoolean("enabled") ?: true,
            )
        }
    }

    internal fun parseVariablesList(element: JsonElement): List<SanitizedVariableDto> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val key = obj.optString("key") ?: obj.optString("name") ?: return@mapNotNull null
            val secretFlag = obj.optBoolean("secret") ?: false
            val kind = obj.optString("kind") ?: if (secretFlag) "secret" else "regular"
            SanitizedVariableDto(
                key = key,
                value = obj.optString("value").orEmpty(),
                kind = kind,
                variableType = obj.optString("variableType") ?: obj.optString("type") ?: "string",
                secret = kind == "secret" || secretFlag,
                enabled = obj.optBoolean("enabled") ?: true,
                dynamicScript = obj.optString("dynamicScript") ?: obj.optString("script"),
                dynamicParameters = (obj["dynamicParameters"] as? JsonArray)?.mapNotNull { pElem ->
                    val pObj = pElem as? JsonObject ?: return@mapNotNull null
                    val pName = pObj.optString("name") ?: return@mapNotNull null
                    DynamicVariableParameter(
                        name = pName,
                        description = pObj.optString("description"),
                    )
                }.orEmpty(),
            )
        }
    }

    private fun parseGrpcConfig(element: JsonElement): GrpcRequestConfigDto? {
        val obj = element as? JsonObject ?: return null
        val metadata = (obj["metadata"] as? JsonArray)?.mapNotNull { item ->
            val mObj = item as? JsonObject ?: return@mapNotNull null
            val name = mObj.optString("name") ?: mObj.optString("key") ?: return@mapNotNull null
            GrpcMetadataDto(
                name = name,
                value = mObj.optString("value").orEmpty(),
                disabled = mObj.optBoolean("disabled") ?: (mObj.optBoolean("enabled") == false),
            )
        }.orEmpty()
        return GrpcRequestConfigDto(
            protoFilePath = obj.optString("protoFilePath"),
            grpcMethod = obj.optString("grpcMethod") ?: obj.optString("method"),
            grpcMethodType = obj.optString("grpcMethodType") ?: obj.optString("methodType") ?: "unary",
            metadata = metadata,
            message = obj.optString("message"),
        )
    }

    private fun parseGraphQlConfig(element: JsonElement): GraphQlRequestConfigDto? {
        val obj = element as? JsonObject ?: return null
        val variants = (obj["variants"] as? JsonArray)?.mapNotNull { item ->
            val vObj = item as? JsonObject ?: return@mapNotNull null
            val title = vObj.optString("title") ?: vObj.optString("name") ?: return@mapNotNull null
            GraphQlVariantDto(
                title = title,
                query = vObj.optString("query").orEmpty(),
                variables = vObj.optString("variables") ?: vObj.optString("variablesJson"),
                selected = vObj.optBoolean("selected") ?: false,
            )
        }.orEmpty()
        return GraphQlRequestConfigDto(
            method = obj.optString("method") ?: "POST",
            query = obj.optString("query").orEmpty(),
            operationName = obj.optString("operationName"),
            variablesJson = obj.optString("variablesJson") ?: obj.optString("variables"),
            variants = variants,
        )
    }

    private fun parseWebSocketConfig(element: JsonElement): WebSocketRequestConfigDto? {
        val obj = element as? JsonObject ?: return null
        val variants = (obj["variants"] as? JsonArray)?.mapNotNull { item ->
            val vObj = item as? JsonObject ?: return@mapNotNull null
            val title = vObj.optString("title") ?: vObj.optString("name") ?: return@mapNotNull null
            WebSocketVariantDto(
                title = title,
                type = vObj.optString("type") ?: "text",
                data = vObj.optString("data") ?: vObj.optString("message").orEmpty(),
                selected = vObj.optBoolean("selected") ?: false,
            )
        }.orEmpty()
        return WebSocketRequestConfigDto(
            messageFormat = obj.optString("messageFormat") ?: "text",
            timeout = obj.optString("timeout"),
            keepAliveInterval = obj.optString("keepAliveInterval"),
            variants = variants,
        )
    }
}
