# Feature Specification: Support Multiple OpenCollection Collections

**Feature Branch**: `008-multiple-collections`

**Created**: 2026-11-09

**Status**: Draft

**Input**: User description: "Currently we only have 1 global collection in which the user can define folders, requests etc. We now want to support being able to maintain multiple OpenCollection Collections. The sidebar now has additional hierarchy, instead of having one top level 'Collection', there is a 'Collections' with '+' to add a new collection, and there will be for every collection an item like current 'Collection' top-level one, inside of which its folders etc. are shown. Collections can be expanded/collapsed. Ensure that the import/export properly support this. For the global export option allow the user to select which collections should be exported (1 file per collection). In the collection editor add new tab (should be first on the left) 'Info', in which user can change the OpenCollectionDocument.info CollectionInfo fields (authors can be omitted). Same as for folders/requests, by double clicking the collections name you can inline change the collection's name. Also now it should be possible to define collection-level requestDefaults for the same fields as currently for folder-level requestDefaults in the Collection Editor. A folder can then inherit their request defaults from the collection."

## Clarifications

### Session 2026-11-09

- Q: How should duplicate collection names be handled when creating, renaming, or importing a collection? → A: Automatically append an incremental index (e.g., `Collection (1)`, `Collection (2)`) instead of displaying validation error feedback.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Multiple Collections Hierarchy and Management in Sidebar (Priority: P1)

Users managing multiple independent APIs or microservices need to organize their endpoints into distinct, standalone collections within a single workspace. In the sidebar, instead of having a single top-level "Collection" section containing folders directly, users see a primary "Collections" section header equipped with an "Add Collection" (`+`) action. Underneath this header, multiple collections are listed at the root level. Each collection entry displays its name and collection icon, can be expanded or collapsed to show or hide its nested folders and requests, and can be renamed inline by double-clicking on the collection's name text. Clicking on a collection entry opens or focuses its dedicated Collection Editor in the main editor area.

**Why this priority**: Foundational structural change needed to enable multi-collection support and navigate multiple independent collections in the UI.

**Independent Test**: Can be tested by launching the application, clicking the "+" button on the Collections header to create multiple collections, expanding/collapsing each collection, double-clicking a collection's name to rename it inline, and clicking a collection to open its editor.

**Acceptance Scenarios**:

1. **Given** an open workspace, **When** viewing the sidebar, **Then** the primary section is titled "Collections" and features an "Add Collection" (`+`) action button.
2. **Given** the "Collections" header, **When** the user clicks the "Add Collection" (`+`) button, **Then** a new collection is created with a default unique name and appended to the collections list in the sidebar.
3. **Given** one or more collections in the sidebar, **When** the user clicks the expand/collapse toggle of a collection, **Then** its child folders and requests expand or collapse without affecting other collections.
4. **Given** a collection listed in the sidebar, **When** the user double-clicks on its name text, **Then** an inline text input is activated allowing the user to type a new name and commit it by pressing Enter or clicking away.
5. **Given** a collection in the sidebar, **When** the user single-clicks on its name, **Then** an editor tab for that collection opens in the central workspace editor tab bar (or gains focus if already open).
6. **Given** a collection with nested folders and requests, **When** the user deletes the collection, **Then** the user is prompted for confirmation (if confirmations are enabled), the collection and all its descendants are removed, and any open editor tabs associated with that collection or its descendants are closed.
7. **Given** a collection named "Payments", **When** the user creates, renames, or imports another collection with the name "Payments", **Then** the system automatically disambiguates the name to "Payments (1)" by appending an incremental index without blocking the user with validation error messages.

---

### User Story 2 - Collection Editor with Info Tab and Collection Request Defaults (Priority: P1)

Users configuring a collection need to maintain its high-level metadata (such as summary and version) as well as collection-wide defaults that apply across all child folders and requests. When opening a collection in the editor:
- The first tab on the left is the "Info" tab, which allows viewing and editing the collection's metadata: name, summary, and version (authors are omitted).
- The editor also provides Request Defaults tabs (Variables, Headers, Authentication, Pre-request Script, Post-request / Tests Script) identical in configuration capability to folder-level request defaults.
- The existing Protobuf management tab remains accessible within the Collection Editor for collection-level proto files and import paths.

**Why this priority**: Enables full parity with the OpenCollection standard metadata and allows users to set baseline request defaults and metadata at the collection boundary.

**Independent Test**: Can be tested by opening the Collection Editor for a collection, navigating to the "Info" tab, editing the summary and version, updating the collection name, switching to the Headers, Variables, Auth, and Scripts tabs to configure collection-level defaults, and confirming all changes persist and reflect across the workspace.

**Acceptance Scenarios**:

1. **Given** an open Collection Editor, **When** viewed, **Then** the first tab on the left is labeled "Info".
2. **Given** the "Info" tab, **When** the user edits the name, summary, or version fields and saves, **Then** the collection metadata is updated, and changes to the collection name immediately update the sidebar label and editor tab title.
3. **Given** an open Collection Editor, **When** navigating through the editor tabs, **Then** the user can access Info, Variables, Headers, Auth, Pre-request Script, Tests Script, and Protobuf tabs.
4. **Given** the Request Defaults tabs in the Collection Editor, **When** the user configures headers, variables, auth settings, or scripts, **Then** these settings are stored as collection-level request defaults.

---

### User Story 3 - Request Defaults Inheritance from Collection to Folders and Requests (Priority: P1)

Users want a consistent cascading configuration model: Collection -> Folder -> Request. Folders inside a collection inherit their request defaults from that collection, and requests inside folders inherit defaults from their ancestor folders and collection:
- In folder and request settings, inherited headers and variables from the enclosing collection (and parent folders) are clearly displayed and marked as "Inherited".
- Overriding rules apply consistently: a child folder or request specifying a header or variable with the same key overrides the inherited value.
- Authentication configured as "Inherit" at the folder level inherits from the parent collection's authentication. Requests set to "Inherit" resolve authentication through the folder hierarchy up to the collection.
- Collection pre-request and post-request test scripts execute in sequence before/after folder and request scripts.

**Why this priority**: Eliminates redundant configuration across folders in the same collection and ensures predictable cascade behavior throughout the entire hierarchy.

**Independent Test**: Configure a default header and Bearer token at the collection level. Create a folder inside that collection and verify that the folder reflects inherited collection auth and headers. Create a request inside the folder, verify that the inherited headers and auth cascade through, and test execution to ensure the resolved defaults are applied.

**Acceptance Scenarios**:

1. **Given** a collection with default headers and variables configured, **When** a user views a folder's settings inside that collection, **Then** the collection's defaults are visible and marked as inherited from the collection.
2. **Given** a folder with authentication set to "Inherit", **When** its parent collection has an authentication method configured, **Then** the folder resolves and displays the inherited collection authentication.
3. **Given** a request inside a folder within a collection, **When** the request auth is set to "Inherit", **Then** it inherits the resolved authentication from its parent folder, which cascades from the collection if the folder also inherits.
4. **Given** a collection with an inherited header `Authorization: Bearer col-token`, **When** a folder overrides `Authorization: Bearer folder-token`, **Then** requests in that folder receive the folder-level overridden value.

---

### User Story 4 - Selective Multi-Collection Export and Collection Import (Priority: P2)

Users who manage multiple collections need flexible import and export capabilities:
- When exporting workspace collections globally, the user is presented with a selection dialog allowing them to choose which collections to export.
- Each selected collection is exported as its own individual OpenCollection file (1 file per collection) in the selected target destination directory.
- Users can also export an individual collection directly from that collection's context menu or options.
- When importing OpenCollection files, users can import one or more collection files into the workspace; each imported file creates or updates a distinct collection in the workspace rather than merging into a single global collection.

**Why this priority**: Essential for sharing individual collections with teammates, exporting subsets of collections for version control or distribution, and importing external OpenCollection files cleanly.

**Independent Test**: Can be tested by creating 3 collections, choosing the global export action, selecting 2 of the 3 collections, picking an output destination, and verifying that exactly 2 distinct collection files are written. Then importing a collection file and verifying it appears as a distinct collection in the sidebar.

**Acceptance Scenarios**:

1. **Given** multiple collections in the workspace, **When** the user initiates the global OpenCollection export action, **Then** a dialog is presented listing all collections with checkboxes to select which collections should be exported.
2. **Given** the export selection dialog with one or more collections selected, **When** the user confirms and selects an export destination folder, **Then** the system writes one OpenCollection file per selected collection into that destination.
3. **Given** an individual collection in the sidebar, **When** the user opens its options menu and selects export, **Then** that single collection is exported to a user-chosen file.
4. **Given** an external OpenCollection file (JSON or YAML), **When** the user imports it, **Then** a new collection is added to the sidebar retaining the imported document's name, info metadata, request defaults, folders, and requests.

---

### User Story 5 - Workspace Persistence and Migration from Single Collection (Priority: P3)

Existing users have workspaces created under the previous single-collection model. When opening an existing workspace:
- The system automatically migrates the single global collection into the multi-collection model without data loss.
- The existing root folders and requests become part of the primary migrated collection.
- Future application launches seamlessly load all collections, their expansion states, info metadata, request defaults, folders, and requests.

**Why this priority**: Guarantees zero regressions or data loss for existing users transitioning to the multi-collection architecture.

**Independent Test**: Can be tested by loading a workspace stored in the previous single-collection format, verifying that all folders and requests appear inside a valid collection, saving modifications, restarting the application, and confirming that the workspace state persists intact.

**Acceptance Scenarios**:

1. **Given** an existing workspace saved under the single-collection format, **When** loaded into the application, **Then** the workspace loads with a default collection containing all previous items, request defaults, environments, and configs.
2. **Given** a workspace with multiple collections, **When** the user modifies collections, folders, or requests and closes/reopens the application, **Then** all collections and their contents are restored exactly as saved.

---

### Edge Cases

- **Deleting the last collection**: If the user deletes all collections in the workspace, the sidebar displays an empty state with a clear call-to-action to create or import a collection, or automatically initializes a new default empty collection.
- **Duplicate collection names**: If a user creates, renames, or imports a collection resulting in a name collision with an existing collection, the system automatically appends an incremental index (e.g., "Collection (1)", "Collection (2)") to guarantee unique naming without displaying blocking validation error feedback.
- **Inline rename cancellation**: Pressing `Escape` during inline rename reverts the name to its previous value without applying changes.
- **Empty name validation**: Submitting a blank or whitespace-only name during inline rename cancels the edit and preserves the previous valid name.
- **Exporting with existing filenames**: If an exported collection file name matches an existing file in the chosen export directory, the user is warned before overwriting or given the option to choose a different location.
- **Deeply nested inheritance**: If a request is in a deeply nested folder (e.g., Collection -> Folder A -> Folder B -> Request) and multiple levels define conflicting defaults, the nearest ancestor takes precedence (Request > Folder B > Folder A > Collection).
- **Moving folders or requests across collections**: If a folder or request is moved from Collection A to Collection B (via drag-and-drop or context menu), its inherited defaults immediately re-bind to Collection B's defaults.
- **Importing invalid or corrupt collection files**: If an imported file is invalid or corrupt, the system reports a descriptive error dialog and leaves existing collections untouched.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The sidebar MUST display a top-level "Collections" section header that contains all workspace collections.
- **FR-002**: The "Collections" sidebar header MUST provide an "Add Collection" (`+`) action button that creates a new collection in the workspace.
- **FR-003**: Each collection item in the sidebar MUST display an expand/collapse toggle, the collection name, and a collection icon.
- **FR-004**: Each collection item in the sidebar MUST be independently expandable and collapsible, persisting its expanded/collapsed state.
- **FR-005**: Double-clicking a collection's name in the sidebar MUST activate inline renaming, allowing the user to rename the collection directly.
- **FR-006**: Left-clicking a collection item in the sidebar MUST open or focus that collection's dedicated Collection Editor tab.
- **FR-007**: The Collection Editor MUST include an "Info" tab positioned as the first tab on the left.
- **FR-008**: The "Info" tab in the Collection Editor MUST allow viewing and editing the collection name, summary, and version metadata fields (authors omitted).
- **FR-009**: The Collection Editor MUST provide Request Defaults editing tabs for Variables, Headers, Authentication, Pre-request Script, and Tests Script.
- **FR-010**: The Collection Editor MUST preserve the Protobuf management tab for configuring collection-level proto files and import paths.
- **FR-011**: Folders contained within a collection MUST inherit request defaults (headers, variables, authentication, and scripts) from the collection.
- **FR-012**: In folder and request settings, inherited headers and variables MUST be visibly distinguished with an "Inherited" indicator and attributed to their inheritance source (collection or folder).
- **FR-013**: Authentication on folders and requests MUST support an "Inherit" option that resolves up through ancestor folders to the parent collection's configured authentication.
- **FR-014**: The application MUST provide a global export dialog allowing the user to select which collections to export.
- **FR-015**: Global multi-collection export MUST produce exactly one standalone OpenCollection file per selected collection in the chosen target directory.
- **FR-016**: The application MUST allow exporting an individual collection directly to a single file from its sidebar item or options menu.
- **FR-017**: Importing an OpenCollection document MUST create or update a distinct collection in the workspace.
- **FR-018**: Workspaces created under the legacy single-collection format MUST automatically migrate to the multi-collection model on first load without data loss.
- **FR-019**: All collections, their metadata, request defaults, protobuf configurations, folders, and requests MUST persist across application restarts.
- **FR-020**: When creating, renaming, or importing a collection, if the name conflicts with an existing collection, the system MUST automatically disambiguate the name by appending an incremental index (e.g., "Collection (1)") instead of showing validation error feedback.

### Key Entities *(include if feature involves data)*

- **Collection**: The top-level container representing an OpenCollection document. Contains collection metadata (`CollectionInfo`), collection configuration (`CollectionConfig` including Protobuf and Environments), collection-level request defaults (`RequestDefaults`), and a tree of folders and requests (`OpenCollectionItem`).
- **Collection Info (`CollectionInfo`)**: Metadata describing a collection, containing `name`, `summary`, and `version` (with `authors` omitted in the Info tab editor).
- **Collection Request Defaults (`RequestDefaults`)**: Baseline request configuration defined at the collection level, including headers, variables, authentication, pre-request scripts, and test scripts.
- **Folder**: An intermediate container within a collection that groups requests and child folders, optionally overriding or supplementing collection request defaults.
- **Request**: An individual HTTP or gRPC request residing within a folder or collection root, inheriting defaults through the hierarchy unless explicitly overridden.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can create, expand/collapse, and inline rename a new collection in under 15 seconds.
- **SC-002**: 100% of collection-level metadata (name, summary, version) and request defaults configured in the Collection Editor are accurately preserved and applied across child folders and requests.
- **SC-003**: When performing a global export with multiple collections selected, the system generates exactly one valid OpenCollection file per selected collection with zero data corruption.
- **SC-004**: Workspaces with single-collection legacy data load and migrate to the multi-collection model on first launch with 100% data fidelity (no lost folders, requests, scripts, or settings).
- **SC-005**: All open editor tabs, collection hierarchies, and inheritance resolution update within 100 milliseconds of renaming a collection or modifying collection request defaults.

## Assumptions

- **Authors Field**: The `authors` field of `CollectionInfo` is omitted from the UI editor as specified, but if present in imported files, it is preserved during storage and export.
- **File Format on Multi-Export**: The global export option writes files in the user's preferred format (JSON or YAML) to a destination folder chosen by the user via the system file picker.
- **Inheritance Cascade**: The inheritance order follows standard lexical scoping: Request overrides direct Folder, direct Folder overrides Parent Folder, and Parent Folder overrides Collection.
- **Default Collection on Migration**: If an existing workspace is loaded that has no explicit multi-collection container, all existing items are wrapped in a single collection taking the workspace or document name.
