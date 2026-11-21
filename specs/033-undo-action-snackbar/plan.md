# Implementation Plan: Undoable Action Snackbar

**Branch**: `033-undo-action-snackbar` | **Date**: 2026-11-21 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/033-undo-action-snackbar/spec.md` + user note to document the new setting in `docs/settings/settings.md`.

## Summary

Add a centralized, extensible, bottom-right undoable action snackbar to Clapi that appears after **delete** (requests, folders with nested subtrees, collections, environments, history items), **move** (drag-and-drop and move up/down for requests, folders, and collections), and **rename** (requests, folders, collections, environments) actions. The snackbar enforces a strict single-visible-snackbar policy (immediately dismissing any previous snackbar and resetting the timer), auto-dismisses after a user-configurable duration (default **5 seconds**, range **1–60 seconds**) configurable in **Global Settings → General**, persists that preference via `SettingsRepository`, and documents it in `docs/settings/settings.md`.

## Technical Context

**Language/Version**: Kotlin 2.4.10 (Kotlin Multiplatform)

**Primary Dependencies**: Compose Multiplatform 1.12.0 (Material 3), Kotlinx Coroutines 1.10.2, Kotlinx Serialization 1.8.1

**Storage**: `PlatformStorage` via `SettingsRepository` (`settings.snackbarAutoDismissSec`), `WorkspaceRepository` for workspace persistence on delete/move/rename/undo

**Testing**: `kotlin-test` + `kotlinx-coroutines-test` (`:ui-shared:jvmTest`), Compose Desktop UI tests (`ui-test-junit4-desktop` in `:ui-desktop:desktopTest`)

**Target Platform**: Desktop JVM (Linux, macOS, Windows) via `ui-desktop`, with shared state and UI in `ui-shared`

**Project Type**: Multi-module Kotlin Multiplatform Desktop Application

**Performance Goals**: Snackbar visible within <200 ms of action completion; zero frame drops on rapid back-to-back actions; O(tree size) minimal placement snapshots without full-workspace deep cloning

**Constraints**: Never display 2 snackbars simultaneously (0% overlap); at-most-once undo execution; bounded auto-dismiss duration (1–60 seconds); localized across all 4 supported locales (`en`, `de`, `es`, `fr`)

**Scale/Scope**: ~10–15 touched/added files across `ui-shared`, `ui-desktop` tests, `docs/settings/settings.md`, and `CHANGELOG.md`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` is currently the uncustomized template (no project-specific constitution rules ratified). Applying repository governance rules from `AGENTS.md` and `.github/copilot-instructions.md`:
- **Gradle via `rtk`**: All test/build commands will use `rtk gradlew ...`.
- **Multiplatform separation**: All snackbar state, undo helpers, UI host, settings model, and i18n live in `ui-shared`; Desktop UI tests live in `ui-desktop`.
- **Documentation**: `docs/settings/settings.md` will be updated to document the new General setting.
- **Changelog & Versioning**: `CHANGELOG.md` (and `gradle.properties` if bumping version) will be updated upon implementation completion.

**Gate Status**: PASS (both pre-Phase 0 and post-Phase 1).

## Project Structure

### Documentation (this feature)

```text
specs/033-undo-action-snackbar/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── snackbar-api.md  # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit-tasks - not created here)
```

### Source Code (repository root)

```text
ui-shared/src/commonMain/
├── kotlin/io/github/makeforge/clapi/ui/shared/
│   ├── MainScreen.kt                                    # Root Box + ActionSnackbarHost overlay; settings snapshotFlow; tab rename undo
│   ├── state/
│   │   ├── ActionSnackbarState.kt                       # [NEW] ActionSnackbar, ActionSnackbarController, constants
│   │   ├── UndoableWorkspaceActions.kt                  # [NEW] Placement snapshots & undo helpers for delete/move/rename
│   │   ├── AppSettings.kt                               # Add snackbarAutoDismissSec + copy in withNetworkOverrides()
│   │   └── AppState.kt                                  # Expose val snackbar = ActionSnackbarController()
│   ├── persistence/
│   │   └── SettingsRepository.kt                        # Load/save & clamp settings.snackbarAutoDismissSec
│   ├── components/
│   │   ├── ActionSnackbarHost.kt                        # [NEW] Bottom-right snackbar UI, auto-dismiss LaunchedEffect, Undo & close
│   │   ├── dialogs/settings/
│   │   │   ├── GeneralSettingsPane.kt                   # Add Snackbar Auto-Dismiss Duration field
│   │   │   └── SettingControls.kt                       # Enhance SettingNumberField with optional description, min/max, testTag
│   │   └── sidebar/
│   │       ├── Sidebar.kt                               # Wire undoable rename (inline + dialogs for request/folder/collection/env)
│   │       ├── SidebarCollectionsSection.kt             # Wire undoable delete & move (single/multi, drag-and-drop, move up/down)
│   │       ├── SidebarEnvironmentsSection.kt            # Wire undoable environment delete
│   │       ├── SidebarHistoryRow.kt                     # Wire undoable history item removal
│   │       └── SidebarCollectionHelpers.kt              # Tree location lookup & re-insertion helpers
│   └── i18n/
│       └── Strings.kt                                   # Add snackbar & setting string accessors
└── resources/i18n/
    ├── en.json                                          # Add new translation keys
    ├── de.json                                          # Add new translation keys
    ├── es.json                                          # Add new translation keys
    └── fr.json                                          # Add new translation keys

ui-shared/src/commonTest/kotlin/io/github/makeforge/clapi/ui/shared/
├── state/
│   ├── ActionSnackbarControllerTest.kt                  # [NEW] Single-snackbar replacement, once-only undo, stale dismiss, error fallback
│   └── UndoableWorkspaceActionsTest.kt                  # [NEW] Delete/move/rename undo & no-op detection unit tests
└── persistence/
    └── SettingsRepositorySnackbarTest.kt                # [NEW] Default, persistence, clamping (1..60)

ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/
├── ActionSnackbarUiTest.kt                              # [NEW] Bottom-right snackbar UI, Undo click, replacement, auto-dismiss
└── SettingsDialogUiTest.kt                              # Extend with General settings snackbar auto-dismiss field tests

docs/settings/
└── settings.md                                          # Document Snackbar Auto-Dismiss Duration under General settings
```

**Structure Decision**: All production logic and composables reside in `ui-shared` (consistent with every other workspace feature in Clapi), with pure-Kotlin state/persistence unit tests in `ui-shared/src/commonTest` and Compose Desktop UI tests in `ui-desktop/src/desktopTest`. Documentation is updated in `docs/settings/settings.md`.

## Complexity Tracking

No constitution violations; no extra complexity justifications required.
