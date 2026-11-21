package io.github.crustack.clapi.core.network.mcp

import io.github.crustack.clapi.core.model.JsonRpcEnvelope
import io.github.crustack.clapi.core.model.McpConnectionConfig
import io.github.crustack.clapi.core.model.McpContent
import io.github.crustack.clapi.core.model.McpCreateMessageResult
import io.github.crustack.clapi.core.model.McpElicitAction
import io.github.crustack.clapi.core.model.McpHttpMode
import io.github.crustack.clapi.core.model.McpImplementation
import io.github.crustack.clapi.core.model.McpInitializeResult
import io.github.crustack.clapi.core.model.McpRoot
import io.github.crustack.clapi.core.model.McpSamplingMode
import io.github.crustack.clapi.core.model.McpTransportType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpClientHandshakeTest {

    @Test
    fun initialize_and_list_tools() = runTest {
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Get || request.method == HttpMethod.Delete) {
                return@MockEngine respond("", HttpStatusCode.MethodNotAllowed)
            }
            val body = request.body.toByteArray().decodeToString()
            val envelope = mcpJson.decodeFromString(JsonRpcEnvelope.serializer(), body)
            if (envelope.method == "initialize") {
                assertTrue(body.contains("\"sampling\""), body)
                assertTrue(body.contains("\"roots\""), body)
                assertTrue(body.contains("\"elicitation\""), body)
            }
            val result = when (envelope.method) {
                "initialize" -> """{"protocolVersion":"2025-06-18","capabilities":{"tools":{"listChanged":true},"resources":{"subscribe":true},"prompts":{}},"serverInfo":{"name":"mock","version":"1"}}"""
                "tools/list" -> """{"tools":[{"name":"echo","description":"echo","inputSchema":{"type":"object"}}]}"""
                "notifications/initialized" -> null
                else -> error(envelope.method.orEmpty())
            }
            if (result == null) {
                respond("", HttpStatusCode.Accepted)
            } else {
                respond(
                    """{"jsonrpc":"2.0","id":${envelope.id},"result":$result}""",
                    HttpStatusCode.OK,
                    headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        "Mcp-Session-Id" to listOf("s1"),
                    ),
                )
            }
        }
        val client = McpClient(this, HttpClient(engine) { expectSuccess = false })
        val init = client.connect(McpConnectionConfig(url = "https://example/mcp", httpMode = McpHttpMode.STREAMABLE_2025_06_18))
        assertEquals("mock", init.serverInfo.name)
        assertEquals("s1", client.sessionId)
        val tools = client.listTools()
        assertEquals(listOf("echo"), tools.map { it.name })
        client.disconnect()
    }

    @Test
    fun inbound_sampling_and_elicitation() = runTest {
        val inbound = Channel<String>(Channel.UNLIMITED)
        val written = Channel<String>(Channel.UNLIMITED)
        val transport = NdjsonStdioTransport(this, inbound, { written.send(it) })
        val client = McpClient(
            scope = this,
            stdioFactory = { transport },
            callTimeoutMs = 5_000,
            handlers = McpClientHandlers(
                onSampling = {
                    McpCreateMessageResult(content = McpContent(type = "text", text = "hello"))
                },
                onElicit = { McpElicitResult(action = McpElicitAction.ACCEPT, content = buildJsonObject { put("ok", true) }) },
            ),
        )
        val connect = async {
            client.connect(McpConnectionConfig(transport = McpTransportType.STDIO, command = "unused"))
        }
        val initLine = written.receive()
        assertTrue(initLine.contains("initialize"))
        val initId = mcpJson.decodeFromString(
            JsonRpcEnvelope.serializer(),
            initLine,
        ).id
        inbound.send("""{"jsonrpc":"2.0","id":$initId,"result":{"protocolVersion":"2025-06-18","capabilities":{},"serverInfo":{"name":"t","version":"1"}}}""")
        written.receive() // notifications/initialized
        connect.await()
        client.handlers.onSampling = {
            McpCreateMessageResult(content = McpContent(type = "text", text = "hello"))
        }
        client.handlers.onElicit = {
            McpElicitResult(action = McpElicitAction.ACCEPT, content = buildJsonObject { put("ok", true) })
        }
        inbound.send("""{"jsonrpc":"2.0","id":"srv-1","method":"sampling/createMessage","params":{"messages":[],"maxTokens":16}}""")
        inbound.send("""{"jsonrpc":"2.0","id":"srv-2","method":"elicitation/create","params":{"message":"hi","requestedSchema":{"type":"object"}}}""")
        val replies = listOf(written.receive(), written.receive())
        assertTrue(replies.any { it.contains("hello") && it.contains("srv-1") })
        assertTrue(replies.any { it.contains("accept") && it.contains("srv-2") })
        client.disconnect()
    }

    @Test
    fun config_mock_sampling_and_auto_elicit_accept() = runTest {
        val replies = connectStdioAndCollectServerReplies(
            McpConnectionConfig(
                transport = McpTransportType.STDIO,
                command = "unused",
                samplingMode = McpSamplingMode.MOCK,
                autoRespondElicitation = true,
            ),
            """{"jsonrpc":"2.0","id":"srv-1","method":"sampling/createMessage","params":{"messages":[],"maxTokens":8}}""",
            """{"jsonrpc":"2.0","id":"srv-2","method":"elicitation/create","params":{"message":"hi","requestedSchema":{"type":"object"}}}""",
        )
        assertTrue(replies.any { it.contains("mock reply from Clapi") && it.contains("srv-1") })
        assertTrue(replies.any { it.contains("accept") && it.contains("srv-2") })
    }

    @Test
    fun config_manual_sampling_and_elicit_decline() = runTest {
        val replies = connectStdioAndCollectServerReplies(
            McpConnectionConfig(
                transport = McpTransportType.STDIO,
                command = "unused",
                samplingMode = McpSamplingMode.MANUAL,
                autoRespondElicitation = false,
            ),
            """{"jsonrpc":"2.0","id":"srv-1","method":"sampling/createMessage","params":{"messages":[],"maxTokens":8}}""",
            """{"jsonrpc":"2.0","id":"srv-2","method":"elicitation/create","params":{"message":"hi","requestedSchema":{"type":"object"}}}""",
        )
        assertTrue(replies.any { it.contains("cancelled") && it.contains("srv-1") })
        assertTrue(replies.none { it.contains("mock reply from Clapi") })
        assertTrue(replies.any { it.contains("decline") && it.contains("srv-2") })
    }

    @Test
    fun config_roots_are_returned_on_roots_list() = runTest {
        val replies = connectStdioAndCollectServerReplies(
            McpConnectionConfig(
                transport = McpTransportType.STDIO,
                command = "unused",
                roots = listOf(McpRoot("file:///tmp/clapi", "tmp")),
            ),
            """{"jsonrpc":"2.0","id":"srv-roots","method":"roots/list","params":{}}""",
        )
        assertTrue(replies.single().contains("file:///tmp/clapi"))
        assertTrue(replies.single().contains("tmp"))
    }

    @Test
    fun initialize_survives_response_arriving_during_send() = runTest {
        val transport = ReplyDuringSendTransport()
        val client = McpClient(
            scope = CoroutineScope(Dispatchers.Unconfined),
            stdioFactory = { transport },
            callTimeoutMs = 5_000,
        )
        val init = client.connect(
            McpConnectionConfig(transport = McpTransportType.STDIO, command = "unused"),
        )
        assertEquals("race", init.serverInfo.name)
        client.disconnect()
    }

    private suspend fun CoroutineScope.connectStdioAndCollectServerReplies(
        config: McpConnectionConfig,
        vararg inboundMessages: String,
    ): List<String> {
        val inbound = Channel<String>(Channel.UNLIMITED)
        val written = Channel<String>(Channel.UNLIMITED)
        val transport = NdjsonStdioTransport(this, inbound, { written.send(it) })
        val client = McpClient(scope = this, stdioFactory = { transport }, callTimeoutMs = 5_000)
        val connect = async { client.connect(config) }
        val initLine = written.receive()
        val initId = mcpJson.decodeFromString(
            JsonRpcEnvelope.serializer(),
            initLine,
        ).id
        inbound.send("""{"jsonrpc":"2.0","id":$initId,"result":{"protocolVersion":"2025-06-18","capabilities":{},"serverInfo":{"name":"t","version":"1"}}}""")
        written.receive()
        connect.await()
        inboundMessages.forEach { inbound.send(it) }
        val replies = inboundMessages.map { written.receive() }
        client.disconnect()
        return replies
    }
}

/**
 * Replies to initialize during send(), matching legacy HTTP+SSE where the SSE
 * collector can complete the pending RPC before send() returns.
 */
private class ReplyDuringSendTransport : McpTransport {
    private val _incoming = MutableSharedFlow<JsonRpcEnvelope>(extraBufferCapacity = 16)
    override val incoming: SharedFlow<JsonRpcEnvelope> = _incoming
    override val sessionId: String? = null
    override var protocolVersion: String = "2025-06-18"
    override val lastEventId: String? = null
    override val negotiatedHttpMode: McpHttpMode = McpHttpMode.LEGACY_2024_11_05

    override suspend fun start() = Unit

    override suspend fun send(message: JsonRpcEnvelope) {
        if (message.method == "initialize") {
            _incoming.emit(
                JsonRpcEnvelope(
                    id = message.id,
                    result = encodeParams(
                        McpInitializeResult(
                            serverInfo = McpImplementation(name = "race", version = "1"),
                        ),
                    ),
                ),
            )
        }
    }

    override suspend fun close() = Unit
}
