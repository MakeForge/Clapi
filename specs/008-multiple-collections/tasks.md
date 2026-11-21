---
description: "Task list for supporting multiple OpenCollection collections"
---

# Tasks: Support Multiple OpenCollection Collections

**Input**: Design documents from `specs/008-multiple-collections/` (`spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`)

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`

**Tests**: Test tasks included for unit, state, UI, and QA validation across user stories.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Update core state models and shared resources required by all user stories.

- [x] T001 Extend `CollectionNode` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt` with `summary: String?`, `version: String?`, `protoFiles: List<ProtoFileItemState>`, `importPaths: List<ProtoImportPathState>`, and helper `isCollection: Boolean`.
- [x] T002 [P] Update `OpenCollectionModelMapping.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/OpenCollectionModelMapping.kt` to serialize and deserialize collection-level metadata (`CollectionInfo`) and `RequestDefaults`.
- [x] T003 [P] Add string localization entries for "Collections", "Add Collection", "Info", and multi-collection export options in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/i18n/Strings.kt`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: State management infrastructure that must be complete before user stories begin.

- [x] T004 Implement collection creation, deletion, and duplicate-name disambiguation helpers (`generateUniqueCollectionName` appending incremental numeric suffixes) in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt`.
- [x] T005 [P] Update `RequestTabState` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt` to support `collectionSummary`, `collectionVersion`, and tab initialization for `RequestKind.COLLECTION`.
- [x] T006 [P] Create state unit tests verifying multiple collections modeling and name disambiguation in `ui-desktop/src/desktopTest/kotlin/io/github/crustack/clapi/ui/desktop/state/MultipleCollectionsStateTest.kt`.

**Checkpoint**: Foundation ready — user story implementation can now begin.

---

## Phase 3: User Story 1 - Multiple Collections Hierarchy and Management in Sidebar (Priority: P1) 🎯 MVP

**Goal**: Present a top-level "Collections" section header with a `+` button in the sidebar; render each collection as an expandable/collapsible item with Article icon, inline rename via double-click, and a `+` button to add folders within that collection.

**Independent Test**: Launch Clapi, click `+` on "Collections" header to create collections, expand/collapse each collection, double-click to rename inline, verify duplicate names auto-disambiguate with incremental index, and left-click to open collection tab.

### Tests for User Story 1

- [x] T007 [P] [US1] Create UI tests for sidebar "Collections" header, collection tree items, expand/collapse, and inline rename in `ui-desktop/src/desktopTest/kotlin/io/github/crustack/clapi/ui/desktop/components/SidebarMultipleCollectionsUiTest.kt`.

### Implementation for User Story 1

- [x] T008 [US1] Replace the single "Collection" header in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/Sidebar.kt` with a "Collections" `SectionHeader` featuring an `IconButton` (`+`) that creates a new collection.
- [x] T009 [US1] Implement collection item rows in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/Sidebar.kt` displaying Article icon, expand/collapse toggle, collection name, and `+` action button for adding folders.
- [x] T010 [US1] Implement double-click inline renaming on collection name text in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/Sidebar.kt`, automatically disambiguating duplicate names via incremental index (e.g., `Collection (1)`) without validation error dialogs.
- [x] T011 [US1] Implement collection item context menu in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/Sidebar.kt` with Add Folder, Add Request, Duplicate, Export, and Delete actions (prompting confirmation and closing open descendant tabs).

**Checkpoint**: User Story 1 (MVP) is fully functional and testable independently.

---

## Phase 4: User Story 2 - Collection Editor with Info Tab and Collection Request Defaults (Priority: P1)

**Goal**: Provide a dedicated `CollectionEditor` with an "Info" tab positioned first on the left (name, summary, version), Request Defaults tabs (Variables, Headers, Auth, Pre-request, Tests), and the Protobuf tab.

**Independent Test**: Open a collection tab, verify "Info" is the first tab on the left, edit name/summary/version, configure collection-level headers/variables/auth/scripts, save, and verify values sync and persist.

### Tests for User Story 2

- [x] T012 [P] [US2] Create UI tests for `CollectionEditor` tab ordering, "Info" tab metadata editing, and request defaults tabs in `ui-desktop/src/desktopTest/kotlin/io/github/crustack/clapi/ui/desktop/CollectionEditorTabsUiTest.kt`.

### Implementation for User Story 2

- [x] T013 [US2] Update `CollectionEditorTab` enum in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/CollectionEditor.kt` to include `INFO`, `VARIABLES`, `HEADERS`, `AUTH`, `PRE_REQUEST`, `TESTS`, and `PROTOBUF`, ensuring `INFO` is first on the left.
- [x] T014 [US2] Implement the "Info" tab form composable in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/CollectionEditor.kt` with text fields for Name, Summary, and Version (`authors` omitted).
- [x] T015 [US2] Integrate Request Defaults tabs (Variables, Headers, Auth, Pre-request, Tests) into `CollectionEditor.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/CollectionEditor.kt` reusing `KeyValueEditor`, `AuthEditor`, and `ScriptEditor`.
- [x] T016 [US2] Implement bidirectional synchronization in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt` (`syncTabToCollectionNode`) updating collection name, metadata, and request defaults on save.

**Checkpoint**: User Stories 1 AND 2 work together independently.

---

## Phase 5: User Story 3 - Request Defaults Inheritance from Collection to Folders and Requests (Priority: P1)

**Goal**: Enable folders to inherit request defaults from their enclosing collection, and requests to inherit from folders and collection.

**Independent Test**: Set collection-level headers and Bearer auth, create a folder inside the collection and verify folder inherits auth with source banner, create a request in the folder and verify inherited headers and auth apply at execution time.

### Tests for User Story 3

- [x] T017 [P] [US3] Create unit and integration tests for collection-to-folder-to-request inheritance in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/state/CollectionInheritanceTest.kt`.

### Implementation for User Story 3

- [x] T018 [US3] Update `getAncestorFolders` and `findAncestorChain` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt` to include the enclosing `CollectionNode` as root ancestor.
- [x] T019 [US3] Update `applyInheritedDefaults` and `resolveInheritedAuth` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt` to cascade collection-level headers, variables, and authentication down to folders and requests.
- [x] T020 [US3] Update `FolderEditor.kt` and `AuthEditor.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/` to display inherited collection defaults and support `AuthType.INHERIT` resolving to the collection.
- [x] T021 [US3] Update script execution pipeline in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/RequestExecutor.kt` to execute pre-request and post-request scripts in cascade sequence (`Collection -> Folder -> Request`).

**Checkpoint**: User Stories 1, 2, and 3 work seamlessly together.

---

## Phase 6: User Story 4 - Selective Multi-Collection Export and Collection Import (Priority: P2)

**Goal**: Provide global export dialog to select which collections to export (generating 1 file per selected collection), export individual collections directly, and import OpenCollection files into distinct collections.

**Independent Test**: In global export, select 2 of 3 collections, choose output folder, verify 2 distinct OpenCollection files written. Import an OpenCollection document, verify it appears as a distinct collection.

### Tests for User Story 4

- [x] T022 [P] [US4] Create unit tests for multi-collection export and import in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/persistence/MultiCollectionImportExportTest.kt`.

### Implementation for User Story 4

- [x] T023 [P] [US4] Implement `ExportCollectionsDialog` composable in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/ExportCollectionsDialog.kt` with collection checkboxes, select all toggle, and JSON/YAML format choice.
- [x] T024 [US4] Implement multi-collection export runner in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/ImportExportRepository.kt` writing 1 file per selected collection into user-selected directory.
- [x] T025 [US4] Update OpenCollection import handler in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/ImportExportRepository.kt` to create a new distinct collection in `state.collections` with automatic index disambiguation on collision.
- [x] T026 [US4] Wire the "Export Collections..." action in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/Sidebar.kt` to show `ExportCollectionsDialog` with native directory picker.

**Checkpoint**: User Stories 1, 2, 3, and 4 are complete.

---

## Phase 7: User Story 5 - Workspace Persistence and Migration from Single Collection (Priority: P3)

**Goal**: Automatically migrate legacy single-collection workspaces to the multi-collection model on first launch without data loss, and persist multiple collections across sessions.

**Independent Test**: Load a legacy `workspace.json`, verify all folders and requests load under a valid collection, restart application, and verify full workspace state is restored.

### Tests for User Story 5

- [x] T027 [P] [US5] Create unit tests for single-collection migration and multi-collection persistence in `ui-shared/src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/persistence/WorkspaceMigrationTest.kt`.

### Implementation for User Story 5

- [x] T028 [US5] Update `WorkspaceRepository.load` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/WorkspaceRepository.kt` to detect legacy single-collection files and wrap items into a primary collection on load.
- [x] T029 [US5] Update `toGlobalOpenCollectionDocument` and `save` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/OpenCollectionModelMapping.kt` and `WorkspaceRepository.kt` to persist all collections, their metadata, request defaults, and items.
- [x] T030 [US5] Update `CanonicalCollectionRepository.kt` in `core-storage/src/commonMain/kotlin/io/github/crustack/clapi/core/storage/opencollection/CanonicalCollectionRepository.kt` to support multi-collection persistence.

**Checkpoint**: All user stories functional with full persistence and backward compatibility.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Cross-cutting improvements, documentation, and final end-to-end QA validation.

- [x] T031 [P] Update user documentation in `docs/collections.md` describing multiple collections hierarchy, Info tab, request defaults inheritance, and export/import.
- [x] T032 [P] Update and run end-to-end QA test suite in `qa-tests/src/test/kotlin/io/github/crustack/clapi/qa/OpenCollectionRoundTripE2ETest.kt` for multiple collections.
- [x] T033 Execute manual validation scenarios in `specs/008-multiple-collections/quickstart.md`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Phase 1 completion — BLOCKS all user stories.
- **User Story 1 (Phase 3 - P1)**: Depends on Phase 2. Foundational for navigation and sidebar UI.
- **User Story 2 (Phase 4 - P1)**: Depends on Phase 2 and US1 (opens from sidebar collection click).
- **User Story 3 (Phase 5 - P1)**: Depends on Phase 2, US1, and US2 (inherits request defaults defined in US2).
- **User Story 4 (Phase 6 - P2)**: Depends on Phase 2 and US1 (exports collections created in US1).
- **User Story 5 (Phase 7 - P3)**: Depends on Phase 2 and US1 (persists multi-collection state).
- **Polish (Phase 8)**: Depends on all user story phases being complete.

### Parallel Opportunities

- **Phase 1**: T002, T003 can run in parallel.
- **Phase 2**: T005, T006 can run in parallel.
- **Phase 3 (US1)**: Test T007 can be written in parallel with T008.
- **Phase 4 (US2)**: Test T012 can be written in parallel with T013/T014.
- **Phase 5 (US3)**: Test T017 can be written in parallel with T018.
- **Phase 6 (US4)**: T022 and T023 can run in parallel.
- **Phase 7 (US5)**: Test T027 can run in parallel with T028.
- **Phase 8**: T031 and T032 can run in parallel.

---

## Parallel Example: User Story 1

```bash
# Launch test task and initial UI implementation in parallel:
Task: "T007 [P] [US1] Create UI tests for sidebar 'Collections' header, collection tree items, expand/collapse, and inline rename in ui-desktop/src/jvmTest/kotlin/io/github/crustack/clapi/ui/desktop/components/SidebarMultipleCollectionsUiTest.kt"
Task: "T008 [US1] Replace the single 'Collection' header in ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/Sidebar.kt with a 'Collections' SectionHeader featuring an IconButton (+) that creates a new collection"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001 - T003)
2. Complete Phase 2: Foundational (T004 - T006)
3. Complete Phase 3: User Story 1 (T007 - T011)
4. **STOP and VALIDATE**: Verify users can add collections, expand/collapse, and inline rename with auto-disambiguation in the sidebar.

### Incremental Delivery

1. Setup + Foundational → Core multi-collection state ready.
2. User Story 1 → Sidebar collections hierarchy, expand/collapse, inline renaming (MVP).
3. User Story 2 → Collection Editor with Info tab and request defaults.
4. User Story 3 → Request defaults inheritance (Collection -> Folder -> Request).
5. User Story 4 → Selective multi-collection export (1 file/collection) and import.
6. User Story 5 → Legacy single-collection migration and multi-collection persistence.
7. Polish → Documentation, QA end-to-end tests, quickstart validation.
