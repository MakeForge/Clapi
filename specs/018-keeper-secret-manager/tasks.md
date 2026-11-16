# Tasks: Keeper Security Cloud Secret Manager Integration

**Input**: Design documents from `specs/018-keeper-secret-manager/` (`spec.md`, `plan.md`, `data-model.md`, `contracts/`, `research.md`, `quickstart.md`)

**Prerequisites**: `plan.md` (required), `spec.md` (required for user stories), `data-model.md`, `contracts/`, `research.md`, `quickstart.md`

**Tests**: Test tasks are included as specified in user requirements and project quality standards.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `- [ ] [TaskID] [P?] [Story?] Description with file path`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., [US1], [US2], [US3], [US4], [US5])
- Exact file paths included in all task descriptions

---

## Phase 1: Setup (Dependencies & Infrastructure)

**Purpose**: Project dependency configuration for Keeper Secrets Manager SDK

- [X] T001 Add `keeper-secrets-manager` core library (`com.keepersecurity.secrets-manager:core:17.2.0`) to `gradle/libs.versions.toml`
- [X] T002 Add `libs.keeper.secrets.manager.core` dependency under `desktopMain` in `core-network/build.gradle.kts`
- [X] T003 [P] Create package directory structure for Keeper Secret Manager in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/keeper/` and `core-network/src/desktopMain/kotlin/io/github/crustack/clapi/core/network/keeper/`

---

## Phase 2: Foundational (Core Models & Client Infrastructure)

**Purpose**: Core data models, OpenCollection schema updates, and base Keeper client abstraction that block all user stories

**⚠️ CRITICAL**: No user story work can begin until this foundational phase is complete

- [X] T004 Add `KEEPER_SECURITY_CLOUD("keeper-security-cloud")` enum value with `@SerialName("keeper-security-cloud")` to `ExternalSecretType` in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/OpenCollectionEnvironment.kt`
- [X] T005 Implement `KeeperSecurityExternalSecret` data class (`name: String`, `recordTitle: String = ""`, `targetField: String = "password"`, `customFilters: Map<String, String> = emptyMap()`, `description: Description? = null`, `disabled: Boolean? = null`) implementing `ExternalSecretVariable` in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/OpenCollectionEnvironment.kt`
- [X] T006 Update `ExternalSecretVariableSerializer` polymorphic deserializer in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/OpenCollectionEnvironment.kt` to deserialize `KeeperSecurityExternalSecret` when `type == "keeper-security-cloud"` or `obj.containsKey("recordTitle")`
- [X] T007 Update OpenCollection JSON schema in `core-storage/src/commonMain/resources/opencollection/opencollection_v1.0.0.json` adding `"keeper-security-cloud"` to `ExternalSecrets.properties.type.enum`, referencing `KeeperSecurityExternalSecret` in `ExternalSecretVariable.oneOf`, and defining the `KeeperSecurityExternalSecret` schema object
- [X] T008 [P] Create `KeeperClientConfig` serializable data class (`hostname: String`, `clientId: String`, `privateKey: String`, `appKey: String? = null`, `clientKey: String? = null`, `appOwnerPublicKey: String? = null`, `serverPublicKeyId: String? = null`, `serverPublicKey: String? = null`) in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/keeper/KeeperClientConfig.kt`
- [X] T009 [P] Create `KeeperClient` interface (`suspend fun getSecrets(recordsFilter: List<String> = emptyList()): KeeperSecrets`, `suspend fun testConnection(): Boolean`, `suspend fun disconnect()`) in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/keeper/KeeperClient.kt`
- [X] T010 Implement `KeeperSecretsManagerClient` in `core-network/src/desktopMain/kotlin/io/github/crustack/clapi/core/network/keeper/KeeperSecretsManagerClient.kt` wrapping KSM SDK `SecretsManager.getSecrets` using `InMemoryStorage` initialized with the serialized client configuration JSON
- [X] T011 Implement `KeeperStorageInitializer.initializeAndBind(oneTimeToken: String): String` in `core-network/src/desktopMain/kotlin/io/github/crustack/clapi/core/network/keeper/KeeperStorageInitializer.kt` running `initializeStorage` and initial read binding to produce the complete client configuration JSON
- [X] T012 [P] Create unit tests for `KeeperSecurityExternalSecret` serialization and OpenCollection schema roundtrip in `core-storage/src/commonTest/kotlin/io/github/crustack/clapi/core/storage/opencollection/OpenCollectionExternalSecretsTest.kt`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Connect Keeper Secrets Manager in Global Settings (Priority: P1) 🎯 MVP

**Goal**: Allow users to select Keeper Security Cloud in Settings -> Secrets Manager, enter a One-Time Token (OTT), click "Connect" to bind credentials, encrypt client config in KSafe, see brief connection health status, and view documentation links.

**Independent Test**: Open Settings dialog -> Secrets Manager, select Keeper Security Cloud. Enter a one-time token, click "Connect". Verify that the token is bound, client config is persisted encrypted in KSafe, the input is cleared, and connection status is confirmed. Reopen settings to verify health indicator check.

### Tests for User Story 1
- [X] T013 [P] [US1] Create unit tests for Keeper settings persistence, KSafe encryption of client config, and OTT non-storage in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/KeeperSettingsTest.kt`

### Implementation for User Story 1
- [X] T014 [US1] Declare `openUrlInBrowser(url: String)` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/platform/PlatformApi.kt` and implement it in `ui-shared/src/desktopMain/kotlin/io/github/crustack/clapi/ui/shared/platform/PlatformApi.desktop.kt` using `Desktop.getDesktop().browse(URI.create(url))`
- [X] T015 [US1] Add `keeperConfigJson` (with encrypted backing), `keeperConnectionStatus`, and `KeeperConnectionStatus` enum to `AppSettings` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppSettings.kt`
- [X] T016 [US1] Update `SettingsRepository.load` and `SettingsRepository.save` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/SettingsRepository.kt` to persist `keeperConfig` using `PlatformStorage.putSecretString` and `PlatformStorage.getSecretString`
- [X] T017 [US1] Add `ExternalSecretType.KEEPER_SECURITY_CLOUD` to provider dropdown and build `KeeperSecretsSettings` composable in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/SettingsDialog.kt` with OTT input, "Connect" button, brief connection check, documentation guidance hint, and clickable `?` icon linking to `https://docs.keeper.io/keeperpam/secrets-manager/quick-start-guide#create-a-secrets-manager-application`

**Checkpoint**: User Story 1 is fully functional as an MVP increment. Keeper can be connected and verified from Global Settings.

---

## Phase 4: User Story 2 - Define Keeper External Secrets in Environment Editor (Priority: P1)

**Goal**: Allow users to configure `keeper-security-cloud` external secrets in the Environment Editor using a table layout similar to KeePassXC, with Record Title/UID, Target Field (default hint `"password"`), Status indicator, Description, and expandable custom filters (default key hint `'url'`).

**Independent Test**: In Environment Editor -> External Secrets, select Keeper Security Cloud. Add rows for `clapi-test-user` (target field `password`) and `clapi-test-custom-client-credentials` (custom filter `clientId = clapi-client-id`), then save. Verify environment persistence and OpenCollection serialization.

### Tests for User Story 2
- [X] T018 [P] [US2] Create unit tests for Environment Editor Keeper mapping, table row binding, and custom filter synchronization in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/EnvironmentEditorKeeperTest.kt`

### Implementation for User Story 2
- [X] T019 [US2] Update `EnvState.toOpenCollectionEnvironment` and `Environment.toEnvState` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/OpenCollectionModelMapping.kt` to map `KeeperSecurityExternalSecret` bidirectionally with `MutableExternalSecret`
- [X] T020 [US2] Update `ImportExportRepository.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/ImportExportRepository.kt` to support `keeper-security-cloud` provider serialization without leaking credentials
- [X] T021 [US2] Add `keeper-security-cloud` option to provider dropdown, configure table headers (`NAME`, `RECORD / TITLE`, `TARGET FIELD`, `STATUS`, `FILTERS`, `DESCRIPTION`), and implement `EnvKeeperSecretRow` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/EnvironmentEditDialog.kt` with target field hint `"password"` and custom filters panel with key hint `'url'`

**Checkpoint**: User Stories 1 and 2 work together. Users can define Keeper external secrets in environments and export them safely.

---

## Phase 5: User Story 3 - Resolve Keeper Secrets at Request Execution Runtime (Priority: P1)

**Goal**: Dynamically resolve `{{_secret.<name>}}` and `secret: true` variables from Keeper Security Cloud during request execution, supporting standard types (`login`, `serverCredentials`), custom types (`OAuth Client Credentials`), dotted host properties, and Keeper Notation.

**Independent Test**: Configure an environment with Keeper external secrets. Send an HTTP request referencing `{{_secret.clapi_user_password}}` and `{{_secret.oauth_client_secret}}`. Assert that the request header substitutions contain decrypted secret values from Keeper.

### Tests for User Story 3
- [X] T022 [P] [US3] Create unit tests for `KeeperRecordFilter` dynamic field matching and target field extraction across `login`, `serverCredentials`, and custom `OAuth Client Credentials` records in `core-network/src/commonTest/kotlin/io/github/crustack/clapi/core/network/keeper/KeeperRecordFilterTest.kt`
- [X] T023 [P] [US3] Create unit tests for `resolveKeeperSecrets` and error handling in `core-network/src/commonTest/kotlin/io/github/crustack/clapi/core/network/vault/SecretResolutionServiceKeeperTest.kt`
- [X] T024 [P] [US3] Create unit tests for request execution with Keeper secret resolution in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/RequestExecutorKeeperSecretsTest.kt`

### Implementation for User Story 3
- [X] T025 [US3] Implement `KeeperRecordFilter` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/keeper/KeeperRecordFilter.kt` with `matchesFilter` (matching title, type, recordUid, custom field labels, field types, and host properties) and `extractTargetFieldValue` (handling password, login, field labels, dotted host properties, and Keeper Notation)
- [X] T026 [US3] Update `SecretDeclaration` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/vault/SecretResolutionService.kt` to include `recordTitle`
- [X] T027 [US3] Implement `resolveKeeperSecrets` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/vault/SecretResolutionService.kt` with query deduplication, dynamic field filtering, target field extraction, and variable interpolation in filters
- [X] T028 [US3] Update `resolveAllSecrets` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/vault/SecretResolutionService.kt` and `feature-requests/src/commonMain/kotlin/io/github/crustack/clapi/feature/requests/RequestExecutionService.kt` to route `keeper-security-cloud` declarations to `resolveKeeperSecrets`
- [X] T029 [US3] Wire `AppState.settings.keeperConfigJson` into `resolveExternalSecretsForTab` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/executor/RequestVariableResolution.kt`

**Checkpoint**: Requests referencing Keeper external secrets resolve dynamically during execution.

---

## Phase 6: User Story 4 - Resource and Memory Optimization: No Overhead When Unused (Priority: P2)

**Goal**: Ensure zero static crypto or SDK overhead when Keeper is not used, and automatically shut down and release the Keeper client when switching away from an environment using Keeper.

**Independent Test**: Switch active environment from a Keeper environment to a non-Keeper environment. Verify `KeeperClientManager.onEnvironmentSwitched(false)` is invoked and the client instance is dropped from memory.

### Tests for User Story 4
- [X] T030 [P] [US4] Create unit tests for `KeeperClientManager` lazy client creation, lifecycle management, and `onEnvironmentSwitched` shutdown in `core-network/src/commonTest/kotlin/io/github/crustack/clapi/core/network/keeper/KeeperClientManagerTest.kt`

### Implementation for User Story 4
- [X] T031 [US4] Implement `KeeperClientManager` and `DefaultKeeperClientManager` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/keeper/KeeperClientManager.kt` managing active client instance lifecycle, lazy initialization, and shutdown
- [X] T032 [US4] Wire active environment selection in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt` so that switching away from an environment with Keeper secrets invokes `DefaultKeeperClientManager.Shared.onEnvironmentSwitched(false)`

**Checkpoint**: Only the currently active secret manager is retained in memory; unused clients and crypto state are cleaned up immediately.

---

## Phase 7: User Story 5 - Automated End-to-End Tests in `qa-tests` (Priority: P1)

**Goal**: Validate Keeper secret resolution end-to-end in `qa-tests` without Docker, using `friendPaths` to access `TestStubs` and testing all record types from `test-records.json` (`login`, `serverCredentials`, and `OAuth Client Credentials`).

**Independent Test**: Run `rtk gradlew :qa-tests:test --tests "io.github.crustack.clapi.qa.KeeperSecurityCloudIntegrationTest"`. Verify all offline E2E scenarios pass with 0 failures.

### Implementation for User Story 5
- [X] T033 [US5] Add `libs.keeper.secrets.manager.core` dependency to `jvmTest` and configure `friendPaths` for `compileTestKotlin` in `qa-tests/build.gradle.kts`:
  ```kotlin
  tasks.named<org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile>("compileTestKotlinJvm") {
      friendPaths.from(
          configurations.testCompileClasspath.map { classpath ->
              classpath.filter { file ->
                  file.name.startsWith("core-17.") && file.path.contains("keepersecurity")
              }
          }
      )
  }
  ```
- [X] T034 [US5] Implement `KeeperSecurityCloudIntegrationTest.kt` in `qa-tests/src/test/kotlin/io/github/crustack/clapi/qa/KeeperSecurityCloudIntegrationTest.kt` using `qa-tests/src/test/resources/secrets/keeper/test-records.json` and a local Netty echo server, verifying:
  1. `login` record (`clapi-test-user`): resolving `login`, `password`, `url`
  2. `serverCredentials` record (`clapi-test-server`): resolving `host.hostName`, `host.port`, `login`, `password`
  3. Custom `OAuth Client Credentials` record (`clapi-test-custom-client-credentials`): resolving `clientId`, `clientSecret`
  4. Custom filters matching and error handling for unmatched records

**Checkpoint**: Complete offline E2E test suite validates all Keeper record types and dynamic filtering.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Documentation updates, validation, and build verification

- [X] T035 [P] Update `FEATURES.md` to document Keeper Security Cloud (`keeper-security-cloud`) external secrets support
- [X] T036 Execute full quickstart validation scenarios from `specs/018-keeper-secret-manager/quickstart.md`
- [X] T037 Run full compilation and test checks (`rtk gradlew :core-model:check :core-network:check :ui-shared:check :qa-tests:test`)
