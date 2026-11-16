# Phase 0: Technical Research & Architectural Decisions

## Overview

This document resolves technical choices, patterns, and integration details for introducing collection-level Protobuf configuration in Clapi, conforming to the OpenCollection v1.0.0 schema.

---

### Decision 1: OpenCollection Schema Mapping for `CollectionConfig.protobuf`

- **Decision**: Add `protobuf: CollectionProtobufConfig? = null` to `CollectionConfig` in `core-model` adhering strictly to OpenCollection v1.0.0 JSON schema.
- **Rationale**:
  - OpenCollection v1.0.0 defines:
    ```json
    "Protobuf": {
      "type": "object",
      "properties": {
        "protoFiles": {
          "type": "array",
          "items": { "$ref": "#/$defs/ProtoFileItem" }
        },
        "importPaths": {
          "type": "array",
          "items": { "$ref": "#/$defs/ProtoFileImportPath" }
        }
      }
    }
    ```
  - `ProtoFile` has `type: "file"` (const) and `path: string`.
  - `ProtoFileImportPath` has `path: string` and optional `disabled: boolean`.
  - Storing path references without copying file contents preserves user workflows and complies with requirement FR-004.
- **Alternatives Considered**:
  - *Storing file contents inline*: Rejected because it duplicates files, violates OpenCollection specification, and desynchronizes edits made externally in IDEs.
  - *Custom extension property*: Rejected because OpenCollection already natively defines `CollectionConfig.protobuf`.

---

### Decision 2: Sidebar Tree Header and Collection Editor Tab

- **Decision**:
  - Rename the top-level section header in `Sidebar.kt` from "Folders" (`Strings.folders`) to "Collection" (`Strings.collection`), and switch its icon from `Icons.Default.FolderOpen` to `Icons.Default.Article`.
  - Extend `RequestKind` enum in `McpModels.kt` with `COLLECTION`.
  - When the user left-clicks the "Collection" header, call `state.openCollection()`:
    - If a tab with `kind == RequestKind.COLLECTION` already exists in `state.openTabs`, set `activeTabIndex` to that tab.
    - Otherwise, append a new `RequestTabState(id = "collection_settings", name = "Collection", kind = RequestKind.COLLECTION)` and focus it.
  - In `RequestTabsBar.kt`, render `Icons.Default.Article` for `tab.kind == RequestKind.COLLECTION`.
  - In `MainScreen.kt`, when `activeTab.kind == RequestKind.COLLECTION`, render `CollectionEditor(state = state, onSave = { persistWorkspaceAsync() })`.
- **Rationale**:
  - Reusing the tab infrastructure (`RequestTabState` + `RequestKind`) allows the Collection settings to participate naturally in tab navigation, closing, keyboard shortcuts, and split-pane layouts alongside request and folder tabs.
- **Alternatives Considered**:
  - *Separate modal dialog for collection settings*: Rejected because the specification specifically requires a CollectionEditor tab similar to `FolderEditor`.

---

### Decision 3: CollectionEditor Component Design

- **Decision**:
  - Implement `CollectionEditor` with an editor tab bar (currently displaying a single tab "Protobuf").
  - The "Protobuf" tab contains two main sections:
    1. **Proto Files**: Displays a list/table of collection `protoFiles`.
       - Each row shows the file name, directory path, an "Open File" button (launching system viewer), and a remove button.
       - "Add Proto Files" button triggers multi-file selection via `pickProtoFiles`.
    2. **Import Paths**: Displays a list/table of `importPaths`.
       - Each row includes an enable/disable checkbox toggle, path display, an "Open File" button, and a remove button.
       - "Add Import Paths" button triggers file selection (multi-file) via `pickProtoFiles` (or directory selection).
- **External File Viewer Integration**:
  - Provide platform-safe `openFileInDefaultApp(filePath: String)` in `PlatformApi`.
  - On Desktop JVM, utilize `java.awt.Desktop.getDesktop().open(File(filePath))` with a fallback to `ProcessBuilder` (`xdg-open` on Linux, `open` on macOS, `explorer` on Windows) inside a safe `runCatching` block to avoid application crashes if no default handler is configured.

---

### Decision 4: Proto File Selection in gRPC Request Editor

- **Decision**:
  - Enhance `SelectProtoFileDialog` to support choosing between collection-level `protoFiles` and picking a local file from disk manually.
  - When collection-level `protoFiles` exist:
    - Display a "Collection Proto Files" section with a dropdown or selectable list showing the collection-configured proto files.
    - Selecting an entry populates the target path.
  - Retain the "Select from computer" action button for manual file picking.
  - Saving sets `tab.grpcProtoFilePath` and re-triggers proto compilation and method discovery as before.
- **Rationale**:
  - Meets requirement FR-010 while preserving 100% backward compatibility for existing requests and ad-hoc proto files.

---

### Decision 5: Import Path Resolution in ProtocJarParser and Execution Pipeline

- **Decision**:
  - In `ProtocJarParser.kt`, enhance include directories resolution:
    - For any path in `includePaths`:
      - If it is a directory: include `-I$dir`.
      - If it is a file (`f.isFile`): include `f.parentFile` and walk ancestor directories (up to repository/drive boundaries) so that relative import paths (such as `import "protobuf/common/metadata.proto";`) resolve cleanly against their root.
  - Only provide **enabled** import paths (`it.disabled != true`) from `state.collectionImportPaths` to `ProtocJarParser`.
  - Extend `GrpcCallRequest` with `includePaths: List<String> = emptyList()`.
  - Update `RequestExecutionService.executeGrpc(...)` and `NettyDynamicGrpcClient.executeUnary(...)` to pass through `includePaths`.
  - In `RequestTabState` or during editor method parsing, pass enabled collection import paths when discovering methods for a selected proto file.
- **Rationale**:
  - Developers frequently import proto files using root-relative paths like `protobuf/common/metadata.proto` while selecting the file directly from a file picker. Ensuring ancestor directories are included in `-I` guarantees compilation success without requiring manual path fiddling.

---

### Decision 6: End-to-End QA Test Configuration

- **Decision**:
  - Update `DummyApiServer.kt`:
    - Ensure `metadata.proto` is available to the test server when compiling `greeter.proto` (extracting `metadata.proto` into the temp directory alongside `greeter.proto` under `protobuf/common/metadata.proto` or passing test resources root as `includePaths`).
    - Adjust `SayHello` and `SayHelloAgain` RPC response handlers to echo or set `ack_metadata`.
  - In `GrpcApiE2ETest.kt`:
    - Update `GrpcRequestItem` execution tests to configure collection-level `importPaths` pointing to `protobuf/common/metadata.proto` (or its resource directory).
    - Add explicit test assertions verifying:
      1. Parsing and executing with enabled collection `importPaths` succeeds.
      2. Disabling the collection import path causes expected compilation failure.
      3. Requests selecting collection-level proto files execute successfully.
