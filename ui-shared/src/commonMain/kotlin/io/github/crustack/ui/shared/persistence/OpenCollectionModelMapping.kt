package io.github.crustack.ui.shared.persistence

import androidx.compose.runtime.toMutableStateList
import io.github.crustack.core.model.AuthType
import io.github.crustack.core.model.BodyType
import io.github.crustack.core.model.FormEntryType
import io.github.crustack.core.model.HttpMethodType
import io.github.crustack.core.model.KeyValueEntry
import io.github.crustack.core.model.opencollection.AuthApiKey
import io.github.crustack.core.model.opencollection.AuthBasic
import io.github.crustack.core.model.opencollection.AuthBearer
import io.github.crustack.core.model.opencollection.CollectionInfo
import io.github.crustack.core.model.opencollection.Description
import io.github.crustack.core.model.opencollection.FolderInfo
import io.github.crustack.core.model.opencollection.FolderItem
import io.github.crustack.core.model.opencollection.FormParamEntry
import io.github.crustack.core.model.opencollection.FormUrlEncodedBody
import io.github.crustack.core.model.opencollection.HttpRequestBody
import io.github.crustack.core.model.opencollection.HttpRequestDetails
import io.github.crustack.core.model.opencollection.HttpRequestHeader
import io.github.crustack.core.model.opencollection.HttpRequestInfo
import io.github.crustack.core.model.opencollection.HttpRequestItem
import io.github.crustack.core.model.opencollection.HttpRequestParam
import io.github.crustack.core.model.opencollection.HttpRequestRuntime
import io.github.crustack.core.model.opencollection.MultipartFormBody
import io.github.crustack.core.model.opencollection.MultipartPartEntry
import io.github.crustack.core.model.opencollection.OpenCollectionDocument
import io.github.crustack.core.model.opencollection.OpenCollectionItem
import io.github.crustack.core.model.opencollection.RawBody
import io.github.crustack.core.model.opencollection.Script
import io.github.crustack.ui.shared.platform.generateUuid
import io.github.crustack.ui.shared.state.CollectionNode
import io.github.crustack.ui.shared.state.FormDataEntryState
import io.github.crustack.core.model.RequestKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

private fun collectClapiMetadata(node: CollectionNode, out: MutableMap<String, JsonObject>) {
    if (node.isFolder) {
        node.children.forEach { collectClapiMetadata(it, out) }
    } else {
        val meta = buildJsonObject {
            put("id", node.id)
            put("name", node.name)
            node.bodyType?.let { put("bodyType", it.name) }
            if (node.bodyContents.isNotEmpty()) {
                put("bodyContents", buildJsonObject {
                    node.bodyContents.forEach { (k, v) -> put(k, v) }
                })
            }
            if (node.queryEntries != null) {
                put("queryEntries", buildJsonArray {
                    node.queryEntries!!.forEach { entry ->
                        add(buildJsonObject {
                            put("key", entry.key)
                            put("value", entry.value)
                            put("enabled", entry.enabled)
                            put("secret", entry.secret)
                        })
                    }
                })
            }
            if (node.headerEntries != null) {
                put("headerEntries", buildJsonArray {
                    node.headerEntries!!.forEach { entry ->
                        add(buildJsonObject {
                            put("key", entry.key)
                            put("value", entry.value)
                            put("enabled", entry.enabled)
                            put("secret", entry.secret)
                        })
                    }
                })
            }
            node.authApiPlacement?.let { put("authApiPlacement", it) }
            put("kind", node.kind.name)
            node.requestRef?.let { put("requestRef", it) }
            node.mcpConfig?.let { mcp ->
                put("mcpConfig", json.encodeToJsonElement(io.github.crustack.core.model.McpConnectionConfig.serializer(), mcp))
            }
        }
        out[node.name] = meta
        out[node.id] = meta
    }
}

fun CollectionNode.toOpenCollectionDocument(): OpenCollectionDocument {
    val ext = mutableMapOf<String, JsonElement>()
    ext["id"] = JsonPrimitive(id)
    val clapiMetaMap = mutableMapOf<String, JsonObject>()
    collectClapiMetadata(this, clapiMetaMap)
    if (clapiMetaMap.isNotEmpty()) {
        ext["clapi"] = buildJsonObject {
            put("requests", buildJsonObject {
                clapiMetaMap.forEach { (k, v) -> put(k, v) }
            })
        }
    }
    return OpenCollectionDocument(
        opencollection = "1.0.0",
        info = CollectionInfo(name = name),
        bundled = true,
        items = children.map { it.toOpenCollectionItem() },
        extensions = ext
    )
}

fun CollectionNode.toOpenCollectionItem(): OpenCollectionItem {
    return if (isFolder) {
        val ext = mutableMapOf<String, JsonElement>()
        ext["id"] = JsonPrimitive(id)
        FolderItem(
            info = FolderInfo(name = name, type = "folder"),
            items = children.map { it.toOpenCollectionItem() },
            extensions = ext
        )
    } else {
        val ext = mutableMapOf<String, JsonElement>()
        ext["id"] = JsonPrimitive(id)
        if (requestRef != null) {
            ext["requestRef"] = JsonPrimitive(requestRef)
        }
        if (authType == AuthType.JWT) {
            ext["clapi"] = buildJsonObject { put("authType", "jwt") }
        }
        if (kind != io.github.crustack.core.model.RequestKind.HTTP) {
            ext["kind"] = JsonPrimitive(kind.name)
        }

        val effectiveHeaders = (headerEntries ?: userHeaders.map { KeyValueEntry(it.first, it.second) }).map {
            HttpRequestHeader(
                name = it.key,
                value = it.value,
                disabled = if (it.enabled) null else true
            )
        }

        val effectiveParams = (queryEntries ?: emptyList()).map {
            HttpRequestParam(
                name = it.key,
                value = it.value,
                type = "query",
                disabled = if (it.enabled) null else true
            )
        }

        val body: HttpRequestBody? = when (bodyType) {
            BodyType.FORM_DATA -> MultipartFormBody(
                data = formDataEntries.map {
                    MultipartPartEntry(
                        name = it.key,
                        type = if (it.type == FormEntryType.FILE) "file" else "text",
                        value = JsonPrimitive(it.value),
                        description = if (it.description.isNotBlank()) Description(it.description) else null,
                        disabled = if (it.enabled) null else true
                    )
                }
            )
            BodyType.X_WWW_FORM_URLENCODED -> FormUrlEncodedBody(
                data = urlencodedEntries.map {
                    FormParamEntry(
                        name = it.key,
                        value = it.value,
                        description = if (it.description.isNotBlank()) Description(it.description) else null,
                        disabled = if (it.enabled) null else true
                    )
                }
            )
            BodyType.JSON, BodyType.XML, BodyType.HTML, BodyType.JAVASCRIPT, BodyType.RAW_TEXT, BodyType.GRAPHQL -> {
                val rawType = when (bodyType) {
                    BodyType.JSON, BodyType.GRAPHQL -> "json"
                    BodyType.XML -> "xml"
                    else -> "text"
                }
                RawBody(type = rawType, data = bodyContent ?: "")
            }
            else -> if (!bodyContent.isNullOrBlank()) RawBody(type = "text", data = bodyContent) else null
        }

        val auth = when (authType) {
            AuthType.BASIC -> AuthBasic(username = authUsername ?: "", password = authPassword ?: "")
            AuthType.BEARER -> AuthBearer(token = authToken ?: "")
            AuthType.JWT -> AuthBearer(token = authToken ?: "")
            AuthType.API_KEY -> AuthApiKey(
                key = authApiKey ?: "",
                value = authApiValue ?: "",
                placement = authApiPlacement ?: "header"
            )
            else -> null
        }

        val scripts = mutableListOf<Script>()
        if (!preRequestScript.isNullOrBlank()) {
            scripts.add(Script(type = "before-request", code = preRequestScript))
        }
        if (!testScript.isNullOrBlank()) {
            scripts.add(Script(type = "after-response", code = testScript))
        }
        val runtime = if (scripts.isNotEmpty()) HttpRequestRuntime(scripts = scripts) else null

        HttpRequestItem(
            info = HttpRequestInfo(name = name, type = "http"),
            http = HttpRequestDetails(
                method = method?.name ?: "GET",
                url = url ?: "",
                headers = effectiveHeaders,
                params = effectiveParams,
                body = body,
                auth = auth
            ),
            runtime = runtime,
            extensions = ext
        )
    }
}

fun OpenCollectionDocument.toCollectionNode(nameOverride: String? = null, preserveIds: Boolean = true): CollectionNode {
    val collId = if (preserveIds) {
        (extensions["id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: generateUuid()
    } else {
        generateUuid()
    }
    val collName = nameOverride ?: info.name
    val clapiRequests = (extensions["clapi"] as? JsonObject)?.get("requests")?.jsonObject
    val childrenNodes = items.map { it.toCollectionNode(preserveIds, clapiRequests) }.toMutableStateList()
    return CollectionNode(
        id = collId,
        name = collName,
        isFolder = true,
        children = childrenNodes
    )
}

fun OpenCollectionItem.toCollectionNode(preserveIds: Boolean = true, clapiRequests: JsonObject? = null): CollectionNode {
    return when (this) {
        is FolderItem -> {
            val folderId = if (preserveIds) {
                (extensions["id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: generateUuid()
            } else {
                generateUuid()
            }
            CollectionNode(
                id = folderId,
                name = info.name,
                isFolder = true,
                children = items.map { it.toCollectionNode(preserveIds, clapiRequests) }.toMutableStateList()
            )
        }
        is HttpRequestItem -> {
            val reqId = if (preserveIds) {
                (extensions["id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: generateUuid()
            } else {
                generateUuid()
            }
            var reqRef = (extensions["requestRef"] as? JsonPrimitive)?.content

            val headers = http.headers.map {
                KeyValueEntry(key = it.name, value = it.value, enabled = it.disabled != true)
            }
            val userHeaders = http.headers.map { it.name to it.value }
            var queryParams: List<KeyValueEntry>? = http.params.map {
                KeyValueEntry(key = it.name, value = it.value, enabled = it.disabled != true)
            }
            var headerEntries: List<KeyValueEntry>? = headers

            var bodyType: BodyType = BodyType.NONE
            var bodyContent: String? = null
            var bodyContents: Map<String, String> = emptyMap()
            var formData = emptyList<FormDataEntryState>()
            var urlencoded = emptyList<FormDataEntryState>()

            when (val b = http.body) {
                is MultipartFormBody -> {
                    bodyType = BodyType.FORM_DATA
                    formData = b.data.map {
                        val strVal = (it.value as? JsonPrimitive)?.content ?: it.value.toString()
                        FormDataEntryState(
                            key = it.name,
                            type = if (it.type == "file") FormEntryType.FILE else FormEntryType.TEXT,
                            value = strVal,
                            description = it.description?.content ?: "",
                            enabled = it.disabled != true
                        )
                    }
                }
                is FormUrlEncodedBody -> {
                    bodyType = BodyType.X_WWW_FORM_URLENCODED
                    urlencoded = b.data.map {
                        FormDataEntryState(
                            key = it.name,
                            value = it.value,
                            description = it.description?.content ?: "",
                            enabled = it.disabled != true
                        )
                    }
                }
                is RawBody -> {
                    bodyType = when (b.type.lowercase()) {
                        "json" -> BodyType.JSON
                        "xml" -> BodyType.XML
                        else -> BodyType.RAW_TEXT
                    }
                    bodyContent = b.data
                }
                else -> {
                    bodyType = BodyType.NONE
                }
            }

            var authType: AuthType = AuthType.NONE
            var authUsername: String? = null
            var authPassword: String? = null
            var authToken: String? = null
            var authApiKey: String? = null
            var authApiValue: String? = null
            var authApiPlacement: String? = null
            var requestKind: RequestKind = RequestKind.HTTP

            val clapiExt = extensions["clapi"] as? JsonObject
            val isJwt = (clapiExt?.get("authType") as? JsonPrimitive)?.content == "jwt"

            when (val a = http.auth) {
                is AuthBasic -> {
                    authType = AuthType.BASIC
                    authUsername = a.username
                    authPassword = a.password
                }
                is AuthBearer -> {
                    authType = if (isJwt) AuthType.JWT else AuthType.BEARER
                    authToken = a.token
                }
                is AuthApiKey -> {
                    authType = AuthType.API_KEY
                    authApiKey = a.key
                    authApiValue = a.value
                    authApiPlacement = a.placement
                }
                else -> {
                    authType = AuthType.NONE
                }
            }

            val meta = clapiRequests?.get(info.name) as? JsonObject ?: clapiRequests?.get(reqId) as? JsonObject
            if (meta != null) {
                meta["bodyType"]?.jsonPrimitive?.contentOrNull?.let { typeName ->
                    runCatching { BodyType.valueOf(typeName) }.getOrNull()?.let { bodyType = it }
                }
                meta["bodyContents"]?.jsonObject?.let { obj ->
                    bodyContents = obj.mapValues { it.value.jsonPrimitive.content }
                }
                meta["queryEntries"]?.jsonArray?.let { arr ->
                    queryParams = arr.mapNotNull { it as? JsonObject }.map { entryObj ->
                        KeyValueEntry(
                            key = entryObj["key"]?.jsonPrimitive?.content ?: "",
                            value = entryObj["value"]?.jsonPrimitive?.content ?: "",
                            enabled = entryObj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
                            secret = entryObj["secret"]?.jsonPrimitive?.booleanOrNull ?: false
                        )
                    }
                }
                meta["headerEntries"]?.jsonArray?.let { arr ->
                    headerEntries = arr.mapNotNull { it as? JsonObject }.map { entryObj ->
                        KeyValueEntry(
                            key = entryObj["key"]?.jsonPrimitive?.content ?: "",
                            value = entryObj["value"]?.jsonPrimitive?.content ?: "",
                            enabled = entryObj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
                            secret = entryObj["secret"]?.jsonPrimitive?.booleanOrNull ?: false
                        )
                    }
                }
                meta["authApiPlacement"]?.jsonPrimitive?.contentOrNull?.let {
                    authApiPlacement = it
                }
                meta["kind"]?.jsonPrimitive?.contentOrNull?.let { kindName ->
                    runCatching { RequestKind.valueOf(kindName) }.getOrNull()?.let { requestKind = it }
                }
                meta["requestRef"]?.jsonPrimitive?.contentOrNull?.let {
                    reqRef = it
                }
            }

            val preScript = runtime?.scripts?.firstOrNull { it.type == "before-request" }?.code
            val postScript = runtime?.scripts?.firstOrNull { it.type == "after-response" || it.type == "tests" }?.code

            val httpMethod = HttpMethodType.entries.firstOrNull {
                it.name.equals(http.method, ignoreCase = true)
            } ?: HttpMethodType.GET

            val mcpConfig = meta?.get("mcpConfig")?.let {
                runCatching {
                    json.decodeFromJsonElement(io.github.crustack.core.model.McpConnectionConfig.serializer(), it)
                }.getOrNull()
            }

            CollectionNode(
                id = reqId,
                name = info.name,
                isFolder = false,
                method = httpMethod,
                url = http.url,
                headerEntries = headerEntries,
                userHeaders = userHeaders,
                queryEntries = queryParams,
                bodyType = bodyType,
                bodyContent = bodyContent,
                bodyContents = bodyContents,
                formDataEntries = formData,
                urlencodedEntries = urlencoded,
                authType = authType,
                authUsername = authUsername,
                authPassword = authPassword,
                authToken = authToken,
                authApiKey = authApiKey,
                authApiValue = authApiValue,
                authApiPlacement = authApiPlacement,
                preRequestScript = preScript,
                testScript = postScript,
                requestRef = reqRef,
                kind = requestKind,
                mcpConfig = mcpConfig,
            )
        }
    }
}
