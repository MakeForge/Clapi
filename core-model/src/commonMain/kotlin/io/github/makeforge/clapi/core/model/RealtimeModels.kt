// Modified by MakeForge (2026)
package io.github.makeforge.clapi.core.model

import kotlinx.serialization.Serializable

/**
 * Supported realtime protocol types.
 */
@Serializable
enum class RealtimeProtocol(val label: String, val defaultUrl: String) {
    WEBSOCKET("WebSocket", "wss://echo.websocket.org"),
    SSE("SSE", "https://example.com/events"),
    SOCKETIO("Socket.IO", "https://example.com"),
    MQTT("MQTT", "wss://broker.hivemq.com:8884/mqtt"),
}

/**
 * Direction of a realtime message.
 */
@Serializable
enum class MessageDirection { SENT, RECEIVED }

/**
 * A single message in a realtime communication session.
 */
@Serializable
data class RealtimeMessage(
    val id: String,
    val direction: MessageDirection,
    val content: String,
    val timestamp: Long,
    val protocol: RealtimeProtocol,
    val eventType: String? = null,  // SSE event type, Socket.IO event name, MQTT topic
)

/**
 * A message logged during an active WebSocket request session.
 */
@Serializable
data class WebSocketLogMessage(
    val id: String,
    val timestamp: Long,
    val direction: MessageDirection,
    val format: String = "text",
    val payload: String = "",
    val byteLength: Long = payload.encodeToByteArray().size.toLong(),
)
