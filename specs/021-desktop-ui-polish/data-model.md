# Data Model & Architectural Concepts: Desktop UI Polish

**Feature**: `021-desktop-ui-polish`  
**Date**: 2026-11-14  
**Status**: Completed

---

## 1. Architectural Entities & Concepts

### Entity 1: Dialog Dismissal Policy & Keyboard Scope
Represents the keyboard interception and dismissal behavior attached to a modal dialog's root container.

- **Attributes**:
  - `dialogId`: Unique identifier or enum value of the dialog (`AppDialog` / `AppAlert`).
  - `hasDirtyState`: Boolean indicating whether the dialog contains unsaved changes.
  - `isBlockingOperation`: Boolean indicating if a non-cancellable background operation is currently running.
  - `onDismiss`: Callback invoked to close the dialog when no dirty changes exist.
  - `onConfirmDiscard`: Callback invoked to show a dirty-close confirmation if `hasDirtyState` is true.
- **State Transitions**:
  - `OPEN (Clean)` --[Press Escape]--> `DISMISSED`
  - `OPEN (Dirty)` --[Press Escape]--> `DIRTY_CONFIRM_PROMPT`
  - `DIRTY_CONFIRM_PROMPT` --[Confirm Discard]--> `DISMISSED`
  - `DIRTY_CONFIRM_PROMPT` --[Cancel]--> `OPEN (Dirty)`
  - `BLOCKING_OPERATION` --[Press Escape]--> (Ignored or shows cancel prompt)

---

### Entity 2: Desktop Cursor Affordance
Defines the pointer cursor styling applied to interactive desktop UI elements.

- **Attributes**:
  - `targetElement`: The interactive composable surface (row, chip, card, toggle, or custom button).
  - `isEnabled`: Boolean indicating whether the action is currently enabled.
  - `hoverIcon`: `PointerIcon` displayed on mouse hover (`PointerIcon.Hand` when enabled, `PointerIcon.Default` when disabled).
- **Validation Rules**:
  - Elements using `Modifier.clickable` or `Modifier.toggleable` that are not native `Button` or `IconButton` controls MUST specify `pointerHoverIcon(PointerIcon.Hand)`.
  - Disabled controls MUST NOT display a pointing hand cursor.

---

### Entity 3: Stable Virtualized List Key
A composite or domain identifier ensuring stable row reconciliation in `LazyColumn` and `LazyRow`.

- **Attributes**:
  - `containerType`: Type of list (`ConsoleLogs`, `TestResults`, `Headers`, `Cookies`, `Secrets`, `Variables`).
  - `domainIdentifier`: Primary identity from the domain model (e.g., secret ID, header name, timestamp).
  - `itemIndex`: Secondary disambiguator (index within the current list) used when duplicate values or rapid succession entries occur.
  - `keyString`: Formatted string `"${domainIdentifier}_${itemIndex}"` guaranteed to be non-blank and unique within the list scope.
- **Validation Rules**:
  - No two rendered rows within the same lazy list scope may produce identical key values.
  - Keys must be deterministic across recompositions of unchanged items.

---

### Entity 4: Semantic Theme Token
A color token resolving dynamically from the active theme palette (`Light` or `Dark`).

- **Attributes**:
  - `name`: Semantic token identifier (e.g., `Background`, `Surface`, `OnSurface`, `Primary`, `Warning`, `Error`, `HoverOverlay`).
  - `lightColor`: Hex color definition under light theme.
  - `darkColor`: Hex color definition under dark theme.
  - `contrastRatio`: Meets WCAG AA contrast (≥ 4.5:1 for normal text, ≥ 3:1 for large text / graphical objects).
- **Validation Rules**:
  - No UI presentation code may hardcode hex literals (`Color(0x...)`) or static colors (`Color.White`).
  - All color queries must resolve via `ClapiColors.<Token>` within a `@Composable` context.

---

### Entity 5: Decomposed Subpackage Layout
The structural organization of refactored monolithic files.

```text
ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/
├── dialogs/
│   ├── env/
│   │   ├── EnvironmentEditDialog.kt     # Shell & Coordinator (< 800 lines)
│   │   ├── EnvVariableTable.kt          # Variable table (< 400 lines)
│   │   ├── EnvKeePassXcSecretRow.kt     # KeePassXC row (< 400 lines)
│   │   ├── EnvKeeperSecretRow.kt        # Keeper row (< 400 lines)
│   │   ├── EnvAwsSecretRow.kt           # AWS row (< 300 lines)
│   │   ├── EnvAzureSecretRow.kt         # Azure row (< 300 lines)
│   │   └── EnvGcpSecretRow.kt           # GCP row (< 300 lines)
│   └── settings/
│       ├── SettingsDialog.kt            # Coordinator (< 600 lines)
│       ├── GeneralSettingsPane.kt       # General settings (< 300 lines)
│       ├── ThemeSettingsPane.kt         # Theme settings (< 300 lines)
│       ├── VaultSettingsPane.kt         # Vault settings (< 300 lines)
│       ├── SecretsSettingsPane.kt       # Secrets settings (< 300 lines)
│       ├── DataSettingsPane.kt          # Data settings (< 300 lines)
│       └── SettingControls.kt           # Shared controls (< 300 lines)
├── mcp/
│   ├── McpPanel.kt                      # Main layout (< 500 lines)
│   ├── McpConnectionBar.kt              # Connection status (< 300 lines)
│   ├── McpToolsSection.kt               # Tools view (< 300 lines)
│   ├── McpResourcesSection.kt           # Resources view (< 300 lines)
│   ├── McpPromptsSection.kt             # Prompts view (< 300 lines)
│   └── McpLogView.kt                    # Server logs (< 300 lines)
└── sidebar/
    ├── Sidebar.kt                       # Shell (< 400 lines)
    ├── SidebarCollectionsSection.kt     # Collections tree (< 400 lines)
    ├── SidebarEnvironmentsSection.kt    # Environments tree (< 300 lines)
    ├── SidebarHistorySection.kt         # History list (< 300 lines)
    └── SidebarFooter.kt                 # Status & footer (< 200 lines)
```
