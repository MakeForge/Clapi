# Quickstart & Validation Guide: Audit Performance & Architecture Refactoring

**Feature**: `023-audit-performance-refactoring`  
**Date**: 2026-11-14

---

## Prerequisites

- JDK 21 installed and configured
- Repository cloned at `/home/user/IdeaProjects/Clapi`
- All Gradle commands prefixed with `rtk` per project instructions

---

## Phase-by-Phase Verification Commands

### 1. Phase 1 Validation (Shared Clients, Debounced Persistence, Secret Redaction & Cancellation)

Run the core network, storage, scripting, and shared UI test suites to verify shared client reuse, secret redaction in logs, tab-close job cancellation, `CancellationException` propagation, and debounced snapshot persistence:

```bash
rtk ./gradlew :core-network:desktopTest :core-storage:desktopTest :core-scripting:desktopTest :ui-shared:desktopTest
```

**Expected Outcomes**:
- `SharedExecutionClients` provides shared instances across `RequestExecutor`, `GraphQlRequestExecutor`, `GrpcRequestExecutor`, and `RequestVariableResolution` with zero per-request `HttpClient` leaks (`SC-001`).
- `SecretLogRedactorTest` confirms all secret environment variables and external secret manager values interpolated into request URLs/params are masked (`••••••••`) in `networkEventLogs` and `consoleLogs` (`SC-003`).
- `closeTab` and `closeTabsByIds` cancel `tab.currentJob` immediately and reset `tab.isLoading = false`.
- `CancellationException` thrown during secret resolution, script `sendRequest`, Git CLI, or MCP operations propagates cleanly without triggering false error toasts.

---

### 2. Phase 2 Validation (Off-Main-Thread Scripting, $O(1)$ Stream Assembly, Response Size & Pooling)

Run the scripting, streaming, gRPC, and GraphQL executor tests:

```bash
rtk ./gradlew :core-scripting:desktopTest :core-network:desktopTest :ui-shared:desktopTest
```

**Expected Outcomes**:
- `PlatformJsRuntime.desktop.kt` executes JS on `Dispatchers.Default` without blocking the caller's main thread (`SC-004`).
- `LlmTextAssembler` incrementally extracts chunk deltas in $O(1)$ per SSE chunk.
- `KtorApiClient.toResponseDefinition` computes UTF-8 byte size without allocating a duplicate `ByteArray` and skips full-body JSON parsing when responses exceed 512 KB or lack LLM keys.
- `NettyDynamicGrpcClient` caches parsed `FileDescriptorSet` instances by `(protoFilePaths, lastModifiedMillis)` and pools active `ManagedChannel` instances by `(host, port, useTls)`.
- `GraphQlRequestExecutor` applies per-tab `graphQlFollowRedirects`, `graphQlTimeout`, and `graphQlMaxRedirects` overrides.

---

### 3. Phase 3 Validation (Virtualized Raw View, Editor Indexing, Per-Tab `testResults` & Bounded Buffers)

Run the editor UI, shared UI, desktop UI, and end-to-end QA test suites:

```bash
rtk ./gradlew :editor-ui:desktopTest :ui-shared:desktopTest :ui-desktop:desktopTest :qa-tests:test
```

**Expected Outcomes**:
- `ResponseRawView` renders lines via a virtualized `LazyColumn` (`SC-004`).
- `EditorRenderer` pre-indexes `foldableStartLines` and `diagnosticsByLine` outside the `LazyColumn` item block.
- Each `RequestTabState` isolates its own `testResults` across tab switches (`SC-005`).
- `consoleLogs`, `networkEventLogs`, `historyItems`, `webSocketLogMessages`, and `streamChunks` enforce their configured ring-buffer capacity limits.
- All existing and new automated tests pass with exit code `0` (`SC-006`).
