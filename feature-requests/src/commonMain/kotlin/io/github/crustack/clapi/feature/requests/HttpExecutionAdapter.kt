package io.github.crustack.clapi.feature.requests

import io.github.crustack.clapi.core.model.AuthType
import io.github.crustack.clapi.core.model.BodyType
import io.github.crustack.clapi.core.model.FormDataEntry
import io.github.crustack.clapi.core.model.FormEntryType
import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.core.model.KeyValueEntry
import io.github.crustack.clapi.core.model.RequestBody
import io.github.crustack.clapi.core.model.RequestDefinition
import io.github.crustack.clapi.core.model.opencollection.AuthApiKey
import io.github.crustack.clapi.core.model.opencollection.AuthBasic
import io.github.crustack.clapi.core.model.opencollection.AuthBearer
import io.github.crustack.clapi.core.model.opencollection.AuthConfig
import io.github.crustack.clapi.core.model.opencollection.AuthDigest
import io.github.crustack.clapi.core.model.opencollection.AuthInherit
import io.github.crustack.clapi.core.model.opencollection.AuthOAuth2
import io.github.crustack.clapi.core.model.opencollection.Environment
import io.github.crustack.clapi.core.model.opencollection.FileBody
import io.github.crustack.clapi.core.model.opencollection.FolderItem
import io.github.crustack.clapi.core.model.opencollection.FormUrlEncodedBody
import io.github.crustack.clapi.core.model.opencollection.HttpRequestBody
import io.github.crustack.clapi.core.model.opencollection.HttpRequestHeader
import io.github.crustack.clapi.core.model.opencollection.HttpRequestItem
import io.github.crustack.clapi.core.model.opencollection.MultipartFormBody
import io.github.crustack.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.crustack.clapi.core.model.opencollection.RawBody
import io.github.crustack.clapi.core.model.opencollection.RegularVariable
import io.github.crustack.clapi.core.model.opencollection.SecretVariable
import io.github.crustack.clapi.core.network.VariableResolver
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

interface HttpExecutionAdapter {
    /**
     * Resolves inherited defaults (collection headers, folder headers, folder auth)
     * and compiles a canonical [HttpRequestItem] into an executable [RequestDefinition].
     *
     * @param requestItem The target request item to execute.
     * @param parentHierarchy Chain of parent folders up to the collection root for inheritance.
     * @param collectionRoot Root collection containing defaults.
     * @return Fully resolved [RequestDefinition] ready for network execution.
     */
    fun toExecutableRequest(
        requestItem: HttpRequestItem,
        parentHierarchy: List<FolderItem> = emptyList(),
        collectionRoot: OpenCollectionDocument? = null,
    ): RequestDefinition

    /**
     * Builds ordered variable resolution layers in precedence order:
     * Request/runtime > Environment > Folder (leaf to root) > Collection > Global
     */
    fun buildVariableLayers(
        requestItem: HttpRequestItem? = null,
        runtimeVariables: Map<String, String> = emptyMap(),
        environment: Environment? = null,
        environmentVariables: Map<String, String> = emptyMap(),
        parentHierarchy: List<FolderItem> = emptyList(),
        collectionRoot: OpenCollectionDocument? = null,
        globalVariables: Map<String, String> = emptyMap(),
        secretResolver: ((variableName: String) -> String?)? = null,
    ): List<Map<String, String>>

    /**
     * Resolves template variables in [value] against [variableLayers].
     */
    fun resolveVariables(
        value: String,
        variableLayers: List<Map<String, String>>,
        removeUnresolved: Boolean = false,
    ): String

    companion object : HttpExecutionAdapter by DefaultHttpExecutionAdapter
}

object DefaultHttpExecutionAdapter : HttpExecutionAdapter {

    override fun toExecutableRequest(
        requestItem: HttpRequestItem,
        parentHierarchy: List<FolderItem>,
        collectionRoot: OpenCollectionDocument?,
    ): RequestDefinition {
        val rootFirstFolders = normalizeToRootFirst(parentHierarchy)
        val leafFirstFolders = rootFirstFolders.reversed()

        val httpMethod = HttpMethodType.entries.firstOrNull {
            it.name.equals(requestItem.http.method, ignoreCase = true)
        } ?: HttpMethodType.GET

        val queryParams = requestItem.http.params
            .filter { it.type == "query" && it.disabled != true }
            .map {
                KeyValueEntry(
                    key = it.name,
                    value = it.value,
                    enabled = true
                )
            }

        val effectiveHeaders = mergeHeaders(
            collectionHeaders = collectionRoot?.request?.headers,
            folderHeaders = rootFirstFolders.mapNotNull { it.request?.headers },
            requestHeaders = requestItem.http.headers
        )

        val effectiveAuth = resolveAuth(
            requestAuth = requestItem.http.auth,
            parentFolders = leafFirstFolders,
            collectionRoot = collectionRoot,
            requestItem = requestItem
        )

        val effectiveBody = mapBody(
            body = requestItem.http.body,
            requestItem = requestItem,
            collectionRoot = collectionRoot
        )

        val reqId = extractRequestId(requestItem, collectionRoot)

        return RequestDefinition(
            id = reqId,
            name = requestItem.info.name,
            method = httpMethod,
            url = requestItem.http.url,
            queryParams = queryParams,
            headers = effectiveHeaders,
            cookies = emptyList(),
            auth = effectiveAuth,
            body = effectiveBody,
            tags = requestItem.info.tags,
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
        )
    }

    override fun buildVariableLayers(
        requestItem: HttpRequestItem?,
        runtimeVariables: Map<String, String>,
        environment: Environment?,
        environmentVariables: Map<String, String>,
        parentHierarchy: List<FolderItem>,
        collectionRoot: OpenCollectionDocument?,
        globalVariables: Map<String, String>,
        secretResolver: ((variableName: String) -> String?)?,
    ): List<Map<String, String>> {
        val layers = mutableListOf<Map<String, String>>()

        // 1. Request / Runtime variables [Highest Precedence]
        val requestVars = mutableMapOf<String, String>()
        requestVars.putAll(runtimeVariables)
        if (requestVars.isNotEmpty()) {
            layers.add(requestVars)
        }

        // 2. Selected Environment variables
        val envVars = mutableMapOf<String, String>()
        environment?.variables?.forEach { v ->
            if (v.disabled != true) {
                when (v) {
                    is RegularVariable -> v.value?.asString?.let { envVars[v.name] = it }
                    is SecretVariable -> secretResolver?.invoke(v.name)?.let { envVars[v.name] = it }
                }
            }
        }
        envVars.putAll(environmentVariables)
        if (envVars.isNotEmpty()) {
            layers.add(envVars)
        }

        // 3. Folder variables (immediate parent up to root parent)
        val leafFirstFolders = normalizeToRootFirst(parentHierarchy).reversed()
        for (folder in leafFirstFolders) {
            val fVars = mutableMapOf<String, String>()
            folder.request?.variables?.forEach { v ->
                if (v.disabled != true) {
                    when (v) {
                        is RegularVariable -> v.value?.asString?.let { fVars[v.name] = it }
                        is SecretVariable -> secretResolver?.invoke(v.name)?.let { fVars[v.name] = it }
                    }
                }
            }
            if (fVars.isNotEmpty()) {
                layers.add(fVars)
            }
        }

        // 4. Collection-level variables
        val colVars = mutableMapOf<String, String>()
        collectionRoot?.request?.variables?.forEach { v ->
            if (v.disabled != true) {
                when (v) {
                    is RegularVariable -> v.value?.asString?.let { colVars[v.name] = it }
                    is SecretVariable -> secretResolver?.invoke(v.name)?.let { colVars[v.name] = it }
                }
            }
        }
        if (colVars.isNotEmpty()) {
            layers.add(colVars)
        }

        // 5. Global variables [Lowest Precedence]
        if (globalVariables.isNotEmpty()) {
            layers.add(globalVariables)
        }

        return layers
    }

    override fun resolveVariables(
        value: String,
        variableLayers: List<Map<String, String>>,
        removeUnresolved: Boolean,
    ): String = VariableResolver.resolve(value, variableLayers, removeUnresolved)

    private fun normalizeToRootFirst(parentHierarchy: List<FolderItem>): List<FolderItem> {
        if (parentHierarchy.size <= 1) return parentHierarchy
        if (containsItem(parentHierarchy[0], parentHierarchy[1])) {
            return parentHierarchy
        }
        if (containsItem(parentHierarchy.last(), parentHierarchy.first())) {
            return parentHierarchy.reversed()
        }
        // Default assumption when callers traverse from leaf up to root:
        return parentHierarchy.reversed()
    }

    private fun containsItem(parent: FolderItem, target: FolderItem): Boolean {
        for (item in parent.items) {
            if (item is FolderItem) {
                if (item == target || item.info.name == target.info.name) return true
                if (containsItem(item, target)) return true
            }
        }
        return false
    }

    private fun mergeHeaders(
        collectionHeaders: List<HttpRequestHeader>?,
        folderHeaders: List<List<HttpRequestHeader>>,
        requestHeaders: List<HttpRequestHeader>,
    ): List<KeyValueEntry> {
        val result = mutableListOf<KeyValueEntry>()

        fun applyHeaderList(headers: List<HttpRequestHeader>) {
            for (h in headers) {
                val entry = KeyValueEntry(
                    key = h.name,
                    value = h.value,
                    enabled = (h.disabled != true)
                )
                val existingIndex = result.indexOfFirst { it.key.equals(h.name, ignoreCase = true) }
                if (existingIndex >= 0) {
                    result[existingIndex] = entry
                } else {
                    result.add(entry)
                }
            }
        }

        if (collectionHeaders != null) {
            applyHeaderList(collectionHeaders)
        }
        for (fHeaders in folderHeaders) {
            applyHeaderList(fHeaders)
        }
        applyHeaderList(requestHeaders)

        return result.filter { it.enabled && it.key.isNotBlank() }
    }

    private fun resolveAuth(
        requestAuth: AuthConfig?,
        parentFolders: List<FolderItem>,
        collectionRoot: OpenCollectionDocument?,
        requestItem: HttpRequestItem,
    ): io.github.crustack.clapi.core.model.AuthConfig {
        val effectiveAuth = when {
            requestAuth != null && requestAuth !is AuthInherit -> requestAuth
            else -> {
                var foundAuth: AuthConfig? = null
                for (folder in parentFolders) {
                    val fAuth = folder.request?.auth
                    if (fAuth != null && fAuth !is AuthInherit) {
                        foundAuth = fAuth
                        break
                    }
                }
                foundAuth ?: collectionRoot?.request?.auth?.takeIf { it !is AuthInherit }
            }
        }

        return mapToModelAuthConfig(effectiveAuth, requestItem, collectionRoot)
    }

    private fun mapToModelAuthConfig(
        auth: AuthConfig?,
        requestItem: HttpRequestItem,
        collectionRoot: OpenCollectionDocument?,
    ): io.github.crustack.clapi.core.model.AuthConfig {
        if (auth == null || auth is AuthInherit) {
            return io.github.crustack.clapi.core.model.AuthConfig(type = AuthType.NONE)
        }
        return when (auth) {
            is AuthBasic -> io.github.crustack.clapi.core.model.AuthConfig(
                type = AuthType.BASIC,
                params = mapOf("username" to auth.username, "password" to auth.password)
            )
            is AuthBearer -> {
                val isJwt = isJwtAuth(requestItem, collectionRoot)
                io.github.crustack.clapi.core.model.AuthConfig(
                    type = if (isJwt) AuthType.JWT else AuthType.BEARER,
                    params = mapOf("token" to auth.token)
                )
            }
            is AuthApiKey -> io.github.crustack.clapi.core.model.AuthConfig(
                type = AuthType.API_KEY,
                params = mapOf("key" to auth.key, "value" to auth.value),
                placement = auth.placement
            )
            is AuthOAuth2 -> io.github.crustack.clapi.core.model.AuthConfig(
                type = AuthType.OAUTH2,
                params = buildMap {
                    auth.accessTokenUrl?.let { put("accessTokenUrl", it) }
                    auth.refreshTokenUrl?.let { put("refreshTokenUrl", it) }
                    auth.authorizationUrl?.let { put("authorizationUrl", it) }
                    auth.redirectUri?.let { put("redirectUri", it) }
                    auth.scope?.let { put("scope", it) }
                    auth.credentials?.clientId?.let { put("clientId", it) }
                    auth.credentials?.clientSecret?.let { put("clientSecret", it) }
                    auth.resourceOwner?.username?.let { put("username", it) }
                    auth.resourceOwner?.password?.let { put("password", it) }
                }
            )
            is AuthDigest -> io.github.crustack.clapi.core.model.AuthConfig(
                type = AuthType.NONE,
                params = mapOf("username" to auth.username, "password" to auth.password)
            )
            else -> io.github.crustack.clapi.core.model.AuthConfig(type = AuthType.NONE)
        }
    }

    private fun isJwtAuth(requestItem: HttpRequestItem, collectionRoot: OpenCollectionDocument?): Boolean {
        val rootClapi = collectionRoot?.extensions?.get("clapi") as? JsonObject
        val rootRequests = rootClapi?.get("requests") as? JsonObject
        val reqMeta = rootRequests?.get(requestItem.info.name) as? JsonObject
            ?: rootRequests?.get(extractRequestId(requestItem, collectionRoot)) as? JsonObject
        if (reqMeta?.get("authType")?.jsonPrimitive?.contentOrNull == "jwt") return true
        val itemClapi = requestItem.extensions["clapi"] as? JsonObject
        if (itemClapi?.get("authType")?.jsonPrimitive?.contentOrNull == "jwt") return true
        return false
    }

    private fun mapBody(
        body: HttpRequestBody?,
        requestItem: HttpRequestItem,
        collectionRoot: OpenCollectionDocument?,
    ): RequestBody {
        if (body == null) return RequestBody(type = BodyType.NONE)
        return when (body) {
            is RawBody -> {
                val rootClapi = collectionRoot?.extensions?.get("clapi") as? JsonObject
                val rootRequests = rootClapi?.get("requests") as? JsonObject
                val reqMeta = rootRequests?.get(requestItem.info.name) as? JsonObject
                    ?: rootRequests?.get(extractRequestId(requestItem, collectionRoot)) as? JsonObject
                val clapiBodyType = reqMeta?.get("bodyType")?.jsonPrimitive?.contentOrNull
                    ?: (requestItem.extensions["clapi"] as? JsonObject)?.get("bodyType")?.jsonPrimitive?.contentOrNull
                val customType = clapiBodyType?.let { runCatching { BodyType.valueOf(it) }.getOrNull() }
                val effectiveType = customType ?: when (body.type.lowercase()) {
                    "json" -> BodyType.JSON
                    "xml" -> BodyType.XML
                    "sparql", "text" -> BodyType.RAW_TEXT
                    else -> BodyType.RAW_TEXT
                }
                RequestBody(type = effectiveType, content = body.data)
            }
            is FormUrlEncodedBody -> {
                val formEntries = body.data
                    .filter { it.disabled != true }
                    .map {
                        KeyValueEntry(
                            key = it.name,
                            value = it.value,
                            enabled = true
                        )
                    }
                RequestBody(type = BodyType.X_WWW_FORM_URLENCODED, formEntries = formEntries)
            }
            is MultipartFormBody -> {
                val enabledParts = body.data.filter { it.disabled != true }
                val formDataEntries = enabledParts.map { part ->
                    val isFile = part.type.equals("file", ignoreCase = true)
                    val strVal = when (val v = part.value) {
                        is JsonPrimitive -> v.contentOrNull ?: ""
                        else -> v.toString()
                    }
                    FormDataEntry(
                        key = part.name,
                        type = if (isFile) FormEntryType.FILE else FormEntryType.TEXT,
                        value = strVal,
                        description = part.description?.content ?: "",
                        enabled = true
                    )
                }
                val formEntries = enabledParts.map { part ->
                    val strVal = when (val v = part.value) {
                        is JsonPrimitive -> v.contentOrNull ?: ""
                        else -> v.toString()
                    }
                    KeyValueEntry(key = part.name, value = strVal, enabled = true)
                }
                RequestBody(type = BodyType.FORM_DATA, formEntries = formEntries, formDataEntries = formDataEntries)
            }
            is FileBody -> {
                val selected = body.data.firstOrNull { it.selected } ?: body.data.firstOrNull()
                RequestBody(
                    type = BodyType.BINARY,
                    binaryName = selected?.filePath,
                )
            }
        }
    }

    private fun extractRequestId(
        requestItem: HttpRequestItem,
        collectionRoot: OpenCollectionDocument?,
    ): String {
        val rootClapi = collectionRoot?.extensions?.get("clapi") as? JsonObject
        val rootRequests = rootClapi?.get("requests") as? JsonObject
        val reqMeta = rootRequests?.get(requestItem.info.name) as? JsonObject
        val fromRoot = reqMeta?.get("id")?.jsonPrimitive?.contentOrNull
        if (!fromRoot.isNullOrBlank()) return fromRoot

        val fromExt = requestItem.extensions["id"]?.jsonPrimitive?.contentOrNull
        if (!fromExt.isNullOrBlank()) return fromExt

        return requestItem.info.name
    }
}
