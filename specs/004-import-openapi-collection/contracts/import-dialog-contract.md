# Contract: Import Specification Dialog

**Feature**: OpenAPI & OpenCollection Import Dialog
**Path**: `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ImportSpecificationDialog.kt`

## Composable Contract

```kotlin
@Composable
fun ImportSpecificationDialog(
    state: AppState,
    onDismiss: () -> Unit,
)
```

## Behavior Specifications

### 1. Source Selection View (Step 1)
- **Tabs**: Two source tabs: `File` and `Clipboard`.
- **File Mode**:
  - Drag-and-drop container accepting file drops of `.json`, `.yaml`, and `.yml` files.
  - Button "Choose Files" invoking file picker filtered to `.json`, `.yaml`, and `.yml`.
  - When file is chosen, displays file name, file size in KB, and a remove icon button.
- **Clipboard Mode**:
  - Displays description: "Import directly from your system clipboard."
  - When active, reads system clipboard via `PlatformApi.readFromClipboard()`.
  - If clipboard contains text, shows snippet preview or byte length and enables "Scan".
  - If clipboard is empty, displays "Clipboard is empty" message and disables "Scan".
- **Scan Button**:
  - Test tag: `testTag("import-scan-button")`
  - Enabled when source contains content.
  - On click: triggers asynchronous validation and parsing with progress indicator.
  - If error occurs: displays error banner `testTag("import-scan-error")` with message without leaving Step 1.
  - If success: advances state to Step 2 (`ITEM_SELECTION`).

### 2. Item Selection View (Step 2)
- **Header**: Title "Select Items to Import" and format badge (e.g. `OpenAPI 3.0` or `OpenCollection`).
- **Folders Section**:
  - Lists each detected folder candidate.
  - Checkbox to select/deselect: `testTag("import-folder-check-${candidate.id}")`.
  - Folder name with inline edit/rename: `testTag("import-folder-name-${candidate.id}")`.
  - Request count indicator (e.g. `5 requests`).
  - Overwrite Warning Badge: `testTag("import-folder-conflict-${candidate.id}")` shown if folder name matches an existing workspace folder in `state.collections`.
- **Environments Section** (if any detected):
  - Lists each detected environment candidate.
  - Checkbox to select/deselect: `testTag("import-env-check-${candidate.id}")`.
  - Environment name with inline edit/rename: `testTag("import-env-name-${candidate.id}")`.
  - Variable count indicator.
  - Overwrite Warning Badge: `testTag("import-env-conflict-${candidate.id}")` shown if environment name matches an existing workspace environment in `state.environments`.
- **Footer Actions**:
  - **Cancel Button**: `testTag("import-cancel-button")` -> invokes `onDismiss()` without making any changes to `state`.
  - **Import Button**: `testTag("import-confirm-button")` -> enabled if >=1 item selected. Commits selected items to `state.collections` and `state.environments`, replacing conflicting items where matched, persists workspace, logs success, and invokes `onDismiss()`.
