# Research: Support Multiple OpenCollection Collections

**Branch**: `008-multiple-collections` | **Date**: 2026-11-09

## Research Objectives & Findings

### 1. Sidebar Hierarchy & Multiple Collections Tree Model

#### Problem
Previously, the sidebar had a single top-level "Collection" header (representing 1 global collection in `workspace.json`), under which folders and requests were directly listed. We need to introduce an additional hierarchy level:
- A top-level "Collections" section header with a `+` button to create a new collection.
- Underneath, every collection in the workspace is listed with an Article icon, expandable/collapsible toggle, inline renaming via double-click, and a `+` button to add folders within that specific collection.
- When expanded, each collection displays its contained folders and requests.

#### Decision
- Replace the single top-level `SectionHeader` with a `"Collections"` header. Its trailing action is an `IconButton` (`+`) that appends a new collection to `state.collections`.
- Each collection item is rendered as a top-level collection header row with:
  - Expand/collapse chevron controlling the collection's expanded state in `state.collectionExpandedState[collection.id]`.
  - Material `Icons.Default.Article` icon with tertiary tint.
  - Double-click on name activates inline renaming (`inlineRenamingId = collection.id`).
  - Single-click on collection row calls `state.openCollection(collection.id)` to open/focus its `CollectionEditor` tab.
  - Trailing action `+` button to create a new folder inside that collection (`addFolderTarget = collection`).
  - Right-click / options menu with: "Add Folder", "Add Request", "Duplicate Collection", "Export Collection", "Delete Collection".
- If expanded, the collection's `children` (folders and requests) are rendered with `CollectionTreeNode` indented at depth 1.

#### Alternatives Considered
- *Separate side panel for collections*: Rejected because the unified tree view is standard in REST/gRPC desktop clients and preserves muscle memory.
- *Flat list with collection dropdown switcher*: Rejected because viewing multiple collections simultaneously in the sidebar tree allows cross-collection navigation, dragging, and side-by-side inspection.

---

### 2. Collection Data Representation & State Modeling

#### Problem
How should collections be stored in `AppState` while preserving existing tree operations (drag-and-drop, selection, search filtering, and serialization)?

#### Decision
- Model each collection as a `CollectionNode` with `kind = RequestKind.COLLECTION` and `isFolder = true` (or helper `isCollection: Boolean`).
- Store in `state.collections: MutableList<CollectionNode>`.
- Extend `CollectionNode` with collection-level fields:
  - `summary: String? = null` (from `CollectionInfo.summary`)
  - `version: String? = null` (from `CollectionInfo.version`)
  - `protoFiles: List<ProtoFile>? = null` (collection-level protobuf files)
  - `importPaths: List<ProtoFileImportPath>? = null` (collection-level protobuf import paths)
- Existing `CollectionNode` fields seamlessly hold collection-level request defaults:
  - `variableEntries: List<KeyValueEntry>?`
  - `headerEntries: List<KeyValueEntry>?`
  - `authType: AuthType?`, `authUsername: String?`, `authToken: String?`, etc.
  - `preRequestScript: String?`, `testScript: String?`
  - `children: MutableList<CollectionNode>` (the child folders and requests of this collection)
- This avoids duplicating tree traversal, duplicate/delete operations, and search filtering while giving collections full first-class identity.

#### Alternatives Considered
- *Dedicated `CollectionState` separate from `CollectionNode`*: Would require duplicating all tree manipulation logic, hit-area drag-and-drop reordering, and search filtering. Leveraging `CollectionNode` with `kind = RequestKind.COLLECTION` keeps all tree operations unified.

---

### 3. Request Defaults Inheritance (Collection -> Folder -> Request)

#### Problem
Currently, folders can define request defaults that cascade to child requests. We now need collection-level request defaults to cascade to child folders, and through them down to requests:
- Folder inheriting request defaults from its enclosing collection.
- Request inheriting from its folder, which in turn inherits from the collection.
- Requests placed directly under the collection root inheriting directly from the collection.

#### Decision
- Update `getAncestorFolders(tab)` in `AppState` to include the enclosing collection node as the root of the ancestor chain.
- Resolution order (top-down lexical scoping):
  - **Headers & Variables**: Merged root-to-leaf: `Collection Defaults` -> `Parent Folder Defaults` -> `Child Folder Defaults` -> `Request Definitions`. When matching keys exist, closer descendants override higher ancestors. Inherited entries are surfaced with `HeaderKind.INHERITED` in the UI.
  - **Authentication**: `AuthType.INHERIT` searches upward: Child Folder -> Parent Folder -> Enclosing Collection. If a collection defines authentication, an inheriting folder or request resolves to the collection's auth.
  - **Scripts**: Execution sequence follows top-down for pre-request scripts (`Collection -> Folder -> Request`) and bottom-up/top-down standard for test scripts.
- In `FolderEditor`, folders inside a collection display inherited collection defaults and support `AuthType.INHERIT` pointing to the collection's configured auth.

#### Alternatives Considered
- *Separate collection inheritance resolver*: Creates dual inheritance logic that could diverge. Using a unified ancestor chain (`[Collection, Folder1, Folder2, ...]`) guarantees 100% consistent cascade behavior across all levels.

---

### 4. Collection Editor Architecture ("Info", Request Defaults, "Protobuf")

#### Problem
In the Collection Editor, the user needs:
1. An "Info" tab (must be the first tab on the left) to view and edit `CollectionInfo` fields (`name`, `summary`, `version`; `authors` omitted).
2. Request Defaults tabs matching `FolderEditor`: Variables, Headers, Auth, Pre-request Script, Tests Script.
3. The existing "Protobuf" tab for collection proto files and import paths.

#### Decision
- Update `CollectionEditorTab` enum in `CollectionEditor.kt`:
  ```kotlin
  enum class CollectionEditorTab(val label: String) {
      INFO("Info"),
      VARIABLES("Variables"),
      HEADERS("Headers"),
      AUTH("Auth"),
      PRE_REQUEST("Pre-request"),
      TESTS("Tests"),
      PROTOBUF("Protobuf"),
  }
  ```
- Make `INFO` the default and first tab on the left.
- When opening a collection via `state.openCollection(collectionId)`:
  - Populate `RequestTabState` with the collection's `id`, `name`, `summary`, `version`, request defaults (`variables`, `headers`, `authType`, etc., `preRequestScript`, `testScript`), and protobuf lists.
  - Provide an "Info" editor composable with text fields for `Name`, `Summary`, and `Version`.
  - For Request Defaults, reuse the standard `KeyValueEditor`, `AuthEditor`, and `ScriptEditor` composables.
  - On save, sync all modified fields back to the matching `CollectionNode` in `state.collections` and persist workspace.

#### Alternatives Considered
- *Separate dialog for collection info*: User specifically requested an "Info" tab inside the Collection Editor as the first tab on the left.

---

### 5. Multi-Collection Global Export & Import

#### Problem
- Global export currently exports the entire workspace as 1 file. Now, global export must allow selecting which collections should be exported and output 1 file per selected collection.
- Import must handle importing OpenCollection documents as distinct collections in `state.collections`.

#### Decision
- **Global Export Flow**:
  1. Triggering global export (from sidebar header or menu) opens an `ExportCollectionsDialog`.
  2. The dialog lists all collections with selection checkboxes, a "Select All" toggle, and format selection (JSON or YAML, defaulting to the selected export menu action).
  3. When confirmed, a native directory chooser / folder picker is presented (`DirectoryChooser` on desktop).
  4. The system serializes each selected collection to a standalone `OpenCollectionDocument` and writes a file named `<collection_name>.opencollection.json` (or `.yaml`) into the target directory.
  5. If a filename collision occurs, automatic numeric disambiguation is applied.
- **Single Collection Export**:
  - Maintained on each collection's sidebar context menu ("Export Collection..."), writing a single chosen file.
- **Import Flow**:
  - Parses OpenCollection JSON/YAML or Postman v2 document.
  - Disambiguates name collisions by appending `(1)`, `(2)`, etc.
  - Adds the document as a new distinct collection node in `state.collections`.

#### Alternatives Considered
- *Exporting all collections in a single zip archive*: 1 file per collection in a selected target directory provides direct filesystem access without requiring extraction utilities, directly fulfilling the user requirement.

---

### 6. Backward Compatibility & Single-Collection Migration

#### Problem
Existing user workspaces have single-collection data in `workspace.json` where `items` are top-level folders.

#### Decision
- In `WorkspaceRepository.load()`:
  - If the loaded document represents a legacy single-collection document (i.e., top-level items are `FolderItem`s without a parent collection wrapper):
  - Automatically wrap existing folders, requests, protobuf configs, and environments into a single migrated collection named with the document's name (e.g., "Workspace" or "My Collection").
  - On subsequent save, writes the multi-collection structure to `workspace.json`.
  - Ensures zero data loss and seamless upgrade.
