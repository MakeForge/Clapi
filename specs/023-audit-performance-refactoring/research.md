# Phase 0 Research: Audit Performance & Architecture Refactoring

**Feature**: `023-audit-performance-refactoring`  
**Date**: 2026-11-14  
**Inputs**: [spec.md](file:///home/user/IdeaProjects/Clapi/specs/023-audit-performance-refactoring/spec.md), [scratch/audit_findings.md](file:///home/user/IdeaProjects/Clapi/scratch/audit_findings.md)

---

## 1. Phase 1 Architectural Decisions (Critical Fixes: `FR-001` – `FR-006`)

### Decision 1.1: Shared Network Client Registry vs. Default-Argument Client Allocation (`FR-001`, `FR-002`)
- **Decision**:
  1. Introduce a shared, lifecycle-managed `SharedNetworkClients` holder (or shared singleton defaults on `AppState` / `NetworkClientFactory`) that lazily initializes and reuses a single shared `KtorOAuth2TokenClient`, `KtorHashiCorpVaultClient`, `KtorAwsSecretsManagerClient`, `KtorGcpSecretManagerClient`, and `KtorAzureKeyVaultClient` backed by a shared Ktor `HttpClient`.
  2. Replace default argument expressions (`oauth2TokenClient: OAuth2TokenClient = KtorOAuth2TokenClient()`, etc.) in `RequestExecutor.kt`, `GraphQlRequestExecutor.kt`, `GrpcRequestExecutor.kt`, and `RequestVariableResolution.kt` with references to the shared client instances (while still allowing test overrides via explicit arguments).
  3. Update `KtorWebSocketClientManager` (`WebSocketClientManager.kt`) to use `SupervisorJob()` instead of `Job()`, reuse a single shared `HttpClient` (or close any per-connection client on disconnect), and implement `close()`.
  4. Update `McpClient` (`McpClient.kt`) and `StreamableHttpTransport` to reuse or explicitly `.close()` auxiliary `HttpClient` (`replyClient`, `streamClient`, `httpClient`) instances inside `disconnect()`.
- **Rationale**:
  Kotlin evaluates default parameter expressions on every function call. Previously, every single `sendRequest` invocation instantiated 5–6 new `HttpClient(CIO)` engines (each spawning NIO selector and worker threads) and never closed them. Sharing a single underlying `HttpClient` for auth/secret providers and properly closing WebSocket/MCP transport clients eliminates 100% of engine/thread leaks.
- **Alternatives Considered**:
  - *Closing all 5 clients in `sendRequest`'s `finally` block*: Rejected because it still allocates and tears down 5 CIO thread pools on every single request even when no OAuth2 or external secrets are configured, and prevents TCP/TLS connection reuse to Vault/AWS/GCP/Azure/OAuth2 endpoints.

---

### Decision 1.2: Debounced Persistence with Main-Thread Snapshot Capture (`FR-003`)
- **Decision**:
  1. Add `.debounce(500)` and `.collectLatest` (after skipping the initial emission via `.drop(1)`) to the three `snapshotFlow` persistence observers in `MainScreen.kt:134-181` (`SettingsRepository.save`, `TabsRepository.save`, and `WorkspaceRepository.save`).
  2. Refactor `TabsRepository.save(state)` and `WorkspaceRepository.save(state)` so that reading Compose `mutableStateOf` / `SnapshotStateList` properties and constructing the immutable serializable `JsonObject` / model hierarchy occurs on the calling thread (`Dispatchers.Main`) before switching to `withContext(ioDispatcher)` (`Dispatchers.IO`) for JSON string encoding and file writing.
  3. Ensure any UI error reporting (`state.showError(...)`) or state mutation (`state.refreshAllGitStatusesAsync()`) inside `WorkspaceRepository.save` is executed on `Dispatchers.Main` rather than `Dispatchers.IO`.
- **Rationale**:
  Previously, every keystroke in `CodeEditor` (`textChangedFlow` has 0 ms debounce) or `RequestBar` incremented `persistenceRevision` and immediately triggered `TabsRepository.save(state)`, which read mutable Compose state from `Dispatchers.IO` while the UI thread was actively mutating it, serialized all open tabs, and flushed to disk. Capturing the immutable data snapshot on `Dispatchers.Main` after a 500 ms debounce eliminates both per-keystroke disk I/O and cross-thread data races.
- **Alternatives Considered**:
  - *Debouncing inside `EditorRenderer.textChangedFlow`*: Rejected because `textChangedFlow` must update `tab.bodyContent` immediately so Ctrl+Enter / Send always sees the latest typed character; only disk persistence should be debounced.

---

### Decision 1.3: Secret Redaction in Network and Console Logs (`FR-004`)
- **Decision**:
  Introduce a `SecretLogRedactor` utility in `ui-shared` (or `core-network`) that collects all non-blank secret values (length >= 3) from:
  1. `resolvedSecrets` (external secret managers: Vault, AWS, GCP, Azure, KeePassXC, Keeper), and
  2. Active environment and collection variables marked `secret = true`.
  Before calling `state.logNetworkEvent("→ $effectiveMethod ...")` in `RequestExecutor.kt` and `GraphQlRequestExecutor.kt`, pass the logged URL through `SecretLogRedactor.redact(url, secretValues)`, replacing each secret value with `"••••••••"`.
- **Rationale**:
  Interpolating `effectiveVariableLayers` into URL paths or query parameters (`?api_key={{_secret.vault.key}}`) previously wrote the resolved secret in plain text to `networkEventLogs` and `consoleLogs`. Redacting values >= 3 chars protects API keys, tokens, and passwords without corrupting short common characters (such as `/` or `1`).
- **Alternatives Considered**:
  - *Logging the uninterpolated template URL (`{{api_key}}`)*: Rejected because users rely on Network Event Logs to inspect the resolved host/path/non-secret query parameters sent over the wire.

---

### Decision 1.4: Tab Job Cancellation on Close & `CancellationException` Propagation (`FR-005`, `FR-006`)
- **Decision**:
  1. In `AppState.closeTab(index)` and `AppState.closeTabsByIds(ids)`, cancel `closingTab.currentJob?.cancel()`, clear `closingTab.currentJob = null`, and set `closingTab.isLoading = false`.
  2. In `RequestExecutor.kt`, `GraphQlRequestExecutor.kt`, and `GrpcRequestExecutor.kt`, check `currentCoroutineContext().ensureActive()` before mutating tab state or triggering `setNextRequest` chains.
  3. Add explicit `catch (e: CancellationException) { throw e }` blocks before `catch (e: Exception)` in:
     - `RequestVariableResolution.kt` (`resolveExternalSecretsForTab`, `resolveDynamicVariablesForTab`)
     - `ClapiScriptEngine.kt` (`executePreRequestScript`, `executeTestScript`)
     - `DesktopGitCliExecutor.kt` (`execute`)
     - `McpSessionState.kt` (`connect`, `approveSamplingGenerate`)
     - `McpClient.kt` (`connect`, `handleServerRequest`)
- **Rationale**:
  Structured concurrency in Kotlin Coroutines requires `CancellationException` to propagate untouched. Swallowing it caused cancelled requests to show fake error toasts or continue running scripts, and failing to cancel `tab.currentJob` on tab close left orphaned requests running in the background.
- **Alternatives Considered**:
  - *Creating a per-tab `CoroutineScope`*: Useful, and canceling `tab.currentJob` on `closeTab` / `closeTabsByIds` directly terminates the active request job with minimal invasive changes to existing test harnesses that pass a `TestScope` into `sendRequest(scope, state, tab)`.

---

## 2. Phase 2 Architectural Decisions (Core Performance & Pooling: `FR-007` – `FR-012`)

### Decision 2.1: Offloading Script Evaluation, File Picker I/O, and Editor Processing (`FR-007`, `FR-012`)
- **Decision**:
  1. In `PlatformJsRuntime.desktop.kt`, wrap `evaluateJs(script: String)` in `withContext(Dispatchers.Default)` so GraalJS / Javet script evaluation never blocks `Dispatchers.Main`.
  2. In `PlatformApi.desktop.kt`, perform file reading (`selected.readText()`, `selected.readBytes()`, Base64 encoding) and file writing (`file.writeText(content)`) on `Dispatchers.IO` after the Swing file chooser returns, then invoke the `onResult` callback on `Dispatchers.Main`.
  3. In `CodeEditor.kt`, offload manual "Format" button clicks (`autoFormat`) and read-only formatting/folding/search for large documents to `Dispatchers.Default`, and in `EditorRenderer.kt` avoid materializing `viewModel.getFullText()` on every cursor move when the document exceeds a safe size threshold (or query characters directly from `GapBuffer`).
- **Rationale**:
  Desktop UI responsiveness requires keeping AWT EDT / `Dispatchers.Main` free of CPU-bound parsing/scripting and blocking disk reads.
- **Alternatives Considered**:
  - *Running the entire `sendRequest` coroutine on `Dispatchers.IO`*: Rejected because `sendRequest` mutates dozens of Compose `mutableStateOf` and `SnapshotStateList` properties on `RequestTabState` and `AppState` which must be mutated on `Dispatchers.Main` (or inside snapshots) to avoid cross-thread UI state corruption. Keeping state mutations on `Main` while wrapping CPU/IO phases in `withContext(Dispatchers.Default / IO)` is thread-safe and idiomatic.

---

### Decision 2.2: Incremental $O(1)$ Stream Assembly & Non-Allocating Response Size Calculation (`FR-008`, `FR-009`)
- **Decision**:
  1. Add `LlmTextAssembler.extractChunkDelta(rawChunk: String): String?` (or incremental state) in `StreamSupport.kt` so `RequestExecutor.kt` and `GraphQlRequestExecutor.kt` can append each incoming SSE/LLM chunk delta in $O(1)$ time without calling `LlmTextAssembler.assemble(tab.streamChunks.toList())` from index 0 on every event (falling back to full assembly only on `NetworkEvent.Completed` if needed).
  2. In `KtorApiClient.kt:toResponseDefinition`, only compute UTF-8 byte length when `headers["Content-Length"]` is missing/negative, using a zero-allocation `String.utf8ByteLength()` loop instead of `bodyText.encodeToByteArray().size`.
  3. Gate `LlmTextAssembler.assembleFromBody(bodyText)` behind a fast check (`bodyText.length <= 512_000 && mayContainLlmEnvelope(bodyText)`) so multi-megabyte standard REST responses are not parsed into a `JsonElement` tree twice.
- **Rationale**:
  Reduces streaming CPU cost from $O(N^2)$ to $O(N)$ and eliminates tens of megabytes of transient `ByteArray` and `JsonElement` allocations on every HTTP response.
- **Alternatives Considered**:
  - *Using `bodyText.length` as `sizeBytes`*: Rejected because multi-byte UTF-8 characters (emojis, CJK, accented text) have more bytes than UTF-16 code units; a linear `utf8ByteLength()` scan computes exact UTF-8 byte length with zero heap allocation.

---

### Decision 2.3: HTTP Client & gRPC Channel Pooling, `protoc` Descriptor Caching, and GraphQL Settings (`FR-010`, `FR-011`)
- **Decision**:
  1. Cache configured `KtorApiClient` instances in `NetworkClientFactory.desktop.kt` keyed by `ClientPoolKey(followRedirects, maxRedirects, requestTimeoutSec, httpProxy, httpsProxy, noProxy)` (up to e.g. 8 entries, closing evicted clients on shutdown) instead of creating and closing a new `HttpClient(CIO)` on every single request. When `RequestExecutor` / `GraphQlRequestExecutor` uses a pooled client from `createConfiguredApiClient(settings)`, it does not destroy the shared engine in `finally`.
  2. In `NettyDynamicGrpcClient.kt`, cache parsed `DescriptorProtos.FileDescriptorSet` instances in a thread-safe `ConcurrentHashMap<ProtoCacheKey, FileDescriptorSet>` where `ProtoCacheKey` records the canonical `.proto` file paths and their `File.lastModified()` timestamps. Pool `ManagedChannel` instances by `(host, port, useTls)` so sequential gRPC calls reuse HTTP/2 connections.
  3. In `GraphQlRequestExecutor.kt`, merge `tab.graphQlFollowRedirects`, `tab.graphQlTimeout`, and `tab.graphQlMaxRedirects` into `effectiveSettings` before obtaining the `ApiClient`, matching `RequestExecutor.kt`.
- **Rationale**:
  Reusing `HttpClient(CIO)` and gRPC `ManagedChannel` enables TCP/TLS keep-alive and HTTP/2 multiplexing, while caching `FileDescriptorSet` avoids extracting and spawning an external `protoc` OS process on every gRPC call.
- **Alternatives Considered**:
  - *Caching `.proto` descriptors by file path only (ignoring `lastModified`)*: Rejected because users editing a `.proto` file on disk during development would see stale gRPC schemas until restarting Clapi.

---

## 3. Phase 3 Architectural Decisions (UI Virtualization, Memory Caps & State Isolation: `FR-013` – `FR-017`)

### Decision 3.1: Virtualized `ResponseRawView` & Indexed `EditorRenderer` Lookups (`FR-013`, `FR-014`)
- **Decision**:
  1. Replace the single `Text(text = rawText)` inside `SelectionContainer` + `verticalScroll` in `ResponseViewer.kt:ResponseRawView` with a `LazyColumn` rendering lines lazily with `PlatformLazyVerticalScrollbar`.
  2. In `EditorRenderer.kt`, precompute `foldableStartLines: Set<Int>` and `diagnosticsByLine: Map<Int, List<InlineEditorError>>` via `remember(state.version, viewModel.foldRegions)` and `remember(state.diagnostics)` outside the `LazyColumn` `items` block so per-line lookup is $O(1)$.
  3. In `BodyEditor.kt` and `VariableHighlight.kt`, wrap `definedVarNames` / `definedNames` and `variableHighlightProvider` in `remember` / `derivedStateOf`.
- **Rationale**:
  Prevents multi-megabyte raw responses from freezing Skia paragraph layout and eliminates $O(\text{visibleLines} \times \text{foldRegions})$ linear scans on every scroll frame.

---

### Decision 3.2: Per-Tab `testResults`, Ring-Buffer Caps, and State Modularization (`FR-015`, `FR-016`, `FR-017`)
- **Decision**:
  1. Add `val testResults = mutableStateListOf<TestResult>()` to `RequestTabState`. Update `RequestExecutor`, `GraphQlRequestExecutor`, `GrpcRequestExecutor`, `RequestExecutionPipeline`, and `ResponseViewer` to write and read `tab.testResults` (while keeping a delegating property on `AppState.testResults` that returns `activeTab?.testResults ?: fallbackTestResults` for backward compatibility with existing tests).
  2. Enforce maximum capacity limits on `AppState.consoleLogs` (1,000), `AppState.networkEventLogs` (1,000), `AppState.historyItems` (500), `RequestTabState.webSocketLogMessages` (1,000), and `RequestTabState.streamChunks` (5,000).
  3. Replace the unbounded `listStateCache` and `hScrollCache` maps in `EditorRenderer.kt` with `remember(viewModel) { LazyListState() }` and `remember(viewModel) { ScrollState(0) }`.
  4. Extract cohesive domain state helpers/delegates from `AppState` (such as `DiagnosticsLogState` for bounded logging/redaction and `SharedNetworkClientRegistry` for network lifecycle) while keeping `AppState`'s public API 100% source-compatible.
- **Rationale**:
  Isolates test results per tab while keeping 100% backward compatibility with existing tests that inspect `state.testResults`, bounds heap usage over multi-day desktop sessions, and improves maintainability without breaking callers.
