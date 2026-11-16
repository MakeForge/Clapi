# Quickstart & Verification Guide: WebSocket Requests

**Feature**: `013-websocket-requests`
**Date**: 2026-11-10

This guide outlines end-to-end verification scenarios to validate WebSocket request functionality.

---

## 1. Automated E2E Verification (`qa-tests`)

### Scenario 1.1: Live Bidirectional Echo Test
- **Target**: `DummyApiServer` local endpoint `ws://localhost:<port>/ws`
- **Execution Command**:
  ```bash
  ./gradlew :qa-tests:jvmTest --tests "io.github.crustack.clapi.qa.WebSocketE2ETest"
  ```
- **Expectation**:
  - Connects to `/ws`
  - Receives initial server message `"connected"`
  - Sends text `"hello"` and receives `"echo:hello"`
  - Disconnects cleanly with goodbye sequence

### Scenario 1.2: OpenCollection Round-Trip Serialization
- **Target**: `OpenCollectionRoundTripE2ETest`
- **Execution Command**:
  ```bash
  ./gradlew :qa-tests:jvmTest --tests "io.github.crustack.clapi.qa.OpenCollectionRoundTripE2ETest"
  ```
- **Expectation**:
  - OpenCollection document containing `WebSocketRequestItem` with custom headers, auth, message variants, settings, and docs serializes to JSON.
  - JSON strictly passes OpenCollection v1.0.0 schema validation.
  - Deserialized back to `WebSocketRequestItem` matching original object properties 100%.

---

## 2. Interactive UI Verification

### Scenario 2.1: Creation & Styling
1. Launch the desktop application:
   ```bash
   ./gradlew :ui-desktop:run
   ```
2. In the collection sidebar, click the `+` action on any folder or collection root.
3. Select **"New WebSocket Request"**.
4. **Verification**:
   - Item appears in the tree with name `"New Request"`.
   - Distinct `WS` badge styled with `ClapiColors.MethodWebSocket` (light blue) is displayed next to the title.
   - An editor tab opens with the `WS` badge and the title.

### Scenario 2.2: Live Messaging & Timeline
1. Enter `ws://localhost:8080/ws` (or public echo endpoint `wss://echo.websocket.org`).
2. Click **Connect**.
   - Verify connection pill transitions to `"Connected"` in green.
3. In the **Message** tab, select format `JSON` and enter:
   ```json
   {"action": "ping", "timestamp": 123456}
   ```
4. Click **Send**.
5. **Verification**:
   - A `SENT` chip entry appears in the right-side timeline with timestamp and formatted payload.
   - The server response appears as a `RECEIVED` chip entry immediately beneath it.
6. Click **Disconnect**.
   - Verify status pill switches to `"Disconnected"`.
