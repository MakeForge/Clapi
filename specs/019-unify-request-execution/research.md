# Research & Architectural Decisions: Unify Request Execution, Variable & Secret Resolution, and Module Boundaries

**Feature**: `019-unify-request-execution`  
**Date**: 2026-11-13  
**Spec**: [spec.md](file:///home/user/IdeaProjects/Clapi/specs/019-unify-request-execution/spec.md)  
**Source Evaluation**: [http-request-flow-evaluation.md](file:///home/user/IdeaProjects/Clapi/scratch/http-request-flow-evaluation.md)

---

## Decision 1: Dissolving `:feature-requests` into `:core-network`

### Decision
Move `HttpExecutionAdapter.kt` and `RequestExecutionService.kt` (along with their unit tests `HttpExecutionAdapterTest.kt` and `RequestExecutionServiceSecretsTest.kt`) from `:feature-requests` into `:core-network`. Remove `:feature-requests` from `settings.gradle.kts`, `ui-shared/build.gradle.kts`, and `qa-tests/build.gradle.kts`, and delete the `feature-requests/` directory.

### Rationale
- `:feature-requests` contains only 2 production classes (`HttpExecutionAdapter` and `RequestExecutionService`) and 2 test files.
- `:ui-shared` declares `implementation(project(":feature-requests"))` in `ui-shared/build.gradle.kts` (`line 67`), yet has **zero imports** from `:feature-requests`. Instead, `:ui-shared` re-implements the entire execution, secret-resolution, dynamic-variable, and OAuth2 flow across `RequestExecutor.kt`, `GraphQlRequestExecutor.kt`, `GrpcRequestExecutor.kt`, and `RequestVariableResolution.kt`.
- Only `:qa-tests` imports `RequestExecutionService` (`ExecutionPipelineE2ETest.kt` and `RequestFlowBlackBoxTest.kt`), meaning QA tests were exercising a different execution orchestrator than the actual UI.
- `:core-network` currently depends only on `:core-model` (plus Ktor, Koin, and kotlinx libraries) and does **not** import anything from `:core-scripting` or `:core-storage` (`HttpExecutionAdapter` and `RequestExecutionService` only import `core.model.*` and `core.network.*`). Placing `HttpExecutionAdapter` and `RequestExecutionService` in `:core-network` preserves clean module boundaries without adding new dependencies to `:core-network`.

### Alternatives Considered
- **Expand `:feature-requests` to include UI state and scripting**: Rejected because `:ui-shared` owns `AppState` and `RequestTabState`, which would create a circular dependency between `:feature-requests` and `:ui-shared`, or require moving `RequestTabState` out of `:ui-shared`.
- **Keep `:feature-requests` as a thin wrapper**: Rejected because a 2-file module with no unique dependencies adds build overhead and split-brain maintenance burden without architectural value.

---

## Decision 2: Unified Execution Pipeline & Protocol Parity (HTTP, GraphQL, gRPC)

### Decision
Unify request execution across two cleanly separated layers:
1. **Core Transport & Resolution Layer (`:core-network` — `RequestExecutionService` + `HttpExecutionAdapter`)**:
   - Owns `RequestDefinition.extractTemplateStrings()` and `GrpcCallRequest.extractTemplateStrings()` so every field containing `{{...}}` (URL, header keys/values, query param keys/values, cookie keys/values, auth params, raw/form/multipart/GraphQL bodies, gRPC metadata/payloads) is scanned.
   - Owns two-pass secret and dynamic variable resolution via `SecretResolutionService` and `DynamicVariableRegistry`.
   - Owns OAuth2 pre-flight token validation/refresh via `OAuth2TokenManager`.
   - Owns deterministic variable layer construction and delegates transport execution to `ApiClient.execute()` (with `HttpExecutionAdapter` performing final `VariableResolver.resolve` across all `RequestDefinition` fields once).
2. **UI & Scripting Orchestration Layer (`:ui-shared` — `RequestExecutor`, `GraphQlRequestExecutor`, `GrpcRequestExecutor` sharing common pipeline helpers in `RequestExecutionPipeline.kt`)**:
   - Eliminates copy-pasted orchestration across HTTP, GraphQL, and gRPC.
   - Enforces the exact 10-step execution lifecycle:
     1. Collect scoped variable layers (`Environment`, `Folder` leaf-to-root, active `Collection`, `Global`).
     2. **Pass 1 Secret Resolution**: Resolve environment `secret: true` variables and pre-request secret tokens so pre-request scripts can read them via `clapi.environment.get(...)` / `clapi.variables.get(...)`.
     3. Execute pre-request scripts (`Folder` root-to-leaf -> `Request`) with lazy `subReqClient` creation for `clapi.sendRequest()`.
     4. Apply pre-request script mutations (`url`, `headers`, `body`, `requestScopedScriptVars`, `environment`, `collection`, `global`).
     5. **Pass 2 Secret & Dynamic Variable Resolution**: Scan the post-script request (`extractTemplateStrings()`) to resolve any newly injected `{{_secret.*}}`, `{{_auth.*}}`, or `{{$...}}` tokens, and ensure OAuth2 token validity via `OAuth2TokenManager`.
     6. Assemble final `effectiveVariableLayers` in strict precedence order:
        `RequestScopedScriptVars` (Layer 0) > `DynamicVars` (Layer 1) > `ResolvedSecrets` (Layer 2) > `Environment` (Layer 3) > `Folders leaf-to-root` (Layer 4..N) > `Active Collection` (Layer N+1) > `Global` (Layer N+2).
     7. Execute transport call (`KtorApiClient` with guaranteed `use`/`close()` lifecycle, or `GrpcGateway`) using the tab's configured `RetryPolicy` (fixing GraphQL ignoring `tab.retryEnabled`) and without pre-resolving GraphQL `GET` query params twice.
     8. Execute post-request scripts (`Folder` root-to-leaf -> `Request`).
     9. Persist mutated environment, active-collection, and global variables, and evaluate `setNextRequest` across **all** protocols (HTTP, GraphQL, gRPC).
     10. Record history and masked console logs.

### Protocol Skew Fixes Included
- **HTTP & GraphQL `requestScopedScriptVars` dropped on the wire**: `requestScopedScriptVars` (`clapi.variables.set`) is placed at Layer 0 of `effectiveVariableLayers` passed to `client.execute()`.
- **GraphQL `tab.retryEnabled` ignored**: `GraphQlRequestExecutor` uses the same retry policy helper (`if (tab.retryEnabled) RetryPolicy() else RetryPolicy(maxAttempts = 1)`) as `RequestExecutor`.
- **GraphQL `GET` double variable resolution**: `GraphQlRequestExecutor` leaves template resolution of `GET` query parameters (`query`, `variables`, `operationName`) to the single transport resolution pass in `HttpExecutionAdapter` / `KtorApiClient.buildRequest`, preventing double-interpolation when resolved values contain `{{...}}`.
- **GraphQL & gRPC ignoring `setNextRequest`**: Both `GraphQlRequestExecutor` and `GrpcRequestExecutor` propagate `postResult.executionSetNextRequestCalled` and `postResult.executionNextRequest` into `tab.lastScriptNextRequestSet` and `tab.lastScriptNextRequest`.
- **gRPC UI ignoring `tab.authType`**: `resolveGrpcCallRequest` in `GrpcRequestEditor.kt` resolves effective auth (`tab.authType` including `INHERIT`, `BASIC`, `BEARER`, `JWT`, `API_KEY`, `OAUTH2`) via `buildAuthConfig(tab, state)` and `OAuth2TokenManager`, injecting the resulting authorization header/query entry into gRPC call metadata (preserving user-defined metadata headers of the same key if explicitly overridden, or applying standard auth metadata).

---

## Decision 3: Fixing `secret: true` Shadowing & Collection Variable Global Bleed

### Decision
1. **Fix `EnvState.toVariableMap()` Empty-String Shadowing**:
   - In `OpenCollectionModelMapping.kt` (`line 369`), `SecretVariable` maps to `MutableKeyValue(key = v.name, value = "", enabled = !v.disabled, secret = true)`.
   - Update `EnvState.toVariableMap()` in `EnvironmentModels.kt` (`line 83-84`) to exclude entries where `it.secret && it.value.isBlank()`:
     ```kotlin
     fun toVariableMap(): Map<String, String> =
         variables.filter { it.enabled && (!it.secret || it.value.isNotBlank()) }
             .associate { it.key to it.value }
     ```
   - Additionally, during Pass 1 Secret Resolution (before pre-request scripts run), resolve enabled `secret: true` environment variables from the active secret provider or local secret storage and overlay their resolved values into the environment layer (`ResolvedSecrets` / `Environment`). If a secret variable has no local value and cannot be resolved from a secret provider, omitting the blank placeholder ensures lower-priority layers (folder/collection/global) are not shadowed by `""`.
2. **Scope `collectionVariables` to the Active Collection**:
   - Replace the global `AppState.collectionVariables` lookup/mutation during request execution with collection-scoped variable storage tied to the active `CollectionNode` (`CollectionNode.variableEntries` and a per-collection runtime map `collectionVariablesByCollectionId: SnapshotStateMap<String, SnapshotStateMap<String, String>>` initialized from `CollectionNode.variableEntries`).
   - Helper methods on `AppState` (`getCollectionVariables(collectionId: String?)`, `setCollectionVariable(collectionId: String?, key: String, value: String)`, `removeCollectionVariable(collectionId: String?, key: String)`, `clearCollectionVariables(collectionId: String?)`) ensure `clapi.collectionVariables.set(...)` in Collection A never bleeds into Collection B, while maintaining backward compatibility for callers when a single collection is active.

---

## Decision 4: `ExternalSecretProvider` Strategy Pattern & `SecretResolutionContext`

### Decision
Refactor `SecretResolutionService` (`core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/secrets/SecretResolutionService.kt`) to replace the 27-parameter `resolveSecrets(...)` and `resolveEnvironmentSecrets(...)` methods with:
1. **`SecretResolutionContext` data class**: Encapsulates all provider configurations/credentials (`VaultProviderConfig`, `AwsProviderConfig`, `AzureProviderConfig`, `GcpProviderConfig`, `KeeperProviderConfig`, `KeepassXcProviderConfig`), `variableLayers: List<Map<String, String>>`, and `customFilters: List<CustomSecretFilter>`.
2. **`ExternalSecretProvider` interface**:
   ```kotlin
   interface ExternalSecretProvider {
       val providerId: String // e.g., "vault", "aws", "azure", "gcp", "keeper", "keepassxc"
       fun isConfigured(context: SecretResolutionContext): Boolean
       suspend fun resolveSecret(
           secretName: String,
           fieldKey: String?,
           context: SecretResolutionContext,
       ): String?
   }
   ```
3. **Six Strategy Implementations**:
   - `VaultSecretProvider`
   - `AwsSecretProvider`
   - `AzureSecretProvider`
   - `GcpSecretProvider`
   - `KeeperSecretProvider`
   - `KeepassXcSecretProvider`
4. **Unified Custom Filter Interpolation via `VariableResolver.resolve`**:
   - Replace `var interpolated = text; for ((vk, vv) in allVars) { interpolated = interpolated.replace("{{$vk}}", vv) }` in `SecretResolutionService` with `VariableResolver.resolve(text, context.variableLayers)` so whitespace-padded tokens (`{{ myVar }}`) and nested variables resolve identically to the rest of the application.
5. **Centralized `SettingsStorageKeys` Constants**:
   - Define canonical `PlatformStorage` key constants (`SettingsStorageKeys.VAULT_TOKEN`, `SettingsStorageKeys.AWS_SECRET_ACCESS_KEY`, `SettingsStorageKeys.AWS_SESSION_TOKEN`, `SettingsStorageKeys.KEEPER_CONFIG`, `SettingsStorageKeys.KEEPASSXC_KEY`, etc.) in `:ui-shared` (`SettingsRepository.kt`) and update `RequestVariableResolution.kt` to load via `SettingsRepository` / `SettingsStorageKeys` with transparent fallback migration for legacy `"settings.*"` keys.

---

## Decision 5: Shared `OAuth2TokenManager`

### Decision
Create `OAuth2TokenManager` in `:core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/oauth2/OAuth2TokenManager.kt`:
- Consolidates the duplicated token expiry check, refresh-token flow, and auto-fetch (`client_credentials` / `password`) logic currently split between `RequestVariableResolution.kt:ensureValidOAuth2Token` (`lines 198-297`) and `OAuth2DynamicVariableProvider.kt:acquireToken` (`lines 69-134`).
- Coordinates concurrent/sequential token requests within the same execution pass using a `Mutex` and per-collection/token-key cache check so that when a request uses both `AuthType.OAUTH2` and `{{_auth.oauth2.accessToken}}` with an expired token, **only one** refresh/token network request is made.
- Injects `OAuth2TokenManager` into both `OAuth2DynamicVariableProvider` and `RequestExecutionService` (as well as UI executors).

---

## Decision 6: `HttpClient` Lifecycle Management, Dead Code Removal, and Safe JS Callback Escaping

### Decision
1. **Close Transient `KtorApiClient` / `HttpClient` Instances**:
   - Implement `AutoCloseable` (or `close()`) on `ApiClient` / `KtorApiClient` to close the underlying `HttpClient`.
   - Ensure per-request `KtorApiClient` instances created in `RequestExecutor` and `GraphQlRequestExecutor` are closed in a `finally` block (or `use {}`).
   - Instantiate `subReqClient` for `clapi.sendRequest()` **lazily** inside `sendRequestExecutor` only when a script actually calls `clapi.sendRequest()`, and close it in `finally` after the script completes.
2. **Remove Dead WebSocket Stub in `KtorApiClient`**:
   - Remove the hardcoded `if (resolvedRequest.protocol == ProtocolType.WEBSOCKET) { session.send(Frame.Text("hello")) ... }` block (`lines 62-66`) and `executeWebSocket` stub (`lines 125-146`) from `KtorApiClient.kt`. Instead, throw an explicit `UnsupportedOperationException("WebSocket sessions must be executed via WebSocketClient")` if `ProtocolType.WEBSOCKET` is passed to `KtorApiClient.execute()`.
3. **Safe JSON String Escaping in `ClapiScriptEngine`**:
   - Replace the manual 4-character `.replace(...)` chain in `ClapiScriptEngine.kt` (`lines 480-484`) with `kotlinx.serialization.json.JsonPrimitive(rawBody).toString()` (which produces a valid, fully-escaped JSON string literal handling `\t`, `\b`, `\f`, `\u2028`, `\u2029`, and control characters `0x00..0x1F`).

---

## Decision 7: Comprehensive Verification & Bug-Regression Test Strategy

### Decision
Every bug and architectural smell identified in the evaluation will have dedicated unit, integration, and E2E regression tests:

| Bug / Issue ID | Description | Target Test Suite |
|----------------|-------------|-------------------|
| **BUG-01** | `clapi.variables.set` (`requestScopedScriptVars`) dropped on the wire in HTTP & GraphQL | `RequestExecutionPipelineTest` (`:ui-shared`), `ExecutionPipelineE2ETest` (`:qa-tests`) |
| **BUG-02** | `secret: true` environment variables with `value = ""` shadowing folder/collection/global variables and returning `""` in scripts | `EnvironmentModelsTest` (`:ui-shared`), `RequestExecutionServiceSecretsTest` (`:core-network`), `ExecutionPipelineE2ETest` (`:qa-tests`) |
| **BUG-03** | Pre-request scripts running before secret resolution & post-script mutations containing `{{_secret.*}}` / `{{_auth.*}}` not re-scanned | `RequestExecutionServiceSecretsTest` (`:core-network`), `ExecutionPipelineE2ETest` (`:qa-tests`) |
| **BUG-04** | `clapi.collectionVariables.set` bleeding across all collections via global `AppState.collectionVariables` | `CollectionVariablesIsolationTest` (`:ui-shared`), `ExecutionPipelineE2ETest` (`:qa-tests`) |
| **BUG-05** | `PlatformStorage` secret key prefix mismatch (`"settings.vaultToken"` vs `"clapi.settings.vaultToken"`) | `SettingsStorageKeysTest` (`:ui-shared`) |
| **BUG-06a** | GraphQL ignoring `tab.retryEnabled` (`maxAttempts = 1` hardcoded) | `GraphQlRequestExecutorTest` (`:ui-shared`) |
| **BUG-06b** | GraphQL `GET` double variable resolution corrupting values containing `{{...}}` | `GraphQlRequestExecutorTest` (`:ui-shared`) |
| **BUG-06c** | GraphQL & gRPC post-request scripts ignoring `clapi.execution.setNextRequest` | `GraphQlRequestExecutorTest`, `GrpcRequestExecutorTest` (`:ui-shared`) |
| **BUG-06d** | gRPC UI execution ignoring `tab.authType` (`Bearer`, `Basic`, `API Key`, `OAuth2`, `Inherit`) | `GrpcRequestExecutorTest` (`:ui-shared`) |
| **BUG-07** | `RequestDefinition.extractTemplateStrings()` missing header keys, query keys, cookies, `auth.params`, form bodies, and GraphQL bodies | `RequestExecutionServiceSecretsTest` (`:core-network`) |
| **BUG-08** | Custom secret filter interpolation failing on whitespace `{{ var }}` due to naive `String.replace` | `SecretResolutionServiceTest` (`:core-network`) |
| **BUG-09** | Duplicate OAuth2 refresh calls when using both `AuthType.OAUTH2` and `{{_auth.oauth2.accessToken}}` | `OAuth2TokenManagerTest` (`:core-network`) |
| **BUG-10** | Unclosed `HttpClient` per request & eager `subReqClient` allocation when `sendRequest` is unused | `KtorApiClientLifecycleTest` (`:core-network`), `RequestExecutionPipelineTest` (`:ui-shared`) |
| **BUG-11** | `ClapiScriptEngine` `sendRequest` callback crashing on `\t`, `\u2028`, `\u2029`, or control chars in response body | `ClapiScriptEngineTest` (`:core-scripting`) |
