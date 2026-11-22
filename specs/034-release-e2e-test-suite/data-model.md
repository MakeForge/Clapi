# Data Model & Verification Architecture: Release-Ready E2E & UI Verification Suite

**Feature**: `034-release-e2e-test-suite`  
**Date**: 2026-11-22

This feature does not introduce new persisted user-domain entities in `core-model`; instead, it defines the **test harness models, quality-gate matrix, packaged smoke verification payload, and visual baseline artifacts** that validate the entire Clapi 1.0.0 workspace.

---

## 1. Test Harness Entities (`ui-desktop/src/desktopTest/.../e2e/support/`)

### 1.1 `EmbeddedSampleServerRule`
- **Purpose**: Manages an isolated in-process Ktor Netty instance running `sample-server`'s `Application.module` on an ephemeral localhost port.
- **Fields**:
  - `port: Int` — dynamically bound free port (`ServerSocket(0)`).
  - `baseUrl: String` — `"http://127.0.0.1:$port"`.
  - `wsUrl: String` — `"ws://127.0.0.1:$port/ws"`.
  - `mcpHttpUrl: String` — `"http://127.0.0.1:$port/mcp"`.
  - `mcpLegacySseUrl: String` — `"http://127.0.0.1:$port/mcp/sse"`.
- **Lifecycle**:
  - `before()` -> binds free port, starts Netty non-blocking, polls TCP socket readiness up to 5s.
  - `after()` -> graceful Netty shutdown (`stop(500, 2000)`).

### 1.2 `EmbeddedGrpcTestServerRule`
- **Purpose**: Manages an isolated in-process Netty gRPC server serving dynamic unary RPCs (`helloworld.Greeter/SayHello`, `helloworld.Greeter/SayHelloError`) with request-metadata echoing and response trailing metadata, paired with generated temporary `.proto` files.
- **Fields**:
  - `port: Int` — dynamically bound port.
  - `address: String` — `"127.0.0.1:$port"`.
  - `protoFilePath: String` — absolute path to temp `greeter.proto`.
  - `protoDirPath: String` — directory containing imported proto definitions.
- **Lifecycle**:
  - `before()` -> writes `.proto` fixtures to temp dir, starts Netty gRPC server.
  - `after()` -> shuts down gRPC server and deletes temp dir.

### 1.3 `IsolatedWorkspaceRule`
- **Purpose**: Guarantees every UI E2E test starts with clean, isolated storage and leaves zero residual preferences or temp files.
- **Fields**:
  - `tempDir: File` — temporary canonical collection storage directory.
  - `repository: DefaultCanonicalCollectionRepository` — wired into `WorkspaceRepository.canonicalRepository`.
- **Validation Rules**:
  - Saves/restores previous `WorkspaceRepository.canonicalRepository`.
  - Clears `PlatformStorage` keys (`clapi.tabs`, `clapi.workspace`, `clapi.settings`) before and after each test.
  - Calls `appState.disposeAndAwait()` on registered `AppState` instances in teardown so background coroutines/sessions never leak across tests.

---

## 2. CI Container Availability Contract (`qa-tests` & `ui-desktop`)

### 2.1 `CiTestGuard`
- **Purpose**: Unified helper replacing raw `Assume.assumeTrue(...)` for external Docker services (Keycloak, RabbitMQ, Vault, Azure Lowkey Vault).
- **Inputs**:
  - `serviceName: String`
  - `endpoint: String`
  - `isReachable: Boolean`
  - `isCi: Boolean` (`System.getenv("CI")?.equals("true", ignoreCase = true) == true`)
- **State Transitions / Rules**:
  - If `isReachable == true` -> proceed normally.
  - If `isReachable == false && isCi == true` -> **FAIL** (`kotlin.test.fail("$serviceName not reachable at $endpoint in CI — Docker service failed to start")`).
  - If `isReachable == false && isCi == false` -> **SKIP** via `org.junit.Assume.assumeTrue(...)` with local startup hint.

---

## 3. Packaged Runtime Smoke Check Result Model

### 3.1 CLI Mode `--packaged-smoke-test`
- **Inputs**:
  - `--packaged-smoke-test`
  - `--dir <isolated-temp-dir>`
- **Checks Executed Sequentially**:
  1. `STORAGE_ROUNDTRIP`: Save & reload `SettingsRepository`, `WorkspaceRepository` (1 collection + 1 HTTP request), `TabsRepository`.
  2. `SCRIPT_ENGINE_V8`: Instantiate `ClapiScriptEngine`, execute `clapi.test("v8 works", () => clapi.expect(1 + 1).to.equal(2))`, assert `result.success == true && result.testResults.single().passed`.
  3. `APP_DISPOSE`: Create `AppState`, verify `disposeAndAwait()` completes cleanly without hanging.
- **Exit Contract**:
  - Exit `0` + stdout line `CLAPI_SMOKE_OK checks=STORAGE_ROUNDTRIP,SCRIPT_ENGINE_V8,APP_DISPOSE`.
  - Non-zero exit + clear stderr diagnostic on any failure.

---

## 4. Secret Redaction Coverage Matrix

| Secret Source | Must Be Collected By `SecretLogRedactor` | Verified Redacted Surfaces |
|---|---|---|
| Active environment (`secret = true`) | Yes | Console logs, Network logs, Diagnostics logs (`clapi.log`), History items, Crash report / GitHub issue URL, Command exports (cURL/Python/HTTPie/PowerShell) |
| Inherited parent environment (`secret = true`) | Yes (audit & fix) | Same as above |
| Global variables (`secret = true`) | Yes (audit & fix) | Same as above |
| External Secret Manager (`{{_secret.*}}`) | Yes (`resolvedSecrets`) | Same as above |

---

## 5. Visual Regression Baseline Set

- **Directory**: `ui-desktop/src/desktopTest/snapshots/roborazzi/`
- **Baseline Previews Covered** (via Roborazzi Preview Scanner on `io.github.makeforge.clapi.ui.desktop.preview`):
  - `MainScreenPreviews` (HTTP Light/Dark, MCP Light/Dark, Response states)
  - `EnvironmentEditDialogPreviews`
  - `SettingsDialogPreviews`
  - `ImportSpecificationDialogPreviews`
  - `GitSyncPreviews`
  - `SelectProtoFileDialogPreviews`
  - `MainScreenFolderPreviews` & `MainScreenCollectionProtobufPreviews`
- **Validation Rule**: Verified on `ubuntu-latest` under `xvfb-run -a --server-args="-screen 0 1920x1080x24"` with deterministic rendering and bounded comparison threshold.
