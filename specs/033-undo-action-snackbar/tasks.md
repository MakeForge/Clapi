# Tasks: Undoable Action Snackbar

**Input**: Design documents from `/specs/033-undo-action-snackbar/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/snackbar-api.md`, `quickstart.md`

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`[US1]`..`[US5]`)
- Exact file paths are included in every task description.

---

## Phase 1: Setup (Shared Localization & String Resources)

**Purpose**: Prepare localized string resources used across the snackbar UI and General Global Settings.

- [X] T001 [P] Add snackbar and auto-dismiss setting translation keys (`undo`, `snackbar_auto_dismiss_seconds`, `settings_snackbar_auto_dismiss_desc`, `snackbar_deleted_item`, `snackbar_deleted_items`, `snackbar_moved_item`, `snackbar_moved_items`, `snackbar_renamed_item`, `snackbar_undo_failed`) to `ui-shared/src/commonMain/resources/i18n/en.json`
- [X] T002 [P] Add matching localized translations for the new snackbar and auto-dismiss keys in `ui-shared/src/commonMain/resources/i18n/de.json`, `ui-shared/src/commonMain/resources/i18n/es.json`, and `ui-shared/src/commonMain/resources/i18n/fr.json`
- [X] T003 Add typed `Strings` accessors for the snackbar and auto-dismiss setting keys in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/i18n/Strings.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core centralized snackbar controller, bottom-right host composable, settings field, and reusable workspace undo helpers that ALL user stories depend on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T004 Create `ActionSnackbar`, `ActionSnackbarController`, and constants (`DEFAULT_SNACKBAR_AUTO_DISMISS_SEC = 5`, `MIN_SNACKBAR_AUTO_DISMISS_SEC = 1`, `MAX_SNACKBAR_AUTO_DISMISS_SEC = 60`; non-blank `message` validation; monotonic `id: Long`; single active `current: ActionSnackbar?` slot; at-most-once `triggerAction(id)` with `runCatching` failure fallback; stale-id-safe `dismiss(id)`) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/ActionSnackbarState.kt`
- [X] T005 [P] Add `snackbarAutoDismissSec` (`Int`, default `5`, range `1..60`) to `AppSettings` and copy it in `withNetworkOverrides()` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/AppSettings.kt`
- [X] T006 Expose `val snackbar = ActionSnackbarController()` on `AppState` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/AppState.kt`
- [X] T007 [P] Create `ActionSnackbarHost` composable anchored at `Alignment.BottomEnd` with `LaunchedEffect(active?.id, effectiveDurationMs)` auto-dismiss timer, single-slot rendering (no overlapping crossfade), `TextOverflow.Ellipsis` (`maxLines = 2`) truncation, Undo button (`testTag("action-snackbar-undo")`), dismiss button (`testTag("action-snackbar-dismiss")`), and container/message test tags (`action-snackbar`, `action-snackbar-message`) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ActionSnackbarHost.kt`
- [X] T008 Wrap `MainScreen` root in a `Box` mounting `ActionSnackbarHost(controller = state.snackbar, autoDismissSec = state.settings.snackbarAutoDismissSec)` at `Alignment.BottomEnd` and include `snackbarAutoDismissSec` in the settings `snapshotFlow` fingerprint in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/MainScreen.kt`
- [X] T009 [P] Add tree placement lookup (`findNodePlacement`, `captureNodePlacements`) and re-insertion (`reinsertNodeAt`, `restoreNodePlacements`) helpers in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/sidebar/SidebarCollectionHelpers.kt`
- [X] T010 Create `UndoableWorkspaceActions.kt` with `NodePlacement`, `DeletedNodeSnapshot`, `DeletedEnvironmentSnapshot`, `DeletedHistorySnapshot`, and centralized undo helpers for delete, move (with pre/post placement no-op detection), and rename (with identical-name no-op detection) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/UndoableWorkspaceActions.kt`

**Checkpoint**: Foundation ready — user story implementation can now proceed.

---

## Phase 3: User Story 1 - Undo Item Deletion via Bottom-Right Snackbar (Priority: P1) 🎯 MVP

**Goal**: Deleting any request, folder (with nested contents), collection, environment, or history item shows the bottom-right snackbar with an **Undo** button that restores the deleted item(s) intact.

**Independent Test**: Delete a request, a non-empty folder, an environment, and a history entry; verify the bottom-right snackbar appears with an Undo button, click Undo before timeout, and confirm full restoration (including nested folder contents and original position) and immediate snackbar dismissal.

- [X] T011 [US1] Wire undoable delete snackbars for single request, multi-selected requests, single folder (preserving nested subtree), multi-selected folders, and root collection deletion in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/sidebar/SidebarCollectionsSection.kt`
- [X] T012 [P] [US1] Wire undoable environment deletion (restoring `EnvState`, original index, and `selectedEnvIndex`) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/sidebar/SidebarEnvironmentsSection.kt`
- [X] T013 [P] [US1] Wire undoable history item removal (restoring `HistoryItem` at its original index) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/sidebar/SidebarHistoryRow.kt`

**Checkpoint**: User Story 1 (MVP) is fully functional and independently testable.

---

## Phase 4: User Story 2 - Undo Item Move and Rename Actions (Priority: P1)

**Goal**: Moving or renaming any request, folder, collection, or environment shows the bottom-right undoable snackbar and reverts cleanly on Undo while suppressing no-op moves and renames.

**Independent Test**: Move a request or folder (via Move Up/Down or drag-and-drop) and click Undo to confirm original parent and position restoration; rename a request, folder, collection, or environment (inline, dialog, or tab bar) and click Undo to confirm the previous name is restored; verify no-op moves/renames do not show a snackbar.

- [X] T014 [US2] Wire undoable move snackbars (with pre/post placement no-op check) for Move Up/Down, request drag-and-drop (`onDragEnd`), and folder/collection drag-and-drop (`onCollectionDragEnd`) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/sidebar/SidebarCollectionsSection.kt`
- [X] T015 [P] [US2] Wire undoable rename snackbars (skipping no-op renames where `actualNewName == oldName`) for sidebar inline rename and rename dialogs (request, folder, collection, environment) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/sidebar/Sidebar.kt`
- [X] T016 [P] [US2] Wire undoable rename snackbar (skipping no-op renames) for tab bar rename (`onRenameTab` for collection, folder, and request tabs) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/MainScreen.kt`

**Checkpoint**: All required actions (delete, move, rename) are undoable via the bottom-right snackbar.

---

## Phase 5: User Story 3 - Single Active Snackbar Replacement (Priority: P2)

**Goal**: Verify and harden the strict single-snackbar replacement policy, timer reset, once-only undo execution, and error fallback across unit and Desktop UI tests.

**Independent Test**: Trigger two or more eligible actions within 2 seconds and verify that the old snackbar is immediately dismissed with zero overlap, the timer resets to the full configured duration, and rapid Undo clicks execute at most once.

- [X] T017 [P] [US3] Add unit tests for `ActionSnackbarController` (single-snackbar immediate replacement, monotonic IDs, once-only undo execution, stale-id dismiss safety, undo failure fallback) in `ui-shared/src/commonTest/kotlin/io/github/makeforge/clapi/ui/shared/state/ActionSnackbarControllerTest.kt`
- [X] T018 [P] [US3] Add unit tests for `UndoableWorkspaceActions` (single/multi request & folder delete restore with full subtree, move restore, rename restore, and no-op move/rename suppression) in `ui-shared/src/commonTest/kotlin/io/github/makeforge/clapi/ui/shared/state/UndoableWorkspaceActionsTest.kt`
- [X] T019 [US3] Add Compose Desktop UI tests verifying bottom-right snackbar display, Undo click restoration, manual dismiss, auto-dismiss timeout, and immediate single-snackbar replacement on back-to-back actions in `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/ActionSnackbarUiTest.kt`

**Checkpoint**: Single-snackbar replacement and undo mechanics are verified by automated unit and UI tests.

---

## Phase 6: User Story 4 - Configure Auto-Dismiss Duration in General Global Settings (Priority: P2)

**Goal**: Users can configure the snackbar auto-dismiss duration (`1..60` seconds, default `5`) in **Global Settings → General**, persisted across restarts and applied immediately.

**Independent Test**: Open Global Settings → General, confirm default value `5`, reject invalid values (`0`, `-1`, `999`), change to `10`, verify subsequent snackbars use `10s`, and confirm persistence via `SettingsRepository` round-trip tests.

- [X] T020 [P] [US4] Persist and load `settings.snackbarAutoDismissSec` (default `5`, clamped to `1..60`) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/persistence/SettingsRepository.kt`
- [X] T021 [P] [US4] Enhance `SettingNumberField` with optional `description: String = ""`, `minValue: Int? = null`, `maxValue: Int? = null`, and `tag: String = ""` (showing red error border and blocking commits outside `[minValue, maxValue]`) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/dialogs/settings/SettingControls.kt`
- [X] T022 [US4] Add **Snackbar Auto-Dismiss Duration** (`minValue = 1`, `maxValue = 60`, `tag = "snackbar-auto-dismiss-input"`) to `GeneralSettings` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/dialogs/settings/GeneralSettingsPane.kt`
- [X] T023 [P] [US4] Add unit tests for `SettingsRepository` snackbar auto-dismiss default (`5`), save/load persistence, and clamping (`<1` → `1`, `>60` → `60`) in `ui-shared/src/commonTest/kotlin/io/github/makeforge/clapi/ui/shared/persistence/SettingsRepositorySnackbarTest.kt`
- [X] T024 [P] [US4] Extend General Global Settings UI tests to verify default `5`, valid duration update, and invalid/out-of-range rejection in `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/SettingsDialogUiTest.kt`

**Checkpoint**: Auto-dismiss duration is configurable in General Global Settings, persisted, validated, and tested.

---

## Phase 7: User Story 5 - Extensible Action Notification Capability (Priority: P3)

**Goal**: Ensure the centralized snackbar mechanism cleanly supports both undoable actions (`showUndoable`) and future informational/non-undoable actions (`show` without an Undo button) with zero duplicated display or timer logic.

**Independent Test**: Verify via unit and UI tests that calling `state.snackbar.show(message)` without an action renders a message-only bottom-right snackbar (no Undo button) that still follows auto-dismiss and single-snackbar replacement rules.

- [X] T025 [US5] Add test coverage verifying informational (non-undoable) snackbars without an Undo button and custom duration overrides through `ActionSnackbarController` and `ActionSnackbarHost` in `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/ActionSnackbarUiTest.kt`

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, full verification, and changelog/versioning compliance.

- [X] T026 [P] Document the new **Snackbar Auto-Dismiss Duration** setting (default 5 seconds, valid range 1–60 seconds) under General settings in `docs/settings/settings.md`
- [X] T027 Run full automated test verification (`rtk gradlew :ui-shared:jvmTest :ui-desktop:desktopTest`) and confirm all quickstart checks pass
- [X] T028 Update `CHANGELOG.md` (and `gradle.properties` `appVersion` if starting a new unreleased SemVer version) per the Changelog & Versioning Policy in `AGENTS.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — can start immediately.
- **Phase 2 (Foundational)**: Depends on Phase 1 (for i18n string keys) — BLOCKS all user stories.
- **Phase 3 (US1 - Delete Undo)** & **Phase 4 (US2 - Move/Rename Undo)**: Depend on Phase 2; `T014` follows `T011` since both touch `SidebarCollectionsSection.kt`, while `T012`, `T013`, `T015`, `T016` can run in parallel across distinct files.
- **Phase 5 (US3 - Single Replacement Verification)**: Depends on Phases 2–4.
- **Phase 6 (US4 - General Settings)**: Depends on Phase 2 (`T005`, `T008`); can run in parallel with Phases 3–5.
- **Phase 7 (US5 - Extensibility Verification)**: Depends on Phase 5 (`T019`).
- **Phase 8 (Polish)**: Depends on all prior phases (`T026` can run in parallel with tests; `T027` runs after all code/test edits; `T028` finalizes `CHANGELOG.md`).

### Parallel Opportunities

- Phase 1: `T001` and `T002` in parallel.
- Phase 2: `T005`, `T007`, `T009` in parallel alongside `T004`.
- Phase 3: `T011`, `T012`, `T013` in parallel (separate sidebar files).
- Phase 4: `T014`, `T015`, `T016` in parallel (separate files, once `T011` finishes).
- Phase 5: `T017` and `T018` in parallel.
- Phase 6: `T020`, `T021` in parallel, then `T022`, `T023`, `T024` in parallel.

### Parallel Example: User Story 1

```text
T011 [US1] Wire undoable delete in SidebarCollectionsSection.kt
T012 [P] [US1] Wire undoable delete in SidebarEnvironmentsSection.kt
T013 [P] [US1] Wire undoable delete in SidebarHistoryRow.kt
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (Setup) + Phase 2 (Foundational).
2. Complete Phase 3 (User Story 1 — Delete Undo).
3. Validate delete & undo end-to-end as the MVP increment.

### Incremental Delivery

1. Setup + Foundational → Centralized bottom-right snackbar infrastructure ready.
2. US1 (Delete Undo) → High-impact data-loss recovery live.
3. US2 (Move & Rename Undo) → Full initial action coverage complete.
4. US3 (Single Replacement Tests) + US4 (General Settings Auto-Dismiss) → Configurable timeout & hardened replacement semantics.
5. US5 (Extensibility Tests) + Polish (`docs/settings/settings.md`, full test run, `CHANGELOG.md`).
