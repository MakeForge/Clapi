# Tasks: Dynamic OAuth2 Access Token Variable

**Input**: Design documents from `/specs/011-oauth2-token-variable/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/DynamicVariableContract.md`, `contracts/GrpcExecutionAuthContract.md`, `quickstart.md`

## Format: `[ID] [P?] [Story] Description`
- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`[US1]`, `[US2]`, `[US3]`, `[US4]`)
- Every task includes exact file paths

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Initialize packages and shared structure for dynamic variable resolution

- [X] T001 Create `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/dynamicvar/` directory structure per plan
- [X] T002 [P] Create `core-network/src/commonTest/kotlin/io/github/crustack/clapi/core/network/dynamicvar/` test directory structure

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core interfaces, context models, and registry that MUST be complete before user stories can execute

**⚠️ CRITICAL**: No user story work can begin until this foundational phase is complete

- [X] T003 Define `DynamicVariableException` and `DynamicVariableResolutionException` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/dynamicvar/DynamicVariableException.kt`
- [X] T004 [P] Define `OAuth2ResolvedConfig` data class with fields `flow`, `tokenUrl`, `refreshTokenUrl`, `clientId`, `clientSecret`, `credentialsPlacement`, `username`, `password`, `scope` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/dynamicvar/OAuth2ResolvedConfig.kt`
- [X] T005 [P] Define `DynamicVariableContext` data class with fields `effectiveAuthType`, `oauth2Config`, `oauth2TokenState`, `variableLayers`, `oauth2TokenClient`, `onOAuth2TokenUpdated`, and `log` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/dynamicvar/DynamicVariableContext.kt`
- [X] T006 Define `DynamicVariableProvider` interface with `name`, `supports(token: String): Boolean`, and `suspend fun resolve(tokens: Set<String>, context: DynamicVariableContext): Map<String, String>` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/dynamicvar/DynamicVariableProvider.kt`
- [X] T007 Implement `DynamicVariableRegistry` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/dynamicvar/DynamicVariableRegistry.kt` with `register`, `unregister`, token extraction regex `\{\{\s*([^{}]+?)\s*\}\}`, and `suspend fun resolveDynamicVariables(texts: Iterable<String>, context: DynamicVariableContext): Map<String, String>`

**Checkpoint**: Foundational interfaces and registry ready. User story implementation can now begin.

---

## Phase 3: User Story 1 - Resolve OAuth2 Access Token Dynamic Variable in HTTP Requests (Priority: P1) 🎯 MVP

**Goal**: Allow `{{_auth.oauth2.accessToken}}` in HTTP requests. When executed, verify OAuth2 is configured, acquire/refresh a valid access token, and interpolate the variable. Abort with a clear error if OAuth2 is not configured or token fetch fails.

**Independent Test**: Execute an HTTP request containing `{{_auth.oauth2.accessToken}}` in headers or URL with OAuth2 enabled and verify the variable resolves to the active/fetched token; verify it fails gracefully when OAuth2 is unconfigured.

### Tests for User Story 1

- [X] T008 [P] [US1] Unit tests for `OAuth2DynamicVariableProvider` in `core-network/src/commonTest/kotlin/io/github/crustack/clapi/core/network/dynamicvar/OAuth2DynamicVariableProviderTest.kt` verifying valid token reuse, refresh when expired, token fetching for client credentials, error when auth is unconfigured, and error on acquisition failure
- [X] T009 [P] [US1] Component pipeline test for HTTP request execution with `{{_auth.oauth2.accessToken}}` in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/DynamicVariableExecutionPipelineTest.kt`

### Implementation for User Story 1

- [X] T010 [US1] Implement `OAuth2DynamicVariableProvider` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/dynamicvar/OAuth2DynamicVariableProvider.kt` implementing `DynamicVariableProvider` for `_auth.oauth2.accessToken`
- [X] T011 [US1] Register `OAuth2DynamicVariableProvider` as default provider in `DynamicVariableRegistry` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/dynamicvar/DynamicVariableRegistry.kt`
- [X] T012 [US1] Add helper in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/RequestExecutor.kt` to build `DynamicVariableContext` from `state`, `tab`, and `variableLayers` (resolving direct or inherited OAuth2 config, tab token state, and token update callback)
- [X] T013 [US1] Integrate dynamic variable pre-execution resolution into HTTP send pipeline in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/RequestExecutor.kt`, merging resolved variables into `variableLayers` and handling fast-fail errors (`tab.lastError`)

**Checkpoint**: User Story 1 functional and independently testable for HTTP requests.

---

## Phase 4: User Story 2 - Dynamic OAuth2 Token Resolution in gRPC Requests & Metadata (Priority: P1)

**Goal**: Enable OAuth2 token resolution in gRPC requests, allowing metadata headers (such as `authorization: Bearer {{_auth.oauth2.accessToken}}`) to resolve on the wire, supported by an Auth tab in the gRPC editor.

**Independent Test**: Send a gRPC request configured with metadata `authorization = "Bearer {{_auth.oauth2.accessToken}}"` and verify that the wire call received by `DummyApiServer` contains the valid resolved token in metadata.

### Tests for User Story 2

- [X] T014 [P] [US2] Pipeline test for gRPC metadata resolution with `{{_auth.oauth2.accessToken}}` in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/DynamicVariableExecutionPipelineTest.kt`
- [X] T015 [P] [US2] E2E integration test `resolves_oauth2_access_token_in_grpc_metadata_authorization` in `qa-tests/src/test/kotlin/io/github/crustack/clapi/qa/GrpcApiE2ETest.kt` running against `DummyApiServer`

### Implementation for User Story 2

- [X] T016 [US2] Add `AUTH("Auth")` entry to `enum class GrpcEditorTab` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt`
- [X] T017 [US2] Update `GrpcEditorTabBar` and editor routing in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/GrpcRequestEditor.kt` to include `GrpcEditorTab.AUTH` with `Res.drawable.ic_lock` and render `AuthEditor(tab, state)`
- [X] T018 [US2] Include OAuth2 configuration fields from `tab` and inherited auth in gRPC `textsToScan` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/RequestExecutor.kt`
- [X] T019 [US2] Integrate `dynamicVariableRegistry.resolveDynamicVariables(...)` into gRPC execution in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/RequestExecutor.kt`, merging resolved tokens into `mergedScopedVars` and halting execution on error before invoking `grpcClient.executeUnary`

**Checkpoint**: User Story 2 complete. Both HTTP and gRPC requests resolve `{{_auth.oauth2.accessToken}}` on execution.

---

## Phase 5: User Story 3 - Extensible Dynamic Runtime Variable Architecture (Priority: P2)

**Goal**: Provide a clean, extensible public API for `DynamicVariableProvider` registration, enabling future runtime variables to be plugged in without modifying execution handlers.

**Independent Test**: Register a custom dummy/mock `DynamicVariableProvider` (e.g., resolving `{{_custom.testVar}}`) in `DynamicVariableRegistryTest`, invoke `resolveDynamicVariables`, and assert that the custom variable is resolved properly.

### Tests for User Story 3

- [X] T020 [P] [US3] Unit tests for `DynamicVariableRegistry` in `core-network/src/commonTest/kotlin/io/github/crustack/clapi/core/network/dynamicvar/DynamicVariableRegistryTest.kt` verifying custom provider registration, unregistration, multiple provider execution order, and error handling

### Implementation for User Story 3

- [X] T021 [US3] Refactor and verify `DynamicVariableRegistry` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/dynamicvar/DynamicVariableRegistry.kt` to ensure thread-safe provider list management and clean provider discovery API
- [X] T022 [US3] Expose `DynamicVariableRegistry` singleton or configurable instance on `AppState` / `RequestExecutionService` for clean extension

**Checkpoint**: User Story 3 complete. Dynamic variable resolution is modular and decoupled.

---

## Phase 6: User Story 4 - Autocomplete Suggestions & Syntax Highlighting (Priority: P3)

**Goal**: Provide editor autocomplete suggestions and syntax highlighting for `_auth.oauth2.accessToken` when OAuth2 is configured.

**Independent Test**: Trigger variable suggestions via `getAvailableVariables` and verify `_auth.oauth2.accessToken` appears with description when OAuth2 is active; verify syntax highlighting treats it as a defined variable.

### Tests for User Story 4

- [X] T023 [P] [US4] Unit test in `ui-desktop/src/desktopTest/kotlin/io/github/crustack/clapi/ui/desktop/components/VariableHighlightSecretsTest.kt` (or new test file) verifying `getAvailableVariables` includes `_auth.oauth2.accessToken` when OAuth2 is active and excludes it when inactive

### Implementation for User Story 4

- [X] T024 [US4] Update `getAvailableVariables` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/VariableHighlight.kt` to append `_auth.oauth2.accessToken` when effective auth is OAuth2
- [X] T025 [US4] Update `definedNames` calculation in `VariableHighlight.kt` to include `_auth.oauth2.accessToken` when effective auth is OAuth2
- [X] T026 [US4] Add `"AUTH"` badge styling in `VariableDropdownList` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/VariableHighlight.kt` for variables with prefix `_auth.`

**Checkpoint**: User Story 4 complete. Dynamic variable authoring in UI has full autocomplete and visual distinction.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: End-to-end verification and regression testing

- [X] T027 Run quickstart validation test scenarios from `specs/011-oauth2-token-variable/quickstart.md`
- [X] T028 Run full test suite `./gradlew check` across all modules to verify no regressions

---

## Dependencies & Execution Order

### Phase Dependencies
- **Setup (Phase 1)**: No dependencies - can start immediately.
- **Foundational (Phase 2)**: Depends on Phase 1 - BLOCKS all user stories.
- **User Story 1 (Phase 3)**: Depends on Phase 2. Core MVP.
- **User Story 2 (Phase 4)**: Depends on Phase 2 and US1 provider implementation (T010).
- **User Story 3 (Phase 5)**: Depends on Phase 2. Can proceed in parallel with US1/US2.
- **User Story 4 (Phase 6)**: Depends on Phase 2 and US1.
- **Polish (Phase 7)**: Depends on all user stories being completed.

### Parallel Opportunities
- Within Phase 1: T002 can run in parallel with T001.
- Within Phase 2: T004 and T005 can run in parallel.
- Within Phase 3 (US1): T008 and T009 can be written in parallel before implementation.
- Within Phase 4 (US2): T014 and T015 can be written in parallel.
- User Story 3 (Phase 5) can run in parallel with User Story 2 (Phase 4).

---

## Implementation Strategy

### MVP First (User Story 1 Only)
1. Complete Phase 1 (Setup) and Phase 2 (Foundational).
2. Implement Phase 3 (User Story 1: `OAuth2DynamicVariableProvider` + HTTP resolution).
3. Validate User Story 1 independently with unit and pipeline tests.

### Incremental Delivery
1. Foundation + US1 (MVP): HTTP requests support `{{_auth.oauth2.accessToken}}`.
2. Add US2: gRPC requests and metadata support `{{_auth.oauth2.accessToken}}` with full E2E test.
3. Add US3: Extensibility verification and multi-provider registry tests.
4. Add US4: UI editor autocomplete and syntax highlighting.
5. Polish: Quickstart execution and full `./gradlew check`.
