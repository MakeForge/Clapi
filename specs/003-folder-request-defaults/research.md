# Technical Research & Decisions: Folder Settings and Request Defaults Inheritance

**Feature Branch**: `003-folder-request-defaults`
**Date**: 2026-11-07
**Status**: Completed

## 1. UI Nomenclature: "Collections" to "Folders"

### Context & Requirements
Under the hood, Clapi's data model represents collections and their hierarchical subdirectories as OpenCollection `FolderItem`s inside an `OpenCollectionDocument`. However, throughout the UI (sidebar headers, dialogs, button tooltips, and tab labels), these containers are frequently referred to as "Collections", causing confusion when users organize nested items and manage defaults.

### Decision
- Update UI strings and labels across `Sidebar.kt`, `Strings.kt`, `en.json` (and other language bundles), and relevant dialogs from "Collections" to "Folders".
- The sidebar section header becomes "Folders" (or "Folders / Collections" where importing collection files is initiated).
- Actions become "New Folder", "Rename Folder", "Delete Folder", "Export Folder".
- Retain internal file/model compatibility: OpenCollection import/export and serialization continue to use standard OpenCollection specifications where top-level items and subdirectories map to `FolderItem`.

### Rationale
- Matches user expectations for nested tree organization.
- Aligns UI terminology with the underlying `FolderItem` and `isFolder = true` model representation.
- Clean separation between the workspace file/document level and the hierarchical folders within it.

---

## 2. Folder Settings Tab Representation & Architecture

### Context & Requirements
When a user clicks on a folder's name in the sidebar, a tab must open in the editor tab bar allowing the user to view and edit `FolderItem.request` (the `RequestDefaults` object containing headers, query params, auth, scripts, and variables).
The application currently models open tabs using `AppState.openTabs: SnapshotStateList<RequestTabState>`.

### Decision
- Extend `RequestKind` with `RequestKind.FOLDER` (or introduce a dedicated folder tab flag on `RequestTabState` such as `isFolderTab = true` / `folderId: String`).
- When `tab.kind == RequestKind.FOLDER`:
  - `tab.id` equals the folder's `CollectionNode.id`.
  - `tab.name` is the folder name (synced when the folder is renamed).
  - It holds mutable state lists for folder-level default headers, params, auth credentials, and pre-request / post-request scripts.
  - Tab Bar rendering in `RequestTabsBar.kt`: Instead of an HTTP method badge (GET/POST/etc.), render a Folder icon (`Icons.Default.Folder`) and the folder name.
  - Context menu and tab actions: Standard close ("Close", "Close Others", "Close Tabs to Left/Right", "Close All") and "Show in Sidebar" work seamlessly.
  - Main Editor rendering in `MainScreen.kt`: When the active tab is `RequestKind.FOLDER`, render `FolderEditor` (which reuses `EditorTabBar`, `KeyValueEditor`, `AuthEditor`, and `ScriptEditor`) without the HTTP URL bar or ResponseViewer split pane.
  - Single-instance tab focus: Clicking an already-open folder in the sidebar checks `state.openTabs.indexOfFirst { it.id == folderId }`. If found, it simply updates `state.activeTabIndex` rather than opening a duplicate tab.
  - Persistence: `TabsRepository` serializes `kind = FOLDER`, saving and restoring folder tabs across application restarts.

### Alternatives Considered
- *Separate state list `openFolderTabs`*: Rejected because it would require rewriting `RequestTabsBar`, active tab indexing, split panes, and tab drag-and-drop ordering. Reusing `RequestTabState` with `kind = RequestKind.FOLDER` integrates natively into existing tab management with zero friction.
- *Modal dialog for folder settings*: Rejected because modal dialogs disrupt workspace workflows and prevent comparing request settings side-by-side in tabs.

---

## 3. Sidebar Interaction: Folder Name vs. Expand Chevron

### Context & Requirements
Currently in `Sidebar.kt` (`CollectionTreeNode`), clicking anywhere on the entire row toggles expansion (`state.collectionExpandedState[node.id] = !expanded`).
The requirement specifies: "if you left click on a Folder's name (Text) in the side bar it should open a Tab for that folder's settings".

### Decision
- Separate the click handlers in `CollectionTreeNode`:
  - Clicking the expansion chevron (`Icons.Default.KeyboardArrowDown` / `Icons.AutoMirrored.Filled.KeyboardArrowRight`) or folder icon toggles collapse/expand.
  - Left-clicking the folder's name `Text` component opens or focuses the folder settings tab in `AppState`.
  - Right-clicking the folder row continues to open the folder context menu (New Request, New Folder, Rename, Delete, Export, etc.).

### Rationale
- Gives users precise control: expanding/collapsing a folder is lightweight without opening unwanted editor tabs, while clicking the folder name opens the configuration view as expected.

---

## 4. Inherited Settings Resolution & UI Indication

### Context & Requirements
When a request inside a folder is opened:
- The inherited request settings from its folder must be shown in the corresponding tabs.
- For auth: add an "Inherit" option that inherits from the folder `RequestDefaults` or allows manual selection.
- For headers: inherited headers must be shown and marked as "Inherited".
- Overriding: if a request defines a header with the same key, it overrides the folder header.

### Decision
1. **Authentication Inheritance**:
   - Add `AuthType.INHERIT` to `AuthType`.
   - In `RequestTabState`, when `authType == AuthType.INHERIT`, the effective auth is resolved from the parent folder (or traversed up the folder tree if nested).
   - In `AuthEditor`: When `tab.folderPath.isNotEmpty()` or `tab.collectionId != null` (the request is in a folder), include `AuthType.INHERIT` in the selectable chips.
   - When `INHERIT` is active, display a banner/panel: "Inheriting authentication from folder `[FolderName]` ([Resolved Auth Type])", with read-only summary of the inherited auth configuration.
   - For folder settings tabs: If the folder is nested within another parent folder, it too can inherit or configure its own auth; root folders offer standard auth types (`NONE`, `BEARER`, `BASIC`, `API_KEY`).
2. **Header Inheritance**:
   - Extend `HeaderKind` enum with `HeaderKind.INHERITED`.
   - In `RequestTabState`, maintain/compute effective headers: when opening a request, parent folder default headers that are enabled are merged into the tab's header view as `HeaderKind.INHERITED`.
   - In `KeyValueEditor`:
     - Show an "Inherited" tag/badge for rows where `kind == HeaderKind.INHERITED`.
     - Disable inline editing and delete for `INHERITED` rows (or display an informative tooltip: "Inherited from folder settings").
     - If a user adds a custom header with the same name (case-insensitive), the custom header is sent on the wire, overriding the inherited one.
     - Visual indicator: if an inherited header is overridden by a request header, show a strike-through or "Overridden" indicator.
3. **Query Parameter Inheritance**:
   - Folder default query parameters are similarly merged and surfaced or passed during request URL assembly, with request parameters taking precedence for matching keys.

---

## 5. Request Scripts: Behavior & Concerns

### Context & Requirements
"If there are any major concerns when it comes to request scripts, clear that up or add TODO comments to address the issues later"

### Analysis of Script Execution Concerns
1. **Chaining Order**:
   - In standard API tools (Postman, Bruno), scripts execute in hierarchical order:
     - Pre-request scripts: Ancestor Folder 1 -> Folder 2 -> Request pre-request script.
     - Post-request / Test scripts: Request test script -> Folder 2 test script -> Ancestor Folder 1 test script.
2. **State & Variable Propagation**:
   - If a folder pre-request script sets a variable (e.g. `clapi.variables.set("token", ...)`), subsequent scripts and the request itself need access to the updated variable map.
   - In Clapi, `RequestExecutor.kt` executes scripts using `ScriptEngine` and `ScriptContext`.
3. **Control Flow Interruptions**:
   - If a folder script invokes `execution.skipRequest()`, should the child request and remaining scripts be aborted? Yes.
   - If a folder script throws an unhandled script error, should execution halt or log and proceed?
4. **Current Implementation Scope vs. TODOs**:
   - The UI must allow editing folder-level Pre-request and Post-request scripts in the Folder Settings tab.
   - Folder scripts are serialized in `RequestDefaults.scripts` (`Script(type = "before-request", code = ...)`, `Script(type = "after-response", code = ...)`).
   - In `RequestExecutor.kt`:
     - Resolve ancestor folder scripts along `tab.folderPath`.
     - Execute folder pre-request script prior to `tab.preRequestScript`.
     - Add explicit `// TODO:` comments addressing multi-level script rollback, cross-script sandbox variable isolation, and nested script debugging.

### Conclusion & Plan
- Folder settings tab will provide `ScriptEditor` for both "Pre-request Script" and "Post-request Script" (matching `RequestEditor`).
- `RequestExecutor` will be updated to chain folder pre-request scripts, with documented execution semantics and explicit `TODO` markers for advanced cascading features.
