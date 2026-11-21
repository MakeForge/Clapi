package io.github.makeforge.clapi.core.model.opencollection

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebSocketSerializationTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testWebSocketRequestItemWithSingleMessageRoundTrip() {
        val item = WebSocketRequestItem(
            info = WebSocketRequestInfo(name = "Echo WS"),
            websocket = WebSocketRequestDetails(
                url = "wss://echo.websocket.org",
                headers = listOf(
                    HttpRequestHeader(name = "X-Client", value = "Clapi")
                ),
                message = WebSocketMessagePayload.Single(
                    WebSocketMessage(type = WebSocketMessageType.JSON, data = "{\"msg\":\"hello\"}")
                ),
                auth = AuthBearer(token = "secret-token")
            ),
            settings = WebSocketRequestSettings(
                timeout = JsonPrimitive(5000),
                keepAliveInterval = JsonPrimitive("inherit")
            ),
            docs = "Documentation for Echo WS"
        )

        val serialized = json.encodeToString(WebSocketRequestItem.serializer(), item)
        assertTrue(serialized.contains("\"websocket\":"))
        assertTrue(serialized.contains(""""type":"json""""))
        assertTrue(serialized.contains(""""data":"{\"msg\":\"hello\"}""""))

        val deserialized = json.decodeFromString(WebSocketRequestItem.serializer(), serialized)
        assertEquals("Echo WS", deserialized.info.name)
        assertEquals(ItemType.WEBSOCKET, deserialized.info.type)
        assertEquals("wss://echo.websocket.org", deserialized.websocket.url)
        assertEquals(1, deserialized.websocket.headers.size)
        assertEquals("X-Client", deserialized.websocket.headers[0].name)
        val payload = deserialized.websocket.message
        assertIs<WebSocketMessagePayload.Single>(payload)
        assertEquals(WebSocketMessageType.JSON, payload.message.type)
        assertEquals("{\"msg\":\"hello\"}", payload.message.data)
        assertIs<AuthBearer>(deserialized.websocket.auth)
        assertEquals("secret-token", (deserialized.websocket.auth as AuthBearer).token)
    }

    @Test
    fun testWebSocketRequestItemWithVariantsMessageRoundTrip() {
        val item = WebSocketRequestItem(
            info = WebSocketRequestInfo(name = "Chat WS"),
            websocket = WebSocketRequestDetails(
                url = "ws://localhost:8080/chat",
                message = WebSocketMessagePayload.Variants(
                    listOf(
                        WebSocketMessageVariant(
                            title = "Join Room",
                            selected = true,
                            message = WebSocketMessage(type = WebSocketMessageType.TEXT, data = "JOIN room1")
                        ),
                        WebSocketMessageVariant(
                            title = "Send Message",
                            selected = false,
                            message = WebSocketMessage(type = WebSocketMessageType.JSON, data = "{\"text\":\"hi\"}")
                        )
                    )
                )
            )
        )

        val serialized = json.encodeToString(WebSocketRequestItem.serializer(), item)
        val deserialized = json.decodeFromString(WebSocketRequestItem.serializer(), serialized)
        val payload = deserialized.websocket.message
        assertIs<WebSocketMessagePayload.Variants>(payload)
        assertEquals(2, payload.variants.size)
        assertEquals("Join Room", payload.variants[0].title)
        assertTrue(payload.variants[0].selected == true)
        assertEquals("JOIN room1", payload.variants[0].message.data)
        assertEquals("Send Message", payload.variants[1].title)
        assertEquals(WebSocketMessageType.JSON, payload.variants[1].message.type)
    }

    @Test
    fun testPolymorphicDeserializationViaOpenCollectionItemSerializer() {
        val jsonString = """
            {
              "info": {
                "name": "Live Feed",
                "type": "websocket"
              },
              "websocket": {
                "url": "ws://example.com/feed",
                "message": {
                  "type": "text",
                  "data": "ping"
                }
              }
            }
        """.trimIndent()

        val item = json.decodeFromString(OpenCollectionItemSerializer, jsonString)
        assertIs<WebSocketRequestItem>(item)
        assertEquals("Live Feed", item.info.name)
        assertEquals("ws://example.com/feed", item.websocket.url)
        val payload = item.websocket.message
        assertIs<WebSocketMessagePayload.Single>(payload)
        assertEquals("ping", payload.message.data)
    }
}
