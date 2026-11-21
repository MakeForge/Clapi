# UI Contract: Collection Editor & Sidebar Navigation

## 1. Sidebar Top-Level Header Contract

- **Component**: `Sidebar.kt`
- **Location**: Top of collection tree.
- **Display**:
  - Title: `"Collection"` (derived from `Strings.collection` / `Strings.t("collection")`).
  - Leading Icon: `Icons.Default.Article` (tinted with `ClapiColors.OnSurfaceVariant`).
  - Chevron Icon: `KeyboardArrowDown` when expanded, `KeyboardArrowRight` when collapsed.
- **Interactions**:
  - **Left Click on Row**:
    - Expands section if collapsed (`state.collectionsExpanded = true`).
    - Invokes `state.openCollection()`.
  - **Click on Chevron**:
    - Toggles `state.collectionsExpanded`.

---

## 2. Tab Representation Contract

- **Enum**: `RequestKind.COLLECTION`
- **Tab Properties**:
  - `id`: `"collection_settings"` (singleton tab ID per workspace).
  - `name`: `"Collection"`.
  - `kind`: `RequestKind.COLLECTION`.
- **Tab Bar Rendering** (`RequestTabsBar.kt`):
  - When `tab.kind == RequestKind.COLLECTION`, renders `Icons.Default.Article` with `ClapiColors.OnSurfaceVariant` styling in place of HTTP method badges or folder icons.
  - Close button closes the tab normally.

---

## 3. Collection Editor View Contract

- **Composable**: `CollectionEditor(state: AppState, onSave: () -> Unit)`
- **Tabs**: Single tab "Protobuf" (represented by `CollectionEditorTab.PROTOBUF`).
- **Layout & Structure**:
  - **Header Area**:
    - Title: "Collection Settings".
    - Subtitle / Tab selector: "Protobuf".
  - **Section 1: Proto Files**:
    - Title: "Proto Files".
    - Subtitle: "Service definitions available across all gRPC requests in this collection."
    - Action: "Add Proto Files" button (opens native file picker with multi-selection enabled).
    - Table / List:
      - Column 1: File icon + File name (e.g. `greeter.proto`) + full directory tooltip.
      - Column 2: File path (monospace, truncated with ellipsis).
      - Column 3: Actions:
        - "Open File" button (`Icons.Default.OpenInNew` / `Visibility`) invoking `PlatformApi.openFileInDefaultApp(path)`.
        - "Remove" button (`Icons.Default.Delete`) removing the entry and calling `onSave()`.
  - **Section 2: Import Paths**:
    - Title: "Import Paths".
    - Subtitle: "Search paths and shared proto files used to resolve imports during compilation."
    - Action: "Add Import Paths" button.
    - Table / List:
      - Column 1: Checkbox toggle (checked = enabled, unchecked = disabled).
      - Column 2: File/folder icon + path.
      - Column 3: Actions:
        - "Open File" button (disabled or hidden for directories, active for files).
        - "Remove" button.
