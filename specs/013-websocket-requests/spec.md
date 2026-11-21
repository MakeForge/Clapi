# Feature Specification: WebSocket Requests

**Feature Branch**: `013-websocket-requests`

**Created**: 2026-11-10

**Status**: Draft

**Input**: User description: "Support creating/editing/executing websocket requests - Check for existing implementation, I think maybe websocket request are already implemented but simply only if a http request has url starting with ws/wss. Websocket requests should now be a first-level request type like HTTP or gRPC requests in the OpenCollection schema - Use OpenCollection Websocket request types - Check OpenCollection websocket fields user could edit - Ensure import/export works fine - Ensure qa-tests covers this - For Websocket request Badge in UI, define new color in ClapiColors, a blueish color for Websocket"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create and Identify WebSocket Requests in Collection Sidebar (Priority: P1)

Users organizing multi-protocol API collections need to add WebSocket requests alongside existing HTTP and gRPC requests within folders and collections. When a user clicks the "+" action button on any folder or collection root in the sidebar, the menu provides an option to create a "New WebSocket Request". When selected, a new first-class WebSocket request item is created and added to the folder or collection. In the sidebar tree and tab bar, the item displays with a dedicated `WebSocketBadge` showing "WS" styled with a distinct blueish theme color (`MethodWebSocket`). Clicking the item opens the request in a specialized WebSocket editor tab.

**Why this priority**: Discoverability and creation of first-class WebSocket requests within the collection tree is the essential entry point for the entire feature.

**Independent Test**: Click "+" on any folder in the sidebar, choose "New WebSocket Request", verify that a new item appears in the folder, verify that the sidebar renders a "WS" badge with the new blueish styling, and verify clicking the item opens a dedicated WebSocket editor tab displaying the "WS" badge in the tab bar.

**Acceptance Scenarios**:

1. **Given** a collection folder or collection root with an add action button ("+"), **When** the user clicks the "+" button, **Then** a dropdown menu presents options including "New HTTP Request", "New gRPC Request", and "New WebSocket Request".
2. **Given** the add menu is open, **When** the user selects "New WebSocket Request", **Then** a new first-class WebSocket request item is instantiated with default name "New Request" and added to the target folder or collection.
3. **Given** a WebSocket request in the sidebar tree, **When** rendered in the collection hierarchy, **Then** it displays the request name and a `WebSocketBadge` showing "WS" with the distinctive blueish theme color.
4. **Given** an active or inactive tab for a WebSocket request, **When** displayed in the editor tab bar, **Then** the tab header displays the request title and the `WebSocketBadge`.

---

### User Story 2 - Configure WebSocket Connection Endpoint, Handshake Headers, Auth, and Settings (Priority: P1)

Users need to target a WebSocket server, configure handshake headers, set up authentication credentials, and adjust connection settings. In the WebSocket editor, the user enters an endpoint address (e.g. `ws://localhost:8080/ws` or `wss://echo.websocket.org`) in a URL input supporting dynamic variable interpolation (e.g., `{{ws_base_url}}/stream`). In dedicated sub-tabs, users can:
- Configure custom handshake headers (name, value, description, enable/disable toggle).
- Select and configure authentication (Inherit from parent folder/collection, Bearer token, Basic Auth, API Key).
- Configure connection settings: connection timeout and keep-alive ping interval (with option to inherit from collection or define explicit milliseconds).
- Write and view Markdown documentation for the request in a "Docs" tab.

**Why this priority**: Connecting to authenticated or customized WebSocket gateways requires configuring target endpoints, headers, authentication tokens, and timeouts.

**Independent Test**: Open a WebSocket request, enter a target URL with variable tokens (e.g. `{{ws_host}}/chat`), add a handshake header, configure Bearer authentication, update connection timeout in settings, save the request, and verify all values persist in the request state.

**Acceptance Scenarios**:

1. **Given** an active WebSocket request editor, **When** viewing the address bar, **Then** a URL input field is provided supporting `ws://` and `wss://` schemes and resolving dynamic variables (e.g., `{{wsHost}}:{{wsPort}}/feed`).
2. **Given** the "Headers" tab in the WebSocket editor, **When** the user adds custom headers, **Then** key-value entries with descriptions can be added, modified, toggled on/off, or deleted.
3. **Given** the "Auth" tab in the WebSocket editor, **When** configuring authentication, **Then** the user can select between Inherit, None, Bearer, Basic, and API Key authentication, with resolved credentials injected into the initial handshake.
4. **Given** the "Settings" tab in the WebSocket editor, **When** configuring connection options, **Then** fields for connection timeout and keep-alive ping interval (either numeric milliseconds or "inherit") are provided and editable.
5. **Given** the "Docs" tab in the WebSocket editor, **When** entering documentation text, **Then** the content is preserved as Markdown documentation for the request.

---

### User Story 3 - Compose Outgoing Messages and Manage Message Variants (Priority: P1)

Users need to formulate messages to send across the WebSocket connection. The editor provides a "Message" composition area with:
- Message type selection supporting `text`, `json`, `xml`, and `binary` formats.
- A text/code editor with syntax highlighting for the selected format.
- Support for message variants (multiple saved messages with titles), allowing users to switch between predefined payload variants and select an active default variant.
- Variable interpolation: `{{variable}}` syntax within the message body is resolved at send time.

**Why this priority**: Testing real-time APIs requires sending diverse payloads (event triggers, query subscriptions, commands) and quickly switching between predefined message shapes without manually re-typing or copy-pasting.

**Independent Test**: Compose a JSON message payload in the editor, create a second message variant with a different title and payload, switch between variants, click send while connected, and verify the selected variant's resolved payload is transmitted.

**Acceptance Scenarios**:

1. **Given** the "Message" tab in the WebSocket editor, **When** viewing the message editor, **Then** the user can choose the message type (`text`, `json`, `xml`, `binary`) and enter payload data in a syntax-highlighted editor.
2. **Given** a user working with multiple payload types, **When** adding message variants, **Then** each variant has an editable title, payload content, and a selection state indicating the currently active variant.
3. **Given** a message payload containing dynamic variable tokens (e.g., `{"token": "{{authToken}}", "action": "subscribe"}`), **When** sent over the connection, **Then** variable tokens are resolved before transmitting the frame.

---

### User Story 4 - Connect, Execute Bidirectional Messaging, and View Realtime Message Timeline (Priority: P1)

Users need to establish a live connection to the WebSocket endpoint, transmit messages on demand, and observe bidirectional traffic in real time. The top bar contains a primary "Connect" / "Disconnect" action and a "Send" action. When connected, the editor displays a live connection status pill ("Connected", "Connecting", "Disconnected", "Error"), connection elapsed time, and a real-time message stream pane. The stream pane shows sent and received messages chronologically with:
- Message direction badge (SENT vs RECEIVED).
- Message type (text, json, binary).
- Timestamp and payload size.
- Expandable message body with syntax formatting and a one-click copy button.
- Actions to clear the message stream, filter messages, and inspect connection close codes.

**Why this priority**: Interactive bidirectional communication and real-time observability of incoming/outgoing frames are the core operational capabilities of a WebSocket client.

**Independent Test**: Connect to a running WebSocket server (such as the QA test server `/ws`), verify the status switches to "Connected", send a message from the message editor, verify the sent message and echoed reply appear in the timeline with correct timestamps and direction badges, and click "Disconnect" to close the socket cleanly.

**Acceptance Scenarios**:

1. **Given** a configured WebSocket request with a valid endpoint, **When** the user clicks "Connect", **Then** the client initiates the WebSocket handshake, transitions status to "Connecting" then "Connected", and enables message transmission.
2. **Given** an active WebSocket connection, **When** the user clicks "Send", **Then** the current message payload (with variables resolved) is dispatched as a WebSocket frame, and a "SENT" record is appended to the message timeline.
3. **Given** incoming frames received from the server, **When** received, **Then** each frame is immediately appended to the message timeline as a "RECEIVED" record with timestamp, payload size, and content preview.
4. **Given** an active connection, **When** the user clicks "Disconnect", **Then** the connection is gracefully closed, the status changes to "Disconnected", and the reason/code is displayed.
5. **Given** a network failure or server disconnect, **When** the connection drops or fails handshake, **Then** the status transitions to "Error" with a clear diagnostic message displayed in the UI.

---

### User Story 5 - OpenCollection v1.0.0 Round-Trip Persistence and Legacy Migration (Priority: P2)

Users need their WebSocket requests saved to disk, exported, and imported conforming strictly to the OpenCollection v1.0.0 specification. WebSocket requests must be serialized with `info.type = "websocket"` and a complete `websocket` object structure containing `url`, `headers`, `message` (single message or variants array), `auth`, `runtime`, `settings`, and `docs`. Furthermore, if an older collection or file contains an HTTP request that was configured with a `ws://` or `wss://` URL scheme, the system must detect it and cleanly map or handle it as a first-class WebSocket request.

**Why this priority**: Guarantees interoperability with external tools adhering to OpenCollection v1.0.0 and prevents data loss when sharing or migrating workspace collections.

**Independent Test**: Export a collection containing WebSocket requests to an OpenCollection JSON/YAML file, re-import it into a fresh workspace, and assert that all fields (`url`, headers, message/variants, auth, settings, scripts, docs) are identical. Open a collection containing a legacy HTTP request with a `ws://` URL and verify it behaves seamlessly.

**Acceptance Scenarios**:

1. **Given** a collection containing WebSocket requests, **When** the collection is saved or exported to OpenCollection JSON or YAML, **Then** each item is serialized as a `WebSocketRequest` with `info.type: "websocket"` complying with the OpenCollection v1.0.0 schema.
2. **Given** an OpenCollection file containing `type: "websocket"` items, **When** imported or loaded into Clapi, **Then** polymorphic deserialization parses them as `WebSocketRequestItem` with all properties restored.
3. **Given** a legacy collection where a request has `kind = HTTP` but the URL scheme is `ws://` or `wss://`, **When** loaded or executed, **Then** the system gracefully recognizes the protocol and allows seamless execution and editing without errors.

---

### Edge Cases

- What happens when a user attempts to "Send" a message while the WebSocket is disconnected?
  - The application prevents sending, displays an unobtrusive warning ("Connect to endpoint before sending messages"), and highlights the "Connect" button.
- What happens when a WebSocket URL uses an invalid scheme (e.g. `http://` instead of `ws://` or `wss://`)?
  - The client provides inline validation and automatically handles or normalizes scheme conversions (e.g. promoting `http://` to `ws://` and `https://` to `wss://` with user notification).
- What happens when an incoming frame contains non-UTF8 binary data?
  - The timeline displays the frame as "Binary", indicates the byte length, and provides an option to view hexadecimal representation or download/copy the raw bytes.
- What happens when the user switches tabs or navigates away while a WebSocket connection is active?
  - The connection remains active in the background, messages continue to accumulate in the tab's timeline buffer, and the tab displays an active connection indicator.
- What happens when large volumes of messages arrive rapidly (e.g. high-frequency market data)?
  - The message timeline caps its in-memory buffer (e.g. default maximum of 1,000 entries) with automatic eviction of oldest entries, preventing UI freeze and out-of-memory errors.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST support WebSocket requests as a first-class `OpenCollectionItem` type (`WebSocketRequestItem`) matching OpenCollection v1.0.0 specification with `info.type = "websocket"`.
- **FR-002**: The system MUST implement OpenCollection WebSocket schema structures including `WebSocketRequestInfo`, `WebSocketRequestDetails`, `WebSocketRequestRuntime`, `WebSocketRequestSettings`, `WebSocketMessage`, and `WebSocketMessageVariant`.
- **FR-003**: The collection sidebar context menu on folders and collection roots MUST include an action to create a "New WebSocket Request" alongside HTTP and gRPC request creation.
- **FR-004**: In the sidebar tree and editor tab bar, WebSocket requests MUST display a dedicated `WebSocketBadge` displaying the text "WS".
- **FR-005**: The system MUST define a new theme color in `ClapiColors` (e.g., `MethodWebSocket`) representing a distinct blueish hue for WebSocket requests across dark and light themes, used for `WebSocketBadge` text and background tints.
- **FR-006**: The WebSocket request editor MUST provide an address bar supporting `ws://` and `wss://` URL schemes with dynamic variable resolution (`{{variable}}`).
- **FR-007**: The WebSocket request editor MUST provide an interactive "Connect" / "Disconnect" control that initiates and terminates the WebSocket session.
- **FR-008**: The WebSocket request editor MUST provide a "Headers" sub-tab allowing users to configure custom HTTP handshake headers (name, value, description, enabled/disabled state).
- **FR-009**: The WebSocket request editor MUST provide an "Auth" sub-tab supporting authentication modes (Inherit, None, Bearer, Basic, API Key) injected into the handshake.
- **FR-010**: The WebSocket request editor MUST provide a "Message" sub-tab allowing users to author message content with selectable format (`text`, `json`, `xml`, `binary`) and syntax highlighting.
- **FR-011**: The WebSocket request editor MUST support message variants, enabling users to maintain multiple named payload variants with title and selection state.
- **FR-012**: The WebSocket request editor MUST provide a "Send" action that resolves dynamic variables in the active message and transmits it over the connected socket.
- **FR-013**: The WebSocket request editor MUST provide a real-time message stream panel displaying sent and received messages chronologically with timestamps, direction indicators (SENT/RECEIVED), frame size, and formatted preview.
- **FR-014**: The WebSocket request editor MUST provide a "Settings" sub-tab allowing users to configure connection timeout and keep-alive ping interval.
- **FR-015**: The WebSocket request editor MUST provide a "Docs" sub-tab for reading and editing Markdown documentation associated with the request.
- **FR-016**: The system MUST support full round-trip serialization and deserialization of WebSocket requests in OpenCollection JSON and YAML formats without property loss.
- **FR-017**: The system MUST gracefully handle legacy HTTP requests configured with `ws://` or `wss://` URLs by supporting their execution or offering seamless migration to first-class WebSocket requests.
- **FR-018**: The system's QA test suite (`qa-tests`) MUST include automated end-to-end integration tests validating WebSocket request creation, OpenCollection schema validation, and live bidirectional communication against the test dummy server.

### Key Entities

- **WebSocketRequestItem**: Represents a complete WebSocket request item in an OpenCollection document, containing `info: WebSocketRequestInfo`, `websocket: WebSocketRequestDetails`, optional `runtime: WebSocketRequestRuntime`, optional `settings: WebSocketRequestSettings`, and optional `docs: String`.
- **WebSocketRequestDetails**: Encapsulates protocol-specific configuration: `url: String`, `headers: List<HttpRequestHeader>`, `message: WebSocketMessagePayload` (representing either a single message or list of message variants), and optional `auth: AuthConfig`.
- **WebSocketMessage**: Represents an individual message payload with `type: String` (`text`, `json`, `xml`, `binary`) and `data: String`.
- **WebSocketMessageVariant**: Represents a saved message variation with `title: String`, `selected: Boolean`, and `message: WebSocketMessage`.
- **WebSocketRequestSettings**: Encapsulates connection parameters including `timeout: TimeoutSetting` (numeric ms or "inherit") and `keepAliveInterval: KeepAliveSetting` (numeric ms or "inherit").
- **WebSocketMessageLogEntry**: Represents an in-memory recorded frame in the active connection timeline with unique id, timestamp, direction (SENT / RECEIVED), message format, payload content, byte size, and status.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can create a new WebSocket request and establish a live connection to a local or remote endpoint in under 15 seconds.
- **SC-002**: 100% of WebSocket requests exported to OpenCollection JSON or YAML strictly validate against the official OpenCollection v1.0.0 JSON schema.
- **SC-003**: Bidirectional messages sent and received appear in the real-time timeline within 50 milliseconds of socket delivery.
- **SC-004**: Round-trip import and export of collections with WebSocket requests preserves 100% of configured fields (URL, headers, auth, message variants, settings, and documentation).
- **SC-005**: All QA test suite WebSocket E2E tests pass reliably in CI across Linux, macOS, and Windows environments.

## Assumptions

- WebSocket connections utilize standard WebSocket protocol (RFC 6455) over HTTP/1.1 Upgrade handshakes.
- Authentication parameters configured in the Auth tab are applied during the initial HTTP upgrade handshake as standard request headers or query parameters.
- Message timeline history is ephemeral to the active tab session and is not persisted into OpenCollection file artifacts (only request definitions and saved message variants are persisted).
- The maximum in-memory message history buffer defaults to 1,000 entries per open WebSocket tab to ensure memory safety under high message rates.
- Legacy requests with `ws://` or `wss://` URLs continue to function during upgrade transitions without breaking user collections.
