package io.github.crustack.ui.shared.persistence

import androidx.compose.runtime.mutableStateListOf
import io.github.crustack.core.model.AuthType
import io.github.crustack.core.model.BodyType
import io.github.crustack.core.model.FormEntryType
import io.github.crustack.core.model.HttpMethodType
import io.github.crustack.core.model.KeyValueEntry
import io.github.crustack.core.model.opencollection.OpenCollectionDocument
import io.github.crustack.ui.shared.components.encodeOrderedQuery
import io.github.crustack.ui.shared.components.joinRequestUrl
import io.github.crustack.ui.shared.components.splitRequestUrl
import io.github.crustack.ui.shared.platform.generateUuid
import io.github.crustack.ui.shared.state.CollectionNode
import io.github.crustack.ui.shared.state.FormDataEntryState
import io.github.crustack.ui.shared.state.normalizeApiKeyPlacement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class PostmanCollectionDto(
    val name: String,
    val folders: List<PostmanFolderDto>,
    val requests: List<PostmanRequestDto>,
) {
    fun toOpenCollectionDocument(): OpenCollectionDocument =
        toCollectionNode().toOpenCollectionDocument()

    internal fun toCollectionNode(): CollectionNode {
        val children = mutableStateListOf<CollectionNode>()
        folders.forEach { children.add(it.toCollectionNode()) }
        requests.forEach { children.add(it.toCollectionNode()) }
        return CollectionNode(
            id = generateUuid(),
            name = name,
            isFolder = true,
            children = children,
        )
    }
}

data class PostmanFolderDto(
    val name: String,
    val folders: List<PostmanFolderDto>,
    val requests: List<PostmanRequestDto>,
) {
    internal fun toCollectionNode(): CollectionNode {
        val children = mutableStateListOf<CollectionNode>()
        folders.forEach { children.add(it.toCollectionNode()) }
        requests.forEach { children.add(it.toCollectionNode()) }
        return CollectionNode(
            id = generateUuid(),
            name = name,
            isFolder = true,
            children = children,
        )
    }
}

data class PostmanRequestDto(
    val name: String,
    val method: String,
    val url: String,
    val preRequestScript: String? = null,
    val testScript: String? = null,
    val userHeaders: List<Pair<String, String>> = emptyList(),
    val queryEntries: List<KeyValueEntry>? = null,
    val headerEntries: List<KeyValueEntry>? = null,
    val bodyType: String? = null,
    val bodyContent: String? = null,
    val authType: String? = null,
    val authUsername: String? = null,
    val authPassword: String? = null,
    val authToken: String? = null,
    val authApiKey: String? = null,
    val authApiValue: String? = null,
    val authApiPlacement: String? = null,
) {
    internal fun toCollectionNode(): CollectionNode {
        val resolvedMethod = runCatching { HttpMethodType.valueOf(method.uppercase()) }.getOrDefault(HttpMethodType.GET)
        val resolvedBodyType = bodyType?.let { runCatching { BodyType.valueOf(it.uppercase()) }.getOrNull() }
        val resolvedAuthType = authType?.let { runCatching { AuthType.valueOf(it.uppercase()) }.getOrNull() }
        val formDataEntries = if (resolvedBodyType == BodyType.FORM_DATA) {
            parseFormDataContent(bodyContent)
        } else {
            emptyList()
        }
        val urlencodedEntries = if (resolvedBodyType == BodyType.X_WWW_FORM_URLENCODED) {
            parseUrlencodedContent(bodyContent)
        } else {
            emptyList()
        }
        return CollectionNode(
            id = generateUuid(),
            requestRef = generateUuid(),
            name = name,
            isFolder = false,
            method = resolvedMethod,
            url = compatibleUrl(url, queryEntries),
            preRequestScript = preRequestScript,
            testScript = testScript,
            userHeaders = userHeaders,
            queryEntries = queryEntries,
            headerEntries = headerEntries,
            bodyType = resolvedBodyType,
            bodyContent = bodyContent,
            formDataEntries = formDataEntries,
            urlencodedEntries = urlencodedEntries,
            authType = resolvedAuthType,
            authUsername = authUsername,
            authPassword = authPassword,
            authToken = authToken,
            authApiKey = authApiKey,
            authApiValue = authApiValue,
            authApiPlacement = normalizeApiKeyPlacement(authApiPlacement),
        )
    }
}

private fun parseFormDataContent(content: String?): List<FormDataEntryState> {
    if (content.isNullOrBlank()) return emptyList()
    return content.replace("\r\n", "\n")
        .split('\n', '&')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { token ->
            val eq = token.indexOf('=')
            if (eq >= 0) {
                FormDataEntryState(
                    key = token.substring(0, eq).trim(),
                    type = FormEntryType.TEXT,
                    value = token.substring(eq + 1),
                    enabled = true,
                )
            } else {
                FormDataEntryState(
                    key = token,
                    type = FormEntryType.TEXT,
                    value = "",
                    enabled = true,
                )
            }
        }
        .filter { it.key.isNotBlank() }
}

private fun parseUrlencodedContent(content: String?): List<FormDataEntryState> {
    if (content.isNullOrBlank()) return emptyList()
    return content
        .split('&')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { token ->
            val eq = token.indexOf('=')
            if (eq >= 0) {
                FormDataEntryState(
                    key = token.substring(0, eq).trim(),
                    value = token.substring(eq + 1),
                    enabled = true,
                )
            } else {
                FormDataEntryState(
                    key = token,
                    value = "",
                    enabled = true,
                )
            }
        }
        .filter { it.key.isNotBlank() }
}

private fun compatibleUrl(url: String, entries: List<KeyValueEntry>?): String {
    if (entries == null) return url
    val parts = splitRequestUrl(url)
    val query = encodeOrderedQuery(
        entries.filter { it.enabled && it.key.isNotBlank() }.map { it.key to it.value },
        preserveTemplates = true,
    )
    return joinRequestUrl(parts.base, query, parts.fragment)
}

/**
 * Imports Postman Collection v2 / v2.1 and Postman Environment JSON files into OpenCollection / Clapi format.
 *
 * Detection:
 *  - Collection: `info.schema` field contains "schema.getpostman.com"
 *  - Environment: top-level `values` array is present and the file is not a clapi-typed doc
 *
 * Script conversion: Postman's `pm.*` namespace is replaced with `clapi.*`.
 */
object PostmanImporter {

    private const val POSTMAN_SCHEMA_FRAGMENT = "schema.getpostman.com"

    // ── Detection ──────────────────────────────────────────────────────────────

    /**
     * Returns true if [root] looks like a Postman collection (v2 or v2.1),
     * including wrapped payloads where `root["collection"]` holds the actual data.
     */
    fun isPostmanCollection(root: JsonObject): Boolean {
        val inner = root["collection"]?.jsonObject ?: root
        val schema = inner["info"]?.jsonObject?.get("schema")?.jsonPrimitive?.contentOrNull
        return schema?.contains(POSTMAN_SCHEMA_FRAGMENT) == true
    }

    /**
     * Returns true if [root] looks like a Postman environment export.
     * A Postman environment has a `values` array and a `name`, and is not typed as a clapi document.
     */
    fun isPostmanEnvironment(root: JsonObject): Boolean {
        val notClapi = root["type"]?.jsonPrimitive?.contentOrNull?.startsWith("clapi") != true
        val hasValues = root["values"] is JsonArray
        val hasName = root["name"]?.jsonPrimitive?.contentOrNull != null
        return notClapi && hasValues && hasName
    }

    // ── Collection import ──────────────────────────────────────────────────────

    /**
     * Converts a Postman collection JSON object to [PostmanCollectionDto].
     *
     * @throws ImportExportException if required fields are absent.
     */
    fun importCollection(root: JsonObject): PostmanCollectionDto {
        val collectionRoot = root["collection"]?.jsonObject ?: root
        val info = collectionRoot["info"]?.jsonObject
            ?: throw ImportExportException("Missing 'info' in Postman collection")
        val name = info["name"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: throw ImportExportException("Missing collection name in Postman JSON")
        val items = collectionRoot["item"]?.jsonArray ?: JsonArray(emptyList())
        val (folders, requests) = parseItems(items)
        return PostmanCollectionDto(name = name, folders = folders, requests = requests)
    }

    private fun parseItems(items: JsonArray): Pair<List<PostmanFolderDto>, List<PostmanRequestDto>> {
        val folders = mutableListOf<PostmanFolderDto>()
        val requests = mutableListOf<PostmanRequestDto>()
        for (element in items) {
            val obj = runCatching { element.jsonObject }.getOrNull() ?: continue
            when {
                obj.containsKey("item") -> folders.add(parseFolder(obj))
                obj.containsKey("request") -> parseRequest(obj)?.let { requests.add(it) }
                // Items with neither "item" nor "request" are ignored
            }
        }
        return folders to requests
    }

    private fun parseFolder(obj: JsonObject): PostmanFolderDto {
        val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim() ?: "Unnamed Folder"
        val childItems = obj["item"]?.jsonArray ?: JsonArray(emptyList())
        val (subFolders, requests) = parseItems(childItems)
        return PostmanFolderDto(name = name, folders = subFolders, requests = requests)
    }

    private fun parseRequest(obj: JsonObject): PostmanRequestDto? {
        val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim() ?: "Unnamed Request"
        val requestObj = obj["request"]?.jsonObject ?: return null
        val method = normalizeMethod(requestObj["method"]?.jsonPrimitive?.contentOrNull)
        val url = parseUrl(requestObj["url"])
        val queryEntries = parseQueryEntries(requestObj["url"])
        val headers = parseHeaders(requestObj["header"])
        val (bodyType, bodyContent) = parseBody(requestObj["body"], headers)
        val auth = parseAuth(requestObj["auth"])
        val (preRequestScript, testScript) = parseEvents(obj["event"])
        return PostmanRequestDto(
            name = name,
            method = method,
            url = url,
            preRequestScript = preRequestScript.takeIf { it.isNotBlank() },
            testScript = testScript.takeIf { it.isNotBlank() },
            userHeaders = headers,
            queryEntries = queryEntries,
            bodyType = bodyType,
            bodyContent = bodyContent,
            authType = auth.type,
            authUsername = auth.username,
            authPassword = auth.password,
            authToken = auth.token,
            authApiKey = auth.apiKey,
            authApiValue = auth.apiValue,
            authApiPlacement = auth.apiPlacement,
        )
    }

    private fun parseQueryEntries(urlElement: JsonElement?): List<KeyValueEntry>? {
        val urlObj = runCatching { urlElement?.jsonObject }.getOrNull() ?: return null
        val query = urlObj["query"] as? JsonArray ?: return null
        return query.mapNotNull { element ->
            val item = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val key = item["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            KeyValueEntry(
                key = key,
                value = item["value"]?.jsonPrimitive?.contentOrNull ?: "",
                enabled = !(item["disabled"]?.jsonPrimitive?.booleanOrNull ?: false),
                secret = false,
            )
        }
    }

    private fun normalizeMethod(raw: String?): String = when (raw?.uppercase()) {
        "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD", "TRACE", "CONNECT" -> raw!!.uppercase()
        else -> "GET"
    }

    private fun parseUrl(urlElement: JsonElement?): String {
        if (urlElement == null) return ""
        if (urlElement is JsonPrimitive) return urlElement.contentOrNull ?: ""
        val urlObj = runCatching { urlElement.jsonObject }.getOrNull() ?: return ""
        // raw field contains the full URL, including query string and {{variable}} placeholders
        val raw = urlObj["raw"]?.jsonPrimitive?.contentOrNull
        if (!raw.isNullOrBlank()) return raw
        // Reconstruct from host + path + query when raw is absent
        val protocol = urlObj["protocol"]?.jsonPrimitive?.contentOrNull ?: "https"
        val host = urlObj["host"]?.jsonArray?.mapNotNull { hostPart(it) }?.joinToString(".") ?: ""
        val path = urlObj["path"]?.jsonArray?.mapNotNull { pathPart(it) }?.joinToString("/") ?: ""
        val port = urlObj["port"]?.jsonPrimitive?.contentOrNull
        val hostPart = if (port != null) "$host:$port" else host
        val query = urlObj["query"]?.jsonArray?.mapNotNull { el ->
            val q = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
            val disabled = q["disabled"]?.jsonPrimitive?.booleanOrNull ?: false
            if (disabled) return@mapNotNull null
            val key = q["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val value = q["value"]?.jsonPrimitive?.contentOrNull ?: ""
            "$key=$value"
        }?.joinToString("&")
        val pathWithQuery = if (!query.isNullOrBlank()) "$path?$query" else path
        return if (hostPart.isNotEmpty()) "$protocol://$hostPart/$pathWithQuery" else pathWithQuery
    }

    private fun hostPart(element: JsonElement): String? =
        when (element) {
            is JsonPrimitive -> element.contentOrNull
            is JsonObject -> element["value"]?.jsonPrimitive?.contentOrNull
            else -> null
        }

    private fun pathPart(element: JsonElement): String? =
        when (element) {
            is JsonPrimitive -> element.contentOrNull?.trim('/')
            is JsonObject -> element["value"]?.jsonPrimitive?.contentOrNull?.trim('/')
            else -> null
        }

    private fun parseHeaders(headerElement: JsonElement?): List<Pair<String, String>> {
        val array = runCatching { headerElement?.jsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { el ->
            val obj = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
            val disabled = obj["disabled"]?.jsonPrimitive?.booleanOrNull ?: false
            if (disabled) return@mapNotNull null
            val key = obj["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (key.isBlank()) return@mapNotNull null
            val value = obj["value"]?.jsonPrimitive?.contentOrNull ?: ""
            key to value
        }
    }

    private fun parseBody(bodyElement: JsonElement?, headers: List<Pair<String, String>>): Pair<String?, String?> {
        if (bodyElement == null || bodyElement is JsonNull) return null to null
        val bodyObj = runCatching { bodyElement.jsonObject }.getOrNull() ?: return null to null
        val mode = bodyObj["mode"]?.jsonPrimitive?.contentOrNull ?: return null to null
        return when (mode.lowercase()) {
            "raw" -> {
                val raw = bodyObj["raw"]?.jsonPrimitive?.contentOrNull ?: ""
                val language = bodyObj["options"]?.jsonObject
                    ?.get("raw")?.jsonObject
                    ?.get("language")?.jsonPrimitive?.contentOrNull ?: "text"
                val bodyType = if (isJsonRawBody(language, raw, headers)) "JSON" else "RAW_TEXT"
                bodyType to raw
            }
            "formdata" -> {
                val entries = bodyObj["formdata"]?.jsonArray?.mapNotNull { el ->
                    val obj = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
                    val disabled = obj["disabled"]?.jsonPrimitive?.booleanOrNull ?: false
                    if (disabled) return@mapNotNull null
                    val key = obj["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val value = obj["value"]?.jsonPrimitive?.contentOrNull ?: ""
                    "$key=$value"
                }?.joinToString("\n") ?: ""
                "FORM_DATA" to entries.ifEmpty { null }
            }
            "urlencoded" -> {
                val entries = bodyObj["urlencoded"]?.jsonArray?.mapNotNull { el ->
                    val obj = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
                    val disabled = obj["disabled"]?.jsonPrimitive?.booleanOrNull ?: false
                    if (disabled) return@mapNotNull null
                    val key = obj["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val value = obj["value"]?.jsonPrimitive?.contentOrNull ?: ""
                    "$key=$value"
                }?.joinToString("&") ?: ""
                "X_WWW_FORM_URLENCODED" to entries.ifEmpty { null }
            }
            "binary", "file" -> "BINARY" to null
            "graphql" -> {
                val query = bodyObj["graphql"]?.jsonObject
                    ?.get("query")?.jsonPrimitive?.contentOrNull ?: ""
                "GRAPHQL" to query
            }
            "none" -> null to null
            else -> "RAW_TEXT" to (bodyObj["raw"]?.jsonPrimitive?.contentOrNull)
        }
    }

    private fun isJsonRawBody(language: String?, raw: String, headers: List<Pair<String, String>>): Boolean {
        if (language.equals("json", ignoreCase = true)) return true

        val contentType = headers
            .firstOrNull { it.first.equals("Content-Type", ignoreCase = true) }
            ?.second
            ?.lowercase()
            ?: ""
        if ("json" in contentType || "+json" in contentType) return true

        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return false
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))
    }

    private data class ParsedAuth(
        val type: String?,
        val username: String?,
        val password: String?,
        val token: String?,
        val apiKey: String?,
        val apiValue: String?,
        val apiPlacement: String?,
    )

    private val noAuth = ParsedAuth(null, null, null, null, null, null, null)

    private fun parseAuth(authElement: JsonElement?): ParsedAuth {
        if (authElement == null || authElement is JsonNull) return noAuth
        val authObj = runCatching { authElement.jsonObject }.getOrNull() ?: return noAuth
        val type = authObj["type"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: return noAuth
        return when (type) {
            "bearer" -> {
                val token = authObj["bearer"]?.jsonArray?.kvValue("token")
                ParsedAuth("BEARER", null, null, token, null, null, null)
            }
            "basic" -> {
                val username = authObj["basic"]?.jsonArray?.kvValue("username")
                val password = authObj["basic"]?.jsonArray?.kvValue("password")
                ParsedAuth("BASIC", username, password, null, null, null, null)
            }
            "apikey" -> {
                val apiKey = authObj["apikey"]?.jsonArray?.kvValue("key")
                val apiValue = authObj["apikey"]?.jsonArray?.kvValue("value")
                val placement = normalizeApiKeyPlacement(authObj["apikey"]?.jsonArray?.kvValue("in"))
                ParsedAuth("API_KEY", null, null, null, apiKey, apiValue, placement)
            }
            "noauth", "none" -> ParsedAuth("NONE", null, null, null, null, null, null)
            else -> noAuth
        }
    }

    private fun JsonArray.kvValue(keyName: String): String? =
        mapNotNull { el ->
            val obj = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
            if (obj["key"]?.jsonPrimitive?.contentOrNull == keyName)
                obj["value"]?.jsonPrimitive?.contentOrNull
            else null
        }.firstOrNull()

    private fun parseEvents(eventElement: JsonElement?): Pair<String, String> {
        val events = runCatching { eventElement?.jsonArray }.getOrNull() ?: return "" to ""
        var preRequest = ""
        var test = ""
        for (event in events) {
            val obj = runCatching { event.jsonObject }.getOrNull() ?: continue
            val listen = obj["listen"]?.jsonPrimitive?.contentOrNull ?: continue
            val scriptObj = obj["script"]?.jsonObject ?: continue
            val exec = when (val execEl = scriptObj["exec"]) {
                is JsonArray -> execEl.mapNotNull {
                    runCatching { it.jsonPrimitive.contentOrNull }.getOrNull()
                }.joinToString("\n")
                is JsonPrimitive -> execEl.contentOrNull ?: ""
                else -> continue
            }
            val converted = convertScript(exec)
            when (listen.lowercase()) {
                "prerequest" -> preRequest = converted
                "test" -> test = converted
            }
        }
        return preRequest to test
    }

    // ── Script conversion ──────────────────────────────────────────────────────

    /**
     * Converts Postman `pm.*` API calls to the Clapi `clapi.*` equivalent.
     *
     * Only the namespace prefix and well-known aliases are rewritten; all other
     * JavaScript is left unchanged so the converted script runs unmodified.
     */
    fun convertScript(script: String): String {
        if (script.isBlank()) return script
        return script
            // ── Response chain assertions (MUST precede pm.response generic replacement) ──
            .replace("pm.response.to.have.status(", "clapi.response.statusIs(")
            .replace("pm.response.to.have.header(", "clapi.response.hasHeader(")
            .replace(Regex("""\bpm\.response\.to\.be\.ok\b"""), "clapi.response.statusOk()")

            // ── Execution control (Postman-compatible) ───────────────────────
            .replace(
                Regex("""\bpm\.execution\.setNextRequest\s*\("""),
                "clapi.execution.setNextRequest(",
            )
            .replace(
                Regex("""\bpm\.execution\.skipRequest\s*\("""),
                "clapi.execution.skipRequest(",
            )
            // pm.sendRequest is fully supported — translate directly
            .replace("pm.sendRequest(", "clapi.sendRequest(")

            // ── Modern pm.* → clapi.* namespace translation ───────────────────
            // Named operations first, then catch-all namespace prefixes.
            .replace("pm.test(", "clapi.test(")
            .replace("pm.expect(", "clapi.expect(")
            // Catch-all: handles get/set/unset/clear/has/toObject/replaceIn + any future method.
            .replace("pm.response", "clapi.response")
            .replace("pm.request", "clapi.request")
            .replace("pm.environment", "clapi.environment")
            .replace("pm.globals", "clapi.globals")
            .replace("pm.collectionVariables", "clapi.collectionVariables")
            .replace("pm.variables", "clapi.variables")
            .replace("pm.info", "clapi.info")
            .replace("pm.iterationData", "clapi.iterationData")
            .replace("pm.cookies", "clapi.cookies")

            // ── Legacy postman.* API (pre-pm era) ─────────────────────────────
            .replace("postman.getEnvironmentVariable(", "clapi.environment.get(")
            .replace("postman.setEnvironmentVariable(", "clapi.environment.set(")
            .replace("postman.clearEnvironmentVariable(", "clapi.environment.unset(")
            .replace("postman.clearEnvironmentVariables(", "clapi.environment.clear(")
            .replace("postman.getGlobalVariable(", "clapi.globals.get(")
            .replace("postman.setGlobalVariable(", "clapi.globals.set(")
            .replace("postman.clearGlobalVariable(", "clapi.globals.unset(")
            .replace("postman.clearGlobalVariables(", "clapi.globals.clear(")
            .replace(
                Regex("""\bpostman\.setNextRequest\s*\("""),
                "clapi.execution.setNextRequest(",
            )

            // ── Legacy global sandbox shortcuts ───────────────────────────────
            .replace("responseCode.code", "response.code")
            .replace("responseCode.name", "response.status")
            .replace(Regex("""\bresponseBody\b"""), "response.text()")
    }

    // ── Environment import ──────────────────────────────────────────────────────

    /**
     * Converts a Postman environment JSON object to [ClapiEnvironmentDto].
     * Disabled variables are skipped.
     *
     * @throws ImportExportException if required fields are absent.
     */
    fun importEnvironment(root: JsonObject): ClapiEnvironmentDto {
        val name = root["name"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: throw ImportExportException("Missing environment name in Postman JSON")
        val values = root["values"]?.jsonArray?.mapNotNull { el ->
            val obj = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
            val enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true
            if (!enabled) return@mapNotNull null
            val key = obj["key"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
            if (key.isEmpty()) return@mapNotNull null
            val value = obj["value"]?.jsonPrimitive?.contentOrNull ?: ""
            key to value
        } ?: emptyList()
        return ClapiEnvironmentDto(name = name, variables = values.toMap())
    }
}
