# Contract: WebSocket Client & Execution

**Feature**: `013-websocket-requests`
**Date**: 2026-11-10

## 1. `WebSocketClientManager` Interface

### Purpose
Manages live WebSocket connections, session coroutines, sending frames, and message reception across open request tabs.

### Interface Definition
```kotlin
package io.github.crustack.clapi.core.network.websocket

import io.github.crustack.clapi.core.model.ConnectionState
import io.github.crustack.clapi.core.model.WebSocketLogMessage
import io.github.crustack.clapi.core.model.WebSocketMessageType
import kotlinx.coroutines.flow.SharedFlow

data class WebSocketConnectConfig(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val timeoutMs: Long? = null,
    val pingIntervalMs: Long? = null,
)

interface WebSocketClientManager {
    /** Connects to the given WebSocket endpoint. */
    fun connect(tabId: String, config: WebSocketConnectConfig)

    /** Sends a text or binary message on the active session for tabId. */
    suspend fun sendMessage(tabId: String, content: String, format: WebSocketMessageType)

    /** Disconnects the active session for tabId gracefully. */
    fun disconnect(tabId: String, closeReason: String? = null)

    /** Returns connection state flow for a specific tab. */
    fun connectionState(tabId: String): SharedFlow<ConnectionState>

    /** Returns incoming/outgoing message flow for a specific tab. */
    fun messages(tabId: String): SharedFlow<WebSocketLogMessage>
}
```

---

## 2. Ktor-Based Implementation Details
- Uses `HttpClient` with `WebSockets` plugin installed.
- Dynamic variable resolution applied to `config.url`, `config.headers`, and message payloads prior to socket dispatch.
- Authentication parameters (Bearer token, Basic auth header, API key header/query) resolved and added to handshake headers.
- Handles incoming frames:
  - `Frame.Text`: decoded as UTF-8 string, emitted as `WebSocketLogMessage(direction = RECEIVED, format = TEXT/JSON/XML)`.
  - `Frame.Binary`: emitted as `WebSocketLogMessage(direction = RECEIVED, format = BINARY)`.
  - `Frame.Ping` / `Frame.Pong`: handled automatically by Ktor engine.
  - `Frame.Close`: transitions connection state to `DISCONNECTED` with reason message.
