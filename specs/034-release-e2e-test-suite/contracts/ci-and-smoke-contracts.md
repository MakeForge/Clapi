# Contract: CI Quality Gates & Packaged Deliverable Smoke Verification

**Feature**: `034-release-e2e-test-suite`  
**Covers**: `FR-001`, `FR-002`, `FR-003`, `FR-004`, `FR-016`

---

## 1. GitHub Actions Quality Gate Matrix

Both `.github/workflows/quality-check.yml` (PRs and pushes to `main`) and `.github/workflows/release.yml` (release tags & manual dispatch) MUST execute all 10 tested modules under `xvfb-run -a --server-args="-screen 0 1920x1080x24"`:

| # | Gradle Task | Module Scope |
|---|---|---|
| 1 | `:core-model:allTests` | Domain models, JSON5, OpenCollection, MCP, Realtime serialization |
| 2 | `:core-network:desktopTest` | HTTP, auth, OAuth2, retry, WS, gRPC, MCP (including re-enabled `McpClientHandshakeTest`), RabbitMQ, external secrets |
| 3 | `:core-scripting:desktopTest` | Javet V8 engine, dynamic variables, request context |
| 4 | `:core-storage:allTests` | OpenCollection JSON/YAML, OpenAPI/Postman/Insomnia importers, Git CLI |
| 5 | `:editor-core:desktopTest` | Document model, lexers, folding, diagnostics |
| 6 | `:editor-ui:allTests` | Compose code editor rendering & interactions |
| 7 | `:mcp-server:allTests` | Embedded MCP server protocol & tools |
| 8 | `:ui-shared:desktopTest` | Shared `AppState`, repositories, executors, i18n |
| 9 | `:ui-desktop:desktopTest` | All Compose Desktop UI tests + new UI E2E suite |
| 10 | `:qa-tests:jvmTest` | Full JVM E2E against `sample-server` & Docker Compose services |
| 11 | `:ui-desktop:verifyRoborazziDesktop` | Visual baseline verification on `ubuntu-latest` |

### Docker Compose Lifecycle Ordering in CI
1. Start containers via `:qa-tests:dockerComposeUp` before `:ui-desktop:desktopTest` and `:qa-tests:jvmTest` (so RabbitMQ live UI E2E and `qa-tests` both see running services).
2. Always tear down containers with `if: always()` via `:qa-tests:dockerComposeDown`.
3. `CI=true` is set by GitHub Actions; `OAuth2IntegrationTest`, `RabbitMqEndToEndTest`, and live RabbitMQ UI tests MUST fail hard if their container is unreachable when `CI=true`.

---

## 2. Packaged Runtime Smoke Contract

### 2.1 CLI Entrypoints in `Main.kt`

| CLI Flag | Input Args | Expected Behavior | Success Signal |
|---|---|---|---|
| `--shutdown-smoke-test` | (optional `--dir <path>`) | Initializes storage + calls `AppState(openDefaultTab = false).disposeAndAwait()` | Exit code `0` |
| `--packaged-smoke-test` | `--dir <temp-dir>` | Runs storage round-trip (`SettingsRepository`, `WorkspaceRepository`, `TabsRepository`), Javet V8 script execution (`ClapiScriptEngine`), and `AppState.disposeAndAwait()` | Exit code `0` + stdout `CLAPI_SMOKE_OK` |
| `--mcp-http-headless` | `--mcp-port 0 --dir <temp-dir>` | Starts embedded MCP HTTP server, prints `CLAPI_MCP_READY url=<url> pid=<pid>`, serves JSON-RPC `initialize` POST (`200 OK` with `protocolVersion`), exits cleanly on stdin EOF | HTTP `200` + Exit code `0` |
| `--mcp` | `--dir <temp-dir>` | Starts MCP stdio server, reads JSON-RPC `initialize` line from stdin, writes JSON-RPC result with `protocolVersion` to stdout, exits cleanly on stdin EOF | Valid JSON-RPC line + Exit code `0` |

### 2.2 Gradle Verification Tasks in `ui-desktop/build.gradle.kts`

1. **`verifyPackagedRuntime`** (depends on `packageClapiJar`):
   - Asserts fat JAR contains `META-INF/LICENSE` and `META-INF/NOTICE`.
   - Runs fat JAR with `--shutdown-smoke-test`, `--packaged-smoke-test --dir <tmp>`, `--mcp-http-headless --mcp-port 0 --dir <tmp>` (sends live HTTP `initialize`), and `--mcp --dir <tmp>` (sends stdio `initialize`).
2. **`verifyDistributableRuntime`** (depends on `createDistributable`):
   - Locates platform launcher binary under `ui-desktop/build/compose/binaries/main/app/`:
     - Linux: `Clapi/bin/Clapi`
     - macOS: `Clapi.app/Contents/MacOS/Clapi`
     - Windows: `Clapi/Clapi.exe`
   - Asserts bundled `LICENSE` and `NOTICE` files exist in the distributable app resources.
   - Runs the native launcher binary (which uses the **jlink-trimmed JDK 25 runtime**) with `--packaged-smoke-test --dir <tmp>` and `--mcp-http-headless --mcp-port 0 --dir <tmp>` (with live HTTP `initialize` handshake).
3. **`release.yml` Build Matrix Integration**:
   - Every platform runner (`macos`, `linux`, `windows`) executes `:ui-desktop:createDistributable` and `:ui-desktop:verifyDistributableRuntime` alongside `:ui-desktop:verifyPackagedRuntime` before staging/uploading release artifacts.
