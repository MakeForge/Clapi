# Tasks: Unify Request Execution, Variable & Secret Resolution, and Module Boundaries

**Input**: Design documents from `/specs/019-unify-request-execution/` (`plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`)

**Prerequisites**: `plan.md` (required), `spec.md` (required for user stories), `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: Comprehensive unit, integration, and E2E regression test tasks are included for all 11 identified bugs and architectural refactorings as requested.

**Organization**: Tasks are grouped by user story (`US1`–`US5`) to enable independent implementation and testing of each increment.

## Format: `- [ ] [TaskID] [P?] [Story?] Description with file path`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (`[US1]`, `[US2]`, `[US3]`, `[US4]`, `[US5]`)
- Exact repository file paths are included in every task description

---

## Phase 1: Setup (Module Consolidation & Build Configuration)

**Purpose**: Dissolve the redundant `:feature-requests` Gradle module into `:core-network` and update module dependencies and imports across the workspace.

- [ ] T001 Move `HttpExecutionAdapter.kt` and `RequestExecutionService.kt` from `feature-requests/src/commonMain/kotlin/io/github/crustack/clapi/feature/requests/` to `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/execution/` (keeping typealiases or updating package to `io.github.crustack.clapi.core.network.execution`)
- [ ] T002 Move `HttpExecutionAdapterTest.kt` and `RequestExecutionServiceSecretsTest.kt` from `feature-requests/src/commonTest/kotlin/io/github/crustack/clapi/feature/requests/` to `core-network/src/commonTest/kotlin/io/github/crustack/clapi/core/network/execution/`
- [ ] T003 Remove `include(":feature-requests")` from `settings.gradle.kts`, remove `api(project(":feature-requests"))` from `ui-shared/build.gradle.kts`, remove `implementation(project(":feature-requests"))` from `qa-tests/build.gradle.kts`, and delete the `feature-requests/` directory
- [ ] T004 [P] Update `RequestExecutionService` imports from `io.github.crustack.clapi.feature.requests.RequestExecutionService` to `io.github.crustack.clapi.core.network.execution.RequestExecutionService` in `qa-tests/src/test/kotlin/io/github/crustack/clapi/qa/ApiClientE2ETest.kt`, `AwsSecretsManagerIntegrationTest.kt`, `AzureRequestExecutionIntegrationTest.kt`, `ExternalSecretsIntegrationTest.kt`, `GcpSecretManagerIntegrationTest.kt`, `GraphQLRequestE2ETest.kt`, `GrpcApiE2ETest.kt`, and `KeeperSecurityCloudIntegrationTest.kt`

---

## Phase 2: Foundational (Core Templates, Context Models & Storage Keys)

**Purpose**: Core models, template extractors, and storage key constants that are blocking prerequisites across user stories.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T005 [P] Create `RequestTemplateExtractor.kt` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/execution/RequestTemplateExtractor.kt` implementing `RequestDefinition.extractTemplateStrings(): List<String>` (scanning `url`, enabled `headers` `key`/`value`, enabled `queryParams` `key`/`value`, enabled `cookies` `key`/`value`, `auth.params.values`, `body.content`, enabled `body.formEntries` `key`/`value`, enabled `body.formDataEntries` `key`/`value`/`contentType`, and `body.graphQl` `query`/`variables`/`operationName`) and `GrpcCallRequest.extractTemplateStrings(): List<String>` (scanning `target`, `serviceName`, `methodName`, `payloadJson`, `payloadMessages`, and `metadata` `key`/`value`)
- [ ] T006 [P] Create `SecretResolutionContext.kt` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/vault/SecretResolutionContext.kt` defining `SecretResolutionContext` (`vault: VaultProviderConfig`, `aws: AwsProviderConfig`, `azure: AzureProviderConfig`, `gcp: GcpProviderConfig`, `keeper: KeeperProviderConfig`, `keepassXc: KeepassXcProviderConfig`, `variableLayers: List<Map<String, String>> = emptyList()`, `customFilters: List<CustomSecretFilter> = emptyList()`) with `VaultProviderConfig(mountPath: String = "secret")` and provider config data classes
- [ ] T007 [P] Create `ExternalSecretProvider` interface (`val providerId: String`, `fun isConfigured(context: SecretResolutionContext): Boolean`, `suspend fun resolveSecret(secretName: String, fieldKey: String?, context: SecretResolutionContext): String?`) in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/vault/ExternalSecretProvider.kt`
- [ ] T008 [P] Define `SettingsStorageKeys` object with canonical `"clapi.settings.*"` constants (`VAULT_TOKEN`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`, `AZURE_CLIENT_SECRET`, `GCP_SERVICE_ACCOUNT_JSON`, `GCP_ACCESS_TOKEN`, `KEEPER_CONFIG`, `KEEPASSXC_KEY`) and legacy `"settings.*"` fallback constants in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/SettingsRepository.kt`

**Checkpoint**: Foundation ready — user story implementation can now proceed.

---

## Phase 3: User Story 1 - Unified Multi-Protocol Request Execution Pipeline & Module Consolidation (Priority: P1) 🎯 MVP

**Goal**: Unify HTTP, GraphQL, and gRPC request execution so all three protocols share consistent pre/post-script execution, complete `RequestDefinition` template interpolation, GraphQL retry policy & single-pass `GET` interpolation, gRPC UI authentication metadata injection, and `setNextRequest` chaining.

**Independent Test**: Execute HTTP, GraphQL (`GET` with nested `{{...}}` text and retry enabled), and gRPC (with `AuthType.BEARER`, `AuthType.API_KEY`, `AuthType.OAUTH2`, and `AuthType.INHERIT`) requests and verify that auth metadata, retry policy, single-pass interpolation, and `setNextRequest` work identically across protocols.

### Tests for User Story 1

- [ ] T009 [P] [US1] Expand unit tests in `core-network/src/commonTest/kotlin/io/github/crustack/clapi/core/network/execution/HttpExecutionAdapterTest.kt` to verify `HttpExecutionAdapter` resolves variables in header keys, query param keys, cookies, `auth.params`, `formEntries`, `formDataEntries`, and `graphQl` body fields, and test `RequestDefinition.extractTemplateStrings()` completeness (`BUG-07`)
- [ ] T010 [P] [US1] Create unit tests in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/executor/GraphQlRequestExecutorTest.kt` verifying GraphQL requests honor `tab.retryEnabled` (`BUG-06a`), perform single-pass variable resolution on GraphQL `GET` query/variables without secondary expansion of literal `{{nested}}` values (`BUG-06b`), and propagate `clapi.execution.setNextRequest` from post-request scripts (`BUG-06c`)
- [ ] T011 [P] [US1] Create unit tests in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/executor/GrpcRequestExecutorTest.kt` verifying gRPC UI requests resolve `tab.authType` (`BASIC`, `BEARER`, `JWT`, `API_KEY`, `OAUTH2`, `INHERIT`) into outbound gRPC metadata headers (`BUG-06d`) and propagate `clapi.execution.setNextRequest` from post-request scripts (`BUG-06c`)

### Implementation for User Story 1

- [ ] T012 [US1] Update `HttpExecutionAdapter.kt` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/execution/HttpExecutionAdapter.kt` to resolve variables across all `RequestDefinition` fields (`headers` keys/values, `queryParams` keys/values, `cookies` keys/values, `auth.params`, `body.content`, `body.formEntries`, `body.formDataEntries`, and `body.graphQl`)
- [ ] T013 [US1] Create `RequestExecutionPipeline.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/executor/RequestExecutionPipeline.kt` providing shared pipeline helpers for root-to-leaf pre-request script execution, leaf-to-root post-request script execution, variable layer assembly, and `setNextRequest` state propagation across HTTP, GraphQL, and gRPC
- [ ] T014 [US1] Refactor `RequestExecutor.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/executor/RequestExecutor.kt` to delegate shared script and variable layer orchestration to `RequestExecutionPipeline`
- [ ] T015 [US1] Refactor `GraphQlRequestExecutor.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/executor/GraphQlRequestExecutor.kt` to use `RequestExecutionPipeline`, apply `if (tab.retryEnabled) RetryPolicy() else RetryPolicy(maxAttempts = 1)` (`BUG-06a`), remove redundant pre-resolution of `GET` query/variables before `KtorApiClient.execute` (`BUG-06b`), and propagate `postResult.executionSetNextRequestCalled` and `postResult.executionNextRequest` to `tab` (`BUG-06c`)
- [ ] T016 [US1] Update `resolveGrpcCallRequest` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/GrpcRequestEditor.kt` to resolve effective authentication (`tab.authType` including `INHERIT`, `BASIC`, `BEARER`, `JWT`, `API_KEY`, `OAUTH2`) via `buildAuthConfig(tab, state)` and inject the resolved authorization entry into `GrpcCallRequest.metadata` (`BUG-06d`)
- [ ] T017 [US1] Refactor `GrpcRequestExecutor.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/executor/GrpcRequestExecutor.kt` to use `RequestExecutionPipeline` and propagate `postResult.executionSetNextRequestCalled` and `postResult.executionNextRequest` to `tab` (`BUG-06c`)

**Checkpoint**: User Story 1 is complete and independently testable across HTTP, GraphQL, and gRPC.

---

## Phase 4: User Story 2 - Accurate Variable Precedence, Script Scoping & Secret Shadowing Prevention (Priority: P1)

**Goal**: Ensure `clapi.variables.set` (`requestScopedScriptVars`) is sent on the wire at Layer 0, prevent `secret: true` environment variables with blank in-memory values from shadowing lower layers with `""`, execute two-pass secret resolution before and after pre-request scripts, isolate `clapi.collectionVariables` per collection, and unify secret storage key lookups.

**Independent Test**: Execute requests with `clapi.variables.set("token", "req-123")`, an environment containing a `secret: true` variable, a pre-request script injecting `{{_secret.dynamic}}`, and two workspace collections (`Collection A` and `Collection B`) mutating collection variables; verify wire values, secret resolution, and collection isolation.

### Tests for User Story 2

- [ ] T018 [P] [US2] Create unit tests in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/state/EnvironmentModelsTest.kt` verifying `EnvState.toVariableMap()` excludes `secret = true` entries when `value.isBlank()` so lower-priority folder/collection/global variables are not shadowed by `""` (`BUG-02`)
- [ ] T019 [P] [US2] Create unit tests in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/state/CollectionVariablesIsolationTest.kt` verifying `clapi.collectionVariables.set(key, value)` in `Collection A` updates `Collection A` without bleeding into `Collection B` (`BUG-04`)
- [ ] T020 [P] [US2] Create unit tests in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/persistence/SettingsStorageKeysTest.kt` verifying `SettingsRepository` and `RequestVariableResolution` retrieve Vault and AWS credentials via canonical `SettingsStorageKeys` with fallback migration from legacy `"settings.*"` keys (`BUG-05`)
- [ ] T021 [P] [US2] Create unit tests in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/executor/RequestExecutionPipelineTest.kt` verifying `requestScopedScriptVars` (`clapi.variables.set`) is applied at Layer 0 on the wire (`BUG-01`), `secret: true` environment variables are resolved before pre-request scripts (`BUG-02`), and `{{_secret.*}}` / `{{_auth.*}}` tokens added by pre-request script mutations are resolved in Pass 2 before transport dispatch (`BUG-03`)

### Implementation for User Story 2

- [ ] T022 [P] [US2] Update `EnvState.toVariableMap()` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/EnvironmentModels.kt` to filter entries using `variables.filter { it.enabled && (!it.secret || it.value.isNotBlank()) }.associate { it.key to it.value }` (`BUG-02`)
- [ ] T023 [P] [US2] Update `AppState` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt` to store collection variables per collection ID (`collectionVariablesById`) seeded from `CollectionNode.variableEntries` and provide collection-scoped accessors/mutators (`BUG-04`)
- [ ] T024 [US2] Update `SettingsRepository.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/SettingsRepository.kt` and `RequestVariableResolution.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/executor/RequestVariableResolution.kt` to read/write secret credentials using `SettingsStorageKeys` with fallback support for legacy `"settings.*"` keys (`BUG-05`)
- [ ] T025 [US2] Update `RequestExecutionPipeline.kt`, `RequestExecutor.kt`, `GraphQlRequestExecutor.kt`, and `GrpcRequestExecutor.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/executor/` to: (1) include `requestScopedScriptVars` at Layer 0 (`index 0`) of `effectiveVariableLayers` (`BUG-01`), (2) resolve referenced `secret: true` variables in Pass 1 before pre-request scripts run (`BUG-02`), (3) re-scan post-script mutated requests via `extractTemplateStrings()` in Pass 2 to resolve newly introduced secrets and dynamic variables (`BUG-03`), and (4) read/write collection variables scoped to the active request's root collection (`BUG-04`)

**Checkpoint**: User Stories 1 and 2 are complete; all variable precedence, script variable wire delivery, secret shadowing, and collection isolation bugs are resolved.

---

## Phase 5: User Story 3 - Pluggable External Secret Provider Registry & Context Encapsulation (Priority: P2)

**Goal**: Replace 25–27 parameter secret resolution signatures with `SecretResolutionContext` and modular `ExternalSecretProvider` strategy implementations across all 6 secret backends, and use `VariableResolver.resolve` for custom filter interpolation.

**Independent Test**: Resolve secrets across Vault, AWS, Azure, GCP, Keeper, and KeePassXC via `SecretResolutionService` using `SecretResolutionContext`, including custom filters with whitespace-padded `{{ target_env }}` variables and secrets inside `auth.params` and `formEntries`.

### Tests for User Story 3

- [ ] T026 [P] [US3] Create/expand unit tests in `core-network/src/commonTest/kotlin/io/github/crustack/clapi/core/network/vault/SecretResolutionServiceTest.kt` testing `ExternalSecretProvider` dispatch for all 6 providers using `SecretResolutionContext` and verifying custom filter interpolation resolves `{{ spaced_var }}` via `VariableResolver.resolve` (`BUG-08`)
- [ ] T027 [P] [US3] Expand unit tests in `core-network/src/commonTest/kotlin/io/github/crustack/clapi/core/network/execution/RequestExecutionServiceSecretsTest.kt` testing `RequestExecutionService` with `SecretResolutionContext` and verifying secrets inside `auth.params`, `formEntries`, `formDataEntries`, and `graphQl` bodies are extracted and resolved (`BUG-07`)

### Implementation for User Story 3

- [ ] T028 [US3] Implement `VaultSecretProvider`, `AwsSecretProvider`, `AzureSecretProvider`, `GcpSecretProvider`, `KeeperSecretProvider`, and `KeepassXcSecretProvider` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/vault/ExternalSecretProvider.kt`
- [ ] T029 [US3] Refactor `SecretResolutionService.kt` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/vault/SecretResolutionService.kt` to accept `SecretResolutionContext`, dispatch provider resolution through `List<ExternalSecretProvider>`, and replace naive `String.replace("{{$vk}}", vv)` in custom filter interpolation with `VariableResolver.resolve(text, context.variableLayers)` (`BUG-08`)
- [ ] T030 [US3] Refactor `RequestExecutionService.kt` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/execution/RequestExecutionService.kt` to use `SecretResolutionContext` and `RequestDefinition.extractTemplateStrings()` / `GrpcCallRequest.extractTemplateStrings()` (`BUG-07`)
- [ ] T031 [US3] Update `RequestVariableResolution.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/executor/RequestVariableResolution.kt` and `NetworkDiModule.kt` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/NetworkDiModule.kt` to construct `SecretResolutionContext` and register the `ExternalSecretProvider` strategies

**Checkpoint**: User Story 3 is complete; `SecretResolutionService` and `RequestExecutionService` use encapsulated `SecretResolutionContext` and strategy providers.

---

## Phase 6: User Story 4 - Shared OAuth2 Token Lifecycle Manager (Priority: P2)

**Goal**: Consolidate duplicated OAuth2 token validation, refresh-token flow, and `client_credentials` / `password` auto-fetch logic into a single `Mutex`-coordinated `OAuth2TokenManager` in `:core-network`.

**Independent Test**: Execute a request that uses both `AuthType.OAUTH2` and `{{_auth.oauth2.accessToken}}` in a header with an expired token, and verify that `OAuth2TokenManager` performs only one token refresh HTTP call and updates both places with the new token.

### Tests for User Story 4

- [ ] T032 [P] [US4] Create unit tests in `core-network/src/commonTest/kotlin/io/github/crustack/clapi/core/network/oauth2/OAuth2TokenManagerTest.kt` verifying token expiry buffer handling (`expiryBufferMillis = 30_000L`), refresh token grant, fallback `client_credentials` / `password` grant acquisition, and single-refresh deduplication when called concurrently or back-to-back for `AuthType.OAUTH2` and `{{_auth.oauth2.accessToken}}` (`BUG-09`)

### Implementation for User Story 4

- [ ] T033 [US4] Implement `OAuth2TokenManager` and `OAuth2TokenRefreshResult` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/oauth2/OAuth2TokenManager.kt` with internal `Mutex` synchronization and `ensureValidToken(authConfig: AuthConfig, expiryBufferMillis: Long = 30_000L, onTokenUpdated: ((AuthConfig) -> Unit)? = null)`
- [ ] T034 [US4] Refactor `OAuth2DynamicVariableProvider.kt` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/dynamicvar/OAuth2DynamicVariableProvider.kt` to delegate token validation, refresh, and acquisition to `OAuth2TokenManager` (`BUG-09`)
- [ ] T035 [US4] Refactor `ensureValidOAuth2Token` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/executor/RequestVariableResolution.kt` and `RequestExecutionService.kt` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/execution/RequestExecutionService.kt` to delegate to `OAuth2TokenManager` and register `OAuth2TokenManager` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/NetworkDiModule.kt` (`BUG-09`)

**Checkpoint**: User Story 4 is complete; OAuth2 token lifecycle management exists in a single authoritative service in `:core-network`.

---

## Phase 7: User Story 5 - Managed Network Client Lifecycle & Safe Script Sub-Request Serialization (Priority: P3)

**Goal**: Prevent `HttpClient` resource leaks by closing per-request transport clients and lazily instantiating `subReqClient` only when `clapi.sendRequest()` is called, remove the dead `"hello"` WebSocket stub from `KtorApiClient`, and safely encode `sendRequest` callback responses in `ClapiScriptEngine` using standard JSON string serialization.

**Independent Test**: Execute requests without `clapi.sendRequest()` and verify `subReqClient` is never created and per-request `KtorApiClient` is closed; execute `clapi.sendRequest()` returning `\t`, `\u2028`, `\u2029`, quotes, and backslashes and verify the JS callback parses the response without syntax errors.

### Tests for User Story 5

- [ ] T036 [P] [US5] Create unit tests in `core-network/src/commonTest/kotlin/io/github/crustack/clapi/core/network/KtorApiClientLifecycleTest.kt` verifying `KtorApiClient.close()` closes the underlying `HttpClient` engine and `KtorApiClient.execute()` with `ProtocolType.WEBSOCKET` throws `UnsupportedOperationException` (`BUG-10`)
- [ ] T037 [P] [US5] Add unit tests in `core-scripting/src/commonTest/kotlin/io/github/crustack/clapi/core/scripting/ClapiScriptEngineTest.kt` verifying `clapi.sendRequest()` callbacks safely parse response bodies containing `\t`, `\b`, `\f`, `\u2028`, `\u2029`, quotes, and backslashes (`BUG-11`)

### Implementation for User Story 5

- [ ] T038 [P] [US5] Add `fun close() {}` (`AutoCloseable`) to `ApiClient` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/ApiClient.kt` and update `KtorApiClient.kt` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/KtorApiClient.kt` to close `httpClient` in `close()` and replace the hardcoded `"hello"` WebSocket stub (`executeWebSocket`) with `UnsupportedOperationException("WebSocket sessions must be executed via WebSocketClient")` (`BUG-10`)
- [ ] T039 [US5] Update `RequestExecutionPipeline.kt`, `RequestExecutor.kt`, `GraphQlRequestExecutor.kt`, and `GrpcRequestExecutor.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/executor/` to lazily instantiate `subReqClient` inside `sendRequestExecutor` only when a script invokes `clapi.sendRequest()` and close both `subReqClient` and per-request `ApiClient` instances in `finally` blocks (`BUG-10`)
- [ ] T040 [P] [US5] Update `ClapiScriptEngine.kt` in `core-scripting/src/commonMain/kotlin/io/github/crustack/clapi/core/scripting/ClapiScriptEngine.kt` to encode `sendRequest` response body and status text literals using `kotlinx.serialization.json.JsonPrimitive(rawBody).toString()` instead of manual `.replace()` chains (`BUG-11`)

**Checkpoint**: All 5 user stories are complete and independently verified.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: End-to-end integration test coverage, documentation updates, and full verification.

- [ ] T041 [P] Add end-to-end regression test cases in `qa-tests/src/test/kotlin/io/github/crustack/clapi/qa/ApiClientE2ETest.kt` and `qa-tests/src/test/kotlin/io/github/crustack/clapi/qa/ExternalSecretsIntegrationTest.kt` covering Layer 0 `requestScopedScriptVars`, `secret: true` shadowing prevention, and post-script secret resolution
- [ ] T042 [P] Update module architecture diagrams and table descriptions in `DEVELOPMENT.md` and `docs/architecture.md` to reflect the removal of `:feature-requests` and consolidation into `:core-network`
- [ ] T043 Execute full build and test validation suite from `specs/019-unify-request-execution/quickstart.md` across `:core-network`, `:core-scripting`, `:ui-shared`, and `:qa-tests`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — starts immediately to dissolve `:feature-requests` into `:core-network`.
- **Phase 2 (Foundational)**: Depends on Phase 1 — creates shared extractors, context models, provider SPI, and storage key constants.
- **Phase 3 (US1 - P1 🎯 MVP)**: Depends on Phase 2 — unifies HTTP, GraphQL, and gRPC execution pipeline and fixes protocol feature skew (`BUG-06a/b/c/d`, `BUG-07`).
- **Phase 4 (US2 - P1)**: Depends on Phase 2 & Phase 3 (`RequestExecutionPipeline`) — fixes Layer 0 script variables (`BUG-01`), `secret: true` shadowing (`BUG-02`), two-pass secret scanning (`BUG-03`), collection variable isolation (`BUG-04`), and storage key consistency (`BUG-05`).
- **Phase 5 (US3 - P2)**: Depends on Phase 2 — refactors `SecretResolutionService` to `ExternalSecretProvider` strategies + `SecretResolutionContext` and fixes custom filter interpolation (`BUG-08`). Can run in parallel with US4 and US5.
- **Phase 6 (US4 - P2)**: Depends on Phase 2 — introduces `OAuth2TokenManager` and deduplicates OAuth2 refresh calls (`BUG-09`). Can run in parallel with US3 and US5.
- **Phase 7 (US5 - P3)**: Depends on Phase 2 — manages `HttpClient` lifecycles (`BUG-10`) and fixes `ClapiScriptEngine` JSON string escaping (`BUG-11`). Can run in parallel with US3 and US4.
- **Phase 8 (Polish)**: Depends on Phases 3–7 completion.

### User Story Dependency Graph

```text
Phase 1: Setup (:feature-requests -> :core-network)
   │
   ▼
Phase 2: Foundational (Extractors, SecretResolutionContext, ExternalSecretProvider, SettingsStorageKeys)
   │
   ├──────────────────────────────┬──────────────────────────────┬──────────────────────────────┐
   ▼                              ▼                              ▼                              ▼
Phase 3: US1 (P1 MVP)          Phase 5: US3 (P2)              Phase 6: US4 (P2)              Phase 7: US5 (P3)
Unified Pipeline & Parity      Secret Provider Registry       OAuth2TokenManager             Client Lifecycle & JS Escaping
   │                              │                              │                              │
   ▼                              │                              │                              │
Phase 4: US2 (P1)                 │                              │                              │
Variable Precedence & Scoping     │                              │                              │
   │                              │                              │                              │
   └──────────────────────────────┴──────────────────────────────┴──────────────────────────────┘
                                  │
                                  ▼
                         Phase 8: Polish & E2E QA
```

---

## Parallel Opportunities

### Parallel Example: Foundational Phase (Phase 2)
```bash
# All 4 foundational files touch independent packages/modules and can be implemented in parallel:
Task T005: "Create RequestTemplateExtractor.kt in core-network/.../execution/RequestTemplateExtractor.kt"
Task T006: "Create SecretResolutionContext.kt in core-network/.../vault/SecretResolutionContext.kt"
Task T007: "Create ExternalSecretProvider.kt in core-network/.../vault/ExternalSecretProvider.kt"
Task T008: "Define SettingsStorageKeys in ui-shared/.../persistence/SettingsRepository.kt"
```

### Parallel Example: User Story 1 & User Story 2 Tests
```bash
# All test suites for US1 and US2 can be written in parallel:
Task T009: "Expand HttpExecutionAdapterTest.kt in core-network/.../execution/HttpExecutionAdapterTest.kt"
Task T010: "Create GraphQlRequestExecutorTest.kt in ui-shared/.../components/executor/GraphQlRequestExecutorTest.kt"
Task T011: "Create GrpcRequestExecutorTest.kt in ui-shared/.../components/executor/GrpcRequestExecutorTest.kt"
Task T018: "Create EnvironmentModelsTest.kt in ui-shared/.../state/EnvironmentModelsTest.kt"
Task T019: "Create CollectionVariablesIsolationTest.kt in ui-shared/.../state/CollectionVariablesIsolationTest.kt"
Task T020: "Create SettingsStorageKeysTest.kt in ui-shared/.../persistence/SettingsStorageKeysTest.kt"
```

### Parallel Example: User Stories 3, 4, and 5
```bash
# Once Phase 2 is complete, US3, US4, and US5 core components can be developed concurrently:
Task T028: "Implement 6 ExternalSecretProvider strategies in core-network/.../vault/ExternalSecretProvider.kt"
Task T033: "Implement OAuth2TokenManager in core-network/.../oauth2/OAuth2TokenManager.kt"
Task T038: "Implement ApiClient/KtorApiClient close() and remove WebSocket stub in core-network/.../KtorApiClient.kt"
Task T040: "Update ClapiScriptEngine.kt safe JSON string escaping in core-scripting/.../ClapiScriptEngine.kt"
```

---

## Implementation Strategy

### MVP First (Phase 1 + Phase 2 + User Story 1 + User Story 2)
1. Complete **Phase 1: Setup** to remove `:feature-requests` and consolidate into `:core-network`.
2. Complete **Phase 2: Foundational** to establish template extraction, secret context models, and canonical storage keys.
3. Complete **Phase 3: User Story 1 (P1)** to unify HTTP, GraphQL, and gRPC execution pipelines and fix protocol skews.
4. Complete **Phase 4: User Story 2 (P1)** to fix `clapi.variables.set` wire delivery, `secret: true` shadowing, two-pass secret resolution, and per-collection variable isolation.
5. **Validate P1 MVP**: Run `:core-network:test` and `:ui-shared:desktopTest` to confirm all P1 correctness and parity tests pass.

### Incremental Delivery (P2 & P3 Enhancements)
6. Complete **Phase 5: User Story 3 (P2)** to modularize `SecretResolutionService` with `ExternalSecretProvider` strategies and `SecretResolutionContext`.
7. Complete **Phase 6: User Story 4 (P2)** to deduplicate OAuth2 token refresh logic into `OAuth2TokenManager`.
8. Complete **Phase 7: User Story 5 (P3)** to enforce `HttpClient` cleanup, lazy `subReqClient` creation, and safe JSON string encoding in `ClapiScriptEngine`.
9. Complete **Phase 8: Polish** to run full E2E QA tests and update architecture docs.
