# Development Guide

Everything you need to build, run, test, and contribute to Clapi.

---

## Prerequisites

| Tool | Minimum | Recommended |
|---|---|---|
| JDK | 17 | 21 (tested with Zulu 21) |
| OS | macOS / Linux / Windows | — |

> The Gradle Wrapper is bundled — no separate Gradle installation needed.

---

## Repository Layout

```
clapi/
├── core-model/          # Shared domain models (RequestDefinition, ResponseDefinition, …)
├── core-network/        # Ktor-backed HTTP engine, request execution service & adapter, interceptors, retry, WebSocket, external secrets, OAuth2
├── core-storage/        # Persistence contracts and JSON file adapter
├── core-scripting/      # Script engine contracts (pre-request / post-request scripts)
├── editor-core/         # Pure-Kotlin editor engine: document model, lexer, fold map, diagnostics
├── editor-ui/           # Compose editor renderer: EditorRenderer, EditorViewModel, IdleLexer
├── ui-shared/           # Compose UI code (desktop/JVM target) — 95% of all UI
├── ui-desktop/          # Thin Compose Desktop launcher (~74 lines)
├── qa-tests/            # JVM integration + end-to-end tests
├── sample-server/       # Standalone Ktor server for manual/exploratory testing
├── buildSrc/            # Shared Gradle build logic
├── docs/                # Architecture and testing reference docs
├── gradle/              # Version catalog (libs.versions.toml) and Gradle Wrapper
└── settings.gradle.kts  # Module declarations
```

### Key library versions

| Library | Version |
|---|---|
| Kotlin | 2.1.21 |
| Compose Multiplatform | 1.8.1 |
| Ktor | 3.1.2 |

Full version catalog: [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

---

## Building

Clapi is a **desktop-only** application (Kotlin Multiplatform with a single `jvm("desktop")` target; there are no Android, iOS or web targets). Prefer a targeted compile or run while developing.

```bash
./gradlew :ui-desktop:compileKotlinDesktop
./gradlew :ui-desktop:run
```

If you still want a full `build`:

```bash
./gradlew build
```

**FOR ALL GRADLE COMMANDS USE `rtk` as described in [copilot-instructions.md](./.github/copilot-instructions.md)**

Compile only the shared UI module (fast check during development):

```bash
./gradlew :ui-shared:compileKotlinDesktop
```

### Build Linux Target and Install on Ubuntu

To package and install Clapi on Ubuntu (Debian-based distributions):

```bash
# 1. Build the Debian / Ubuntu native .deb package
./gradlew :ui-desktop:packageDeb
# Or for optimized release packaging:
# ./gradlew :ui-desktop:packageReleaseDeb

# 2. Install the package via apt (automatically resolves system dependencies)
sudo apt install ./ui-desktop/build/compose/binaries/main/deb/clapi_*.deb

# 3. Launch Clapi
clapi
```

> **Tip**: The package installs desktop integration under `/usr/share/applications/` and `/opt/clapi/` (search for **Clapi** in the Ubuntu Application Dash). To run without installing, execute `./ui-desktop/build/compose/binaries/main/app/clapi/bin/clapi`. To uninstall: `sudo apt remove clapi`.

### Linux Target Packaging

Clapi uses Compose Multiplatform's native distribution packaging to build Linux native installers (`.deb` for Debian/Ubuntu, `.rpm` for Fedora/RHEL) as well as unpacked standalone application binaries.

Build Debian / Ubuntu package (`.deb`):

```bash
./gradlew :ui-desktop:packageDeb
# Or for optimized release packaging:
./gradlew :ui-desktop:packageReleaseDeb
```

Build Red Hat / Fedora package (`.rpm`):

```bash
./gradlew :ui-desktop:packageRpm
# Or for release:
./gradlew :ui-desktop:packageReleaseRpm
```

Build standalone unpacked application image (executable directory without creating installer):

```bash
./gradlew :ui-desktop:createDistributable
# Or for release:
./gradlew :ui-desktop:createReleaseDistributable
```

Build for the current host operating system automatically:

```bash
./gradlew :ui-desktop:packageDistributionForCurrentOS
```

#### Linux Artifact Locations

| Artifact Type | Output Directory / Path |
|---|---|
| **Debian / Ubuntu (`.deb`)** | `ui-desktop/build/compose/binaries/main/deb/clapi_<version>_amd64.deb` |
| **Release `.deb`** | `ui-desktop/build/compose/binaries/main-release/deb/clapi_<version>_amd64.deb` |
| **Fedora / RHEL (`.rpm`)** | `ui-desktop/build/compose/binaries/main/rpm/clapi-<version>-1.x86_64.rpm` |
| **Unpacked Application Directory** | `ui-desktop/build/compose/binaries/main/app/clapi/` (launcher: `bin/clapi`) |
| **Universal Fat JAR** | `ui-desktop/build/distribute/Clapi-<version>.jar` |

### Installing and Running on Ubuntu

#### 1. Install via `apt` (Recommended)
Using `apt` automatically installs the package and resolves any missing system dependencies:

```bash
sudo apt install ./ui-desktop/build/compose/binaries/main/deb/clapi_*.deb
```

#### 2. Install via `dpkg`
Alternatively, use `dpkg`:

```bash
sudo dpkg -i ui-desktop/build/compose/binaries/main/deb/clapi_*.deb
# If there are unresolved dependencies, run:
sudo apt-get install -f
```

#### 3. Launching Clapi on Ubuntu
- **From Terminal**:
  ```bash
  clapi
  ```
- **From Application Menu / Dash**:
  Search for **Clapi** in the Ubuntu application launcher. The package installs standard `.desktop` integration under `/usr/share/applications/` and `/opt/clapi/`.

#### 4. Running Without Installing
To run the built native executable directly from the build directory without installing the `.deb`:

```bash
./ui-desktop/build/compose/binaries/main/app/clapi/bin/clapi
# Or via Gradle:
./gradlew :ui-desktop:runDistributable
```

#### 5. Uninstalling from Ubuntu
To remove the application:

```bash
sudo apt remove clapi
# Or to completely purge package files and configuration:
sudo apt purge clapi
```

## Branding Assets

App icons are versioned in:

- `ui-desktop/src/desktopMain/resources/icons/` (PNG sizes + `.icns` + `.ico`)

Desktop window icon is loaded from `Main.kt`; native package icons are configured in
`ui-desktop/build.gradle.kts` under `nativeDistributions`.

---

## Running

### Desktop application

```bash
./gradlew :ui-desktop:run
```

To run in the background (returns the prompt immediately):

```bash
./gradlew :ui-desktop:run &
```

### Development mode with isolated storage (`jvmDev` / `runDev`)

Runs Clapi in development mode with persistent, isolated storage in `.clapi-dev/` rather than the system user preferences/storage:

```bash
./gradlew :ui-desktop:runDev
# Or:
./gradlew :ui-desktop:jvmDev
```

### Desktop jar artifact (fat jar)

Build runnable jar:

```bash
./gradlew :ui-desktop:packageClapiJar
```

Run jar directly:

```bash
java -jar ui-desktop/build/distribute/Clapi-$(grep '^appVersion=' gradle.properties | cut -d= -f2).jar
```

Run jar via Gradle launcher task (sets Dock app name/icon on macOS):

```bash
./gradlew :ui-desktop:runClapiJar
```

### Sample server (for manual testing)

The `sample-server` module runs a local Ktor server that mirrors the endpoints used by integration tests:

```bash
./gradlew :sample-server:run
```

Default address: `http://localhost:8080`

Import `qa-tests/fixtures/clapi-test-collection.json` and `clapi-test-environment.json`. The **LLM (OpenAI-compatible)** folder uses `llmBaseUrl` / `llmApiKey` / `llmModel`. **LLM Chat Completions Stream (visible)** (`?demo=true`) is the request to use when you want to watch tokens arrive.

Available endpoints (selected):

| Method | Path | Notes |
|---|---|---|
| GET | `/` | Health check |
| GET/POST/PUT/PATCH/DELETE/OPTIONS/HEAD | `/api/users` (+ `/{id}` where applicable) | HTTP methods coverage |
| GET | `/api/search` | Query param coverage |
| GET | `/api/echo-headers` | Header echo |
| POST | `/api/json` | JSON body echo |
| POST | `/api/graphql` | GraphQL body coverage |
| POST | `/api/raw` | Raw text body |
| POST | `/api/form-data` | Multipart form data |
| POST | `/api/urlencoded` | URL-encoded body |
| POST | `/api/upload` | Upload / binary payload handling |
| GET | `/api/auth/basic` | Basic auth checks |
| GET | `/api/auth/bearer` | Bearer token checks |
| GET | `/api/auth/apikey` | API key checks |
| GET | `/api/time` / `/api/timestamp` | Time helpers |
| GET | `/api/protected` | Script token-protected endpoint |
| GET | `/api/cookies` | Cookies set/echo |
| GET | `/api/redirect` → `/api/final` | Redirect flow |
| GET | `/api/error/{code}` | Error response matrix |
| GET | `/api/slow` | Slow response simulation |
| GET | `/status/200`, `/status/201` | Deterministic script assertions |
| GET | `/json/user`, `/json/array`, `/json/object` | Script/runtime payload checks |
| GET | `/headers`, `/cookies`, `/response-time`, `/string-body` | Script-runtime support endpoints |
| POST | `/echo-body`, `/api/validate`, `/api/token`, `/api/echo-full` | Script and validation helpers |
| GET | `/v1/models` | OpenAI-compatible model list (Bearer `llm-test-key` or `test-token`) |
| POST | `/v1/chat/completions` | OpenAI-compatible chat (JSON or SSE when `"stream": true`) |
| POST | `/v1/chat/ndjson` | Ollama-style NDJSON chat stream |
| POST | `/v1/embeddings` | Fixed-length embedding vector |
| GET | `/v1/chat/slow` | Delayed non-stream chat completion |
| GET | `/sse` | Finite SSE (`text/event-stream`); `?count=` (default 3), `?delayMs=` |
| POST | `/sse` | Finite SSE; last event echoes a body snippet; same `count` / `delayMs` |
| POST | `/mcp` | MCP Streamable HTTP (JSON-RPC) |
| POST | `/mcp/auth/bearer` | MCP Bearer `clapi-mcp-token` |
| POST | `/mcp/auth/basic` | MCP Basic `admin` / `password` |
| POST | `/mcp/auth/apikey` | MCP header `X-Api-Key: clapi-key` |
| POST | `/mcp/auth/jwt` | MCP Bearer JWT `clapi-mcp-jwt` |
| POST | `/mcp/authed` | MCP requiring Bearer `clapi-mcp-token` and `X-Api-Key: clapi-key` |
| POST | `/mcp?requireTenant=true&tenant=acme` | MCP query params required by the mock |
| GET | `/mcp` | MCP GET SSE (server-initiated) |
| DELETE | `/mcp` | MCP session terminate (`Mcp-Session-Id`) |
| GET | `/mcp/sse` | Legacy MCP HTTP+SSE |
| POST | `/mcp/messages` | Legacy MCP POST |
| POST | `/mcp/secure` | MCP with Bearer `mcp-oauth-token` |
| GET | `/.well-known/oauth-*` | MCP OAuth 2.1 metadata |
| POST | `/oauth/register` `/oauth/token` | Dynamic registration + token |
| GET | `/oauth/authorize` | Authorization (auto-approve for tests) |
| WS | `/ws` | WebSocket echo |

### MCP stdio and PATH shim

Stdio only (no HTTP port):

```bash
./gradlew :sample-server:run --args='--stdio'
```

To put `sample-server` on your login PATH:

```bash
./gradlew :sample-server:installMcpCommand
```

That writes `~/.local/bin/sample-server` on macOS/Linux, or `%USERPROFILE%\AppData\Local\Clapi\bin\sample-server.cmd` on Windows. The shim always starts MCP stdio (the Gradle HTTP start script without `--stdio` would print a banner on stdout and break framing). After install, the command field is `sample-server`. On a new machine or after moving the repo, run `installMcpCommand` again (the shim stores an absolute path to this repo’s `mcp-stdio` script).

How Clapi resolves a stdio command:

1. Parse the command line (tokens and quoting). Quoted paths with spaces work.
2. Resolve PATH from your login shell (`zsh`/`bash -ilc 'echo $PATH'`), merged with the process PATH, then look up the first token. On Windows, `.cmd` / `.exe` / `.bat` are tried.
3. If the first token looks like a path (`/` or `\`), resolve it against the process working directory once if that file exists.
4. Otherwise spawn the token as given. GUI apps often see a short PATH; the login-shell PATH is why `npx` and Homebrew binaries still resolve.

### MCP mock tools

| Tool | Role |
|---|---|
| `echo` | Returns the `text` argument |
| `add` | Adds numbers |
| `fail` | Error result |
| `slow` | Delayed result |
| `trigger_sampling` | Server requests `sampling/createMessage`; tool result is the client’s sampling reply |
| `trigger_roots` | Server requests `roots/list`; tool result is the client’s roots JSON |
| `trigger_elicitation` | Server requests `elicitation/create`; tool result is accept/decline |
| `trigger_ping` | Server requests `ping`; tool result is the client’s empty ping result |

Resources, prompts, and logging are also advertised so you can exercise those tabs. Product guide: [docs/request-types/mcp/index.md](docs/request-types/mcp/index.md).

### MCP sample-server troubleshooting

| Symptom | Likely cause | What to do |
|---|---|---|
| `Cannot run program "sample-server"` | Not on login PATH | `./gradlew :sample-server:installMcpCommand`, or an absolute path, or `./gradlew :sample-server:run --args='--stdio'` |
| Handshake is garbage / HTTP banner on stdout | PATH `sample-server` is the Gradle HTTP script without `--stdio` | Use the shim from `installMcpCommand` |
| `Cannot run program "sample-server --stdio"` | Whole string used as the executable (fixed in current builds) | Use current Clapi; command can be `sample-server` once it is on PATH |
| Timed out waiting for legacy SSE endpoint | Sample HTTP server not running, or GET `/mcp/sse` not streaming | `./gradlew :sample-server:run`; URL is `/mcp/sse` not `/mcp` |
| `Lost pending id` on legacy SSE | Reply arrived on SSE before POST returned (fixed in current builds) | Use current Clapi; **Legacy** and `http://localhost:8080/mcp/sse` |

LLM mock query parameters:

- `?demo=true` — longer assistant reply; streaming uses ~200ms per token (visible typewriter)
- `?chunkMs=<n>` — override SSE/NDJSON chunk delay
- `?fail=429` / `?fail=500` — simulated error statuses
- `?earlyClose=true` — close the stream before `[DONE]`
- `?ms=<n>` — extra delay before the response

Query string simulation modes:

- `?mode=slow` — adds artificial latency
- `?mode=error` — returns a 500 error
- `?large=true` — returns a large payload

---

## Testing

### Run all tests

```bash
./gradlew check
```

### Run tests by module

```bash
# Core protocol, network, gRPC, and OAuth behaviour
./gradlew :core-network:allTests

# Storage persistence and OpenCollection serialization
./gradlew :core-storage:allTests

# Script engine (Javet V8) and test execution
./gradlew :core-scripting:desktopTest

# Editor core document model, lexer, fold map, and syntax highlighting
./gradlew :editor-core:desktopTest

# Editor UI rendering and interaction tests
./gradlew :editor-ui:allTests

# Shared Compose UI composables and state tests
./gradlew :ui-shared:desktopTest

# Compose Desktop UI smoke tests
./gradlew :ui-desktop:desktopTest

# JVM integration + end-to-end tests (requires no external server)
./gradlew :qa-tests:jvmTest
```

### Integration test bundle only

```bash
./gradlew :qa-tests:jvmTest :core-storage:allTests :ui-desktop:desktopTest
```

### Compose UI Screenshot Testing (Roborazzi)

Clapi uses Roborazzi for Compose Desktop preview screenshot testing:

```bash
# Record / update preview screenshots
./gradlew :ui-desktop:recordRoborazziDesktop

# Verify preview screenshots against golden baselines
./gradlew :ui-desktop:verifyRoborazziDesktop

# Copy verified screenshots to docs assets
./gradlew :ui-desktop:copyScreenshots
```

### Packaging Verification & Diagnostics

```bash
# Execute the built fat JAR through a headless shutdown smoke test
./gradlew :ui-desktop:verifyPackagedRuntime

# Inspect dependency footprint, JAR structure, and installer sizes
./gradlew :ui-desktop:analyzeReleaseSize
```

### Test structure

| Module | Scope |
|---|---|
| `core-model` | Unit tests: domain models, OpenCollection items, serialization |
| `core-network` | Unit tests: HTTP method support, auth, variable interpolation, retry, WebSocket, gRPC, external secrets |
| `core-storage` | Unit tests: in-memory repository behaviour, OpenCollection JSON/YAML serialization round-trips |
| `core-scripting` | Unit tests: Javet V8 JavaScript execution, assertion libraries, request mutation |
| `editor-core` | Unit tests: document model, text buffers, syntax highlighters, fold regions |
| `editor-ui` | Unit tests: editor rendering, scrollbars, gutter diagnostics |
| `ui-shared` | Unit tests: shared UI state, viewmodels, dialog logic |
| `ui-desktop` | Compose Desktop smoke tests: window rendering, preview scanners |
| `qa-tests` | JVM integration and E2E tests running real requests against the embedded sample server |

### CI

GitHub Actions release packaging is defined in [`.github/workflows/release.yml`](.github/workflows/release.yml).

- **Push to `main`** — runs a quality gate first, then builds desktop artifacts for macOS/Linux/Windows.
- **Push tag `v*`** — runs the same quality gate, then builds artifacts and publishes a GitHub release.
- **Manual dispatch** — allows on-demand artifact builds.
```
git tag -a v1.19.0 -m "<MSG>"
git push origin v1.19.0
```
---

## Project Architecture

See [`docs/settings/architecture.md`](docs/settings/architecture.md) for a full write-up. Key rules:

- The **UI launcher** (`ui-desktop`) depends on **ui-shared** only.
- **ui-shared** depends on **feature modules** and **core modules**.
- **Feature modules** depend on **core modules** only.
- **Core modules** do not depend on feature or UI modules.
- All business logic and UI composables live in `commonMain` sources.
- Platform-specific code uses `expect`/`actual` declarations in `ui-shared`; the only actuals are in `desktopMain` (JVM).

### `ui-shared` component map

All shared UI code lives in
`ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/`:

```
MainScreen.kt              — root composable (toolbar, sidebar, editors, dialogs)
components/
├── RequestBar.kt          — method dropdown, URL field, Send/Save/Retry/cURL buttons
├── KeyValueEditor.kt      — reusable key-value table (Params, Headers)
├── BodyEditor.kt          — body type selector + content editor
├── AuthEditor.kt          — auth type selector + credential fields
├── ScriptEditor.kt        — code editor for Pre-request and Post-request scripts
├── RequestTabsBar.kt      — horizontal tab bar with scroll, context menu, indicators
├── RequestExecutor.kt     — sendRequest, saveRequest, buildAuthConfig, buildCurlCommand
├── RequestEditor.kt       — top-level editor composable, delegates to above
├── ResponseViewer.kt      — pretty/raw response body, headers, metrics
├── Sidebar.kt             — collections tree, environments, history
├── TopToolbar.kt          — environment picker, theme toggle, settings button
├── BottomPanel.kt         — network log panel
├── SettingsDialog.kt      — settings + workspace import/export
└── …dialogs and utilities
state/
├── AppState.kt            — root observable state (tabs, environments, collections, settings)
├── RequestTabState.kt     — per-tab mutable state
└── …
persistence/
├── ImportExportRepository.kt — JSON serialization for import/export (string-based)
├── SettingsRepository.kt  — settings persistence via PlatformStorage
├── TabsRepository.kt      — tab state persistence via PlatformStorage
└── WorkspaceRepository.kt — workspace persistence via PlatformStorage
network/
└── NetworkClientFactory.kt — expect/actual Ktor HttpClient factory
platform/
└── PlatformApi.kt         — expect/actual for UUID, clipboard, storage, file I/O, cursors
theme/
└── ClapiTheme.kt         — Material 3 colour tokens, typography, code font
```

Platform-specific actuals are in `desktopMain/` (JVM).

### `ui-desktop` (thin launcher)

`ui-desktop/src/desktopMain/kotlin/io/github/makeforge/ui/desktop/Main.kt` — ~44 lines.
Opens a Compose `Window`, loads settings/tabs/workspace, and renders `MainScreen`.

### UI Development Guidelines & Skills

For authoring and evaluating Compose Multiplatform Desktop UI elements, refer to the [create-compose-ui-kmp-desktop](/.agents/skills/create-compose-ui-kmp-desktop/SKILL.md) skill. It covers:
- **Modifier hygiene**: Mandatory `modifier: Modifier = Modifier` first optional parameter on the root element.
- **Desktop ergonomics**: Pairing scrollable containers with `VerticalScrollbar` / `HorizontalScrollbar`, cursor hover icons (`PointerIcon.Hand`), hover feedback, and `Key.Escape` dialog dismissal.
- **State management**: Aligning with `AppState` / `DialogState` / `RequestTabState` without Android ViewModels or mobile lifecycles.
- **Desktop previews**: Previews are reserved for sophisticated views (e.g. `MainScreen`, `RequestEditor`, `Sidebar`) to ease maintenance and debugging, and must live strictly in the `ui-desktop` module under `preview/` (`io.github.makeforge.clapi.ui.desktop.preview`). Individual leaf components/buttons do not require previews.

---

## Useful Gradle Tasks

```bash
# List all available tasks for a module
./gradlew :ui-desktop:tasks --all

# Run Clapi desktop in normal mode
./gradlew :ui-desktop:run

# Run Clapi desktop with isolated dev storage (.clapi-dev/)
./gradlew :ui-desktop:runDev

# Build Debian / Ubuntu package (.deb)
./gradlew :ui-desktop:packageDeb
./gradlew :ui-desktop:packageReleaseDeb

# Build standalone unpacked native Linux executable directory
./gradlew :ui-desktop:createDistributable

# Package runnable fat JAR (build/distribute/Clapi-<version>.jar)
./gradlew :ui-desktop:packageClapiJar

# Verify fat JAR headless startup & shutdown
./gradlew :ui-desktop:verifyPackagedRuntime

# Analyze release artifact size & dependencies
./gradlew :ui-desktop:analyzeReleaseSize

# Force re-run tests (skip UP-TO-DATE cache)
./gradlew :ui-desktop:desktopTest --rerun-tasks

# Build and show a dependency report for a module
./gradlew :ui-desktop:dependencies

# Compile shared UI for desktop
./gradlew :ui-shared:compileKotlinDesktop
```


## Remove data
```
# macOS — delete the prefs file
defaults delete io.github.makeforge.clapi.ui.shared.platform
# or remove the file directly
rm ~/Library/Preferences/io.github.makeforge.clapi.ui.plist

# Linux
rm -rf ~/.java/.userPrefs/io/github/makeforge/

# Windows (PowerShell)
Remove-Item -Path "HKCU:\Software\JavaSoft\Prefs\com\clapi" -Recurse
```
