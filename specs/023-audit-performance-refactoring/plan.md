# Implementation Plan: Audit Performance & Architecture Refactoring

**Branch**: `023-audit-performance-refactoring` | **Date**: 2026-11-14 | **Spec**: [spec.md](file:///home/user/IdeaProjects/Clapi/specs/023-audit-performance-refactoring/spec.md)

**Input**: Feature specification from `/specs/023-audit-performance-refactoring/spec.md` and audit report from [scratch/audit_findings.md](file:///home/user/IdeaProjects/Clapi/scratch/audit_findings.md)

---

## Summary

Implement the 11 prioritized architecture, performance, concurrency, security, and memory-hygiene refactorings identified in [scratch/audit_findings.md](file:///home/user/IdeaProjects/Clapi/scratch/audit_findings.md), strictly ordered in the three recommended phases:
- **Phase 1 (Immediate Critical Fixes — `FR-001` to `FR-006`)**: Eliminate the 5 per-request leaked `HttpClient(CIO)` engines caused by default argument allocations across HTTP/GraphQL/gRPC/variable resolution and close WebSocket/MCP auxiliary clients; add 500 ms debounce and main-thread snapshot capture to Tabs/Workspace/Settings autosaves; redact secret environment and external secret manager values before logging to Network/Console logs; cancel `tab.currentJob` on tab close; and rethrow `CancellationException` across secret resolution, scripting, Git CLI, and MCP operations.
- **Phase 2 (Core Performance & Concurrency Hardening — `FR-007` to `FR-012`)**: Offload embedded JS evaluation (`PlatformJsRuntime.desktop.kt`), file picker I/O (`PlatformApi.desktop.kt`), and code editor formatting/folding/search (`CodeEditor.kt`, `EditorRenderer.kt`) off `Dispatchers.Main`; make SSE/LLM stream chunk assembly incremental ($O(1)$ per chunk); replace `bodyText.encodeToByteArray().size` with a zero-allocation UTF-8 byte counter and guard `LlmTextAssembler.assembleFromBody` on size/envelope keys; pool configured `KtorApiClient` and gRPC `ManagedChannel` instances while caching compiled `.proto` `FileDescriptorSet`s by file modification timestamp; and honor per-request GraphQL timeout/redirect settings.
- **Phase 3 (UI Virtualization, Bounded Memory Hygiene & Tab State Isolation — `FR-013` to `FR-017`)**: Virtualize `ResponseRawView` with `LazyColumn`; pre-index `foldRegions` and `diagnostics` by line number in `EditorRenderer` and memoize variable highlighting in `BodyEditor` and `VariableAwareTextField`; scope `testResults` per `RequestTabState`; enforce ring-buffer capacity limits on `consoleLogs`, `networkEventLogs`, `historyItems`, `webSocketLogMessages`, and `streamChunks`; prune `EditorRenderer` scroll caches and inactive tab buffers; and extract cohesive state holders from `AppState`.

---

## Technical Context

**Language/Version**: Kotlin Multiplatform 2.3.20 (`jvm("desktop")` target), Java 21 toolchain  
**Primary Dependencies**:
- Compose Multiplatform `1.10.3` (`:ui-shared`, `:editor-ui`, `:ui-desktop`)
- Ktor Client `3.4.2` (`CIO` engine in `:core-network`)
- gRPC `1.85.1` (`grpc-okhttp` / `grpc-protobuf` / `protoc-jar` in `:core-network`)
- GraalJS / Javet JS runtime (`:core-scripting`)
- `kotlinx-coroutines` `1.10.2`, `kotlinx-serialization-json` `1.10.0`
**Storage**: `PlatformStorage` (`~/.clapi/pstore`), OpenCollection YAML/JSON files (`:core-storage`)  
**Testing**: `kotlin.test`, `kotlinx-coroutines-test`, JUnit 4/5, Ktor `MockEngine` (`:core-network`, `:core-storage`, `:core-scripting`, `:editor-ui`, `:ui-shared`, `:ui-desktop`, `:qa-tests`)  
**Target Platform**: Desktop JVM (Linux, macOS, Windows)  
**Project Type**: Multi-module Kotlin Multiplatform desktop application  
**Performance Goals**:
- 0 leaked `HttpClient` engines or selector threads across repeated requests (`SC-001`)
- <= 1 disk persistence write per 500 ms typing burst (`SC-002`)
- 100% redaction of secret values (>= 3 chars) in network and console logs (`SC-003`)
- Responsive UI thread (< 100 ms frame times) during 3s script runs, 1,000-chunk SSE streams, and 5 MB response viewing (`SC-004`)
**Constraints**:
- Preserve KMP module dependency DAG (`:core-network` and `:core-scripting` must never depend on `:ui-shared`)
- Preserve 100% backward compatibility with existing public state APIs and test suites (`SC-006`)
**Scale/Scope**: Touch points across `:core-network`, `:core-storage`, `:core-scripting`, `:editor-ui`, and `:ui-shared` (~25 source files + dedicated unit/integration tests).

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Clean Module Boundaries (`DEVELOPMENT.md` & `.github/copilot-instructions.md`)**: **PASS** — All changes respect the existing module boundaries (`core-model` <- `core-network` / `core-storage` / `core-scripting` <- `ui-shared` <- `ui-desktop`, and `editor-core` <- `editor-ui` <- `ui-shared`).
- **Resource & Concurrency Safety**: **PASS** — Eliminates unclosed `HttpClient` engines, restores structured concurrency (`CancellationException` propagation and tab job cancellation), and keeps blocking I/O and CPU-heavy work off `Dispatchers.Main`.
- **Security & Secret Hygiene**: **PASS** — Prevents plain-text secret exposure in `networkEventLogs` and `consoleLogs`.
- **Test Verification Gate**: **PASS** — Every phase is backed by targeted unit and integration tests across all affected modules.

---

## Project Structure

### Documentation (this feature)

```text
specs/023-audit-performance-refactoring/
├── plan.md                                                    # This file (/speckit-plan output)
├── research.md                                                # Phase 0 architectural decisions & trade-offs
├── data-model.md                                              # Phase 1 pooling, redaction, buffer & index models
├── quickstart.md                                              # Phase 1 validation & test execution guide
├── checklists/
│   └── requirements.md                                        # Spec quality checklist (16/16 passed)
└── contracts/
    ├── shared-network-clients-contract.md                     # Client sharing, pooling & lifecycle contract
    ├── persistence-redaction-cancellation-contract.md         # Debounced save, secret masking & cancellation contract
    └── editor-ui-virtualization-contract.md                   # Off-main-thread execution, streaming & UI virtualization contract
```

### Source Code (repository root)

```text
# Phase 1: Immediate Critical Fixes (Shared Clients, Debounced Save, Secret Redaction, Cancellation)
core-network/
├── src/commonMain/kotlin/io/github/makeforge/clapi/core/network/
│   ├── websocket/
│   │   └── WebSocketClientManager.kt                          # MODIFY: SupervisorJob, reuse/close HttpClient, close()
│   └── mcp/
│       ├── McpClient.kt                                       # MODIFY: Rethrow CancellationException, close aux clients on disconnect
│       └── StreamableHttpTransport.kt                         # MODIFY: Close replyClient & streamClient in close()
core-storage/
└── src/desktopMain/kotlin/io/github/makeforge/clapi/core/storage/git/
    └── DesktopGitCliExecutor.kt                               # MODIFY: Rethrow CancellationException before wrapping GitExecutionException
core-scripting/
└── src/commonMain/kotlin/io/github/makeforge/clapi/core/scripting/
    └── ClapiScriptEngine.kt                                   # MODIFY: Rethrow CancellationException in pre-request & test script loops
ui-shared/
└── src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/
    ├── MainScreen.kt                                          # MODIFY: Add .drop(1).debounce(500).collectLatest to Settings, Tabs, Workspace snapshotFlows
    ├── mcp/
    │   └── McpSessionState.kt                                 # MODIFY: Rethrow CancellationException in connect & approveSamplingGenerate
    ├── persistence/
    │   ├── TabsRepository.kt                                  # MODIFY: Capture immutable JsonObject snapshot on Main before withContext(ioDispatcher)
    │   └── WorkspaceRepository.kt                             # MODIFY: Capture collection snapshots on Main, dispatch showError/git refresh on Main
    ├── state/
    │   └── AppState.kt                                        # MODIFY: Cancel tab.currentJob in closeTab & closeTabsByIds
    └── components/executor/
        ├── SharedExecutionClients.kt                          # NEW: Shared singleton OAuth2 & external secret clients
        ├── SecretLogRedactor.kt                               # NEW: Collect and mask secret values (>= 3 chars) in network/console logs
        ├── RequestVariableResolution.kt                       # MODIFY: Use SharedExecutionClients defaults & rethrow CancellationException
        ├── RequestExecutor.kt                                 # MODIFY: Use SharedExecutionClients defaults & SecretLogRedactor
        ├── GraphQlRequestExecutor.kt                          # MODIFY: Use SharedExecutionClients defaults & SecretLogRedactor
        └── GrpcRequestExecutor.kt                             # MODIFY: Use SharedExecutionClients defaults

# Phase 2: Core Performance & Concurrency Hardening (Off-Main-Thread, O(1) Streaming, Pooling)
core-scripting/
└── src/desktopMain/kotlin/io/github/makeforge/clapi/core/scripting/
    └── PlatformJsRuntime.desktop.kt                           # MODIFY: Offload evaluateJs to withContext(Dispatchers.Default)
core-network/
├── src/commonMain/kotlin/io/github/makeforge/clapi/core/network/
│   ├── KtorApiClient.kt                                       # MODIFY: Zero-allocation utf8ByteLength() & guarded LlmTextAssembler.assembleFromBody
│   └── StreamSupport.kt                                       # MODIFY: Add incremental LlmTextAssembler.extractChunkDelta
└── src/desktopMain/kotlin/io/github/makeforge/clapi/core/network/grpc/
    └── NettyDynamicGrpcClient.kt                              # MODIFY: Cache FileDescriptorSet by (paths, mtimes) & pool ManagedChannel by (host, port, useTls)
ui-shared/
├── src/desktopMain/kotlin/io/github/makeforge/clapi/ui/shared/
│   ├── network/
│   │   └── NetworkClientFactory.desktop.kt                    # MODIFY: Pool configured KtorApiClient instances by NetworkClientPoolKey
│   └── platform/
│       └── PlatformApi.desktop.kt                             # MODIFY: Offload file picker readText/readBytes/Base64/writeText to Dispatchers.IO
└── src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/
    ├── CodeEditor.kt                                          # MODIFY: Offload autoFormat, detectFoldRegions, and findSearchMatches off UI thread for large docs
    └── executor/
        ├── RequestExecutor.kt                                 # MODIFY: Incremental O(1) liveStreamText assembly on NetworkEvent.Chunk
        └── GraphQlRequestExecutor.kt                          # MODIFY: Incremental O(1) liveStreamText assembly & apply tab.graphQlFollowRedirects/Timeout/MaxRedirects

# Phase 3: UI Virtualization, Bounded Memory Hygiene & Tab State Isolation
editor-ui/
└── src/commonMain/kotlin/io/github/makeforge/clapi/editor/ui/
    ├── EditorViewModel.kt                                     # MODIFY: @Immutable on EditorDisplayState, bounded bracket match scan
    └── EditorRenderer.kt                                      # MODIFY: Pre-index foldableStartLines & diagnosticsByLine; replace unbounded scroll caches with remember(viewModel)
ui-shared/
└── src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/
    ├── components/
    │   ├── ResponseViewer.kt                                  # MODIFY: Virtualize ResponseRawView with LazyColumn; read tab.testResults
    │   ├── BodyEditor.kt                                      # MODIFY: Memoize definedVarNames & variableHighlightProvider with remember/derivedStateOf
    │   └── VariableHighlight.kt                               # MODIFY: Memoize definedNames with remember/derivedStateOf
    └── state/
        ├── RequestTabState.kt                                 # MODIFY: Add per-tab testResults, cap webSocketLogMessages (1,000) & streamChunks (5,000), evict inactive large bodyViewModels
        ├── DiagnosticsLogState.kt                             # NEW: Extracted bounded consoleLogs (1,000) & networkEventLogs (1,000) state holder
        └── AppState.kt                                        # MODIFY: Delegate logs to DiagnosticsLogState, cap historyItems (500), delegate testResults to activeTab
```

**Structure Decision**: All refactorings operate within the existing multi-module KMP layout (`:core-network`, `:core-storage`, `:core-scripting`, `:editor-ui`, `:ui-shared`), adding focused helper files (`SharedExecutionClients.kt`, `SecretLogRedactor.kt`, `DiagnosticsLogState.kt`) and corresponding unit/integration tests without altering the Gradle subproject graph.

---

## Complexity Tracking

No constitution violations. All changes simplify lifecycle management, reduce memory/thread footprint, and preserve existing module boundaries.
