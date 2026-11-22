# Quickstart Validation Guide: Release-Ready E2E & UI Verification Suite

**Feature**: `034-release-e2e-test-suite`  
**Date**: 2026-11-22

Use this guide to validate all user stories end-to-end once implementation is complete. All Gradle commands follow the project `rtk gradlew ...` convention.

---

## Prerequisites

- JDK 25 installed (Temurin 25 recommended)
- Docker & Docker Compose available (for full `qa-tests` + live RabbitMQ verification)
- On headless Linux, prefix desktop UI commands with `xvfb-run -a --server-args="-screen 0 1920x1080x24"`

---

## 1. Validate All 10 Subsystems & Re-Enabled Handshake Tests (`US1` / `SC-001`)

Run the full quality-gate suite across every module:

```bash
rtk gradlew \
  :core-model:allTests \
  :core-network:desktopTest \
  :core-scripting:desktopTest \
  :core-storage:allTests \
  :editor-core:desktopTest \
  :editor-ui:allTests \
  :mcp-server:allTests \
  :ui-shared:desktopTest \
  :ui-desktop:desktopTest \
  :qa-tests:jvmTest
```

**Expected outcomes**:
- All 10 modules execute and pass with `0` failures and `0` `@Ignore`d tests (confirm `McpClientHandshakeTest` reports 6 passed tests).
- Running `:qa-tests:jvmTest` with `CI=true` and stopped Docker containers fails explicitly rather than silently skipping `OAuth2IntegrationTest` or `RabbitMqEndToEndTest`.

---

## 2. Validate Interactive UI E2E Suites (`US2`, `US3`, `US4`, `US5` / `SC-002`, `SC-003`, `SC-005`, `SC-006`)

Run only the new interactive UI E2E suites directly for fast feedback:

```bash
rtk gradlew :ui-desktop:desktopTest --tests "io.github.makeforge.clapi.ui.desktop.e2e.*"
```

**Expected outcomes**:
- `HttpRequestUiE2ETest` passes (HTTP CRUD, bodies, JSON5, auth, OAuth2, inheritance, History, Cancel, errors, retries).
- `ScriptingAndVariablesUiE2ETest` passes (pre/post scripts, Test Results, Console, typed dynamic vars, circular vars, secret redaction across all UI surfaces).
- `StreamingAndRealtimeUiE2ETest` passes (SSE, LLM streaming, `RealtimePanel`, GraphQL POST/GET/variants, WebSocket connect/echo/disconnect/tab-close).
- `McpAndGrpcUiE2ETest` passes (`McpPanel` Streamable HTTP & Legacy SSE, tools/resources/prompts/log/callbacks, `GrpcRequestEditor` unary execution).
- `RabbitMqUiE2ETest` passes (mode switching, tab hiding, persistence, unreachable broker error; plus live Publish/Subscribe/RPC when broker is running).
- `DialogsSettingsAndShortcutsUiTest` passes (`ExportCollectionsDialog`, `McpServerSettingsSection`, Theme/Env pickers, global hotkeys, `DirtyMultiCloseDialog`, real Sidebar Undo snackbar, `BottomPanel`, detached response viewer).
- `ResilienceLayoutAndRoundTripUiTest` passes (corrupt storage, orphaned tabs, shutdown teardown, binary response, 800×600 layout, 20-tab overflow, 5-level folders, malformed imports, multi-protocol round-trip, `AotTrainingRunner`).

---

## 3. Validate Packaged Fat JAR & Native Distributable Smoke Tests (`US1` / `SC-004`)

```bash
rtk gradlew :ui-desktop:verifyPackagedRuntime :ui-desktop:verifyDistributableRuntime
```

**Expected outcomes**:
- Both fat JAR and unpacked jlink native distributable pass:
  - Legal file presence (`LICENSE`, `NOTICE`).
  - `--packaged-smoke-test --dir <tmp>` (`CLAPI_SMOKE_OK` with storage round-trip, Javet V8 eval, clean dispose).
  - `--mcp-http-headless --mcp-port 0 --dir <tmp>` (live JSON-RPC `initialize` HTTP 200 + clean stdin EOF exit).
  - `--mcp --dir <tmp>` (live JSON-RPC `initialize` stdio response + clean exit).

---

## 4. Validate Visual Regression Baselines (`US5` / `SC-007`)

```bash
# Record/update baselines when intentionally changing UI:
rtk gradlew :ui-desktop:recordRoborazziDesktop

# Verify baselines (run under xvfb-run on headless Linux):
rtk gradlew :ui-desktop:verifyRoborazziDesktop
```

**Expected outcomes**:
- Committed golden PNG baselines exist under `ui-desktop/src/desktopTest/snapshots/roborazzi/`.
- `:ui-desktop:verifyRoborazziDesktop` passes with `0` unexpected visual diffs.
