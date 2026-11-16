---
description: "Task list for Insomnia v5 Collection & Environment Import"
---

# Tasks: Insomnia v5 Collection & Environment Import

**Input**: Design documents from `/specs/025-import-insomnia-collection/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/)
**Tests**: Automated tests are explicitly required by the specification across unit, UI, and QA test modules.
**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `- [ ] [ID] [P?] [Story?] Description with file path`

- **[P]**: Can run in parallel (different files, no blocking dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Exact file paths are specified for every task

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Core model extensions and test fixture preparation.

- [ ] T001 Add `INSOMNIA` and `POSTMAN` values to `SpecFormat` enum in `core-model/src/commonMain/kotlin/io/github/makeforge/clapi/core/model/importers/ImportModels.kt`
- [ ] T002 [P] Create test fixtures for Insomnia v5 export files (collection YAML, spec YAML, and environment JSON) in `core-storage/src/commonTest/resources/fixtures/insomnia/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core Insomnia deserialization models and script translation engine.

- [ ] T003 Define Insomnia v5 data transfer models (`InsomniaV5Document`, `InsomniaItem`, `InsomniaFolder`, `InsomniaHttpRequest`, `InsomniaGrpcRequest`, `InsomniaWebSocketRequest`, `InsomniaEnvironments`, `InsomniaAuth`, `InsomniaBody`) in `core-storage/src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/importers/InsomniaModels.kt`
- [ ] T004 Implement `InsomniaScriptTranslator` with regex and token replacement converting `insomnia.*` to `clapi.*` namespaces in `core-storage/src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/importers/InsomniaScriptTranslator.kt`
- [ ] T005 [P] Unit tests for `InsomniaScriptTranslatorTest` validating variable, assertion, and request script translation in `core-storage/src/commonTest/kotlin/io/github/makeforge/clapi/core/storage/importers/InsomniaScriptTranslatorTest.kt`

**Checkpoint**: Foundation ready - user story implementation can now begin.

---

## Phase 3: User Story 1 - Import Insomnia v5 Request Collections (Priority: P1) 🎯 MVP

**Goal**: Enable importing Insomnia v5 collections (YAML/JSON) with folder hierarchies, HTTP/gRPC/WebSocket requests, auth, and environments into Clapi.

**Independent Test**: Provide an Insomnia v5 collection fixture and verify that `InsomniaImporter` parses and maps the entire hierarchy, headers, parameters, authentication, and environments to `OpenCollectionDocument` without data corruption.

### Tests for User Story 1

- [ ] T006 [P] [US1] Unit tests for Insomnia v5 document detection and parsing in `core-storage/src/commonTest/kotlin/io/github/makeforge/clapi/core/storage/importers/InsomniaImporterTest.kt`

### Implementation for User Story 1

- [ ] T007 [US1] Implement `InsomniaImporter` engine parsing YAML via `kaml` and JSON via `kotlinx.serialization`, mapping folder hierarchies, HTTP/gRPC/WebSocket requests, auth (Basic, Bearer, API Key, AWS IAM, OAuth2, Digest), and environments in `core-storage/src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/importers/InsomniaImporter.kt`
- [ ] T008 [US1] Integrate `InsomniaImporter` into `CompositeSpecificationImporter` in `core-storage/src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/importers/SpecificationImporter.kt` to support scanning and preview generation
- [ ] T009 [US1] Integrate `InsomniaImporter` into `ImportExportRepository.importCollectionFromString` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/persistence/ImportExportRepository.kt` to support direct collection imports
- [ ] T010 [P] [US1] Integration tests for `ImportExportRepositoryTest` verifying Insomnia v5 collection and environment import into `AppState` in `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/persistence/ImportExportRepositoryTest.kt`

**Checkpoint**: User Story 1 is fully functional and testable as an MVP.

---

## Phase 4: User Story 2 - Discover Supported Formats in the Import Dialog (Priority: P2)

**Goal**: Display clear visual format badges below the file selection dropzone in `ImportSpecificationDialog` and show the format badge in the Step 2 header.

**Independent Test**: Launch the Import Dialog in a Compose desktop test and assert that badges for `OpenAPI 3.0+`, `OpenCollection`, `Postman`, and `Insomnia` are rendered in Step 1, and "Insomnia v5" appears in Step 2 upon scanning.

### Tests for User Story 2

- [ ] T011 [P] [US2] UI component tests for Import Dialog format badges and step 2 header badge in `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/ImportSpecificationUiTest.kt`

### Implementation for User Story 2

- [ ] T012 [US2] Render `SupportedFormatBadges` row (`OpenAPI 3.0+`, `OpenCollection`, `Postman`, `Insomnia`) with test tags beneath the file selection area and update Step 2 header format badge for `INSOMNIA` and `POSTMAN` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ImportSpecificationDialog.kt`
- [ ] T013 [US2] Add localized strings and format labels in `ui-shared/src/commonMain/resources/i18n/en.json` and `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/i18n/Strings.kt`

**Checkpoint**: User Stories 1 and 2 work seamlessly together with visual format discovery.

---

## Phase 5: User Story 3 - Automatic Translation of Insomnia Pre-Request & Test Scripts (Priority: P3)

**Goal**: Automatically translate `scripts.preRequest` and `scripts.afterResponse` in Insomnia requests into executable Clapi before-request and after-response scripts.

**Independent Test**: Import an Insomnia request containing `insomnia.environment.set` and `insomnia.test` and assert that the resulting `RequestItem` contains `ScriptType.BEFORE_REQUEST` and `ScriptType.AFTER_RESPONSE` scripts with translated `clapi.*` calls.

### Tests for User Story 3

- [ ] T014 [P] [US3] Unit tests verifying end-to-end request script attachment and translation in `core-storage/src/commonTest/kotlin/io/github/makeforge/clapi/core/storage/importers/InsomniaScriptImporterTest.kt`

### Implementation for User Story 3

- [ ] T015 [US3] Wire `InsomniaScriptTranslator` into `InsomniaImporter.kt` so pre-request scripts (`ScriptType.BEFORE_REQUEST`) and after-response scripts (`ScriptType.AFTER_RESPONSE`) are mapped to request runtimes in `core-storage/src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/importers/InsomniaImporter.kt`

**Checkpoint**: User Story 3 complete; Insomnia automated test suites and pre-request scripts translate automatically.

---

## Phase 6: User Story 4 - Seamless Migration Guidance in Documentation & QA Tests (Priority: P4)

**Goal**: Provide step-by-step Postman and Insomnia migration instructions in quickstart documentation and automated end-to-end QA regression tests.

**Independent Test**: Run `InsomniaImportE2ETest` to verify complete workspace migration and verify documentation readability.

### Tests for User Story 4

- [ ] T016 [P] [US4] Implement end-to-end automated QA tests verifying Insomnia v5 collection, environment, and script migration in `qa-tests/src/test/kotlin/io/github/makeforge/clapi/qa/InsomniaImportE2ETest.kt`

### Implementation for User Story 4

- [ ] T017 [P] [US4] Add Postman and Insomnia migration guides with step-by-step export and import instructions in `docs/guide/import-export.md` and `docs/guide/index.md`

**Checkpoint**: All user stories complete with end-to-end QA verification and user-facing documentation.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Verification against unsupported field specifications and full test suite execution.

- [ ] T018 [P] Review and verify unsupported fields logging and graceful degradation against `scratch/insomnia-import-unsupported.md` in `core-storage/src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/importers/InsomniaImporter.kt`
- [ ] T019 Run full test suite validation (`:core-storage:test`, `:ui-desktop:test`, `:qa-tests:test`) per `specs/025-import-insomnia-collection/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies
- **Setup (Phase 1)**: Can start immediately.
- **Foundational (Phase 2)**: Depends on Phase 1 completion; blocks all user stories.
- **User Stories (Phase 3+)**: Depend on Phase 2 completion.
  - US1 (P1): Can proceed immediately after Phase 2.
  - US2 (P2): Depends on `SpecFormat` extension from Phase 1.
  - US3 (P3): Depends on `InsomniaScriptTranslator` from Phase 2 and `InsomniaImporter` from Phase 3.
  - US4 (P4): Depends on Phase 3 and Phase 5 completion.
- **Polish (Phase 7)**: Depends on all user stories being complete.

### Parallel Opportunities
- T002 (Fixtures) can run in parallel with T001 (Model update).
- T005 (Script Translator Unit Test) can run in parallel with T004 implementation once interfaces are defined.
- T006 (US1 Unit Test) and T010 (Integration Test) can run in parallel with T007 / T008.
- T011 (US2 UI Test) can run in parallel with T012 / T013.
- T016 (QA Test) and T017 (Documentation) can run in parallel once US1 and US3 are complete.

---

## Implementation Strategy

### MVP First (User Story 1 Only)
1. Complete Phase 1: Setup (T001, T002)
2. Complete Phase 2: Foundational (T003, T004, T005)
3. Complete Phase 3: User Story 1 (T006 - T010)
4. **Validate MVP**: Run `./gradlew :core-storage:test` to confirm Insomnia v5 collections import into `OpenCollectionDocument` and `AppState`.

### Incremental Delivery
1. Add User Story 2 (T011 - T013): Format badges in the Import Dialog.
2. Add User Story 3 (T014 - T015): Pre-request and test assertion script translation.
3. Add User Story 4 (T016 - T017): End-to-end QA tests and quickstart migration documentation.
4. Final Polish (T018 - T019): Full validation against `quickstart.md`.
