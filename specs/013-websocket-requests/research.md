# Research & Technical Decisions: WebSocket Requests

**Feature**: `013-websocket-requests`
**Date**: 2026-11-10

## 1. OpenCollection v1.0.0 WebSocket Schema Representation

### Context
OpenCollection v1.0.0 defines `WebSocketRequest` as a first-class `Item` alongside `HttpRequest`, `GrpcRequest`, and `Folder`.
Its structure in the JSON schema (`scratch/opencollection_v1.0.0.json`):
- `info`: `WebSocketRequestInfo` (`name`, `description`, `type = "websocket"`, `seq`, `tags`)
- `websocket`: `WebSocketRequestDetails`
  - `url`: string
  - `headers`: array of `HttpRequestHeader` (`name`, `value`, `description`, `disabled`)
  - `message`: `oneOf` [ `WebSocketMessage`, array of `WebSocketMessageVariant` ]
  - `auth`: `Auth`
- `runtime`: `WebSocketRequestRuntime` (`variables`, `scripts`)
- `settings`: `WebSocketRequestSettings` (`timeout`, `keepAliveInterval`)
- `docs`: string documentation

### Decision: Polymorphic `message` Handling in Kotlin Serialization
- Define `WebSocketMessage` with `type: WebSocketMessageType` (`text`, `json`, `xml`, `binary`) and `data: String`.
- Define `WebSocketMessageVariant` with `title: String`, `selected: Boolean`, and `message: WebSocketMessage`.
- Model the `message` field as a sealed interface `WebSocketMessagePayload`:
  - `WebSocketMessagePayload.Single(val message: WebSocketMessage)`
  - `WebSocketMessagePayload.Variants(val variants: List<WebSocketMessageVariant>)`
- Implement custom `KSerializer<WebSocketMessagePayload>`:
  - During serialization: encodes either as a single JSON object or a JSON array.
  - During deserialization: inspects `JsonElement`; if `JsonArray`, decodes as `Variants`; if `JsonObject`, decodes as `Single`.
- Rationale: Fully adheres to OpenCollection v1.0.0 strict `additionalProperties: false` validation while providing a clean, idiomatic Kotlin API.

---

## 2. Interactive WebSocket Connection Lifecycle & Tab State Scoping

### Context
Unlike unary HTTP/gRPC requests which execute a single request/response cycle, a WebSocket connection is interactive, persistent, and bidirectional. Users can connect, send multiple messages, receive streaming frames asynchronously, switch tabs, and disconnect.

### Decision: Tab-Scoped Active Session State
- Embed session and timeline observables inside `RequestTabState`:
  - `connectionState: ConnectionState` (`DISCONNECTED`, `CONNECTING`, `CONNECTED`, `ERROR`)
  - `connectionJob: Job?`
  - `activeWebSocketSession: WebSocketSession?`
  - `messageLog: SnapshotStateList<WebSocketLogMessage>`
  - `messageVariants: SnapshotStateList<WebSocketMessageVariant>`
  - `activeVariantIndex: Int`
  - `selectedMessageFormat: WebSocketMessageType`
- Keep connection coroutines active even when the user switches editor tabs.
- When a tab is closed, automatically trigger graceful socket disconnect (`close(CloseReason.NORMAL)`).
- Bound the in-memory timeline history to 1,000 messages with FIFO eviction to prevent memory leaks during rapid streaming.
- Rationale: Integrates seamlessly with Clapi's existing tab model without requiring a disconnected separate window or losing connection state when browsing collection items.

---

## 3. UI Color Scheme for WebSocket Badge

### Context
The user requested: "For Websocket request Badge in UI, define new color in ClapiColors, a blueish color for Websocket".
Existing method badge colors:
- GET: Mint Teal (`#4EC9B0` / `#0FA88E`)
- POST: Amber (`#E5C07B` / `#B07D10`)
- PUT: Periwinkle (`#61AFEF` / `#4B6BF5`)
- PATCH / gRPC: Violet/Purple (`#C678DD` / `#8B44CC`)
- DELETE: Coral Red (`#E06C75` / `#CF3141`)
- TRACE / CONNECT: Cyan (`#56B6C2` / `#2A8FA3`)

### Decision: Dedicated `MethodWebSocket` Accent Color
- Dark Palette (`DarkAppColors`): `Color(0xFF29B6F6)` (Vivid Light Sky Blue, hex `#29B6F6`), high contrast against dark surface `#18181B`.
- Light Palette (`LightAppColors`): `Color(0xFF0288D1)` (Deep Ocean Blue, hex `#0288D1`), high legibility on light background `#F8FAFC`.
- Expose via `ClapiColors.MethodWebSocket`.
- Badge: `WebSocketBadge` displaying `"WS"` with `ClapiColors.MethodWebSocket` and background `MethodWebSocket.copy(alpha = 0.12f)`.

---

## 4. Execution Engine & Backwards Compatibility

### Context
In existing code (`KtorApiClient.kt`), an HTTP request with a URL starting with `ws://` or `wss://` was intercepted and executed via `executeWebSocketRequest`, returning a synthetic 101 response with 2 frames.

### Decision: First-Class `WebSocketClient` & Legacy Preservation
- Create dedicated `WebSocketConnectionService` in `core-network` / `feature-requests` utilizing Ktor's `WebSockets` plugin to manage full interactive sessions (connect, send, receive, disconnect, ping/pong).
- Introduce `sendWebSocketRequestInternal` in `RequestExecutor.kt` wired to `tab.kind == RequestKind.WEBSOCKET`.
- Retain backwards-compatibility check in `KtorApiClient` for legacy single-shot tests or legacy HTTP requests with `ws://` URLs, while migrating and encouraging first-class `RequestKind.WEBSOCKET`.
