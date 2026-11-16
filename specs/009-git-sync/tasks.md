---
description: "Task list for Git Collection Sync implementation"
---

# Tasks: Git Collection Sync

**Input**: Design documents from `specs/009-git-sync/` (`spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`)

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`

**Tests**: Test tasks included for unit, state, UI, and QA validation across user stories.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`[US1]`, `[US2]`, `[US3]`, `[US4]`)
- Exact file paths included in all descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Define core data structures, storage extension types, and UI localization resources required by all user stories.

- [x] T001 Create `StorageExtension`, `StorageType`, `GitStorageConfig`, and `GitCredentialsConfig` in `core-model/src/commonMain/kotlin/io/github/makeforge/clapi/core/model/storage/StorageExtension.kt` with validation rules (when `type == "git"`, `remoteUrl` and `branch` must be non-blank, `credentials.type == "system"`).
- [x] T002 [P] Update `OpenCollectionModelMapping.kt` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/persistence/OpenCollectionModelMapping.kt` to serialize and deserialize `extensions.storage` for both `"local"` and `"git"` configurations.
- [x] T003 [P] Add string localization entries for Storage tab, Git indicator, Git Sync Dialog, commit/push/pull actions, and Git error notices in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/i18n/Strings.kt`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core Git CLI process execution engine and repository management that MUST be complete before ANY user story UI work can begin.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [x] T004 Define `GitCliExecutor` interface, `GitCommandResult`, and Git exception hierarchy in `core-storage/src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/git/GitCliExecutor.kt`.
- [x] T005 Implement `DesktopGitCliExecutor` in `core-storage/src/desktopMain/kotlin/io/github/makeforge/clapi/core/storage/git/DesktopGitCliExecutor.kt` using `ProcessBuilder` on Dispatchers.IO to invoke host system `git` binary and capture stdout/stderr with timeouts.
- [x] T006 Implement `GitRepositoryManager` in `core-storage/src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/git/GitRepositoryManager.kt` handling clone, checkout, status (`git status --porcelain=v1`), stage/commit, push, and pull within `~/.clapi/storage/<repoId>/`.
- [x] T007 [P] Create unit tests for `DesktopGitCliExecutor` and `GitRepositoryManager` in `core-storage/src/desktopTest/kotlin/io/github/makeforge/clapi/core/storage/git/GitCliExecutorTest.kt` verifying version check, command execution, and status parsing against a local test Git repo.

**Checkpoint**: Foundation ready — user story implementation can now begin.

---

## Phase 3: User Story 1 - Configure Collection Storage in Collection Editor (Priority: P1) 🎯 MVP

**Goal**: Provide a "Storage" tab in the Collection Editor allowing users to choose between Local and Git storage. For Git storage, accept `remoteUrl` and `branch`, clone/connect the repository under `~/.clapi/storage/<repoId>/`, export `collection.opencollection.yaml` into the collection root folder, and save `extensions.storage`.

**Independent Test**: Open Collection Editor, select the "Storage" tab, choose "Git", enter remote URL and branch, click "Save", and verify repository clone exists in `~/.clapi/storage/` with `collection.opencollection.yaml` properly written.

### Tests for User Story 1

- [X] T008 [P] [US1] Create UI and state tests for Storage tab in `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/CollectionStorageTabUiTest.kt` verifying Local vs Git radio selection, input field visibility, Save button state, and error banner display.

### Implementation for User Story 1

- [X] T009 [P] [US1] Extend `CollectionEditorTab` enum with `STORAGE("Storage")` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/CollectionEditor.kt`.
- [X] T010 [US1] Add `storageType`, `gitRemoteUrl`, and `gitBranch` state properties to `CollectionNode` and `RequestTabState` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/AppState.kt`.
- [X] T011 [US1] Implement the Storage Tab UI composable in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/CollectionEditor.kt` with Local vs Git selection, `remoteUrl` and `branch` text fields, and "Save Storage Settings" button.
- [X] T012 [US1] Implement storage configuration save handler in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/CollectionEditor.kt` and `WorkspaceRepository.kt` triggering repository clone via `GitRepositoryManager`, writing `collection.opencollection.yaml` to `<repo-root>/<collection-name>/`, and updating `extensions.storage`.

**Checkpoint**: User Story 1 (MVP) is fully functional and testable independently.

---

## Phase 4: User Story 2 - Git Status Indicator and Sync Dialog (Priority: P1)

**Goal**: Show a Git status indicator in the bottom-left corner of the sidebar with branch name and uncommitted changes dot. Clicking opens the Git Sync Dialog to view modified files, write a commit message, commit, push, and pull changes (alerting on merge conflicts).

**Independent Test**: Make an edit in a Git-synced collection, verify the sidebar Git indicator shows uncommitted changes, click it to open the Git Sync Dialog, commit with a message, push to remote, and execute pull to verify conflict alert when merge conflicts occur.

### Tests for User Story 2

- [X] T013 [P] [US2] Create UI tests for sidebar Git indicator and Git Sync Dialog in `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/GitSyncUiTest.kt` verifying branch display, dirty dot, dialog opening, commit button enabling, and pull conflict warning.

### Implementation for User Story 2

- [X] T014 [US2] Implement live Git status tracking and polling in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/AppState.kt` observing active collection's repository state via `GitRepositoryManager.checkStatus()`.
- [X] T015 [US2] Implement bottom-left sidebar Git status indicator composable in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/Sidebar.kt` displaying branch icon, branch name, uncommitted changes badge/dot, and click trigger.
- [X] T016 [US2] Implement `GitSyncDialog` composable in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/GitSyncDialog.kt` with modified files list grouped by collection, commit message text field, "Commit", "Push", and "Pull" action buttons.
- [X] T017 [US2] Implement Commit and Push handlers in `GitSyncDialog.kt` executing `git add -A && git commit -m <msg>` and `git push` via `GitRepositoryManager`.
- [X] T018 [US2] Implement Pull handler and conflict detection in `GitSyncDialog.kt` executing `git pull`, parsing conflict status (`UU`, `AA`), alerting the user with external resolution instructions, and reloading the collection YAML on clean pulls.

**Checkpoint**: User Stories 1 AND 2 are both fully functional and testable independently.

---

## Phase 5: User Story 3 - Selective Protobuf File Sync with Dual-Path Resolution (Priority: P2)

**Goal**: Provide a "Sync to Git" toggle for collection-level proto files, import paths, and request proto files when Git sync is active. Synced files are copied into `<collection-folder>/protobuf/` and referenced via relative `./protobuf/...` in YAML, while un-synced files retain absolute paths. Both resolve transparently during request execution.

**Independent Test**: In a Git-synced collection, add a proto file, toggle "Sync to Git" ON, add another proto file with toggle OFF, save collection, verify physical copy in `protobuf/` subfolder and relative path in YAML, and execute a gRPC call verifying schema resolution succeeds for both files.

### Tests for User Story 3

- [X] T019 [P] [US3] Create unit tests for dual-path resolution in `core-storage/src/desktopTest/kotlin/io/github/makeforge/clapi/core/storage/git/ProtoPathResolverTest.kt` verifying `./protobuf/...` resolves against collection Git directory and absolute paths resolve against host filesystem.
- [X] T020 [P] [US3] Create UI tests for proto sync toggles in `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/ProtoGitSyncUiTest.kt` verifying toggle visibility when Git sync is enabled and persistence into OpenCollection YAML.

### Implementation for User Story 3

- [X] T021 [P] [US3] Implement `ProtoPathResolver` in `core-storage/src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/git/ProtoPathResolver.kt` resolving relative `./protobuf/...` paths against collection repository directory and preserving absolute filesystem paths.
- [X] T022 [US3] Update `ProtoFileItemState` and `ProtoImportPathState` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/AppState.kt` to add `syncToGit: Boolean` flag.
- [X] T023 [US3] Add "Sync to Git" toggle switch in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/CollectionEditor.kt` (Protobuf tab) and in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/GrpcRequestEditor.kt`.
- [X] T024 [US3] Implement proto file copying to `<collection-folder>/protobuf/` and relative path rewriting (`./protobuf/...`) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/persistence/OpenCollectionModelMapping.kt`.
- [X] T025 [US3] Integrate `ProtoPathResolver` into `ProtocJarParser.kt` in `core-network/src/desktopMain/kotlin/io/github/makeforge/clapi/core/network/grpc/ProtocJarParser.kt` and gRPC execution pipeline in `RequestExecutor.kt`.

**Checkpoint**: User Stories 1, 2, and 3 are functional and testable independently.

---

## Phase 6: User Story 4 - Multi-Collection Git Repository Organization (Priority: P3)

**Goal**: Support multiple collections pointing to the same remote Git repository and branch, isolating each collection in its own root subfolder, grouping changes by collection in the Git Sync Dialog, and switching sidebar Git indicator based on the active collection.

**Independent Test**: Connect two collections to the same Git repository URL and branch, make edits in both, open Git Sync Dialog, verify changes are grouped by collection name, and commit/push together.

### Tests for User Story 4

- [x] T026 [P] [US4] Create multi-collection Git sync E2E test in `qa-tests/src/test/kotlin/io/github/makeforge/clapi/qa/MultiCollectionGitSyncE2ETest.kt` verifying shared repository cloning, per-collection subfolder isolation, and unified commit/push.

### Implementation for User Story 4

- [x] T027 [US4] Update `GitRepositoryManager` in `core-storage/src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/git/GitRepositoryManager.kt` to register multiple collections against the same repository clone and map working directory changes to their respective collection roots.
- [x] T028 [US4] Update `AppState.kt` and `Sidebar.kt` to update the active Git repository and branch indicator reactively when the user switches between collections or tabs.
- [x] T029 [US4] Update `GitSyncDialog.kt` to render change lists grouped under expandable/collapsible collection headers with individual file counts.

**Checkpoint**: All user stories (US1-US4) are fully functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Error handling, end-to-end quickstart validation, and regression verification across all test suites.

- [x] T030 [P] Implement friendly error translation for Git errors (Git not installed, SSH authentication failure, network timeout, branch not found) in `core-storage/src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/git/GitErrorTranslator.kt`.
- [x] T031 Create end-to-end Git sync round-trip QA integration test in `qa-tests/src/test/kotlin/io/github/makeforge/clapi/qa/GitSyncE2ETest.kt` executing the scenarios from `quickstart.md`.
- [x] T032 Run regression verification test suites: `:core-model:allTests`, `:core-storage:allTests`, `:core-network:allTests`, `:ui-shared:desktopTest`, `:ui-desktop:desktopTest`, and `:qa-tests:jvmTest`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Phase 1 (T001) — BLOCKS all user stories.
- **User Story 1 (Phase 3)**: Depends on Phase 2 (T004-T006). Delivers MVP.
- **User Story 2 (Phase 4)**: Depends on Phase 2 and integrates with US1 storage state.
- **User Story 3 (Phase 5)**: Depends on Phase 2 and US1 collection folder storage layout.
- **User Story 4 (Phase 6)**: Depends on US1 and US2 completion.
- **Polish (Phase 7)**: Depends on all user stories (US1-US4).

### User Story Completion Order

```text
Foundational (Phase 2)
        │
        ▼
User Story 1 (P1: Configure Storage & Clone) ──► MVP Checkpoint
        │
        ├──► User Story 2 (P1: Indicator & Sync Dialog)
        │           │
        │           ▼
        └──► User Story 3 (P2: Selective Protobuf Sync)
                    │
                    ▼
             User Story 4 (P3: Multi-Collection Organization)
                    │
                    ▼
             Polish & Verification (Phase 7)
```

---

## Parallel Execution Examples

### User Story 1
```bash
# Models and UI tests in parallel:
Task: "T008 [P] [US1] Create UI and state tests for Storage tab in ui-desktop/src/desktopTest/.../CollectionStorageTabUiTest.kt"
Task: "T009 [P] [US1] Extend CollectionEditorTab enum with STORAGE("Storage") in ui-shared/src/commonMain/.../CollectionEditor.kt"
```

### User Story 2
```bash
# Test creation and state polling in parallel:
Task: "T013 [P] [US2] Create UI tests for sidebar Git indicator and Git Sync Dialog in ui-desktop/src/desktopTest/.../GitSyncUiTest.kt"
Task: "T014 [US2] Implement live Git status tracking and polling in ui-shared/src/commonMain/.../AppState.kt"
```

### User Story 3
```bash
# Path resolver logic and UI tests in parallel:
Task: "T019 [P] [US3] Create unit tests for dual-path resolution in core-storage/src/desktopTest/.../ProtoPathResolverTest.kt"
Task: "T020 [P] [US3] Create UI tests for proto sync toggles in ui-desktop/src/desktopTest/.../ProtoGitSyncUiTest.kt"
Task: "T021 [P] [US3] Implement ProtoPathResolver in core-storage/src/commonMain/.../ProtoPathResolver.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)
1. Complete Phase 1 (Setup) and Phase 2 (Foundational: `GitCliExecutor` + `GitRepositoryManager`).
2. Implement Phase 3 (User Story 1: Storage Tab in Collection Editor).
3. **STOP and VALIDATE**: Verify a collection can be configured for Git, cloned to `~/.clapi/storage/`, and exported as `collection.opencollection.yaml`.

### Incremental Delivery
1. Foundation + US1 = User can connect collections to Git repositories.
2. Add US2 = User can see dirty status, commit, push, and pull directly from the sidebar.
3. Add US3 = gRPC engineers can selectively sync `.proto` schemas with relative paths.
4. Add US4 = Teams managing monorepos can host multiple collections in a single Git repository.
5. Polish = End-to-end automated QA validation and regression test pass.
