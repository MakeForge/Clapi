# Research: gRPC Request Proto Folder Workflow

**Feature**: `032-grpc-proto-workflow`  
**Date**: 2026-11-20

## 1. Root Cause of Missing Import Errors on Single-File Selection

- **Finding**: In `core-network/.../ProtocJarParser.kt`, `parseProtoFile(protoFilePath, includePaths)` copies only the selected `.proto` file and its immediate same-directory `.proto` siblings into a temporary `stagingDir` and passes `-I<stagingDir>` plus `-I<dir>` for each path in `includePaths` (along with up to 10 ancestor directories per include path via `normalizeIncludeDirs`).
- **Why `greeter.proto` failed**: When a user selects `qa-tests/src/test/resources/protobuf/greeter.proto` without collection-level `importPaths`, `includePaths` is empty. Only `stagingDir` is passed to `protoc`, so `import "protobuf/common/metadata.proto";` cannot be resolved because neither `common/` nor the parent `resources/` directory is included in `-I` flags.
- **Decision**: Support an explicit `protoFolderPath` on each gRPC request and supply the resolved `protoFolderPath`, enabled collection `importPaths`, AND collection `protoFiles` to `includePaths` during both parsing (`GrpcRequestEditor.kt`) and execution (`resolveGrpcCallRequest`).
- **Rationale**: Passing `protoFolderPath` into `includePaths` leverages `ProtocJarParser.normalizeIncludeDirs`, which adds `protoFolderPath` and its ancestor directories. This means:
  - Selecting `.../resources` and picking `protobuf/greeter.proto` resolves `import "protobuf/common/metadata.proto";` directly against `.../resources`.
  - Selecting `.../resources/protobuf` and picking `greeter.proto` resolves both `import "common/metadata.proto";` (against `.../protobuf`) and `import "protobuf/common/metadata.proto";` (against `.../resources`).
  - Adding collection `protoFiles` alongside enabled `collectionImportPaths` satisfies the requirement that imports resolve against the selected folder and any additional collection-wide proto files/import paths.
- **Alternatives considered**:
  - *Always adding `protoFile.parentFile` and ancestors in `ProtocJarParser` without folder selection*: Would silently scan outside the user's chosen scope and would not solve Git syncing of multi-file folder hierarchies because single-file sync only copies one file.

## 2. Persistence of `protoFolderPath` in OpenCollection Format

- **Finding**: OpenCollection v1.0.0 (`opencollection_v1.0.0.json`) defines `GrpcRequestDetails.protoFilePath` as a string, with no standard `protoFolderPath` field. Clapi already stores Clapi-specific request metadata (such as `protoSyncToGit`, `bodyContents`, `headerEntries`, `requestRef`) in `extensions["clapi"]` via `OpenCollectionModelMapping.kt`.
- **Decision**: Persist `protoFolderPath` in `extensions["clapi"]["protoFolderPath"]` alongside `protoSyncToGit`, while continuing to store the target `.proto` file in `GrpcRequestDetails.protoFilePath` (and `extensions["clapi"]["protoFilePath"]`). Also persist `protoFolderPath` in `TabsRepository` for open tab restoration.
- **Rationale**: Preserves 100% OpenCollection v1.0.0 schema compliance and full backward compatibility with existing gRPC requests (which have `protoFolderPath = null`).
- **Alternatives considered**:
  - *Encoding folder + file into a single delimited string in `protoFilePath`*: Breaks OpenCollection interoperability and breaks existing external consumers expecting a valid file path in `protoFilePath`.

## 3. Folder Picker & Recursive `.proto` Discovery on Desktop

- **Finding**: `ui-shared/src/desktopMain/.../PlatformApi.desktop.kt` already imports `io.github.vinceglb.filekit.dialogs.openDirectoryPicker` and provides `pickProtoFile` / `pickProtoFiles` via `FileKit`.
- **Decision**: Add `expect fun pickProtoDirectory(onResult: (dirPath: String) -> Unit)` using `FileKit.openDirectoryPicker` and `expect fun listProtoFilesInDirectory(dirPath: String): List<DiscoveredProtoFile>` in `PlatformApi.kt` / `PlatformApi.desktop.kt`.
- **Rationale**: Reuses the existing FileKit desktop integration and remembered chooser directory, while keeping `ui-shared/commonMain` cleanly separated from `java.io.File`. Providing an optional `onListProtoFiles` parameter on `SelectProtoFileDialog` keeps Compose UI tests fast and deterministic.
- **Alternatives considered**:
  - *Using `JFileChooser` directly*: Inconsistent with Clapi's native FileKit dialogs used across the rest of the app.

## 4. Git Sync for Request-Level Proto Folders

- **Finding**: `ProtoPathResolver.syncRequestProtoFile` copies a single `.proto` file to `<collectionDir>/protobuf/requests/<safeReqId>/<fileName>` and returns `./protobuf/requests/<safeReqId>/<fileName>`. `CollectionStorageDriver.desktop.kt` provides `copyFileOrDirectory`, which copies *all* files without filtering and without cleaning stale files.
- **Decision**:
  1. Add `expect fun copyProtoFilesFromDirectory(sourceDir: String, destinationDir: String): Boolean` in `core-storage` (`CollectionStorageDriver.kt` & `.desktop.kt`):
     - If `sourceDir.canonicalFile == destinationDir.canonicalFile`, return `true` immediately (idempotent re-save of an already-synced folder).
     - Otherwise, delete any existing `destinationDir` recursively to avoid stale `.proto` files (FR-011), then recursively copy only `.proto` files (case-insensitive) from `sourceDir` to `destinationDir` while preserving relative subdirectory structure (FR-009).
  2. Add `ProtoPathResolver.syncRequestProtoFolder(sourceFolderPath, sourceProtoFilePath, requestId, collectionDir): SyncedProtoFolderResult`:
     - Preserves the selected folder's directory name at `./protobuf/requests/<safeReqId>/<folderName>` so folder-name-prefixed imports (e.g. `protobuf/common/metadata.proto`) continue to resolve after Git sync on another machine.
     - Computes the target `.proto` file's path relative to `sourceFolderPath` and returns both portable relative paths (`folderRelPath` and `protoFileRelPath`).
- **Rationale**: Prevents accidental commits of non-`.proto` files (e.g., binaries, docs, build artifacts), cleans up stale files when switching folders, and keeps relative imports intact across machines.
- **Alternatives considered**:
  - *Copying the entire directory unfiltered with `copyFileOrDirectory`*: Risks committing large or sensitive non-proto files when a user selects a broad directory like `src/main/resources`.

## 5. Consolidating "Sync to Git" Toggle into `SelectProtoFileDialog`

- **Finding**: `GrpcRequestBar` in `GrpcRequestEditor.kt` currently renders a `grpc-proto-sync-toggle` checkbox right next to the Proto button, duplicating the `proto-dialog-sync-to-git-toggle` checkbox already inside `SelectProtoFileDialog.kt`.
- **Decision**: Remove `grpc-proto-sync-toggle` from `GrpcRequestBar` completely (FR-007). In `SelectProtoFileDialog`, show `proto-dialog-sync-to-git-toggle` only when `isGitSyncEnabled` is `true` AND the selected proto is NOT a collection-level proto file (FR-008). Update the toggle's helper caption when folder mode is active to inform the user that all `.proto` files in the folder and subfolders will be synced.
- **Rationale**: Declutters the gRPC request top bar and ensures a single source of truth for request-level proto Git sync behavior.
- **Alternatives considered**:
  - *Keeping a read-only Git sync badge in the request bar*: Not requested; user explicitly asked to move the Sync to Git toggle into the proto file dialog.
