# Technical Research: Desktop UI Polish & Modularization

**Feature**: `021-desktop-ui-polish`  
**Date**: 2026-11-14  
**Status**: Completed

---

## 1. Modal Dialog Escape Key Dismissal

### Decision
Implement `Key.Escape` dismissal across all desktop modal dialogs using container-level `Modifier.onKeyEvent` (or `onPreviewKeyEvent`), checking `event.type == KeyEventType.KeyDown && event.key == Key.Escape`. When a dialog has unsaved dirty state (e.g. modified environment variables or pending edits), route the `Escape` action to the dialog's discard confirmation prompt (`DirtyCloseDialog` or equivalent) rather than immediately dropping changes.

### Rationale
- Standard desktop platform convention (macOS, Windows, Linux) dictates that `Escape` closes the topmost active modal window.
- In Compose Multiplatform Desktop, `Dialog(onDismissRequest = ...)` intercepts clicks outside the dialog window frame, but keyboard `Escape` events inside the window hierarchy require an explicit key event handler on the root focusable container (`Box` or `Column`).
- Attaching `.onKeyEvent` to the outer container of each dialog ensures immediate responsiveness without needing low-level Swing AWT window hooks.

### Alternatives Considered
- **AWT Global Event Dispatcher (`KeyboardFocusManager`)**: Intercepts `KeyEvent` at the JVM window level. Rejected because it bypasses Compose UI state tree and can interfere with active desktop text inputs or shortcuts in background windows.
- **Requiring Esc to be handled only by Dialog onDismissRequest**: In Compose Desktop, `Dialog` on Linux/X11 does not consistently trigger `onDismissRequest` on `Key.Escape` when focus is held by an internal child component. Adding explicit `.onKeyEvent` provides 100% reliable dismissal.

---

## 2. Cursor Affordances & Pointer Feedback (`PointerIcon.Hand`)

### Decision
Apply `Modifier.pointerHoverIcon(PointerIcon.Hand)` to all interactive, clickable elements that are not native `Button` or `IconButton` controls (such as table rows, cards, custom tabs, chips, dropdown anchors, and token copy fields).

### Rationale
- Compose Multiplatform provides `Modifier.pointerHoverIcon(PointerIcon.Hand)` natively.
- Custom clickable components using `Modifier.clickable` or `Modifier.toggleable` default to the standard arrow pointer on desktop unless `pointerHoverIcon` is explicitly declared.
- Adding this modifier to `KeyValueRow`, `ConsoleRow`, `MethodDropdown`, `EnvironmentChip`, `ProtoFileRow`, `GlobalVariableRow`, `SettingToggle`, and `SettingAction` creates unambiguous visual affordances for mouse users.

### Alternatives Considered
- **Creating a custom `Modifier.handClickable()` extension**: Viable helper, but direct composition of `.pointerHoverIcon(PointerIcon.Hand).clickable(...)` keeps idiomatic Compose modifier semantics clear and explicit. We will provide a convenience modifier extension in `DesktopErgonomics.kt` or chain directly.

---

## 3. Stable Item Keys for Virtualized Lists

### Decision
Add explicit `key` parameters to all `items` and `itemsIndexed` invocations in `LazyColumn` and `LazyRow`:
- **Console Logs** (`BottomPanel.kt`): `key = { it.timestamp.toString() + "_" + it.message.hashCode() + "_$index" }`
- **Test Results** (`BottomPanel.kt`): `key = { it.id ?: (it.name + "_$index") }`
- **Environment Secrets** (`EnvironmentEditDialog.kt`): `key = { secret.id ?: "secret_$sIdx" }`
- **MCP Server Logs** (`McpPanel.kt`): `key = { "mcp_log_${logs[i].timestamp}_$i" }`
- **Response Headers** (`ResponseViewer.kt`): `key = { "${header.name}_${header.value}_$index" }`
- **Response Cookies** (`ResponseViewer.kt`): `key = { "${cookie.name}_${cookie.domain}_$index" }`
- **Variable Suggestions** (`VariableHighlight.kt`): `key = { "${name}_$index" }`

### Rationale
- Without keys, Compose uses item indices for list reconciliation. When items are prepended, sorted, or filtered, every single row is recomposed and local state (e.g. scroll offsets, text selections, or expanded sections) is lost or corrupted.
- Using composite keys with stable fallbacks prevents key collisions while guaranteeing that unchanged rows are skipped during recomposition.

### Alternatives Considered
- **Pure index keys (`key = { index }`)**: Defeats the entire purpose of keys, as index keys behave identically to unkeyed lists.
- **Object hash code only**: Hash collisions between identical strings or consecutive log lines can cause runtime exceptions in `LazyLayoutItemProvider`. Composite keys (`"${identity}_${index}"`) guarantee uniqueness.

---

## 4. Theme Token Normalization & Centralized Strings

### Decision
- **Colors**:
  - Add `Warning` (`Color(0xFFEAB308)`) and timing phase palette tokens to `ClapiColors` / `LocalAppColors` in `ClapiTheme.kt`.
  - Replace static `Color.White` with `ClapiColors.OnPrimary` or `ClapiColors.OnSurface` depending on background contrast.
  - Replace hardcoded hex colors across `AuthEditor.kt`, `GraphQLPanel.kt`, `GraphQLRequestEditor.kt`, `GrpcRequestEditor.kt`, `McpPanel.kt`, `RealtimePanel.kt`, `RequestBar.kt`, `ResponseViewer.kt`, and `WebSocketRequestEditor.kt` with their corresponding `ClapiColors` properties (`Method*`, `Status*`, `Surface*`, `Border`, `Primary`).
- **Strings**:
  - Add missing translation keys to `ui-shared/src/commonMain/resources/i18n/strings_*.json` (or `Strings.kt`).
  - Replace hardcoded English strings in dialogs and editors with `Strings.t("...")` or typed accessors in `Strings.kt`.

### Rationale
- Dark/light mode switching requires dynamic color resolution via `CompositionLocal` (`LocalAppColors.current`). Hardcoded hex codes or static white text cause contrast failures in light mode.
- Centralizing strings enables full localization across English, Spanish, French, and German.

---

## 5. Component Decomposition & Subpackaging Strategy

### Decision
Decompose monolithic files (>1,000 lines) and oversized composables (>100 lines) into dedicated subpackages:

1. **`EnvironmentEditDialog.kt` (3,153 lines)**:
   - Create subpackage `io.github.crustack.clapi.ui.shared.components.dialogs.env`:
     - `EnvironmentEditDialog.kt` (main dialog shell and coordinator)
     - `EnvVariableTable.kt` (plain variable editing rows and table layout)
     - `EnvKeePassXcSecretRow.kt` (KeePassXC secret row editor)
     - `EnvKeeperSecretRow.kt` (Keeper Security secret row editor)
     - `EnvAwsSecretRow.kt` (AWS Secrets Manager row editor)
     - `EnvAzureSecretRow.kt` (Azure Key Vault row editor)
     - `EnvGcpSecretRow.kt` (GCP Secret Manager row editor)

2. **`SettingsDialog.kt` (1,599 lines)**:
   - Create subpackage `io.github.crustack.clapi.ui.shared.components.dialogs.settings`:
     - `SettingsDialog.kt` (dialog coordinator and tab navigation)
     - `GeneralSettingsPane.kt`
     - `ThemeSettingsPane.kt`
     - `VaultSettingsPane.kt`
     - `SecretsSettingsPane.kt`
     - `DataSettingsPane.kt`
     - `SettingControls.kt` (`SettingToggle`, `SettingAction`, `SettingRow`)

3. **`McpPanel.kt` (1,658 lines)**:
   - Create subpackage `io.github.crustack.clapi.ui.shared.components.mcp`:
     - `McpPanel.kt` (panel layout and tabs)
     - `McpConnectionBar.kt`
     - `McpToolsSection.kt`
     - `McpResourcesSection.kt`
     - `McpPromptsSection.kt`
     - `McpLogView.kt`

4. **`Sidebar.kt` (1,349 lines)**:
   - Decompose into:
     - `SidebarCollectionsSection.kt`
     - `SidebarEnvironmentsSection.kt`
     - `SidebarHistorySection.kt`
     - `SidebarFooter.kt`
     - `Sidebar.kt` (shell coordinating tabs and sections)

5. **`WebSocketRequestEditor.kt`, `CodeEditor.kt`, `CollectionEditor.kt`, `VariableHighlight.kt`**:
   - Decompose into focused sub-panes (e.g. search bars, toolbars, timeline panes).

6. **Tooltip Deduplication**:
   - Replace `ToolbarIconButtonWithTooltip` in `TopToolbar.kt` and `EditorIconButtonWithTooltip` in `CodeEditor.kt` with `SharedTooltip.kt`'s `IconButtonWithTooltip`.

### Rationale
- Drastically reduces file sizes (bringing all files below the 1,000 line ceiling).
- Separates domain sub-views into distinct files, improving compile times and readability.
- Maintains public backwards compatibility by preserving public composable entrypoints in their original packages or re-exporting.
