# Interface Contracts: gRPC Request Proto Folder Workflow

**Feature**: `032-grpc-proto-workflow`  
**Date**: 2026-11-20

## 1. Core Storage Contract (`core-storage`)

### `CollectionStorageDriver.kt` (`expect` / `actual`)
```kotlin
/**
 * Recursively copies all `.proto` files (case-insensitive) from [sourceDir] into [destinationDir],
 * preserving relative subdirectory structure and excluding non-`.proto` files.
 *
 * - If [sourceDir] and [destinationDir] resolve to the same canonical directory, returns `true`
 *   without deleting existing files (idempotent re-save).
 * - Otherwise, clears any existing contents in [destinationDir] before copying so stale `.proto`
 *   files do not remain behind.
 * - Returns `false` if [sourceDir] does not exist or is not a directory.
 */
expect fun copyProtoFilesFromDirectory(sourceDir: String, destinationDir: String): Boolean
```

### `ProtoPathResolver.kt`
```kotlin
data class SyncedProtoFolderResult(
    val folderRelPath: String,
    val protoFileRelPath: String,
)

/**
 * Copies all `.proto` files from [sourceFolderPath] (recursively, preserving relative paths)
 * into `<collectionDir>/protobuf/requests/<safeReqId>/<folderName>` and returns portable
 * relative paths for both the synced folder and the target [sourceProtoFilePath].
 */
fun syncRequestProtoFolder(
    sourceFolderPath: String,
    sourceProtoFilePath: String,
    requestId: String,
    collectionDir: String,
): SyncedProtoFolderResult
```

## 2. Platform API Contract (`ui-shared/platform`)

### `PlatformApi.kt` (`expect` / `actual`)
```kotlin
data class DiscoveredProtoFile(
    val relativePath: String,
    val absolutePath: String,
)

/** Opens a native directory picker for selecting a root protobuf folder. */
expect fun pickProtoDirectory(onResult: (dirPath: String) -> Unit)

/**
 * Recursively scans [dirPath] for `.proto` files (case-insensitive) and returns them
 * sorted by normalized relative path (`/` separators). Returns empty list if [dirPath]
 * does not exist or is not a directory.
 */
expect fun listProtoFilesInDirectory(dirPath: String): List<DiscoveredProtoFile>
```

## 3. UI Dialog & Test Tag Contract (`ui-shared/components`)

### `SelectProtoFileDialog`
```kotlin
@Composable
fun SelectProtoFileDialog(
    currentProtoFilePath: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    currentProtoFolderPath: String? = null,
    errorMessage: String? = null,
    isLoading: Boolean = false,
    collectionProtoFiles: List<String> = emptyList(),
    isGitSyncEnabled: Boolean = false,
    initialSyncToGit: Boolean = false,
    onSave: (protoFilePath: String) -> Unit = {},
    onSaveWithSync: ((protoFilePath: String, syncToGit: Boolean) -> Unit)? = null,
    onSaveWithClose: ((protoFilePath: String, syncToGit: Boolean, closeDialog: Boolean) -> Unit)? = null,
    onSaveSelection: ((protoFilePath: String, protoFolderPath: String?, syncToGit: Boolean, closeDialog: Boolean) -> Unit)? = null,
    onPickFile: ((onSelected: (String) -> Unit) -> Unit)? = null,
    onPickFolder: ((onSelected: (String) -> Unit) -> Unit)? = null,
    onListProtoFiles: ((dirPath: String) -> List<DiscoveredProtoFile>)? = null,
)
```

### UI Test Tags
| Component | Test Tag | Notes |
|---|---|---|
| Request top bar Git sync toggle | *(removed)* `grpc-proto-sync-toggle` | MUST NOT exist in `GrpcRequestBar` (FR-007). |
| Pick single `.proto` file button | `proto-dialog-pick-button` | Existing tag preserved. |
| Pick proto folder button | `proto-dialog-pick-folder-button` | New button to select a proto folder. |
| Selected folder path display | `proto-dialog-folder-display` | Shown when a proto folder is active. |
| Clear selected folder button | `proto-dialog-clear-folder-button` | Clears folder mode. |
| Discovered folder `.proto` list | `proto-dialog-folder-file-list` | Scrollable list paired with `VerticalScrollbar`. |
| Discovered folder `.proto` item | `proto-dialog-folder-item-<relativePath>` | Clickable row selecting the target `.proto` file. |
| Empty folder state message | `proto-dialog-folder-empty` | Displayed when 0 `.proto` files are discovered. |
| Dialog Git sync toggle | `proto-dialog-sync-to-git-toggle` | Visible only when `isGitSyncEnabled && !isCollectionProtoSelected`. |

## 4. Import Resolution Helper Contract (`GrpcRequestEditor.kt`)
Both `GrpcRequestEditor` (parsing) and `resolveGrpcCallRequest` (execution) build `includePaths` using a shared helper so parsing and runtime execution can never diverge:

```kotlin
internal fun resolveGrpcIncludePaths(
    protoFolderPath: String?,
    collectionImportPaths: List<ProtoImportPathState>,
    collectionProtoFiles: List<ProtoFileItemState>,
    collectionGitDir: String?,
): List<String>
```
Returns deduplicated, resolved paths in priority order:
1. `protoFolderPath` (if non-blank)
2. `collectionImportPaths.filter { it.enabled }.map { it.path }`
3. `collectionProtoFiles.map { it.path }`
