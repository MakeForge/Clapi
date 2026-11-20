# Quickstart Validation Guide: gRPC Request Proto Folder Workflow

**Feature**: `032-grpc-proto-workflow`  
**References**: [spec.md](spec.md), [data-model.md](data-model.md), [contracts/grpc-proto-workflow-contract.md](contracts/grpc-proto-workflow-contract.md)

## Prerequisites

- JDK 25 installed (per `DEVELOPMENT.md`).
- Run all Gradle commands via `rtk gradlew ...` from repository root.

## Automated Validation Commands

### 1. Proto Compilation & Import Resolution (`core-network`)
```bash
rtk gradlew :core-network:desktopTest --tests "*ProtocJarParserTest*"
```
**Expected**: `greeter.proto` importing `protobuf/common/metadata.proto` compiles cleanly when either the `resources/` folder or the `resources/protobuf/` folder is provided in `includePaths`, and when combined with collection-level import paths/proto files.

### 2. Git Sync Folder Copying & Portable Path Resolution (`core-storage`)
```bash
rtk gradlew :core-storage:desktopTest --tests "*ProtoPathResolverTest*"
```
**Expected**:
- `syncRequestProtoFolder` copies all `.proto` files recursively into `./protobuf/requests/<requestId>/<folderName>/...` with intact relative paths.
- Non-`.proto` files are excluded.
- Re-syncing with a new folder removes stale `.proto` files; re-syncing an already-synced folder is idempotent.

### 3. Shared State, Persistence & Request Execution (`ui-shared`)
```bash
rtk gradlew :ui-shared:desktopTest
```
**Expected**: `protoFolderPath` round-trips through `OpenCollectionModelMapping` and `TabsRepository`, and `resolveGrpcCallRequest` includes `protoFolderPath` + enabled collection `importPaths` + collection `protoFiles`.

### 4. Compose Desktop UI Tests (`ui-desktop`)
```bash
rtk gradlew :ui-desktop:desktopTest --tests "*ProtoGitSyncUiTest*" --tests "*GrpcProto*"
```
**Expected**:
- `grpc-proto-sync-toggle` does not exist in `GrpcRequestBar`.
- `SelectProtoFileDialog` allows selecting a folder, lists discovered `.proto` files with relative paths, validates empty folders, saves the selected file + folder, and shows `proto-dialog-sync-to-git-toggle` only for Git-backed collections on non-collection-level selections.

### 5. End-to-End Integration Tests (`qa-tests`)
```bash
rtk gradlew :qa-tests:jvmTest --tests "*GrpcApiE2ETest*" --tests "*GitSyncE2ETest*"
```
**Expected**:
- A gRPC request using folder-based proto selection (`qa-tests/src/test/resources` + `protobuf/greeter.proto`) executes against the embedded gRPC server with zero collection-level import paths configured.
- A Git-synced gRPC request with a proto folder pushes all `.proto` files recursively and executes cleanly from a freshly cloned repository.

## Manual Desktop Verification

1. Launch Clapi in dev mode: `rtk gradlew :ui-desktop:runDev`.
2. Create a gRPC request in a local collection with no collection-level import paths.
3. Click the **Proto** button in the request bar -> click **Select Proto Folder** -> pick `qa-tests/src/test/resources/protobuf` (or `qa-tests/src/test/resources`).
4. Pick `greeter.proto` (or `protobuf/greeter.proto`) from the discovered list and click **Save**.
5. Verify the RPC method dropdown populates with `helloworld.Greeter/SayHello` and `helloworld.Greeter/SayHelloAgain`.
6. Switch the collection to Git storage, verify no "Sync to Git" toggle appears in the top bar, reopen the Proto dialog, enable **Sync to Git**, save, and confirm all `.proto` files under the folder are copied into `<repo>/<collection>/protobuf/requests/<requestId>/<folderName>/`.
