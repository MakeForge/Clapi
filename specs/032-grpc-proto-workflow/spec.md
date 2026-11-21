# Feature Specification: gRPC Request Proto Folder Workflow

**Feature Branch**: `032-grpc-proto-workflow`

**Created**: 2026-11-20

**Status**: Draft

**Input**: User description: "we need to rework the grpc request proto workflow:
currently if i try to select a proto file which has imports from another proto in that same directory (or a subdirectory), it fails with e.g. (tried to select greeter.proto from qa-tests resources):
\"Failed to compile greeter.proto (exit code 1): protobuf/common/metadata.proto: File not found.
greeter.proto:8:1: Import \"protobuf/common/metadata.proto\" was not found or had errors.
greeter.proto:22:3: \"clapi.common.RequestMetadata\" is not defined.\"
So this expects me to go into my collection and add the other proto files/folder to import paths.
This is useful, but it should be possible in a single grpc request in the proto file dialog to select an entire folder, and then select a specific proto file from that folder from which I can then select the method in the request editor to execute the request with (any imports in that proto file are resolved against proto files from the selected folder and any additional collection wide proto files/import paths).
Also the sync to git toggle should be moved into the proto file dialog (be aware if a user selects an entire folder in the select proto dialog, we need to sync all proto files in the entire folder/subfolders to git)"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Select a Proto Folder and Target Proto File in a gRPC Request (Priority: P1)

A user configuring a single gRPC request whose target `.proto` file imports other `.proto` files from the same directory or nested subdirectories wants to configure everything directly in the request's Proto File dialog without having to modify collection-level settings. In the Proto File dialog, the user selects a root proto folder from their computer. The dialog discovers and lists the `.proto` files contained within that folder and its subfolders (showing their relative paths so nested files are easy to identify). The user picks the specific `.proto` file that defines the gRPC service they want to call and saves the selection. The system resolves imports in that target `.proto` file against all `.proto` files in the selected folder hierarchy (as well as any enabled collection-wide proto files and import paths), populates the gRPC method dropdown in the request editor, and executes requests against the selected RPC method without missing-import errors.

**Why this priority**: This directly solves the primary workflow blocker where selecting a `.proto` file with sibling or subdirectory imports fails unless the user manually adds collection-level import paths.

**Independent Test**: Open a gRPC request with no collection-level import paths configured, open the Proto File dialog, select a folder containing a service `.proto` file that imports another `.proto` file in a subdirectory, pick the service `.proto` file, save, select an RPC method in the request editor, and execute the gRPC call successfully.

**Acceptance Scenarios**:

1. **Given** a gRPC request with no collection-level import paths configured, **When** the user opens the Proto File dialog and chooses to select a folder, **Then** a folder picker opens and, upon selecting a folder, the dialog displays the selected folder path and lists all `.proto` files found within that folder and its subdirectories.
2. **Given** a selected proto folder containing a service `.proto` file that imports a shared `.proto` file from the same folder or a subdirectory, **When** the user selects that service `.proto` file and saves the dialog, **Then** the target `.proto` file compiles without import errors, the dialog closes, and the gRPC request editor's method selector lists the services and RPC methods defined in that file.
3. **Given** a gRPC request configured with a proto folder and target `.proto` file, **When** the user selects an RPC method and sends the request to a gRPC server, **Then** the request executes successfully using the resolved imported message types.
4. **Given** a gRPC request configured with a proto folder whose target `.proto` file also imports types defined in an enabled collection-level import path or collection proto file, **When** the user saves the dialog or executes the request, **Then** imports are resolved across both the selected request folder and the enabled collection-wide proto files and import paths.
5. **Given** a saved gRPC request that uses a proto folder and target `.proto` file, **When** the user closes and reopens the request or application, **Then** both the selected folder and the chosen target `.proto` file remain associated with the request, and the available RPC methods load automatically without requiring re-selection.

---

### User Story 2 - Preserve Single-File and Collection-Level Proto Selection (Priority: P1)

A user who already works with standalone `.proto` files (with no external imports or using collection-level import paths) or who selects pre-configured collection-level proto files expects their existing workflows to continue working smoothly alongside the new folder-selection option in the Proto File dialog.

**Why this priority**: Existing gRPC requests and simpler single-file workflows must remain backward-compatible and frictionless.

**Independent Test**: Open the Proto File dialog on a gRPC request, verify the user can still select a single `.proto` file from disk or pick an existing collection-level proto file, and verify previously saved single-file gRPC requests still load and execute without changes.

**Acceptance Scenarios**:

1. **Given** the Proto File dialog, **When** the user chooses to select a single `.proto` file from disk instead of a folder, **Then** the file is selected, parsed using enabled collection-wide proto files and import paths, and its methods populate the request editor.
2. **Given** a collection with configured collection-level proto files, **When** the user opens the Proto File dialog and selects one of the collection-level proto files, **Then** the request associates that collection-level proto file and populates its methods.
3. **Given** a gRPC request previously saved with a single `.proto` file (before folder selection existed), **When** opened, **Then** it continues to display, parse, and execute without requiring migration or manual re-configuration.
4. **Given** a gRPC request currently configured with a proto folder, **When** the user switches to a single `.proto` file or a collection-level proto file in the dialog and saves, **Then** the previous folder selection is cleanly replaced by the new selection.

---

### User Story 3 - Manage Git Sync Exclusively Inside the Proto File Dialog Including Full Folder Sync (Priority: P2)

A user collaborating on a Git-synced collection wants a cleaner gRPC request toolbar and a single, cohesive place to configure both the proto source and whether it is synced to Git. The "Sync to Git" toggle is removed from the main gRPC request bar and lives solely inside the Proto File dialog (shown only when the collection uses Git storage). When the user enables "Sync to Git" for a single `.proto` file, that file is synced to the collection's Git repository. When the user enables "Sync to Git" for a selected proto folder, all `.proto` files within that folder and all of its subfolders are synced into the collection's Git repository while preserving their relative directory structure, so teammates who pull the collection can immediately parse and run the request without local file setup.

**Why this priority**: Consolidating the Git sync toggle into the Proto File dialog declutters the request toolbar and ensures Git-synced requests that depend on multi-file proto folders remain portable across team members.

**Independent Test**: In a Git-synced collection, verify that no "Sync to Git" toggle appears in the gRPC request top bar, open the Proto File dialog, select a folder containing `.proto` files across nested subfolders, enable "Sync to Git", save the request, and verify that all `.proto` files (preserving subfolder paths) are synced into the collection's Git repository while the request stores portable relative paths and compiles cleanly from the synced copy.

**Acceptance Scenarios**:

1. **Given** a gRPC request in a Git-synced collection with a proto file or folder configured, **When** viewing the gRPC request editor top bar, **Then** no "Sync to Git" toggle is shown in the request bar.
2. **Given** a gRPC request in a Git-synced collection, **When** the user opens the Proto File dialog, **Then** the "Sync to Git" toggle is displayed inside the dialog and reflects the request's current sync state.
3. **Given** a gRPC request in a local (non-Git) collection, **When** the user opens the Proto File dialog, **Then** the "Sync to Git" toggle is not displayed.
4. **Given** a Git-synced collection and a proto folder selected in the Proto File dialog with "Sync to Git" enabled, **When** the request is saved, **Then** every `.proto` file in the selected folder and its nested subfolders is copied into the collection's Git repository preserving its relative subdirectory structure, and the request references portable relative paths for both the synced folder and the chosen target `.proto` file.
5. **Given** a Git-synced collection where a request's proto folder contains both `.proto` files and non-`.proto` files (such as documentation or build artifacts), **When** synced to Git, **Then** only `.proto` files (and their containing relative directory structure) are copied to the Git repository.
6. **Given** a teammate who opens the Git-synced collection on another machine where the original local proto folder does not exist, **When** they open and execute the gRPC request, **Then** the request resolves the synced proto folder and target `.proto` file from the repository and executes successfully.

---

### Edge Cases

- **Selected folder contains no `.proto` files**: When a user selects a folder (including subdirectories) that contains zero `.proto` files, the dialog displays a clear empty-state message explaining that no `.proto` files were found and prevents saving until a valid `.proto` file is selected.
- **Selected folder contains many `.proto` files or deep subdirectories**: The `.proto` file list within the dialog is scrollable and displays relative paths from the selected folder root so users can distinguish identically named files in different subfolders (e.g., `v1/service.proto` vs. `v2/service.proto`) and optionally filter or quickly locate the desired file.
- **Target `.proto` file has no `service` definitions (messages/enums only)**: If the user picks a `.proto` file from the folder that only defines shared messages or enums (such as `common/metadata.proto`), parsing succeeds with zero available RPC methods and the request editor clearly indicates that no RPC methods were found in the selected file.
- **Imports prefixed with the selected folder's own name**: If a user selects a folder such as `.../protobuf` containing `greeter.proto` and `common/metadata.proto`, and `greeter.proto` writes `import "protobuf/common/metadata.proto";`, import resolution also checks the folder's parent or handles the folder-name prefix cleanly—or if the user selects the parent `.../resources` folder, `protobuf/greeter.proto` and `protobuf/common/metadata.proto` both appear and resolve directly against `resources`.
- **Missing or moved proto folder / target file on disk**: If a previously saved local (non-Git-synced) proto folder or target `.proto` file is later moved or deleted on disk, the request displays a clear, actionable error message instead of crashing, allowing the user to reopen the Proto File dialog and choose a new folder or file.
- **Disabling "Sync to Git" or switching synced folder**: If a user unchecks "Sync to Git" or replaces a previously synced proto folder with a different folder, the updated selection is reflected on save without leaving stale conflicting proto files in the request's synced folder.
- **Selecting a collection-level proto file while Git storage is enabled**: When the user selects an existing collection-level proto file in the dialog, the request-level "Sync to Git" toggle does not duplicate the collection-level file into request-level Git storage (its Git sync lifecycle continues to be governed by the collection's Protobuf settings).
- **Unsaved changes in the Proto File dialog**: Changing the selected folder, changing the chosen target `.proto` file within the folder, or toggling "Sync to Git" marks the dialog as having unsaved changes so accidental dismissal prompts the user before discarding.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The gRPC request Proto File dialog MUST allow users to select either a single `.proto` file from disk, an entire folder from disk, or an existing collection-level proto file.
- **FR-002**: When a user selects a folder in the Proto File dialog, the system MUST recursively scan that folder and its subdirectories for `.proto` files and present them for selection with their paths relative to the selected folder.
- **FR-003**: When a folder is selected in the Proto File dialog, the user MUST be able to choose one specific `.proto` file from the discovered list as the request's target service definition before saving.
- **FR-004**: When parsing a target `.proto` file or executing a gRPC request configured with a proto folder, the system MUST resolve `import` statements against the selected folder hierarchy in addition to any enabled collection-wide import paths and collection-wide proto files.
- **FR-005**: When a user selects a single `.proto` file (without folder mode), the system MUST continue to resolve `import` statements against the file's directory and any enabled collection-wide import paths and collection-wide proto files, preserving full backward compatibility with existing requests.
- **FR-006**: The system MUST persist both the selected proto folder (when folder mode is used) and the selected target `.proto` file as part of the gRPC request configuration so that reopening or reloading the request restores the exact proto selection and available RPC methods.
- **FR-007**: The gRPC request editor top bar MUST NOT display a "Sync to Git" toggle; the request-level "Sync to Git" toggle MUST be located exclusively inside the Proto File dialog.
- **FR-008**: The "Sync to Git" toggle inside the Proto File dialog MUST only be visible when the request's parent collection is configured for Git storage, and MUST apply to request-level local file or folder selections (not collection-level proto references).
- **FR-009**: When "Sync to Git" is enabled for a request using a selected proto folder, saving the request MUST copy all `.proto` files from the selected folder and all of its subfolders into the collection's Git repository while preserving their relative subdirectory structure, excluding non-`.proto` files.
- **FR-010**: When a proto folder is synced to Git, the saved request configuration MUST store portable relative paths for both the synced proto folder and the target `.proto` file so the request parses and executes identically on any machine that clones or pulls the collection.
- **FR-011**: When a synced proto folder is replaced by a different folder or re-synced, the system MUST refresh the request's synced folder contents in the Git repository so removed or stale `.proto` files from the previous folder do not remain behind.
- **FR-012**: The Proto File dialog MUST display clear validation and error feedback when a selected folder contains no `.proto` files, when no target `.proto` file has been picked from a selected folder, or when compiling the target `.proto` file fails.
- **FR-013**: Changing the proto folder, changing the selected `.proto` file within the folder, or changing the "Sync to Git" state in the Proto File dialog MUST be tracked as unsaved dialog changes and require confirmation before discarding on close/cancel.

### Key Entities *(include if feature involves data)*

- **gRPC Request Proto Selection**: Represents how a gRPC request locates its schema. Contains the target `.proto` file path (required for execution), an optional proto root folder path (present when folder mode is used), and a request-level Git sync preference indicating whether the selected local file or folder is copied into the collection's Git repository.
- **Discovered Proto Entry**: Represents a `.proto` file found within a user-selected proto folder, identified by its display filename and its relative path from the selected folder root.
- **Collection Protobuf Configuration (existing)**: Represents collection-wide shared proto files and enabled/disabled import paths that supplement a request's own proto selection during import resolution.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can configure a gRPC request whose `.proto` file imports sibling or subdirectory `.proto` files entirely within the request's Proto File dialog in under 30 seconds without opening Collection settings.
- **SC-002**: 100% of valid multi-file proto folders (including nested subdirectory imports such as `greeter.proto` importing `common/metadata.proto` or `protobuf/common/metadata.proto`) compile and populate selectable RPC methods on the first save when the appropriate folder and target `.proto` file are selected.
- **SC-003**: 100% of existing single-file and collection-level gRPC requests continue to load, parse, and execute with zero regressions or manual reconfiguration.
- **SC-004**: In Git-synced collections, 100% of `.proto` files across all nested subdirectories of a synced proto folder are preserved with intact relative paths in the Git repository, enabling a newly cloned collection on another machine to parse and execute the gRPC request with zero manual path fixes.
- **SC-005**: The gRPC request editor top bar has zero "Sync to Git" controls, consolidating all proto source and Git sync controls into the single Proto File dialog.

## Assumptions

- **Single target proto per request**: Even when an entire folder is selected, a single gRPC request selects one target `.proto` file from that folder to populate its RPC method dropdown; all other `.proto` files in the folder serve as import resolution context.
- **Syncing only `.proto` files for folders**: When syncing a selected folder to Git, only `.proto` files (and their relative directory structure) are copied into the Git repository to avoid inadvertently committing unrelated files (e.g., compiled binaries, IDE files, or test fixtures) when a user selects a broader project directory.
- **Collection-level proto files remain managed at collection level**: Selecting a collection-level proto file in the request's Proto File dialog references the collection's configuration; its Git sync behavior remains controlled in the Collection Editor rather than duplicated per request.
- **Default folder-name import fallback**: When a user selects a folder whose directory name matches the leading path segment of an import (e.g., selecting `.../protobuf` when a file inside imports `"protobuf/common/metadata.proto"`), the system can resolve both imports relative to the selected folder itself and imports prefixed with the selected folder's own name, while still recommending selecting the root import directory.
- **No automatic background file watching required for Git re-sync**: Re-syncing updated local `.proto` files to Git occurs when the user saves the request or updates the proto selection in the dialog (matching the existing single-file Git sync behavior).
