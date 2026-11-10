# Tasks: WebSocket Requests

**Input**: Design documents from `specs/013-websocket-requests/` (`spec.md`, `plan.md`, `data-model.md`, `contracts/`, `research.md`, `quickstart.md`)

**Prerequisites**: `plan.md` (required), `spec.md` (required for user stories), `data-model.md`, `contracts/`, `research.md`, `quickstart.md`

**Tests**: Test tasks are included as explicitly requested in user requirements (QA tests, schema validation, and unit test suites).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `- [ ] [TaskID] [P?] [Story?] Description with file path`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., [US1], [US2], [US3]...)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Dependencies & Theme Color Configuration)

**Purpose**: Theme color additions and i18n string definitions for WebSocket requests

- [X] T001 Add `methodWebSocket` color property to `AppColorPalette` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/theme/AppColorPalette.kt` with dark color `Color(0xFF29B6F6)` and light color `Color(0xFF0288D1)`
- [X] T002 [P] Expose `ClapiColors.MethodWebSocket` accessor in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/theme/ClapiTheme.kt`
- [X] T003 [P] Add i18n localization keys (`new_websocket_request`, `websocket_connect`, `websocket_disconnect`, `websocket_send`, `websocket_variants`, `websocket_messages`) in `ui-shared/src/commonMain/resources/i18n/en.json`, `de.json`, `es.json`, and `fr.json`

---

## Phase 2: Foundational (Core Models & Schemas)

**Purpose**: Core data entities, OpenCollection schema types, and polymorphic serializers that block all user stories

**⚠️ CRITICAL**: No user story work can begin until this foundational phase is complete

- [X] T004 Extend `RequestKind` enum with `WEBSOCKET` in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/McpModels.kt`
- [X] T005 [P] Define `WebSocketMessageType`, `WebSocketMessage`, `WebSocketMessageVariant`, `WebSocketMessagePayload`, `WebSocketRequestSettings`, `WebSocketRequestRuntime`, `WebSocketRequestDetails`, `WebSocketRequestInfo`, and `WebSocketRequestItem` in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/OpenCollectionItem.kt`
- [X] T006 Implement `WebSocketMessagePayloadSerializer` handling polymorphic serialization of `WebSocketMessagePayload` (Single `WebSocketMessage` vs Variants `List<WebSocketMessageVariant>`) in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/OpenCollectionItem.kt`
- [X] T007 Update `OpenCollectionItemSerializer` in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/OpenCollectionItem.kt` to handle polymorphic deserialization for `WebSocketRequestItem` when element has `websocket` or `info.type == "websocket"`
- [X] T008 [P] Define `WebSocketLogMessage` in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/RealtimeModels.kt`
- [X] T009 [P] Add unit tests for `WebSocketRequestItem` serialization and polymorphic `WebSocketMessagePayload` in `core-model/src/commonTest/kotlin/io/github/crustack/clapi/core/model/opencollection/WebSocketSerializationTest.kt`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Create and Identify WebSocket Requests in Collection Sidebar (Priority: P1) 🎯 MVP

**Goal**: Enable users to create WebSocket requests via folder and collection "+" context menus and view them in sidebar tree and tab bar with a styled `WebSocketBadge` ("WS").

**Independent Test**: Click "+" on any collection folder in sidebar, select "New WebSocket Request", and verify item is created and rendered with a "WS" badge displaying `MethodWebSocket` accent color.

### Tests for User Story 1
- [X] T010 [P] [US1] Create UI unit test verifying `WebSocketBadge` rendering and folder "+" context menu in `ui-desktop/src/desktopTest/kotlin/io/github/crustack/clapi/ui/desktop/components/SidebarWebSocketBadgeTest.kt`

### Implementation for User Story 1
- [X] T011 [P] [US1] Create `WebSocketBadge` composable with `ClapiColors.MethodWebSocket` styling in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/Sidebar.kt`
- [X] T012 [US1] Update `CollectionTreeNode` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/Sidebar.kt` to display `WebSocketBadge` when `node.kind == RequestKind.WEBSOCKET`
- [X] T013 [P] [US1] Update `RequestTabsBar` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/RequestTabsBar.kt` to render `WebSocketBadge` for tabs with `RequestKind.WEBSOCKET`
- [X] T014 [US1] Update folder and collection "+" dropdown menus in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/Sidebar.kt` to include "New WebSocket Request" action
- [X] T015 [US1] Add handler `onAddWebSocketRequest` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/Sidebar.kt` and `AppState` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt` to create a `CollectionNode` with `kind = RequestKind.WEBSOCKET` and default URL `ws://`

**Checkpoint**: User Story 1 is functional and testable independently (MVP ready).

---

## Phase 4: User Story 2 - Configure WebSocket Connection Endpoint, Handshake Headers, Auth, and Settings (Priority: P1)

**Goal**: Enable users to configure endpoint URL (`ws://`/`wss://`), handshake HTTP headers, authentication, connection timeout, keep-alive interval, and request documentation.

**Independent Test**: Open a WebSocket request, enter target URL with `{{variables}}`, configure custom handshake headers, select Bearer authentication, update timeout settings, and verify values persist in tab state.

### Implementation for User Story 2
- [X] T016 [US2] Extend `RequestTabState` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt` with fields for `webSocketTimeout`, `webSocketKeepAliveInterval`, and selected editor tab routing
- [X] T017 [P] [US2] Implement top address bar in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/WebSocketRequestEditor.kt` with `WebSocketBadge`, URL input field with variable resolution preview, and connection status indicator
- [X] T018 [US2] Implement "Headers", "Auth", "Settings" (timeout and keep-alive interval), and "Docs" tabs in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/WebSocketRequestEditor.kt`
- [X] T019 [US2] Route `tab.kind == RequestKind.WEBSOCKET` to `WebSocketRequestEditor` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/RequestEditor.kt`

**Checkpoint**: User Stories 1 and 2 operate together independently.

---

## Phase 5: User Story 3 - Compose Outgoing Messages and Manage Message Variants (Priority: P1)

**Goal**: Provide a message editor allowing users to select payload formats (TEXT, JSON, XML, BINARY), compose messages with syntax highlighting, and define/switch between multiple named message variants.

**Independent Test**: Create two message variants with different titles and JSON payloads, toggle between them, and confirm the active variant payload and format update accordingly.

### Implementation for User Story 3
- [X] T020 [US3] Extend `RequestTabState` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt` with `webSocketMessageVariants` list, `activeWebSocketVariantIndex`, and `webSocketMessageFormat`
- [X] T021 [US3] Implement message format selector (`TEXT`, `JSON`, `XML`, `BINARY`) and syntax-highlighted code editor in the "Message" tab in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/WebSocketRequestEditor.kt`
- [X] T022 [US3] Implement Message Variants management UI (variant tabs/dropdown, add variant, delete variant, rename title, select active variant) in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/WebSocketRequestEditor.kt`

**Checkpoint**: Outgoing message composition and variant management are functional.

---

## Phase 6: User Story 4 - Connect, Execute Bidirectional Messaging, and View Realtime Message Timeline (Priority: P1)

**Goal**: Connect to WebSocket endpoints, transmit messages with variable resolution, and display a real-time message stream with direction chips, timestamps, and formatted payloads.

**Independent Test**: Connect to a live WebSocket echo endpoint, verify status switches to "Connected", send a JSON message, observe both SENT and RECEIVED entries appear in the timeline with timestamps, and click Disconnect.

### Implementation for User Story 4
- [X] T023 [P] [US4] Implement `WebSocketClientManager` and `KtorWebSocketClientManager` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/websocket/WebSocketClientManager.kt` using Ktor `WebSockets` plugin to handle connect, frame sending, incoming frame loop, keep-alive, and graceful close
- [X] T024 [US4] Implement `sendWebSocketRequestInternal` and connection action handlers in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/RequestExecutor.kt` wired to `WebSocketClientManager`
- [X] T025 [US4] Implement timeline stream pane in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/WebSocketRequestEditor.kt` displaying chronological messages with SENT/RECEIVED direction badges, timestamps, byte size, format tag, copy button, and clear log action
- [X] T026 [US4] Add FIFO buffer cap (maximum 1,000 entries) and auto-scroll behavior for the message timeline in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/WebSocketRequestEditor.kt`
- [X] T027 [US4] Add cleanup handler on tab close in `AppState.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt` to gracefully close active WebSocket sessions when tabs are closed

**Checkpoint**: End-to-end interactive WebSocket communication and real-time timeline are fully operational.

---

## Phase 7: User Story 5 - OpenCollection v1.0.0 Round-Trip Persistence and Legacy Migration (Priority: P2)

**Goal**: Enable seamless round-trip import and export of collections containing WebSocket requests in OpenCollection JSON and YAML formats conforming to `opencollection_v1.0.0.json`, and handle legacy HTTP requests with `ws://` schemes.

**Independent Test**: Export a collection containing WebSocket requests to JSON and YAML, re-import into a fresh workspace, and verify all properties (URL, headers, auth, message variants, settings, docs) match 100%.

### Implementation for User Story 5
- [ ] T028 [P] [US5] Update `OpenCollectionSerializer.kt` in `core-storage/src/commonMain/kotlin/io/github/crustack/clapi/core/storage/opencollection/OpenCollectionSerializer.kt` to include `KNOWN_WEBSOCKET_REQUEST_KEYS` and handle serialization/deserialization for `WebSocketRequestItem`
- [ ] T029 [US5] Update `OpenCollectionModelMapping.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/OpenCollectionModelMapping.kt` to map between `CollectionNode`/`RequestTabState` and `WebSocketRequestItem`
- [ ] T030 [US5] Implement legacy URL fallback in `OpenCollectionModelMapping.kt` and `KtorApiClient.kt` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/KtorApiClient.kt` ensuring existing HTTP requests with `ws://`/`wss://` URLs continue to function or can be upgraded to first-class WebSocket requests
- [ ] T031 [US5] Update `TabsRepository.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/TabsRepository.kt` to persist and restore `RequestKind.WEBSOCKET` open tabs

**Checkpoint**: OpenCollection v1.0.0 persistence, serialization, and legacy migration are complete.

---

## Phase 8: Automated QA Integration Test Suite (`qa-tests`)

**Goal**: Verify full end-to-end functionality of WebSocket requests against `DummyApiServer` and schema validation in `qa-tests`.

**Independent Test**: Run `./gradlew :qa-tests:test --tests "*WebSocket*"` and verify all tests pass.

### Tests & Implementation for QA Suite
- [ ] T032 [P] Add sample WebSocket request item with message variants and settings to `clapi-test-collection.json` in `qa-tests/fixtures/clapi-test-collection.json`
- [ ] T033 Update `OpenCollectionRoundTripE2ETest.kt` in `qa-tests/src/test/kotlin/io/github/crustack/clapi/qa/OpenCollectionRoundTripE2ETest.kt` to verify `WebSocketRequestItem` survives save/load and YAML export/import round-trips
- [ ] T034 Extend `WebSocketE2ETest.kt` in `qa-tests/src/test/kotlin/io/github/crustack/clapi/qa/WebSocketE2ETest.kt` to test connection, message transmission, echo frame verification, and clean disconnection using `WebSocketClientManager` against `DummyApiServer`

**Checkpoint**: Automated regression suite verifies WebSocket requests against the mock server in CI.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Cleanup, formatting, documentation, and validation

- [ ] T035 [P] Run code formatting and static checks across modified modules (`core-model`, `core-network`, `core-storage`, `ui-shared`, `qa-tests`)
- [ ] T036 [P] Update `FEATURES.md` and `docs/architecture.md` to document WebSocket first-class request support
- [ ] T037 Execute manual verification steps per `specs/013-websocket-requests/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3-7)**:
  - US1 (Sidebar & Creation) depends on Phase 2
  - US2 (URL, Headers, Auth, Settings) depends on Phase 2
  - US3 (Messages & Variants) depends on US2
  - US4 (Execution & Timeline) depends on US2 and US3
  - US5 (Import/Export & Migration) depends on Phase 2
- **QA Integration (Phase 8)**: Depends on US4 and US5
- **Polish (Phase 9)**: Depends on all user stories and QA tests being complete

### User Story Completion Order

```text
Foundational (Phase 2)
      │
      ├──► US1: Sidebar & Folder Creation (P1) [MVP]
      │
      ├──► US2: URL, Headers, Auth, Settings (P1)
      │         │
      │         ▼
      │     US3: Outgoing Messages & Variants (P1)
      │         │
      │         ▼
      │     US4: Execution & Realtime Timeline (P1)
      │         │
      ├──► US5: OpenCollection Import/Export & Migration (P2)
      │         │
      └─────────┴──► QA Test Suite (Phase 8)
                           │
                           ▼
                     Polish (Phase 9)
```

---

## Parallel Execution Opportunities

- **Phase 1**: T001, T002, T003 can be prepared in parallel
- **Phase 2**: T004, T005, T008 (models) and T009 (tests) can proceed in parallel
- **Phase 3 (US1)**: T010 (test), T011 (badge composable), and T013 (tab bar) can be developed in parallel
- **Phase 4 (US2)**: T016 (tab state) and T017 (address bar) can run in parallel
- **Phase 6 (US4)**: T023 (`WebSocketClientManager`) can proceed in parallel with UI timeline composables
- **Phase 7 (US5)**: T028 (`OpenCollectionSerializer`) can run in parallel with T031 (`TabsRepository`)
- **Phase 8 (QA)**: T032 (fixture JSON) and T034 (E2E test) can run in parallel

---

## Implementation Strategy

### MVP First (User Story 1 Only)
1. Complete Phase 1: Theme color and localization
2. Complete Phase 2: Foundational models (`WebSocketRequestItem`, `RequestKind.WEBSOCKET`)
3. Complete Phase 3: User Story 1 (Folder "+" menu, `WebSocketBadge`, creation)
4. **Validate**: Verify WebSocket requests can be created and displayed in the sidebar tree with the distinct "WS" badge.

### Incremental Delivery
1. Add User Story 2: Address bar, handshake headers, auth, settings
2. Add User Story 3: Outgoing message composition and variant management
3. Add User Story 4: Live connection execution and real-time message stream
4. Add User Story 5: OpenCollection v1.0.0 import/export persistence and legacy URL migration
5. Add QA Test Suite: Automated regression tests against `DummyApiServer` in CI
