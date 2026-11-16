# Contract: Unified Request Execution Pipeline

**Feature**: `019-unify-request-execution`  
**Modules**: `:core-network` (`io.github.makeforge.clapi.core.network.execution`) and `:ui-shared` (`io.github.makeforge.clapi.ui.shared.components`)

---

## 1. Two-Pass Execution Pipeline Sequence

Every protocol executor (HTTP, GraphQL, gRPC) MUST execute the following deterministic sequence:

```mermaid
sequenceDiagram
    participant UI as UI Executor (:ui-shared)
    participant RES as RequestExecutionService (:core-network)
    participant SRS as SecretResolutionService (:core-network)
    participant OTM as OAuth2TokenManager (:core-network)
    participant SE as ClapiScriptEngine (:core-scripting)
    participant Transport as KtorApiClient / GrpcGateway (:core-network)

    UI->>UI: 1. Collect scoped layers (Env, Folder leaf->root, Active Collection, Global)
    UI->>RES: 2. Pass 1: Resolve Env secret:true vars & pre-script secrets
    RES->>SRS: resolveEnvironmentSecrets(secretVarNames, context)
    RES->>SRS: resolveSecrets(initialRequest.extractTemplateStrings(), context)
    RES-->>UI: initialResolvedSecrets
    UI->>SE: 3. Run pre-request scripts (Folder root->leaf, then Request) with resolved secrets in ScriptContext
    Note over UI,SE: Lazy subReqClient created ONLY if clapi.sendRequest() is invoked; closed in finally
    SE-->>UI: ScriptExecutionResult (mutated URL/headers/body, requestScopedScriptVars, env/collection/global mutations)
    UI->>UI: 4. Apply pre-request script mutations to RequestDefinition / GrpcCallRequest & scoped variable maps
    UI->>RES: 5. Pass 2: Re-scan mutated request & resolve newly injected secrets + dynamic vars + OAuth2
    RES->>OTM: ensureValidOAuth2Token(authConfig) (deduplicated via Mutex)
    RES->>SRS: resolveSecrets(postScriptRequest.extractTemplateStrings(), updatedContext)
    RES-->>UI:EffectiveVariableLayers [RequestScopedScriptVars, DynamicVars, ResolvedSecrets, Env, Folders, Collection, Global]
    UI->>Transport: 6. Execute transport call (with tab RetryPolicy; single-pass VariableResolver; auto-close HttpClient)
    Transport-->>UI: ApiResponse / GrpcResponse
    UI->>SE: 7. Run post-request scripts (Folder root->leaf, then Request)
    SE-->>UI: PostScriptResult (assertions, variable mutations, setNextRequest)
    UI->>UI: 8. Apply variable mutations (scoped to active collection) & propagate setNextRequest (HTTP, GraphQL, gRPC)
```

---

## 2. Protocol Parity Matrix

All three request executors MUST satisfy the following parity contract:

| Capability | HTTP (`RequestExecutor`) | GraphQL (`GraphQlRequestExecutor`) | gRPC (`GrpcRequestExecutor`) |
|------------|--------------------------|------------------------------------|------------------------------|
| `requestScopedScriptVars` (`clapi.variables.set`) applied to wire request | REQUIRED (Layer 0) | REQUIRED (Layer 0) | REQUIRED (Layer 0) |
| Environment `secret: true` resolved before pre-request scripts | REQUIRED | REQUIRED | REQUIRED |
| Post-script re-scan for `{{_secret.*}}` / `{{_auth.*}}` / `{{$...}}` | REQUIRED | REQUIRED | REQUIRED |
| Collection variables isolated to active collection (`CollectionNode`) | REQUIRED | REQUIRED | REQUIRED |
| Respects `tab.retryEnabled` (`RetryPolicy()`) | REQUIRED | REQUIRED | N/A (gRPC transport) |
| Single-pass variable resolution (no double-resolution on GraphQL GET) | REQUIRED | REQUIRED | REQUIRED |
| Propagates `executionSetNextRequestCalled` & `executionNextRequest` | REQUIRED | REQUIRED | REQUIRED |
| Applies `tab.authType` (`Basic`, `Bearer`, `JWT`, `API Key`, `OAuth2`, `Inherit`) | REQUIRED | REQUIRED | REQUIRED (via gRPC call metadata) |
| Closes per-request `HttpClient` & lazy `subReqClient` in `finally` | REQUIRED | REQUIRED | REQUIRED (`subReqClient`) |

---

## 3. Core Service Signatures (`:core-network`)

```kotlin
package io.github.makeforge.clapi.core.network.execution

class RequestExecutionService(
    private val apiClient: ApiClient,
    private val secretResolutionService: SecretResolutionService? = null,
    private val dynamicVariableRegistry: DynamicVariableRegistry? = null,
    private val oauth2TokenManager: OAuth2TokenManager? = null,
) {
    /**
     * Executes [request] with full secret resolution, OAuth2 token validation,
     * dynamic variable evaluation, and deterministic variable layer ordering.
     */
    suspend fun executeWithContext(
        request: RequestDefinition,
        variableLayers: List<Map<String, String>> = emptyList(),
        requestScopedScriptVars: Map<String, String> = emptyMap(),
        secretContext: SecretResolutionContext = SecretResolutionContext(variableLayers = variableLayers),
        secretEnvVarNames: List<String> = emptyList(),
        retryPolicy: RetryPolicy = RetryPolicy(),
        onOAuth2TokenUpdated: ((AuthConfig) -> Unit)? = null,
    ): ExecutionResult
}
```
