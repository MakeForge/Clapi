# Tasks: Environment Enhancements

**Input**: Design documents from `specs/026-environment-enhancements/`
- Spec: [spec.md](spec.md)
- Plan: [plan.md](plan.md)
- Research: [research.md](research.md)
- Data Model: [data-model.md](data-model.md)
- Contracts: [contracts/](contracts/)
- Quickstart: [quickstart.md](quickstart.md)

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization, baseline data structures, and helper utilities.

- [ ] T001 Inspect and verify environment models and OpenCollection serializers in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/EnvironmentModels.kt`
- [ ] T002 [P] Setup color palette presets and hex parsing utilities in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/dialogs/env/EnvironmentDialogHelpers.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core state modeling and inheritance resolution algorithms that MUST be complete before ANY user story can be implemented.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T003 Make `color` and `extends` observable with `mutableStateOf` in `EnvState` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/EnvironmentModels.kt`
- [ ] T004 Implement `resolveEffectiveVariables`, `resolveVariableProjections`, and cycle-safe `getEligibleParentEnvironments` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/EnvironmentModels.kt`
- [ ] T005 [P] Update `AppState.activeVariableLayers` to resolve environment variables through inheritance in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/AppState.kt`
- [ ] T006 [P] Create unit tests for inheritance, cycle detection, and variable overrides in `ui-shared/src/commonTest/kotlin/io/github/makeforge/clapi/ui/shared/state/EnvironmentInheritanceTest.kt`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel.

---

## Phase 3: User Story 1 - Environment Inheritance & Variable Overrides (Priority: P1) 🎯 MVP

**Goal**: Support `OpenCollection.Environment.extends` allowing an environment to inherit variables from a parent environment with nearest-child override precedence, cycle prevention, and visual override badges in the edit dialog.

**Independent Test**: Create environment `Base` with `BASE_URL=https://api.example.com` and `TIMEOUT=30`, and child environment `Dev` extending `Base` with `BASE_URL=https://dev.api.example.com`. Verify that `Dev` resolves `TIMEOUT=30` from `Base`, resolves `BASE_URL=https://dev.api.example.com` locally, and renders the "Overridden" badge for `BASE_URL` in the edit dialog.

### Tests for User Story 1 ⚠️

- [ ] T007 [P] [US1] Unit test for multi-tier variable inheritance, overrides, and cycle detection in `ui-shared/src/commonTest/kotlin/io/github/makeforge/clapi/ui/shared/state/EnvironmentInheritanceTest.kt`
- [ ] T008 [P] [US1] Desktop Compose UI test for extends dropdown and override badge in `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/EnvironmentEditorUiTest.kt`

### Implementation for User Story 1

- [ ] T009 [US1] Implement extends dropdown in `EnvironmentEditDialog` with eligible environment options and disabled state when no other environments exist in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/dialogs/env/EnvironmentEditDialog.kt`
- [ ] T010 [US1] Display inherited variables and render "Overridden" badge with tooltip for overridden variables in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/dialogs/env/EnvironmentEditDialog.kt`
- [ ] T011 [US1] Persist `extends` selection to `EnvState` and canonical OpenCollection on dialog save in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/dialogs/env/EnvironmentEditDialog.kt`

**Checkpoint**: User Story 1 should be fully functional and testable independently.

---

## Phase 4: User Story 2 - Instant Environment Creation & Expanded Navigation (Priority: P1)

**Goal**: Automatically expand the Environments section in the sidebar and immediately open the Edit Environment dialog when a new environment is created.

**Independent Test**: Collapse the sidebar Environments section, click the "New Environment" button, input an environment name, and confirm creation. Verify that the section expands and the Edit Environment dialog is displayed focused on the newly created environment.

### Tests for User Story 2 ⚠️

- [ ] T012 [P] [US2] UI test verifying environment creation expands sidebar and opens edit dialog in `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/EnvironmentEditorUiTest.kt`

### Implementation for User Story 2

- [ ] T013 [US2] Update environment creation callback in `Sidebar.kt` to expand environments (`state.environmentsExpanded = true`, `state.settings.environmentsExpanded = true`) and invoke `state.openEnvEdit(newIndex)` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/sidebar/Sidebar.kt`
- [ ] T014 [US2] Update toolbar environment creation flow in `TopToolbar.kt` to expand sidebar and open edit dialog in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/TopToolbar.kt`

**Checkpoint**: User Stories 1 and 2 should both work independently.

---

## Phase 5: User Story 3 - Quick-Add Top Row for Variables & External Secrets (Priority: P2)

**Goal**: Provide pinned inline quick-add input rows at the top of the variables and external secrets tables in `EnvironmentEditDialog` for frictionless data entry without scrolling.

**Independent Test**: Open an environment containing 20+ variables so the list scrolls. Verify the top row is pinned, enter a key and value, press Enter, and assert that the new variable is appended and the top fields reset to blank.

### Tests for User Story 3 ⚠️

- [ ] T015 [P] [US3] UI test verifying inline quick-add row at the top of variables and secrets tables in `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/EnvironmentEditorUiTest.kt`

### Implementation for User Story 3

- [ ] T016 [US3] Implement pinned top inline quick-add row in Variables tab of `EnvironmentEditDialog` with Enter key commit in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/dialogs/env/EnvironmentEditDialog.kt`
- [ ] T017 [US3] Implement pinned top inline quick-add row in External Secrets tab of `EnvironmentEditDialog` with provider fields in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/dialogs/env/EnvironmentEditDialog.kt`

**Checkpoint**: User Stories 1, 2, and 3 should all be functional independently.

---

## Phase 6: User Story 4 - Environment Color Customization & Visual Palette (Priority: P2)

**Goal**: Allow choosing an environment color via 8 predefined color swatches or custom hex input, visually reflected in the sidebar, header toolbar, and dialogs.

**Independent Test**: Select a predefined color swatch or input a custom hex string in `EnvironmentEditDialog`. Save and verify that the environment indicator dot in `SidebarEnvironmentRow` and the environment picker in `TopToolbar` render the selected color.

### Tests for User Story 4 ⚠️

- [ ] T018 [P] [US4] UI test verifying predefined color selection and custom hex color rendering in `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/EnvironmentEditorUiTest.kt`

### Implementation for User Story 4

- [ ] T019 [US4] Add color palette swatches and custom hex input controls to `EnvironmentEditDialog.kt` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/dialogs/env/EnvironmentEditDialog.kt`
- [ ] T020 [P] [US4] Update `SidebarEnvironmentRow` to render `env.color` in the indicator dot in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/sidebar/SidebarEnvironmentRow.kt`
- [ ] T021 [P] [US4] Update `TopToolbar` environment dropdown button and items to display the environment color in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/TopToolbar.kt`

**Checkpoint**: User Stories 1 through 4 should all work independently.

---

## Phase 7: User Story 5 - Reliable YAML Drag-and-Drop Import & Consistent Action Icons (Priority: P3)

**Goal**: Resolve the bug where dragging and dropping `.yaml`/`.yml` files into the import dialog fails, and update the Environment Import button icon to match Collection Import (`ic_download`).

**Independent Test**: Drag a `.yaml` or `.yml` file into the import dropzone and confirm it parses successfully; inspect the sidebar to confirm environment import uses `ic_download`.

### Tests for User Story 5 ⚠️

- [ ] T022 [P] [US5] Compose UI test for YAML drag-and-drop file ingestion in `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/ImportSpecificationUiTest.kt`
- [ ] T023 [P] [US5] UI test asserting the environment import button displays the download icon in `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/SidebarImportExportUiTest.kt`

### Implementation for User Story 5

- [ ] T024 [US5] Update environment import button icon to `Res.drawable.ic_download` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/sidebar/SidebarEnvironmentsSection.kt`
- [ ] T025 [US5] Implement desktop drag-and-drop listener for `.yaml`, `.yml`, `.json` files on the import dropzone in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ImportSpecificationDialog.kt`

**Checkpoint**: User Stories 1 through 5 should all work independently.

---

## Phase 8: User Story 6 - External Secrets Configuration Import & Export (Priority: P3)

**Goal**: Export and import external secrets configuration definitions (provider type, identifiers, field targets) while strictly omitting sensitive plaintext secret values and credentials.

**Independent Test**: Export an environment with AWS Secrets Manager configuration to a JSON string; verify configuration fields are serialized and no secret values are leaked, then import and verify configuration is restored.

### Tests for User Story 6 ⚠️

- [ ] T026 [P] [US6] Unit tests verifying external secret configuration export/import round-trip and secret value sanitization in `ui-shared/src/commonTest/kotlin/io/github/makeforge/clapi/ui/shared/persistence/EnvironmentImportExportTest.kt`

### Implementation for User Story 6

- [ ] T027 [US6] Update `ImportExportRepository.exportEnvironmentToString` to include `color`, `extends`, and sanitized `externalSecrets` while omitting secret values in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/persistence/ImportExportRepository.kt`
- [ ] T028 [US6] Update `ImportExportRepository.importEnvironmentFromString` to parse `color`, `extends`, and `externalSecrets` into `EnvState` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/persistence/ImportExportRepository.kt`

**Checkpoint**: All user stories should now be independently functional.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories and end-to-end QA validation.

- [ ] T029 [P] Create comprehensive end-to-end QA test suite covering creation, inheritance, colors, quick-add, and drag-and-drop in `qa-tests/src/desktopTest/kotlin/io/github/makeforge/clapi/qa/EnvironmentEnhancementsQaTest.kt`
- [ ] T030 Execute quickstart validation guide and verify all test suites pass

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately.
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories.
- **User Stories (Phase 3+)**: All depend on Foundational phase completion:
  - User Story 1 (P1): Can start after Foundational phase (Phase 2).
  - User Story 2 (P1): Can start after Foundational phase (Phase 2).
  - User Story 3 (P2): Can start after Foundational phase (Phase 2).
  - User Story 4 (P2): Can start after Foundational phase (Phase 2).
  - User Story 5 (P3): Can start after Foundational phase (Phase 2).
  - User Story 6 (P3): Can start after Foundational phase (Phase 2).
- **Polish (Phase 9)**: Depends on all user stories being complete.

### User Story Dependencies

- **US1 (Inheritance)**: Foundational models & resolver (T003-T006).
- **US2 (Instant Create)**: Foundational models (T003). Independent from US1.
- **US3 (Quick-Add Row)**: Foundational models (T003). Independent from US1/US2.
- **US4 (Colors)**: Foundational models (T002, T003). Independent from US1-US3.
- **US5 (YAML Drop & Icon)**: Independent from US1-US4.
- **US6 (Secrets Import/Export)**: Foundational models (T003). Independent from US1-US5.

### Parallel Opportunities

- **Setup tasks**: T001 and T002 can run in parallel.
- **Foundational tasks**: T005 and T006 can run in parallel with T004.
- **User Story tests**: T007, T008, T012, T015, T018, T022, T023, T026 can all run in parallel across independent test files.
- **User Stories in parallel**: US1, US2, US3, US4, US5, US6 can be implemented in parallel once Foundational phase is complete.

---

## Parallel Example: User Story 1

```bash
# Launch test and helper tasks for User Story 1 together:
Task: "Unit test for multi-tier variable inheritance, overrides, and cycle detection in ui-shared/src/commonTest/kotlin/io/github/makeforge/clapi/ui/shared/state/EnvironmentInheritanceTest.kt"
Task: "Desktop Compose UI test for extends dropdown and override badge in ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/EnvironmentEditorUiTest.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T002).
2. Complete Phase 2: Foundational (T003-T006) - **CRITICAL**.
3. Complete Phase 3: User Story 1 (T007-T011).
4. **STOP and VALIDATE**: Test User Story 1 independently with unit & UI tests.

### Incremental Delivery

1. Complete Setup + Foundational → Core models and inheritance resolution verified.
2. Complete US1 (Inheritance & Overrides) → MVP delivered.
3. Complete US2 (Immediate Create & Sidebar Expansion) → Navigation friction removed.
4. Complete US3 (Quick-Add Top Rows) → Fast editing workflow enabled.
5. Complete US4 (Environment Colors) → Visual distinction across the app.
6. Complete US5 (YAML DnD & Icon Consistency) → Ingestion reliability restored.
7. Complete US6 (External Secrets Import/Export) → Secure sharing without secret leaks.
8. Complete Phase 9 (QA Tests & Verification) → Full confidence across all modules.
