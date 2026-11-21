# Tasks: Fix Desktop UI High-Severity Violations

**Input**: Design documents from `specs/020-fix-desktop-ui-violations/` (`spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`)  
**Prerequisites**: `plan.md` (required), `spec.md` (required), `contracts/` (required)  
**Tests**: Verification via `./gradlew compileKotlinDesktop` and `./gradlew :ui-shared:allTests`

---

## Phase 1: Setup (Baseline & Infrastructure)

**Purpose**: Verify repository health and ensure shared scrollbar/tooltip abstractions are ready.

- [X] T001 Verify baseline compilation and existing test suite via `./gradlew compileKotlinDesktop`
- [X] T002 [P] Inspect and ensure `PlatformLazyVerticalScrollbar` and `PlatformColumnVerticalScrollbar` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/platform/PlatformScrollbar.kt` support required test tags and alignment
- [X] T003 [P] Verify `IconButtonWithTooltip` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/SharedTooltip.kt` conforms to `contracts/icon-button-tooltip-contract.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core component contracts that must be in place before wide rollout across editors.

- [X] T004 Standardize `IconButtonWithTooltip` signature and remove redundant hover handlers in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/SharedTooltip.kt`
- [X] T005 [P] Verify horizontal scrollbar support for tab strips in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/platform/PlatformScrollbar.kt`

**Checkpoint**: Foundation ready — user story implementation can proceed.

---

## Phase 3: User Story 1 - Desktop Scrollbar Visibility & Direct Scrolling (Priority: P1) 🎯 MVP

**Goal**: Equip all 16 scrollable workspace panels, editors, and dialogs with functional desktop scrollbars per `contracts/desktop-scrollbar-contract.md`.

**Independent Test**: Open responses, console logs, settings, and variable tables. Drag the vertical scrollbar thumbs on each panel to confirm fluid navigation and visual position tracking.

### Implementation for User Story 1

- [X] T006 [P] [US1] Add `PlatformLazyVerticalScrollbar` and `PlatformColumnVerticalScrollbar` to response body, headers, cookies, and timing views in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ResponseViewer.kt`
- [X] T007 [P] [US1] Add `PlatformLazyVerticalScrollbar` to console log view and test results view in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/BottomPanel.kt`
- [X] T008 [P] [US1] Add `PlatformColumnVerticalScrollbar` to the scrollable authorization configuration column in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/AuthEditor.kt`
- [X] T009 [P] [US1] Add desktop scrollbars to form data and urlencoded tables in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/BodyEditor.kt`
- [X] T010 [P] [US1] Add `PlatformColumnVerticalScrollbar` to collection settings column in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/CollectionEditor.kt`
- [X] T011 [P] [US1] Add `PlatformLazyVerticalScrollbar` to collection item selector in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ExportCollectionsDialog.kt`
- [X] T012 [P] [US1] Add `PlatformColumnVerticalScrollbar` to scrollable dialog content in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/GitSyncDialog.kt`
- [X] T013 [P] [US1] Add `PlatformLazyVerticalScrollbar` to variables table in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/GlobalVariablesDialog.kt`
- [X] T014 [P] [US1] Add desktop scrollbars to query editor, headers, and response panel in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/GraphQLPanel.kt`
- [X] T015 [P] [US1] Add desktop scrollbars to body and variable editors in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/GraphQLRequestEditor.kt`
- [X] T016 [P] [US1] Add `PlatformColumnVerticalScrollbar` to scrollable about dialog content in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/HelpAboutDialog.kt`
- [X] T017 [P] [US1] Add `PlatformColumnVerticalScrollbar` to specification import form in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ImportSpecificationDialog.kt`
- [X] T018 [P] [US1] Add `PlatformLazyVerticalScrollbar` to message timeline and communication view in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/RealtimePanel.kt`
- [X] T019 [P] [US1] Add `HorizontalScrollbar` to overflowing tab strip in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/RequestTabsBar.kt`
- [X] T020 [P] [US1] Add `PlatformColumnVerticalScrollbar` to active settings pane in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/SettingsDialog.kt`
- [X] T021 [P] [US1] Add desktop scrollbars to message payload pane and timeline in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/WebSocketRequestEditor.kt`

**Checkpoint**: All 16 scrollable panels now display functional desktop scrollbars without content obstruction.

---

## Phase 4: User Story 2 - Reliable Button Clicks & Contextual Tooltip Feedback (Priority: P1)

**Goal**: Eliminate redundant `.hoverable` on `IconButton` and adopt centralized `IconButtonWithTooltip` to prevent dropped clicks.

**Independent Test**: Hover and rapidly click buttons in the Code Editor search bar and Top Toolbar. Verify 0 dropped click events and proper tooltip appearance.

### Implementation for User Story 2

- [X] T022 [P] [US2] Remove redundant `.hoverable(interactionSource)` and migrate `EditorIconButtonWithTooltip` to use `IconButtonWithTooltip` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/CodeEditor.kt`
- [X] T023 [P] [US2] Remove redundant `.hoverable(interactionSource)` and migrate `ToolbarIconButtonWithTooltip` to use `IconButtonWithTooltip` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/TopToolbar.kt`
- [X] T024 [US2] Verify that rapid consecutive clicks on toolbar and code editor action buttons trigger without delay or event swallowing

**Checkpoint**: Action buttons in toolbars and code editor respond reliably to every click while displaying non-blocking tooltips.

---

## Phase 5: User Story 3 - Flexible Workspace Layout Adaptability & Dialog Constraint Control (Priority: P2)

**Goal**: Add `modifier: Modifier = Modifier` as the first optional parameter and bind to the root element across all primary workspace panels, editors, and modal dialogs per `contracts/composable-modifier-contract.md`.

**Independent Test**: Pass custom padding and layout modifiers to panels from test harnesses or parent layouts; verify that root containers honor outer bounds and resize dynamically.

### Implementation for User Story 3: Workspace Panels

- [X] T025 [P] [US3] Add `modifier: Modifier = Modifier` to `MainScreen` and `HttpWorkspaceContent` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/MainScreen.kt`
- [X] T026 [P] [US3] Add `modifier: Modifier = Modifier` to `RequestEditor` and `EditorTabBar` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/RequestEditor.kt`
- [X] T027 [P] [US3] Add `modifier: Modifier = Modifier` to `BottomPanel` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/BottomPanel.kt`
- [X] T028 [P] [US3] Add `modifier: Modifier = Modifier` to `ResponseViewer` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ResponseViewer.kt`
- [X] T029 [P] [US3] Add `modifier: Modifier = Modifier` to `AuthEditor` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/AuthEditor.kt`
- [X] T030 [P] [US3] Add `modifier: Modifier = Modifier` to `BodyEditor` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/BodyEditor.kt`
- [X] T031 [P] [US3] Add `modifier: Modifier = Modifier` to `CodeEditor` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/CodeEditor.kt`
- [X] T032 [P] [US3] Add `modifier: Modifier = Modifier` to `CollectionEditor` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/CollectionEditor.kt`
- [X] T033 [P] [US3] Add `modifier: Modifier = Modifier` to `FolderEditor` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/FolderEditor.kt`
- [X] T034 [P] [US3] Add `modifier: Modifier = Modifier` to `GraphQLPanel` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/GraphQLPanel.kt`
- [X] T035 [P] [US3] Add `modifier: Modifier = Modifier` to `GraphQLRequestEditor` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/GraphQLRequestEditor.kt`
- [X] T036 [P] [US3] Add `modifier: Modifier = Modifier` to `GrpcRequestEditor` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/GrpcRequestEditor.kt`
- [X] T037 [P] [US3] Add `modifier: Modifier = Modifier` to `RealtimePanel` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/RealtimePanel.kt`
- [X] T038 [P] [US3] Add `modifier: Modifier = Modifier` to `RequestBar` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/RequestBar.kt`
- [X] T039 [P] [US3] Add `modifier: Modifier = Modifier` to `RequestTabsBar` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/RequestTabsBar.kt`
- [X] T040 [P] [US3] Add `modifier: Modifier = Modifier` to `TopToolbar` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/TopToolbar.kt`
- [X] T041 [P] [US3] Add `modifier: Modifier = Modifier` to `WebSocketRequestEditor` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/WebSocketRequestEditor.kt`

### Implementation for User Story 3: Modal Dialogs

- [X] T042 [P] [US3] Add `modifier: Modifier = Modifier` to `ConfirmDeleteDialog` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ConfirmDeleteDialog.kt`
- [X] T043 [P] [US3] Add `modifier: Modifier = Modifier` to `DirtyCloseDialog` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/DirtyCloseDialog.kt`
- [X] T044 [P] [US3] Add `modifier: Modifier = Modifier` to `DirtyMultiCloseDialog` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/DirtyMultiCloseDialog.kt`
- [X] T045 [P] [US3] Add `modifier: Modifier = Modifier` to `EnvironmentEditDialog` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/EnvironmentEditDialog.kt`
- [X] T046 [P] [US3] Add `modifier: Modifier = Modifier` to `ErrorMessageDialog` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ErrorMessageDialog.kt`
- [X] T047 [P] [US3] Add `modifier: Modifier = Modifier` to `ExportCollectionsDialog` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ExportCollectionsDialog.kt`
- [X] T048 [P] [US3] Add `modifier: Modifier = Modifier` to `GitSyncDialog` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/GitSyncDialog.kt`
- [X] T049 [P] [US3] Add `modifier: Modifier = Modifier` to `GlobalVariablesDialog` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/GlobalVariablesDialog.kt`
- [X] T050 [P] [US3] Add `modifier: Modifier = Modifier` to `HelpAboutDialog` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/HelpAboutDialog.kt`
- [X] T051 [P] [US3] Add `modifier: Modifier = Modifier` to `ImportSpecificationDialog` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ImportSpecificationDialog.kt`
- [X] T052 [P] [US3] Add `modifier: Modifier = Modifier` to `OperationProgressDialog` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/OperationProgressDialog.kt`
- [X] T053 [P] [US3] Add `modifier: Modifier = Modifier` to `SelectProtoFileDialog` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/SelectProtoFileDialog.kt`
- [X] T054 [P] [US3] Add `modifier: Modifier = Modifier` to `SettingsDialog` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/SettingsDialog.kt`
- [X] T055 [P] [US3] Add `modifier: Modifier = Modifier` to `RenameVariantDialog` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/WebSocketRequestEditor.kt`

**Checkpoint**: All workspace panels and dialogs now adhere to modifier pass-through conventions.

---

## Phase 6: User Story 4 - Predictable Control Configuration & Parameter Ordering (Priority: P3)

**Goal**: Standardize parameter ordering in `SplitPane.kt` and `MethodBadge.kt` so mandatory parameters precede optional modifiers.

**Independent Test**: Instantiate `HorizontalSplitPane`, `VerticalSplitPane`, and `MethodBadge` with positional arguments and custom modifiers; verify clean compilation and correct slot placement.

### Implementation for User Story 4

- [X] T056 [P] [US4] Reorder parameters in `HorizontalSplitPane` and `VerticalSplitPane` to place `splitFraction` and `onSplitChanged` before `modifier: Modifier = Modifier` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/SplitPane.kt`
- [X] T057 [P] [US4] Reorder parameters in `MethodBadge` and badge helpers to place `modifier: Modifier = Modifier` as the first optional parameter before boolean flags in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/MethodBadge.kt`
- [X] T058 [US4] Update all split pane and method badge call sites across `ui-shared` (`MainScreen.kt`, `RequestTabsBar.kt`, `CollectionTreeNode.kt`, `SidebarHistoryRow.kt`, etc.) to match the new signatures

**Checkpoint**: Parameter signatures follow Compose standards with zero call-site regressions.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Verification, cleanup, and quality gates across all stories.

- [X] T059 Remove redundant `@Preview` declaration (`RequestEditorPreview`) from `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/RequestEditor.kt`
- [X] T060 Run full compilation and lint check via `./gradlew compileKotlinDesktop` and `./gradlew check`
- [X] T061 Run automated UI and unit test suites via `./gradlew :ui-shared:allTests :ui-desktop:desktopTest`
- [X] T062 Execute validation scenarios in `specs/020-fix-desktop-ui-violations/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies
- **Setup (Phase 1)**: Can start immediately.
- **Foundational (Phase 2)**: Depends on Phase 1; establishes scrollbar and tooltip core primitives.
- **User Story 1 (Phase 3 - P1)**: Depends on Phase 2; adds desktop scrollbars across 16 components.
- **User Story 2 (Phase 4 - P1)**: Depends on Phase 2; fixes hover click-swallowing.
- **User Story 3 (Phase 5 - P2)**: Depends on Phase 2; adds `modifier: Modifier = Modifier` to panels and dialogs. Can execute in parallel with US1/US2.
- **User Story 4 (Phase 6 - P3)**: Depends on Phase 2; standardizes parameter ordering.
- **Polish (Phase 7)**: Depends on all user stories being completed.

### Parallel Opportunities
- Within **User Story 1**, all 16 scrollbar addition tasks (T006–T021) operate on separate files and can run in parallel.
- Within **User Story 3**, all panel tasks (T025–T041) and dialog tasks (T042–T055) operate on independent files and can run in parallel.
- User Stories 1, 2, and 3 can be executed concurrently once Foundational (Phase 2) is complete.

---

## Implementation Strategy

### MVP First (User Story 1 & 2)
1. Complete Phase 1 (Setup) and Phase 2 (Foundational).
2. Complete Phase 3 (User Story 1: Desktop Scrollbars) and Phase 4 (User Story 2: Click Event Swallowing Fix).
3. Validate scrollbar ergonomics and rapid clicking in desktop preview/runner.

### Incremental Delivery
1. Foundation & Tooltip fix established.
2. Scrollbars integrated across all scrollable views (MVP).
3. Modifier parameter hygiene added to all panels and dialogs (US3).
4. Signature reordering in `SplitPane` and `MethodBadge` applied with call-site updates (US4).
5. Full project test suite and quickstart validation executed (Polish).
