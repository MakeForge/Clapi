package io.github.makeforge.clapi.mcp.server.inspection

import io.github.makeforge.clapi.mcp.server.backend.DEFAULT_MAX_BODY_LENGTH
import io.github.makeforge.clapi.mcp.server.backend.ExecutionResultDto
import io.github.makeforge.clapi.mcp.server.backend.ExecutionSnapshot
import io.github.makeforge.clapi.mcp.server.backend.MAX_BODY_LENGTH
import io.github.makeforge.clapi.mcp.server.backend.MIN_BODY_LENGTH

/**
 * Thread-safe in-memory LRU cache (`max 50 requests`) storing sanitized [ExecutionSnapshot] instances
 * keyed by request UUID.
 */
class ResponseSnapshotCache(
    private val maxEntries: Int = 50,
) {
    private val lock = Any()
    private val map = object : LinkedHashMap<String, ExecutionSnapshot>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ExecutionSnapshot>?): Boolean =
            size > maxEntries
    }

    fun put(snapshot: ExecutionSnapshot) {
        synchronized(lock) {
            map[snapshot.requestId] = snapshot
        }
    }

    fun get(requestId: String): ExecutionSnapshot? =
        synchronized(lock) {
            map[requestId]
        }

    fun remove(requestId: String) {
        synchronized(lock) {
            map.remove(requestId)
        }
    }

    fun clear() {
        synchronized(lock) {
            map.clear()
        }
    }

    fun size(): Int =
        synchronized(lock) {
            map.size
        }

    companion object {
        private val BINARY_MIME_PREFIXES = listOf(
            "image/",
            "audio/",
            "video/",
            "application/octet-stream",
            "application/pdf",
            "application/zip",
            "application/gzip",
            "application/x-tar",
            "application/protobuf",
            "application/x-protobuf",
        )

        fun isBinaryContentType(contentType: String?): Boolean {
            if (contentType.isNullOrBlank()) return false
            val lower = contentType.trim().lowercase()
            return BINARY_MIME_PREFIXES.any { lower.startsWith(it) }
        }

        fun toExecutionResultDto(
            snapshot: ExecutionSnapshot,
            maxBodyLength: Int = DEFAULT_MAX_BODY_LENGTH,
        ): ExecutionResultDto {
            val budget = maxBodyLength.coerceIn(MIN_BODY_LENGTH, MAX_BODY_LENGTH)
            if (snapshot.isBinary) {
                return ExecutionResultDto(
                    requestId = snapshot.requestId,
                    slashPath = snapshot.slashPath,
                    executedAtEpochMillis = snapshot.executedAtEpochMillis,
                    statusCode = snapshot.statusCode,
                    statusText = snapshot.statusText,
                    contentType = snapshot.contentType,
                    requestHeaders = snapshot.sanitizedRequestHeaders,
                    responseHeaders = snapshot.sanitizedResponseHeaders,
                    bodyPreview = "[Binary payload: ${snapshot.contentType ?: "application/octet-stream"}, ${snapshot.totalBytes} bytes]",
                    metrics = snapshot.metrics,
                    assertions = snapshot.assertions,
                    scriptLogs = snapshot.scriptLogs,
                    scriptError = snapshot.scriptError,
                    _truncated = false,
                    _binary = true,
                    _totalBytes = snapshot.totalBytes,
                    _hint = null,
                )
            }

            val bodyBytes = snapshot.sanitizedBodyText.encodeToByteArray()
            val totalBytes = bodyBytes.size.toLong()
            val truncated = bodyBytes.size > budget
            val preview = if (truncated) {
                bodyBytes.decodeToString(0, budget)
            } else {
                snapshot.sanitizedBodyText
            }
            val hint = if (truncated) {
                "Response body truncated to $budget of $totalBytes bytes. Use clapi_inspect_response with requestId='${snapshot.requestId}', jsonPath, or byteOffset/length to inspect further."
            } else {
                null
            }

            return ExecutionResultDto(
                requestId = snapshot.requestId,
                slashPath = snapshot.slashPath,
                executedAtEpochMillis = snapshot.executedAtEpochMillis,
                statusCode = snapshot.statusCode,
                statusText = snapshot.statusText,
                contentType = snapshot.contentType,
                requestHeaders = snapshot.sanitizedRequestHeaders,
                responseHeaders = snapshot.sanitizedResponseHeaders,
                bodyPreview = preview,
                metrics = snapshot.metrics,
                assertions = snapshot.assertions,
                scriptLogs = snapshot.scriptLogs,
                scriptError = snapshot.scriptError,
                _truncated = truncated,
                _binary = false,
                _totalBytes = totalBytes,
                _hint = hint,
            )
        }
    }
}
