# Tasks: Audit Performance & Architecture Refactoring

**Input**: Design documents from `/specs/023-audit-performance-refactoring/`

**Prerequisites**: [plan.md](file:///home/user/IdeaProjects/Clapi/specs/023-audit-performance-refactoring/plan.md) (required), [spec.md](file:///home/user/IdeaProjects/Clapi/specs/023-audit-performance-refactoring/spec.md) (required for user stories), [research.md](file:///home/user/IdeaProjects/Clapi/specs/023-audit-performance-refactoring/research.md), [data-model.md](file:///home/user/IdeaProjects/Clapi/specs/023-audit-performance-refactoring/data-model.md), [contracts/](file:///home/user/IdeaProjects/Clapi/specs/023-audit-performance-refactoring/contracts/)

**Tests**: Included per project verification gate and contracts to prove each refactoring increment works without regression.

**Organization**: Tasks are grouped by user story (`US1` = Phase 1 Immediate Critical Fixes, `US2` = Phase 2 Core Performance & Concurrency Hardening, `US3` = Phase 3 UI Virtualization, Bounded Memory Hygiene & Tab State Isolation) in the exact phased order from `scratch/audit_findings.md`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (`[US1]`, `[US2]`, `[US3]`)
- Exact file paths and `data-model.md` constraints are included in every task description

---

## Phase 1: Setup (Shared Baseline Verification)

**Purpose**: Confirm baseline build and test health across affected modules before applying phased refactorings

- [ ] T001 Verify baseline compilation of `:core-network`, `:core-storage`, `:core-scripting`, `:editor-ui`, and `:ui-shared` via `build.gradle.kts`

---

## Phase 2: Foundational (Shared Client & Redaction Primitives)

**Purpose**: Core shared primitives required by Phase 1 (`US1`) and Phase 2 (`US2`) request executors

**⚠️ CRITICAL**: Complete these foundational utilities before beginning User Story 1 executor updates

- [ ] T002 [P] Create `SharedExecutionClients` singleton holder with shared `sharedHttpClient: HttpClient`, `oauth2TokenClient: OAuth2TokenClient`, `vaultClient: HashiCorpVaultClient`, `awsSecretsClient: AwsSecretsManagerClient`, `gcpSecretsClient: GcpSecretManagerClient`, and `azureKeyVaultClient: AzureKeyVaultClient` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/executor/SharedExecutionClients.kt`
- [ ] T003 [P] Create `SecretLogRedactor` utility that collects secret values from `resolvedSecrets.values` and active environment/collection variables where `secret == true && enabled`, filters to non-blank strings with `length >= 3`, sorted by descending length, and replaces all occurrences with `"••••••••"` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/executor/SecretLogRedactor.kt`

**Checkpoint**: Shared client registry and secret log redactor ready for integration

---

## Phase 3: User Story 1 - Phase 1: Immediate Critical Resource, Persistence, Security & Cancellation Fixes (Priority: P1) 🎯 MVP

**Goal**: Eliminate the 5 per-request leaked `HttpClient(CIO)` engines and unclosed WebSocket/MCP clients (`FR-001`, `FR-002`), debounce Tabs/Workspace/Settings autosaves by 500 ms with main-thread snapshot capture (`FR-003`), redact secrets in network and console logs (`FR-004`), cancel `tab.currentJob` on tab close (`FR-005`), and preserve `CancellationException` propagation across suspending calls (`FR-006`).

**Independent Test**: Run `:core-network:desktopTest`, `:core-storage:desktopTest`, `:core-scripting:desktopTest`, and `:ui-shared:desktopTest` to verify zero per-request client allocations, masked secrets in `networkEventLogs`/`consoleLogs`, immediate `currentJob` cancellation on `closeTab`/`closeTabsByIds`, `CancellationException` propagation, and 500 ms debounced main-thread snapshot persistence.

### Implementation for User Story 1

- [ ] T004 [P] [US1] Replace default parameter client instantiations (`KtorOAuth2TokenClient()`, `KtorHashiCorpVaultClient()`, `KtorAwsSecretsManagerClient()`, `KtorGcpSecretManagerClient()`, `KtorAzureKeyVaultClient()`) with `SharedExecutionClients` defaults and rethrow `CancellationException` before `catch (e: Exception)` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/executor/RequestVariableResolution.kt`
- [ ] T005 [P] [US1] Replace default parameter client instantiations with `SharedExecutionClients` defaults and redact `effectiveUrlForLog` via `SecretLogRedactor.redact(...)` before calling `state.logNetworkEvent(...)` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/executor/RequestExecutor.kt`
- [ ] T006 [P] [US1] Replace default parameter client instantiations with `SharedExecutionClients` defaults and redact `effectiveUrlForLog` via `SecretLogRedactor.redact(...)` before calling `state.logNetworkEvent(...)` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/executor/GraphQlRequestExecutor.kt`
- [ ] T007 [P] [US1] Replace default parameter client instantiations with `SharedExecutionClients` defaults in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/executor/GrpcRequestExecutor.kt`
- [ ] T008 [P] [US1] Update `KtorWebSocketClientManager` to use `SupervisorJob()` instead of `Job()`, reuse a single shared `HttpClient` (or close per-connection clients in `finally`), and add `close()` in `core-network/src/commonMain/kotlin/io/github/makeforge/clapi/core/network/websocket/WebSocketClientManager.kt`
- [ ] T009 [P] [US1] Rethrow `CancellationException` in `McpClient.connect` and `McpClient.handleServerRequest` and add `close()` to close owned `httpClient` on session disposal in `core-network/src/commonMain/kotlin/io/github/makeforge/clapi/core/network/mcp/McpClient.kt`
- [ ] T010 [P] [US1] Rethrow `CancellationException` in `McpSessionState.connect` and `McpSessionState.approveSamplingGenerate`, and call `client.close()` in `McpSessionState.dispose()` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/mcp/McpSessionState.kt`
- [ ] T011 [P] [US1] Rethrow `CancellationException` before `catch (e: Exception)` around `sendRequestExecutor` calls in `executePreRequestScript` and `executeTestScript` in `core-scripting/src/commonMain/kotlin/io/github/makeforge/clapi/core/scripting/ClapiScriptEngine.kt`
- [ ] T012 [P] [US1] Rethrow `CancellationException` (when not caused by process timeout) before wrapping exceptions in `GitExecutionException` in `core-storage/src/desktopMain/kotlin/io/github/makeforge/clapi/core/storage/git/DesktopGitCliExecutor.kt`
- [ ] T013 [US1] Cancel `closingTab.currentJob?.cancel()`, clear `closingTab.currentJob = null`, and reset `closingTab.isLoading = false` inside `closeTab` and `closeTabsByIds` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/AppState.kt`
- [ ] T014 [P] [US1] Add `.drop(1).debounce(500).collectLatest` to the Settings, Tabs, and Workspace `snapshotFlow` persistence observers in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/MainScreen.kt`
- [ ] T015 [P] [US1] Refactor `TabsRepository.save` to capture the immutable `JsonObject` payload from Compose state on the calling thread (`Dispatchers.Main`) before switching to `withContext(ioDispatcher)` for JSON encoding and `PlatformStorage.putString` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/persistence/TabsRepository.kt`
- [ ] T016 [P] [US1] Refactor `WorkspaceRepository.save` to capture immutable collection/environment snapshots on the calling thread before `withContext(ioDispatcher)` and dispatch `state.showError` and `state.refreshAllGitStatusesAsync` on the calling/main thread in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/persistence/WorkspaceRepository.kt`

### Verification Tests for User Story 1

- [ ] T017 [P] [US1] Add unit tests for `SecretLogRedactor` (verifying `length >= 3` filter, descending length sort, and `"••••••••"` masking across environment and external secrets) and `closeTab` / `closeTabsByIds` in-flight `currentJob` cancellation in `ui-shared/src/commonTest/kotlin/io/github/makeforge/clapi/ui/shared/components/executor/Phase1SecurityAndCancellationTest.kt`

**Checkpoint**: User Story 1 (Phase 1 Immediate Critical Fixes) is complete and independently verified

---

## Phase 4: User Story 2 - Phase 2: Core Execution Performance, Off-Main-Thread Processing & Connection Pooling (Priority: P2)

**Goal**: Offload embedded JS evaluation, file picker I/O, and code editor formatting/folding/search off `Dispatchers.Main` (`FR-007`, `FR-012`), make SSE/LLM stream chunk assembly incremental ($O(1)$ per chunk, `FR-008`), replace `bodyText.encodeToByteArray().size` with zero-allocation UTF-8 byte counting and guard `LlmTextAssembler.assembleFromBody` on `bodyText.length <= 512_000` (`FR-009`), pool HTTP clients and gRPC `ManagedChannel`s while caching `.proto` descriptors by modification timestamp (`FR-010`), and wire per-request GraphQL settings (`FR-011`).

**Independent Test**: Run `:core-scripting:desktopTest`, `:core-network:desktopTest`, and `:ui-shared:desktopTest` to verify off-main-thread script evaluation, $O(1)$ stream delta extraction, zero-allocation UTF-8 byte size calculation, HTTP/gRPC pooling and `.proto` descriptor caching, and GraphQL per-tab timeout/redirect overrides.

### Implementation for User Story 2

- [ ] T018 [P] [US2] Wrap `evaluateJs(script: String)` in `withContext(Dispatchers.Default)` so GraalJS script evaluation never blocks `Dispatchers.Main` in `core-scripting/src/desktopMain/kotlin/io/github/makeforge/clapi/core/scripting/PlatformJsRuntime.desktop.kt`
- [ ] T019 [P] [US2] Offload `selected.readText()`, `selected.readBytes()` + Base64 encoding, and `file.writeText(content)` in `pickFileForImport`, `pickBinaryFileForRequest`, and `saveFileForExport` to `Dispatchers.IO` with completion callbacks dispatched on `Dispatchers.Main` in `ui-shared/src/desktopMain/kotlin/io/github/makeforge/clapi/ui/shared/platform/PlatformApi.desktop.kt`
- [ ] T020 [P] [US2] Add `LlmTextAssembler.extractChunkDelta(rawChunk: String): String?` and `LlmTextAssembler.mayContainLlmEnvelope(raw: String): Boolean` in `core-network/src/commonMain/kotlin/io/github/makeforge/clapi/core/network/StreamSupport.kt`
- [ ] T021 [US2] Update `HttpResponse.toResponseDefinition` to compute UTF-8 byte length via a zero-allocation `String.utf8ByteLength()` helper only when `Content-Length` is absent (`sizeFromHeader < 0`), and gate `LlmTextAssembler.assembleFromBody(bodyText)` on `bodyText.length <= 512_000 && LlmTextAssembler.mayContainLlmEnvelope(bodyText)` in `core-network/src/commonMain/kotlin/io/github/makeforge/clapi/core/network/KtorApiClient.kt`
- [ ] T022 [P] [US2] Update `NetworkEvent.Chunk` handling in `RequestExecutor.kt` to append deltas incrementally in $O(1)$ via `LlmTextAssembler.extractChunkDelta(event.raw)` instead of re-parsing `tab.streamChunks.toList()` on every chunk in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/executor/RequestExecutor.kt`
- [ ] T023 [P] [US2] Update `NetworkEvent.Chunk` handling to append deltas incrementally in $O(1)` and apply per-tab `tab.graphQlFollowRedirects`, `tab.graphQlTimeout`, and `tab.graphQlMaxRedirects` overrides when constructing `effectiveSettings` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/executor/GraphQlRequestExecutor.kt`
- [ ] T024 [P] [US2] Implement `NetworkClientPoolKey(followRedirects, maxRedirects, requestTimeoutSec, httpProxy, httpsProxy, noProxy)` and pool up to `MAX_POOL_SIZE = 8` configured `KtorApiClient` instances (wrapped in a non-closing pool facade so per-request `close()` retains the pooled engine) in `ui-shared/src/desktopMain/kotlin/io/github/makeforge/clapi/ui/shared/network/NetworkClientFactory.desktop.kt`
- [ ] T025 [P] [US2] Cache compiled `DescriptorProtos.FileDescriptorSet` by `ProtoDescriptorCacheKey(paths: List<String>, lastModifiedMillis: List<Long>)` and pool active `ManagedChannel` instances by `GrpcChannelKey(host: String, port: Int, useTls: Boolean)` in `core-network/src/desktopMain/kotlin/io/github/makeforge/clapi/core/network/grpc/NettyDynamicGrpcClient.kt`
- [ ] T026 [P] [US2] Offload manual Format button execution (`autoFormat`), fold-region detection (`detectFoldRegions`), and search match scanning (`findSearchMatches`) off the UI thread for large payloads in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/CodeEditor.kt`

### Verification Tests for User Story 2

- [ ] T027 [P] [US2] Add unit tests verifying zero-allocation `utf8ByteLength()` (ASCII, multi-byte, surrogate pairs), 512 KB LLM parse guard, and incremental `extractChunkDelta` parity in `core-network/src/commonTest/kotlin/io/github/makeforge/clapi/core/network/ResponseSizeAndStreamDeltaTest.kt`
- [ ] T028 [P] [US2] Add unit tests verifying per-request GraphQL settings (`graphQlFollowRedirects`, `graphQlTimeout`, `graphQlMaxRedirects`) in `ui-shared/src/commonTest/kotlin/io/github/makeforge/clapi/ui/shared/components/executor/GraphQlRequestExecutorTest.kt`

**Checkpoint**: User Story 2 (Phase 2 Core Performance & Concurrency Hardening) is complete and independently verified

---

## Phase 5: User Story 3 - Phase 3: UI Virtualization, Bounded Memory Hygiene & Tab State Isolation (Priority: P3)

**Goal**: Virtualize `ResponseRawView` with `LazyColumn` (`FR-013`), pre-index `foldRegions` and `diagnostics` by line number in `EditorRenderer` and memoize variable highlighting in `BodyEditor` and `VariableAwareTextField` (`FR-014`), isolate `testResults` per `RequestTabState` (`FR-015`), enforce ring-buffer caps on `consoleLogs` (`1,000`), `networkEventLogs` (`1,000`), `historyItems` (`500`), `webSocketLogMessages` (`1,000`), and `streamChunks` (`5,000`) (`FR-016`), prune `EditorRenderer` scroll caches and inactive tab buffers, and extract `DiagnosticsLogState` from `AppState` (`FR-017`).

**Independent Test**: Run `:editor-ui:desktopTest`, `:ui-shared:desktopTest`, `:ui-desktop:desktopTest`, and `:qa-tests:test` to verify virtualized raw response rendering, $O(1)$ per-line fold/diagnostic indexing, per-tab `testResults` isolation across tab switches, and ring-buffer eviction limits.

### Implementation for User Story 3

- [ ] T029 [P] [US3] Virtualize `ResponseRawView` using `LazyColumn` and `PlatformLazyVerticalScrollbar` inside `SelectionContainer`, and read `tab.testResults` (with fallback to `state.testResults`) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ResponseViewer.kt`
- [ ] T030 [P] [US3] Annotate `EditorDisplayState` with `@Immutable` in `editor-ui/src/commonMain/kotlin/io/github/makeforge/clapi/editor/ui/EditorViewModel.kt`
- [ ] T031 [P] [US3] Pre-index `foldableStartLines: Set<Int>` and `diagnosticsByLine: Map<Int, List<InlineEditorError>>` once per document version outside `LazyColumn`, replace unbounded `listStateCache` and `hScrollCache` maps with `remember(viewModel) { LazyListState() }` and `remember(viewModel) { ScrollState(0) }`, and bound cursor bracket-matching scans on oversized documents in `editor-ui/src/commonMain/kotlin/io/github/makeforge/clapi/editor/ui/EditorRenderer.kt`
- [ ] T032 [P] [US3] Memoize `definedVarNames` and `variableHighlightProvider` using `remember` and `derivedStateOf` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/BodyEditor.kt`
- [ ] T033 [P] [US3] Memoize `definedNames` using `remember` and `derivedStateOf` in `VariableAwareTextField` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/VariableHighlight.kt`
- [ ] T034 [US3] Add tab-scoped `val testResults = mutableStateListOf<TestResult>()`, enforce max capacity limits on `webSocketLogMessages` (`1,000` entries) and `streamChunks` (`5,000` entries), and add helper to release cached `EditorViewModel`s for inactive tabs with large bodies in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/RequestTabState.kt`
- [ ] T035 [P] [US3] Create `DiagnosticsLogState` state holder encapsulating `consoleLogs` (max capacity `1,000` entries, dropping oldest tail entries via `removeRange(MAX, size)`) and `networkEventLogs` (max capacity `1,000` entries, dropping oldest tail entries via `removeRange(MAX, size)`) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/DiagnosticsLogState.kt`
- [ ] T036 [US3] Update `AppState` to delegate `consoleLogs` and `networkEventLogs` to `DiagnosticsLogState`, delegate `testResults` to `activeTab?.testResults ?: fallbackTestResults`, enforce max capacity `500` entries on `historyItems` in `recordHistory`, and cap `webSocketLogMessages` at `1,000` entries in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/AppState.kt`
- [ ] T037 [US3] Update `RequestExecutionPipeline.runPostResponseTestScript` and `RequestExecutor` / `GraphQlRequestExecutor` / `GrpcRequestExecutor` to clear and populate `tab.testResults` (and cap `tab.streamChunks` at `5,000` entries) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/executor/RequestExecutionPipeline.kt`

### Verification Tests for User Story 3

- [ ] T038 [P] [US3] Add unit tests verifying per-tab `testResults` isolation when switching between Tab A and Tab B, and ring-buffer eviction caps for `consoleLogs` (`1,000`), `networkEventLogs` (`1,000`), `historyItems` (`500`), `webSocketLogMessages` (`1,000`), and `streamChunks` (`5,000`) in `ui-shared/src/commonTest/kotlin/io/github/makeforge/clapi/ui/shared/state/Phase3StateIsolationAndBoundsTest.kt`

**Checkpoint**: All three user stories (Phases 1, 2, and 3) are complete and independently verified

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: End-to-end regression suite verification across all modules

- [ ] T039 Run full test validation across `:core-network`, `:core-storage`, `:core-scripting`, `:editor-ui`, `:ui-shared`, `:ui-desktop`, and `:qa-tests` per `specs/023-audit-performance-refactoring/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — starts immediately.
- **Phase 2 (Foundational — `T002`, `T003`)**: Depends on Phase 1 — provides `SharedExecutionClients` and `SecretLogRedactor` used by Phase 3 (`US1`).
- **Phase 3 (User Story 1 — `T004` to `T017`, P1 🎯 MVP)**: Depends on Phase 2 (`T002`, `T003`).
- **Phase 4 (User Story 2 — `T018` to `T028`, P2)**: Depends on Phase 3 (`US1`) completion for shared executor files (`RequestExecutor.kt`, `GraphQlRequestExecutor.kt`).
- **Phase 5 (User Story 3 — `T029` to `T038`, P3)**: Depends on Phase 4 (`US2`) completion for executor/state integration.
- **Phase 6 (Polish — `T039`)**: Depends on Phases 3, 4, and 5 completion.

### Parallel Opportunities

- **Foundational (`Phase 2`)**: `T002` (`SharedExecutionClients.kt`) and `T003` (`SecretLogRedactor.kt`) touch separate new files and can run in parallel.
- **User Story 1 (`Phase 3`)**: `T004`–`T012` and `T014`–`T016` modify distinct files across `:core-network`, `:core-storage`, `:core-scripting`, and `:ui-shared` and can be executed in parallel.
- **User Story 2 (`Phase 4`)**: `T018` (`PlatformJsRuntime.desktop.kt`), `T019` (`PlatformApi.desktop.kt`), `T020` (`StreamSupport.kt`), `T024` (`NetworkClientFactory.desktop.kt`), `T025` (`NettyDynamicGrpcClient.kt`), and `T026` (`CodeEditor.kt`) touch distinct files and can run in parallel.
- **User Story 3 (`Phase 5`)**: `T029` (`ResponseViewer.kt`), `T030` (`EditorViewModel.kt`), `T031` (`EditorRenderer.kt`), `T032` (`BodyEditor.kt`), `T033` (`VariableHighlight.kt`), and `T035` (`DiagnosticsLogState.kt`) touch distinct files and can run in parallel.

---

## Parallel Example: User Story 1 (Phase 1 Fixes)

```bash
# 1. Build foundational utilities in parallel:
Task T002: "Create SharedExecutionClients singleton holder in ui-shared/.../SharedExecutionClients.kt"
Task T003: "Create SecretLogRedactor utility in ui-shared/.../SecretLogRedactor.kt"

# 2. Apply client sharing, redaction, cancellation, and debounce across independent files in parallel:
Task T005: "Update RequestExecutor.kt to use SharedExecutionClients and SecretLogRedactor"
Task T008: "Update WebSocketClientManager.kt to use SupervisorJob and shared/closed HttpClient"
Task T011: "Rethrow CancellationException in ClapiScriptEngine.kt"
Task T014: "Add .drop(1).debounce(500).collectLatest to snapshotFlows in MainScreen.kt"
Task T015: "Capture immutable JsonObject snapshot on Main in TabsRepository.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 / Phase 1 Immediate Critical Fixes)

1. Complete Phase 1 (Setup) and Phase 2 (Foundational `SharedExecutionClients` + `SecretLogRedactor`).
2. Complete Phase 3 (User Story 1: `T004`–`T017`).
3. **Validate MVP**: Run `:core-network:desktopTest :core-storage:desktopTest :core-scripting:desktopTest :ui-shared:desktopTest` to confirm zero leaked per-request clients, masked secrets in logs, cancelled jobs on tab close, and 500 ms debounced main-thread snapshot saves.

### Incremental Delivery (Phases 2 & 3)

1. Complete Phase 4 (User Story 2: `T018`–`T028`) → Verify off-main-thread scripting/file I/O, $O(1)$ stream assembly, zero-allocation UTF-8 response size, and HTTP/gRPC connection & `.proto` pooling.
2. Complete Phase 5 (User Story 3: `T029`–`T038`) → Verify virtualized `ResponseRawView`, $O(1)$ indexed editor rendering, per-tab `testResults`, and ring-buffer caps.
3. Complete Phase 6 (`T039`) → Run full cross-module regression suite.
