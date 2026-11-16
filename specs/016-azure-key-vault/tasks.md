# Tasks: Azure Key Vault Integration

**Input**: Design documents from `specs/016-azure-key-vault/` (`spec.md`, `plan.md`, `data-model.md`, `contracts/`, `research.md`, `quickstart.md`)

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

- [X] T001 [P] Create package directory structure for Azure Key Vault in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/azure/`

---

## Phase 2: Foundational (Core Models & Client Infrastructure)

**Purpose**: Core data models, Azure client interface, and client implementation that block all user stories

**⚠️ CRITICAL**: No user story work can begin until this foundational phase is complete

- [X] T002 Implement `AzureKeyVaultExternalSecret` data class (`name: String`, `secretName: String`, `vaultName: String? = null`, `targetField: String = ""`, `customFilters: Map<String, String> = emptyMap()`, `description: Description? = null`, `disabled: Boolean? = null`) implementing `ExternalSecretVariable` in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/OpenCollectionEnvironment.kt`
- [X] T003 Update `ExternalSecretVariableSerializer` polymorphic deserializer in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/OpenCollectionEnvironment.kt` to deserialize `AzureKeyVaultExternalSecret` when `vaultName` or `secretName` is present with `type: "azure-key-vault"`
- [X] T004 [P] Create `AzureKeyVaultConfig` data class (`vaultName: String`, `secretName: String`, `secretVersion: String? = null`, `propertyKey: String? = null`, `tenantId: String`, `clientId: String`, `clientSecret: String`, `authorityHost: String = "https://login.microsoftonline.com"`, `apiVersion: String = "7.4"`) in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/azure/AzureKeyVaultConfig.kt`
- [X] T005 Implement `AzureKeyVaultClient` interface and `KtorAzureKeyVaultClient` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/azure/AzureKeyVaultClient.kt` handling Entra ID OAuth2 token acquisition (with `authorityHost = "bypass"` support), HTTP GET secret retrieval, JSON object decoding, and plain text fallback
- [X] T006 [P] Create unit tests for `KtorAzureKeyVaultClient` with MockEngine verifying token acquisition, bypass mode, JSON property key extraction, and plain text fallback in `core-network/src/commonTest/kotlin/io/github/crustack/clapi/core/network/azure/AzureKeyVaultClientTest.kt`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Configure Azure Key Vault Provider in Global Settings (Priority: P1) 🎯 MVP

**Goal**: Enable users to configure Azure Key Vault connection parameters in Settings -> Secrets Manager (Vault Name/URL, Tenant ID, Client ID, Client Secret, Authority Host, API Version), with secure encrypted storage for sensitive keys.

**Independent Test**: Open Settings dialog -> Secrets Manager tab, select `Azure Key Vault`, input test credentials and authority host `bypass`, close and reopen dialog, verifying settings persist and sensitive fields remain masked.

### Tests for User Story 1
- [X] T007 [P] [US1] Create unit tests for Azure settings persistence, masking, and encrypted storage in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/AzureSettingsTest.kt`

### Implementation for User Story 1
- [X] T008 [US1] Add Azure settings properties (`azureVaultName`, `azureTenantId`, `azureClientId`, `azureClientSecret`, `azureAuthorityHost`, `azureApiVersion`) to `AppSettings` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppSettings.kt`
- [X] T009 [US1] Update `SettingsRepository.load` and `SettingsRepository.save` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/SettingsRepository.kt` to persist Azure settings using `PlatformStorage.putSecretString` for `azureClientSecret`
- [X] T010 [US1] Add `azure-key-vault` option to provider dropdown and implement `AzureKeyVaultSettings` form (Vault Name / URL, Tenant ID, Client ID, masked Client Secret, Authority Host, API Version) in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/SettingsDialog.kt`

**Checkpoint**: User Story 1 is functional and testable independently (MVP ready).

---

## Phase 4: User Story 2 - Define Azure External Secrets in Environment Editor (Priority: P1)

**Goal**: Enable users to select `azure-key-vault` as secret provider in the Environment Editor, define secrets with Name, Secret Name / ID, Target Field, Custom Filters, Description, and Enabled status matching the KeePassXC/AWS table layout, and save with OpenCollection mapping.

**Independent Test**: Open Environment Editor -> External Secrets tab, select `Azure Key Vault`, add a row with Name `client_id`, Secret Name `payment-foo`, and Target Field `client_id`, then save and verify that the environment model updates with `AzureKeyVaultExternalSecret`.

### Tests for User Story 2
- [X] T011 [P] [US2] Create unit tests for Azure external secrets mapping and UI state in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/EnvironmentEditorAzureTest.kt`

### Implementation for User Story 2
- [X] T012 [US2] Update `EnvironmentState.toOpenCollectionEnvironment()` and `Environment.toEnvState()` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/OpenCollectionModelMapping.kt` to map between `MutableExternalSecret` and `AzureKeyVaultExternalSecret`
- [X] T013 [US2] Update `ClapiEnvironmentDto` and persistence serialization in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/ImportExportRepository.kt` to serialize `targetField` and `customFilters` for `azure-key-vault`
- [X] T014 [US2] Add `azure-key-vault` to provider dropdown and configure table headers and rows in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/EnvironmentEditDialog.kt` (Name, Secret Name / ID, Target Field, Custom Filters button, Description, Enabled toggle)

**Checkpoint**: User Stories 1 and 2 operate together independently.

---

## Phase 5: User Story 3 - Resolve Azure Secrets at Runtime During Request Execution (Priority: P1)

**Goal**: Resolve `{{_secret.<name>}}` placeholders and `secret: true` environment variables against Azure Key Vault during request dispatch with query deduplication, JSON target field extraction, custom filters, and error reporting.

**Independent Test**: Send an HTTP request referencing `{{_secret.client_id}}` and `{{_secret.client_secret}}` sharing secret name `payment-foo`. Verify the request executes with substituted values from Lowkey Vault and issues only one query to Azure Key Vault.

### Tests for User Story 3
- [X] T015 [P] [US3] Create unit tests for Azure secret resolution and query deduplication by Secret Name in `core-network/src/commonTest/kotlin/io/github/crustack/clapi/core/network/vault/SecretResolutionServiceAzureTest.kt`
- [X] T016 [P] [US3] Create unit tests for `resolveExternalSecretsForTab` with `azure-key-vault` provider in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/RequestExecutorAzureSecretsTest.kt`

### Implementation for User Story 3
- [X] T017 [US3] Implement `resolveAzureSecrets` in `SecretResolutionService.kt` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/vault/SecretResolutionService.kt` (batching by Secret Name, querying `AzureKeyVaultClient`, extracting `targetField` from JSON map or raw string, and evaluating custom filters with variable substitution)
- [X] T018 [US3] Update `resolveAllSecrets` in `SecretResolutionService.kt` and `RequestExecutionService.kt` in `feature-requests/src/commonMain/kotlin/io/github/crustack/clapi/feature/requests/RequestExecutionService.kt` to accept Azure connection settings and dispatch resolution for `azure-key-vault` declarations
- [X] T019 [US3] Wire Azure settings credentials from `AppState.settings` and dispatch Azure resolution in `resolveExternalSecretsForTab` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/executor/RequestVariableResolution.kt`

**Checkpoint**: User Stories 1, 2, and 3 provide end-to-end secret resolution during live request dispatch.

---

## Phase 6: User Story 4 - Discover and Insert Azure Secrets via Autocomplete (Priority: P2)

**Goal**: Display enabled Azure external secrets in request editor text fields via `Ctrl+Space` autocompletion with `{{_secret.<name>}}` syntax and external-secret badges.

**Independent Test**: In request editor URL bar with an active Azure secret environment, press `Ctrl+Space`, verify `{{_secret.<name>}}` appears in the dropdown with a secret badge, and select it to insert.

### Tests for User Story 4
- [X] T020 [P] [US4] Create unit tests for Azure external secrets in request autocomplete in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/RequestEditorAzureSecretsAutocompleteTest.kt`

### Implementation for User Story 4
- [X] T021 [US4] Verify and update autocomplete suggestion builder in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/VariableHighlight.kt` to include enabled Azure external secrets with `_secret` prefix and secret badge

**Checkpoint**: Autocomplete discoverability works seamlessly for Azure external secrets.

---

## Phase 7: User Story 5 - Portable OpenCollection Export and Import without Credential Leakage (Priority: P2)

**Goal**: Ensure collections containing Azure external secrets serialize to valid OpenCollection JSON and YAML documents with zero credential leakage (no client secrets, tenant IDs, client IDs, or local tokens).

**Independent Test**: Export a collection with an Azure external secret environment to JSON and YAML. Inspect exported files to verify `externalSecrets` structure is present and no Azure credentials or secret payloads are included.

### Tests for User Story 5
- [X] T022 [P] [US5] Add unit tests for `AzureKeyVaultExternalSecret` JSON and YAML round-trip serialization and credential leakage prevention in `core-storage/src/commonTest/kotlin/io/github/crustack/clapi/core/storage/opencollection/OpenCollectionExternalSecretsTest.kt`

### Implementation for User Story 5
- [X] T023 [US5] Update `opencollection_v1.0.0.json` schema in `core-storage/src/commonMain/resources/opencollection/opencollection_v1.0.0.json` to define `secretName`, `vaultName`, `targetField`, and `customFilters` on `AzureKeyVaultExternalSecret`

**Checkpoint**: OpenCollection documents are portable, standards-compliant, and leak zero credentials.

---

## Phase 8: User Story 6 - Automated Lowkey Vault End-to-End Verification in QA Test Suite (Priority: P2)

**Goal**: Provide an automated end-to-end integration test in `qa-tests` running against a containerized Lowkey Vault instance verifying live client resolution and HTTP request execution.

**Independent Test**: Execute `./gradlew :qa-tests:jvmTest --tests "io.github.crustack.clapi.qa.AzureKeyVaultIntegrationTest"` against running Lowkey Vault container and verify all assertions pass.

### Implementation & Tests for User Story 6
- [X] T024 [P] [US6] Add Lowkey Vault service `azure-keyvault` and seeder container `azure-vault-seeder` to `qa-tests/docker/secret-managers-compose.yml` seeding `payment-foo` with `{"client_id":"azure_client_999","client_secret":"sk_azure_secret_888"}` and `plain-token` with `super-secret-plain-token-12345`
- [X] T025 [P] [US6] Add `Azure Staging` environment with `azure-key-vault` external secrets to test fixture in `qa-tests/fixtures/clapi-test-collection.json`
- [X] T026 [US6] Implement end-to-end integration test in `qa-tests/src/test/kotlin/io/github/crustack/clapi/qa/AzureKeyVaultIntegrationTest.kt` verifying live secret retrieval, multi-value JSON target field extraction, plaintext secret retrieval, request substitution, and error handling for missing secrets against Lowkey Vault

**Checkpoint**: CI and automated test pipelines verify Azure Key Vault integration against Lowkey Vault.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Improvements and validation that cross multiple user stories

- [X] T027 Execute full validation walkthrough per `specs/016-azure-key-vault/quickstart.md`
- [X] T028 [P] Verify error diagnostics and ensure zero secret or credential leakage across logs and error toasts in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/azure/AzureKeyVaultClient.kt`
- [X] T029 Run complete project test suite `./gradlew test` to ensure zero regressions across all modules

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
   (Autocomplete)     (Export)      (Lowkey Vault QA)
         │               │               │
         └───────────────┼───────────────┘
                         ▼
                  Phase 9: Polish
```

---

## Parallel Opportunities

- **Phase 1 & 2**: T001, T004 (`AzureKeyVaultConfig`), and T006 (Unit tests) can run in parallel once T002/T003/T005 are established.
- **Phase 3 & 4**: User Story 1 (Settings UI) and User Story 2 (Environment Editor UI) touch separate components and can be implemented in parallel once Phase 2 completes.
- **Phase 6, 7 & 8**: User Story 4 (Autocomplete), User Story 5 (Export/Import), and User Story 6 (QA Lowkey Vault integration test) can run in parallel once User Story 3 completes.

---

## Implementation Strategy

### MVP First (User Story 1 & 2 & 3 Core Flow)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (`AzureKeyVaultExternalSecret`, `AzureKeyVaultConfig`, `KtorAzureKeyVaultClient`)
3. Complete Phase 3: User Story 1 (Settings UI)
4. Complete Phase 4: User Story 2 (Environment Editor UI)
5. Complete Phase 5: User Story 3 (Runtime Secret Resolution)
6. **STOP and VALIDATE**: Verify end-to-end secret resolution against Lowkey Vault with manual request dispatch.

### Incremental Delivery

1. Foundation + US1 + US2 + US3 -> Core Working Secret Resolution (MVP!)
2. Add US4 -> Discoverability via Autocomplete
3. Add US5 -> Portable OpenCollection Export/Import
4. Add US6 -> Automated Lowkey Vault QA Regression Suite
5. Polish & Final Validation
