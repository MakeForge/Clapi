# Phase 1 Data Model: Audit Performance & Architecture Refactoring

**Feature**: `023-audit-performance-refactoring`  
**Date**: 2026-11-14

---

## 1. Shared Network & gRPC Client Pooling Models

### 1.1 `NetworkClientPoolKey` (`ui-shared` / `desktopMain`)
Represents the immutable configuration key used to pool and reuse configured `KtorApiClient` instances across requests.

| Field | Type | Description |
|---|---|---|
| `followRedirects` | `Boolean` | Whether HTTP 3xx redirects are automatically followed |
| `maxRedirects` | `Int` | Maximum redirect hops allowed |
| `requestTimeoutSec` | `Int` | Request/connect/socket timeout in seconds (`0` = infinite) |
| `httpProxy` | `String` | Configured HTTP proxy URL (trimmed) |
| `httpsProxy` | `String` | Configured HTTPS proxy URL (trimmed) |
| `noProxy` | `String` | Comma-separated bypass hosts |

**Lifecycle & Validation Rules**:
- Pooled clients are wrapped in a `NonClosingApiClient` (or managed by the pool) so `apiClient.close()` in `RequestExecutor`'s `finally` block only closes one-off custom test clients (`apiClientProvider != null`) while keeping pooled production clients alive for connection reuse.
- When `AppState` is disposed or the pool exceeds `MAX_POOL_SIZE = 8`, the oldest entry is evicted and closed.

---

### 1.2 `SharedExecutionClients` (`ui-shared`)
Singleton/session-scoped holder providing shared default clients for request executors and variable resolution so default parameters never allocate unclosed `HttpClient(CIO)` engines.

| Field | Type | Description |
|---|---|---|
| `sharedHttpClient` | `HttpClient` | Shared Ktor `HttpClient` configured with JSON content negotiation |
| `oauth2TokenClient` | `OAuth2TokenClient` | Shared `KtorOAuth2TokenClient(sharedHttpClient)` |
| `vaultClient` | `HashiCorpVaultClient` | Shared `KtorHashiCorpVaultClient(sharedHttpClient)` |
| `awsSecretsClient` | `AwsSecretsManagerClient` | Shared `KtorAwsSecretsManagerClient(sharedHttpClient)` |
| `gcpSecretsClient` | `GcpSecretManagerClient` | Shared `KtorGcpSecretManagerClient(sharedHttpClient)` |
| `azureKeyVaultClient` | `AzureKeyVaultClient` | Shared `KtorAzureKeyVaultClient(sharedHttpClient)` |

---

### 1.3 `ProtoDescriptorCacheKey` & `GrpcChannelKey` (`core-network` / `desktopMain`)
Keys used by `NettyDynamicGrpcClient` to avoid re-running `protoc` and rebuilding HTTP/2 channels on every unary gRPC request.

| Entity | Fields | Description |
|---|---|---|
| `ProtoDescriptorCacheKey` | `paths: List<String>`, `lastModifiedMillis: List<Long>` | Cache key for compiled `DescriptorProtos.FileDescriptorSet` |
| `GrpcChannelKey` | `host: String`, `port: Int`, `useTls: Boolean` | Cache key for reusable gRPC `ManagedChannel` instances |

**Validation Rules**:
- If any `.proto` file's `File.lastModified()` timestamp changes on disk, `ProtoDescriptorCacheKey` misses and triggers a fresh `protoParser.parse(paths)`.
- Cached `ManagedChannel` instances that have entered `isShutdown` or `isTerminated` state are automatically replaced on lookup.

---

## 2. Tab State, Bounded Buffers & Secret Redaction Models

### 2.1 `RequestTabState` Additions (`ui-shared`)

| Property | Type | Default | Description |
|---|---|---|---|
| `testResults` | `SnapshotStateList<TestResult>` | `mutableStateListOf()` | Tab-scoped post-response script assertion results (`FR-015`) |
| `currentJob` | `Job?` | `null` | Active execution coroutine job; explicitly cancelled on `closeTab` / `closeTabsByIds` (`FR-005`) |

**State Transitions**:
- `Idle` -> `Loading` (`isLoading = true`, `currentJob` assigned, `tab.testResults.clear()`).
- `Loading` -> `Completed` (`isLoading = false`, `tab.response` populated, `tab.testResults` populated with post-response script results).
- `Loading` -> `Cancelled / Closed` (`currentJob.cancel()`, `currentJob = null`, `isLoading = false`; no downstream state mutations or `setNextRequest` transitions permitted).

---

### 2.2 Bounded Buffer Capacities (`FR-016`)

| Collection | Owner | Max Capacity | Eviction Policy |
|---|---|---|---|
| `consoleLogs` | `AppState` | `1,000` entries | Drop oldest tail entries (`removeRange(MAX, size)`) on prepend |
| `networkEventLogs` | `AppState` | `1,000` entries | Drop oldest tail entries (`removeRange(MAX, size)`) on prepend |
| `historyItems` | `AppState` | `500` entries | Drop oldest tail entries (`removeRange(MAX, size)`) on prepend |
| `webSocketLogMessages` | `RequestTabState` | `1,000` entries | Drop oldest head entries when appending |
| `streamChunks` | `RequestTabState` | `5,000` entries | Drop oldest head entries when appending (while `liveStreamText` preserves assembled text) |

---

### 2.3 `SecretLogRedactor` (`ui-shared`)
Pure transformation helper that masks sensitive values before any string is appended to `networkEventLogs` or `consoleLogs`.

- **Input**:
  - `rawMessage: String`
  - `secretValues: Set<String>` (collected from `resolvedSecrets.values` and active environment/collection variables where `secret == true && enabled`)
- **Rule**:
  - Filter `secretValues` to non-blank strings with `length >= 3`, sorted by descending length (so longer secrets containing shorter secrets are masked completely first).
  - Replace all occurrences with `"••••••••"`.

---

## 3. Editor Indexing Model (`editor-ui`)

### 3.1 `EditorLineMetadataIndex`
Precomputed per-document-version lookup structures in `EditorRenderer` (`FR-014`):

| Field | Type | Complexity | Description |
|---|---|---|---|
| `foldableStartLines` | `Set<Int>` | $O(1)$ lookup per visible line | Set of `0`-based document line numbers that start a fold region spanning $\ge 1$ line |
| `diagnosticsByLine` | `Map<Int, List<InlineEditorError>>` | $O(1)$ lookup per visible line | Map from `0`-based document line (`error.line - 1`) to its inline diagnostics |
