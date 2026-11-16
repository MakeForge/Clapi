# Tasks: GCP Secret Manager Integration

**Input**: Design documents from `specs/017-gcp-secret-manager/` (`spec.md`, `plan.md`, `data-model.md`, `contracts/`, `research.md`, `quickstart.md`)

**Prerequisites**: `plan.md` (required), `spec.md` (required for user stories), `data-model.md`, `contracts/`, `research.md`, `quickstart.md`

**Tests**: Test tasks are included as requested by project testing standards and user specification requirements.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `- [ ] [TaskID] [P?] [Story?] Description with file path`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., [US1], [US2], [US3], [US4], [US5], [US6])
- Exact file paths included in all task descriptions

---

## Phase 1: Setup (Dependencies & Infrastructure)

**Purpose**: Project initialization and client infrastructure package setup

- [X] T001 [P] Create package directory structure for GCP Secret Manager in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/gcp/`

---

## Phase 2: Foundational (Core Models & Client Infrastructure)

**Purpose**: Core data models, GCP client interface, and client implementation that block all user stories

**⚠️ CRITICAL**: No user story work can begin until this foundational phase is complete

- [X] T002 Implement `GcpSecretsManagerExternalSecret` data class (`name: String`, `secretId: String`, `version: String = "latest"`, `targetField: String = ""`, `projectId: String = ""`, `customFilters: Map<String, String> = emptyMap()`, `description: Description? = null`, `disabled: Boolean? = null`) implementing `ExternalSecretVariable` in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/OpenCollectionEnvironment.kt`
- [X] T003 Update `ExternalSecretVariableSerializer` polymorphic deserializer in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/OpenCollectionEnvironment.kt` to deserialize `GcpSecretsManagerExternalSecret` when `type: "gcp-secrets-manager"` or `secretId` is present (and distinct from AWS/Azure)
- [X] T004 [P] Create `GcpSecretManagerConfig` data class (`projectId: String`, `secretId: String`, `version: String = "latest"`, `propertyKey: String? = null`, `accessToken: String`, `endpointUrl: String = "https://secretmanager.googleapis.com"`) and response models `GcpAccessSecretVersionResponse` and `GcpSecretPayload` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/gcp/GcpSecretManagerConfig.kt`
- [X] T005 Implement `GcpSecretManagerClient` interface and `KtorGcpSecretManagerClient` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/gcp/GcpSecretManagerClient.kt` handling HTTP GET `/v1/projects/{projectId}/secrets/{secretId}/versions/{version}:access`, Bearer authentication, Base64 decoding of `payload.data`, JSON property key extraction, and plain text fallback
- [X] T006 [P] Create unit tests for `KtorGcpSecretManagerClient` with MockEngine verifying Bearer auth, Base64 payload decoding, JSON property extraction, and plaintext fallback in `core-network/src/commonTest/kotlin/io/github/crustack/clapi/core/network/gcp/GcpSecretManagerClientTest.kt`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Configure GCP Secret Manager Provider in Global Settings (Priority: P1) 🎯 MVP

**Goal**: Enable users to configure GCP Secret Manager connection parameters in Settings -> Secrets Manager (Project ID, Access Token, Endpoint URL Override), with secure encrypted storage for sensitive tokens.

**Independent Test**: Open Settings dialog -> Secrets Manager tab, select `GCP Secret Manager`, input Project ID, Access Token, and a custom Endpoint URL, close and reopen dialog, verifying settings persist and sensitive fields remain masked.

### Tests for User Story 1
- [X] T007 [P] [US1] Create unit tests for GCP settings persistence, masking, and encrypted storage in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/GcpSettingsTest.kt`

### Implementation for User Story 1
- [X] T008 [US1] Add GCP settings properties (`gcpProjectId`, `gcpAccessToken`, `gcpEndpointUrl`) to `AppSettings` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppSettings.kt`
- [X] T009 [US1] Update `SettingsRepository.load` and `SettingsRepository.save` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/SettingsRepository.kt` to persist GCP settings using `PlatformStorage.putSecretString` for `gcpAccessToken`
- [X] T010 [US1] Add `gcp-secrets-manager` option to provider dropdown and build GCP settings input form (Project ID, masked Access Token, Endpoint URL Override) in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/SettingsDialog.kt`
- [X] T011 [US1] Add desktop UI tests for GCP settings form rendering and provider switching in `ui-desktop/src/desktopTest/kotlin/io/github/crustack/clapi/ui/desktop/SettingsDialogUiTest.kt`

**Checkpoint**: Users can configure and securely persist GCP Secret Manager credentials in application settings.

---

## Phase 4: User Story 2 - Define GCP External Secrets in Environment Editor (Priority: P1)

**Goal**: Enable users to select `gcp-secrets-manager` as secret provider in the Environment Editor, define secrets with Name, Secret ID, Target Field, Version (defaulting to `latest`), Custom Filters, Description, and Enabled status matching the KeePassXC/AWS/Azure table layout, and save with OpenCollection mapping.

**Independent Test**: In Environment Editor -> External Secrets, select `GCP Secret Manager`, add a row with Variable Name `payment_key`, Secret ID `payment-foo`, Target Field `client_secret`, and Version `latest`, then save. Verify environment persistence and OpenCollection serialization.

### Tests for User Story 2
- [X] T012 [P] [US2] Create unit tests for GCP external secret editor mapping in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/EnvironmentEditorGcpTest.kt`

### Implementation for User Story 2
- [X] T013 [US2] Update `ClapiEnvironmentDto` and persistence serialization in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/ImportExportRepository.kt` to serialize `targetField`, `version`, and `customFilters` for `gcp-secrets-manager`
- [X] T014 [US2] Add `gcp-secrets-manager` to provider dropdown and configure table headers and rows (`EnvGcpSecretRow`) in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/EnvironmentEditDialog.kt` (Name, Secret ID, Target Field, Version, Custom Filters button, Description, Enabled toggle)
- [X] T015 [US2] Update `EnvState.toOpenCollectionEnvironment` and `toEnvState` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/OpenCollectionModelMapping.kt` to map `GcpSecretsManagerExternalSecret` bidirectionally with `MutableExternalSecret`

**Checkpoint**: Users can define, edit, and persist GCP external secrets within environments.

---

## Phase 5: User Story 3 - Resolve GCP Secrets at Runtime During Request Execution (Priority: P1)

**Goal**: Automatically resolve `{{_secret.<name>}}` and `secret: true` variables from GCP Secret Manager during request execution, with query deduplication by `(projectId, secretId, version)`, JSON property extraction, and error handling.

**Independent Test**: Execute an HTTP request with header `Authorization: Bearer {{_secret.plain_api_token}}` against seeded GCP emulator secrets. Verify secret is resolved and substituted into the outgoing request with zero credential leakage.

### Tests for User Story 3
- [X] T016 [P] [US3] Create unit tests for `resolveGcpSecrets` and query deduplication batching in `core-network/src/commonTest/kotlin/io/github/crustack/clapi/core/network/vault/SecretResolutionServiceGcpTest.kt`
- [X] T017 [P] [US3] Create unit tests for request execution with GCP secret resolution and error handling in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/RequestExecutorGcpSecretsTest.kt`

### Implementation for User Story 3
- [X] T018 [US3] Implement `resolveGcpSecrets` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/vault/SecretResolutionService.kt` with query deduplication by `(projectId, secretId, version)`, JSON property extraction, custom filters evaluation with variable interpolation, and plain text fallback
- [X] T019 [US3] Update `resolveAllSecrets` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/vault/SecretResolutionService.kt` and `feature-requests/src/commonMain/kotlin/io/github/crustack/clapi/feature/requests/RequestExecutionService.kt` to accept GCP connection parameters and map `GcpSecretsManagerExternalSecret` to `SecretDeclaration`
- [X] T020 [US3] Wire `AppState.settings` GCP properties into `resolveExternalSecretsForTab` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/executor/RequestVariableResolution.kt`

**Checkpoint**: Runtime request execution transparently resolves and substitutes GCP secrets.

---

## Phase 6: User Story 4 - Discover and Insert GCP Secrets via Autocomplete (Priority: P2)

**Goal**: Provide instant code completion suggestions for declared GCP external secrets when typing `{{_secret.` in request editors (URL, Headers, Body, Auth).

**Independent Test**: Open a request editor, type `{{_secret.` in a header value field, and verify that all enabled GCP external secrets appear in autocomplete suggestions with Secret badges.

### Tests for User Story 4
- [X] T021 [P] [US4] Create unit test for GCP secret autocomplete suggestions in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/RequestEditorGcpSecretsAutocompleteTest.kt`

### Implementation for User Story 4
- [X] T022 [US4] Verify and update autocomplete provider logic in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/RequestEditor.kt` to surface active GCP external secrets with secret badge and variable documentation

**Checkpoint**: Autocomplete suggests declared GCP secrets across all request input fields.

---

## Phase 7: User Story 5 - Portable OpenCollection Export and Import without Credential Leakage (Priority: P2)

**Goal**: Export and import environments containing GCP Secret Manager external secrets using standard OpenCollection JSON and YAML without leaking access tokens, emulator endpoints, or sensitive values.

**Independent Test**: Export an environment with GCP external secrets to OpenCollection JSON and YAML. Inspect exported files to verify `type: "gcp-secrets-manager"` is present and assert zero GCP tokens or emulator URLs exist in output.

### Tests for User Story 5
- [X] T023 [P] [US5] Add unit tests in `core-storage/src/commonTest/kotlin/io/github/crustack/clapi/core/storage/opencollection/OpenCollectionExternalSecretsTest.kt` validating JSON/YAML roundtrip for GCP external secrets and asserting zero credential leakage (no access tokens or local URLs in exported files)

### Implementation for User Story 5
- [X] T024 [US5] Update `core-storage/src/commonMain/resources/opencollection/opencollection_v1.0.0.json` schema to include `secretId`, `version`, `targetField`, `projectId`, and `customFilters` for `GcpSecretsManagerExternalSecret`

**Checkpoint**: Portable OpenCollection documents safely export and import GCP external secrets.

---

## Phase 8: User Story 6 - Automated GCP Secret Manager End-to-End Verification in QA Test Suite (Priority: P2)

**Goal**: Provide continuous automated regression testing for GCP Secret Manager in `qa-tests` against a containerized GCP Secret Manager REST emulator (`ghcr.io/blackwell-systems/gcp-secret-manager-emulator-rest:1.9`) and curl seeder.

**Independent Test**: Run `./gradlew :qa-tests:test --tests "io.github.crustack.clapi.qa.GcpSecretManagerIntegrationTest"` against running emulator container and verify all assertions pass.

### Implementation & Tests for User Story 6
- [X] T025 [P] [US6] Add GCP Secret Manager REST emulator service `gcp-secretmanager` (`ghcr.io/blackwell-systems/gcp-secret-manager-emulator-rest:1.9`) and seeder container `gcp-seeder` to `qa-tests/docker/secret-managers-compose.yml` seeding `payment-foo` with `{"client_id":"gcp_client_789","client_secret":"sk_gcp_secret_999"}` and `plain-token` with `gcp-plain-token-value-12345`
- [X] T026 [P] [US6] Add `GCP Staging` environment with `gcp-secrets-manager` external secrets to test fixture in `qa-tests/fixtures/clapi-test-collection.json`
- [X] T027 [US6] Implement end-to-end integration test in `qa-tests/src/test/kotlin/io/github/crustack/clapi/qa/GcpSecretManagerIntegrationTest.kt` verifying live secret retrieval, multi-value JSON target field extraction, plaintext secret retrieval, request substitution, and error handling for missing secrets against the GCP emulator container

**Checkpoint**: CI and automated test pipelines verify GCP Secret Manager integration against the container emulator.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Improvements and validation that cross multiple user stories

- [X] T028 Execute full validation walkthrough per `specs/017-gcp-secret-manager/quickstart.md`
- [X] T029 [P] Verify error diagnostics and ensure zero secret or credential leakage across logs and error toasts in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/gcp/GcpSecretManagerClient.kt`
- [X] T030 Run complete project test suite `./gradlew test` to ensure zero regressions across all modules

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — BLOCKS all user stories
- **User Story 1 (Phase 3, P1)**: Depends on Phase 2 (Foundational)
- **User Story 2 (Phase 4, P1)**: Depends on Phase 2 (Foundational)
- **User Story 3 (Phase 5, P1)**: Depends on User Story 1 and User Story 2 (requires settings and environment declarations)
- **User Story 4 (Phase 6, P2)**: Depends on User Story 2 (requires environment secret state)
- **User Story 5 (Phase 7, P2)**: Depends on User Story 2 (requires environment model mapping)
- **User Story 6 (Phase 8, P2)**: Depends on User Story 3 (requires runtime secret resolution)
- **Polish (Phase 9)**: Depends on all user stories being completed

### User Story Dependencies

```
Phase 1: Setup ──► Phase 2: Foundational
                         │
         ┌───────────────┴───────────────┐
         ▼                               ▼
  Phase 3: US1 (Settings)         Phase 4: US2 (Env Editor)
         │                               │
         └───────────────┬───────────────┘
                         ▼
                  Phase 5: US3 (Runtime Resolution)
                         │
         ┌───────────────┼───────────────┐
         ▼               ▼               ▼
    Phase 6: US4    Phase 7: US5    Phase 8: US6
   (Autocomplete)     (Export)       (GCP QA E2E)
         │               │               │
         └───────────────┼───────────────┘
                         ▼
                  Phase 9: Polish
```

---

## Parallel Opportunities

- **Phase 1 & 2**: T001, T004 (`GcpSecretManagerConfig`), and T006 (Unit tests) can run in parallel once T002/T003/T005 are established.
- **Phase 3 & 4**: User Story 1 (Settings UI) and User Story 2 (Environment Editor UI) touch separate components and can be implemented in parallel once Phase 2 completes.
- **Phase 6, 7 & 8**: User Story 4 (Autocomplete), User Story 5 (Export/Import), and User Story 6 (QA GCP emulator integration test) can run in parallel once User Story 3 completes.

---

## Implementation Strategy

### MVP First (User Story 1 & 2 & 3 Core Flow)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (`GcpSecretsManagerExternalSecret`, `GcpSecretManagerConfig`, `KtorGcpSecretManagerClient`)
3. Complete Phase 3: User Story 1 (Settings UI)
4. Complete Phase 4: User Story 2 (Environment Editor UI)
5. Complete Phase 5: User Story 3 (Runtime Secret Resolution)
6. **STOP and VALIDATE**: Verify end-to-end secret resolution against GCP emulator with manual request dispatch.

### Incremental Delivery

1. Foundation + US1 + US2 + US3 -> Core Working Secret Resolution (MVP!)
2. Add US4 -> Discoverability via Autocomplete
3. Add US5 -> Portable OpenCollection Export/Import
4. Add US6 -> Automated GCP Emulator QA Regression Suite
5. Polish & Final Validation
