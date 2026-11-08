# Tasks: External Secret Variables

**Input**: Design documents from `specs/006-external-secret-variables/` (`spec.md`, `plan.md`, `data-model.md`, `contracts/`, `research.md`, `quickstart.md`)

**Prerequisites**: `plan.md` (required), `spec.md` (required for user stories), `data-model.md`, `contracts/`, `research.md`, `quickstart.md`

**Tests**: Test tasks are included as explicitly requested in user requirements (QA tests, integration fixtures, and unit test suites).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `- [ ] [TaskID] [P?] [Story?] Description with file path`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., [US1], [US2], [US3]...)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Dependencies & Project Configuration)

**Purpose**: Dependency configuration and build setup for `ksafe` encrypted storage and Ktor client

- [X] T001 Add `ksafe` dependency (`eu.anifantakis:ksafe:3.3.0`) in `gradle/libs.versions.toml`
- [X] T002 Add `ksafe` dependency to `ui-shared/build.gradle.kts`
- [X] T003 [P] Ensure Ktor client content negotiation and serialization dependencies are configured in `core-network/build.gradle.kts`

---

## Phase 2: Foundational (Storage Extensions & Core Models)

**Purpose**: Core storage abstractions and OpenCollection data models that block all user stories

**⚠️ CRITICAL**: No user story work can begin until this foundational phase is complete

- [X] T004 Extend `PlatformStorage` declaration with `putSecretString(key: String, value: String)` and `getSecretString(key: String): String?` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/platform/PlatformApi.kt`
- [X] T005 Implement `PlatformStorage.putSecretString` and `getSecretString` using `KSafe(fileName = "clapi-secrets")` with `putDirect` and `getDirect` in `ui-shared/src/desktopMain/kotlin/io/github/crustack/clapi/ui/shared/platform/PlatformApi.desktop.kt`
- [X] T006 Implement OpenCollection data models `ExternalSecrets`, `ExternalSecretVariable`, `HashicorpVaultExternalSecret`, and polymorphic serializer `ExternalSecretVariableSerializer` in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/OpenCollectionEnvironment.kt`
- [X] T007 Extend `Environment` data class with `val externalSecrets: ExternalSecrets? = null` in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/OpenCollectionEnvironment.kt`
- [X] T008 [P] Define Vault KV v2 wire models `VaultKvV2Response` and `VaultKvV2Data` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/vault/VaultModels.kt`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Configure Secret Provider Credentials in Global Settings (Priority: P1) 🎯 MVP

**Goal**: Enable users to configure HashiCorp Vault Server credentials (URL, mount path, namespace, encrypted token) in application settings.

**Independent Test**: Open Settings dialog -> Secrets Manager tab, enter Vault URL, mount path, and token, close and re-open dialog, and assert that credentials persist with the token stored encrypted via `ksafe`.

### Tests for User Story 1
- [X] T009 [P] [US1] Create unit tests for `PlatformStorage.putSecretString` and `getSecretString` encryption in `ui-desktop/src/desktopTest/kotlin/io/github/crustack/clapi/ui/desktop/persistence/PlatformStorageSecretTest.kt`
- [X] T010 [P] [US1] Create unit tests for `SettingsRepository` loading and saving Vault settings in `ui-desktop/src/desktopTest/kotlin/io/github/crustack/clapi/ui/desktop/persistence/SettingsRepositorySecretsTest.kt`

### Implementation for User Story 1
- [X] T011 [US1] Extend `AppSettings` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt` with fields for `vaultServerUrl: String`, `vaultMountPath: String`, `vaultNamespace: String`, and `vaultToken: String`
- [X] T012 [US1] Update `SettingsRepository.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/SettingsRepository.kt` to load and save Vault settings (`vaultServerUrl`, `vaultMountPath`, `vaultNamespace` via `putString`/`getString`, and `vaultToken` via `putSecretString`/`getSecretString`)
- [X] T013 [P] [US1] Add `SECRETS` section to `SettingsSection` enum and section label in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/SettingsDialog.kt`
- [X] T014 [US1] Implement Secrets Manager settings tab UI in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/SettingsDialog.kt` with provider selector (`hashicorp-vault-server`), Server URL, Mount Path, Namespace, and masked Vault Token with visibility toggle

**Checkpoint**: User Story 1 is functional and testable independently (MVP ready).

---

## Phase 4: User Story 2 - Define External Secrets in Environment Editor (Priority: P1)

**Goal**: Enable users to select a secret provider and manage external secret declarations (`name`, `path`, `disabled`, `description`) in an environment.

**Independent Test**: Open Environment Editor, switch to the "External Secrets" tab, select "HashiCorp Vault (Server)", add secrets (`client_id`, `client_secret` with path `payment/foo`), save, and verify that the environment data model contains the external secrets.

### Tests for User Story 2
- [ ] T015 [P] [US2] Create unit tests for `EnvState` external secrets mapping to and from OpenCollection `Environment` in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/EnvironmentEditorSecretsTest.kt`

### Implementation for User Story 2
- [ ] T016 [P] [US2] Implement `MutableExternalSecret` class with `name`, `path`, `description`, and `enabled` reactive properties in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt`
- [ ] T017 [US2] Extend `EnvState` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt` with `externalSecretsProvider: String` and `externalSecrets: SnapshotStateList<MutableExternalSecret>`
- [ ] T018 [US2] Update `Environment.toEnvState()` and `EnvState.toOpenCollectionEnvironment()` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/OpenCollectionModelMapping.kt` to serialize and deserialize `externalSecrets`
- [ ] T019 [US2] Add tab header selector ("Variables" vs "External Secrets") in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/EnvironmentEditDialog.kt`
- [ ] T020 [US2] Implement "External Secrets" tab view in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/EnvironmentEditDialog.kt` with provider dropdown, secret rows table (Enabled checkbox, Name, Path, Description, Delete button), and "Add Secret" button

**Checkpoint**: User Stories 1 and 2 operate together independently.

---

## Phase 5: User Story 3 - Discover and Insert Secrets via Code Completion (Priority: P2)

**Goal**: Display available external secrets formatted as `{{_secret.<name>}}` in the `Ctrl+Space` autocomplete dropdown below regular variables.

**Independent Test**: In a request editor input with an active environment containing external secrets, press `Ctrl+Space`, verify that external secrets appear in the suggestions list with a Secret indicator, and confirm selection inserts `{{_secret.<name>}}`.

### Tests for User Story 3
- [ ] T021 [P] [US3] Create unit tests for `getAvailableVariables()` with external secrets in `ui-desktop/src/desktopTest/kotlin/io/github/crustack/clapi/ui/desktop/components/VariableHighlightSecretsTest.kt`

### Implementation for User Story 3
- [ ] T022 [US3] Update `getAvailableVariables()` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/VariableHighlight.kt` to extract enabled external secrets from `state.selectedEnvironment?.externalSecrets` and append them as `_secret.<name>` entries
- [ ] T023 [US3] Update `VariableDropdownList` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/VariableHighlight.kt` to visually distinguish secret items (e.g., rendering a distinctive Secret badge or tag)
- [ ] T024 [US3] Ensure autocomplete confirmation in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/VariableHighlight.kt` inserts `{{_secret.<name>}}` without introducing unintended whitespace

**Checkpoint**: Autocomplete displays external secrets with the required syntax and formatting.

---

## Phase 6: User Story 4 - Resolve External Secrets at Runtime During Request Execution (Priority: P1)

**Goal**: Fetch external secrets from HashiCorp Vault KV v2 API at runtime, cache by path per request run, and substitute `{{_secret.<name>}}` tokens during execution.

**Independent Test**: Configure Vault credentials and an environment with secret `client_id` pointing to `payment/foo`. Execute a request using `{{_secret.client_id}}` against a test endpoint, and verify that the dispatched request contains the resolved secret value.

### Tests for User Story 4
- [ ] T025 [P] [US4] Create unit test for `HashiCorpVaultClient` using Ktor MockEngine in `core-network/src/commonTest/kotlin/io/github/crustack/clapi/core/network/vault/HashiCorpVaultClientTest.kt`
- [ ] T026 [P] [US4] Create unit test for `SecretResolutionService` path-batching and key extraction in `core-network/src/commonTest/kotlin/io/github/crustack/clapi/core/network/vault/SecretResolutionServiceTest.kt`

### Implementation for User Story 4
- [ ] T027 [US4] Implement `HashiCorpVaultClient` interface and Ktor implementation in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/vault/HashiCorpVaultClient.kt` querying `/v1/{mount}/data/{path}` with `X-Vault-Token` and optional `X-Vault-Namespace`
- [ ] T028 [US4] Implement `SecretResolutionService` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/vault/SecretResolutionService.kt` to extract required `{{_secret.<name>}}` tokens, batch Vault calls by secret path, match keys within `data.data`, and return a resolved secret map
- [ ] T029 [US4] Update `RequestExecutionService.kt` in `feature-requests/src/commonMain/kotlin/io/github/crustack/clapi/feature/requests/RequestExecutionService.kt` to resolve secret variables and inject them into execution layers
- [ ] T030 [US4] Update `RequestExecutor.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/RequestExecutor.kt` to invoke secret resolution before HTTP and gRPC request dispatch, handling errors (unreachable Vault, missing token, non-existent secret, disabled secret) and displaying user-facing error notices

**Checkpoint**: Requests referencing external secrets seamlessly resolve secrets from HashiCorp Vault at runtime.

---

## Phase 7: User Story 5 - Portable OpenCollection Export and Import without Credential Leakage (Priority: P2)

**Goal**: Ensure collections containing environments with external secrets import and export correctly according to the OpenCollection v1.0.0 specification, while never leaking Vault credentials into exported files.

**Independent Test**: Export a collection containing an environment with external secrets to JSON/YAML. Verify that `externalSecrets` declarations are serialized while Vault tokens and server URLs are completely absent. Re-import and verify external secrets are restored.

### Tests for User Story 5
- [ ] T031 [P] [US5] Create round-trip serialization and zero-credential-leakage tests in `core-storage/src/commonTest/kotlin/io/github/crustack/clapi/core/storage/opencollection/OpenCollectionExternalSecretsTest.kt`

### Implementation for User Story 5
- [ ] T032 [US5] Update `OpenCollectionSerializer.kt` in `core-storage/src/commonMain/kotlin/io/github/crustack/clapi/core/storage/opencollection/OpenCollectionSerializer.kt` to serialize and deserialize `externalSecrets` in environment blocks
- [ ] T033 [US5] Update `ImportExportRepository.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/ImportExportRepository.kt` to preserve `externalSecrets` during collection import/export while ensuring provider credentials are strictly excluded

**Checkpoint**: Collections with external secrets export and import losslessly without leaking credentials.

---

## Phase 8: User Story 6 - Automated QA Integration Tests with Vault Docker Service (Priority: P2)

**Goal**: Provide automated end-to-end regression tests running against the local HashiCorp Vault Docker container (`qa-tests/secret-managers-compose.yml`).

**Independent Test**: Start the Vault container and run `./gradlew :qa-tests:test --tests "io.github.crustack.clapi.qa.ExternalSecretsIntegrationTest"` to verify end-to-end secret resolution.

### Tests & Implementation for User Story 6
- [ ] T034 [P] [US6] Add sample environment with `externalSecrets` (`client_id`, `client_secret` at path `payment/foo`) to `qa-tests/fixtures/clapi-test-collection.json`
- [ ] T035 [US6] Implement `ExternalSecretsIntegrationTest.kt` in `qa-tests/src/test/kotlin/io/github/crustack/clapi/qa/ExternalSecretsIntegrationTest.kt` verifying connection to Vault (`http://127.0.0.1:8200`, `root-dev-token`), fetching `payment/foo`, secret substitution in requests, and error reporting for invalid tokens and missing paths

**Checkpoint**: Automated regression suite verifies external secret resolution against live HashiCorp Vault container.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Cleanup, formatting, security validation, and documentation

- [ ] T036 [P] Run linter and formatting across modified modules (`core-model`, `core-network`, `core-storage`, `feature-requests`, `ui-shared`, `qa-tests`)
- [ ] T037 Perform security audit to ensure Vault tokens are never written to unencrypted log files or collection export files
- [ ] T038 Execute quickstart scenarios per `specs/006-external-secret-variables/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3-8)**:
  - US1 (Global Settings & ksafe) depends on Phase 2
  - US2 (Environment Editor) depends on Phase 2
  - US3 (Autocomplete) depends on US2
  - US4 (Runtime Resolution & Ktor) depends on US1 and US2
  - US5 (OpenCollection Import/Export) depends on US2
  - US6 (QA Tests & Docker) depends on US4 and US5
- **Polish (Phase 9)**: Depends on all user stories being complete

### User Story Completion Order

```text
Foundational (Phase 2)
      │
      ├──► US1: Global Settings & ksafe (P1) [MVP]
      │         │
      │         ├───────────────────────────┐
      │         ▼                           ▼
      ├──► US2: Environment Editor (P1)   US4: Runtime Resolution & Ktor (P1)
      │         │                           │
      │         ├──────────────┐            │
      │         ▼              ▼            │
      │     US3: Autocomplete  US5: Import/ │
      │         (P2)           Export (P2)  │
      │                        │            │
      └────────────────────────┴────────────┴──► US6: QA Tests with Vault (P2)
                                                       │
                                                       ▼
                                                 Polish (Phase 9)
```

---

## Parallel Execution Opportunities

- **Phase 1**: T001, T002, T003 can be prepared in parallel
- **Phase 2**: T004/T005 (`PlatformStorage` secret extension), T006/T007 (`core-model`), and T008 (`VaultModels`) can run in parallel
- **Phase 3 (US1)**: T009/T010 (unit tests) and T013 (settings section enum) can run in parallel before T014 (dialog UI)
- **Phase 4 (US2)**: T015 (test) and T016 (`MutableExternalSecret`) can run in parallel
- **Phase 5 (US3)**: T021 (test) and T022/T023 can run in parallel
- **Phase 6 (US4)**: T025/T026 (tests) and T027/T028 (`HashiCorpVaultClient`/`SecretResolutionService`) can run in parallel
- **Phase 7 (US5)**: T031 (test) and T032/T033 can run in parallel
- **Phase 8 (US6)**: T034 (fixture JSON) and T035 (test setup) can run in parallel

---

## Implementation Strategy

### MVP First (User Story 1 Only)
1. Complete Phase 1: Setup dependencies (`ksafe`)
2. Complete Phase 2: Foundational storage extensions (`putSecretString`) and core models
3. Complete Phase 3: User Story 1 (Secrets Manager settings tab, encrypted token persistence)
4. **Validate**: Verify Vault credentials can be entered, token is stored encrypted in `ksafe`, and settings reload cleanly.

### Incremental Delivery
1. Add User Story 2: Environment Editor tab for external secret declarations
2. Add User Story 3: Autocomplete suggestions for `{{_secret.<name>}}`
3. Add User Story 4: Runtime secret fetching via Ktor and request variable substitution
4. Add User Story 5: OpenCollection import/export with credential-free portability
5. Add User Story 6: Automated QA integration test suite running against Docker Vault container
