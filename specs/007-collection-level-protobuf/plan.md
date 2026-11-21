# Implementation Plan: Collection-Level Protobuf Management

**Branch**: `007-collection-level-protobuf` | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/007-collection-level-protobuf/spec.md`

## Summary

This feature introduces collection-level Protocol Buffer management into Clapi conforming to OpenCollection's `CollectionConfig.protobuf` specification. It provides a top-level "Collection" entry point in the sidebar tree (replacing "Folders" with Material `Article` icon), opening a dedicated `CollectionEditor` tab with a "Protobuf" configuration interface. Users can manage linked `protoFiles` (for collection-wide gRPC request selection) and `importPaths` (with enable/disable toggles for protobuf compiler import resolution), without duplicating files into internal storage. Each proto entry features an "Open File" action for quick external viewing. In the gRPC Request Editor, users can select from collection-level proto files in addition to manual browsing. `ProtocJarParser` and the runtime execution pipeline are updated to resolve imports through configured collection-level import paths, and the QA test suite is updated to validate end-to-end gRPC calls with imported proto schemas.

## Technical Context

**Language/Version**: Kotlin 2.1 (JVM 21 / 17 compatible)

**Primary Dependencies**:
- Protobuf & gRPC:
  - `com.github.os72:protoc-jar:3.11.4` (embedded protoc compiler)
  - `com.google.protobuf:protobuf-java:4.28.3` & `protobuf-java-util:4.28.3`
  - `io.grpc:grpc-netty-shaded:1.68.2`, `grpc-protobuf:1.68.2`, `grpc-stub:1.68.2`
- Compose Multiplatform 1.8.1 (Desktop UI)
- `kotlinx.serialization` (JSON / OpenCollection polymorphic serialization)
- Desktop Platform Integration (`java.awt.Desktop`, native `FileDialog`)
- Testing: JUnit 4, `kotlin.test`, `kotlinx.coroutines.test`, `DummyApiServer`

**Storage**:
- Canonical OpenCollection format (`core-storage` / `collection.json`) adhering to OpenCollection v1.0.0 `CollectionConfig.protobuf` schema (`protoFiles` and `importPaths`).
- Workspace state persistence via `WorkspaceRepository` and `OpenCollectionModelMapping`.

**Testing**:
- Unit tests for OpenCollection serialization in `core-model` and `core-storage`
- Unit tests for `ProtocJarParser` include directory resolution in `core-network`
- UI component and tab navigation tests in `ui-desktop`
- End-to-end integration tests in `qa-tests` (`GrpcApiE2ETest`) against `DummyApiServer`

**Target Platform**: Desktop (Linux, macOS, Windows)

**Project Type**: Multiplatform Desktop Application

**Performance Goals**:
- Protoc compilation with imported proto hierarchies under 1 second.
- Collection editor tab opening and switching under 16ms (instant 60fps response).
- Zero file duplication overhead (paths stored as lightweight string references).

**Constraints**:
- Strict compliance with OpenCollection v1.0.0 JSON schema.
- Embedded runtime protoc compilation without external protoc dependency.
- Safe desktop file viewer invocation without crashing on unassociated file extensions.

**Scale/Scope**:
- Supports arbitrary multi-file proto schemas with complex nested import dependencies across multiple directories.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

No constitutional violations exist. The architectural design adheres to:
1. **Modularity & Layering**: OpenCollection schemas in `core-model`, persistence in `core-storage`, compiler and execution logic in `core-network` / `feature-requests`, and UI components in `ui-shared`.
2. **Standard Interoperability**: Strict adherence to OpenCollection v1.0.0 JSON schema ensures full portability with other OpenCollection-compatible tooling.
3. **Non-Invasive Storage**: Proto files are referenced by path; no copying or internal shadowing is performed.
4. **Test-First Compliance**: Complete automated test coverage including model serialization, compiler import resolution, and end-to-end gRPC roundtrip testing.

---

## Project Structure

### Documentation (this feature)

```text
specs/007-collection-level-protobuf/
├── spec.md                     # Feature specification
├── plan.md                     # This file (/speckit-plan output)
├── research.md                 # Technical research & decisions (Phase 0)
├── data-model.md               # Data entities & state transitions (Phase 1)
├── quickstart.md               # Runnable validation guide (Phase 1)
├── contracts/                  # Interface contracts (Phase 1)
│   ├── collection-editor-contract.md
│   └── protobuf-runtime-contract.md
└── checklists/
    └── requirements.md         # Specification quality checklist
```

### Source Code (repository root)

```text
core-model/
└── src/commonMain/kotlin/io/github/makeforge/clapi/core/model/
    ├── McpModels.kt                                 # Extend RequestKind with COLLECTION
    └── opencollection/
        └── OpenCollectionDocument.kt                # Add CollectionProtobufConfig, ProtoFile, ProtoFileImportPath

core-network/
├── src/commonMain/kotlin/io/github/makeforge/clapi/core/network/grpc/
│   └── GrpcModels.kt                                # Add includePaths to GrpcCallRequest
└── src/desktopMain/kotlin/io/github/makeforge/clapi/core/network/grpc/
    ├── NettyDynamicGrpcClient.kt                    # Forward includePaths to ProtoParser
    └── ProtocJarParser.kt                           # Resolve directory & ancestor include paths for protoc

feature-requests/
└── src/commonMain/kotlin/io/github/makeforge/clapi/feature/requests/
    └── RequestExecutionService.kt                   # Forward collection importPaths during gRPC execution

ui-shared/
└── src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/
    ├── MainScreen.kt                                # Route RequestKind.COLLECTION to CollectionEditor
    ├── components/
        ├── CollectionEditor.kt                      # New: CollectionEditor composable with Protobuf tab
        ├── RequestTabsBar.kt                        # Render Article icon for RequestKind.COLLECTION
        ├── SelectProtoFileDialog.kt                 # Add collection-level protoFiles selection
        └── Sidebar.kt                               # Rename Folders to Collection, Article icon, click to open
    ├── persistence/
        └── OpenCollectionModelMapping.kt            # Map config.protobuf to/from AppState
    ├── platform/
        ├── PlatformApi.kt                           # Add pickProtoFiles and openFileInDefaultApp expect
        └── PlatformApi.desktop.kt                   # Implement multi-pick and desktop open
    └── state/
        └── AppState.kt                              # Add collectionProtoFiles, collectionImportPaths, openCollection()

qa-tests/
└── src/test/kotlin/io/github/makeforge/clapi/
    ├── testsupport/DummyApiServer.kt                # Support imported metadata.proto on test server start
    └── qa/GrpcApiE2ETest.kt                         # Test collection-level proto files and importPaths
```

**Structure Decision**: The implementation cleanly augments existing layered modules (`core-model`, `core-network`, `feature-requests`, `ui-shared`, `qa-tests`) without introducing new subprojects or altering build configurations.

---

## Complexity Tracking

> No constitutional violations or unwarranted complexity.
