# Tasks: AWS Secrets Manager Integration

**Input**: Design documents from `specs/015-aws-secrets-manager/` (`spec.md`, `plan.md`, `data-model.md`, `contracts/`, `research.md`, `quickstart.md`)

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

- [X] T001 Verify and add Ktor client dependencies in `core-network/build.gradle.kts` if needed for AWS HTTP communication
- [X] T002 [P] Create package directory structure for AWS Secrets Manager in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/aws/`

---

## Phase 2: Foundational (Core Models & Client Infrastructure)

**Purpose**: Core data models, AWS SigV4 signer, and client interface that block all user stories

**⚠️ CRITICAL**: No user story work can begin until this foundational phase is complete

- [X] T003 Implement `AwsSecretsManagerExternalSecret` data class (`name: String`, `secretName: String`, `targetField: String = ""`, `customFilters: Map<String, String> = emptyMap()`, `description: Description? = null`, `disabled: Boolean? = null`) implementing `ExternalSecretVariable` in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/OpenCollectionEnvironment.kt`
- [X] T004 Update `ExternalSecretVariableSerializer` polymorphic deserializer in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/OpenCollectionEnvironment.kt` to deserialize `AwsSecretsManagerExternalSecret` when `secretName` or `secretId` is present
- [X] T005 [P] Create `AwsSigV4Signer` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/aws/AwsSigV4Signer.kt` implementing AWS Signature Version 4 HMAC-SHA256 signing for `secretsmanager` service RPC POST requests
- [X] T006 [P] Create `AwsSecretsManagerConfig` data class (`secretId: String`, `accessKey: String`, `secretKey: String`, `region: String = "us-east-1"`, `endpointUrl: String? = null`, `sessionToken: String? = null`, `versionStage: String? = "AWSCURRENT"`) in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/aws/AwsSecretsManagerConfig.kt`
- [X] T007 Implement `AwsSecretsManagerClient` interface and `KtorAwsSecretsManagerClient` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/aws/AwsSecretsManagerClient.kt` sending `secretsmanager.GetSecretValue` via Ktor `HttpClient` and parsing `SecretString` as a JSON map or raw string
- [X] T008 [P] Create unit tests for `AwsSigV4Signer` and `KtorAwsSecretsManagerClient` with MockEngine in `core-network/src/commonTest/kotlin/io/github/crustack/clapi/core/network/aws/AwsSecretsManagerClientTest.kt`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Configure AWS Secrets Manager Provider in Global Settings (Priority: P1) 🎯 MVP

**Goal**: Enable users to configure AWS connection parameters in Settings -> Secrets Manager (Region, Access Key ID, Secret Access Key, Session Token, Endpoint URL Override, Version Stage), with secure encrypted storage for sensitive keys.

**Independent Test**: Open Settings dialog -> Secrets Manager tab, select `AWS Secrets Manager`, input test credentials and endpoint URL `http://localhost:4566`, close and reopen dialog, verifying settings persist and sensitive fields remain masked.

### Tests for User Story 1
- [X] T009 [P] [US1] Create unit tests for AWS settings persistence, masking, and encrypted storage in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/SecretsManagerSettingsTest.kt`

### Implementation for User Story 1
- [X] T010 [US1] Add AWS settings properties (`awsRegion`, `awsAccessKeyId`, `awsSecretAccessKey`, `awsSessionToken`, `awsEndpointUrl`, `awsVersionStage`) to `AppSettings` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppSettings.kt`
- [X] T011 [US1] Update `SettingsRepository.load` and `SettingsRepository.save` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/SettingsRepository.kt` to persist AWS settings using `PlatformStorage.putSecretString` for `awsSecretAccessKey` and `awsSessionToken`
- [X] T012 [US1] Add `aws-secrets-manager` option to provider dropdown and implement `AwsSecretsSettings` form (Region, Access Key ID, masked Secret Access Key, masked Session Token, Endpoint URL Override, Version Stage) in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/SettingsDialog.kt`

**Checkpoint**: User Story 1 is functional and testable independently (MVP ready).

---

## Phase 4: User Story 2 - Define AWS External Secrets in Environment Editor (Priority: P1)

**Goal**: Enable users to select `aws-secrets-manager` as secret provider in the Environment Editor, define secrets with Name, Secret ID / Name, Target Field, Custom Filters, Description, and Enabled status matching the KeePassXC table layout, and save with OpenCollection mapping.

**Independent Test**: Open Environment Editor -> External Secrets tab, select `AWS Secrets Manager`, add a row with Name `client_id`, Secret ID `local/payment/foo`, and Target Field `client_id`, then save and verify that the environment model updates with `AwsSecretsManagerExternalSecret`.

### Tests for User Story 2
- [X] T013 [P] [US2] Create unit tests for AWS external secrets mapping and UI state in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/EnvironmentEditorSecretsTest.kt`

### Implementation for User Story 2
- [X] T014 [US2] Update `EnvironmentState.toOpenCollectionEnvironment()` and `Environment.toEnvState()` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/OpenCollectionModelMapping.kt` to map between `MutableExternalSecret` and `AwsSecretsManagerExternalSecret`
- [X] T015 [US2] Update `ClapiEnvironmentDto` and persistence serialization in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/ImportExportRepository.kt` to serialize `targetField` and `customFilters` for `aws-secrets-manager`
- [X] T016 [US2] Add `aws-secrets-manager` to provider dropdown and configure table headers and rows in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/EnvironmentEditDialog.kt` (Name, Secret ID / Name, Target Field, Custom Filters button, Description, Enabled toggle)

**Checkpoint**: User Stories 1 and 2 operate together independently.

---

## Phase 5: User Story 3 - Resolve AWS Secrets at Runtime During Request Execution (Priority: P1)

**Goal**: Resolve `{{_secret.<name>}}` placeholders and `secret: true` environment variables against AWS Secrets Manager during request dispatch with query deduplication, JSON target field extraction, custom filters, and error reporting.

**Independent Test**: Send an HTTP request referencing `{{_secret.client_id}}` and `{{_secret.client_secret}}` sharing secret ID `local/payment/foo`. Verify the request executes with substituted values from LocalStack and issues only one query to AWS Secrets Manager.

### Tests for User Story 3
- [X] T017 [P] [US3] Create unit tests for AWS secret resolution and query deduplication by Secret ID in `core-network/src/commonTest/kotlin/io/github/crustack/clapi/core/network/vault/SecretResolutionServiceTest.kt`
- [X] T018 [P] [US3] Create unit tests for `resolveExternalSecretsForTab` with `aws-secrets-manager` provider in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/RequestExecutorSecretsTest.kt`

### Implementation for User Story 3
- [X] T019 [US3] Extend `SecretDeclaration` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/vault/SecretResolutionService.kt` to ensure `path`, `targetField`, `customFilters`, and `secretId` are exposed for AWS secrets
- [X] T020 [US3] Implement `resolveAwsSecrets` in `SecretResolutionService.kt` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/vault/SecretResolutionService.kt` (batching by secret ID, querying `AwsSecretsManagerClient`, extracting `targetField` from JSON map or raw string, and evaluating custom filters with variable substitution)
- [X] T021 [US3] Wire AWS settings credentials from `AppState.settings` and dispatch AWS resolution in `resolveExternalSecretsForTab` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/executor/RequestVariableResolution.kt`

**Checkpoint**: User Stories 1, 2, and 3 provide end-to-end secret resolution during live request dispatch.

---

## Phase 6: User Story 4 - Discover and Insert AWS Secrets via Autocomplete (Priority: P2)

**Goal**: Display enabled AWS external secrets in request editor text fields via `Ctrl+Space` autocompletion with `{{_secret.<name>}}` syntax and external-secret badges.

**Independent Test**: In request editor URL bar with an active AWS secret environment, press `Ctrl+Space`, verify `{{_secret.<name>}}` appears in the dropdown with a secret badge, and select it to insert.

### Tests for User Story 4
- [X] T022 [P] [US4] Create unit tests for AWS external secrets in request autocomplete in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/RequestEditorSecretsAutocompleteTest.kt`

### Implementation for User Story 4
- [X] T023 [US4] Verify and update autocomplete suggestion builder in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/VariableHighlight.kt` to include enabled AWS external secrets with `_secret` prefix and secret badge

**Checkpoint**: Autocomplete discoverability works seamlessly for AWS external secrets.

---

## Phase 7: User Story 5 - Portable OpenCollection Export and Import without Credential Leakage (Priority: P2)

**Goal**: Ensure collections containing AWS external secrets serialize to valid OpenCollection JSON and YAML documents with zero credential leakage (no access keys, secret keys, session tokens, or local endpoints).

**Independent Test**: Export a collection with an AWS external secret environment to JSON and YAML. Inspect exported files to verify `externalSecrets` structure is present and no AWS credentials or secret payloads are included.

### Tests for User Story 5
- [ ] T024 [P] [US5] Add unit tests for `AwsSecretsManagerExternalSecret` JSON and YAML round-trip serialization and credential leakage prevention in `core-storage/src/commonTest/kotlin/io/github/crustack/clapi/core/storage/opencollection/OpenCollectionExternalSecretsTest.kt`

### Implementation for User Story 5
- [ ] T025 [US5] Ensure `OpenCollectionSerializer` in `core-storage/src/commonMain/kotlin/io/github/crustack/clapi/core/storage/opencollection/OpenCollectionSerializer.kt` serializes `AwsSecretsManagerExternalSecret` with `type: "aws-secrets-manager"` and `secretName` property in conformance with schema definitions

**Checkpoint**: OpenCollection documents are portable, standards-compliant, and leak zero credentials.

---

## Phase 8: User Story 6 - Automated LocalStack End-to-End Verification in QA Test Suite (Priority: P2)

**Goal**: Provide an automated end-to-end integration test in `qa-tests` running against a containerized LocalStack Secrets Manager instance verifying live client resolution and HTTP request execution.

**Independent Test**: Execute `./gradlew :qa-tests:test --tests "io.github.crustack.clapi.qa.AwsSecretsManagerIntegrationTest"` against running LocalStack container and verify all assertions pass.

### Implementation & Tests for User Story 6
- [ ] T026 [P] [US6] Add LocalStack `secretsmanager` service and seeder container to `qa-tests/docker/secret-managers-compose.yml` seeding `local/payment/foo` with `{"client_id":"foo_client_123","client_secret":"sk_live_secret_456"}`
- [ ] T027 [P] [US6] Add `AWS Staging` environment with `aws-secrets-manager` external secrets to test fixture in `qa-tests/fixtures/clapi-test-collection.json`
- [ ] T028 [US6] Implement end-to-end integration test in `qa-tests/src/test/kotlin/io/github/crustack/clapi/qa/AwsSecretsManagerIntegrationTest.kt` verifying live secret retrieval, multi-value JSON target field extraction, request substitution, and error handling for missing secrets against LocalStack

**Checkpoint**: CI and automated test pipelines verify AWS Secrets Manager integration against LocalStack.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Improvements and validation that cross multiple user stories

- [ ] T029 Execute full validation walkthrough per `specs/015-aws-secrets-manager/quickstart.md`
- [ ] T030 [P] Verify error diagnostics and ensure zero secret or credential leakage across logs and error toasts in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/vault/SecretResolutionService.kt`
- [ ] T031 Run complete project test suite `./gradlew test` to ensure zero regressions across all modules

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
   (Autocomplete)     (Export)      (LocalStack QA)
         │               │               │
         └───────────────┼───────────────┘
                         ▼
                  Phase 9: Polish
```

---

## Parallel Opportunities

- **Phase 1**: T001 and T002 can run in parallel.
- **Phase 2**: T005 (`AwsSigV4Signer`), T006 (`AwsSecretsManagerConfig`), and T008 (Unit tests) can run in parallel once T003/T004/T007 are established.
- **Phase 3 & 4**: User Story 1 (Settings UI) and User Story 2 (Environment Editor UI) touch separate components and can be implemented in parallel once Phase 2 completes.
- **Phase 6, 7 & 8**: User Story 4 (Autocomplete), User Story 5 (Export/Import), and User Story 6 (QA LocalStack integration test) can run in parallel once User Story 3 completes.

---

## Parallel Example: User Story 1 & Foundational

```bash
# Launch Foundational models and signer in parallel:
Task: "Implement AwsSigV4Signer in core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/aws/AwsSigV4Signer.kt"
Task: "Implement AwsSecretsManagerConfig in core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/aws/AwsSecretsManagerConfig.kt"

# Launch Settings tests and Environment mapping tests in parallel:
Task: "Create unit tests in ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/SecretsManagerSettingsTest.kt"
Task: "Create unit tests in ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/EnvironmentEditorSecretsTest.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 & 2 & 3 Core Flow)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (`AwsSecretsManagerExternalSecret`, `AwsSigV4Signer`, `KtorAwsSecretsManagerClient`)
3. Complete Phase 3: User Story 1 (Settings UI)
4. Complete Phase 4: User Story 2 (Environment Editor UI)
5. Complete Phase 5: User Story 3 (Runtime Secret Resolution)
6. **STOP and VALIDATE**: Verify end-to-end secret resolution against LocalStack with manual request dispatch.

### Incremental Delivery

1. Foundation + US1 + US2 + US3 -> Core Working Secret Resolution (MVP!)
2. Add US4 -> Discoverability via Autocomplete
3. Add US5 -> Portable OpenCollection Export/Import
4. Add US6 -> Automated LocalStack QA Regression Suite
5. Polish & Final Validation
