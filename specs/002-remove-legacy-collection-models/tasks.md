# Tasks: Remove Legacy Collection Models

**Input**: Design documents from `/specs/002-remove-legacy-collection-models/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/import-export.md, quickstart.md

**Tests**: Test tasks are included. The spec requires that all unit, integration, and qa-test suites pass after the change (FR-010, SC-003), and that legacy input is rejected with a test (FR-005, SC-004).

**Organization**: Tasks are grouped by user story (US1 P1, US2 P2, US3 P3) from spec.md.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: User story the task belongs to (US1, US2, US3)
- Every task includes an exact file path

## Terminology (read before any task)

These names are used precisely in every task below. Do not mix them.

- **OpenCollection Collection**: the one global OpenCollection document that Clapi persists. It is a single file. Its `items` hold all Clapi collections, and its `config.environments` hold all environments.
- **OpenCollection Folder**: a top-level entry in the global Collection's `items`. Each Clapi Collection is exactly one OpenCollection Folder. Folders and requests of that Clapi Collection are nested under it.
- **Clapi Collection**: the user-facing unit (sidebar, import, export, tabs). It is represented as an OpenCollection Folder. It is NOT an OpenCollection Collection.
- **OpenCollection Environment**: an entry of the global Collection's `config.environments`. It is a Clapi environment. It is NOT owned by any Clapi Collection or Folder (FR-015).
- **Single-collection export**: a standalone OpenCollection document for one Clapi Collection, with no `config` and no environments (FR-017, `contracts/import-export.md`).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish baseline and verify assumptions before changing code.

- [X] T001 Record baseline results by running `rtk gradlew :core-model:allTests :core-storage:allTests :ui-shared:desktopTest :ui-desktop:desktopTest :qa-tests:jvmTest` before any change, so SC-003 can be compared against a known starting state
- [X] T002 Save the legacy symbol inventory to `/home/user/.copilot/session-state/a6b53ac6-b2f1-432a-bca2-a28d4beaebec/files/legacy-inventory.txt` by grepping `WorkspaceDefinition|CollectionDefinition|FolderDefinition|CollectionRepository|clapiCollection|clapiWorkspace|clapi.workspace|workspace-backup` across the repo, excluding `build/`, `specs/001-*`, and `CHANGELOG.md`
- [X] T003 Verify that the OpenCollection folder schema accepts an `extensions` key on folder items, using the OpenCollection schema validator used by `core-storage/src/commonTest/kotlin/io/github/crustack/core/storage/opencollection/OpenCollectionStorageTest.kt`. If it rejects it, stop and record the chosen alternative for folder id persistence in `specs/002-remove-legacy-collection-models/research.md` item 9 before continuing

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The global OpenCollection Collection API and folder id persistence. Every user story depends on these.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T004 Fix folder id loss on save in `core-storage/src/commonMain/kotlin/io/github/crustack/core/storage/opencollection/OpenCollectionSerializer.kt` (`enrichItemsForSerialization`, around lines 115-131): write each OpenCollection Folder's `extensions["id"]` into the serialized folder, so the id of a Clapi Collection survives save and reload (FR-019). Depends on T003
- [X] T005 Add a round-trip test in `core-storage/src/commonTest/kotlin/io/github/crustack/core/storage/opencollection/OpenCollectionStorageTest.kt` that saves a global OpenCollection Collection with two top-level OpenCollection Folders, reloads it, and asserts both folder ids are unchanged (FR-019, SC-005). Depends on T004
- [X] T006 Replace the per-file store in `core-storage/src/commonMain/kotlin/io/github/crustack/core/storage/opencollection/CanonicalCollectionRepository.kt` with one global OpenCollection Collection file in `baseDirectory`. Add `loadGlobalCollection(): OpenCollectionDocument?` and `saveGlobalCollection(document: OpenCollectionDocument)`. `observeCollections` must return the top-level OpenCollection Folders of that document. Keep `collectionId` (line 12) and single-collection `importCollection`/`exportCollection`. Depends on T004
- [X] T007 Create the private internal store for global variables, request history, and expanded tree state in `ui-shared/src/commonMain/kotlin/io/github/crustack/ui/shared/persistence/PrivateStateRepository.kt`. It must not use any `clapi*` type or key, and it must not be a user-facing format (FR-018). Storage mechanism follows `research.md` item 10. Depends on T006 for the storage-directory convention

**Checkpoint**: The global OpenCollection Collection API exists, and folder ids survive reload. User story work can begin.

---

## Phase 3: User Story 1 - Delete deprecated collection models (Priority: P1)

**Goal**: The three deprecated types and the legacy `CollectionRepository` interface are removed from source.

**Independent Test**: `grep` for `WorkspaceDefinition`, `CollectionDefinition`, `FolderDefinition`, and `CollectionRepository` in source returns zero hits (SC-001), and `core-storage` compiles.

**Note**: US1 cannot be fully finished before US2 removes the legacy references in `ImportExportRepository.kt` and `WorkspaceRepository.kt`. T013 is blocked until those tasks are done.

### Implementation for User Story 1

- [ ] T008 [US1] Retype `InMemoryCollectionRepository` in `core-storage/src/commonMain/kotlin/io/github/crustack/core/storage/InMemoryRepositories.kt` (lines 3, 56-61) so it holds `OpenCollectionDocument` values instead of the deprecated types (FR-002). Depends on T006
- [ ] T009 [US1] Delete the legacy `CollectionRepository` interface from `core-storage/src/commonMain/kotlin/io/github/crustack/core/storage/StorageContracts.kt` (lines 3, 15-17). `CanonicalCollectionRepository` remains the only collection store (FR-002)
- [ ] T010 [US1] Update `core-storage/src/commonTest/kotlin/io/github/crustack/core/storage/InMemoryRepositoriesTest.kt` (lines 3, 65-76) to use `OpenCollectionDocument` with `InMemoryCollectionRepository`. Depends on T008
- [ ] T011 [US1] Delete `core-model/src/commonMain/kotlin/io/github/crustack/core/model/CollectionModels.kt`, which holds `WorkspaceDefinition`, `CollectionDefinition`, and `FolderDefinition`. Delete the whole file, since all three types are removed (FR-001). Blocked by T009, T010, T016, T017, T018, T024, T025 (no remaining references)
- [ ] T012 [US1] Verify SC-001: run the grep from `specs/002-remove-legacy-collection-models/quickstart.md` for the three type names and `CollectionRepository`, and confirm zero hits in source. Depends on T011

**Checkpoint**: The deprecated types no longer exist in source.

---

## Phase 4: User Story 2 - Work only with the OpenCollection format (Priority: P2)

**Goal**: Import and export use only OpenCollection. Clapi Collections are OpenCollection Folders of one global OpenCollection Collection. Environments are OpenCollection Environments in that Collection's `config.environments`. Workspace bundle export and import are removed.

**Independent Test**: Export one Clapi Collection, import it into a fresh store, and get the same folders and requests in the same order (SC-005). Import a `clapiCollection` or `clapiWorkspace` file and get a user-visible error with no data written (SC-004). Save a global document with environments and reload it.

### Import/export of one collection

- [ ] T013 [US2] Delete the `"clapiCollection"` branch in `importCollectionFromString` in `ui-shared/src/commonMain/kotlin/io/github/crustack/ui/shared/persistence/ImportExportRepository.kt` (around lines 165-209, branch at 181-186). No legacy read path remains (FR-003)
- [ ] T014 [US2] Reject legacy inputs in `ui-shared/src/commonMain/kotlin/io/github/crustack/ui/shared/persistence/ImportExportRepository.kt`: a `clapiCollection` or `clapiWorkspace` file returns a user-visible error and writes nothing (FR-005). Rewrite the unrecognized-format messages (around lines 187 and 206) to remove the word "Clapi" and keep the rejection behavior. Depends on T013
- [ ] T015 [US2] Delete the workspace functions from `ui-shared/src/commonMain/kotlin/io/github/crustack/ui/shared/persistence/ImportExportRepository.kt`: `exportWorkspaceToString` (around 231), `importWorkspaceFromString` (around 277), `decodeWorkspace` (around 285), `encodeWorkspace` (around 293), `mergeWorkspaceIntoState` (around 298), `replaceWorkspaceState` (around 320), and the `clapiWorkspace` writer (around 538). Workspace bundle format is removed (FR-013, FR-004)
- [ ] T016 [US2] Delete the legacy writers (`put("type", "clapiCollection")` around lines 386 and 567), the legacy DTOs (around lines 70-146), and the legacy parsers (around lines 680-1041) from `ui-shared/src/commonMain/kotlin/io/github/crustack/ui/shared/persistence/ImportExportRepository.kt`. No code may write `clapiCollection` or `clapiWorkspace` (FR-004, FR-006). Depends on T014
- [ ] T017 [US2] Remove the workspace export and import actions from `ui-shared/src/commonMain/kotlin/io/github/crustack/ui/shared/components/SettingsDialog.kt` (around lines 490-510), including the `workspace-backup.json` save. Collections are exported one at a time (FR-013)
- [ ] T018 [US2] Make single-collection export in `ui-shared/src/commonMain/kotlin/io/github/crustack/ui/shared/persistence/OpenCollectionModelMapping.kt` produce a standalone OpenCollection document with no `config` and no environments (FR-017, `contracts/import-export.md`). Keep the `CollectionNode` to OpenCollection mapping (around lines 99-130) and the `toCollectionNode` direction (around lines 229-260)
- [ ] T019 [US2] Map environments between the UI and OpenCollection in `ui-shared/src/commonMain/kotlin/io/github/crustack/ui/shared/persistence/OpenCollectionModelMapping.kt`. An OpenCollection Environment (`Environment` in `core-model/src/commonMain/kotlin/io/github/crustack/core/model/opencollection/OpenCollectionEnvironment.kt`, line 152) maps to `EnvState` (`ui-shared/.../state/AppState.kt`, line 213). Name maps to name. `RegularVariable` maps to a `MutableKeyValue` with `enabled = !disabled`. `SecretVariable` maps to a `MutableKeyValue` with `secret = true`
- [ ] T020 [US2] Merge environments on collection import in `ui-shared/src/commonMain/kotlin/io/github/crustack/ui/shared/persistence/ImportExportRepository.kt`. An environment in the imported OpenCollection document is merged into the global environments by name. Missing variables and fields are added. Existing entries are never removed or overwritten (FR-016). Depends on T019
- [ ] T021 [US2] Import one Clapi Collection as a top-level OpenCollection Folder of the global OpenCollection Collection, in `ui-shared/src/commonMain/kotlin/io/github/crustack/ui/shared/persistence/ImportExportRepository.kt`. The imported folder keeps its folder id (`extensions["id"]`, FR-019) and gets a unique name using `generateUniqueName` (`core-storage/.../CanonicalCollectionRepository.kt`). Depends on T006, T004

### Global document and workspace persistence

- [ ] T022 [US2] Rework `save` in `ui-shared/src/commonMain/kotlin/io/github/crustack/ui/shared/persistence/WorkspaceRepository.kt` (around lines 18-66). Write every Clapi Collection as a top-level OpenCollection Folder of the single global OpenCollection Collection (FR-014). Write environments into `config.environments` (FR-015). Save global variables, history, and expanded state through `PrivateStateRepository` (T007). Stop writing the `clapi.workspace` key (line 18). Depends on T006, T007, T019
- [ ] T023 [US2] Rework `load` in `ui-shared/src/commonMain/kotlin/io/github/crustack/ui/shared/persistence/WorkspaceRepository.kt` (around lines 39, 65-66). Read the global OpenCollection Collection, map its top-level folders to Clapi Collections, and read environments from `config.environments`, including when no collection exists (FR-015). Read the rest from `PrivateStateRepository`. Ignore the `clapi.workspace` key entirely (FR-018). Depends on T022
- [ ] T024 [US2] Remove the `ImportExportRepository` workspace calls (`decodeWorkspace`, `replaceWorkspaceState`) from `WorkspaceRepository.kt` (line 39 and surrounding code). Depends on T015, T022

**Checkpoint**: The app runs on one global OpenCollection Collection with Clapi Collections as top-level Folders and environments in `config.environments`. Legacy input is rejected.

---

## Phase 5: User Story 3 - Test suites target OpenCollection (Priority: P3)

**Goal**: All unit, integration, and qa tests use OpenCollection input. Tests that cover legacy input assert rejection. The qa fixture is OpenCollection.

**Independent Test**: `rtk gradlew :core-model:allTests :core-storage:allTests :ui-shared:desktopTest :ui-desktop:desktopTest :qa-tests:jvmTest` passes (SC-003). No test uses a legacy format as valid input except for rejection assertions (FR-010).

### Unit tests in ui-shared

- [ ] T025 [US3] Delete `ui-shared/src/commonTest/kotlin/io/github/crustack/ui/shared/persistence/McpWorkspaceBackwardCompatTest.kt` (FR-011)
- [ ] T026 [P] [US3] Update `ui-shared/src/commonTest/kotlin/io/github/crustack/ui/shared/components/RequestReliabilityTest.kt` (around line 234, `clapiCollection` input) to an OpenCollection document
- [ ] T027 [P] [US3] Update `ui-shared/src/commonTest/kotlin/io/github/crustack/ui/shared/components/PreReleaseQaTest.kt`: convert the `clapiCollection` inputs (around lines 254, 586), and remove the workspace export and import round trip (around lines 688-721)
- [ ] T028 [P] [US3] Update `ui-shared/src/commonTest/kotlin/io/github/crustack/ui/shared/persistence/ImportExportFormDataTest.kt` (around lines 188, 215, 247) to OpenCollection input
- [ ] T029 [P] [US3] Update `ui-shared/src/commonTest/kotlin/io/github/crustack/ui/shared/persistence/PostmanImporterTest.kt` (parse helper, around line 40) so it no longer uses `clapiCollection`. Keep the Postman to OpenCollection conversion assertions (FR-007)
- [ ] T030 [P] [US3] Update `ui-shared/src/commonTest/kotlin/io/github/crustack/ui/shared/persistence/PersistenceErrorReportingTest.kt` (line 41, `DefaultCanonicalCollectionRepository`) to the new global-document API from T006

### Integration and UI tests in ui-desktop

- [ ] T031 [US3] Update `ui-desktop/src/desktopTest/kotlin/io/github/crustack/ui/desktop/persistence/ImportExportRepositoryTest.kt` (about 35 legacy references). Convert legacy cases to rejection cases (FR-005). Remove workspace export, import, decode, and replace cases. Keep single-collection OpenCollection round-trip cases. Depends on T014, T015
- [ ] T032 [P] [US3] Update `ui-desktop/src/desktopTest/kotlin/io/github/crustack/ui/desktop/persistence/ImportExportFixturesIntegrationTest.kt` (lines 42-46). Replace the workspace round trip with a per-collection round trip. Use the converted fixture from T037. Depends on T037
- [ ] T033 [P] [US3] Update `ui-desktop/src/desktopTest/kotlin/io/github/crustack/ui/desktop/persistence/PostmanImportIntegrationTest.kt` (lines 117, 133, 165, `clapiCollection` inputs). Keep the Postman import assertions
- [ ] T034 [P] [US3] Update `ui-desktop/src/desktopTest/kotlin/io/github/crustack/ui/desktop/persistence/WorkspaceRepositoryTest.kt` (line 24, `WORKSPACE_KEY`) to the global document, `config.environments`, and private store behavior from T022 and T023
- [ ] T035 [P] [US3] Update `ui-desktop/src/desktopTest/kotlin/io/github/crustack/ui/desktop/integration/RequestSettingsPersistenceWorkflowIntegrationTest.kt` (around line 134, `clapi.workspace`) so it asserts the new storage behavior
- [ ] T036 [P] [US3] Update `ui-desktop/src/desktopTest/kotlin/io/github/crustack/ui/desktop/LargePayloadSaveUiTest.kt` (lines 42, 48, 171-174). Replace workspace export and import with single-collection export and import
- [ ] T037 [P] [US3] Update `ui-desktop/src/desktopTest/kotlin/io/github/crustack/ui/desktop/SettingsDialogUiTest.kt` and `ui-desktop/src/desktopTest/kotlin/io/github/crustack/ui/desktop/ImportExportUiTest.kt` to remove expectations for the workspace export and import buttons (T017) and cover the single-collection path
- [ ] T038 [P] [US3] Check `ui-desktop/src/desktopTest/kotlin/io/github/crustack/ui/desktop/ArchitecturalFixesUiTest.kt` and `ui-desktop/src/desktopTest/kotlin/io/github/crustack/ui/desktop/persistence/TabsRepositoryTest.kt` for `clapi.workspace` assumptions and update them to the new storage behavior
- [ ] T039 [P] [US3] Retype `core-storage/src/commonTest/kotlin/io/github/crustack/core/storage/opencollection/OpenCollectionStorageTest.kt` (around line 339) to the new `CanonicalCollectionRepository` global-document API from T006

### New behavior tests (in existing files)

- [ ] T040 [US3] Add rejection tests to `ui-desktop/src/desktopTest/kotlin/io/github/crustack/ui/desktop/persistence/ImportExportRepositoryTest.kt`: a `clapiCollection` and a `clapiWorkspace` file each produce a user-visible error and write zero collections (FR-005, SC-004). Depends on T014
- [ ] T041 [US3] Add tests to `ui-desktop/src/desktopTest/kotlin/io/github/crustack/ui/desktop/persistence/WorkspaceRepositoryTest.kt` for FR-016 (environment merge by name keeps existing entries and adds missing variables), FR-017 (single-collection export has no `config`), FR-015 (environments persist with zero collections), and FR-018 (a stored `clapi.workspace` key is ignored). Depends on T020, T022, T023

### qa-tests

- [ ] T042 [US3] Convert `qa-tests/fixtures/clapi-test-collection.json` to an OpenCollection document with `info`, `items`, and `http` request entries. Keep the same logical content: folders, requests, and order. Keep MCP configuration in the `clapi` extension (FR-009, SC-006). Depends on T006
- [ ] T043 [US3] Update `qa-tests/collection-validator.mjs` so `flattenRequests` walks OpenCollection `items` and `http` requests, not `folders` and `requests`. Depends on T042
- [ ] T044 [US3] Regenerate `qa-tests/collection-validation-results.json` by running `qa-tests/collection-validator.mjs` on the converted fixture. Depends on T043
- [ ] T045 [P] [US3] Update `qa-tests/src/test/kotlin/io/github/crustack/qa/OpenCollectionRoundTripE2ETest.kt` and `qa-tests/src/test/kotlin/io/github/crustack/qa/OpenCollectionValidationE2ETest.kt` (both use `DefaultCanonicalCollectionRepository` with `baseDirectory`) to the new API from T006. Depends on T006
- [ ] T046 [P] [US3] Update `qa-tests/src/test/kotlin/io/github/crustack/qa/SampleCollectionE2ETest.kt` so it loads the converted fixture from T042 as an OpenCollection document. Depends on T042

**Checkpoint**: All suites use OpenCollection input. Legacy input appears only in rejection assertions.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, final verification, and cleanup.

- [ ] T047 [P] Update `docs/architecture.md` (around line 80), `README.md` (around lines 66-67), `docs/mcp.md` (around line 189), `docs/tests.md`, and `FEATURES.md` to describe the global OpenCollection Collection, Clapi Collections as top-level OpenCollection Folders, environments in `config.environments`, and the removal of the workspace bundle (FR-012). Do not edit `specs/001-*` or `CHANGELOG.md`
- [ ] T048 Delete the `clapi.workspace` decode and replace comments, dead helpers, and unused imports left in `ui-shared/src/commonMain/kotlin/io/github/crustack/ui/shared/persistence/` and `ui-shared/src/commonMain/kotlin/io/github/crustack/ui/shared/components/SettingsDialog.kt`
- [ ] T049 Run the SC-002 grep from `specs/002-remove-legacy-collection-models/quickstart.md` and confirm zero production writes of `clapiCollection` or `clapiWorkspace`. Exclude `specs/001-*`, `CHANGELOG.md`, and rejection tests
- [ ] T050 Run `rtk gradlew :core-model:allTests :core-storage:allTests :ui-shared:desktopTest :ui-desktop:desktopTest :qa-tests:jvmTest` and confirm SC-003 (all suites pass)
- [ ] T051 Run the manual validation steps in `specs/002-remove-legacy-collection-models/quickstart.md` end to end

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies
- **Foundational (Phase 2)**: Depends on Setup (T003 gates T004). BLOCKS all user stories
- **User Story 1 (Phase 3)**: Starts after Foundational. Its final task T011 is blocked until US2 removes all references (T015, T016, T024)
- **User Story 2 (Phase 4)**: Starts after Foundational. Its import removal (T013-T016) must finish before T011
- **User Story 3 (Phase 5)**: Test updates depend on the behavior from US2. Qa conversion (T042) depends only on Foundational (T006)
- **Polish (Phase 6)**: Depends on all user stories

### Real Task Dependencies (not list order)

- T004 → T005, T006 (folder ids must persist before the global document relies on them)
- T006 → T008, T021, T022, T030, T039, T042, T045
- T007 → T022
- T009, T010, T016, T017, T018, T024, T025 → T011 (the file can only be deleted when nothing references it)
- T013 → T014 → T016 → T031 → T040 (legacy removal, then rejection behavior, then tests)
- T019 → T020, T022 (environment mapping before merge and workspace save)
- T020, T022, T023 → T041 (merge, save, and load tests)
- T042 → T043 → T044, and T042 → T046, T032

### Parallel Opportunities

- Within Foundational: none. T004 is the root of the chain
- Unit test files in ui-shared (T026-T030) are different files and can run in parallel
- ui-desktop test files (T032-T039) are different files and can run in parallel once their dependencies are done
- qa tests T045 and T046 can run in parallel
- Polish docs (T047) can run in parallel with the test updates

---

## Parallel Example: User Story 3 unit tests

```bash
# After US2 is complete, these touch different files and can run in parallel:
Task: "T026 Update RequestReliabilityTest.kt in ui-shared/src/commonTest/.../components/"
Task: "T027 Update PreReleaseQaTest.kt in ui-shared/src/commonTest/.../components/"
Task: "T028 Update ImportExportFormDataTest.kt in ui-shared/src/commonTest/.../persistence/"
Task: "T029 Update PostmanImporterTest.kt in ui-shared/src/commonTest/.../persistence/"
Task: "T030 Update PersistenceErrorReportingTest.kt in ui-shared/src/commonTest/.../persistence/"
```

---

## Implementation Strategy

### MVP (User Story 1 + User Story 2)

US1 alone cannot delete `CollectionModels.kt`. Its references in `ImportExportRepository.kt` and `WorkspaceRepository.kt` are removed by US2. The smallest working increment is therefore Foundational + US2 core (T013-T024), followed by T011 (US1 delete). This gives the global document with legacy removed. US3 test updates follow.

### Incremental Delivery

1. Setup + Foundational: global document API and folder id persistence work
2. US2: OpenCollection-only import and export, global document, environments in `config`
3. US1: delete the deprecated types (T011, T012)
4. US3: update tests and qa fixture, then run the full suites
5. Polish: docs and final verification

---

## Notes

- [P] tasks touch different files and have no dependency on incomplete tasks
- Every test that covers legacy input is a rejection test (FR-005, SC-004). Do not keep any test that treats legacy format as valid
- Do not touch `docs/Clapi/...`, `docs/images/...`, or `rename.py`. They are unrelated staged or untracked files
- Do not edit `specs/001-*` history or `CHANGELOG.md`
- Commit after each phase or logical group if the user asks for commits. Include the Co-authored-by trailer
