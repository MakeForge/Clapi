---
description: "Task list for OAuth2 authentication implementation"
---

# Tasks: OAuth2 Authentication

**Input**: Design documents from `specs/010-oauth2-auth/` (`spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`)

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`

**Tests**: Test tasks included for unit, UI, and QA integration validation across all user stories.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Update core state models, token structures, and localization resources required by all user stories.

- [x] T001 Extend `RequestTabState` and `CollectionNode` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt` with OAuth2 state properties (`authOAuth2Flow`, `authOAuth2AccessTokenUrl`, `authOAuth2RefreshTokenUrl`, `authOAuth2AuthorizationUrl`, `authOAuth2RedirectUri`, `authOAuth2Scope`, `authOAuth2ClientId`, `authOAuth2ClientSecret`, `authOAuth2CredentialsPlacement`, `authOAuth2Username`, `authOAuth2Password`, `authOAuth2AccessToken`, `authOAuth2RefreshToken`, `authOAuth2IdToken`, `authOAuth2ExpiresAtEpochMs`, `authOAuth2IsFetching`, `authOAuth2FetchError`).
- [x] T002 [P] Add string localization entries for OAuth2 UI (flow labels, credential fields, "Fetch Tokens", copy feedback, token statuses) in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/i18n/Strings.kt` and `ui-shared/src/commonMain/resources/i18n/en.json`.
- [x] T003 [P] Create `OAuth2TokenResponse` model and `OAuth2TokenState` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/oauth2/OAuth2TokenModels.kt` with `isValid(bufferMs: Long)` and JWT `exp` parsing fallback.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Network client and persistence infrastructure that MUST be complete before user stories begin.

**⚠️ CRITICAL**: Foundational tasks must complete before user story implementation can begin.

- [x] T004 Implement `OAuth2TokenClient` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/oauth2/OAuth2TokenClient.kt` handling `fetchClientCredentialsToken`, `fetchPasswordToken`, and `refreshToken` over Ktor `HttpClient` with URL-encoded form parameters, Basic auth / body placement, and RFC 6749 error decoding.
- [x] T005 [P] Create unit tests for `OAuth2TokenClient` and `OAuth2TokenState` in `core-network/src/commonTest/kotlin/io/github/crustack/clapi/core/network/oauth2/OAuth2TokenClientTest.kt` using Ktor `MockEngine`.
- [x] T006 [P] Update `OpenCollectionModelMapping.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/OpenCollectionModelMapping.kt` to serialize `AuthOAuth2` from `RequestTabState` / `CollectionNode` and deserialize `AuthOAuth2` back to tab / node state.
- [x] T007 [P] Create serialization unit tests for `AuthOAuth2` mapping in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/persistence/OpenCollectionOAuth2MappingTest.kt`.

**Checkpoint**: Foundation ready — user story implementation can now begin.

---

## Phase 3: User Story 1 - Configure OAuth2 Authentication in Auth Editor (Priority: P1) 🎯 MVP

**Goal**: Enable selecting "OAuth 2.0" in the Auth Editor, configuring flow (Client Credentials, Password), endpoints, client credentials, placement, scopes, username/password, and supporting variables and external secrets on all fields with password masking.

**Independent Test**: Navigate to the Auth tab of a request or collection, select OAuth2, set flow, credentials with variables like `{{client_secret}}`, verify persistence in OpenCollection JSON and UI roundtrip.

### Tests for User Story 1

- [x] T008 [P] [US1] Create UI tests for OAuth2 selection, flow switching, and field binding in `ui-desktop/src/desktopTest/kotlin/io/github/crustack/clapi/ui/desktop/components/AuthEditorOAuth2UiTest.kt`.

### Implementation for User Story 1

- [x] T009 [US1] Update `selectableAuthTypes` and `AuthEditor.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/AuthEditor.kt` to re-enable `AuthType.OAUTH2` and render flow selection, URLs, scope, client ID, masked client secret, placement selector, and resource owner fields.
- [x] T010 [US1] Ensure `resolveExternalSecretsForTab` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/RequestExecutor.kt` scans all OAuth2 string properties (`authOAuth2ClientId`, `authOAuth2ClientSecret`, `authOAuth2Username`, `authOAuth2Password`, `authOAuth2AccessTokenUrl`, `authOAuth2Scope`) for variable/secret references.
- [x] T011 [US1] Update `resolveInheritedAuth` and `applyInheritedDefaults` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt` to support cascading `AuthType.OAUTH2` from collections and folders down to child requests.

**Checkpoint**: User Story 1 (MVP) is fully functional and testable independently.

---

## Phase 4: User Story 2 - Interactively Fetch and Inspect OAuth2 Tokens (Priority: P1)

**Goal**: In Auth Editor tab, provide a "Fetch Tokens" button and display current Access Token, Refresh Token, and Identity Token in copyable text fields with copy buttons and expiration status.

**Independent Test**: Provide credentials in Auth Editor, click "Fetch Tokens", assert tokens are fetched and populated into copyable text boxes, verify clipboard copy action.

### Tests for User Story 2

- [x] T012 [P] [US2] Create UI tests for "Fetch Tokens" action, loading state, error display, and clipboard copy action in `ui-desktop/src/desktopTest/kotlin/io/github/crustack/clapi/ui/desktop/components/AuthEditorFetchTokensUiTest.kt`.

### Implementation for User Story 2

- [x] T013 [US2] Implement `fetchOAuth2TokensForTab` action in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/AuthEditor.kt` that resolves variables, calls `OAuth2TokenClient`, updates tab tokens (`authOAuth2AccessToken`, `authOAuth2RefreshToken`, `authOAuth2IdToken`, `authOAuth2ExpiresAtEpochMs`), and handles loading and error states.
- [x] T014 [US2] Implement token inspection composables in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/AuthEditor.kt` rendering the "Fetch Tokens" button, error alerts, copyable text fields for Access/Refresh/Identity tokens, and expiration timer label.

**Checkpoint**: User Stories 1 AND 2 work together independently.

---

## Phase 5: User Story 3 - Automatic Token Validation and Refresh on Request Execution (Priority: P1)

**Goal**: When a request is executed, check if active access token is valid (using clock-skew buffer + JWT exp fallback). If expired/missing, fetch new token (using refresh token if available, or primary credentials) via Ktor client and attach `Authorization: Bearer <token>` before sending the HTTP request.

**Independent Test**: Dispatch request with OAuth2 auth and no token/expired token; verify automatic acquisition and successful authenticated request execution without user intervention.

### Tests for User Story 3

- [ ] T015 [P] [US3] Create unit/pipeline tests for token pre-flight validation and auto-refresh in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/OAuth2ExecutionPipelineTest.kt`.

### Implementation for User Story 3

- [ ] T016 [US3] Update `buildAuthConfig` and `prepareTabRequest` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/RequestExecutor.kt` to construct `AuthConfig(type = AuthType.OAUTH2, params = ...)` from direct or inherited OAuth2 properties.
- [ ] T017 [US3] Implement OAuth2 pre-execution token validation and auto-refresh interceptor in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/RequestExecutor.kt` that checks validity, attempts refresh token or primary grant if invalid, updates tab state, and injects `Authorization: Bearer <token>`.
- [ ] T018 [US3] Update `HttpExecutionAdapter.kt` in `feature-requests/src/commonMain/kotlin/io/github/crustack/clapi/feature/requests/HttpExecutionAdapter.kt` and `KtorApiClient.kt` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/KtorApiClient.kt` to ensure resolved OAuth2 bearer tokens attach correctly when executed via standalone adapter.

**Checkpoint**: User Stories 1, 2, and 3 work seamlessly together.

---

## Phase 6: User Story 4 - End-to-End Automated Testing with Keycloak (Priority: P2)

**Goal**: Automated integration tests in `qa-tests` validating Client Credentials, Resource Owner Password, and auto-refresh against Keycloak (`qa-tests/keycloak-oauth2-compose.yml`, `test-realm.json`).

**Independent Test**: Run `./gradlew :qa-tests:test --tests "io.github.crustack.clapi.qa.OAuth2IntegrationTest"`.

### Implementation & Tests for User Story 4

- [ ] T019 [US4] Implement `OAuth2IntegrationTest.kt` in `qa-tests/src/test/kotlin/io/github/crustack/clapi/qa/OAuth2IntegrationTest.kt` covering Client Credentials flow, Resource Owner Password flow, Refresh Token grant, and protected endpoint access against the Keycloak test realm.
- [ ] T020 [US4] Add end-to-end request execution test in `OAuth2IntegrationTest.kt` in `qa-tests/src/test/kotlin/io/github/crustack/clapi/qa/OAuth2IntegrationTest.kt` verifying automatic token acquisition and expiration refresh before calling protected endpoints.

**Checkpoint**: All user stories have complete automated test coverage.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, legacy test updates, and end-to-end validation across the application.

- [ ] T021 [P] Update `FEATURES.md` and documentation in `docs/` reflecting full OAuth2 authentication support.
- [x] T022 [P] Update legacy UI test in `ui-desktop/src/desktopTest/kotlin/io/github/crustack/clapi/ui/desktop/QaFixesUiTest.kt` (which previously asserted that OAuth2 was hidden) to verify that OAuth2 is now visible and selectable.
- [ ] T023 Run validation scenarios in `specs/010-oauth2-auth/quickstart.md` and execute `./gradlew test` across all affected modules.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Can start immediately.
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational completion.
- **User Story 2 (Phase 4)**: Depends on Foundational and User Story 1 state properties.
- **User Story 3 (Phase 5)**: Depends on Foundational and User Story 1 state properties.
- **User Story 4 (Phase 6)**: Depends on Foundational, US1, US2, and US3.
- **Polish (Phase 7)**: Depends on all user stories being complete.

### Parallel Opportunities

- T002, T003 can execute in parallel during Setup.
- T005, T006, T007 can execute in parallel during Foundational.
- T008 (UI tests) can execute in parallel with foundational tasks.
- T012, T015 can be drafted in parallel with story implementations.
- T021, T022 can execute in parallel during Polish.

---

## Implementation Strategy

### MVP First (User Story 1 Only)
1. Complete Phase 1: Setup (`RequestTabState` properties, token models).
2. Complete Phase 2: Foundational (`OAuth2TokenClient`, OpenCollection mapping).
3. Complete Phase 3: User Story 1 (OAuth2 Auth Editor configuration & persistence).
4. **VALIDATE**: Ensure OAuth2 auth can be configured and saved/loaded.

### Incremental Delivery
1. Add User Story 2: "Fetch Tokens" action & copyable token inspection fields.
2. Add User Story 3: Pre-flight token validity check & automatic refresh on request execution.
3. Add User Story 4: Keycloak integration tests in `qa-tests`.
4. Polish: Documentation and regression test sweep.
