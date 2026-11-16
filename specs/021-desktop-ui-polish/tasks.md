# Tasks: Desktop UI Polish & Modularization

**Feature**: `021-desktop-ui-polish`  
**Date**: 2026-11-14  
**Input**: Design documents from `/specs/021-desktop-ui-polish/`  
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

---

## Format: `[ID] [P?] [Story] Description`
- **[P]**: Can run in parallel (separate files, no inter-task blocking)
- **[Story]**: User Story label (`[US1]`, `[US2]`, `[US3]`, `[US4]`, `[US5]`)
- Every task includes an exact file path.

---

## Phase 1: Setup (Shared Infrastructure & Theming)

**Purpose**: Core helpers, semantic theme tokens, and subpackage directory structures.

- [X] T001 Add `StatusWarning` and `StatusInfo` semantic color tokens to `ClapiColors` and `LocalAppColors` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/theme/ClapiTheme.kt`
- [X] T002 Add missing localized string property accessors and translation keys in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/i18n/Strings.kt`
- [X] T003 Create desktop ergonomics helper extensions (`onEscapeDismiss`, `handPointerHover`) in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/DesktopErgonomics.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Ensure baseline infrastructure is ready before user stories execute.

- [X] T004 Create subpackage directories `dialogs/env`, `dialogs/settings`, `mcp`, and `sidebar` under `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/`
- [X] T005 Verify compile status of base shared UI module via `./gradlew :ui-shared:compileKotlinDesktop`

**Checkpoint**: Ergonomics helpers, theme tokens, and package scaffolds ready.

---

## Phase 3: User Story 1 - Effortless Dialog Dismissal via Keyboard Navigation (Priority: P1) 🎯 MVP

**Goal**: Every modal dialog dismisses immediately upon pressing the `Escape` key, honoring dirty edit confirmations where appropriate.

**Independent Test**: Open each dialog, press `Escape`, and verify immediate dismissal or discard prompt without triggering destructive actions.

### Implementation for User Story 1

- [X] T006 [P] [US1] Add `Escape` key dismissal to `ConfirmDeleteDialog` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/ConfirmDeleteDialog.kt`
- [X] T007 [P] [US1] Add `Escape` key dismissal to `DirtyCloseDialog` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/DirtyCloseDialog.kt`
- [X] T008 [P] [US1] Add `Escape` key dismissal to `DirtyMultiCloseDialog` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/DirtyMultiCloseDialog.kt`
- [X] T009 [P] [US1] Add `Escape` key dismissal with dirty check to `EnvironmentEditDialog` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/EnvironmentEditDialog.kt`
- [X] T010 [P] [US1] Add `Escape` key dismissal to `ErrorMessageDialog` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/ErrorMessageDialog.kt`
- [X] T011 [P] [US1] Add `Escape` key dismissal to `ExportCollectionsDialog` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/ExportCollectionsDialog.kt`
- [X] T012 [P] [US1] Add `Escape` key dismissal to `GitSyncDialog` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/GitSyncDialog.kt`
- [X] T013 [P] [US1] Add `Escape` key dismissal to `GlobalVariablesDialog` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/GlobalVariablesDialog.kt`
- [X] T014 [P] [US1] Add `Escape` key dismissal to `HelpAboutDialog` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/HelpAboutDialog.kt`
- [X] T015 [P] [US1] Add `Escape` key dismissal to `ImportSpecificationDialog` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/ImportSpecificationDialog.kt`
- [X] T016 [P] [US1] Add `Escape` key dismissal to `OperationProgressDialog` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/OperationProgressDialog.kt`
- [X] T017 [P] [US1] Add `Escape` key dismissal to `SelectProtoFileDialog` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/SelectProtoFileDialog.kt`
- [X] T018 [P] [US1] Add `Escape` key dismissal to `SettingsDialog` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/SettingsDialog.kt`
- [X] T019 [P] [US1] Add `Escape` key dismissal to `RenameVariantDialog` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/WebSocketRequestEditor.kt`

**Checkpoint**: All 14 modal dialogs support keyboard `Escape` dismissal per CTR-DIALOG-ESCAPE-001.

---

## Phase 4: User Story 2 - Clear Cursor Affordances & Pointer Feedback (Priority: P2)

**Goal**: Mouse users see a pointing hand cursor (`PointerIcon.Hand`) whenever hovering over interactive rows, cards, custom buttons, badges, chips, or dropdown anchors.

**Independent Test**: Hover mouse over custom clickable controls; verify the OS cursor immediately changes to a pointing hand.

### Implementation for User Story 2

- [X] T020 [P] [US2] Add pointing hand hover cursor to token copy fields and action buttons in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/AuthEditor.kt`
- [X] T021 [P] [US2] Add pointing hand hover cursor to `FormDataRowItem`, `TypeDropdown`, `FilePickerCell`, and `AddRowButton` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/BodyEditor.kt`
- [X] T022 [P] [US2] Add pointing hand hover cursor to `ConsoleRow` and action buttons in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/BottomPanel.kt`
- [X] T023 [P] [US2] Add pointing hand hover cursor to `ProtoFileRow` and `ProtoImportPathRow` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/CollectionEditor.kt`
- [X] T024 [P] [US2] Add pointing hand hover cursor to `GlobalVariableRow` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/GlobalVariablesDialog.kt`
- [X] T025 [P] [US2] Add pointing hand hover cursor to `KeyValueRow` and action controls in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/KeyValueEditor.kt`
- [X] T026 [P] [US2] Add pointing hand hover cursor to clickable `MethodBadge` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/MethodBadge.kt`
- [X] T027 [P] [US2] Add pointing hand hover cursor to `MethodDropdown`, `SendButton`, `SaveButton`, and `CopyCurlButton` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/RequestBar.kt`
- [X] T028 [P] [US2] Add pointing hand hover cursor to `MetricChip` and `ResponseTabBar` tab buttons in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/ResponseViewer.kt`
- [X] T029 [P] [US2] Add pointing hand hover cursor to `SettingToggle`, `SettingAction`, and category tabs in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/SettingsDialog.kt`
- [X] T030 [P] [US2] Add pointing hand hover cursor to `EnvironmentChip` and toolbar action buttons in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/TopToolbar.kt`
- [X] T031 [P] [US2] Add pointing hand hover cursor to `FormatDropdown` and `TimelineMessageItem` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/WebSocketRequestEditor.kt`
- [X] T032 [P] [US2] Add pointing hand hover cursor to collection tree item rows in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/sidebar/CollectionTreeNode.kt`
- [X] T033 [P] [US2] Add pointing hand hover cursor to history request rows in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/sidebar/SidebarHistoryRow.kt`

**Checkpoint**: Cursor affordances adhere strictly to CTR-POINTER-HOVER-002 across all interactive surfaces.

---

## Phase 5: User Story 3 - Smooth List Scrolling & State Stability via Unique Keys (Priority: P3)

**Goal**: All virtualized lazy lists use stable, deterministic item keys so updates, reordering, and additions do not trigger visual jitter or state corruption.

**Independent Test**: Stream logs in the bottom panel or filter response headers; verify smooth rendering without layout recreation or state loss.

### Implementation for User Story 3

- [X] T034 [P] [US3] Add stable composite keys to console log rows (`itemsIndexed`) in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/BottomPanel.kt`
- [X] T035 [P] [US3] Add stable composite keys to test result rows (`items`) in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/BottomPanel.kt`
- [X] T036 [P] [US3] Add stable keys to working secrets list in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/EnvironmentEditDialog.kt`
- [X] T037 [P] [US3] Add stable composite keys to server log items in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/McpPanel.kt`
- [X] T038 [P] [US3] Add stable composite keys to response headers list (`itemsIndexed`) in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/ResponseViewer.kt`
- [X] T039 [P] [US3] Add stable composite keys to response cookies list (`itemsIndexed`) in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/ResponseViewer.kt`
- [X] T040 [P] [US3] Add stable composite keys to variable auto-complete popup list in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/VariableHighlight.kt`
- [X] T041 [P] [US3] Add stable keys to collection picker list in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/ExportCollectionsDialog.kt`

**Checkpoint**: Zero un-keyed lazy lists remain in `ui-shared` per CTR-LAZY-KEY-003.

---

## Phase 6: User Story 4 - Visual Polish through Theme Tokens & Centralized Strings (Priority: P4)

**Goal**: Standardize on `ClapiColors` tokens and `Strings.kt` localization, eliminating hardcoded hex codes, static white text, and raw string literals.

**Independent Test**: Toggle between light and dark themes; verify high-contrast, theme-adapted text and colors across all panels; verify localized strings.

### Implementation for User Story 4

- [X] T042 [P] [US4] Replace hardcoded colors and extract raw string literals in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/AuthEditor.kt`
- [X] T043 [P] [US4] Replace hardcoded colors (`Color(0xFF49CC90)`, `Color(0xFFE535AB)`, `Color.White`) with theme tokens in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/GraphQLPanel.kt`
- [X] T044 [P] [US4] Replace hardcoded colors and extract strings in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/GraphQLRequestEditor.kt`
- [X] T045 [P] [US4] Replace hardcoded error red (`Color(0xFFD32F2F)`) with `ClapiColors.Error` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/GrpcRequestEditor.kt`
- [X] T046 [P] [US4] Replace hardcoded status colors (`Color(0xFF22C55E)`, `Color(0xFFEAB308)`) with `ClapiColors.StatusSuccess` and `ClapiColors.StatusWarning` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/McpPanel.kt`
- [X] T047 [P] [US4] Replace hardcoded status and method colors in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/RealtimePanel.kt`
- [X] T048 [P] [US4] Replace hardcoded timing phase colors (`Color(0xFF42A5F5)`, etc.) with semantic palette tokens in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/ResponseViewer.kt`
- [X] T049 [P] [US4] Replace hardcoded colors and extract connection strings in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/WebSocketRequestEditor.kt`
- [X] T050 [P] [US4] Extract hardcoded string literals into `Strings.kt` across `CollectionEditor.kt`, `ExportCollectionsDialog.kt`, `GitSyncDialog.kt`, and `Sidebar.kt`
- [X] T051 [P] [US4] Verify light/dark theme contrast compliance across all updated components

**Checkpoint**: Zero raw color constants remain in component presentation code per CTR-THEME-STRING-004.

---

## Phase 7: User Story 5 - Modular Component Architecture & File Sizing Decomposition (Priority: P5)

**Goal**: Decompose all monolithic files exceeding 1,000 lines into cohesive subpackages with focused sub-composables under 100 lines each, and standardize tooltip buttons.

**Independent Test**: Verify file line count across `ui-shared/src/commonMain/...` with `wc -l`; ensure every single file is below 1,000 lines and all composables compile cleanly.

### Implementation for User Story 5: Deduplication & Monolith Decomposition

- [X] T052 [P] [US5] Replace duplicate `ToolbarIconButtonWithTooltip` in `TopToolbar.kt` and `EditorIconButtonWithTooltip` in `CodeEditor.kt` with `SharedTooltip.kt`'s `IconButtonWithTooltip`
- [X] T053 [US5] Extract `EnvVariableTable.kt` from `EnvironmentEditDialog.kt` into `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/dialogs/env/EnvVariableTable.kt`
- [X] T054 [US5] Extract `EnvKeePassXcSecretRow.kt` and `EnvKeeperSecretRow.kt` from `EnvironmentEditDialog.kt` into `dialogs/env/`
- [X] T055 [US5] Extract `EnvAwsSecretRow.kt`, `EnvAzureSecretRow.kt`, and `EnvGcpSecretRow.kt` from `EnvironmentEditDialog.kt` into `dialogs/env/`
- [X] T056 [US5] Refactor main coordinator `EnvironmentEditDialog.kt` into `dialogs/env/` and provide backwards-compatible facade in `components/`
- [X] T057 [US5] Extract `GeneralSettingsPane.kt`, `ThemeSettingsPane.kt`, and `VaultSettingsPane.kt` from `SettingsDialog.kt` into `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/dialogs/settings/`
- [X] T058 [US5] Extract `SecretsSettingsPane.kt`, `DataSettingsPane.kt`, and `SettingControls.kt` from `SettingsDialog.kt` into `dialogs/settings/`
- [X] T059 [US5] Refactor main coordinator `SettingsDialog.kt` into `dialogs/settings/` and provide backwards-compatible facade in `components/`
- [X] T060 [US5] Extract `McpConnectionBar.kt` and `McpLogView.kt` from `McpPanel.kt` into `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/mcp/`
- [X] T061 [US5] Extract `McpToolsSection.kt`, `McpResourcesSection.kt`, and `McpPromptsSection.kt` from `McpPanel.kt` into `components/mcp/`
- [X] T062 [US5] Refactor main coordinator `McpPanel.kt` into `components/mcp/` and provide backwards-compatible facade in `components/`
- [X] T063 [US5] Extract `SidebarCollectionsSection.kt` and `SidebarEnvironmentsSection.kt` from `Sidebar.kt` into `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/sidebar/`
- [X] T064 [US5] Extract `SidebarHistorySection.kt` and `SidebarFooter.kt` from `Sidebar.kt` into `components/sidebar/`
- [X] T065 [US5] Refactor main coordinator `Sidebar.kt` in `components/sidebar/` to under 500 lines
- [X] T066 [US5] Extract `WebSocketAddressBar.kt`, `WebSocketMessagePane.kt`, and `WebSocketTimelinePane.kt` from `WebSocketRequestEditor.kt` into sub-components
- [X] T067 [US5] Extract toolbar and search bar sub-components from `CodeEditor.kt` into `components/codeeditor/`
- [X] T068 [US5] Extract protobuf settings and storage settings sub-components from `CollectionEditor.kt` into `components/collectioneditor/`
- [X] T069 [US5] Extract dropdown list and popup sub-components from `VariableHighlight.kt` into `components/variable/`

**Checkpoint**: Zero component files in `ui-shared` exceed 1,000 lines of code.

---

## Phase 8: Polish & Quality Gates

**Purpose**: Verification, build hygiene, automated tests, and regression testing.

- [X] T070 Run full project compilation check via `./gradlew compileKotlinDesktop`
- [X] T071 Run automated test suites via `./gradlew :ui-shared:desktopTest :ui-desktop:desktopTest`
- [X] T072 Verify file line limits with `wc -l` script to confirm 0 files exceed 1,000 lines
- [X] T073 Validate quickstart scenarios against `specs/021-desktop-ui-polish/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies
- **Setup (Phase 1)**: Can start immediately.
- **Foundational (Phase 2)**: Depends on Phase 1; establishes subpackages and ergonomics helpers.
- **User Story 1 (Phase 3 - P1)**: Depends on Phase 2; implements Escape dismissal across dialogs.
- **User Story 2 (Phase 4 - P2)**: Depends on Phase 2; implements pointer hover icons across controls.
- **User Story 3 (Phase 5 - P3)**: Depends on Phase 2; implements stable lazy list keys.
- **User Story 4 (Phase 6 - P4)**: Depends on Phase 1 theme tokens; updates colors and strings.
- **User Story 5 (Phase 7 - P5)**: Depends on US1–US4 completion; decomposes files into subpackages.
- **Polish (Phase 8)**: Depends on all user stories being complete.

### Parallel Opportunities
- Within **User Story 1**, all 14 dialog tasks (T006–T019) operate on separate files and can run in parallel.
- Within **User Story 2**, all 14 cursor affordance tasks (T020–T033) operate on distinct components and can run in parallel.
- Within **User Story 3**, all 8 lazy list key tasks (T034–T041) operate on separate files and can run in parallel.
- Within **User Story 4**, all 10 theming and localization tasks (T042–T051) can run in parallel.

---

## Implementation Strategy

### MVP First (User Story 1)
1. Complete Phase 1 (Setup) and Phase 2 (Foundational).
2. Complete Phase 3 (User Story 1: Dialog Escape Dismissal).
3. Validate Escape key navigation in desktop runner (`./gradlew :ui-desktop:run`).

### Incremental Delivery
1. Foundational helpers and theme tokens ready.
2. Dialog keyboard dismissal operational (MVP).
3. Mouse cursor affordances active on all interactive surfaces (US2).
4. Virtualized list state stability and item keys applied (US3).
5. Theme tokens and localization strings consolidated (US4).
6. Monolithic files decomposed into clean subpackages (< 1,000 lines per file) (US5).
7. Full regression test suite and quickstart validation executed (Phase 8).
