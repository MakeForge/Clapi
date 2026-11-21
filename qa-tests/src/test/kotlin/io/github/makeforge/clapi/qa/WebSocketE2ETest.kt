package io.github.makeforge.clapi.qa

import io.github.makeforge.clapi.core.model.MessageDirection
import io.github.makeforge.clapi.core.model.WebSocketLogMessage
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessageType
import io.github.makeforge.clapi.core.network.ConnectionState
import io.github.makeforge.clapi.core.network.websocket.KtorWebSocketClientManager
import io.github.makeforge.clapi.core.network.websocket.WebSocketConnectConfig
import io.github.makeforge.clapi.testsupport.DummyApiServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebSocketE2ETest {
    private lateinit var server: DummyApiServer
    private lateinit var client: HttpClient

    @BeforeTest
    fun setUp() {
        server = DummyApiServer()
        server.start()
        client = HttpClient(CIO) {
            install(WebSockets)
        }
    }

    @AfterTest
    fun tearDown() {
        client.close()
        server.stop()
    }

    @Test
    fun connect_send_receive_and_disconnect() {
        runBlocking {
        val wsUrl = server.baseUrl().replace("http", "ws") + "/ws"

        withTimeout(5_000) {
            client.webSocket(urlString = wsUrl) {
                val connected = (incoming.receive() as Frame.Text).readText()
                assertEquals("connected", connected)

                send(Frame.Text("hello"))
                val echoed = (incoming.receive() as Frame.Text).readText()
                assertEquals("echo:hello", echoed)

                send(Frame.Text("close"))
                val goodbye = (incoming.receive() as Frame.Text).readText()
                assertEquals("bye", goodbye)
            }
        }
        }
    }

    @Test
    fun connect_and_echo_via_websocket_client_manager() = runBlocking {
        val manager = KtorWebSocketClientManager()
        val wsUrl = server.baseUrl().replace("http", "ws") + "/ws"
        val tabId = "test-tab-ws"

        val messages = mutableListOf<WebSocketLogMessage>()
        val states = mutableListOf<ConnectionState>()

        val stateJob = launch {
            manager.connectionState(tabId).collect { states.add(it) }
        }
        val messageJob = launch {
            manager.messages(tabId).collect { messages.add(it) }
        }

        try {
            withTimeout(5_000) {
                manager.connect(tabId, WebSocketConnectConfig(url = wsUrl))

                // Wait until state is CONNECTED
                while (states.lastOrNull() != ConnectionState.CONNECTED) {
                    delay(50)
                }

                // Initial frame from DummyApiServer is "connected"
                while (messages.none { it.payload == "connected" && it.direction == MessageDirection.RECEIVED }) {
                    delay(50)
                }

                // Send echo message
                manager.sendMessage(tabId, "hello from manager", WebSocketMessageType.TEXT)

                // Wait for echoed message
                while (messages.none { it.payload == "echo:hello from manager" && it.direction == MessageDirection.RECEIVED }) {
                    delay(50)
                }

                // Disconnect
                manager.disconnect(tabId)

                // Wait until state is DISCONNECTED
                while (states.lastOrNull() != ConnectionState.DISCONNECTED) {
                    delay(50)
                }
            }

            assertTrue(messages.any { it.payload == "hello from manager" && it.direction == MessageDirection.SENT })
            assertTrue(messages.any { it.payload == "echo:hello from manager" && it.direction == MessageDirection.RECEIVED })
        } finally {
            stateJob.cancel()
            messageJob.cancel()
        }
    }
}
