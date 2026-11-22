# Contract: Interactive UI E2E Test Suites & Coverage Guarantees

**Feature**: `034-release-e2e-test-suite`  
**Covers**: `FR-005` through `FR-015`

All suites live under `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/e2e/` (plus targeted unit/integration files where appropriate) and drive the real `MainScreen(state)` composable using `createComposeRule()` against deterministic local servers.

---

## Suite 1: `HttpRequestUiE2ETest` (`FR-005`)
- **Server**: `EmbeddedSampleServerRule`
- **Mandatory Scenarios**:
  1. `GET`, `POST`, `PUT`, `DELETE` via UI method dropdown + `url-input` (using `{{baseUrl}}`) + `send-button` click -> verify `ResponseViewer` shows `200`/`201`, latency, size, formatted JSON body, Headers tab, Cookies tab, Timing tab, Raw tab.
  2. Body formats on the wire via `BodyEditor`: JSON, JSON5 (comments + trailing comma transmitted as strict JSON), `FORM_DATA`, `URL_ENCODED`, `XML`, `RAW` against `/api/json`, `/api/form-data`, `/api/urlencoded`, `/api/raw`.
  3. Auth via `AuthEditor`: Basic (`/api/auth/basic`), Bearer (`/api/auth/bearer`), API Key (`/api/auth/apikey`) — both valid (`200`) and invalid (`401`).
  4. OAuth2 live token fetch via `AuthEditor` "Fetch Tokens" against `sample-server` `/oauth/token` -> execute request using `{{_oauth2.accessToken}}` against `/mcp/secure` (or protected Bearer endpoint).
  5. Collection & Folder inheritance on the wire: configure Bearer auth + custom header + variable on Collection/Folder -> save -> open child request with `Auth = Inherit` -> click Send -> verify echoed header & `200 OK`.
  6. History & Console side effects: clicking Send records new History entry -> clicking History entry reopens request -> Bottom Panel Console records execution.
  7. In-flight cancellation: Send to `/api/slow?ms=5000` -> assert `stop-button` displayed -> click `stop-button` -> assert `send-button` returns, no stale response populates, subsequent Send succeeds.
  8. Error & Retry UI flow: `/api/error/404`, `/api/error/500`, unreachable port, invalid URL, and retry config (`retryEnabled = true`, attempts = 2) executing cleanly without hanging.

## Suite 2: `ScriptingAndVariablesUiE2ETest` (`FR-006`, `FR-014`, `FR-015`)
- **Server**: `EmbeddedSampleServerRule`
- **Mandatory Scenarios**:
  1. Pre-request script mutating outgoing header (`clapi.request.setHeader`) & env var (`clapi.environment.set`) + Post-request script with 1 passing `clapi.test`, 1 failing `clapi.test`, and `clapi.console.log` -> click Send -> verify server received mutated header, `test-results` displays pass/fail counts & names, `console-output` displays log, and active env reflects new variable.
  2. Pre/post script syntax or runtime exception -> clear UI error reported, `send-button` restored (not stuck loading).
  3. Unquoted typed dynamic variables in JSON body (`{"count": {{n}}, "flag": {{b}}}`) sent via UI under JSON5 ON and JSON5 OFF; invalid JSON5 rejected cleanly when JSON5 setting is OFF.
  4. Circular/self-referencing variables (`a={{b}}`, `b={{a}}`) and unresolved/malformed templates (`{{missing}}`, `{{unclosed`) terminate deterministically without freezing UI.
  5. Secret redaction across all UI surfaces: `secret = true` in active env, inherited parent env, global variables, and external secret mock -> verify plaintext secret NEVER appears in Console output, Diagnostics logs (`UiDiagnosticsLogWriter`), persisted History items, `CrashReportDialog` copy/bug-report URL, or redacted command exports.

## Suite 3: `StreamingAndRealtimeUiE2ETest` (`FR-007`, `FR-008`)
- **Server**: `EmbeddedSampleServerRule`
- **Mandatory Scenarios**:
  1. Finite SSE (`GET /sse?count=3&delayMs=20` and `POST /sse`) via `MainScreen` -> verify SSE badge, incremental events in `response-live-stream`, and completion state.
  2. LLM streaming (`POST /v1/chat/completions` with `"stream": true`) -> verify live assembled text preview, TTFT/metrics, manual Stop mid-stream, and `?earlyClose=true` partial text preservation.
  3. `RealtimePanel` (`WorkspaceMode.REALTIME`) UI rendering & interactive controls smoke check.
  4. GraphQL UI E2E via `MainScreen`: create GraphQL request -> POST with query + variables -> GET mode -> switch variants and verify active variant executes against `/api/graphql` -> verify `ResponseViewer`.
  5. WebSocket UI E2E via `MainScreen`: open WS tab -> connect to `ws://127.0.0.1:<port>/ws` -> verify `CONNECTED` -> send text/JSON message -> verify `↑ SENT` and echoed `↓ RECEIVED` in `websocket-timeline` -> disconnect -> verify `DISCONNECTED`; plus closing a tab while `CONNECTED` cleans up cleanly.

## Suite 4: `McpAndGrpcUiE2ETest` (`FR-008`, `FR-009`)
- **Servers**: `EmbeddedSampleServerRule` + `EmbeddedGrpcTestServerRule`
- **Mandatory Scenarios**:
  1. `McpPanel` Streamable HTTP (`/mcp`) & Legacy HTTP+SSE (`/mcp/sse`): enter URL in `mcp-url` -> click `mcp-connect` -> assert `mcp-status-CONNECTED` -> select `echo` tool, fill form & raw JSON args, invoke -> verify result -> switch to Resources (list/read/subscribe), Prompts, and Activity Log (`mcp-activity-payload`) -> disconnect via `mcp-disconnect`.
  2. `McpPanel` interactive callbacks: invoke `trigger_sampling` and `trigger_elicitation` in manual mode -> interact with `McpCallbackPane` -> verify echoed tool result in UI.
  3. Closing an active MCP tab tears down session cleanly.
  4. `GrpcRequestEditor` in `MainScreen`: configure `.proto` file (direct and collection-inherited) -> select discovered `helloworld.Greeter/SayHello` in `grpc-method-dropdown` -> enter JSON message & metadata -> click `send-button` -> verify gRPC `OK` status, response JSON, headers, and trailers; also test error status.

## Suite 5: `RabbitMqUiE2ETest` (`FR-010`)
- **Mandatory Broker-Independent UI Tests**:
  1. Render `RabbitMqRequestEditor` in `MainScreen`: switch `PUBLISH` -> `SUBSCRIBE` -> `RPC` -> verify `MESSAGE` & `HEADERS` tabs hide in `SUBSCRIBE` mode and active tab falls back to `AMQP`.
  2. Edit connection host/port, exchange, routing key, queue, headers, and payload -> verify dirty indicator and save round-trip.
  3. Click Publish/Subscribe against an unreachable localhost port -> verify clear error message in UI without crash.
- **Live Broker UI E2E** (runs when RabbitMQ broker is reachable; fails hard when `CI=true` and RabbitMQ compose service is expected):
  4. Publish + Subscribe live message delivery in `RabbitMqTimelinePane` with manual Ack/Nack controls + RPC reply in `RabbitMqRpcReplyPane`.

## Suite 6: `DialogsSettingsAndShortcutsUiTest` (`FR-011`, `FR-012`)
- **Mandatory Scenarios**:
  1. `ExportCollectionsDialog`: open via sidebar export menu -> toggle Select All / individual collections -> switch JSON vs YAML -> confirm with temp `onPickDirectory` -> assert exported files exist on disk and dialog dismisses.
  2. `McpServerSettingsSection`: Settings -> MCP Server -> toggle `mcp-server-enable-toggle` ON -> verify status card shows running -> change port -> verify restart -> click copy config snippet -> toggle OFF -> verify stopped.
  3. `ThemeSettingsPane` + TopToolbar theme toggle: switch Light / Dark / System -> verify `state.settings.theme` updates, persists via `SettingsRepository`, and recomposes `ClapiTheme`.
  4. TopToolbar `env-chip` picker: switch between Base and Child inherited environments -> verify `{{var}}` resolution updates immediately on Send.
  5. Global hotkeys in `MainScreen`: `Ctrl+Enter` (Send), `Ctrl+S` (Save), `Ctrl+N` (New Tab), `Ctrl+W` (Close clean tab & Close dirty tab prompting `dirty-close-dialog`), `Ctrl+,` (Open Settings), `Ctrl+Shift+[` / `]` (Move Tab Left/Right).
  6. `DirtyMultiCloseDialog`: right-click tab -> "Close Others" / "Close All" with 2+ dirty tabs -> verify Cancel, Discard All, and Save All.
  7. Real Sidebar Undo Snackbar E2E: trigger UI delete of request, nested folder (with children), collection, environment, and history item + rename + move -> assert `action-snackbar` appears -> click `action-snackbar-undo` -> assert full restoration in Sidebar UI, open tabs, and persistence.
  8. `BottomPanel` interactions: switch Console / Test Results / Logs, clear logs, verify diagnostics log rendering, collapse/expand bottom panel.
  9. Detached Response Viewer lifecycle: detach -> send request -> switch tabs -> close last tab (shows empty state) -> reattach.

## Suite 7: `ResilienceLayoutAndRoundTripUiTest` (`FR-013`, `FR-015`)
- **Mandatory Scenarios**:
  1. Corrupt/truncated storage recovery (`clapi.tabs`, `clapi.settings`, corrupted YAML/JSON collection file alongside valid collection) -> boots cleanly, reports error, preserves valid collections.
  2. Orphaned tab restoration (tab references deleted request ID) -> opens/prunes cleanly without crash.
  3. Window-close shutdown persistence & teardown (`TabsRepository.save` + `WorkspaceRepository.save` + `disposeAndAwait()` with active WS/MCP session).
  4. Binary response payload (non-UTF8 bytes from `/api/upload` or binary endpoint) -> renders safely without crashing or corrupting download action.
  5. Minimum window size `800.dp x 600.dp` layout smoke test -> `url-input`, `send-button`, `request-tabs-bar`, `sidebar`, `response-viewer` remain displayed and clickable.
  6. 20 open tabs overflow scrolling, reordering, closing index 0/middle/last, and closing a tab while `isLoading = true`.
  7. 5-level nested folder search filtering, deleting a parent folder with open child tab, and moving requests across collections.
  8. Malformed OpenAPI / Postman / Insomnia / OpenCollection imports rejected with error banner and zero workspace mutation.
  9. Multi-protocol (HTTP + GraphQL + gRPC + WS + MCP + RabbitMQ + Env) Export -> Delete -> Re-Import UI round-trip preserves all protocol fields.
  10. `AotTrainingRunner.runTrainingPass(state)` smoke test executing cleanly inside `MainScreen`.
