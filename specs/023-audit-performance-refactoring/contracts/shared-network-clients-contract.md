# Contract: Shared Network Client Lifecycle & Connection Pooling

**Feature**: `023-audit-performance-refactoring`  
**Covers**: `FR-001`, `FR-002`, `FR-010`, `FR-011`

---

## 1. Executor Default Client Contract (`ui-shared`)

All request execution entry points (`sendRequest`, `sendGraphQlRequest`, `sendGrpcRequest`, `resolveExternalSecretsForTab`, `resolveDynamicVariablesForTab`, `buildDynamicVariableContext`) MUST NOT instantiate new `KtorOAuth2TokenClient()`, `KtorHashiCorpVaultClient()`, `KtorAwsSecretsManagerClient()`, `KtorGcpSecretManagerClient()`, or `KtorAzureKeyVaultClient()` instances in default parameter expressions.

```kotlin
object SharedExecutionClients {
    val oauth2TokenClient: OAuth2TokenClient
    val vaultClient: HashiCorpVaultClient
    val awsSecretsClient: AwsSecretsManagerClient
    val gcpSecretsClient: GcpSecretManagerClient
    val azureKeyVaultClient: AzureKeyVaultClient
}
```

### Invariants
1. Invoking `sendRequest(scope, state, tab)` with default parameters MUST allocate **0** new `HttpClient` instances for OAuth2 or secret providers.
2. Tests passing mock/fake clients explicitly via parameters continue to override the shared defaults without side effects.

---

## 2. WebSocket & MCP Client Lifecycle Contract (`core-network`)

### `KtorWebSocketClientManager`
- MUST use `SupervisorJob()` in its default `CoroutineScope` so an exception in one WebSocket session never cancels sibling sessions.
- MUST reuse a shared `HttpClient` (or close any per-session `HttpClient` in `finally` when the session terminates).
- MUST implement `close()` to cancel its internal scope and close its owned `HttpClient`.

### `McpClient`
- `disconnect()` MUST close any auxiliary `HttpClient` instances (`replyClient`, `streamClient`) created by `StreamableHttpTransport` so reconnecting does not leak `HttpClient(CIO)` engines.

---

## 3. Primary HTTP & gRPC Pooling Contract (`core-network`, `ui-shared`)

### HTTP / GraphQL Client Pool (`NetworkClientFactory.desktop.kt`)
- `createConfiguredApiClient(settings: AppSettings)` returns a pooled `ApiClient` keyed by `NetworkClientPoolKey(followRedirects, maxRedirects, requestTimeoutSec, httpProxy, httpsProxy, noProxy)`.
- Calling `.close()` on a pooled `ApiClient` from `RequestExecutor` / `GraphQlRequestExecutor`'s per-request `finally` block is a no-op so the underlying `HttpClient(CIO)` keep-alive pool remains active across requests.
- `GraphQlRequestExecutor` MUST compute `effectiveSettings` using `tab.graphQlFollowRedirects`, `tab.graphQlTimeout`, and `tab.graphQlMaxRedirects` before calling `createConfiguredApiClient(effectiveSettings)`.

### gRPC Descriptor & Channel Cache (`NettyDynamicGrpcClient.kt`)
- `FileDescriptorSet` parsing is cached by `(protoFilePaths, lastModifiedTimestamps)`.
- `ManagedChannel` instances are cached by `(host, port, useTls)` and reused across unary calls while active (`!channel.isShutdown && !channel.isTerminated`).
