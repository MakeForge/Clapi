// Modified by MakeForge (2026)
package io.github.makeforge.clapi.core.network.mcp

import io.github.makeforge.clapi.core.model.JsonRpcEnvelope
import io.github.makeforge.clapi.core.model.MCP_PROTOCOL_VERSION
import io.github.makeforge.clapi.core.model.McpHttpMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Newline-delimited JSON transport used by stdio and in-process tests.
 */
class NdjsonStdioTransport(
    private val scope: CoroutineScope,
    private val incomingLines: Channel<String>,
    private val writeLine: suspend (String) -> Unit,
    private val onClose: suspend () -> Unit = {},
) : McpTransport {
    private val _incoming = MutableSharedFlow<JsonRpcEnvelope>(extraBufferCapacity = 256)
    override val incoming: SharedFlow<JsonRpcEnvelope> = _incoming
    override var sessionId: String? = null
    override var protocolVersion: String = MCP_PROTOCOL_VERSION
    override var lastEventId: String? = null
    override val negotiatedHttpMode: McpHttpMode? = null

    private var reader: Job? = null

    override suspend fun start() {
        reader = scope.launch {
            for (line in incomingLines) {
                if (!isActive) break
                parseJsonRpc(line)?.let { _incoming.emit(it) }
            }
        }
    }

    override suspend fun send(message: JsonRpcEnvelope) {
        val compact = mcpJson.encodeToString(JsonRpcEnvelope.serializer(), message)
        require('\n' !in compact) { "JSON-RPC stdio frames must not contain embedded newlines" }
        writeLine(compact)
    }

    override suspend fun close() {
        reader?.cancel()
        incomingLines.close()
        onClose()
    }
}
