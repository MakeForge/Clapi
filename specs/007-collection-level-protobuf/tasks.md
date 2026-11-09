# Tasks: Collection-Level Protobuf Management

**Input**: Design documents from `/specs/007-collection-level-protobuf/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`[US1]`, `[US2]`, `[US3]`, `[US4]`, `[US5]`)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization, strings, and cross-platform desktop hooks

- [X] T001 [P] Add collection string localization keys (`collection`, `add_proto_files`, `import_paths`, `open_file_externally`) to `ui-shared/src/commonMain/resources/i18n/en.json`
- [X] T002 [P] Declare `pickProtoFiles` (multi-file selection) and `openFileInDefaultApp` platform functions in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/platform/PlatformApi.kt`
- [X] T003 [P] Implement `pickProtoFiles` (using AWT `FileDialog` with `isMultipleMode = true`) and `openFileInDefaultApp` (using `Desktop.getDesktop().open` with OS fallback) in `ui-shared/src/desktopMain/kotlin/io/github/crustack/clapi/ui/shared/platform/PlatformApi.desktop.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core data models, RequestKind extension, and OpenCollection persistence mappings

- [X] T004 [P] Add `COLLECTION` to `enum class RequestKind` in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/McpModels.kt`
- [X] T005 [P] Define `CollectionProtobufConfig`, `ProtoFile`, and `ProtoFileImportPath` data models conforming to OpenCollection schema, and add `val protobuf: CollectionProtobufConfig? = null` to `CollectionConfig` in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/OpenCollectionDocument.kt`
- [X] T006 [P] Add unit tests for `CollectionConfig.protobuf` serialization and schema validation in `core-model/src/desktopTest/kotlin/io/github/crustack/clapi/core/model/opencollection/OpenCollectionModelTest.kt`
- [X] T007 Define `ProtoFileItemState` and `ProtoImportPathState` observable UI models, and add `collectionProtoFiles` and `collectionImportPaths` lists to `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt`
- [X] T008 Update `toGlobalOpenCollectionDocument` and `load` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/OpenCollectionModelMapping.kt` and `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/WorkspaceRepository.kt` to serialize and restore `config.protobuf`

**Checkpoint**: Foundation ready — user story implementations can now proceed.

---

## Phase 3: User Story 1 - Collection Root Navigation and Collection Settings Editor (Priority: P1) 🎯 MVP

**Goal**: Users can navigate to collection settings from the sidebar root, opening a dedicated "Collection" tab displaying the CollectionEditor with a "Protobuf" tab.

**Independent Test**: Click the top-level "Collection" item in the sidebar; verify a "Collection" editor tab opens with the Article icon, showing the Protobuf tab.

- [X] T009 [US1] Update `Sidebar.kt` to rename the root section from "Folders" (`Strings.folders`) to "Collection" (`Strings.collection`), use `Icons.Default.Article` instead of folder icon, and open Collection tab on click in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/Sidebar.kt`
- [X] T010 [US1] Implement `AppState.openCollection()` to open or focus a `RequestKind.COLLECTION` tab with ID `"collection_settings"` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt`
- [X] T011 [P] [US1] Update `RequestTabsBar.kt` to render `Icons.Default.Article` for tabs where `tab.kind == RequestKind.COLLECTION` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/RequestTabsBar.kt`
- [X] T012 [US1] Create the `CollectionEditor` composable scaffold with tab bar (displaying "Protobuf" tab) in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/CollectionEditor.kt`
- [X] T013 [US1] Route `tab.kind == RequestKind.COLLECTION` to `CollectionEditor` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/MainScreen.kt`
- [X] T014 [US1] Add UI test for opening and focusing the Collection tab from the sidebar in `ui-desktop/src/desktopTest/kotlin/io/github/crustack/clapi/ui/desktop/CollectionEditorNavigationUiTest.kt`

**Checkpoint**: User Story 1 complete — Collection settings tab opens and displays from the sidebar.

---

## Phase 4: User Story 2 - Manage Collection Proto Files and Import Paths (Priority: P1)

**Goal**: Users can add, remove, and externally open collection-level proto files and import paths (with enabled/disabled toggles) in the CollectionEditor Protobuf tab.

**Independent Test**: In the Protobuf tab, add multiple `.proto` files, toggle an import path, click "Open File" to view externally, remove an item, and verify state persists.

- [X] T015 [US2] Implement the "Proto Files" UI section in `CollectionEditor.kt` supporting multi-file addition via `pickProtoFiles`, file path display, external open button, and remove action in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/CollectionEditor.kt`
- [X] T016 [US2] Implement the "Import Paths" UI section in `CollectionEditor.kt` with enabled/disabled checkbox toggle, path display, external open button, remove action, and add button in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/CollectionEditor.kt`
- [X] T017 [US2] Wire collection proto files and import paths mutations in `CollectionEditor.kt` to trigger `onSave` / `persistWorkspaceAsync()` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/MainScreen.kt`
- [X] T018 [US2] Add unit and UI tests for adding, toggling, and removing collection proto files and import paths in `ui-desktop/src/desktopTest/kotlin/io/github/crustack/clapi/ui/desktop/CollectionProtobufEditorUiTest.kt`

**Checkpoint**: User Story 2 complete — Collection-level proto files and import paths are fully manageable and editable in the UI.

---

## Phase 5: User Story 3 - Select Collection Proto Files in gRPC Request Editor (Priority: P1)

**Goal**: Users can select collection-level proto files from a dropdown/list in the gRPC Request Editor's proto file selection dialog as an alternative to choosing a file manually from disk.

**Independent Test**: In a gRPC request, open the proto file dialog, select a collection-level proto file, save, and verify that the services and methods from that proto file populate the method dropdown.

- [X] T019 [US3] Update `SelectProtoFileDialog.kt` to accept `collectionProtoFiles: List<String>` and render a selectable "Collection Proto Files" list/dropdown alongside the manual file picker in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/SelectProtoFileDialog.kt`
- [X] T020 [US3] Pass `state.collectionProtoFiles.map { it.path }` to `SelectProtoFileDialog` when opened from gRPC request editor in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/MainScreen.kt`
- [X] T021 [US3] Add UI test verifying selection of collection-level proto files in the gRPC Request Editor dialog in `ui-desktop/src/desktopTest/kotlin/io/github/crustack/clapi/ui/desktop/GrpcProtoSelectionUiTest.kt`

**Checkpoint**: User Story 3 complete — gRPC requests can consume collection-level proto files seamlessly.

---

## Phase 6: User Story 4 - Resolve Protobuf Imports During Parsing and Execution (Priority: P1)

**Goal**: ProtocJarParser and the gRPC execution pipeline include all enabled collection-level import paths to resolve proto dependencies with imported types.

**Independent Test**: Configure an enabled collection import path for `metadata.proto`, parse a service proto file that imports it, and execute an RPC call successfully.

- [X] T022 [US4] Update `ProtocJarParser.kt` to normalize `includePaths` (deriving parent and ancestor directory paths for files so relative imports like `import "protobuf/common/metadata.proto";` resolve) in `core-network/src/desktopMain/kotlin/io/github/crustack/clapi/core/network/grpc/ProtocJarParser.kt`
- [X] T023 [P] [US4] Add unit tests in `core-network/src/desktopTest/kotlin/io/github/crustack/clapi/core/network/grpc/ProtocJarParserTest.kt` verifying compilation with imported proto files across include paths
- [X] T024 [P] [US4] Add `includePaths: List<String> = emptyList()` to `GrpcCallRequest` in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/grpc/GrpcModels.kt`
- [X] T025 [US4] Update `NettyDynamicGrpcClient.kt` to forward `request.includePaths` to `protoParser.parseProtoFile` in `core-network/src/desktopMain/kotlin/io/github/crustack/clapi/core/network/grpc/NettyDynamicGrpcClient.kt`
- [X] T026 [US4] Update `RequestExecutionService.kt` to accept and forward `includePaths: List<String>` in `executeGrpc(...)` in `feature-requests/src/commonMain/kotlin/io/github/crustack/clapi/feature/requests/RequestExecutionService.kt`
- [X] T027 [US4] Update `RequestTabState` method discovery and `MainScreen.kt` request execution to pass enabled collection import paths (`state.collectionImportPaths.filter { it.enabled }.map { it.path }`) to `protoParser` and `executionService` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt` and `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/MainScreen.kt`

**Checkpoint**: User Story 4 complete — proto files with imported dependencies compile and execute dynamically.

---

## Phase 7: User Story 5 - Interoperable Persistence in OpenCollection Standard Format (Priority: P2)

**Goal**: Collection-level protobuf settings (`protoFiles` and `importPaths`) serialize and restore faithfully without data loss or schema violations.

**Independent Test**: Export and re-import or reload a workspace with collection proto files and import paths; verify all fields and enabled states are identical.

- [X] T028 [US5] Add storage integration tests in `core-storage/src/commonTest/kotlin/io/github/crustack/clapi/core/storage/opencollection/OpenCollectionProtobufStorageTest.kt` verifying save and load roundtrip of `CollectionConfig.protobuf`
- [X] T029 [US5] Add workspace persistence tests in `ui-desktop/src/desktopTest/kotlin/io/github/crustack/clapi/ui/desktop/persistence/CollectionProtobufPersistenceTest.kt` verifying that collection proto files and import paths persist across app reloads

**Checkpoint**: User Story 5 complete — Full roundtrip persistence complies with OpenCollection v1.0.0.

---

## Phase 8: End-to-End QA Testing & Polish

**Purpose**: Test server adaptation, end-to-end integration tests with imported types, and final validation

- [X] T030 Update `DummyApiServer.kt` to make `metadata.proto` available on classpath/temp dir and compile `greeter.proto` with its import path in `qa-tests/src/test/kotlin/io/github/crustack/clapi/testsupport/DummyApiServer.kt`
- [X] T031 Update `GrpcApiE2ETest.kt` to test unary RPC calls with `greeter.proto` using collection-level `importPaths` pointing to `metadata.proto` in `qa-tests/src/test/kotlin/io/github/crustack/clapi/qa/GrpcApiE2ETest.kt`
- [X] T032 Add a test in `GrpcApiE2ETest.kt` asserting that disabling the required import path causes a compilation failure with descriptive missing import error in `qa-tests/src/test/kotlin/io/github/crustack/clapi/qa/GrpcApiE2ETest.kt`
- [X] T033 Execute complete quickstart validation suite per `specs/007-collection-level-protobuf/quickstart.md` across `:core-model:desktopTest`, `:core-network:desktopTest`, `:core-storage:desktopTest`, `:ui-desktop:desktopTest`, and `:qa-tests:jvmTest`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1: Setup**: No dependencies — can start immediately.
- **Phase 2: Foundational**: Depends on Phase 1 completion — blocks all User Stories.
- **Phase 3: User Story 1 (P1)**: Depends on Phase 2. Enables collection navigation and editor tab.
- **Phase 4: User Story 2 (P1)**: Depends on Phase 3 (CollectionEditor UI).
- **Phase 5: User Story 3 (P1)**: Depends on Phase 2 and Phase 4 (proto files in collection).
- **Phase 6: User Story 4 (P1)**: Depends on Phase 2. Enables ProtocJarParser import resolution.
- **Phase 7: User Story 5 (P2)**: Depends on Phase 2 and Phase 4.
- **Phase 8: Polish & QA**: Depends on Phases 4, 5, 6, and 7.

### Parallel Opportunities

- In Phase 1: `T001`, `T002`, `T003` can run in parallel.
- In Phase 2: `T004`, `T005`, `T006` can run in parallel.
- Once Phase 2 is complete:
  - User Story 1 (`T009`-`T014`) and User Story 4 (`T022`-`T027`) can proceed in parallel.
- In Phase 6: `T023` (compiler unit tests) and `T024` (`GrpcModels.kt`) can run in parallel.
- In Phase 7: `T028` and `T029` can run in parallel.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (Setup) and Phase 2 (Foundational).
2. Complete Phase 3 (User Story 1): Sidebar "Collection" header and CollectionEditor tab.
3. Validate: Click "Collection" in sidebar, verify tab opens with Article icon.

### Incremental Delivery

1. Setup + Foundation ready (`T001`-`T008`).
2. Add Collection Editor navigation (US1: `T009`-`T014`).
3. Add Collection Proto Files & Import Paths management (US2: `T015`-`T018`).
4. Add Proto File Selection in gRPC Request Editor (US3: `T019`-`T021`).
5. Add Protobuf Import Path resolution in compiler and execution (US4: `T022`-`T027`).
6. Add Roundtrip persistence tests (US5: `T028`-`T029`).
7. Complete End-to-End QA Test adjustments and full verification (`T030`-`T033`).
