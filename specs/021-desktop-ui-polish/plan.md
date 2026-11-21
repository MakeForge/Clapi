# Implementation Plan: Desktop UI Polish & Modularization

**Branch**: `021-desktop-ui-polish` | **Date**: 2026-11-14 | **Spec**: [spec.md](file:///home/user/IdeaProjects/Clapi/specs/021-desktop-ui-polish/spec.md)

**Input**: Feature specification from `/specs/021-desktop-ui-polish/spec.md`

---

## Summary

Resolve all Medium and Low severity suggestions from [scratch/composables_evaluation.md](file:///home/user/IdeaProjects/Clapi/scratch/composables_evaluation.md) to complete desktop UI ergonomics, theming, localization, and architectural modularity:
1. **Keyboard Ergonomics**: Intercept `Key.Escape` on all modal dialogs for clean dismissal, respecting dirty edit states.
2. **Pointer Ergonomics**: Apply `Modifier.pointerHoverIcon(PointerIcon.Hand)` across all custom interactive surfaces (rows, cards, chips, toggles, dropdown anchors).
3. **List State Stability**: Supply deterministic, stable item keys to all `items` and `itemsIndexed` calls in `LazyColumn` and `LazyRow`.
4. **Theming & Localization**: Replace hardcoded hex colors and static white text with `ClapiColors` semantic tokens; extract user-visible strings into `Strings.kt`.
5. **Component Decomposition**: Decompose monolithic files exceeding 1,000 lines (`EnvironmentEditDialog.kt`, `SettingsDialog.kt`, `McpPanel.kt`, `Sidebar.kt`, etc.) into dedicated subpackages and focused single-responsibility composables. Deduplicate tooltip icon buttons via `IconButtonWithTooltip`.

---

## Technical Context

**Language/Version**: Kotlin Multiplatform 2.1.20 / Java 17+  
**Primary Dependencies**: Compose Multiplatform 1.7.3, Material 3, Coroutines, Serialization  
**Storage**: N/A (UI layout, state presentation, and desktop ergonomics)  
**Testing**: Kotlin Test, JUnit 4, Compose Multiplatform UI Test  
**Target Platform**: Desktop JVM (Linux, macOS, Windows)  
**Project Type**: Desktop UI Application (`ui-shared` and `ui-desktop`)  
**Performance Goals**: 60 fps smooth scrolling in virtualized lists; instant (< 50ms) dialog dismissal; zero unnecessary recompositions during log streaming  
**Constraints**: Zero regression on existing public composables; no UI source file exceeding 1,000 lines; 100% theme adaptability without hardcoded color literals  
**Scale/Scope**: ~30 dialog and workspace component files touched; 4 new subpackages created (`dialogs/env`, `dialogs/settings`, `mcp`, `sidebar`)  

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Rule I (Library-First & Modularity)**: PASS. All refactored components remain modular, reusable, and cleanly organized into domain-specific subpackages.
- **Rule II (Ergonomics & Consistency)**: PASS. Standardizes `Escape` key dismissal and `PointerIcon.Hand` across all interactive surfaces.
- **Rule III (Test-First & Regression Safety)**: PASS. Validated against automated unit tests and desktop test suites before and after refactoring.
- **Rule IV (Resource & Theming Hygiene)**: PASS. Eliminates hardcoded strings and color literals, enforcing `Strings.kt` and `ClapiColors`.
- **Rule V (Sizing Limits)**: PASS. Enforces < 1,000 lines per file and < 100 lines per composable function.

---

## Project Structure

### Documentation (this feature)

```text
specs/021-desktop-ui-polish/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output: decisions, rationale, alternatives
├── data-model.md        # Phase 1 output: architectural entities & state flows
├── quickstart.md        # Phase 1 output: runnable validation scenarios
├── contracts/           # Phase 1 output: formal interface contracts
│   ├── dialog-escape-dismissal-contract.md
│   ├── pointer-affordance-contract.md
│   ├── lazy-list-key-contract.md
│   └── theme-and-string-contract.md
└── checklists/
    └── requirements.md  # Spec quality checklist
```

### Source Code (repository root)

```text
ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/
├── theme/
│   └── ClapiTheme.kt                       # Semantic tokens (StatusWarning, etc.)
├── i18n/
│   └── Strings.kt                          # Centralized localization keys
└── components/
    ├── SharedTooltip.kt                    # Standardized IconButtonWithTooltip
    ├── dialogs/
    │   ├── env/
    │   │   ├── EnvironmentEditDialog.kt    # Coordinator & shell (< 800 lines)
    │   │   ├── EnvVariableTable.kt         # Variable table editor
    │   │   ├── EnvKeePassXcSecretRow.kt    # KeePassXC secret row editor
    │   │   ├── EnvKeeperSecretRow.kt       # Keeper secret row editor
    │   │   ├── EnvAwsSecretRow.kt          # AWS Secrets Manager row editor
    │   │   ├── EnvAzureSecretRow.kt        # Azure Key Vault row editor
    │   │   └── EnvGcpSecretRow.kt          # GCP Secret Manager row editor
    │   └── settings/
    │       ├── SettingsDialog.kt           # Coordinator & tabs (< 600 lines)
    │       ├── GeneralSettingsPane.kt      # General settings
    │       ├── ThemeSettingsPane.kt        # Theme settings
    │       ├── VaultSettingsPane.kt        # Vault settings
    │       ├── SecretsSettingsPane.kt      # Secrets settings
    │       ├── DataSettingsPane.kt         # Data settings
    │       └── SettingControls.kt          # SettingToggle, SettingAction, SettingRow
    ├── mcp/
    │   ├── McpPanel.kt                     # Coordinator & tabs (< 500 lines)
    │   ├── McpConnectionBar.kt             # Server status and connection bar
    │   ├── McpToolsSection.kt              # Tools list and invocation
    │   ├── McpResourcesSection.kt          # Resources list
    │   ├── McpPromptsSection.kt            # Prompts list
    │   └── McpLogView.kt                   # Server logs viewer
    ├── sidebar/
    │   ├── Sidebar.kt                      # Sidebar coordinator (< 400 lines)
    │   ├── SidebarCollectionsSection.kt    # Collections tree section
    │   ├── SidebarEnvironmentsSection.kt   # Environments section
    │   ├── SidebarHistorySection.kt        # History requests section
    │   └── SidebarFooter.kt                # Footer controls & status
    ├── BottomPanel.kt                      # Keyed logs & pointer hover icons
    ├── ResponseViewer.kt                   # Keyed headers/cookies & pointer hover
    ├── RequestBar.kt                       # Pointer hover icons & theme tokens
    └── KeyValueEditor.kt                   # Pointer hover icons
```

**Structure Decision**: Decomposed monolithic files into cohesive subpackages (`dialogs/env`, `dialogs/settings`, `mcp`, `sidebar`). Preserved public composable facades in original packages or provided clean forwards to avoid breaking existing callers.

---

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Creating dedicated subpackages | Monolithic files exceed 1,500 to 3,000+ lines each | Flat single-file packaging causes merge conflicts and high cognitive load |
