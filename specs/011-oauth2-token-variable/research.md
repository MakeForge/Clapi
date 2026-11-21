# Research: Dynamic Runtime Variables & OAuth2 Token Resolution

**Feature**: Dynamic OAuth2 Access Token Variable (`011-oauth2-token-variable`)

**Date**: 2026-11-09

---

## 1. Dynamic Runtime Variable Architecture

### Context
Clapi currently supports:
1. Static variables across Environment, Collection, and Folder layers (`{{baseUrl}}`, `{{userId}}`).
2. Synchronous dynamic inline functions in `VariableResolver` (`{{$timestamp}}`, `{{$randomInt}}`).
3. Asynchronous external secret variables (`{{_secret.key}}`) resolved via `SecretResolutionService` from HashiCorp Vault.

We now require support for special runtime variables starting with `{{_auth.oauth2.accessToken}}` across both HTTP and gRPC requests, with an extensible API allowing additional runtime variables to be plugged in effortlessly.

### Decision
Introduce a provider-based dynamic runtime variable resolution framework in `core-network/src/commonMain/kotlin/io/github/makeforge/clapi/core/network/dynamicvar/`:
- `DynamicVariableProvider`: An interface declaring variable detection (`supports(token: String)`), token extraction, and asynchronous resolution (`resolve(...)`).
- `DynamicVariableContext`: Encapsulates request execution context, effective authentication configuration, active variable layers, cached token state, token update callbacks, and logging.
- `OAuth2DynamicVariableProvider`: Implements `DynamicVariableProvider` for `_auth.oauth2.accessToken`.
- `DynamicVariableRegistry`: Central registry managing active providers and orchestrating pre-execution variable resolution for requests.

### Rationale
- **Single Responsibility & Open/Closed Principle**: Future variables (such as `_auth.oauth2.idToken`, `_auth.oauth2.refreshToken`, AWS SigV4 dynamic tokens, or dynamic cryptographic signatures) can be implemented as independent `DynamicVariableProvider` classes without modifying `RequestExecutor.kt` or core dispatch logic.
- **Protocol Agnostic**: Resolves variables into a key-value dictionary (`Map<String, String>`) that is placed at the highest priority in `variableLayers` / `requestScopedVars`, allowing existing `VariableResolver` interpolation to populate HTTP URLs, query parameters, headers, bodies, gRPC endpoints, metadata, and protobuf JSON payloads identically.

### Alternatives Considered
- **Ad-hoc string replacement in `RequestExecutor.kt`**: Adding hardcoded `if (text.contains("{{_auth.oauth2.accessToken}}"))` in HTTP and gRPC branches.
  - *Rejected*: Violates extensibility requirements, causes code duplication across HTTP and gRPC execution paths, and makes adding future dynamic variables error-prone.
- **Coupling with `SecretResolutionService`**:
  - *Rejected*: Vault secrets require Vault server configurations and KV paths; OAuth2 requires RFC 6749 grant endpoints, client credentials, and token refresh lifecycles. Conflating them would degrade domain boundaries.

---

## 2. Integration with Request Execution Pipeline (HTTP & gRPC)

### Context
HTTP requests currently execute `resolveExternalSecretsForTab` followed by `ensureValidOAuth2Token` (which injects the `Authorization: Bearer <token>` header).
gRPC requests currently execute `resolveExternalSecretsForTab` and call `resolveGrpcCallRequest(...)`, but lacked OAuth2 validation and token resolution.

### Decision
Standardize pre-execution dynamic variable resolution across both request execution pipelines:
1. **Extraction**: Inspect all request text fields (`effectiveUrl`, headers/metadata, parameters, body, auth parameters) for variable patterns matching `\{\{\s*([^{}]+?)\s*\}\}`.
2. **Provider Dispatch**: For any matched token supported by a registered `DynamicVariableProvider` (e.g. `_auth.oauth2.accessToken`), dispatch resolution to `DynamicVariableRegistry.resolveDynamicVariables(...)`.
3. **Execution Context**: Pass a `DynamicVariableContext` containing:
   - Effective auth configuration (resolved via `state.resolveInheritedAuth(tab)` or tab's direct auth).
   - `OAuth2TokenState` and `OAuth2TokenClient`.
   - Update callback `onOAuth2TokenUpdated` to persist fresh access/refresh tokens and expiration timestamps to `tab`.
4. **Resolution Injection**:
   - Merge resolved dynamic variables into the scoped variable map alongside external secrets (`mergedScopedVars = requestScopedScriptVars + resolvedSecrets + resolvedDynamicVariables`).
   - For HTTP: `variableLayersWithSecretsAndDynamic = listOf(resolvedDynamicVariables, resolvedSecrets) + baseLayers`.
   - For gRPC: `resolveGrpcCallRequest` consumes `mergedScopedVars` as its top-priority layer, ensuring `authorization: Bearer {{_auth.oauth2.accessToken}}` is interpolated automatically when resolving metadata entries.
5. **Fast-Fail on Error**:
   - If `{{_auth.oauth2.accessToken}}` is referenced but OAuth2 is not configured/enabled, or if token acquisition fails, abort execution immediately with a clear error message in `tab.lastError` and log without dispatching network calls.

### Rationale
- Completely unified variable injection model.
- Solves gRPC OAuth2 token resolution without needing a custom gRPC interceptor specifically for OAuth2, as gRPC metadata interpolation already leverages `VariableResolver`.
- Automatically keeps `tab.authOAuth2AccessToken` synchronized for the user interface.

---

## 3. UI Discoverability: GrpcEditorTab and Autocomplete

### Context
Users editing HTTP requests have an "Auth" tab to configure OAuth2. gRPC requests currently have "Message", "Headers", and "Scripts" tabs, meaning users could only configure auth on the parent collection or folder.
Additionally, users need autocomplete suggestions and syntax highlighting when typing `{{_auth.` in any request field.

### Decision
1. **gRPC Auth Tab**:
   - Add `AUTH("Auth")` to `GrpcEditorTab` in `AppState.kt`.
   - In `GrpcRequestEditor.kt`, include the Auth tab in `GrpcEditorTabBar` with the `ic_lock` icon, rendering `AuthEditor(tab = tab, state = state)`.
   - Allows users to configure OAuth2 directly on gRPC requests or choose "Inherit Auth from Parent".
2. **Autocomplete & Highlighting in `VariableHighlight.kt`**:
   - In `getAvailableVariables(...)`: If OAuth2 is enabled on the tab or inherited from parent, include `_auth.oauth2.accessToken` with description `"OAuth2 Access Token"`.
   - In `highlightVariablesWithStatus(...)`: Include `_auth.oauth2.accessToken` in `definedNames` when OAuth2 is configured, preventing it from rendering as an unknown/missing variable.
   - In `VariableDropdownList(...)`: Render a distinct `"AUTH"` badge for variables with prefix `_auth.` (styled similarly to `"SECRET"` for `_secret.`).

### Rationale
Provides a consistent, intuitive developer experience across both HTTP and gRPC request types.

---

## 4. Testing Strategy

### Context
User requirement:
"Ensure there is a test for a grpc request and set e.g. 'authorization' header with the new special variable and check its correctly resolved when sending the request"
"Design the api in a way that it will be easy to add further special variables with dynamic runtime values later for usage in requests"

### Decision
Implement multi-level testing:
1. **Unit Tests (`core-network`)**:
   - `DynamicVariableRegistryTest`: Test registering custom dynamic variable providers, prefix matching, asynchronous token resolution, and error handling.
   - `OAuth2DynamicVariableProviderTest`: Test token resolution with valid cached token, auto-refresh on expiration, error when OAuth2 is unconfigured, and error when token fetch fails.
2. **Component & Pipeline Tests (`ui-shared`)**:
   - `DynamicVariableExecutionPipelineTest`: Test HTTP and gRPC request execution with `{{_auth.oauth2.accessToken}}`.
   - Verify gRPC metadata `authorization` header is resolved to `Bearer <access_token>` when sending the request.
   - Verify execution aborts cleanly with descriptive error if OAuth2 is disabled.
3. **End-to-End Integration Tests (`qa-tests`)**:
   - Extend `GrpcApiE2ETest` to execute an actual gRPC call against `DummyApiServer` with metadata `authorization: Bearer {{_auth.oauth2.accessToken}}` and assert that `_requestsLog.value.headers["authorization"]` receives the resolved token.
