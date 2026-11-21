# Tasks: KeePassXC Secret Manager Integration

**Input**: Design documents from `specs/012-keepassxc-secret-manager/` (`spec.md`, `plan.md`, `data-model.md`, `contracts/`, `research.md`, `quickstart.md`)

**Prerequisites**: `plan.md` (required), `spec.md` (required for user stories), `data-model.md`, `contracts/`, `research.md`, `quickstart.md`

**Tests**: Test tasks are included as requested by project testing standards and user verification requirements.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `- [ ] [TaskID] [P?] [Story?] Description with file path`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., [US1], [US2], [US3], [US4])
- Exact file paths included in all descriptions

---

## Phase 1: Setup (Dependencies & Project Configuration)

**Purpose**: Dependency configuration and build setup for `keepassxc-proxy-access` library

- [X] T001 Add `keepassxc-proxy-access` dependency (`org.purejava:keepassxc-proxy-access:1.3.2`) in `gradle/libs.versions.toml`
- [X] T002 Add `libs.keepassxc.proxy.access` dependency to `desktopMain.dependencies` in `core-network/build.gradle.kts`

---

## Phase 2: Foundational (Core Models & Proxy Client Abstraction)

**Purpose**: Core data models, serialization handlers, and client interfaces that block all user stories

**⚠️ CRITICAL**: No user story work can begin until this foundational phase is complete

- [X] T003 Add `KEEPASSXC("keepassxc")` enum value to `ExternalSecretType` in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/OpenCollectionEnvironment.kt`
- [X] T004 Implement `KeePassXcExternalSecret` data class (`name: String`, `url: String`, `targetField: String = "password"`, `customFilters: Map<String, String> = emptyMap()`, `description: Description? = null`, `disabled: Boolean? = null`) implementing `ExternalSecretVariable` in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/OpenCollectionEnvironment.kt`
- [X] T005 Update `ExternalSecretVariableSerializer` polymorphic deserializer in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/OpenCollectionEnvironment.kt` to deserialize `KeePassXcExternalSecret` when `url`, `targetField`, or `customFilters` properties are present
- [X] T006 [P] Add unit tests for `ExternalSecretType.KEEPASSXC` and `KeePassXcExternalSecret` JSON and YAML round-trip serialization in `core-storage/src/commonTest/kotlin/io/github/crustack/clapi/core/storage/opencollection/OpenCollectionExternalSecretsTest.kt`
- [X] T007 [P] Define `KeePassXcEntry`, `KeePassXcStatus`, and `KeePassXcClient` interface in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/keepassxc/KeePassXcClient.kt`
- [X] T008 Implement `PureJavaKeePassXcClient` using `org.purejava.KeepassProxyAccess` with workstation association persistence in `core-network/src/desktopMain/kotlin/io/github/crustack/clapi/core/network/keepassxc/PureJavaKeePassXcClient.kt`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Configure KeePassXC External Secrets in Environment (Priority: P1) 🎯 MVP

**Goal**: Enable users to select `keepassxc` as secret provider in the Environment Editor, define entries (Name, Entry URL, Target Field, optional Custom Field Filters with variable support), and view real-time entry match counts without leaking secret values.

**Independent Test**: Open Environment Editor -> External Secrets tab, select `keepassxc`, add a secret with Entry URL `https://test.api.payment.internal` and Target Field `password`, verify that the match indicator displays `✔ 1 entry matched` with zero credential values rendered or logged, and save the environment.

### Tests for User Story 1
- [X] T009 [P] [US1] Create unit tests for KeePassXC entry matching, custom filter filtering, and non-leaking match counting in `core-network/src/commonTest/kotlin/io/github/crustack/clapi/core/network/keepassxc/KeePassXcClientTest.kt`
- [X] T010 [P] [US1] Create unit tests for `MutableExternalSecret` and `EnvState` KeePassXC mapping in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/EnvironmentEditorSecretsTest.kt`

### Implementation for User Story 1
- [X] T011 [US1] Extend `MutableExternalSecret` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt` with `url: String`, `targetField: String = "password"`, and `customFilters: SnapshotStateMap<String, String>`
- [X] T012 [US1] Update `Environment.toEnvState()` and `EnvState.toOpenCollectionEnvironment()` mapping in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/OpenCollectionModelMapping.kt` to map `KeePassXcExternalSecret` to/from `MutableExternalSecret`
- [X] T013 [US1] Update `ClapiEnvironmentDto` and persistence serialization in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/ImportExportRepository.kt` to serialize `url`, `targetField`, and `customFilters`
- [X] T014 [US1] Add `keepassxc` ("KeePassXC") to the provider dropdown options in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/EnvironmentEditDialog.kt`
- [X] T015 [US1] Implement secret configuration rows for KeePassXC in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/EnvironmentEditDialog.kt` (Name, Entry URL, Target Field, expandable Custom Field Filters table, Description, and Enabled toggle)
- [X] T016 [US1] Implement asynchronous, non-leaking entry match count indicator in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/EnvironmentEditDialog.kt` querying matching count with variable interpolation from active environment variables

**Checkpoint**: User Story 1 is functional and testable independently (MVP ready).

---

## Phase 4: User Story 2 - Resolve KeePassXC Secrets at Request Execution (Priority: P1)

**Goal**: Dynamically query KeePassXC for entries matching Entry URL, filter by custom criteria (resolving template variables), extract the specified target field, and substitute into `{{_secret.<name>}}` placeholders during request execution.

**Independent Test**: Send an HTTP request referencing `{{_secret.payment_token}}` in an active environment configured with a KeePassXC secret. Verify the request dispatches with the resolved token and execution halts with clear error diagnostics if KeePassXC is closed or locked.

### Tests for User Story 2
- [X] T017 [P] [US2] Create unit tests for KeePassXC secret resolution in `core-network/src/commonTest/kotlin/io/github/crustack/clapi/core/network/vault/SecretResolutionServiceTest.kt`
- [X] T018 [P] [US2] Create unit tests for `resolveExternalSecretsForTab` with KeePassXC provider in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/RequestExecutorSecretsTest.kt`

### Implementation for User Story 2
- [X] T019 [US2] Extend `SecretDeclaration` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/vault/SecretResolutionService.kt` with `url: String = ""`, `targetField: String = "password"`, `customFilters: Map<String, String> = emptyMap()`, and `providerType: ExternalSecretType`
- [X] T020 [US2] Implement KeePassXC secret resolution in `SecretResolutionService.kt` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/vault/SecretResolutionService.kt` (query logins by URL, apply custom field filters with variable substitution, extract target field from `password`, `login`/`username`, `totp`, or custom attribute from `stringFields`)
- [X] T021 [US2] Update `resolveExternalSecretsForTab` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/RequestExecutor.kt` to inspect `externalSecretsProvider` and dispatch KeePassXC resolution when set to `"keepassxc"`

**Checkpoint**: User Stories 1 and 2 operate together independently.

---

## Phase 5: User Story 3 - On-Demand Connection and Zero Idle Proxy Footprint (Priority: P2)

**Goal**: Guarantee that no KeePassXC proxy process or background socket connection is started or maintained when the active environment does not use KeePassXC external secrets, and ensure clean disconnection on environment switch.

**Independent Test**: Switch from a KeePassXC environment to an environment with no external secrets (or Vault); verify that `KeePassXcClientManager` disconnects and closes socket handles immediately.

### Tests for User Story 3
- [X] T022 [P] [US3] Create unit tests for `KeePassXcClientManager` lazy instantiation, environment tracking, and automatic disconnection in `core-network/src/commonTest/kotlin/io/github/crustack/clapi/core/network/keepassxc/KeePassXcClientManagerTest.kt`

### Implementation for User Story 3
- [X] T023 [US3] Implement `DefaultKeePassXcClientManager` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/keepassxc/KeePassXcClientManager.kt` handling lazy connection, reference counting, and disconnection
- [X] T024 [US3] Hook active environment selection in `AppState.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt` to call `KeePassXcClientManager.onEnvironmentSwitched` to disconnect when switching away from KeePassXC environments

**Checkpoint**: Connection lifecycle is resource-safe and leaves zero idle footprint.

---

## Phase 6: User Story 4 - Discover KeePassXC Secrets via Autocomplete (Priority: P3)

**Goal**: Display enabled KeePassXC external secrets formatted as `{{_secret.<name>}}` in the `Ctrl+Space` autocomplete suggestion popup in request editors.

**Independent Test**: In a request editor URL or header field with an active KeePassXC environment, press `Ctrl+Space`, verify that external secrets appear in the suggestions list with a Secret badge, and confirm selection inserts `{{_secret.<name>}}`.

### Tests for User Story 4
- [X] T025 [P] [US4] Create unit test in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/RequestEditorSecretsAutocompleteTest.kt` verifying KeePassXC secrets appear in autocomplete suggestions

### Implementation for User Story 4
- [X] T026 [US4] Update autocomplete proposal provider in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt` to include enabled KeePassXC external secrets formatted as `{{_secret.<name>}}`

**Checkpoint**: All user stories functional and discoverable.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, security verification against leakage, and quickstart scenario validation

- [X] T027 [P] Update documentation in `docs/` and `README.md` explaining KeePassXC secret manager setup, browser integration configuration, and entry syntax
- [X] T028 Perform credential security audit across error handling paths in `SecretResolutionService.kt` and `EnvironmentEditDialog.kt` to verify that passwords, TOTPs, and custom attributes are never logged or exposed in error messages
- [X] T029 Execute and verify all validation scenarios in `specs/012-keepassxc-secret-manager/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies
- **Setup (Phase 1)**: No dependencies - can start immediately.
- **Foundational (Phase 2)**: Depends on Phase 1 completion - BLOCKS all user stories.
- **User Stories (Phase 3+)**: Depend on Phase 2 completion.
  - Can proceed sequentially: US1 (P1) → US2 (P1) → US3 (P2) → US4 (P3).
  - Or in parallel across team members once Foundational phase completes.
- **Polish (Phase 7)**: Depends on all desired user stories being complete.

### User Story Dependencies
- **User Story 1 (P1)**: Depends on Foundational (Phase 2). Delivers standalone MVP.
- **User Story 2 (P1)**: Depends on Foundational (Phase 2) and integrates with models defined in US1.
- **User Story 3 (P2)**: Depends on Foundational (Phase 2) and client implementation in US1/US2.
- **User Story 4 (P3)**: Depends on US1 model integration.

---

## Parallel Opportunities

- **Phase 1**: T001 and T002 can be executed sequentially or in rapid succession.
- **Phase 2**: T006 and T007 can run in parallel while T003-T005 are being completed.
- **Phase 3 (US1)**: T009 and T010 test tasks can run in parallel before implementation tasks T011-T016.
- **Phase 4 (US2)**: T017 and T018 test tasks can run in parallel before implementation tasks T019-T021.
- **Phase 5 (US3)**: T022 test task can run in parallel with US3 design.
- **Phase 6 (US4)**: T025 test task can run in parallel with US4 autocomplete integration.

---

## Implementation Strategy

### MVP First (User Story 1 Only)
1. Complete Phase 1: Setup (`keepassxc-proxy-access` dependency)
2. Complete Phase 2: Foundational (Models, Serializers, and Client interface)
3. Complete Phase 3: User Story 1 (Environment Editor UI, configuration, non-leaking match count indicator)
4. **VALIDATE**: Verify User Story 1 independently in Environment Editor.

### Incremental Delivery
1. Add User Story 2 (Runtime resolution during request execution) → Test with requests.
2. Add User Story 3 (Lifecycle manager ensuring zero idle proxy footprint) → Test environment switching.
3. Add User Story 4 (Autocomplete suggestions) → Test editor autocompletion.
4. Run Phase 7 Polish & Quickstart validation.
