---

description: "Task list for implementing gRPC Request Proto Folder Workflow"
---

# Tasks: gRPC Request Proto Folder Workflow

**Input**: Design documents from `/specs/032-grpc-proto-workflow/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/grpc-proto-workflow-contract.md](contracts/grpc-proto-workflow-contract.md), [quickstart.md](quickstart.md)

**Tests**: Explicitly requested by the user ("also dont forget to update docs and tests if needed"). Unit, Compose Desktop UI, and E2E tests are included per user story.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`US1`, `US2`, `US3`)
- Exact file paths are included in every task description.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add shared i18n copy and verify the desktop build baseline before modifying models and UI.

- [X] T001 [P] Add UI string keys for proto folder selection, empty folder state, and folder Git sync hint in `ui-shared/src/commonMain/resources/i18n/en.json` and `ui-shared/src/commonMain/resources/i18n/fr.json`
- [X] T002 Verify clean baseline compilation for affected modules via `rtk gradlew :core-storage:compileKotlinDesktop :core-network:compileKotlinDesktop :ui-shared:compileKotlinDesktop`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core platform APIs, state extensions, and persistence plumbing required by all user stories.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T003 [P] Add `DiscoveredProtoFile(relativePath, absolutePath)`, `expect fun pickProtoDirectory(onResult: (dirPath: String) -> Unit)`, and `expect fun listProtoFilesInDirectory(dirPath: String): List<DiscoveredProtoFile>` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/platform/PlatformApi.kt`
- [X] T004 Implement desktop `actual fun pickProtoDirectory` (using `FileKit.openDirectoryPicker` and remembered directory) and `actual fun listProtoFilesInDirectory` (recursive `.proto` case-insensitive scan sorted by normalized `/` relative path, returning empty list for missing/non-directory paths) in `ui-shared/src/desktopMain/kotlin/io/github/makeforge/clapi/ui/shared/platform/PlatformApi.desktop.kt`
- [X] T005 [P] Add `val protoFolderPath: String? = null` to `CollectionNode` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/CollectionModels.kt`
- [X] T006 [P] Add `var protoFolderPath by mutableStateOf<String?>(null)` and include it in `computeDirtySnapshot()` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/RequestTabState.kt`
- [X] T007 Persist and restore `"protoFolderPath"` (serialized as `""` when null, deserialized as `null` when blank) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/persistence/TabsRepository.kt`
- [X] T008 Wire `protoFolderPath` between `CollectionNode` and `RequestTabState` when opening and saving gRPC tabs in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/AppState.kt`
- [X] T009 Persist `protoFolderPath` in `extensions["clapi"]["protoFolderPath"]` (omitted when null/blank) and deserialize it back into `CollectionNode.protoFolderPath` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/persistence/OpenCollectionModelMapping.kt`

**Checkpoint**: Foundation ready — `protoFolderPath` flows through state, tabs, and OpenCollection persistence, and desktop folder picking + recursive `.proto` discovery are available.

---

## Phase 3: User Story 1 - Select a Proto Folder and Target Proto File in a gRPC Request (Priority: P1) 🎯 MVP

**Goal**: Allow users to select an entire proto folder in `SelectProtoFileDialog`, pick a specific `.proto` file from that folder (and its subdirectories), and automatically resolve imports against the selected folder plus enabled collection-wide import paths and collection proto files during both parsing and execution.

**Independent Test**: Open a gRPC request with no collection-level import paths configured, select `qa-tests/src/test/resources` (or `qa-tests/src/test/resources/protobuf`) in `SelectProtoFileDialog`, choose `greeter.proto`, save, verify RPC methods populate in the dropdown, and execute a gRPC request successfully.

### Implementation for User Story 1

- [X] T010 [US1] Add shared `resolveGrpcIncludePaths(protoFolderPath, collectionImportPaths, collectionProtoFiles, collectionGitDir)` helper and use it in both `LaunchedEffect` parsing, dialog save parsing, and `resolveGrpcCallRequest` (resolving `protoFolderPath`, enabled `collectionImportPaths`, and `collectionProtoFiles` in deduplicated priority order) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/GrpcRequestEditor.kt`
- [X] T011 [US1] Rework `SelectProtoFileDialog` to add `currentProtoFolderPath`, `onSaveSelection`, `onPickFolder`, and `onListProtoFiles` parameters, add the "Select Proto Folder" button (`proto-dialog-pick-folder-button`), selected folder display (`proto-dialog-folder-display`) with clear action (`proto-dialog-clear-folder-button`), scrollable discovered `.proto` list (`proto-dialog-folder-file-list` with `VerticalScrollbar` and `proto-dialog-folder-item-<relativePath>` rows), empty-folder state (`proto-dialog-folder-empty`) disabling Save, and dirty-state tracking across folder + file changes in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/SelectProtoFileDialog.kt`
- [X] T012 [US1] Wire `SelectProtoFileDialog` in `GrpcRequestEditor` to pass `currentProtoFolderPath = tab.protoFolderPath`, resolve `protoFolderPath` + `protoFilePath` against `collectionGitDir` on save, update `tab.protoFolderPath` and `tab.protoFilePath`, and trigger `LaunchedEffect` re-parsing when `tab.protoFolderPath` or `state.collectionProtoFiles` changes in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/GrpcRequestEditor.kt`

### Tests for User Story 1

- [X] T013 [P] [US1] Add unit tests verifying `greeter.proto` importing `protobuf/common/metadata.proto` compiles cleanly when either the parent `resources` folder or the `protobuf` folder is passed in `includePaths`, and when combined with collection import paths/proto files, in `core-network/src/desktopTest/kotlin/io/github/makeforge/clapi/core/network/grpc/ProtocJarParserTest.kt`
- [X] T014 [P] [US1] Add unit tests verifying `resolveGrpcIncludePaths` combines `protoFolderPath`, enabled `collectionImportPaths`, and `collectionProtoFiles`, and verifying `protoFolderPath` round-trips through `OpenCollectionModelMapping` and `TabsRepository`, in `ui-shared/src/commonTest/kotlin/io/github/makeforge/clapi/ui/shared/components/executor/GrpcRequestExecutorTest.kt`
- [X] T015 [P] [US1] Add Compose Desktop UI tests covering folder selection, discovered `.proto` list rendering with relative paths, picking a target `.proto` file, empty folder validation disabling Save, and unsaved-changes confirmation on dismiss in `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/GrpcProtoFolderDialogUiTest.kt`
- [X] T016 [US1] Add an end-to-end gRPC test executing `helloworld.Greeter/SayHello` using folder-based proto selection (`greeter.proto` + `common/metadata.proto`) with zero collection-level import paths configured in `qa-tests/src/test/kotlin/io/github/makeforge/clapi/qa/GrpcApiE2ETest.kt`

**Checkpoint**: User Story 1 (MVP) is complete and independently testable across unit, UI, and E2E suites.

---

## Phase 4: User Story 2 - Preserve Single-File and Collection-Level Proto Selection (Priority: P1)

**Goal**: Ensure single-file selection, collection-level proto selection, switching between folder and single-file/collection modes, and loading legacy single-file gRPC requests remain seamless and backward-compatible.

**Independent Test**: Open `SelectProtoFileDialog` on a gRPC request, switch from folder mode to a single `.proto` file or collection-level proto file, verify `protoFolderPath` is cleared cleanly, and verify legacy gRPC requests saved without `protoFolderPath` load and execute without errors.

### Implementation for User Story 2

- [X] T017 [US2] Ensure mode exclusivity in `SelectProtoFileDialog`: picking a single `.proto` file via `proto-dialog-pick-button`, manually editing the single-file path when not in folder mode, or clicking a collection-level proto file clears `selectedFolderPath` to `null` and preserves backward-compatible `onSave` / `onSaveWithSync` / `onSaveWithClose` fallback invocation in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/SelectProtoFileDialog.kt`

### Tests for User Story 2

- [X] T018 [P] [US2] Add UI tests verifying single-file selection, collection-level proto selection, switching from folder mode to single-file/collection mode clears `protoFolderPath`, and reopening legacy single-file requests (`protoFolderPath = null`) in `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/GrpcProtoFolderDialogUiTest.kt`
- [X] T019 [P] [US2] Add unit test verifying OpenCollection deserialization of legacy gRPC requests without `extensions.clapi.protoFolderPath` loads `protoFolderPath = null` cleanly in `ui-shared/src/commonTest/kotlin/io/github/makeforge/clapi/ui/shared/components/executor/GrpcRequestExecutorTest.kt`

**Checkpoint**: User Stories 1 and 2 both work independently with full backward compatibility.

---

## Phase 5: User Story 3 - Manage Git Sync Exclusively Inside the Proto File Dialog Including Full Folder Sync (Priority: P2)

**Goal**: Remove the "Sync to Git" toggle from `GrpcRequestBar`, keep it exclusively inside `SelectProtoFileDialog` (for Git-backed collections on request-level file/folder selections), and recursively sync all `.proto` files (excluding non-`.proto` files and cleaning stale files) when a proto folder is synced to Git.

**Independent Test**: In a Git-backed collection, confirm no `grpc-proto-sync-toggle` exists in `GrpcRequestBar`, open `SelectProtoFileDialog`, select a folder with nested `.proto` and non-`.proto` files, enable `proto-dialog-sync-to-git-toggle`, save, push/clone the repository, and verify only `.proto` files were synced with intact relative paths and the request executes from the cloned copy.

### Implementation for User Story 3

- [X] T020 [P] [US3] Declare `expect fun copyProtoFilesFromDirectory(sourceDir: String, destinationDir: String): Boolean` in `core-storage/src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/opencollection/CollectionStorageDriver.kt`
- [X] T021 [US3] Implement `actual fun copyProtoFilesFromDirectory` (canonical same-directory no-op guard, stale destination directory cleanup, recursive `.proto`-only case-insensitive copy preserving relative subdirectory structure) in `core-storage/src/desktopMain/kotlin/io/github/makeforge/clapi/core/storage/opencollection/CollectionStorageDriver.desktop.kt`
- [X] T022 [US3] Add `SyncedProtoFolderResult(folderRelPath, protoFileRelPath)` and `syncRequestProtoFolder(sourceFolderPath, sourceProtoFilePath, requestId, collectionDir)` (preserving `<folderName>` under `./protobuf/requests/<safeReqId>/<folderName>` and computing portable relative paths for both folder and target `.proto` file) in `core-storage/src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/git/ProtoPathResolver.kt`
- [X] T023 [US3] Wire `ProtoPathResolver.syncRequestProtoFolder` in `OpenCollectionModelMapping.kt` when `collectionDir != null && protoSyncToGit && !protoFolderPath.isNullOrBlank() && !protoFilePath.isNullOrBlank()`, persisting portable relative paths for both `protoFolderPath` and `protoFilePath` (and inferring `protoSyncToGit` when either path is a relative Git proto path) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/persistence/OpenCollectionModelMapping.kt`
- [X] T024 [US3] Remove the top-bar `grpc-proto-sync-toggle` from `GrpcRequestBar` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/GrpcRequestEditor.kt`
- [X] T025 [US3] Scope `proto-dialog-sync-to-git-toggle` in `SelectProtoFileDialog` so it is visible only when `isGitSyncEnabled && !isCollectionProtoSelected`, forces `syncToGit = false` when a collection-level proto file is selected, and displays folder-sync guidance when a folder is selected in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/SelectProtoFileDialog.kt`

### Tests for User Story 3

- [X] T026 [P] [US3] Add unit tests for `syncRequestProtoFolder` covering recursive `.proto` copy preserving nested subfolders, excluding non-`.proto` files, cleaning stale `.proto` files when replacing a folder, idempotent re-save of an already-synced folder, and portable relative path generation in `core-storage/src/desktopTest/kotlin/io/github/makeforge/clapi/core/storage/git/ProtoPathResolverTest.kt`
- [X] T027 [P] [US3] Update/extend `ProtoGitSyncUiTest` to assert `grpc-proto-sync-toggle` does not exist in `GrpcRequestBar`, verify `proto-dialog-sync-to-git-toggle` visibility in `SelectProtoFileDialog` (hidden for Local storage and for collection-level proto selections, visible for Git storage on request-level file/folder selections), and verify folder Git sync persists portable relative paths in `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/ProtoGitSyncUiTest.kt`
- [X] T028 [US3] Add an E2E Git sync test verifying a gRPC request with a Git-synced proto folder (`greeter.proto` + `common/metadata.proto` + an excluded non-`.proto` file) pushes only `.proto` files with intact relative paths and parses/executes cleanly from a fresh clone in `qa-tests/src/test/kotlin/io/github/makeforge/clapi/qa/GitSyncE2ETest.kt`

**Checkpoint**: All three user stories are complete and independently verified.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Update user-facing documentation and execute full validation across all affected modules.

- [X] T029 [P] Update gRPC proto management guide to document selecting a proto folder and picking a target `.proto` file, automatic import resolution across folder + collection settings, and managing Git sync inside the Proto File dialog in `docs/request-types/grpc/proto-management.md`
- [X] T030 [P] Update gRPC overview Quick Start to mention selecting either a `.proto` file or a proto folder in `docs/request-types/grpc/index.md`
- [X] T031 [P] Update Git Sync overview to document request-level proto folder synchronization (`./protobuf/requests/<requestId>/<folderName>/...`, `.proto`-only filtering, and stale-file cleanup) in `docs/organization/collaboration/git-sync-overview.md`
- [X] T032 Run full validation suite from `quickstart.md` via `rtk gradlew :core-network:desktopTest :core-storage:desktopTest :ui-shared:desktopTest :ui-desktop:desktopTest :qa-tests:jvmTest` and verify all tests pass with zero regressions

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately.
- **Phase 2 (Foundational)**: Depends on Phase 1 — **BLOCKS** all user stories.
- **Phase 3 (US1 - P1 MVP)**: Depends on Phase 2.
- **Phase 4 (US2 - P1)**: Depends on Phase 2 and `SelectProtoFileDialog` updates in US1 (T011).
- **Phase 5 (US3 - P2)**: `core-storage` tasks (T020–T022, T026) can start immediately after Phase 2 in parallel with US1; UI/mapping integration (T023–T025, T027–T028) depends on T009, T011, and T012.
- **Phase 6 (Polish & Docs)**: Documentation updates (T029–T031) can run in parallel once implementation details are finalized; T032 runs last after all code, test, and doc updates.

### Parallel Opportunities

- **Phase 2**: T003, T005, T006 can run in parallel (different files); T004 follows T003; T007, T008, T009 follow T005/T006.
- **User Story 1**: T013, T014, T015 can run in parallel once T010–T012 are complete.
- **User Story 2**: T018 and T019 can run in parallel.
- **User Story 3**: T020–T022 + T026 (`core-storage`) can run in parallel with Phase 3/4 UI work; T026 and T027 can run in parallel.
- **Phase 6**: T029, T030, and T031 can all run in parallel.

---

## Parallel Example: Foundational Phase (Phase 2)

```bash
# Run in parallel (distinct files, no cross-dependencies):
Task T003: ui-shared/.../platform/PlatformApi.kt
Task T005: ui-shared/.../state/CollectionModels.kt
Task T006: ui-shared/.../state/RequestTabState.kt
```

## Parallel Example: User Story 1 Tests

```bash
# Run in parallel after T010-T012:
Task T013: core-network/.../ProtocJarParserTest.kt
Task T014: ui-shared/.../GrpcRequestExecutorTest.kt
Task T015: ui-desktop/.../GrpcProtoFolderDialogUiTest.kt
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (Setup) + Phase 2 (Foundational).
2. Complete Phase 3 (User Story 1: T010–T016).
3. **STOP & VALIDATE**: Run `:core-network:desktopTest`, `:ui-shared:desktopTest`, `:ui-desktop:desktopTest`, and `:qa-tests:jvmTest --tests "*GrpcApiE2ETest*"`.

### Incremental Delivery

1. Phase 1 + Phase 2 → Foundation ready.
2. Phase 3 (US1) → Folder + target `.proto` selection and import resolution working end-to-end (MVP).
3. Phase 4 (US2) → Single-file, collection-level, and legacy compatibility verified.
4. Phase 5 (US3) → Top-bar toggle removed, dialog-only Git sync + recursive `.proto` folder Git sync verified end-to-end.
5. Phase 6 → Docs updated and full multi-module test suite green.
