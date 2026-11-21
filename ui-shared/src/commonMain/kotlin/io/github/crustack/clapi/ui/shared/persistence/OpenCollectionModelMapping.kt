package io.github.crustack.clapi.ui.shared.persistence

import androidx.compose.runtime.toMutableStateList
import io.github.crustack.clapi.core.model.AuthType
import io.github.crustack.clapi.core.model.BodyType
import io.github.crustack.clapi.core.model.FormEntryType
import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.core.model.KeyValueEntry
import io.github.crustack.clapi.core.model.McpConnectionConfig
import io.github.crustack.clapi.core.model.RequestKind
import io.github.crustack.clapi.core.model.opencollection.AuthApiKey
import io.github.crustack.clapi.core.model.opencollection.AuthBasic
import io.github.crustack.clapi.core.model.opencollection.AuthBearer
import io.github.crustack.clapi.core.model.opencollection.AuthConfig
import io.github.crustack.clapi.core.model.opencollection.CollectionConfig
import io.github.crustack.clapi.core.model.opencollection.CollectionInfo
import io.github.crustack.clapi.core.model.opencollection.Description
import io.github.crustack.clapi.core.model.opencollection.Environment
import io.github.crustack.clapi.core.model.opencollection.FolderInfo
import io.github.crustack.clapi.core.model.opencollection.FolderItem
import io.github.crustack.clapi.core.model.opencollection.FormParamEntry
import io.github.crustack.clapi.core.model.opencollection.FormUrlEncodedBody
import io.github.crustack.clapi.core.model.opencollection.HttpRequestBody
import io.github.crustack.clapi.core.model.opencollection.HttpRequestDetails
import io.github.crustack.clapi.core.model.opencollection.HttpRequestHeader
import io.github.crustack.clapi.core.model.opencollection.HttpRequestInfo
import io.github.crustack.clapi.core.model.opencollection.HttpRequestItem
import io.github.crustack.clapi.core.model.opencollection.HttpRequestParam
import io.github.crustack.clapi.core.model.opencollection.HttpRequestRuntime
import io.github.crustack.clapi.core.model.opencollection.MultipartFormBody
import io.github.crustack.clapi.core.model.opencollection.MultipartPartEntry
import io.github.crustack.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.crustack.clapi.core.model.opencollection.OpenCollectionItem
import io.github.crustack.clapi.core.model.opencollection.RawBody
import io.github.crustack.clapi.core.model.opencollection.RegularVariable
import io.github.crustack.clapi.core.model.opencollection.RequestDefaults
import io.github.crustack.clapi.core.model.opencollection.Script
import io.github.crustack.clapi.core.model.opencollection.SecretVariable
import io.github.crustack.clapi.core.model.opencollection.VariableEntry
import io.github.crustack.clapi.core.model.opencollection.VariableValue
import io.github.crustack.clapi.ui.shared.platform.generateUuid
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.CollectionNode
import io.github.crustack.clapi.ui.shared.state.EnvState
import io.github.crustack.clapi.ui.shared.state.FormDataEntryState
import io.github.crustack.clapi.ui.shared.state.MutableKeyValue
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

private fun collectClapiMetadata(
    node: CollectionNode,
    out: MutableMap<String, JsonObject>,
    path: String = "",
) {
    if (node.isFolder) {
        val folderMeta = mutableMapOf<String, JsonElement>()
        if (node.authApiPlacement != null) {
            folderMeta["authApiPlacement"] = JsonPrimitive(node.authApiPlacement)
        }
        if (folderMeta.isNotEmpty()) {
            folderMeta["id"] = JsonPrimitive(node.id)
            folderMeta["name"] = JsonPrimitive(node.name)
            val key = if (path.isEmpty()) node.id else path
            out[key] = JsonObject(folderMeta)
        }
        node.children.forEachIndexed { index, child ->
            val childPath = if (path.isEmpty()) index.toString() else "$path/$index"
            collectClapiMetadata(child, out, childPath)
        }
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
                put("mcpConfig", json.encodeToJsonElement(McpConnectionConfig.serializer(), mcp))
            }
        }
        out[node.name] = meta
        out[node.id] = meta
        if (path.isNotEmpty()) {
            out[path] = meta
        }
    }
}

/**
 * Produces a standalone OpenCollection document for single-collection export.
 * Per FR-017, single-collection exports omit `config` and environments.
 */
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
        config = null,
        bundled = true,
        items = children.map { it.toOpenCollectionItem() },
        extensions = ext,
    )
}

/**
 * Maps a Clapi Collection [CollectionNode] to a top-level [FolderItem] of the global OpenCollection document.
 */
fun CollectionNode.toCollectionFolder(): FolderItem {
    val ext = mutableMapOf<String, JsonElement>("id" to JsonPrimitive(id))
    if (authType == AuthType.JWT) {
        ext["clapi"] = buildJsonObject { put("authType", "jwt") }
    }
    return FolderItem(
        info = FolderInfo(name = name, type = "folder"),
        items = children.map { it.toOpenCollectionItem() },
        request = toRequestDefaults(),
        extensions = ext,
    )
}

/**
 * Maps a standalone [OpenCollectionDocument] into a top-level [FolderItem] representing a Clapi Collection.
 * Preserves the document's `extensions["id"]` (FR-019) or assigns a new UUID when absent.
 */
fun OpenCollectionDocument.toCollectionFolder(nameOverride: String? = null): FolderItem {
    val folderId = (extensions["id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: generateUuid()
    return FolderItem(
        info = FolderInfo(name = nameOverride ?: info.name, type = "folder"),
        items = items,
        request = request,
        extensions = extensions + ("id" to JsonPrimitive(folderId)),
    )
}

/**
 * Builds the single global [OpenCollectionDocument] from [AppState] (FR-014, FR-015).
 * Every Clapi Collection becomes a top-level [FolderItem], and environments are stored in `config.environments`.
 */
fun AppState.toGlobalOpenCollectionDocument(): OpenCollectionDocument {
    val ext = mutableMapOf<String, JsonElement>()
    val clapiMetaMap = mutableMapOf<String, JsonObject>()
    collections.forEachIndexed { index, collection ->
        collectClapiMetadata(collection, clapiMetaMap, index.toString())
    }
    if (clapiMetaMap.isNotEmpty()) {
        ext["clapi"] = buildJsonObject {
            put("requests", buildJsonObject {
                clapiMetaMap.forEach { (k, v) -> put(k, v) }
            })
        }
    }
    val openCollectionEnvs = environments.map { it.toOpenCollectionEnvironment() }
    return OpenCollectionDocument(
        opencollection = "1.0.0",
        info = CollectionInfo(name = "Workspace"),
        config = CollectionConfig(environments = openCollectionEnvs),
        bundled = true,
        items = collections.map { it.toCollectionFolder() },
        extensions = ext,
    )
}

fun Environment.toEnvState(): EnvState =
    EnvState(
        name = name,
        variables = variables.map { it.toMutableKeyValue() },
        color = color,
        description = description,
        clientCertificates = clientCertificates,
        extends = extends,
        dotEnvFilePath = dotEnvFilePath,
    )

fun EnvState.toOpenCollectionEnvironment(): Environment =
    Environment(
        name = name,
        color = color,
        description = description,
        variables = variables
            .filter { it.key.trim().isNotEmpty() }
            .map { it.toVariableEntry() },
        clientCertificates = clientCertificates,
        extends = extends,
        dotEnvFilePath = dotEnvFilePath,
    )

fun VariableEntry.toMutableKeyValue(): MutableKeyValue =
    when (this) {
        is RegularVariable -> MutableKeyValue(
            key = name,
            value = value?.asString ?: "",
            enabled = disabled != true,
            secret = false,
        )
        is SecretVariable -> MutableKeyValue(
            key = name,
            value = "",
            enabled = disabled != true,
            secret = true,
        )
    }

fun MutableKeyValue.toVariableEntry(): VariableEntry {
    val trimmedKey = key.trim()
    val disabledFlag = if (enabled) null else true
    return if (secret) {
        SecretVariable(
            name = trimmedKey,
            disabled = disabledFlag,
            secret = true,
        )
    } else {
        RegularVariable(
            name = trimmedKey,
            value = VariableValue.of(value),
            disabled = disabledFlag,
        )
    }
}

/**
 * Merges an imported OpenCollection [Environment] into this [EnvState] (FR-016).
 * Missing variables and fields are added; existing entries are never removed or overwritten.
 */
fun EnvState.mergeFrom(incoming: Environment) {
    if (color == null && incoming.color != null) {
        color = incoming.color
    }
    if (description == null && incoming.description != null) {
        description = incoming.description
    }
    if (extends == null && incoming.extends != null) {
        extends = incoming.extends
    }
    if (dotEnvFilePath == null && incoming.dotEnvFilePath != null) {
        dotEnvFilePath = incoming.dotEnvFilePath
    }
    if (incoming.clientCertificates.isNotEmpty()) {
        val existingDomains = clientCertificates.map { it.domain }.toSet()
        val missingCerts = incoming.clientCertificates.filter { it.domain !in existingDomains }
        if (missingCerts.isNotEmpty()) {
            clientCertificates = clientCertificates + missingCerts
        }
    }
    val existingKeys = variables.map { it.key }.toMutableSet()
    for (variable in incoming.variables) {
        if (variable.name.isNotBlank() && variable.name !in existingKeys) {
            variables.add(variable.toMutableKeyValue())
            existingKeys.add(variable.name)
        }
    }
}

internal fun CollectionNode.toRequestDefaults(): RequestDefaults? {
    val effectiveHeaders = (headerEntries ?: userHeaders.map { KeyValueEntry(it.first, it.second) }).map {
        HttpRequestHeader(
            name = it.key,
            value = it.value,
            disabled = if (it.enabled) null else true
        )
    }
    val authConfig: AuthConfig? = when (authType) {
        AuthType.BASIC -> AuthBasic(username = authUsername ?: "", password = authPassword ?: "")
        AuthType.BEARER, AuthType.JWT -> AuthBearer(token = authToken ?: "")
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
    val vars = (variableEntries ?: emptyList()).filter { it.key.isNotBlank() }.map {
        RegularVariable(
            name = it.key,
            value = VariableValue.of(it.value),
            disabled = if (it.enabled) null else true,
        )
    }
    return if (effectiveHeaders.isNotEmpty() || authConfig != null || scripts.isNotEmpty() || vars.isNotEmpty()) {
        RequestDefaults(
            headers = effectiveHeaders,
            auth = authConfig,
            variables = vars,
            scripts = scripts,
        )
    } else {
        null
    }
}

fun CollectionNode.toOpenCollectionItem(): OpenCollectionItem {
    return if (isFolder) {
        val ext = mutableMapOf<String, JsonElement>()
        ext["id"] = JsonPrimitive(id)
        val clapiFields = mutableMapOf<String, JsonElement>()
        if (authType == AuthType.JWT) {
            clapiFields["authType"] = JsonPrimitive("jwt")
        }
        if (clapiFields.isNotEmpty()) {
            ext["clapi"] = JsonObject(clapiFields)
        }
        FolderItem(
            info = FolderInfo(name = name, type = "folder"),
            items = children.map { it.toOpenCollectionItem() },
            request = toRequestDefaults(),
            extensions = ext
        )
    } else {
        val ext = mutableMapOf<String, JsonElement>()
        if (requestRef != null) {
            ext["requestRef"] = JsonPrimitive(requestRef)
        }
        if (authType == AuthType.JWT) {
            ext["clapi"] = buildJsonObject { put("authType", "jwt") }
        }
        if (kind != RequestKind.HTTP) {
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

fun OpenCollectionDocument.toCollectionNode(
    nameOverride: String? = null,
    preserveIds: Boolean = true,
    existingIds: Set<String> = emptySet(),
): CollectionNode {
    val clapiRequests = (extensions["clapi"] as? JsonObject)?.get("requests")?.jsonObject
    val folder = toCollectionFolder(nameOverride = nameOverride)
    return folder.toCollectionNode(
        preserveIds = preserveIds,
        clapiRequests = clapiRequests,
        path = "",
        existingIds = existingIds,
    )
}

fun OpenCollectionItem.toCollectionNode(
    preserveIds: Boolean = true,
    clapiRequests: JsonObject? = null,
    path: String = "",
    existingIds: Set<String> = emptySet(),
): CollectionNode {
    return when (this) {
        is FolderItem -> {
            val candidateId = (extensions["id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            val folderId = if (preserveIds && candidateId != null && candidateId !in existingIds) {
                candidateId
            } else {
                generateUuid()
            }
            val updatedExistingIds = existingIds + folderId

            val req = request
            val headers = req?.headers?.map {
                KeyValueEntry(key = it.name, value = it.value, enabled = it.disabled != true)
            }
            val userHeaders = req?.headers?.map { it.name to it.value } ?: emptyList()

            var authType: AuthType? = null
            var authUsername: String? = null
            var authPassword: String? = null
            var authToken: String? = null
            var authApiKey: String? = null
            var authApiValue: String? = null
            var authApiPlacement: String? = null

            val clapiExt = extensions["clapi"] as? JsonObject
            val isJwt = (clapiExt?.get("authType") as? JsonPrimitive)?.content == "jwt"

            when (val a = req?.auth) {
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
                    authType = null
                }
            }

            val preScript = req?.scripts?.firstOrNull { it.type == "before-request" }?.code
            val postScript = req?.scripts?.firstOrNull { it.type == "after-response" || it.type == "tests" }?.code

            val meta = clapiExt
                ?: (if (path.isNotEmpty()) clapiRequests?.get(path) as? JsonObject else null)
                ?: clapiRequests?.get(info.name) as? JsonObject
                ?: clapiRequests?.get(folderId) as? JsonObject

            if (meta != null) {
                meta["authApiPlacement"]?.jsonPrimitive?.contentOrNull?.let {
                    authApiPlacement = it
                }
            }

            val folderVariables = req?.variables?.map { v ->
                when (v) {
                    is RegularVariable -> KeyValueEntry(
                        key = v.name,
                        value = v.value?.asString ?: "",
                        enabled = v.disabled != true,
                        secret = false,
                    )
                    is SecretVariable -> KeyValueEntry(
                        key = v.name,
                        value = "",
                        enabled = v.disabled != true,
                        secret = true,
                    )
                }
            }

            CollectionNode(
                id = folderId,
                name = info.name,
                isFolder = true,
                headerEntries = headers,
                userHeaders = userHeaders,
                variableEntries = folderVariables,
                queryEntries = null,
                authType = authType,
                authUsername = authUsername,
                authPassword = authPassword,
                authToken = authToken,
                authApiKey = authApiKey,
                authApiValue = authApiValue,
                authApiPlacement = authApiPlacement,
                preRequestScript = preScript,
                testScript = postScript,
                children = items.mapIndexed { index, child ->
                    val childPath = if (path.isEmpty()) index.toString() else "$path/$index"
                    child.toCollectionNode(
                        preserveIds = preserveIds,
                        clapiRequests = clapiRequests,
                        path = childPath,
                        existingIds = updatedExistingIds,
                    )
                }.toMutableStateList()
            )
        }
        is HttpRequestItem -> {
            val reqId = generateUuid()
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

            val meta = (if (path.isNotEmpty()) clapiRequests?.get(path) as? JsonObject else null)
                ?: clapiRequests?.get(info.name) as? JsonObject
                ?: clapiRequests?.get(reqId) as? JsonObject
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
                    json.decodeFromJsonElement(McpConnectionConfig.serializer(), it)
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
                requestRef = reqRef ?: generateUuid(),
                kind = requestKind,
                mcpConfig = mcpConfig,
            )
        }
    }
}
