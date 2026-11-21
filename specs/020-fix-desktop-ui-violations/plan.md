# Implementation Plan: Fix Desktop UI High-Severity Violations

**Branch**: `020-fix-desktop-ui-violations` | **Date**: 2026-11-13 | **Spec**: [spec.md](file:///home/user/IdeaProjects/Clapi/specs/020-fix-desktop-ui-violations/spec.md)

**Input**: Feature specification from `specs/020-fix-desktop-ui-violations/spec.md`

---

## Summary

Resolve all 34 high-severity Desktop Compose violations identified in the audit (`scratch/composables_evaluation.md`):
1. **Redundant Hover Removal**: Remove redundant `.hoverable` calls on `IconButton` in `CodeEditor.kt` and `TopToolbar.kt`, migrating to the centralized `IconButtonWithTooltip` to prevent swallowed click events.
2. **Desktop Scrollbars**: Integrate desktop scrollbars (`PlatformLazyVerticalScrollbar` / `PlatformColumnVerticalScrollbar`) on all 16 scrollable panels and dialogs.
3. **Modifier Hygiene**: Add `modifier: Modifier = Modifier` as the first optional parameter applied to the root element across all 17 primary workspace panels and 14 modal dialogs.
4. **Parameter Ordering**: Standardize parameter order in `SplitPane.kt` and `MethodBadge.kt` so mandatory parameters precede optional layout modifiers.

---

## Technical Context

**Language/Version**: Kotlin 2.1+, Compose Multiplatform (CMP) 1.7+, JDK 17/21  
**Primary Dependencies**: JetBrains Compose Multiplatform (`foundation`, `material3`, `runtime`, `ui`)  
**Storage**: N/A (UI layout, event, and modifier standards)  
**Testing**: `./gradlew :ui-shared:check`, `./gradlew :ui-desktop:check`, `./gradlew desktopTest`  
**Target Platform**: Desktop (JVM) — Linux, macOS, Windows  
**Project Type**: Desktop UI shared module (`ui-shared`) and desktop launcher (`ui-desktop`)  
**Performance Goals**: 60 fps smooth scrolling and 0% click drops across toolbars, dialogs, and workspace panels  
**Constraints**: Zero behavioral regressions in domain state; maintain dark/light theme fidelity with `ClapiColors`  
**Scale/Scope**: 34 high-severity violations across 25 Kotlin files in `ui-shared`  

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **Principle I: Library/Component Hygiene**: Reusable UI components must expose clean, stateless APIs with clear unidirectional data flow (UDF) and modifier pass-through.
- [x] **Principle II: Desktop Ergonomics**: Desktop apps must support physical keyboard and mouse interaction (scrollbars, pointer hover icons, click reliability).
- [x] **Principle III: Testability**: Composable modifier hygiene ensures parent containers and test suites can supply test tags and spatial constraints.
- [x] **Principle IV: No Android Leaks**: Strictly multiplatform Compose code; zero Android dependencies.

---

## Project Structure

### Documentation (this feature)

```text
specs/020-fix-desktop-ui-violations/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output: technical decisions & alternatives
├── data-model.md        # Phase 1 output: UI structural models & interaction state
├── quickstart.md        # Phase 1 output: runnable validation scenarios
├── checklists/
│   └── requirements.md  # Spec quality checklist
├── contracts/
│   ├── composable-modifier-contract.md   # Modifier parameter standards
│   ├── desktop-scrollbar-contract.md     # Desktop scrollbar layout standards
│   └── icon-button-tooltip-contract.md   # Button click & tooltip standards
└── tasks.md             # Phase 2 output (/speckit-tasks command)
```

### Source Code (affected areas)

```text
ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/
├── MainScreen.kt                         # Shell composable modifier pass-through
├── components/
│   ├── AuthEditor.kt                    # Modifier + scrollbar
│   ├── BodyEditor.kt                    # Modifier + scrollbar
│   ├── BottomPanel.kt                   # Modifier + scrollbars on Console/Results
│   ├── CodeEditor.kt                    # Modifier + scrollbar + fix redundant hover
│   ├── CollectionEditor.kt              # Modifier + scrollbar
│   ├── ConfirmDeleteDialog.kt           # Modifier pass-through
│   ├── DirtyCloseDialog.kt              # Modifier pass-through
│   ├── DirtyMultiCloseDialog.kt         # Modifier pass-through
│   ├── EnvironmentEditDialog.kt         # Modifier pass-through
│   ├── ErrorMessageDialog.kt            # Modifier pass-through
│   ├── ExportCollectionsDialog.kt       # Modifier + scrollbar
│   ├── FolderEditor.kt                  # Modifier pass-through
│   ├── GitSyncDialog.kt                 # Modifier + scrollbar
│   ├── GlobalVariablesDialog.kt         # Modifier + scrollbar
│   ├── GraphQLPanel.kt                  # Modifier + scrollbar
│   ├── GraphQLRequestEditor.kt          # Modifier + scrollbar
│   ├── GrpcRequestEditor.kt             # Modifier + scrollbar
│   ├── HelpAboutDialog.kt               # Modifier + scrollbar
│   ├── ImportSpecificationDialog.kt     # Modifier + scrollbar
│   ├── MethodBadge.kt                   # Parameter ordering (modifier first optional)
│   ├── OperationProgressDialog.kt       # Modifier pass-through
│   ├── RealtimePanel.kt                 # Modifier + scrollbar
│   ├── RequestBar.kt                    # Modifier pass-through
│   ├── RequestEditor.kt                 # Modifier pass-through
│   ├── RequestTabsBar.kt                # Modifier + horizontal scrollbar
│   ├── ResponseViewer.kt                # Modifier + scrollbars on tabs
│   ├── SelectProtoFileDialog.kt         # Modifier pass-through
│   ├── SettingsDialog.kt                # Modifier + scrollbars
│   ├── SharedTooltip.kt                 # Centralized IconButtonWithTooltip
│   ├── SplitPane.kt                     # Parameter ordering (mandatory before modifier)
│   ├── TopToolbar.kt                    # Modifier + fix redundant hover
│   └── WebSocketRequestEditor.kt        # Modifier + scrollbars
└── platform/
    └── PlatformScrollbar.kt             # Scrollbar wrappers for desktop
```

---

## Complexity Tracking

No constitution violations or complex architectural additions are introduced. The plan strictly rectifies modifier parameter hygiene, component parameter ordering, and standard desktop scrollbars.
