# Implementation Plan: Environment Enhancements

**Branch**: `026-environment-enhancements` | **Date**: 2026-11-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/026-environment-enhancements/spec.md`

## Summary

This feature delivers significant usability and power enhancements to environment management in Clapi:
1. **Immediate Editing & Expanded Navigation**: Creating a new environment automatically expands the Environments sidebar section and immediately launches the Edit Environment dialog focused on the new environment.
2. **Environment Inheritance (`extends`) & Variable Overrides**: Supports `OpenCollection.Environment.extends` allowing an environment to inherit variables and configurations from a parent environment with cycle detection and child override precedence, clearly indicated in the UI.
3. **Color Customization (`color`)**: Allows assigning a color hex code to environments via a predefined 8-swatch palette and custom hex input, visually reflected across the sidebar, header toolbar, and dialogs.
4. **Quick-Add Top Rows**: Pinning an inline entry row directly to the top of the Variables and External Secrets tables in the Edit Environment dialog for frictionless data entry without scrolling.
5. **Icon Consistency & YAML Drag-and-Drop**: Unifies the Environment Import button icon to match Collection Import (`ic_download`) and implements reliable `.yaml`/`.yml`/`.json` file drag-and-drop into the import dropzone.
6. **Secure External Secrets Import/Export**: Ensures external secret configurations are fully exported and imported while strictly omitting sensitive plaintext secret values and credentials.
7. **Comprehensive Automated Testing**: Unit, Desktop Compose UI, and end-to-end QA tests validating the entire feature set.

## Technical Context

**Language/Version**: Kotlin 2.1 (Multiplatform, JVM 21 / 17 target)

**Primary Dependencies**:
- Compose Multiplatform 1.8.x (Desktop)
- Material 3 (`androidx.compose.material3`)
- `kaml` (`com.charleskorn.kaml:kaml`) for YAML serialization & parsing
- `kotlinx.serialization` (`kotlinx-serialization-json`) for JSON serialization & parsing
- `kotlinx.coroutines`
- AWT Drag & Drop (`java.awt.dnd.DropTarget`, `DataFlavor.javaFileListFlavor`)

**Storage**:
- Canonical OpenCollection format (`core-storage`)
- Workspace JSON persistence (`WorkspaceRepository`)

**Testing**:
- `kotlin.test`
- Desktop Compose UI testing (`createComposeRule` in `ui-desktop`)
- End-to-end multiplatform tests in `qa-tests`

**Target Platform**: Desktop (Linux, macOS, Windows)

**Project Type**: Desktop Multiplatform Application

**Performance Goals**:
- Variable resolution across inheritance chains under 1ms.
- Dialog launch under 50ms upon environment creation.
- Instantaneous (<16ms) reactivity when committing quick-add row entries or toggling color presets.

**Constraints**:
- OpenCollection specification compliance (`color`, `extends`, `externalSecrets`).
- Zero secret value leakage in export files.
- Safe cycle detection: infinite loops in inheritance chains must be prevented in the UI and handled safely during resolution.

**Scale/Scope**:
- Workspaces with arbitrarily many environments and variable counts (>100 variables).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

No constitutional violations exist:
1. **Modularity & Separation**: Core models (`core-model`) and storage serialization (`core-storage`) remain completely decoupled from presentation logic (`ui-shared`, `ui-desktop`).
2. **Data Safety & Privacy**: Secret variable values and provider credentials are strictly excluded from exported files; only configuration schemas and non-secret variables are serialized.
3. **Test-First Compliance**: Complete coverage including unit tests for inheritance and serialization, Desktop Compose UI tests for interaction flows, and QA tests for end-to-end verification.
4. **Resilience & Robustness**: Cycle detection algorithms prevent recursive stack overflows, and missing parent environments gracefully fall back to local variables without crashing.

## Project Structure

### Documentation (this feature)

```text
specs/026-environment-enhancements/
├── spec.md                     # Feature specification
├── plan.md                     # Implementation plan
├── research.md                 # Technical research & decisions (Phase 0)
├── data-model.md               # Data entities & state transitions (Phase 1)
├── quickstart.md               # Runnable validation guide (Phase 1)
├── contracts/                  # Interface contracts (Phase 1)
│   ├── environment-inheritance-contract.md
│   └── environment-import-export-contract.md
└── checklists/
    └── requirements.md         # Specification quality checklist
```

### Source Code (repository root)

```text
ui-shared/
├── src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/
│   ├── state/
│   │   ├── EnvironmentModels.kt                         # Observable color & extends; inheritance resolver
│   │   └── AppState.kt                                  # Active variable layers with inheritance resolution
│   ├── components/
│   │   ├── TopToolbar.kt                                # Render environment color swatch in picker
│   │   ├── ImportSpecificationDialog.kt                 # Desktop drag-and-drop file ingestion (.yaml/.yml/.json)
│   │   ├── dialogs/env/
│   │   │   ├── EnvironmentEditDialog.kt                 # Extends selector, color picker, top quick-add rows, override badge
│   │   │   └── EnvironmentDialogHelpers.kt              # Color palette presets and hex parsing utilities
│   │   └── sidebar/
│   │       ├── Sidebar.kt                               # Expand sidebar & open dialog immediately on creation
│   │       ├── SidebarEnvironmentsSection.kt            # Icon update: ic_input -> ic_download
│   │       └── SidebarEnvironmentRow.kt                 # Render environment color dot
│   └── persistence/
│       ├── ImportExportRepository.kt                    # Export/import color, extends, sanitized external secrets
│       └── OpenCollectionModelMapping.kt                # Model mapping verification
└── src/commonTest/kotlin/io/github/makeforge/clapi/ui/shared/
    ├── state/
    │   └── EnvironmentInheritanceTest.kt                # Unit tests for inheritance, overrides, cycles
    └── persistence/
        └── EnvironmentImportExportTest.kt               # Unit tests for secret sanitization & import/export

ui-desktop/
└── src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/
    ├── EnvironmentEditorUiTest.kt                       # UI tests for creation flow, extends, color, quick-add
    └── ImportSpecificationUiTest.kt                     # UI tests for YAML drag-and-drop & icon consistency

qa-tests/
└── src/desktopTest/kotlin/io/github/makeforge/clapi/qa/
    └── EnvironmentEnhancementsQaTest.kt                 # End-to-end integration QA tests
```

**Structure Decision**: Retains existing modular separation. Core data contracts reside in `core-model` and `core-storage`. State and Compose dialog implementations reside in `ui-shared`. Desktop UI interactions are tested in `ui-desktop` and multi-module flows in `qa-tests`.

## Complexity Tracking

> No constitutional violations or unwarranted complexity introduced.
