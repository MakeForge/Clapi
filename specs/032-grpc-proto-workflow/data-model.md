# Data Model: gRPC Request Proto Folder Workflow

**Feature**: `032-grpc-proto-workflow`  
**Date**: 2026-11-20

## Entities & State Extensions

### 1. `DiscoveredProtoFile` (new value type in `ui-shared/platform`)
Represents a `.proto` file discovered recursively inside a user-selected proto folder.

| Field | Type | Constraints / Notes |
|---|---|---|
| `relativePath` | `String` | Path relative to the selected proto folder, normalized with `/` separators (e.g. `protobuf/greeter.proto`, `common/metadata.proto`). |
| `absolutePath` | `String` | Full filesystem path to the `.proto` file for compilation and persistence. |

- **Ordering**: Sorted lexicographically by `relativePath` (case-insensitive).

### 2. `SyncedProtoFolderResult` (new value type in `core-storage/git`)
Returned by `ProtoPathResolver.syncRequestProtoFolder(...)` when syncing a request-level proto folder into a Git-backed collection.

| Field | Type | Constraints / Notes |
|---|---|---|
| `folderRelPath` | `String` | Portable relative path to the synced folder inside the collection repo: `./protobuf/requests/<safeReqId>/<folderName>`. |
| `protoFileRelPath` | `String` | Portable relative path to the chosen target `.proto` file inside the synced folder: `./protobuf/requests/<safeReqId>/<folderName>/<relativeProtoSubPath>`. |

### 3. `CollectionNode` (extended in `ui-shared/state/CollectionModels.kt`)
Existing fields relevant to gRPC proto configuration, plus new `protoFolderPath`:

| Field | Type | Default | Notes |
|---|---|---|---|
| `protoFilePath` | `String?` | `null` | Target `.proto` file path (absolute local path, collection proto path, or portable `./protobuf/...` Git path). |
| `protoFolderPath` | `String?` | `null` | **NEW**. Optional root proto folder path when folder mode is used (absolute local path or portable `./protobuf/requests/<reqId>/<folderName>` Git path). `null` for single-file or collection-level selection. |
| `protoSyncToGit` | `Boolean` | inferred from relative Git path | Request-level Git sync preference. Applies only to request-level local file/folder selections. |

### 4. `RequestTabState` (extended in `ui-shared/state/RequestTabState.kt`)
| Field | Type | Default | Notes |
|---|---|---|---|
| `protoFilePath` | `String?` (mutableStateOf) | `null` | Existing target `.proto` file path. |
| `protoFolderPath` | `String?` (mutableStateOf) | `null` | **NEW**. Optional selected proto folder path. Included in `computeDirtySnapshot()` so changing the folder marks the tab dirty. |
| `protoSyncToGit` | `Boolean` (mutableStateOf) | `false` | Existing Git sync flag. |

### 5. Persistence Schema Extensions

#### OpenCollection Request File (`extensions["clapi"]` in `OpenCollectionModelMapping.kt`)
- `grpc.protoFilePath`: Effective target `.proto` file path (portable relative path when synced to Git).
- `extensions["clapi"]["protoFilePath"]`: Effective target `.proto` file path.
- `extensions["clapi"]["protoFolderPath"]`: **NEW**. Effective proto folder path (omitted when `null` or blank; portable relative path `./protobuf/requests/<safeReqId>/<folderName>` when synced to Git).
- `extensions["clapi"]["protoSyncToGit"]`: `true` when request-level Git sync is enabled (omitted when `false`).

#### Local Tab State (`TabsRepository.kt`)
- Adds JSON field `"protoFolderPath": String` (serialized as `""` when `null`, deserialized as `null` when blank).

## Validation Rules & State Transitions

1. **Mode Exclusivity**:
   - **Folder mode**: `protoFolderPath != null` AND `protoFilePath` is a `.proto` file inside `protoFolderPath`.
   - **Single-file mode**: `protoFolderPath == null` AND `protoFilePath != null`.
   - **Collection-proto mode**: `protoFolderPath == null` AND `protoFilePath` matches an entry in `collectionProtoFiles`; `protoSyncToGit` is forced to `false` at request level.
2. **Save Precondition in `SelectProtoFileDialog`**:
   - Save is disabled when `protoFilePath` is blank OR when in folder mode with no target `.proto` file chosen (including empty folder with 0 `.proto` files) OR while `isLoading == true`.
3. **Git Sync State Transition on Save**:
   - When `collectionDir != null && protoSyncToGit`:
     - If `!protoFolderPath.isNullOrBlank() && !protoFilePath.isNullOrBlank()`: calls `syncRequestProtoFolder`, copying only `.proto` files recursively (removing stale files if source != destination), and persists portable relative paths for both `protoFolderPath` and `protoFilePath`.
     - Else if `!protoFilePath.isNullOrBlank()`: calls `syncRequestProtoFile` (existing single-file sync).
   - When `!protoSyncToGit`: persists the raw local paths unchanged.
4. **Import Resolution Context**:
   - Effective `includePaths` passed to `ProtoParser.parseProtoFile` and `GrpcCallRequest.includePaths`:
     1. Resolved `protoFolderPath` (if non-blank)
     2. Resolved enabled `collectionImportPaths`
     3. Resolved `collectionProtoFiles` (whose parent directories are included by `ProtocJarParser.normalizeIncludeDirs`)
