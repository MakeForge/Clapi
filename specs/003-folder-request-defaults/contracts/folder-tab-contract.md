# Interface Contract: Folder Settings Tab

**Feature Branch**: `003-folder-request-defaults`
**Date**: 2026-11-07
**Status**: Completed

## 1. Sidebar Interaction Contract

### 1.1 Chevron / Folder Icon Click
- **Trigger**: Mouse left-click on the expand chevron (`KeyboardArrowDown` / `KeyboardArrowRight`) or folder icon in the sidebar tree.
- **Action**: Toggles the folder's expansion state (`state.collectionExpandedState[folderId] = !isExpanded`).
- **Effect**: Expands or collapses child items in the sidebar. Does **not** open an editor tab.

### 1.2 Folder Name Text Click
- **Trigger**: Mouse left-click on the folder's name text in the sidebar tree.
- **Action**: Invokes `state.openFolder(folderId: String)`.
- **Preconditions**:
  - The node has `isFolder == true`.
- **Postconditions**:
  - If a tab with `id == folderId && kind == RequestKind.FOLDER` exists in `state.openTabs`:
    - `state.activeTabIndex` is set to the index of that tab.
  - If no such tab exists:
    - A new `RequestTabState` is instantiated with:
      - `id = folderId`
      - `name = folderNode.name`
      - `kind = RequestKind.FOLDER`
      - Initialized headers, query parameters, auth settings, and scripts from `folderNode`
    - Added to `state.openTabs`.
    - `state.activeTabIndex` set to the newly appended index.

---

## 2. Editor Tab Bar Contract (`RequestTabsBar`)

### 2.1 Tab Chip Representation
- **For `tab.kind == RequestKind.FOLDER`**:
  - Left indicator: Renders `Icons.Default.Folder` (tinted with `ClapiColors.Tertiary`) instead of a method badge.
  - Label: Displays `tab.name` (+ `" *"` if `tab.isDirty`).
  - Close button: Standard close `X` button closes the folder tab.
  - Rename inline: Double click or context menu rename edits the folder name and synchronizes with `CollectionNode.name`.

---

## 3. Main Editor Contract (`MainScreen`)

### 3.1 Content Routing
- **Condition**: `currentTab.kind == RequestKind.FOLDER`
- **Component**: Renders `FolderEditor(tab = currentTab, state = state, onSave = { ... })`.
- **Behavior**:
  - Reuses `EditorTabBar` with tabs:
    - `PARAMS`: Default query parameters.
    - `HEADERS`: Default HTTP headers.
    - `AUTH`: Folder default authentication.
    - `PRE_REQUEST`: Folder pre-request script.
    - `TESTS`: Folder post-response/test script.
  - Excludes the HTTP URL address bar, HTTP method dropdown, Send button, and ResponseViewer split pane.
  - Features a top header displaying the folder name and a "Save" action button (plus shortcut Ctrl+S / Cmd+S support).
