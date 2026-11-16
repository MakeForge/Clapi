package io.github.makeforge.clapi.ui.shared.components.executor

import io.github.makeforge.clapi.core.model.AuthConfig
import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.model.BodyType
import io.github.makeforge.clapi.core.model.FormEntryType
import io.github.makeforge.clapi.core.model.GraphQlBody
import io.github.makeforge.clapi.core.model.RequestBody
import io.github.makeforge.clapi.core.model.json.Json5
import io.github.makeforge.clapi.core.network.VariableResolver
import io.github.makeforge.clapi.core.network.encodeBase64
import io.github.makeforge.clapi.core.network.encodeGraphQlEnvelope
import io.github.makeforge.clapi.ui.shared.components.encodeOrderedQuery
import io.github.makeforge.clapi.ui.shared.components.joinRequestUrl
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.RequestTabState

fun buildCurlCommand(
    tab: RequestTabState,
    variableLayers: List<Map<String, String>> = emptyList(),
    allowJson5: Boolean = true,
    state: AppState? = null,
): String {
    val request = prepareTabRequest(tab, state = state)
    val parts = mutableListOf("curl", "-X ${request.method.name}")

    preparedHeaderList(request, variableLayers).forEach { (key, value) ->
        parts += "-H ${shellQuote("$key: $value")}"
    }
    val body = prepareCopyBody(request.body, variableLayers, allowJson5)
    if (body.multipart != null) {
        body.multipart.forEach { (key, value) -> parts += "--form-string ${shellQuote("$key=$value")}" }
    } else if (body.content != null) {
        parts += "--data-binary ${shellQuote(body.content)}"
    }

    // Build URL with inline query params
    val resolvedUrl = buildUrlWithParams(request, variableLayers, removeUnresolved = true)
    parts += shellQuote(resolvedUrl)
    return parts.joinToString(" \\\n  ")
}


fun buildCurlCommandRaw(tab: RequestTabState, allowJson5: Boolean = true): String =
    buildCurlCommand(tab, emptyList(), allowJson5)

/** Python `requests` snippet resolving {{vars}}. */
fun buildPythonCommand(
    tab: RequestTabState,
    variableLayers: List<Map<String, String>> = emptyList(),
    allowJson5: Boolean = true,
    state: AppState? = null,
): String {
    val request = prepareTabRequest(tab, state = state)
    val sb = StringBuilder()
    sb.appendLine("import requests")
    sb.appendLine()

    val headers = preparedHeaderList(request, variableLayers)
    if (headers.isNotEmpty()) {
        sb.appendLine("headers = {")
        headers.forEach { (k, v) -> sb.appendLine("    ${pyStr(k)}: ${pyStr(v)},") }
        sb.appendLine("}")
        sb.appendLine()
    }

    val url = buildUrlWithParams(request, variableLayers, removeUnresolved = true)
    val method = request.method.name.uppercase()

    val body = prepareCopyBody(request.body, variableLayers, allowJson5)
    if (body.multipart != null) {
        sb.appendLine("files = [")
        body.multipart.forEach { (key, value) ->
            sb.appendLine("    (${pyStr(key)}, (None, ${pyStr(value)})),")
        }
        sb.appendLine("]")
        sb.appendLine()
    } else if (body.content != null) {
        sb.appendLine("data = ${pyStr(body.content)}")
        sb.appendLine()
    }
    sb.append("response = requests.request(${pyStr(method)}, ${pyStr(url)}")
    if (headers.isNotEmpty()) sb.append(", headers=headers")
    if (body.multipart != null) sb.append(", files=files")
    else if (body.content != null) sb.append(", data=data")
    sb.appendLine(")")
    sb.appendLine("print(response.status_code, response.text)")
    return sb.toString().trimEnd()
}

/** HTTPie CLI snippet resolving {{vars}}. */
fun buildHTTPieCommand(
    tab: RequestTabState,
    variableLayers: List<Map<String, String>> = emptyList(),
    allowJson5: Boolean = true,
    state: AppState? = null,
): String {
    val request = prepareTabRequest(tab, state = state)
    val parts = mutableListOf("http", tab.method.name)
    val url = buildUrlWithParams(request, variableLayers, removeUnresolved = true)
    parts += shellQuote(url)

    preparedHeaderList(request, variableLayers, removeUnresolved = true).forEach { (k, v) -> parts += shellQuote("$k:$v") }

    if (request.body.type != BodyType.NONE && tab.bodyContent.isNotBlank()) {
        parts += "--raw"
        parts += shellQuote(prepareCopyBody(request.body, variableLayers, allowJson5).content.orEmpty())
    }

    return parts.joinToString(" \\\n  ")
}

/** PowerShell `Invoke-WebRequest` snippet resolving {{vars}}. */
fun buildPowerShellCommand(
    tab: RequestTabState,
    variableLayers: List<Map<String, String>> = emptyList(),
    allowJson5: Boolean = true,
    state: AppState? = null,
): String {
    val request = prepareTabRequest(tab, state = state)
    val sb = StringBuilder()
    val url = buildUrlWithParams(request, variableLayers, removeUnresolved = true)
    val headers = preparedHeaderList(request, variableLayers)

    if (headers.isNotEmpty()) {
        sb.appendLine("\$headers = @{")
        headers.forEach { (k, v) -> sb.appendLine("    '${k.replace("'", "''")}'='${v.replace("'", "''")}'") }
        sb.appendLine("}")
        sb.appendLine()
    }

    sb.append("Invoke-WebRequest -Uri '${url.replace("'", "''")}' -Method ${request.method.name}")
    if (headers.isNotEmpty()) sb.append(" -Headers \$headers")

    val body = prepareCopyBody(request.body, variableLayers, allowJson5)
    if (body.multipart != null) {
        val fields = body.multipart.joinToString("; ") { (key, value) ->
            "'${key.replace("'", "''")}'='${value.replace("'", "''")}'"
        }
        sb.append(" -Form @{$fields}")
    } else if (body.content != null) {
        sb.append(" -Body '${body.content.replace("'", "''")}'")
    }

    return sb.toString().trimEnd()
}

// ── Curl / format helpers ────────────────────────────────────────────

private fun buildUrlWithParams(
    request: PreparedTabRequest,
    variableLayers: List<Map<String, String>>,
    removeUnresolved: Boolean = false,
): String {
    val queryEntries = request.queryParams.filter { it.enabled && it.key.isNotBlank() }.map { p ->
        VariableResolver.resolve(p.key, variableLayers, removeUnresolved) to
            VariableResolver.resolve(p.value, variableLayers, removeUnresolved)
    }.toMutableList()
    val auth = request.auth
    if (auth.type == AuthType.API_KEY && effectiveApiKeyPlacement(auth) == "query") {
        val key = VariableResolver.resolve(auth.params["key"].orEmpty(), variableLayers, removeUnresolved)
        if (key.isNotBlank()) {
            queryEntries += key to VariableResolver.resolve(
                auth.params["value"].orEmpty(), variableLayers, removeUnresolved,
            )
        }
    }
    // Strip any embedded query string from resolvedBase: tab.params is the single
    // source of truth for query parameters (kept in sync by syncParamsFromUrl /
    // syncUrlFromParams). Appending to a URL that already contains those params
    // would duplicate every parameter in the final request URL.
    val qs = encodeOrderedQuery(queryEntries, preserveTemplates = !removeUnresolved)
    val resolvedBase = VariableResolver.resolve(request.url, variableLayers, removeUnresolved)
    val resolvedFragment = VariableResolver.resolve(request.fragment, variableLayers, removeUnresolved)
    return joinRequestUrl(resolvedBase, qs, resolvedFragment)
}

private data class PreparedCopyBody(
    val content: String? = null,
    val multipart: List<Pair<String, String>>? = null,
)

private fun prepareCopyBody(
    body: RequestBody,
    variableLayers: List<Map<String, String>>,
    allowJson5: Boolean,
): PreparedCopyBody {
    fun resolve(value: String) = VariableResolver.resolve(value, variableLayers, removeUnresolved = true)
    return when (body.type) {
        BodyType.NONE -> PreparedCopyBody()
        BodyType.BINARY -> error("Embedded binary has no reusable file path")
        BodyType.FORM_DATA -> {
            require(body.formDataEntries.none { it.type == FormEntryType.FILE }) {
                "Embedded multipart file has no reusable file path"
            }
            val entries = if (body.formDataEntries.isNotEmpty()) {
                body.formDataEntries.map { it.key to resolve(it.value) }
            } else body.formEntries.map { it.key to resolve(it.value) }
            PreparedCopyBody(multipart = entries)
        }
        BodyType.X_WWW_FORM_URLENCODED -> PreparedCopyBody(content =
            encodeOrderedQuery(body.formEntries.filter { it.enabled }.map { it.key to resolve(it.value) }))
        BodyType.GRAPHQL -> PreparedCopyBody(content =
            encodeGraphQlEnvelope(body.graphQl ?: GraphQlBody(), variableLayers, allowJson5))
        else -> PreparedCopyBody(content = body.content?.let { content ->
            if (allowJson5 && body.type == BodyType.JSON && content.isNotBlank()) {
                Json5.toWireJson(resolve(content)).getOrDefault(resolve(content))
            } else resolve(content)
        })
    }
}

internal fun copyHeaderList(
    tab: RequestTabState,
    variableLayers: List<Map<String, String>>,
    omitMultipartContentType: Boolean = true,
): List<Pair<String, String>> = preparedHeaderList(
    prepareTabRequest(tab), variableLayers, removeUnresolved = true,
    omitMultipartContentType = omitMultipartContentType,
)

private fun preparedHeaderList(
    request: PreparedTabRequest,
    variableLayers: List<Map<String, String>>,
    removeUnresolved: Boolean = true,
    omitMultipartContentType: Boolean = true,
): List<Pair<String, String>> {
    fun resolve(s: String) = VariableResolver.resolve(s, variableLayers, removeUnresolved)
    val list = mutableListOf<Pair<String, String>>()
    request.headers.filter { it.enabled && it.key.isNotBlank() }
        .forEach { list += resolve(it.key) to resolve(it.value) }
    val auth = request.auth
    when (auth.type) {
        AuthType.BASIC -> {
            val encoded = "${resolve(auth.params["username"].orEmpty())}:${resolve(auth.params["password"].orEmpty())}"
                .encodeToByteArray().encodeBase64()
            list += "Authorization" to "Basic $encoded"
        }
        AuthType.BEARER, AuthType.JWT -> {
            val token = resolve(auth.params["token"].orEmpty()).trim()
            if (token.isNotEmpty()) list += "Authorization" to "Bearer $token"
        }
        AuthType.API_KEY -> {
            if (effectiveApiKeyPlacement(auth) != "query") {
                val key = resolve(auth.params["key"].orEmpty())
                if (key.isNotBlank()) list += key to resolve(auth.params["value"].orEmpty())
            }
        }
        else -> Unit
    }
    return list.filterNot { (key, _) ->
        omitMultipartContentType && request.body.type == BodyType.FORM_DATA &&
            key.equals("Content-Type", ignoreCase = true)
    }
}

internal fun effectiveApiKeyPlacement(auth: AuthConfig): String =
    io.github.makeforge.clapi.ui.shared.state.normalizeApiKeyPlacement(auth.placement ?: auth.params["placement"])

private fun pyStr(s: String): String = "\"" + s
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
    .replace("\t", "\\t") + "\""

internal fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"

