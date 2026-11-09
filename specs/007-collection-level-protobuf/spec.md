# Feature Specification: Collection-Level Protobuf Management

**Feature Branch**: `007-collection-level-protobuf`

**Created**: 2026-11-09

**Status**: Draft

**Input**: User description: "It should now be possible to add proto files on collection level - For collection-level proto files we use OpenCollection's `CollectionConfig.protobuf` (see @opencollection_v1.0.0.json) to store proto files that are available in any folder/request of the collection - `protoFiles` will be proto files which the user can select in a gRPC Request's Editor proto file selection as an alternative to having to provide a proto file for the request - `importPaths` will be proto files solely used to resolve imports when executing a grpc request - the proto file configs are only paths to link to existing proto files, the files itself should not be copied to Clapi's internal storage or anywhere else - To add/remove collection-level proto files we need to introduce a collection editor: - In the sidebar rename the top-level "Folders" to "Collection" and also use material icon "Article" instead of the folder icon - If the user left clicks on the top-level "Collection", a tab "Collection" should open with a new CollectionEditor (similar to FolderEditor) - Currently, the only tab should be "Protobuf" in which the user can add/remove the collection-level `protoFiles` and `importPaths` (for `importPaths` also includes disabled toggles). Similar to in GRPCRequest Editor the user can choose .proto files from his computer to be added (multiple files at once possible) - For every proto file include a little open file button, so the user can quickly view the proto file externally - In grpcRequest Editor in the proto file selection dialog, the user can now either choose a proto file from his computer manually as before, or select one of the collection-level `protoFiles` - Ensure that ProtocJarParser properly includes collection-level defined protobuf `importPaths` to support service proto files that use imported types - ensure qa-tests grpc test is properly adjusted to also test this. I added @qa-tests/src/test/resources/protobuf/common/metadata.proto which can be used as a test for `importPaths`, in the greeter.proto I added the import statement and usage of the metadata field, so the tests can be properly adjusted to that example"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Collection Root Navigation and Collection Settings Editor (Priority: P1)

Users managing API collections need an intuitive entry point to configure collection-wide settings and assets. In the primary sidebar tree, the top-level header/root currently designated as "Folders" is renamed to "Collection" and displays the Material "Article" icon rather than a folder icon. When the user left-clicks the "Collection" root item, a dedicated editor tab titled "Collection" opens in the main workspace editor area (alongside existing request or folder tabs). Within this Collection Editor, the user sees a "Protobuf" configuration tab where collection-wide Protocol Buffer definitions and import paths are managed.

**Why this priority**: Navigating to collection-level settings via the top-level sidebar node is the foundational entry point required to view, configure, and maintain collection-wide resources.

**Independent Test**: Click on the top-level "Collection" item in the sidebar, observe that a "Collection" tab opens with the Article icon, and verify that the editor displays the "Protobuf" tab for configuration.

**Acceptance Scenarios**:

1. **Given** the application sidebar displaying the collection tree, **When** rendered, **Then** the top-level root item displays the title "Collection" with the Material "Article" icon instead of "Folders" with a folder icon.
2. **Given** the "Collection" root item in the sidebar, **When** the user left-clicks on it, **Then** an editor tab named "Collection" opens (or gains focus if already open) in the central editor tab bar.
3. **Given** the opened Collection Editor, **When** viewed, **Then** it presents a tabbed layout currently containing a "Protobuf" tab.
4. **Given** multiple open tabs in the editor tab bar, **When** switching between a request tab, a folder tab, and the Collection tab, **Then** the Collection tab preserves its state and displays appropriately.

---

### User Story 2 - Manage Collection Proto Files and Import Paths (Priority: P1)

Users working with gRPC services across multiple requests need to link common `.proto` files once at the collection level rather than attaching redundant file references to every individual request. Additionally, proto definitions frequently depend on external or shared imports (e.g., common error formats, shared metadata, or sub-messages). In the Collection Editor's "Protobuf" tab, users can configure two distinct lists:
1. **Proto Files (`protoFiles`)**: Service definitions available for selection in any gRPC request across the collection. Users can add one or multiple `.proto` files from their computer via a file picker, view their paths, remove entries, and click an "Open File" button next to any item to immediately view or edit the file in their system's default external application.
2. **Import Paths (`importPaths`)**: Shared proto files or search directories used strictly for resolving `import` statements during protobuf compilation and request execution. Users can add one or multiple `.proto` files or directories, toggle individual import paths enabled or disabled, remove entries, and click an "Open File" button to view individual files externally.

All linked proto paths refer directly to existing locations on the user's filesystem; files are not duplicated or copied into internal workspace storage.

**Why this priority**: Managing reusable proto files and import resolution paths at the collection root is essential for scalable gRPC workflows and complex proto dependency hierarchies.

**Independent Test**: In the Protobuf tab, add multiple `.proto` files to `protoFiles`, add an import file to `importPaths`, toggle the import path disabled and back to enabled, click the "Open File" button on an entry, and verify that all configuration changes are retained in the collection configuration.

**Acceptance Scenarios**:

1. **Given** the Collection Editor's "Protobuf" tab, **When** viewed, **Then** it displays two distinct management sections: "Proto Files" and "Import Paths".
2. **Given** the "Proto Files" section, **When** the user clicks the add action, **Then** a file chooser opens allowing the user to select one or multiple `.proto` files from disk, appending them to the collection's `protoFiles` list as file links without copying file contents.
3. **Given** an entry in the "Proto Files" or "Import Paths" list, **When** the user clicks its remove action, **Then** the item is removed from the collection configuration.
4. **Given** an entry in the "Proto Files" or "Import Paths" list, **When** the user clicks the "Open File" action button, **Then** the system requests the operating system to open that file using the default external application.
5. **Given** the "Import Paths" section, **When** an import path is listed, **Then** an enable/disable toggle is displayed next to it, allowing the user to exclude or include the path without deleting it.
6. **Given** changes made to collection proto files or import paths, **When** the collection is saved, **Then** changes persist in the collection's configuration file under `config.protobuf`.

---

### User Story 3 - Select Collection Proto Files in gRPC Request Editor (Priority: P1)

When configuring a gRPC request, users need to associate a proto file to discover available services and RPC methods. In the gRPC Request Editor's proto file selection dialog, users can now choose between:
1. Selecting an existing collection-level proto file from a list/dropdown of configured `protoFiles`.
2. Selecting a manual proto file directly from their local computer (existing behavior).

When a collection-level proto file is selected, the request uses that reference, and the available services and methods defined in that proto file are immediately parsed and listed in the RPC method dropdown.

**Why this priority**: Enables requests to consume collection-level proto definitions, eliminating repetitive manual file selection per request and keeping proto definitions synchronized.

**Independent Test**: In a collection with collection-level proto files configured, create or open a gRPC request, open the proto file selection dialog, choose a collection-level proto file, save the selection, and verify that the services and methods from that file are available in the method selector dropdown.

**Acceptance Scenarios**:

1. **Given** a gRPC request editor, **When** the user opens the "Select Proto File" dialog, **Then** options are presented to either select from collection-level proto files or choose a local file from disk manually.
2. **Given** collection-level proto files configured in the active collection, **When** opening the proto file dialog, **Then** all collection-level proto files appear with their filenames and paths for selection.
3. **Given** a selected collection-level proto file, **When** the user saves the dialog selection, **Then** the gRPC request associates the selected proto file reference and triggers method discovery.
4. **Given** a user who prefers a custom local file, **When** selecting a file manually, **Then** manual selection functions seamlessly as an alternative to collection-level files.

---

### User Story 4 - Resolve Protobuf Imports During Parsing and Execution (Priority: P1)

Proto files often contain `import` statements referencing shared messages, enums, or common types (e.g., `import "protobuf/common/metadata.proto";`). When parsing a service proto file or executing a gRPC call, the protobuf compiler/parser must resolve all imported definitions using the configured collection-level `importPaths`. Only enabled import paths are supplied to the parser. If an import path is disabled, the parser does not include it, causing compilation to fail if that dependency was required.

**Why this priority**: Without import path resolution, proto files that utilize shared message schemas or modular type architectures cannot be compiled or executed.

**Independent Test**: Configure an enabled collection-level import path pointing to a shared proto directory or file, parse a service proto file that imports a message from that path, and verify that parsing succeeds without unresolved reference errors.

**Acceptance Scenarios**:

1. **Given** an enabled collection-level import path, **When** a service proto file with an `import` statement is parsed, **Then** the parser resolves the imported proto file and discovers all RPC methods and nested message types without error.
2. **Given** a collection-level import path that has been toggled to disabled, **When** parsing a service proto file requiring that import, **Then** the disabled path is not provided to the parser, resulting in a descriptive compilation error indicating missing imports.
3. **Given** a gRPC request targeting an RPC with imported types, **When** the user sends the request, **Then** execution resolves the message definitions correctly and roundtrips the call to the server successfully.

---

### User Story 5 - Interoperable Persistence in OpenCollection Standard Format (Priority: P2)

Collections containing collection-level protobuf configurations must be completely portable and interoperable according to the OpenCollection v1.0.0 specification (`CollectionConfig.protobuf`). When saved to disk or exported, collection protobuf files and import paths are written into `config.protobuf`. When an existing collection is loaded or imported, Clapi parses `config.protobuf` and populates the Collection Editor and request selection options.

**Why this priority**: Guarantees zero data loss, backwards and forwards compatibility, and compliance with the OpenCollection standard.

**Independent Test**: Configure collection proto files and import paths, export/save the collection, re-open the collection in Clapi, and assert that all proto files, import paths, and enabled/disabled states match the original configuration.

**Acceptance Scenarios**:

1. **Given** a collection with configured proto files and import paths, **When** saved to disk, **Then** the configuration matches OpenCollection v1.0.0 schema under `config.protobuf` (`protoFiles` array with `type: "file"` and `path`, and `importPaths` array with `path` and `disabled`).
2. **Given** an OpenCollection document containing `config.protobuf`, **When** opened in Clapi, **Then** the collection-level proto files and import paths are loaded accurately.
3. **Given** a collection without a `protobuf` configuration block, **When** opened, **Then** Clapi handles it gracefully with empty proto file and import path lists without errors.

---

### Edge Cases

- **Missing Linked File**: What happens if a linked collection proto file or import path is moved, deleted, or inaccessible on disk? The UI displays a clear indicator (e.g., warning badge or error message) when attempting to view or compile the file, informing the user that the file path cannot be resolved.
- **Multiple Files with Identical Names**: What happens when two proto files added to collection `protoFiles` have the same filename but different paths? The UI clearly differentiates them by displaying their parent directory or full path alongside the name.
- **Circular or Redundant Import Paths**: How does the system handle redundant or duplicate import directories? The parser normalizes and deduplicates import paths prior to invocation to avoid compiler flags conflicts.
- **Special Characters and Spaces in Paths**: Paths containing spaces, non-ASCII characters, or operating system-specific separators are handled correctly across Linux, macOS, and Windows without path truncation or parsing failure.
- **External File Opening Failure**: If the operating system cannot launch a default viewer for a `.proto` file (e.g., no default text editor associated), the application catches the event and alerts the user with an actionable error rather than crashing.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST display the top-level collection tree root in the sidebar with the title "Collection" and the Material "Article" icon.
- **FR-002**: System MUST open a "Collection" editor tab displaying collection-level settings when the user clicks the "Collection" root in the sidebar.
- **FR-003**: System MUST provide a "Protobuf" tab within the Collection Editor for managing collection-level Protocol Buffer configurations.
- **FR-004**: Users MUST be able to add one or multiple `.proto` files from disk to the collection-level `protoFiles` list without copying the files into Clapi's internal storage.
- **FR-005**: Users MUST be able to remove any proto file entry from the `protoFiles` list.
- **FR-006**: Users MUST be able to add one or multiple `.proto` files or directories to the collection-level `importPaths` list.
- **FR-007**: Users MUST be able to toggle each collection-level import path between enabled and disabled states.
- **FR-008**: Users MUST be able to remove any import path entry from the `importPaths` list.
- **FR-009**: System MUST provide an "Open File" button for each entry in `protoFiles` and `importPaths` that launches the file in the user's default external application.
- **FR-010**: System MUST allow users in the gRPC Request Editor proto file dialog to select either a collection-level proto file from `protoFiles` or a local file from disk.
- **FR-011**: System MUST supply all enabled collection-level `importPaths` to the protobuf parser during proto discovery and gRPC request execution.
- **FR-012**: System MUST exclude disabled collection-level `importPaths` from compiler flags during proto parsing and execution.
- **FR-013**: System MUST persist collection-level protobuf settings strictly conforming to OpenCollection v1.0.0 `CollectionConfig.protobuf` schema (`protoFiles` and `importPaths`).
- **FR-014**: System MUST restore all collection-level `protoFiles` and `importPaths` (including enabled/disabled flags) when loading or reopening a collection.

### Key Entities

- **CollectionConfig.protobuf**: The collection-wide Protocol Buffer configuration object containing `protoFiles` and `importPaths`.
- **ProtoFileItem**: A reference to a proto file storing its type (`file`) and filesystem path (`path`).
- **ProtoFileImportPath**: An import path configuration entry containing the filesystem path (`path`) and an optional disabled flag (`disabled`).
- **CollectionEditor**: The workspace editor component managing collection-level properties and configurations across dedicated tabs (beginning with "Protobuf").

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can access collection protobuf settings from the sidebar and open the Collection Editor in a single click.
- **SC-002**: Users can link multiple `.proto` files to the collection in a single file-selection action.
- **SC-003**: 100% of service proto files relying on imported schemas compile and execute successfully when the corresponding import paths are enabled at the collection level.
- **SC-004**: Changing an import path's enabled state takes effect immediately on the subsequent proto parse or request execution without requiring an application restart.
- **SC-005**: 100% of collection protobuf configuration settings roundtrip through OpenCollection persistence without data degradation or file duplication.

## Assumptions

- Users have read access to the `.proto` files on their local filesystem that they link into the collection.
- Operating system desktop integration is available for the "Open File" action (e.g. system default text editor or IDE).
- If no collection-level proto files are defined, the gRPC Request Editor continues to offer manual local file selection as default.
- Protobuf import paths can represent either individual `.proto` files or directories containing referenced proto hierarchies.
