# Implementation Plan: WebSocket Requests

**Branch**: `013-websocket-requests` | **Date**: 2026-11-10 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/013-websocket-requests/spec.md`

## Summary

This feature introduces first-class WebSocket request support across Clapi in strict compliance with the OpenCollection v1.0.0 specification. The data model is extended with `WebSocketRequestItem`, `WebSocketRequestDetails`, `WebSocketMessagePayload`, `WebSocketMessageVariant`, `WebSocketRequestSettings`, and `WebSocketRequestRuntime`. The sidebar and collection tree provide a "New WebSocket Request" creation action and render a distinctive `WebSocketBadge` displaying "WS" styled with a new blueish `MethodWebSocket` color palette. A dedicated `WebSocketRequestEditor` provides an address bar, live connection controls (Connect/Disconnect/Send), handshake headers, authentication, message variant management, settings, and a real-time message stream timeline. Import and export strictly validate against OpenCollection schemas, while legacy HTTP requests with `ws://` schemes are supported seamlessly. Automated tests in `qa-tests` validate end-to-end communication against `DummyApiServer` and lossless round-trip serialization.

## Technical Context

**Language/Version**: Kotlin 2.1 (JVM 21 / 17 compatible)

**Primary Dependencies**:
- Ktor Client & WebSockets:
  - `io.ktor:ktor-client-core`
  - `io.ktor:ktor-client-websockets`
  - `io.ktor:ktor-websocket-serialization`
- Multiplatform UI & State:
  - Compose Multiplatform 1.8.1 (Desktop)
  - `kotlinx.coroutines`
  - `kotlinx.serialization` (JSON polymorphic serializer)
  - `kaml` (YAML support)
- Mock Server & E2E Testing:
  - `io.ktor:ktor-server-websockets` in `DummyApiServer`
  - `kotlin.test`, JUnit 4

**Storage**:
- Canonical OpenCollection format (`core-storage`, `OpenCollectionModelMapping.kt`) supporting `WebSocketRequestItem` serialization in JSON and YAML adhering to `opencollection_v1.0.0.json`.

**Testing**:
- Unit tests for OpenCollection serialization and polymorphic deserialization in `core-model` and `core-storage`
- UI component, badge, and tab state tests in `ui-desktop` / `ui-shared`
- End-to-end integration and round-trip tests in `qa-tests` (`WebSocketE2ETest`, `OpenCollectionRoundTripE2ETest`)

**Target Platform**: Desktop (Linux, macOS, Windows)

**Project Type**: Multiplatform Desktop Application

**Performance Goals**:
- Frame send-to-stream dispatch latency < 10ms.
- Smooth rendering of timeline up to 1,000 recorded frames without UI jank.
- Instantaneous (<16ms) variable interpolation in URL, headers, and outgoing messages.

**Constraints**:
- Strict conformance to OpenCollection v1.0.0 schema (`additionalProperties: false`).
- Bounded memory usage for message stream (FIFO capped at 1,000 entries).
- Clean disconnection on tab close or application exit.

**Scale/Scope**:
- Multi-protocol support (HTTP, gRPC, WebSocket).
- Full message variants and format support (text, json, xml, binary).

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

No constitutional violations exist. The architectural design adheres to:
1. **Modularity & Layering**: Schema types reside in `core-model`, network session management in `core-network`, storage mapping in `core-storage`/`ui-shared`, and UI presentation in `ui-shared`.
2. **Standard Compatibility**: Full adherence to OpenCollection v1.0.0 guarantees complete interoperability without proprietary vendor lock-in.
3. **Test-First Compliance**: Complete automated test coverage defined in `qa-tests` with mock server support in `DummyApiServer`.
4. **Resilience & Safety**: Asynchronous non-blocking coroutines, automatic FIFO message timeline eviction, and graceful connection teardown.

---

## Project Structure

### Documentation (this feature)

```text
specs/013-websocket-requests/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── websocket-editor-contract.md
│   └── websocket-client-contract.md
└── tasks.md             # Phase 2 output (/speckit-tasks command)
```

### Source Code (repository root)

```text
core-model/
├── src/commonMain/kotlin/io/github/makeforge/clapi/core/model/
│   ├── McpModels.kt                                 # RequestKind.WEBSOCKET
│   └── opencollection/
│       └── OpenCollectionItem.kt                    # WebSocketRequestItem, details, payload, settings

core-network/
├── src/commonMain/kotlin/io/github/makeforge/clapi/core/network/
│   └── websocket/
│       ├── WebSocketClientManager.kt                # Interactive session management
│       └── KtorWebSocketClientManager.kt

ui-shared/
├── src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/
│   ├── theme/
│   │   ├── AppColorPalette.kt                       # methodWebSocket color definitions
│   │   └── ClapiTheme.kt                            # ClapiColors.MethodWebSocket
│   ├── components/
│   │   ├── Sidebar.kt                               # WebSocketBadge, "+" context menu entry
│   │   ├── RequestTabsBar.kt                        # Render WebSocketBadge in tabs
│   │   ├── RequestEditor.kt                         # Route RequestKind.WEBSOCKET to editor
│   │   ├── RequestExecutor.kt                       # WebSocket connect/send/disconnect logic
│   │   └── WebSocketRequestEditor.kt                # Full WebSocket request editor
│   ├── persistence/
│   │   ├── OpenCollectionModelMapping.kt            # Import/export mapping for WebSocketRequestItem
│   │   └── TabsRepository.kt                        # Persist/restore RequestKind.WEBSOCKET tabs
│   └── state/
│       └── AppState.kt                              # RequestTabState WebSocket state fields

qa-tests/
├── src/test/kotlin/io/github/makeforge/clapi/qa/
│   ├── WebSocketE2ETest.kt                          # E2E test against DummyApiServer
│   └── OpenCollectionRoundTripE2ETest.kt            # Round-trip fixture with WebSocket request
```

**Structure Decision**:
Follows the established multi-module layout of Clapi, matching the gRPC implementation structure (`specs/005-grpc-support`).

---

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *None* | — | — |
