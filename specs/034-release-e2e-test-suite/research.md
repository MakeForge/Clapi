# Research & Technical Decisions: Release-Ready E2E & UI Verification Suite

**Feature**: `034-release-e2e-test-suite`  
**Date**: 2026-11-22

---

## 1. CI Quality-Gate Module Coverage & Container Fail-Fast Policy

- **Decision**: Update `.github/workflows/quality-check.yml` and `.github/workflows/release.yml` so both workflows execute all 10 tested modules (`:core-model:allTests`, `:core-network:desktopTest`, `:core-scripting:desktopTest`, `:core-storage:allTests`, `:editor-core:desktopTest`, `:editor-ui:allTests`, `:mcp-server:allTests`, `:ui-shared:desktopTest`, `:ui-desktop:desktopTest`, and `:qa-tests:jvmTest`) under `xvfb-run`. Add a shared CI guard helper in `qa-tests` so `OAuth2IntegrationTest` and `RabbitMqEndToEndTest` throw an assertion failure instead of calling `Assume.assumeTrue(...)` when `System.getenv("CI") == "true"`.
- **Rationale**:
  - Currently `quality-check.yml` omits `:ui-desktop:desktopTest` (all 1,190 Compose UI tests) plus `:core-model`, `:core-network`, `:core-storage`, and `:mcp-server`.
  - `release.yml` omits `:core-model`, `:core-storage`, `:core-scripting`, and `:mcp-server`.
  - `OAuth2IntegrationTest` and `RabbitMqEndToEndTest` use `Assume.assumeTrue`, which causes JUnit to mark tests as *skipped* (green build) if Docker containers fail to boot in CI.
  - Keeping `Assume.assumeTrue` for local developer runs (`CI != "true"`) preserves fast offline ergonomics while making CI strict.
- **Alternatives Considered**:
  - Replacing explicit module task lists with `./gradlew check`: simpler, but `:qa-tests:jvmTest` uses Docker Compose lifecycle hooks (`dockerComposeUp` / `dockerComposeDown`) and `release.yml` needs explicit ordering and `--no-parallel` / `--info` diagnostics when containers fail. Keeping explicit tasks covering all 10 modules (or `./gradlew check --exclude-task ...` plus explicit `:qa-tests:jvmTest`) gives deterministic execution and full coverage.

---

## 2. Re-Enabling `McpClientHandshakeTest`

- **Decision**: Remove `@Ignore` from `core-network/src/commonTest/kotlin/io/github/makeforge/clapi/core/network/mcp/McpClientHandshakeTest.kt`, diagnose any coroutine scheduler or transport teardown race (notably `runTest` virtual-time vs. `Dispatchers.Unconfined` / `McpClient` background receive loops), and make all 6 tests pass deterministically.
- **Rationale**: `McpClientHandshakeTest` is the only `@Ignore`d test class in the repository. It covers critical MCP client handshake, capability advertisement, sampling/elicitation routing, roots listing, and legacy SSE response-during-send race conditions.
- **Alternatives Considered**:
  - Deleting the test class because `McpHttpE2ETest` exists: rejected because `McpClientHandshakeTest` tests deterministic unit-level protocol races (`ReplyDuringSendTransport`) and stdio framing without spinning up Netty.

---

## 3. Expanded Packaged Runtime & Native Distributable Smoke Verification

- **Decision**:
  1. Extend `ui-desktop/src/desktopMain/kotlin/io/github/makeforge/clapi/ui/desktop/Main.kt` with a comprehensive `--packaged-smoke-test` mode (keeping `--shutdown-smoke-test` backward-compatible) that executes inside an isolated `--dir <temp>` directory:
     - `SettingsRepository.load`/`save`, `WorkspaceRepository.load`/`save`, `TabsRepository.load`/`save` round-trip.
     - `ClapiScriptEngine` (Javet V8 native library load + `1 + 1 === 2` evaluation + `clapi.test(...)` assertion).
     - `AotTrainingRunner.runTrainingPass(state)` completion without unhandled exceptions (exercising Compose-free/state paths or verifying via a dedicated `desktopTest` inside Compose).
     - `AppState.disposeAndAwait()` clean shutdown.
  2. Expand `:ui-desktop:verifyPackagedRuntime` (and add a companion `:ui-desktop:verifyDistributableRuntime` task) to:
     - Verify `META-INF/LICENSE` and `META-INF/NOTICE` exist inside the fat JAR and `LICENSE`/`NOTICE` exist inside the unpacked native distributable resources.
     - Execute `--packaged-smoke-test --dir <temp>`.
     - Execute `--mcp-http-headless --mcp-port 0 --dir <temp>`, parse `CLAPI_MCP_READY url=...`, send a live JSON-RPC `initialize` POST to the endpoint, assert HTTP 200 + `protocolVersion`, and close stdin to assert exit code `0`.
     - Execute `--mcp --dir <temp>` over stdin/stdout, send a JSON-RPC `initialize` line, assert valid JSON-RPC response on stdout, and close stdin to assert exit code `0`.
  3. Wire `:ui-desktop:createDistributable` + `:ui-desktop:verifyDistributableRuntime` into all 3 OS runners (`macos-latest`, `ubuntu-latest`, `windows-latest`) in `.github/workflows/release.yml`.
- **Rationale**: The fat JAR runs on the full JDK 25, whereas `.deb`, `.dmg`, and `.exe` run on a **jlink-trimmed JDK 25 runtime** (`modules(...)` in `ui-desktop/build.gradle.kts:252-268`). Running the expanded smoke suite against the unpacked native distributable catches missing JPMS modules (TLS/EC crypto, XML, prefs, SQL, management) and missing native libs (Javet V8, Skiko, Tao) before release.
- **Alternatives Considered**:
  - Launching full GUI windows in headless CI for native installers: flaky across Windows/macOS runners without display servers; headless CLI smoke modes (`--packaged-smoke-test`, `--mcp-http-headless`, `--mcp`) exercise the exact packaged classpath, jlink runtime, native V8 libs, persistence, and embedded Ktor server deterministically on all 3 platforms.

---

## 4. In-Process Server Harness for Compose UI E2E Tests

- **Decision**: Create a reusable test harness package `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/e2e/support/` containing:
  - `EmbeddedSampleServerRule` (JUnit `ExternalResource` wrapping `io.ktor.server.engine.embeddedServer(Netty, port = 0)` with `io.github.makeforge.clapi.server.module`, reusing the existing `:sample-server` `desktopTest` dependency in `ui-desktop/build.gradle.kts`).
  - `EmbeddedGrpcTestServerRule` (lightweight in-process Netty gRPC server implementing dynamic unary `helloworld.Greeter/SayHello` + error status + metadata echo + temp `.proto` files, using `grpc-netty-shaded` and `protobuf-java` already transitively present via `core-network`).
  - `IsolatedWorkspaceRule` (sets temp `DefaultCanonicalCollectionRepository`, clears/restores `PlatformStorage` keys, resets `SettingsRepository`, and guarantees clean teardown so tests never leak prefs or state across classes).
- **Rationale**:
  - `ui-desktop` already depends on `project(":sample-server")` and `ktor-server-netty` in `desktopTest`.
  - Ephemeral port allocation (`ServerSocket(0)` / Netty port 0) avoids port conflicts (unlike legacy hardcoded port `18081` in `SettingsImpactIntegrationTest`).
  - Sharing one embedded server per test class (`@ClassRule`) keeps the entire new UI E2E suite fast (<30–45s total).
- **Alternatives Considered**:
  - Adding Compose UI tests to `qa-tests`: rejected because `qa-tests` does not depend on Compose Desktop test artifacts or `ui-desktop` launcher code; keeping UI E2E tests in `ui-desktop/src/desktopTest` keeps all Compose UI testing consolidated and runnable via `:ui-desktop:desktopTest`.

---

## 5. Streaming UI (SSE / LLM) Architecture Clarification

- **Decision**: Cover **both** live streaming surfaces:
  1. **HTTP SSE & LLM Streaming inside `ResponseViewer`** (`testTag("response-live-stream")`, `response.streamEvents`, SSE sidebar badge, live assembled text preview, TTFT/metrics, Stop button cancellation, `?earlyClose=true` partial text retention) driven via `MainScreen` -> `send-button` against `sample-server` `/sse` and `/v1/chat/completions` (`"stream": true`).
  2. **`RealtimePanel` (`WorkspaceMode.REALTIME`)** interactive UI smoke coverage (switching mode, rendering connection/stream controls without crashes).
- **Rationale**: Code inspection confirmed HTTP SSE & LLM streams render inside `ResponseViewer`, while `RealtimePanel` is mounted under `WorkspaceMode.REALTIME`. Testing both ensures zero uncovered streaming UI paths.

---

## 6. Secret Redaction Audit Across All UI Surfaces

- **Decision**: During implementation of FR-014, audit and verify (and fix if gaps exist):
  - `SecretLogRedactor.collectSecretValues` (currently only inspects `state.selectedEnvironment?.variables`, missing inherited parent environment secrets, `state.globalVariables` with `secret = true`, and collection/folder secret variables).
  - `BottomPanel` Console & Diagnostics logs (`UiDiagnosticsLogWriter`).
  - `HistoryItem` recording in `RequestExecutionPipeline` (must not persist plaintext secret values in resolved URLs/headers).
  - `RequestCommandExporter` (`buildCurlCommand`, `buildPythonCommand`, `buildHTTPieCommand`, `buildPowerShellCommand`) — ensure secret variables use placeholders or redacted masks rather than leaking plaintext secrets unintentionally, or verify explicit redacted vs. raw behavior per product contract.
  - `CrashReportDialog` / `GlobalCrashHandler` bug report URL & copied details redaction.
- **Rationale**: Inspecting `SecretLogRedactor.kt` during planning already revealed a concrete gap (parent-inherited and global secrets are not collected by `collectSecretValues`). Fixing this alongside comprehensive UI tests directly closes a real 1.0.0 security risk.

---

## 7. RabbitMQ UI Tests (Broker-Independent vs. Live Broker)

- **Decision**:
  - Put broker-independent `RabbitMqRequestEditor` UI tests (mode switching `PUBLISH`/`SUBSCRIBE`/`RPC`, tab hiding & fallback to `AMQP` in `SUBSCRIBE` mode, AMQP/headers/payload editing, dirty/save round-trip, unreachable-broker error banner) in `ui-desktop/src/desktopTest` so they run unconditionally everywhere.
  - Add live broker UI E2E tests (Publish, Subscribe with live timeline + Ack/Nack, RPC reply pane) guarded by `RABBITMQ_HOST`/`RABBITMQ_PORT` reachability in `ui-desktop/src/desktopTest` (and/or run with the RabbitMQ Docker container active in CI, failing hard when `CI=true` and `CLAPI_REQUIRE_RABBITMQ_UI=true` or when Docker Compose is up before `:ui-desktop:desktopTest`).
- **Rationale**: In `quality-check.yml` and `release.yml`, `:qa-tests:dockerComposeUp` spins up RabbitMQ/Keycloak/Vault. If CI starts Docker Compose before `:ui-desktop:desktopTest` and tears it down in `if: always()`, live RabbitMQ UI E2E tests also execute deterministically in CI while local `:ui-desktop:desktopTest` runs remain fast without requiring Docker.

---

## 8. Roborazzi Visual Baseline Strategy

- **Decision**:
  - Use the already-configured Roborazzi Compose Desktop Preview Scanner in `ui-desktop/build.gradle.kts` (`packages = listOf("io.github.makeforge.clapi.ui.desktop.preview")`).
  - Configure a stable, version-controlled baseline directory (`ui-desktop/src/desktopTest/snapshots/roborazzi/`) with a small tolerance (`compareOptions` / `ChangeThreshold` ~`0.01f` for minor subpixel font antialiasing across Linux environments) and record golden baselines for primary `MainScreen` and core dialog previews.
  - Run `:ui-desktop:verifyRoborazziDesktop` under `xvfb-run` on `ubuntu-latest` in CI (consistent font stack & 1920×1080×24 framebuffer eliminates macOS/Windows font drift).
- **Rationale**: `ui-desktop/build.gradle.kts` already sets up Roborazzi and `copyScreenshots`, but currently has 0 committed baselines and no CI verification step. Pinning visual verification to `ubuntu-latest` under `xvfb-run` avoids cross-OS font flakiness while catching real layout/theme regressions.

---

## 9. Test Tag Additions (Minimal Production UI Touchpoints)

- **Decision**: Most target UI components (`McpPanel`, `GrpcRequestEditor`, `WebSocketRequestEditor`, `GraphQLRequestEditor`, `ExportCollectionsDialog`, `McpServerSettingsSection`, `BottomPanel`, `TopToolbar`, `DirtyMultiCloseDialog`) already have rich `testTag(...)` modifiers. Only add missing `testTag(...)` modifiers where a tested interactive control lacks one (e.g., `RabbitMqRequestEditor` controls, `ThemeSettingsPane` theme options, `ResponseViewer` tab chips / download button, `TopToolbar` env dropdown items), following existing kebab-case naming conventions without altering visual appearance or behavior.
- **Rationale**: Stable `testTag` selectors prevent brittle text-matching tests (especially with i18n) and keep UI E2E tests maintainable.
