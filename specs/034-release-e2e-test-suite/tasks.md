---

description: "Executable task list for Release-Ready E2E & UI Verification Suite"
---

# Tasks: Release-Ready E2E & UI Verification Suite

**Input**: Design documents from `/specs/034-release-e2e-test-suite/`  
**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/ci-and-smoke-contracts.md](./contracts/ci-and-smoke-contracts.md), [contracts/ui-e2e-test-contracts.md](./contracts/ui-e2e-test-contracts.md), [quickstart.md](./quickstart.md)

**Tests**: Explicitly requested — this feature IS the release-readiness test, CI quality-gate, packaged smoke, and interactive UI E2E verification suite.

**Organization**: Tasks are grouped by user story (`US1`–`US5`) to enable independent implementation and verification of each release-readiness increment.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: User story label (`[US1]`–`[US5]`) for user story phases only
- Every task includes exact repository-relative file paths

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create the UI E2E package hierarchy and verify existing test classpath readiness.

- [ ] T001 Create UI E2E test package directories `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/e2e/support/` and `ui-desktop/src/desktopTest/snapshots/roborazzi/`
- [ ] T002 Verify `:ui-desktop:compileTestKotlinDesktop` succeeds with `:sample-server`, Ktor Netty, and transitive gRPC/Protobuf test dependencies in `ui-desktop/build.gradle.kts`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared in-process server rules, isolated workspace harness, CI container fail-fast guard, and minimal missing `testTag` hooks required across all user story suites.

**⚠️ CRITICAL**: Complete Phase 2 before starting User Stories 1–5.

- [ ] T003 [P] Implement `EmbeddedSampleServerRule` (JUnit `ExternalResource` binding ephemeral `ServerSocket(0)` port, starting Ktor Netty with `io.github.makeforge.clapi.server.module`, polling TCP readiness up to 5s, exposing `baseUrl`, `wsUrl`, `mcpHttpUrl`, `mcpLegacySseUrl`, and stopping cleanly in `after()`) in `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/e2e/support/EmbeddedSampleServerRule.kt`
- [ ] T004 [P] Implement `EmbeddedGrpcTestServerRule` (in-process Netty gRPC server on ephemeral port serving dynamic unary `helloworld.Greeter/SayHello` and error RPC with request-metadata echo and trailing metadata, writing temp `.proto` files and cleaning them up in `after()`) in `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/e2e/support/EmbeddedGrpcTestServerRule.kt`
- [ ] T005 [P] Implement `IsolatedWorkspaceRule` (creates temp `DefaultCanonicalCollectionRepository`, saves/restores `WorkspaceRepository.canonicalRepository`, clears `PlatformStorage` keys `clapi.tabs`, `clapi.workspace`, `clapi.settings` before/after, and calls `disposeAndAwait()` on registered `AppState` instances in teardown) in `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/e2e/support/IsolatedWorkspaceRule.kt`
- [ ] T006 [P] Implement `CiTestGuard` (`requireServiceOrSkip(serviceName, endpoint, isReachable)`: fails via `kotlin.test.fail(...)` when `!isReachable && System.getenv("CI").equals("true", ignoreCase = true)`, otherwise skips via `Assume.assumeTrue(...)` for local offline runs) in `qa-tests/src/test/kotlin/io/github/makeforge/clapi/testsupport/CiTestGuard.kt`
- [ ] T007 [P] Audit and add minimal missing kebab-case `testTag(...)` modifiers where needed for interactive selectors in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/RabbitMqRequestEditor.kt`, `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ResponseViewer.kt`, `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/dialogs/settings/ThemeSettingsPane.kt`, `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/TopToolbar.kt`, and `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ExportCollectionsDialog.kt`

**Checkpoint**: Shared test harnesses, CI fail-fast guard, and stable UI selectors are ready — user stories can now proceed.

---

## Phase 3: User Story 1 - Complete Automated Quality Gates & Packaged Release Smoke Verification (Priority: P1) 🎯 MVP

**Goal**: Ensure every PR, push to `main`, and release candidate executes all 10 test modules with zero ignored tests, fails hard on missing Docker services in CI, and verifies both fat JAR and jlink native distributables across storage, Javet V8, embedded MCP HTTP/stdio, and bundled legal files.

**Independent Test**: Run `McpClientHandshakeTest`, `:ui-desktop:verifyPackagedRuntime`, `:ui-desktop:verifyDistributableRuntime`, and verify CI workflow task lists cover all 10 modules + `verifyRoborazziDesktop` + Docker Compose lifecycle.

- [ ] T008 [P] [US1] Remove `@Ignore` and fix deterministic coroutine/transport teardown in all 6 tests of `core-network/src/commonTest/kotlin/io/github/makeforge/clapi/core/network/mcp/McpClientHandshakeTest.kt`
- [ ] T009 [P] [US1] Replace raw `Assume.assumeTrue` with `CiTestGuard.requireServiceOrSkip` in `qa-tests/src/test/kotlin/io/github/makeforge/clapi/qa/OAuth2IntegrationTest.kt`
- [ ] T010 [P] [US1] Replace raw `Assume.assumeTrue` with `CiTestGuard.requireServiceOrSkip` in `qa-tests/src/test/kotlin/io/github/makeforge/clapi/rabbitmq/RabbitMqEndToEndTest.kt`
- [ ] T011 [US1] Add `--packaged-smoke-test` CLI mode to `ui-desktop/src/desktopMain/kotlin/io/github/makeforge/clapi/ui/desktop/Main.kt` executing `STORAGE_ROUNDTRIP` (`SettingsRepository`, `WorkspaceRepository`, `TabsRepository` load/save in isolated `--dir`), `SCRIPT_ENGINE_V8` (`ClapiScriptEngine` evaluating `1 + 1 === 2`), and `APP_DISPOSE` (`AppState.disposeAndAwait()`), printing `CLAPI_SMOKE_OK checks=STORAGE_ROUNDTRIP,SCRIPT_ENGINE_V8,APP_DISPOSE` and exiting `0` (preserving `--shutdown-smoke-test`)
- [ ] T012 [US1] Strengthen `verifyPackagedRuntime` (assert `META-INF/LICENSE` and `META-INF/NOTICE` in fat JAR, execute `--shutdown-smoke-test`, `--packaged-smoke-test --dir <tmp>`, live `--mcp-http-headless --mcp-port 0 --dir <tmp>` HTTP JSON-RPC `initialize` 200 check + stdin EOF exit 0, and live `--mcp --dir <tmp>` stdio JSON-RPC `initialize` check) and add `verifyDistributableRuntime` (depends on `createDistributable`, verifies bundled `LICENSE`/`NOTICE` and executes platform native launcher with `--packaged-smoke-test` and `--mcp-http-headless`) in `ui-desktop/build.gradle.kts`
- [ ] T013 [P] [US1] Update `.github/workflows/quality-check.yml` to run `:qa-tests:dockerComposeUp`, all 10 modules (`:core-model:allTests`, `:core-network:desktopTest`, `:core-scripting:desktopTest`, `:core-storage:allTests`, `:editor-core:desktopTest`, `:editor-ui:allTests`, `:mcp-server:allTests`, `:ui-shared:desktopTest`, `:ui-desktop:desktopTest`, `:qa-tests:jvmTest`) + `:ui-desktop:verifyRoborazziDesktop` under `xvfb-run -a --server-args="-screen 0 1920x1080x24"`, and `:qa-tests:dockerComposeDown` with `if: always()`
- [ ] T014 [P] [US1] Update `.github/workflows/release.yml` to run all 10 modules + `:ui-desktop:verifyRoborazziDesktop` with `:qa-tests:dockerComposeUp` before `:ui-desktop:desktopTest` in the `quality` job, and add `:ui-desktop:createDistributable` + `:ui-desktop:verifyDistributableRuntime` to all 3 OS matrix runners (`macos`, `linux`, `windows`) in the `build` job
- [ ] T015 [US1] Validate US1 locally via `rtk gradlew :core-network:desktopTest --tests "*McpClientHandshakeTest*" :ui-desktop:verifyPackagedRuntime :ui-desktop:verifyDistributableRuntime` and fix any issues uncovered

**Checkpoint**: User Story 1 (MVP) is complete — CI quality gates and packaged runtime/distributable smoke checks are hardened and verified.

---

## Phase 4: User Story 2 - End-to-End Interactive UI Verification of Core HTTP, Auth, Scripting & Cancellation Flows (Priority: P1)

**Goal**: Verify live end-to-end HTTP request execution, authentication (including OAuth2 and inheritance), pre/post scripting, history, bottom-panel logs, in-flight cancellation, errors, and retries through `MainScreen` against embedded `sample-server`.

**Independent Test**: Run `rtk gradlew :ui-desktop:desktopTest --tests "*.HttpRequestUiE2ETest" --tests "*.ScriptingAndVariablesUiE2ETest"` and confirm every interactive UI journey passes deterministically offline.

- [ ] T016 [US2] Create `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/e2e/HttpRequestUiE2ETest.kt` using `EmbeddedSampleServerRule` + `IsolatedWorkspaceRule` covering HTTP CRUD (`GET`, `POST`, `PUT`, `DELETE`) via `MainScreen` method dropdown, `url-input` with `{{baseUrl}}`, query params, headers, all body formats (JSON, JSON5 transmitted as strict JSON, `FORM_DATA`, `URL_ENCODED`, `XML`, `RAW`), clicking `send-button`, verifying `ResponseViewer` tabs (Body, Headers, Cookies, Timing, Raw), History sidebar entry creation & reopening, and Bottom Panel Console log capture
- [ ] T017 [US2] Add Auth UI E2E tests (Basic, Bearer, API Key valid `200` & invalid `401`; live OAuth2 `auth-oauth2-fetch-tokens-button` against `/oauth/token` + `{{_oauth2.accessToken}}` call; Collection & Folder inherited auth/headers/variables verified on the wire) to `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/e2e/HttpRequestUiE2ETest.kt`
- [ ] T018 [US2] Add in-flight cancellation (`/api/slow?ms=5000` -> assert `stop-button` displayed -> click `stop-button` -> verify clean recovery and subsequent Send), error statuses (`/api/error/404`, `/api/error/500`, unreachable port, invalid URL), and retry popup config (`retryEnabled = true`, attempts = 2) UI E2E tests to `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/e2e/HttpRequestUiE2ETest.kt`
- [ ] T019 [P] [US2] Create `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/e2e/ScriptingAndVariablesUiE2ETest.kt` covering pre-request script mutations (`clapi.request.setHeader`, `clapi.environment.set`), post-request passing + failing `clapi.test(...)` rendered in `test-results`, `clapi.console.log(...)` rendered in `console-output`, live environment variable updates, and script syntax/runtime exception UI recovery (Send button restored)
- [ ] T020 [US2] Run `rtk gradlew :ui-desktop:desktopTest --tests "*.HttpRequestUiE2ETest" --tests "*.ScriptingAndVariablesUiE2ETest"` and fix any production UI or execution pipeline defects uncovered

**Checkpoint**: User Story 2 is complete — core HTTP, Auth/OAuth2/Inheritance, Scripting, History, Cancellation, and Error/Retry UI E2E journeys pass.

---

## Phase 5: User Story 3 - End-to-End Interactive UI Verification of Multi-Protocol Workspaces (Priority: P1)

**Goal**: Provide interactive UI E2E verification for every non-HTTP protocol workspace: SSE/LLM streaming, `RealtimePanel`, GraphQL, WebSocket, gRPC, MCP Client, and RabbitMQ.

**Independent Test**: Run `rtk gradlew :ui-desktop:desktopTest --tests "*.StreamingAndRealtimeUiE2ETest" --tests "*.McpAndGrpcUiE2ETest" --tests "*.RabbitMqUiE2ETest"` and confirm all 6 protocol workspaces execute and tear down cleanly.

- [ ] T021 [P] [US3] Create `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/e2e/StreamingAndRealtimeUiE2ETest.kt` covering finite SSE (`GET /sse?count=3&delayMs=20`, `POST /sse`, SSE badge, `response-live-stream` incremental arrival), LLM streaming (`POST /v1/chat/completions` `"stream": true`, assembled preview, TTFT/metrics, manual Stop mid-stream, `?earlyClose=true` partial text retention), and `RealtimePanel` (`WorkspaceMode.REALTIME`) interactive UI smoke check
- [ ] T022 [US3] Add GraphQL UI E2E (`POST` and `GET` with query + JSON variables + body variant switching against `/api/graphql` via `MainScreen` -> `graphql-send-button`) and WebSocket UI E2E (connect `ws://127.0.0.1:<port>/ws` via `websocket-connect-button`, send text/JSON, assert `↑ SENT` and echoed `↓ RECEIVED` in `websocket-timeline`, disconnect, and closing active WS tab cleanly) to `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/e2e/StreamingAndRealtimeUiE2ETest.kt`
- [ ] T023 [P] [US3] Create `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/e2e/McpAndGrpcUiE2ETest.kt` covering `McpPanel` in `MainScreen` over Streamable HTTP (`/mcp`) and Legacy HTTP+SSE (`/mcp/sse`): `mcp-url` + `mcp-connect` -> `mcp-status-CONNECTED`, `echo` tool form & raw JSON invocation, Resources list/read/subscribe, Prompts inspection, Activity Log (`mcp-activity-payload`), interactive `trigger_sampling` & `trigger_elicitation` via `McpCallbackPane`, `mcp-disconnect`, and active-tab close cleanup
- [ ] T024 [US3] Add `GrpcRequestEditor` UI E2E using `EmbeddedGrpcTestServerRule` (direct and collection-inherited `.proto` selection, `grpc-method-dropdown` discovery of `helloworld.Greeter/SayHello`, JSON payload & metadata, clicking `send-button`, verifying `OK` & error status, response JSON, headers, and trailers) to `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/e2e/McpAndGrpcUiE2ETest.kt`
- [ ] T025 [P] [US3] Create `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/e2e/RabbitMqUiE2ETest.kt` covering broker-independent `RabbitMqRequestEditor` flows (`PUBLISH`/`SUBSCRIBE`/`RPC` mode switching, hiding `MESSAGE`/`HEADERS` and fallback to `AMQP` in `SUBSCRIBE`, AMQP/headers/payload editing with dirty/save persistence, unreachable localhost port error banner) plus live Publish/Subscribe/Ack/Nack/RPC UI E2E tests guarded by broker reachability (failing hard when `CI=true` and RabbitMQ compose service is expected)
- [ ] T026 [US3] Run `rtk gradlew :ui-desktop:desktopTest --tests "*.StreamingAndRealtimeUiE2ETest" --tests "*.McpAndGrpcUiE2ETest" --tests "*.RabbitMqUiE2ETest"` and fix any protocol UI or lifecycle defects uncovered

**Checkpoint**: User Story 3 is complete — all 7 supported protocols in Clapi have interactive UI E2E verification.

---

## Phase 6: User Story 4 - Interactive UI Verification of Dialogs, Settings, Shortcuts & Workspace Workflows (Priority: P2)

**Goal**: Verify every previously untested dialog, settings pane, toolbar switcher, global keyboard shortcut, multi-tab dirty-close dialog, real sidebar Undo snackbar flow, bottom panel, and detached response viewer lifecycle.

**Independent Test**: Run `rtk gradlew :ui-desktop:desktopTest --tests "*.DialogsSettingsAndShortcutsUiTest"` and verify all dialogs, settings, shortcuts, and workspace flows pass.

- [ ] T027 [US4] Create `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/e2e/DialogsSettingsAndShortcutsUiTest.kt` covering `ExportCollectionsDialog` (open from sidebar export menu, Select All & individual checkboxes, JSON vs YAML radio switch, temp `onPickDirectory` export file verification & dismiss) and `McpServerSettingsSection` (toggle `mcp-server-enable-toggle` ON/OFF, live `mcp-server-status-card`, port change restart, Copy Config Snippet)
- [ ] T028 [US4] Add `ThemeSettingsPane` + toolbar theme toggle persistence tests (Light/Dark/System persisted via `SettingsRepository`) and TopToolbar `env-chip` environment switcher tests (switching Base vs inherited Child environment immediately updates `{{var}}` resolution on Send) to `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/e2e/DialogsSettingsAndShortcutsUiTest.kt`
- [ ] T029 [US4] Add `MainScreen` global keyboard shortcut tests (`Ctrl+Enter` Send, `Ctrl+S` Save, `Ctrl+N` New Tab, `Ctrl+W` Close clean & dirty tab triggering `dirty-close-dialog`, `Ctrl+,` Settings, `Ctrl+Shift+[` / `]` Move Tab Left/Right) and `DirtyMultiCloseDialog` tests ("Close Others" / "Close All" with 2+ dirty tabs testing `dirty-multi-cancel`, `dirty-multi-discard`, `dirty-multi-save-all`) to `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/e2e/DialogsSettingsAndShortcutsUiTest.kt`
- [ ] T030 [US4] Add real Sidebar Undo Snackbar E2E tests (UI delete of request, folder with nested children, collection, environment, history item; rename; move -> verify `action-snackbar` -> click `action-snackbar-undo` -> verify full sidebar UI, open-tab, and persisted restoration), `BottomPanel` interactions (Console/Test Results/Logs switch, clear, diagnostics rendering, collapse/expand), and Detached Response Viewer lifecycle tests (detach -> Send -> switch tabs -> close last tab empty state -> reattach) to `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/e2e/DialogsSettingsAndShortcutsUiTest.kt`
- [ ] T031 [US4] Run `rtk gradlew :ui-desktop:desktopTest --tests "*.DialogsSettingsAndShortcutsUiTest"` and fix any dialog, shortcut, or undo workflow defects uncovered

**Checkpoint**: User Story 4 is complete — all major dialogs, settings panes, shortcuts, and desktop workspace workflows have automated UI coverage.

---

## Phase 7: User Story 5 - Resilience, Security Redaction, Layout & Visual Regression Verification (Priority: P2)

**Goal**: Guarantee secret redaction across every user-facing surface, crash-free storage/import/variable/binary/layout edge-case resilience, `AotTrainingRunner` stability, and committed Roborazzi visual baselines.

**Independent Test**: Run `rtk gradlew :ui-desktop:desktopTest --tests "*.ScriptingAndVariablesUiE2ETest" --tests "*.ResilienceLayoutAndRoundTripUiTest"` and `rtk gradlew :ui-desktop:verifyRoborazziDesktop`.

- [ ] T032 [P] [US5] Harden `SecretLogRedactor.collectSecretValues` (collect `secret = true` variables from active env, inherited parent environments, `state.globalVariables`, and collection/folder nodes with `length >= 3`) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/executor/SecretLogRedactor.kt` and audit/harden secret redaction across `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/executor/RequestExecutionPipeline.kt`, `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/executor/RequestCommandExporter.kt`, and `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/CrashReportDialog.kt`
- [ ] T033 [US5] Add secret redaction tests across all UI surfaces (Console logs, Diagnostics `clapi.log`, persisted History items, `CrashReportDialog` copied details & GitHub bug URL, and command exports for active env, inherited env, global secret, and external secret), unquoted typed dynamic variables under JSON5 ON/OFF (plus invalid JSON5 rejection when OFF), and circular (`a={{b}}`, `b={{a}}`) / unresolved (`{{missing}}`, `{{unclosed`) variable termination tests to `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/e2e/ScriptingAndVariablesUiE2ETest.kt`
- [ ] T034 [P] [US5] Create `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/e2e/ResilienceLayoutAndRoundTripUiTest.kt` covering corrupt/truncated storage recovery (`clapi.tabs`, `clapi.settings`, corrupted collection YAML/JSON alongside valid collection), orphaned restored tab handling, window-close shutdown persistence & active WS/MCP session teardown (`TabsRepository.save`, `WorkspaceRepository.save`, `disposeAndAwait()`), and `AotTrainingRunner.runTrainingPass(state)` smoke execution inside `MainScreen`
- [ ] T035 [US5] Add binary non-UTF8 response rendering/download safety, minimum viewport `800.dp x 600.dp` `MainScreen` layout smoke test (`url-input`, `send-button`, `request-tabs-bar`, `sidebar`, `response-viewer` displayed), 20-tab overflow scrolling/reordering/mid-flight close, 5-level nested folder search/delete-with-open-tab/cross-collection move, malformed OpenAPI/Postman/Insomnia/OpenCollection import rejection without workspace mutation, and multi-protocol (HTTP + GraphQL + gRPC + WS + MCP + RabbitMQ + Env) Export -> Delete -> Re-Import UI round-trip tests to `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/e2e/ResilienceLayoutAndRoundTripUiTest.kt`
- [ ] T036 [US5] Configure Roborazzi baseline output directory (`ui-desktop/src/desktopTest/snapshots/roborazzi/`) and comparison threshold in `ui-desktop/build.gradle.kts`, then generate and commit golden baseline PNGs via `rtk gradlew :ui-desktop:recordRoborazziDesktop`
- [ ] T037 [US5] Verify `rtk gradlew :ui-desktop:verifyRoborazziDesktop` passes cleanly against committed baselines in `ui-desktop/src/desktopTest/snapshots/roborazzi/`
- [ ] T038 [US5] Run `rtk gradlew :ui-desktop:desktopTest --tests "*.ScriptingAndVariablesUiE2ETest" --tests "*.ResilienceLayoutAndRoundTripUiTest"` and fix any resilience, redaction, or layout defects uncovered

**Checkpoint**: User Story 5 is complete — secret redaction, resilience boundaries, minimum viewport, multi-protocol round-trips, and Roborazzi visual baselines all pass.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Final cleanup, full 10-module + packaged smoke + visual baseline verification, and mandatory changelog update.

*(Note: `docs/` tasks intentionally omitted — `plan.md` confirmed `Documentation Impact (docs/): None`.)*

- [ ] T039 Review all new test files under `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/e2e/` and `qa-tests/src/test/` for zero flakiness, clean resource disposal, and deterministic timeouts
- [ ] T040 Run full `specs/034-release-e2e-test-suite/quickstart.md` verification across all 10 Gradle test modules, `:ui-desktop:verifyPackagedRuntime`, `:ui-desktop:verifyDistributableRuntime`, and `:ui-desktop:verifyRoborazziDesktop`
- [ ] T041 Update `CHANGELOG.md` under `## [1.0.0] - TBD` (`### Fixes` or `### New Features`) summarizing the release E2E UI test suite, CI quality-gate expansion, packaged distributable smoke tests, and secret-redaction hardening, and verify `appVersion=1.0.0` in `gradle.properties` matches per `AGENTS.md`

---

## Dependencies & Execution Order

### Phase Dependencies
- **Phase 1 (Setup)** -> **Phase 2 (Foundational)** -> **Phases 3–7 (User Stories 1–5)** -> **Phase 8 (Polish)**
- **Phase 2 (Foundational)** blocks all user stories (`T003`–`T007` can run in parallel).
- **User Story 1 (P1 — MVP)** can execute immediately after Phase 2; completing it first protects every subsequent story in CI.
- **User Stories 2, 3, 4, and 5** are mutually independent at the test-suite level and can execute sequentially by priority (`US1 -> US2 -> US3 -> US4 -> US5`) or in parallel across separate files once Phase 2 is done.
  - Note: `T033` (US5) appends to `ScriptingAndVariablesUiE2ETest.kt` created in `T019` (US2); if parallelizing US2 and US5, finish `T019` before `T033`.
- **Phase 8 (Polish)** depends on all desired user stories being complete.

### Parallel Opportunities per Story
- **Foundational**: `T003`, `T004`, `T005`, `T006`, `T007` all touch distinct files and run in parallel.
- **US1**: `T008`, `T009`, `T010` run in parallel; `T013` and `T014` run in parallel after `T012`.
- **US2**: `T016`–`T018` (`HttpRequestUiE2ETest.kt`) can run in parallel with `T019` (`ScriptingAndVariablesUiE2ETest.kt`).
- **US3**: `T021`–`T022` (`StreamingAndRealtimeUiE2ETest.kt`), `T023`–`T024` (`McpAndGrpcUiE2ETest.kt`), and `T025` (`RabbitMqUiE2ETest.kt`) touch separate files and run in parallel.
- **US5**: `T032` (`SecretLogRedactor` & audit), `T034` (`ResilienceLayoutAndRoundTripUiTest.kt`), and `T036` (Roborazzi baselines) can run in parallel.

---

## Parallel Example: User Story 3

```text
# Implement all three protocol UI E2E suites simultaneously (distinct files):
Task T021/T022: ui-desktop/src/desktopTest/.../e2e/StreamingAndRealtimeUiE2ETest.kt
Task T023/T024: ui-desktop/src/desktopTest/.../e2e/McpAndGrpcUiE2ETest.kt
Task T025:      ui-desktop/src/desktopTest/.../e2e/RabbitMqUiE2ETest.kt
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)
1. Complete Phase 1 (Setup) + Phase 2 (Foundational).
2. Complete Phase 3 (User Story 1): all 10 modules run in CI, `@Ignore` removed, Docker failures fail CI, fat JAR & native distributable smoke tests pass.
3. **STOP & VALIDATE** via `T015` before layering UI E2E suites.

### Incremental Delivery
1. **Increment 1 (US1 — MVP)**: Hardened CI gates + fat JAR & native distributable smoke tests.
2. **Increment 2 (US2)**: Full interactive HTTP + Auth/OAuth2/Inheritance + Scripting + Cancel/Error/Retry UI E2E.
3. **Increment 3 (US3)**: Full interactive SSE/LLM + GraphQL + WebSocket + gRPC + MCP + RabbitMQ UI E2E.
4. **Increment 4 (US4)**: Missing dialogs (`ExportCollectionsDialog`, `McpServerSettingsSection`, `DirtyMultiCloseDialog`), Theme/Env switchers, hotkeys, real Sidebar Undo snackbar, `BottomPanel`, detached viewer.
5. **Increment 5 (US5)**: Secret redaction hardening across all UI surfaces, storage/import/layout/round-trip resilience, and Roborazzi visual baselines.
6. **Final Polish**: Quickstart validation across all 10 modules + `CHANGELOG.md` update.
