package io.github.makeforge.clapi.mcp.server.inspection

import io.github.makeforge.clapi.mcp.server.backend.DEFAULT_MAX_BODY_LENGTH
import io.github.makeforge.clapi.mcp.server.backend.InspectResponseResultDto
import io.github.makeforge.clapi.mcp.server.backend.MAX_BODY_LENGTH
import io.github.makeforge.clapi.mcp.server.backend.MIN_BODY_LENGTH
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Evaluates JSONPath expressions (`$`, `.field`, `['field']`, `[index]`, `[*]`, `[start:end]`, `..field`)
 * and UTF-8 byte-range slices over cached sanitized response bodies.
 */
object JsonPathInspector {

    private val prettyJson = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun inspect(
        requestId: String,
        sanitizedBodyText: String,
        jsonPath: String? = null,
        byteOffset: Int = 0,
        length: Int = DEFAULT_MAX_BODY_LENGTH,
    ): InspectResponseResultDto {
        val targetText = if (!jsonPath.isNullOrBlank()) {
            evaluateJsonPathToText(sanitizedBodyText, jsonPath.trim())
        } else {
            sanitizedBodyText
        }

        val utf8Bytes = targetText.encodeToByteArray()
        val totalBytes = utf8Bytes.size
        val safeOffset = byteOffset.coerceIn(0, totalBytes)
        val safeLength = length.coerceIn(MIN_BODY_LENGTH, MAX_BODY_LENGTH)
        val endExclusive = (safeOffset + safeLength).coerceAtMost(totalBytes)
        val sliceLength = (endExclusive - safeOffset).coerceAtLeast(0)
        val content = if (sliceLength > 0) {
            utf8Bytes.decodeToString(safeOffset, endExclusive)
        } else {
            ""
        }

        return InspectResponseResultDto(
            requestId = requestId,
            jsonPath = jsonPath?.takeIf { it.isNotBlank() },
            byteOffset = safeOffset,
            returnedBytes = sliceLength,
            totalBytes = totalBytes,
            hasMore = endExclusive < totalBytes,
            content = content,
        )
    }

    fun evaluateJsonPath(root: JsonElement, path: String): List<JsonElement> {
        val tokens = tokenize(path)
        var current = listOf(root)
        for (token in tokens) {
            current = when (token) {
                is PathToken.Root -> current
                is PathToken.ChildProperty -> current.mapNotNull { elem ->
                    (elem as? JsonObject)?.get(token.name)
                }
                is PathToken.RecursiveProperty -> current.flatMap { elem ->
                    findRecursive(elem, token.name)
                }
                is PathToken.Wildcard -> current.flatMap { elem ->
                    when (elem) {
                        is JsonArray -> elem.toList()
                        is JsonObject -> elem.values.toList()
                        else -> emptyList()
                    }
                }
                is PathToken.ArrayIndex -> current.mapNotNull { elem ->
                    val arr = elem as? JsonArray ?: return@mapNotNull null
                    val idx = if (token.index < 0) arr.size + token.index else token.index
                    arr.getOrNull(idx)
                }
                is PathToken.ArraySlice -> current.flatMap { elem ->
                    val arr = elem as? JsonArray ?: return@flatMap emptyList()
                    val start = (token.start ?: 0).let { if (it < 0) (arr.size + it).coerceAtLeast(0) else it }
                        .coerceIn(0, arr.size)
                    val end = (token.end ?: arr.size).let { if (it < 0) (arr.size + it).coerceAtLeast(0) else it }
                        .coerceIn(start, arr.size)
                    arr.subList(start, end)
                }
            }
            if (current.isEmpty()) break
        }
        return current
    }

    private fun evaluateJsonPathToText(rawBody: String, jsonPath: String): String {
        val root = try {
            prettyJson.parseToJsonElement(rawBody)
        } catch (e: Exception) {
            throw IllegalArgumentException("Response body is not valid JSON for jsonPath query '$jsonPath': ${e.message}")
        }
        val matches = evaluateJsonPath(root, jsonPath)
        return when {
            matches.isEmpty() -> "null"
            matches.size == 1 -> formatElement(matches.first())
            else -> prettyJson.encodeToString(JsonArray.serializer(), JsonArray(matches))
        }
    }

    private fun formatElement(element: JsonElement): String =
        when (element) {
            is JsonPrimitive -> if (element.isString) element.content else element.toString()
            is JsonNull -> "null"
            else -> prettyJson.encodeToString(JsonElement.serializer(), element)
        }

    private fun findRecursive(element: JsonElement, propertyName: String): List<JsonElement> {
        val found = mutableListOf<JsonElement>()
        fun walk(curr: JsonElement) {
            when (curr) {
                is JsonObject -> {
                    if (propertyName == "*") {
                        found.addAll(curr.values)
                    } else {
                        curr[propertyName]?.let { found.add(it) }
                    }
                    curr.values.forEach(::walk)
                }
                is JsonArray -> curr.forEach(::walk)
                else -> Unit
            }
        }
        walk(element)
        return found
    }

    private sealed interface PathToken {
        data object Root : PathToken
        data class ChildProperty(val name: String) : PathToken
        data class RecursiveProperty(val name: String) : PathToken
        data object Wildcard : PathToken
        data class ArrayIndex(val index: Int) : PathToken
        data class ArraySlice(val start: Int?, val end: Int?) : PathToken
    }

    private fun tokenize(rawPath: String): List<PathToken> {
        val path = rawPath.trim()
        if (path.isEmpty() || path == "$") return listOf(PathToken.Root)
        val tokens = mutableListOf<PathToken>(PathToken.Root)
        var i = if (path.startsWith("$")) 1 else 0

        while (i < path.length) {
            when {
                path.startsWith("..", i) -> {
                    i += 2
                    if (i < path.length && path[i] == '[') {
                        val (bracketToken, nextIdx) = parseBracket(path, i)
                        if (bracketToken is PathToken.ChildProperty) {
                            tokens.add(PathToken.RecursiveProperty(bracketToken.name))
                        } else if (bracketToken is PathToken.Wildcard) {
                            tokens.add(PathToken.RecursiveProperty("*"))
                        } else {
                            tokens.add(bracketToken)
                        }
                        i = nextIdx
                    } else {
                        val start = i
                        while (i < path.length && path[i] != '.' && path[i] != '[') i++
                        val prop = path.substring(start, i).trim()
                        if (prop.isNotEmpty()) {
                            tokens.add(PathToken.RecursiveProperty(prop))
                        }
                    }
                }
                path[i] == '.' -> {
                    i++
                    val start = i
                    while (i < path.length && path[i] != '.' && path[i] != '[') i++
                    val prop = path.substring(start, i).trim()
                    if (prop == "*") {
                        tokens.add(PathToken.Wildcard)
                    } else if (prop.isNotEmpty()) {
                        tokens.add(PathToken.ChildProperty(prop))
                    }
                }
                path[i] == '[' -> {
                    val (token, nextIdx) = parseBracket(path, i)
                    tokens.add(token)
                    i = nextIdx
                }
                else -> {
                    val start = i
                    while (i < path.length && path[i] != '.' && path[i] != '[') i++
                    val prop = path.substring(start, i).trim()
                    if (prop == "*") {
                        tokens.add(PathToken.Wildcard)
                    } else if (prop.isNotEmpty()) {
                        tokens.add(PathToken.ChildProperty(prop))
                    }
                }
            }
        }
        return tokens
    }

    private fun parseBracket(path: String, openIdx: Int): Pair<PathToken, Int> {
        val closeIdx = path.indexOf(']', openIdx + 1)
        if (closeIdx < 0) {
            throw IllegalArgumentException("Unclosed bracket in JSONPath '$path' at offset $openIdx")
        }
        val inner = path.substring(openIdx + 1, closeIdx).trim()
        val nextIdx = closeIdx + 1
        if (inner == "*") {
            return PathToken.Wildcard to nextIdx
        }
        if ((inner.startsWith("'") && inner.endsWith("'")) || (inner.startsWith("\"") && inner.endsWith("\""))) {
            return PathToken.ChildProperty(inner.substring(1, inner.length - 1)) to nextIdx
        }
        if (inner.contains(':')) {
            val parts = inner.split(':', limit = 2)
            val start = parts[0].trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
            val end = parts[1].trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
            return PathToken.ArraySlice(start, end) to nextIdx
        }
        val idx = inner.toIntOrNull()
            ?: throw IllegalArgumentException("Invalid bracket expression '[$inner]' in JSONPath '$path'")
        return PathToken.ArrayIndex(idx) to nextIdx
    }
}
