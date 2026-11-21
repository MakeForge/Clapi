# Implementation Plan: gRPC Support

**Branch**: `005-grpc-support` | **Date**: 2026-11-08 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/005-grpc-support/spec.md`

## Summary

This feature introduces full support for creating, configuring, executing, importing, and exporting gRPC requests in Clapi. The data model is extended with `GrpcRequestItem` adhering strictly to the OpenCollection v1.0.0 specification. The sidebar tree and folder creation flow are enhanced with a context menu to spawn HTTP or gRPC requests and render distinct `GrpcBadge` indicators styled with `MethodPatch` accents. An Insomnia-style gRPC Request Editor is introduced featuring dynamic `.proto` file selection, method dropdown population, metadata headers grid, JSON payload editor, scripting tabs, and variable substitution. Dynamic invocation is achieved via Protobuf `DynamicMessage`, `JsonFormat`, and embedded `protoc-jar` compilation. End-to-end integration tests and mock gRPC server endpoints are added to `DummyApiServer` using `kotlinx.rpc` / gRPC.

## Technical Context

**Language/Version**: Kotlin 2.1 (JVM 21 / 17 compatible)

**Primary Dependencies**:
- Protobuf & gRPC:
  - `com.google.protobuf:protobuf-java:4.28.3`
  - `com.google.protobuf:protobuf-java-util:4.28.3`
  - `com.github.os72:protoc-jar:3.11.4`
  - `io.grpc:grpc-netty-shaded:1.68.2`
  - `io.grpc:grpc-protobuf:1.68.2`
  - `io.grpc:grpc-stub:1.68.2`
- Multiplatform & Serialization:
  - Compose Multiplatform 1.8.1 (Desktop)
  - `kotlinx.serialization` (JSON polymorphic serializer)
  - `kaml` (YAML support)
  - `kotlinx.coroutines`
- Mock Server & Testing:
  - `kotlinx-rpc` / gRPC integration in `DummyApiServer`
  - `kotlin.test`, JUnit 4

**Storage**:
- Canonical OpenCollection format (`core-storage`) supporting `GrpcRequestItem` serialization in JSON and YAML.

**Testing**:
- Unit tests for proto parsing and descriptor generation in `core-network`
- Serialization and roundtrip tests in `core-storage`
- UI component and sidebar tests in `ui-desktop`
- End-to-end integration tests in `qa-tests` against `DummyApiServer`

**Target Platform**: Desktop (Linux, macOS, Windows)

**Project Type**: Multiplatform Desktop Application

**Performance Goals**:
- Proto file parsing and method extraction under 1 second for standard schemas.
- Unary RPC dynamic invocation latency overhead < 15ms beyond network transit.
- Instantaneous (<16ms) variable interpolation in editor view.

**Constraints**:
- Adherence to OpenCollection v1.0.0 JSON schema.
- Embedded runtime protoc compilation must operate across Linux, macOS, and Windows without manual external protoc installation.
- Clean isolation between dynamic JVM network execution and multiplatform UI.

**Scale/Scope**:
- Supports arbitrary `.proto` schemas, complex nested protobuf message definitions, and Unary RPC execution.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

No constitutional violations exist. The architectural design adheres to:
1. **Modularity & Layering**: Dynamic gRPC client logic is encapsulated in `core-network`, data schemas in `core-model`, persistence in `core-storage`, and UI components in `ui-shared`/`ui-desktop`.
2. **Standard Compatibility**: Full fidelity with the OpenCollection v1.0.0 specification ensures interoperability and no proprietary lock-in.
3. **Test-First Compliance**: Complete test harness defined with `DummyApiServer` gRPC service and `qa-tests` fixtures.
4. **Resilience & Safety**: Non-blocking asynchronous RPC execution with Graceful Channel Management and structured error mapping preventing UI freezes or application crashes.

---

## Project Structure

### Documentation (this feature)

```text
specs/005-grpc-support/
├── spec.md                     # Feature specification
├── plan.md                     # Implementation plan
├── research.md                 # Technical research & decisions (Phase 0)
├── data-model.md               # Data entities & state transitions (Phase 1)
├── quickstart.md               # Runnable validation guide (Phase 1)
├── contracts/                  # Interface contracts (Phase 1)
│   ├── grpc-client-contract.md
│   └── grpc-editor-contract.md
└── checklists/
    └── requirements.md         # Specification quality checklist
```

### Source Code (repository root)

```text
core-model/
└── src/commonMain/kotlin/io/github/crustack/clapi/core/model/
    ├── McpModels.kt                                 # Extend RequestKind with GRPC
    └── opencollection/
        └── OpenCollectionItem.kt                    # Add GrpcRequestItem, GrpcMetadata, and serializer cases

core-network/
└── src/
    ├── commonMain/kotlin/io/github/crustack/clapi/core/network/
    │   └── grpc/
    │       ├── GrpcModels.kt                        # Call request/response & proto info types
    │       ├── ProtoParser.kt                       # Proto compilation & descriptor parsing interface
    │       └── DynamicGrpcClient.kt                 # Unary dynamic execution interface
    └── desktopMain/kotlin/io/github/crustack/clapi/core/network/
        └── grpc/
            ├── ProtocJarParser.kt                   # Implementation using protoc-jar & FileDescriptorSet
            └── NettyDynamicGrpcClient.kt            # Implementation using DynamicMessage & ManagedChannel

core-storage/
└── src/commonMain/kotlin/io/github/crustack/clapi/core/storage/opencollection/
    └── OpenCollectionSerializer.kt                  # Register KNOWN_GRPC_REQUEST_KEYS and extension handling

ui-shared/
└── src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/
    ├── components/
    │   ├── Sidebar.kt                               # Folder "+" context menu (HTTP / gRPC) & GrpcBadge
    │   ├── RequestTabsBar.kt                        # Render GrpcBadge for GRPC tab items
    │   ├── RequestEditor.kt                         # Delegate to GrpcRequestEditor when kind == GRPC
    │   ├── GrpcRequestEditor.kt                     # Insomnia-style gRPC request editor
    │   └── SelectProtoFileDialog.kt                 # Modal dialog for choosing proto file
    ├── state/
    │   └── AppState.kt                              # Add gRPC state fields to RequestTabState & CollectionNode
    └── persistence/
        └── OpenCollectionModelMapping.kt            # Mapping between CollectionNode and GrpcRequestItem

qa-tests/
├── fixtures/
│   └── clapi-test-collection.json                   # Include sample gRPC request item
├── src/test/kotlin/io/github/crustack/clapi/
│   ├── testsupport/
│   │   └── DummyApiServer.kt                        # Add gRPC service endpoint (using kotlinx.rpc / gRPC)
│   └── qa/
│       └── GrpcApiE2ETest.kt                        # E2E test verifying gRPC execution & roundtrips
└── src/test/resources/
    └── greeter.proto                                # Example proto file for automated tests
```

**Structure Decision**:
- Core data models and serialization reside in `core-model` and `core-storage`.
- Dynamic runtime execution and descriptor compilation reside in `core-network`.
- UI presentation components and dialogs reside in `ui-shared`.
- Automated mock endpoints and regression suites reside in `qa-tests`.

---

## Complexity Tracking

No constitutional violations exist. Complexity is justified by the requirement to parse arbitrary user-provided `.proto` schemas dynamically at runtime without ahead-of-time code generation.
