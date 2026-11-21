package io.github.makeforge.clapi.mcp.server.inspection

import io.github.makeforge.clapi.mcp.server.backend.DEFAULT_MAX_BODY_LENGTH
import io.github.makeforge.clapi.mcp.server.backend.ExecutionResultDto
import io.github.makeforge.clapi.mcp.server.backend.ExecutionSnapshot
import io.github.makeforge.clapi.mcp.server.backend.MAX_BODY_LENGTH
import io.github.makeforge.clapi.mcp.server.backend.MIN_BODY_LENGTH

/**
 * Thread-safe in-memory LRU cache (`max 50 requests`, `max 32 MB` total body text) storing sanitized
 * [ExecutionSnapshot] instances keyed by request UUID.
 */
class ResponseSnapshotCache(
    private val maxEntries: Int = 50,
    private val maxTotalBytes: Long = 32L * 1024L * 1024L,
) {
    private val lock = Any()
    private var currentBytes: Long = 0L
    private val map = LinkedHashMap<String, ExecutionSnapshot>(16, 0.75f, true)

    private fun estimateBytes(snapshot: ExecutionSnapshot): Long =
        snapshot.sanitizedBodyText.length.toLong() * 2L

    fun put(snapshot: ExecutionSnapshot) {
        synchronized(lock) {
            map.remove(snapshot.requestId)?.let { prev ->
                currentBytes = (currentBytes - estimateBytes(prev)).coerceAtLeast(0L)
            }
            map[snapshot.requestId] = snapshot
            currentBytes += estimateBytes(snapshot)

            val iter = map.entries.iterator()
            while (iter.hasNext() && (map.size > maxEntries || (currentBytes > maxTotalBytes && map.size > 1))) {
                val eldest = iter.next()
                currentBytes = (currentBytes - estimateBytes(eldest.value)).coerceAtLeast(0L)
                iter.remove()
            }
        }
    }

    fun get(requestId: String): ExecutionSnapshot? =
        synchronized(lock) {
            map[requestId]
        }

    fun remove(requestId: String) {
        synchronized(lock) {
            map.remove(requestId)?.let { prev ->
                currentBytes = (currentBytes - estimateBytes(prev)).coerceAtLeast(0L)
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            map.clear()
            currentBytes = 0L
        }
    }

    fun size(): Int =
        synchronized(lock) {
            map.size
        }

    fun estimatedTotalBytes(): Long =
        synchronized(lock) {
            currentBytes
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

            val bodyText = snapshot.sanitizedBodyText
            val totalBytes = utf8ByteLength(bodyText)
            val truncated = totalBytes > budget
            val preview = if (truncated) {
                takeUtf8Prefix(bodyText, budget)
            } else {
                bodyText
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

        fun utf8ByteLength(s: String): Long {
            var bytes = 0L
            var i = 0
            val len = s.length
            while (i < len) {
                val c = s[i].code
                when {
                    c < 0x80 -> {
                        bytes += 1
                        i += 1
                    }
                    c < 0x800 -> {
                        bytes += 2
                        i += 1
                    }
                    c in 0xD800..0xDBFF && i + 1 < len && s[i + 1].code in 0xDC00..0xDFFF -> {
                        bytes += 4
                        i += 2
                    }
                    else -> {
                        bytes += 3
                        i += 1
                    }
                }
            }
            return bytes
        }

        private fun takeUtf8Prefix(s: String, maxBytes: Int): String {
            var bytes = 0
            var i = 0
            val len = s.length
            while (i < len) {
                val c = s[i].code
                val charBytes: Int
                val charStep: Int
                when {
                    c < 0x80 -> {
                        charBytes = 1
                        charStep = 1
                    }
                    c < 0x800 -> {
                        charBytes = 2
                        charStep = 1
                    }
                    c in 0xD800..0xDBFF && i + 1 < len && s[i + 1].code in 0xDC00..0xDFFF -> {
                        charBytes = 4
                        charStep = 2
                    }
                    else -> {
                        charBytes = 3
                        charStep = 1
                    }
                }
                if (bytes + charBytes > maxBytes) break
                bytes += charBytes
                i += charStep
            }
            return s.substring(0, i)
        }
    }
}
