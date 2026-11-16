package io.github.makeforge.clapi.core.storage.exporters

import io.github.makeforge.clapi.core.model.opencollection.Environment
import io.github.makeforge.clapi.core.model.opencollection.FileBody
import io.github.makeforge.clapi.core.model.opencollection.FolderItem
import io.github.makeforge.clapi.core.model.opencollection.FormUrlEncodedBody
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestItem
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestParamType
import io.github.makeforge.clapi.core.model.opencollection.MultipartFormBody
import io.github.makeforge.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.makeforge.clapi.core.model.opencollection.OpenCollectionItem
import io.github.makeforge.clapi.core.model.opencollection.RawBody
import io.github.makeforge.clapi.core.model.opencollection.RequestBodyType
import io.github.makeforge.clapi.core.model.opencollection.RegularVariable
import io.github.makeforge.clapi.core.model.opencollection.SecretVariable
import io.github.makeforge.clapi.core.storage.opencollection.OpenCollectionSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object OpenApiExporter {

    fun exportToYaml(document: OpenCollectionDocument): String {
        val root = exportToJsonElement(document)
        return OpenCollectionSerializer.jsonElementToYaml(root).trim()
    }

    fun exportToJsonElement(document: OpenCollectionDocument): JsonObject {
        val title = document.info.name.trim().ifBlank { "OpenAPI Specification" }
        val description = document.info.summary?.takeIf { it.isNotBlank() }
        val environments = document.config?.environments.orEmpty()

        val servers = buildServers(environments)
        val paths = buildPaths(document.items)

        return buildJsonObject {
            put("openapi", "3.0.3")
            put("info", buildJsonObject {
                put("title", title)
                description?.let { put("description", it) }
                put("version", "1.0.0")
            })
            put("servers", servers)
            put("paths", paths)
            if (environments.isNotEmpty()) {
                put("x-environments", buildJsonArray {
                    environments.forEach { env ->
                        add(buildJsonObject {
                            put("name", env.name)
                            put("variables", buildJsonObject {
                                env.variables.forEach { entry ->
                                    val key = entry.name.trim()
                                    if (key.isNotEmpty()) {
                                        val strVal = when (entry) {
                                            is RegularVariable -> entry.value?.asString ?: ""
                                            is SecretVariable -> ""
                                        }
                                        put(key, strVal)
                                    }
                                }
                            })
                        })
                    }
                })
            }
        }
    }

    private fun buildServers(environments: List<Environment>): JsonArray = buildJsonArray {
        if (environments.isEmpty()) {
            add(buildJsonObject {
                put("url", "http://localhost")
                put("description", "Default Server")
            })
        } else {
            environments.forEach { env ->
                val serverUrl = findServerUrl(env)
                add(buildJsonObject {
                    put("url", serverUrl)
                    put("description", env.name)
                })
            }
        }
    }

    private fun findServerUrl(env: Environment): String {
        for (entry in env.variables) {
            val key = entry.name.trim()
            val value = when (entry) {
                is RegularVariable -> entry.value?.asString?.trim() ?: ""
                is SecretVariable -> ""
            }
            if (key.equals("baseUrl", ignoreCase = true) ||
                key.equals("base_url", ignoreCase = true) ||
                key.equals("url", ignoreCase = true) ||
                key.equals("host", ignoreCase = true)
            ) {
                if (value.isNotEmpty()) return value
            }
        }
        for (entry in env.variables) {
            val value = when (entry) {
                is RegularVariable -> entry.value?.asString?.trim() ?: ""
                is SecretVariable -> ""
            }
            if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
                return value
            }
        }
        return "http://localhost"
    }

    private data class OperationEntry(
        val method: String,
        val path: String,
        val item: HttpRequestItem,
        val tags: List<String>,
    )

    private fun buildPaths(items: List<OpenCollectionItem>): JsonObject {
        val operations = mutableListOf<OperationEntry>()
        collectOperations(items, emptyList(), operations)

        val pathsMap = mutableMapOf<String, MutableMap<String, JsonObject>>()

        for (op in operations) {
            val path = op.path
            val method = op.method.lowercase()
            val opObject = buildOperation(op)
            pathsMap.getOrPut(path) { mutableMapOf() }[method] = opObject
        }

        return buildJsonObject {
            for ((path, methods) in pathsMap) {
                put(path, buildJsonObject {
                    for ((method, opObj) in methods) {
                        put(method, opObj)
                    }
                })
            }
        }
    }

    private fun collectOperations(
        items: List<OpenCollectionItem>,
        currentPath: List<String>,
        out: MutableList<OperationEntry>,
    ) {
        for (item in items) {
            when (item) {
                is FolderItem -> {
                    val folderName = item.info.name.trim().ifBlank { "Folder" }
                    collectOperations(item.items, currentPath + folderName, out)
                }
                is HttpRequestItem -> {
                    val cleanPath = normalizePath(item.http.url)
                    val method = item.http.method.trim().ifBlank { "GET" }
                    val tags = if (currentPath.isNotEmpty()) listOf(currentPath.last()) else listOf("Default")
                    out.add(OperationEntry(method = method, path = cleanPath, item = item, tags = tags))
                }
                is io.github.makeforge.clapi.core.model.opencollection.GrpcRequestItem -> {
                    // OpenAPI 3.0 export only covers HTTP requests
                }
                is io.github.makeforge.clapi.core.model.opencollection.WebSocketRequestItem -> {
                    // OpenAPI 3.0 export only covers HTTP requests
                }
                is io.github.makeforge.clapi.core.model.opencollection.GraphQLRequestItem -> {
                    // OpenAPI 3.0 export only covers HTTP requests
                }
            }
        }
    }

    internal fun normalizePath(rawUrl: String): String {
        var u = rawUrl.trim()
        if (u.isEmpty()) return "/"

        // Remove query part
        if (u.contains("?")) {
            u = u.substringBefore("?")
        }

        // If URL starts with {{...}}, strip the variable prefix e.g. {{baseUrl}}
        if (u.startsWith("{{") && u.contains("}}")) {
            u = u.substringAfter("}}")
        } else if (u.startsWith("http://", ignoreCase = true) || u.startsWith("https://", ignoreCase = true)) {
            // Strip scheme and authority
            val withoutScheme = u.substringAfter("://")
            u = if (withoutScheme.contains("/")) {
                "/" + withoutScheme.substringAfter("/")
            } else {
                "/"
            }
        }

        // Ensure leading slash
        var path = if (u.startsWith("/")) u else "/$u"

        // Convert Express-style path params (:param) to OpenAPI style ({param})
        path = path.replace(Regex(":([a-zA-Z0-9_]+)")) { "{" + it.groupValues[1] + "}" }

        // Convert any remaining {{param}} to {param}
        path = path.replace(Regex("\\{\\{([a-zA-Z0-9_]+)\\}\\}")) { "{" + it.groupValues[1] + "}" }

        // Clean redundant consecutive slashes
        path = path.replace(Regex("/+"), "/")

        return if (path.isEmpty()) "/" else path
    }

    private fun buildOperation(entry: OperationEntry): JsonObject {
        val item = entry.item
        val summary = item.info.name.trim().ifBlank { "${entry.method.uppercase()} ${entry.path}" }
        val description = item.docs?.takeIf { it.isNotBlank() }

        // Extract path parameters from the path pattern
        val pathParamNames = Regex("\\{([a-zA-Z0-9_]+)\\}").findAll(entry.path)
            .map { it.groupValues[1] }
            .toSet()

        val parametersList = mutableListOf<JsonObject>()

        // Add path parameters
        for (paramName in pathParamNames) {
            val matchingParam = item.http.params.firstOrNull { it.name == paramName && it.type == HttpRequestParamType.PATH }
            parametersList.add(buildJsonObject {
                put("name", paramName)
                put("in", "path")
                put("required", true)
                put("schema", buildJsonObject { put("type", "string") })
                if (matchingParam != null && matchingParam.value.isNotBlank()) {
                    put("example", matchingParam.value)
                }
            })
        }

        // Add query parameters
        val queryParams = item.http.params.filter { it.type == HttpRequestParamType.QUERY }
        val handledQueryParamNames = mutableSetOf<String>()
        for (param in queryParams) {
            val name = param.name.trim()
            if (name.isNotEmpty() && handledQueryParamNames.add(name)) {
                parametersList.add(buildJsonObject {
                    put("name", name)
                    put("in", "query")
                    put("required", false)
                    put("schema", buildJsonObject { put("type", "string") })
                    if (param.value.isNotBlank()) {
                        put("example", param.value)
                    }
                })
            }
        }

        // Also check if URL had query parameters that were not in http.params
        val rawUrl = item.http.url
        if (rawUrl.contains("?")) {
            val queryString = rawUrl.substringAfter("?")
            queryString.split("&").forEach { pair ->
                val key = pair.substringBefore("=").trim()
                val value = pair.substringAfter("=", "").trim()
                if (key.isNotEmpty() && handledQueryParamNames.add(key)) {
                    parametersList.add(buildJsonObject {
                        put("name", key)
                        put("in", "query")
                        put("required", false)
                        put("schema", buildJsonObject { put("type", "string") })
                        if (value.isNotEmpty()) {
                            put("example", value)
                        }
                    })
                }
            }
        }

        // Add header parameters
        for (header in item.http.headers) {
            val name = header.name.trim()
            if (name.isNotEmpty() &&
                !name.equals("Host", ignoreCase = true) &&
                !name.equals("Content-Length", ignoreCase = true) &&
                !name.equals("Content-Type", ignoreCase = true)
            ) {
                parametersList.add(buildJsonObject {
                    put("name", name)
                    put("in", "header")
                    put("required", false)
                    put("schema", buildJsonObject { put("type", "string") })
                    if (header.value.isNotBlank()) {
                        put("example", header.value)
                    }
                })
            }
        }

        val requestBody = buildRequestBody(item)

        return buildJsonObject {
            put("summary", summary)
            description?.let { put("description", it) }
            put("tags", buildJsonArray {
                entry.tags.forEach { add(JsonPrimitive(it)) }
            })
            if (parametersList.isNotEmpty()) {
                put("parameters", buildJsonArray {
                    parametersList.forEach { add(it) }
                })
            }
            requestBody?.let { put("requestBody", it) }
            put("responses", buildJsonObject {
                put("200", buildJsonObject {
                    put("description", "Successful response")
                })
            })
        }
    }

    private fun buildRequestBody(item: HttpRequestItem): JsonObject? {
        val method = item.http.method.uppercase()
        if (method !in setOf("POST", "PUT", "PATCH", "DELETE")) return null

        val body = item.http.body ?: return null
        return when (body) {
            is RawBody -> {
                val data = body.data.trim()
                val contentType = when (body.type) {
                    RequestBodyType.XML -> "application/xml"
                    RequestBodyType.TEXT -> "text/plain"
                    else -> "application/json"
                }
                buildJsonObject {
                    put("content", buildJsonObject {
                        put(contentType, buildJsonObject {
                            put("schema", buildJsonObject {
                                put("type", if (contentType == "text/plain") "string" else "object")
                            })
                            if (data.isNotEmpty()) {
                                put("example", data)
                            }
                        })
                    })
                }
            }
            is FormUrlEncodedBody -> {
                buildJsonObject {
                    put("content", buildJsonObject {
                        put("application/x-www-form-urlencoded", buildJsonObject {
                            put("schema", buildJsonObject {
                                put("type", "object")
                            })
                        })
                    })
                }
            }
            is MultipartFormBody -> {
                buildJsonObject {
                    put("content", buildJsonObject {
                        put("multipart/form-data", buildJsonObject {
                            put("schema", buildJsonObject {
                                put("type", "object")
                            })
                        })
                    })
                }
            }
            is FileBody -> null
        }
    }
}
