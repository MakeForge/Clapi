# Feature Specification: Unified Request Execution Pipeline, Variable Resolution & Secret Architecture Refactoring

**Feature Branch**: `019-unify-request-execution`

**Created**: 2026-11-13

**Status**: Draft

**Input**: User description: "the recommended proposals of @[scratch/http-request-flow-evaluation.md]"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Unified Multi-Protocol Request Execution Pipeline & Module Consolidation (Priority: P1)

Users and developers executing HTTP, GraphQL, and gRPC requests need a single, consistent execution pipeline shared between the desktop UI and automated integration tests, eliminating the split between `:ui-shared` executors and the unused `:feature-requests` submodule:
1. The standalone `:feature-requests` Gradle submodule is dissolved into `:core-network` (or consolidated into the core execution layer) so there is no dead/parallel module boundary, and `:ui-shared` delegates request preparation and execution orchestration to this shared pipeline.
2. HTTP, GraphQL, and gRPC request executions share a unified lifecycle:
   - Pre-request script chain execution (Root-to-Leaf: Collection → Ancestor Folders → Request)
   - Variable layer assembly and request-scoped script variable overlay
   - Template token extraction across all request fields (URL, headers, query parameters, metadata, authentication fields, and all body variants)
   - External secret and secret environment variable resolution
   - Dynamic variable resolution (such as `{{_auth.oauth2.accessToken}}` and built-in dynamic tokens)
   - Automatic OAuth2 token validation, refresh, and primary grant acquisition
   - Protocol transport dispatch with configured retry policies (including GraphQL honoring tab retry settings and single-pass variable interpolation for GraphQL `GET` requests)
   - Post-request test script chain execution (Leaf-to-Root: Request → Ancestor Folders → Collection)
   - Chained request execution (`setNextRequest`) across HTTP, GraphQL, and gRPC requests up to the maximum chain depth limit.
3. For gRPC requests in the UI, configured authentication (including `INHERIT`, `BEARER`, `JWT`, `BASIC`, `API_KEY`, and `OAUTH2`) is resolved and applied to outbound gRPC metadata headers just as it is for HTTP and GraphQL.

**Why this priority**: Currently, `:ui-shared` and `:feature-requests` implement two divergent execution engines. Consolidating them into one shared pipeline fixes protocol feature skew (missing GraphQL retries, double-interpolation on GraphQL GET, missing gRPC auth in UI, missing `setNextRequest` in GraphQL/gRPC) and ensures integration tests exercise the exact same code path used in production.

**Independent Test**: Execute HTTP, GraphQL (both `GET` and `POST` with retry enabled), and gRPC (with inherited Bearer/OAuth2/API Key auth) requests through the unified pipeline and verify that pre-request scripts, secret resolution, auth header injection, retry policies, post-request assertions, and `setNextRequest` chaining behave identically across protocols and in `qa-tests`.

**Acceptance Scenarios**:

1. **Given** the project build configuration, **When** inspecting Gradle modules and dependencies, **Then** the redundant `:feature-requests` submodule is removed (its adapter and execution contracts consolidated into `:core-network`), and both `:ui-shared` and `:qa-tests` execute requests through the same unified pipeline.
2. **Given** a GraphQL `GET` request containing a variable `{{queryVar}}` whose value contains literal `{{nested}}` text and tab retry enabled, **When** the request is sent, **Then** variables are interpolated exactly once without secondary template expansion and the configured retry policy is honored on failure.
3. **Given** a gRPC request configured with `AuthType.BEARER`, `AuthType.API_KEY`, `AuthType.OAUTH2`, or `AuthType.INHERIT` from a parent folder/collection, **When** the user sends the gRPC request from the UI, **Then** the resolved authentication credentials are automatically injected into the outbound gRPC call metadata.
4. **Given** a post-request script in a GraphQL or gRPC request that calls `clapi.execution.setNextRequest("Target Request")`, **When** the request completes, **Then** Clapi opens and executes `"Target Request"` up to the chain depth limit, matching HTTP behavior.

---

### User Story 2 - Accurate Variable Precedence, Script Scoping & Secret Shadowing Prevention (Priority: P1)

Users working with request-scoped script variables, collection variables across multiple collections, and secret environment variables need deterministic variable resolution without silent drops or empty-string shadowing:
1. Variables set via `clapi.variables.set(key, value)` in pre-request scripts (`requestScopedScriptVars`) are included in the highest-precedence variable layer sent over the wire for HTTP, GraphQL, and gRPC requests—matching the resolved URL shown in the network console log.
2. Environment variables marked as `secret: true` (which hold an empty placeholder value `""` in UI memory) do NOT shadow lower-priority variable layers (Folder, Collection, Global) with an empty string, and are resolved before or during script and request execution from the process environment (`System.getenv`) or matching external secrets.
3. Pre-request scripts have access to resolved secret variables in their execution context, and any new `{{_secret.*}}` or dynamic variable tokens introduced by pre-request script mutations (e.g., mutating URL, headers, or body) are also scanned and resolved before transport dispatch.
4. Runtime collection variable mutations from scripts (`clapi.collectionVariables.set(key, value)`) are scoped to the active request's root collection rather than a single global map shared across all collections, and are unified with the root collection's variable entries.
5. Fallback secret credential lookups for Vault and AWS in request variable resolution use the canonical `"settings.*"` storage keys matching `SettingsRepository`, rather than mismatched `"clapi.settings.*"` keys.

**Why this priority**: Fixes critical data-correctness bugs where `clapi.variables.set()` values appeared in logs but were never sent on the wire, `secret: true` variables silently resolved to `""` in scripts, collection script variables bled across unrelated collections, and Vault/AWS fallback credential keys mismatched `SettingsRepository`.

**Independent Test**: Run a request where a pre-request script sets `clapi.variables.set("dynamicHeader", "val-123")`, reads a `secret: true` variable, and sets `clapi.collectionVariables.set("colKey", "colVal")`. Verify the wire request receives `val-123`, the secret variable resolves to its actual secret value instead of `""`, and another collection in the workspace does not see `"colVal"`.

**Acceptance Scenarios**:

1. **Given** a pre-request script that calls `clapi.variables.set("token", "req-scoped-123")` and a request using `{{token}}` in its URL, headers, query params, or body, **When** the HTTP or GraphQL request is dispatched, **Then** the wire request transmits `"req-scoped-123"` in all those locations.
2. **Given** an active environment with a `secret: true` variable `API_SECRET` (resolvable from process env or external secrets) and a folder variable `FALLBACK_VAR`, **When** a pre-request script or request references `{{API_SECRET}}`, **Then** it resolves to the actual secret value rather than `""`.
3. **Given** two collections `Collection A` and `Collection B` in the workspace, **When** a script in `Collection A` calls `clapi.collectionVariables.set("sharedKey", "fromA")`, **Then** requests in `Collection A` resolve `{{sharedKey}}` to `"fromA"`, while requests in `Collection B` do not inherit `"fromA"`.
4. **Given** Vault token or AWS secret credentials persisted via `SettingsRepository` under `"settings.vaultToken"` / `"settings.awsSecretAccessKey"`, **When** secret resolution reads the credentials, **Then** it retrieves them from the canonical `"settings.*"` keys without prefix mismatch.

---

### User Story 3 - Pluggable External Secret Provider Registry & Context Encapsulation (Priority: P2)

Developers maintaining and extending external secret managers (HashiCorp Vault, KeePassXC, AWS Secrets Manager, Azure Key Vault, GCP Secret Manager, Keeper Security Cloud) need a modular provider registry instead of 27-parameter methods and a monolithic service class:
1. `SecretResolutionService` delegates provider-specific resolution to dedicated `ExternalSecretProvider` implementations registered in a provider registry, mirroring `DynamicVariableRegistry`.
2. The 25–27 parameter signatures on `resolveAllSecrets`, `resolveSecrets`, `executeWithSecrets`, and `executeGrpcWithSecrets` are replaced with structured configuration/context models (`SecretResolutionContext` / `SecretProvidersConfig`).
3. Custom filters across all secret providers interpolate `{{variable}}` tokens using the standard `VariableResolver.resolve(...)` engine against active variable layers (supporting whitespace inside `{{ var }}` and built-in dynamic tokens) instead of ad-hoc `String.replace("{{$vk}}", vv)`.
4. Template token scanning (`extractTemplateStrings`) is performed directly on the canonical prepared request model (`RequestDefinition` / `GrpcCallRequest`), automatically covering URL, header keys and values, query parameter keys and values, cookie values, all authentication parameters, and all body types (`RAW`, `JSON`, `FORM_DATA`, `X_WWW_FORM_URLENCODED`, `GRAPHQL`) without fragile 55-line manual field lists in each caller.

**Why this priority**: Eliminates the "Tramp Data" (27-parameter functions), "God Class" (937-line `SecretResolutionService`), and "Shotgun Surgery" (`textsToScan` manual lists that missed auth and form body fields in headless execution) code smells.

**Independent Test**: Resolve secrets across all 6 providers (Vault, KeePassXC, AWS, Azure, GCP, Keeper) using `SecretResolutionContext`, including secrets referenced inside Basic/Bearer/OAuth2 auth fields, multipart form rows, and custom filter expressions containing `{{ spaced_var }}`.

**Acceptance Scenarios**:

1. **Given** `SecretResolutionService`, **When** resolving external secrets for any of the 6 supported providers, **Then** it dispatches to the registered `ExternalSecretProvider` for that `ExternalSecretType` using a single `SecretResolutionContext` object.
2. **Given** a `RequestDefinition` with `{{_secret.api_key}}` inside `auth.params["token"]` or `body.formEntries`, **When** template strings are extracted for secret/dynamic variable resolution, **Then** `RequestDefinition.extractTemplateStrings()` automatically includes those tokens without manual per-field listing in the caller.
3. **Given** an external secret with a custom filter `env = {{ target_env }}` (with spaces inside braces), **When** secret resolution interpolates custom filters, **Then** `VariableResolver` resolves `{{ target_env }}` accurately.

---

### User Story 4 - Shared OAuth2 Token Lifecycle Manager (Priority: P2)

Users authenticating requests via OAuth2 (either through the request Auth tab or via the `{{_auth.oauth2.accessToken}}` dynamic variable) need a single, authoritative token acquisition and refresh implementation:
1. The duplicated OAuth2 token validation, refresh-token grant, and fallback primary grant (`client_credentials` / `password`) logic in `RequestPayloadBuilder.ensureValidOAuth2Token` and `OAuth2DynamicVariableProvider.resolve` is unified into a single `OAuth2TokenManager` in `:core-network`.
2. Both `OAuth2DynamicVariableProvider` and the request execution pipeline delegate to `OAuth2TokenManager`, ensuring consistent variable interpolation on OAuth2 config fields, identical log messages, and a single token acquisition network call even when a request uses both `AuthType.OAUTH2` and `{{_auth.oauth2.accessToken}}`.

**Why this priority**: Removes ~120 lines of duplicated OAuth2 state-machine code between `:ui-shared` and `:core-network` and prevents redundant token fetches or divergent error handling.

**Independent Test**: Execute an OAuth2-authenticated request with an expired access token and a valid refresh token (and a fallback client_credentials config), both via `AuthType.OAUTH2` and `{{_auth.oauth2.accessToken}}`, verifying both paths invoke the shared `OAuth2TokenManager` and update tab token state identically.

**Acceptance Scenarios**:

1. **Given** a request with `AuthType.OAUTH2` (or `AuthType.INHERIT` resolving to OAuth2) and an expired token, **When** executed, **Then** the shared `OAuth2TokenManager` refreshes or re-acquires the token and updates the tab state.
2. **Given** a request that both enables `AuthType.OAUTH2` and references `{{_auth.oauth2.accessToken}}` in a custom header, **When** executed with an expired token, **Then** the token is refreshed once and the updated access token is used in both places.

---

### User Story 5 - Managed Network Client Lifecycle & Safe Script Sub-Request Serialization (Priority: P3)

Users running many requests or scripts with `clapi.sendRequest()` during a desktop session need bounded resource usage without leaking HTTP thread pools or failing on special characters in sub-request responses:
1. `NetworkClientFactory` and `KtorApiClient` properly manage underlying `HttpClient` lifecycles (either reusing a shared/cached client per settings configuration or closing transient clients upon request completion), and `subReqClient` for `clapi.sendRequest()` is instantiated lazily only when a script actually invokes `clapi.sendRequest()`.
2. The legacy hardcoded `"hello"` WebSocket echo stub inside `KtorApiClient` is removed (or isolated to test fixtures), keeping all production WebSocket traffic in `WebSocketClientManager`.
3. `ClapiScriptEngine` serializes sub-request response bodies and status strings into JavaScript callback evaluations using standard JSON string encoding rather than partial manual `.replace()` escaping, safely handling control characters (`\t`, `\b`, `\f`) and Unicode line/paragraph separators (`\u2028`, `\u2029`).

**Why this priority**: Prevents CIO selector/thread leaks during long desktop sessions, avoids allocating unused HTTP clients when no `clapi.sendRequest()` is called, and hardens `clapi.sendRequest()` callback evaluation against malformed JS syntax.

**Independent Test**: Execute 50 sequential requests and verify no orphaned `HttpClient` selector threads accumulate; execute a pre-request script calling `clapi.sendRequest()` against an endpoint returning JSON with tabs, newlines, and `\u2028` characters, and verify the JS callback parses the response without syntax errors.

**Acceptance Scenarios**:

1. **Given** a request without `clapi.sendRequest()` calls in its scripts, **When** the request is executed, **Then** no extra sub-request `HttpClient` instance is allocated, and any per-request transport resources are cleanly released or pooled when the request finishes.
2. **Given** a script calling `clapi.sendRequest()` whose response body contains `\t`, quotes, backslashes, and `\u2028`, **When** the callback executes in `ClapiScriptEngine`, **Then** `resp.text()` and `resp.json()` return the exact payload without JS evaluation errors.

---

### Edge Cases

- **Script-mutated secret references**: A pre-request script dynamically sets a header `X-Vault-Secret: {{_secret.dynamic_key}}` via `clapi.request.headers.add(...)`. Because template token extraction runs on the post-script prepared request, `_secret.dynamic_key` is still detected and resolved before the HTTP call is dispatched.
- **Unresolved secret variables (`secret: true`)**: If a `secret: true` environment variable is referenced in a request or script, and is neither present in `System.getenv` nor defined in the active environment's `externalSecrets`, execution fails fast with a clear error message rather than silently sending `""`.
- **Unreferenced `secret: true` variables**: If an environment defines a `secret: true` variable that is *not* referenced anywhere in the current request or its scripts, the request succeeds without throwing an error for the unused secret variable.
- **Request cancellation mid-flight**: Cancelling an in-flight HTTP, GraphQL, or gRPC request during secret resolution, OAuth2 token refresh, or network streaming cleanly resets `tab.isLoading = false` and `tab.currentJob = null` without logging a false failure error.
- **Circular or deep `setNextRequest` chains**: When post-request scripts chain across HTTP, GraphQL, and gRPC requests, the chain stops at `MAX_SCRIPT_CHAIN_DEPTH` (16) and logs a warning.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST consolidate the `:feature-requests` module into `:core-network` (removing the standalone `:feature-requests` Gradle module) and route both `:ui-shared` request execution and `:qa-tests` integration tests through the unified execution classes.
- **FR-002**: System MUST provide a unified execution pipeline supporting HTTP, GraphQL, and gRPC requests that executes: (1) pre-request scripts (root-to-leaf), (2) variable & secret resolution, (3) dynamic variable & OAuth2 token resolution, (4) transport dispatch with retry policy, (5) post-request scripts (leaf-to-root), and (6) `setNextRequest` chaining.
- **FR-003**: System MUST include request-scoped variables set by pre-request scripts (`clapi.variables.set`) in the highest-precedence variable layer during transport dispatch for HTTP, GraphQL, and gRPC requests.
- **FR-004**: System MUST prevent environment variables marked with `secret: true` (which have empty in-memory UI values) from shadowing lower-precedence variable layers with `""`, and MUST resolve referenced `secret: true` variables from process environment or external secrets prior to script and request execution.
- **FR-005**: System MUST scope collection variables modified by scripts (`clapi.collectionVariables.set`) to the target collection rather than a single global map across all collections.
- **FR-006**: System MUST use consistent `"settings.*"` storage keys when reading secret manager credentials from `SettingsRepository` / `PlatformStorage`.
- **FR-007**: System MUST apply configured request/inherited authentication (`BASIC`, `BEARER`, `JWT`, `API_KEY`, `OAUTH2`, `INHERIT`) to gRPC request metadata when executing gRPC requests from the UI.
- **FR-008**: System MUST honor tab retry configuration for GraphQL requests and perform single-pass variable interpolation on GraphQL `GET` query/variables parameters.
- **FR-009**: System MUST refactor `SecretResolutionService` to use a pluggable `ExternalSecretProvider` strategy interface and a consolidated `SecretResolutionContext` / `SecretProvidersConfig` model, eliminating the 25+ parameter method signatures.
- **FR-010**: System MUST use `VariableResolver.resolve` for interpolating variables in external secret `customFilters` across all providers.
- **FR-011**: System MUST provide a unified template token extraction mechanism on prepared requests (`RequestDefinition` / `GrpcCallRequest`) that scans URL, header keys/values, query param keys/values, cookies, auth parameters, and all body types.
- **FR-012**: System MUST deduplicate OAuth2 token validation, refresh, and grant acquisition into a single shared `OAuth2TokenManager` in `:core-network` used by both `OAuth2DynamicVariableProvider` and request authentication.
- **FR-013**: System MUST manage `HttpClient` lifecycles in `NetworkClientFactory` / executors so that client engines are properly closed or reused, and `subReqClient` is lazily created only when `clapi.sendRequest()` is invoked.
- **FR-014**: System MUST use standard JSON string serialization when embedding sub-request responses into JS callback scripts in `ClapiScriptEngine`.

### Key Entities

- **`RequestExecutionPipeline` / `RequestExecutionService`**: Unified orchestrator in `:core-network` (and/or `:ui-shared` adapter) coordinating scripts, variable layers, secrets, dynamic variables, OAuth2 tokens, and transport execution across HTTP, GraphQL, and gRPC.
- **`SecretResolutionContext` & `ExternalSecretProvider`**: Encapsulated configuration data model and provider strategy contract for resolving secrets from HashiCorp Vault, KeePassXC, AWS Secrets Manager, Azure Key Vault, GCP Secret Manager, and Keeper Security Cloud.
- **`OAuth2TokenManager`**: Single service in `:core-network` responsible for checking token validity, refreshing expired tokens, and falling back to configured OAuth2 grant flows.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of HTTP, GraphQL, and gRPC requests executed from the UI and from `qa-tests` flow through the unified execution pipeline with zero references to a separate `:feature-requests` module.
- **SC-002**: 100% of pre-request script variables (`clapi.variables.set`), collection variables (`clapi.collectionVariables.set`), and `secret: true` environment variables resolve to their expected values in both scripts and outbound wire payloads across all protocols.
- **SC-003**: Adding or invoking an external secret provider requires passing a single `SecretResolutionContext` object (reducing secret resolution method parameter counts from 25–27 parameters to <= 5 parameters).
- **SC-004**: OAuth2 token refresh and acquisition logic exists in exactly one place in `:core-network`, eliminating duplicated OAuth2 grant code in `:ui-shared`.
- **SC-005**: Executing 100 consecutive requests without `clapi.sendRequest()` allocates zero unused sub-request HTTP clients and leaves zero leaked/unclosed per-request `HttpClient` pools after execution completes.
- **SC-006**: All existing and new unit, integration, and E2E tests in `:core-network`, `:core-scripting`, `:ui-shared`, and `:qa-tests` pass 100%.

## Assumptions

- Dissolving `:feature-requests` into `:core-network` (Proposal 1, Option A from the evaluation report) is preferred because Clapi is a single-target desktop application where all `core-*` modules are packaged together and `:core-network` already owns `ApiClient`, `DynamicGrpcClient`, `SecretResolutionService`, `DynamicVariableRegistry`, and `OAuth2TokenClient`.
- WebSocket connections (`WebSocketRequestExecutor`) remain managed by `WebSocketClientManager` as persistent bi-directional sessions, though their initial handshake URL/headers/auth variable resolution can benefit from shared auth/variable helpers.
