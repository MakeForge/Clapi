package io.github.makeforge.clapi.core.network.websocket

import io.github.makeforge.clapi.core.model.MessageDirection
import io.github.makeforge.clapi.core.model.WebSocketLogMessage
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessageType
import io.github.makeforge.clapi.core.network.ConnectionState
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

data class WebSocketConnectConfig(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val timeoutMs: Long? = null,
    val pingIntervalMs: Long? = null,
)

interface WebSocketClientManager {
    fun connect(tabId: String, config: WebSocketConnectConfig)
    suspend fun sendMessage(tabId: String, content: String, format: WebSocketMessageType = WebSocketMessageType.TEXT)
    fun disconnect(tabId: String, closeReason: String? = null)
    fun connectionState(tabId: String): SharedFlow<ConnectionState>
    fun messages(tabId: String): SharedFlow<WebSocketLogMessage>
    fun isConnected(tabId: String): Boolean

    companion object {
        val default: WebSocketClientManager by lazy { KtorWebSocketClientManager() }
    }
}

class KtorWebSocketClientManager(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + Job()),
    private val httpClientProvider: (() -> HttpClient) = {
        HttpClient {
            install(WebSockets)
        }
    }
) : WebSocketClientManager {

    private class TabSession(
        val connectionStateFlow: MutableSharedFlow<ConnectionState> = MutableSharedFlow(replay = 1, extraBufferCapacity = 10),
        val messageFlow: MutableSharedFlow<WebSocketLogMessage> = MutableSharedFlow(extraBufferCapacity = 100),
        var connectionJob: Job? = null,
        var session: WebSocketSession? = null,
        var sendChannel: Channel<Pair<String, WebSocketMessageType>> = Channel(Channel.BUFFERED),
        var isConnected: Boolean = false,
    )

    private val sessions = mutableMapOf<String, TabSession>()

    private fun getOrCreateSession(tabId: String): TabSession =
        sessions.getOrPut(tabId) { TabSession() }

    override fun connectionState(tabId: String): SharedFlow<ConnectionState> =
        getOrCreateSession(tabId).connectionStateFlow.asSharedFlow()

    override fun messages(tabId: String): SharedFlow<WebSocketLogMessage> =
        getOrCreateSession(tabId).messageFlow.asSharedFlow()

    override fun isConnected(tabId: String): Boolean =
        sessions[tabId]?.isConnected == true

    private fun generateId(): String = Random.nextLong().toString(36)

    override fun connect(tabId: String, config: WebSocketConnectConfig) {
        disconnect(tabId)
        val tabSession = getOrCreateSession(tabId)
        tabSession.connectionStateFlow.tryEmit(ConnectionState.CONNECTING)
        tabSession.sendChannel = Channel(Channel.BUFFERED)

        tabSession.connectionJob = scope.launch {
            try {
                val client = httpClientProvider()
                client.webSocket(
                    urlString = config.url,
                    request = {
                        config.headers.forEach { (key, value) ->
                            header(key, value)
                        }
                    }
                ) {
                    tabSession.session = this
                    if (config.pingIntervalMs != null && config.pingIntervalMs > 0) {
                        pingIntervalMillis = config.pingIntervalMs
                    }
                    if (config.timeoutMs != null && config.timeoutMs > 0) {
                        timeoutMillis = config.timeoutMs
                    }
                    tabSession.isConnected = true
                    tabSession.connectionStateFlow.tryEmit(ConnectionState.CONNECTED)

                    // Sender coroutine
                    val senderJob = launch {
                        for ((payload, format) in tabSession.sendChannel) {
                            val frame = if (format == WebSocketMessageType.BINARY) {
                                Frame.Binary(true, payload.encodeToByteArray())
                            } else {
                                Frame.Text(payload)
                            }
                            send(frame)
                            tabSession.messageFlow.emit(
                                WebSocketLogMessage(
                                    id = generateId(),
                                    timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                                    direction = MessageDirection.SENT,
                                    format = format.value,
                                    payload = payload,
                                    byteLength = payload.encodeToByteArray().size.toLong(),
                                )
                            )
                        }
                    }

                    // Receiver loop
                    try {
                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Text -> {
                                    val text = frame.readText()
                                    tabSession.messageFlow.emit(
                                        WebSocketLogMessage(
                                            id = generateId(),
                                            timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                                            direction = MessageDirection.RECEIVED,
                                            format = "text",
                                            payload = text,
                                            byteLength = text.encodeToByteArray().size.toLong(),
                                        )
                                    )
                                }
                                is Frame.Binary -> {
                                    val bytes = frame.readBytes()
                                    val text = runCatching { bytes.decodeToString() }.getOrDefault("")
                                    tabSession.messageFlow.emit(
                                        WebSocketLogMessage(
                                            id = generateId(),
                                            timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                                            direction = MessageDirection.RECEIVED,
                                            format = "binary",
                                            payload = text,
                                            byteLength = bytes.size.toLong(),
                                        )
                                    )
                                }
                                is Frame.Close -> {
                                    val reason = frame.readReason()
                                    val reasonText = reason?.message ?: "Closed by server"
                                    tabSession.messageFlow.emit(
                                        WebSocketLogMessage(
                                            id = generateId(),
                                            timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                                            direction = MessageDirection.RECEIVED,
                                            format = "text",
                                            payload = "Connection closed: $reasonText (${reason?.code ?: 1000})",
                                            byteLength = 0,
                                        )
                                    )
                                }
                                else -> Unit
                            }
                        }
                    } finally {
                        senderJob.cancel()
                    }
                }
                tabSession.isConnected = false
                tabSession.connectionStateFlow.tryEmit(ConnectionState.DISCONNECTED)
            } catch (e: CancellationException) {
                tabSession.isConnected = false
                tabSession.connectionStateFlow.tryEmit(ConnectionState.DISCONNECTED)
            } catch (e: Exception) {
                tabSession.isConnected = false
                tabSession.connectionStateFlow.tryEmit(ConnectionState.ERROR)
                tabSession.messageFlow.emit(
                    WebSocketLogMessage(
                        id = generateId(),
                        timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                        direction = MessageDirection.RECEIVED,
                        format = "text",
                        payload = "Error: ${e.message ?: "Connection failed"}",
                        byteLength = 0,
                    )
                )
            } finally {
                tabSession.isConnected = false
                tabSession.session = null
            }
        }
    }

    override suspend fun sendMessage(tabId: String, content: String, format: WebSocketMessageType) {
        val tabSession = sessions[tabId] ?: return
        if (tabSession.isConnected) {
            tabSession.sendChannel.send(content to format)
        }
    }

    override fun disconnect(tabId: String, closeReason: String?) {
        val tabSession = sessions[tabId] ?: return
        tabSession.isConnected = false
        val s = tabSession.session
        tabSession.session = null
        scope.launch {
            runCatching {
                s?.close(CloseReason(CloseReason.Codes.NORMAL, closeReason ?: "Client closed connection"))
            }
        }
        tabSession.connectionJob?.cancel()
        tabSession.connectionJob = null
        tabSession.connectionStateFlow.tryEmit(ConnectionState.DISCONNECTED)
    }
}
