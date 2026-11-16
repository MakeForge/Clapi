# Technical Research: gRPC Support

## Overview

This document analyzes the technical requirements and architecture decisions for introducing comprehensive gRPC support into Clapi. It covers dynamic Protobuf compilation and runtime invocation on JVM Desktop, data model extensions conforming to OpenCollection v1.0.0, Compose Multiplatform UI components (Insomnia-inspired layout), and test mock server integration using `kotlinx.rpc`.

---

## 1. Dynamic Runtime gRPC Client Architecture

### Context & Problem
In a GUI API client (like Insomnia or Postman), users select `.proto` files dynamically at runtime without restarting or re-compiling the application. Ahead-of-time code generation (generated Java/Kotlin stubs) is not applicable because arbitrary user `.proto` files must be supported on the fly.

### Research Findings & Decision
- **Decision**: Adopt dynamic message invocation using an embedded protoc compiler (`protoc-jar`), Protobuf `DynamicMessage`, `JsonFormat`, and gRPC `ClientCalls`.
- **Pipeline**:
  1. **Runtime Proto Compilation**: Use `com.github.os72:protoc-jar` to run `protoc` cross-platform (Linux/macOS/Windows) on the specified `.proto` file with `--include_imports` and `--descriptor_set_out` targeting a temporary `.pb` file.
  2. **Descriptor Resolution**: Read the `FileDescriptorSet` into memory and build dependency-resolved `Descriptors.FileDescriptor` instances. Extract available services, methods, input `Descriptor`, and output `Descriptor`.
  3. **JSON to DynamicMessage**: Use `JsonFormat.parser().ignoringUnknownFields().merge(jsonPayload, builder)` to parse the user's JSON payload into a `DynamicMessage`.
  4. **Dynamic Unary Call**: Construct an `io.grpc.MethodDescriptor<DynamicMessage, DynamicMessage>` configured with `ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(inputType))` and `ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(outputType))`.
  5. **Transport & Execution**: Use `ManagedChannelBuilder.forAddress(host, port)` (with channel caching keyed by host:port). Dispatch using `ClientCalls.futureUnaryCall` converted to coroutines with `kotlinx-coroutines-guava` or `CompletableFuture.asDeferred()`.
  6. **Metadata Interception**: Attach user-configured metadata headers to the call using `MetadataUtils.newAttachHeadersInterceptor(metadata)` or a custom `ClientInterceptor`.
  7. **Response Formatting**: Convert response `DynamicMessage` to formatted JSON string via `JsonFormat.printer().includingDefaultValueFields().print(responseMessage)`. Capture response trailers and status code/description.
- **Alternatives Considered**:
  - *Reflection API (`grpc-reflection`)*: Requires the remote server to have server reflection enabled. Cannot work when the user only has a local `.proto` file. Proto file parsing is universal and works offline.
  - *Command-line `grpcurl` subprocess*: Fragile dependency on external system binary, difficult to package portably across platforms, poor error handling.
- **Dependencies**:
  - `io.grpc:grpc-netty-shaded:1.68.2` (or latest 1.x compatible)
  - `io.grpc:grpc-protobuf:1.68.2`
  - `io.grpc:grpc-stub:1.68.2`
  - `com.google.protobuf:protobuf-java:4.28.3`
  - `com.google.protobuf:protobuf-java-util:4.28.3`
  - `com.github.os72:protoc-jar:3.11.4`

---

## 2. OpenCollection v1.0.0 Data Model & Serialization

### Context & Problem
gRPC requests must be persisted and serialized in full compliance with the OpenCollection v1.0.0 schema (`opencollection_v1.0.0.json`), supporting polymorphic serialization in Kotlin Multiplatform and round-trip export/import.

### Research Findings & Decision
- **Decision**: Define typed Kotlin data models in `core-model` within the `io.github.crustack.clapi.core.model.opencollection` package:
  - `GrpcRequestItem` implementing `OpenCollectionItem` with `info: GrpcRequestInfo`, `grpc: GrpcRequestDetails`, `runtime: GrpcRequestRuntime?`, `docs: String?`, and `extensions: Map<String, JsonElement>`.
  - `GrpcRequestInfo`: `name`, `description`, `type = "grpc"`, `seq`, `tags`.
  - `GrpcRequestDetails`: `url`, `method`, `methodType` (`"unary"`, `"client-streaming"`, `"server-streaming"`, `"bidi-streaming"`), `protoFilePath`, `metadata: List<GrpcMetadata>`, `message: String` (or variants), `auth: AuthConfig?`.
  - `GrpcMetadata`: `name`, `value`, `description`, `disabled: Boolean?`.
  - `GrpcRequestRuntime`: `variables: List<VariableEntry>`, `scripts: Scripts?`, `assertions: List<Assertion>?`.
- **Polymorphism**:
  - Update `OpenCollectionItemSerializer` in `OpenCollectionItem.kt`: Check `element.jsonObject.containsKey("grpc")` or `element.jsonObject["info"]?.jsonObject?.get("type")?.jsonPrimitive?.content == "grpc"` to select `GrpcRequestItem.serializer()`.
  - Update `OpenCollectionSerializer.kt` to register `KNOWN_GRPC_REQUEST_KEYS = setOf("info", "grpc", "runtime", "docs", "extensions")`.
- **UI State Mapping**:
  - Extend `RequestKind` enum in `McpModels.kt` with `GRPC`.
  - Update `OpenCollectionModelMapping.kt`:
    - In `toCollectionNode`: Handle `is GrpcRequestItem` -> construct `CollectionNode` with `kind = RequestKind.GRPC`, storing `url`, `grpcMethod`, `protoFilePath`, `metadata`, etc.
    - In `toOpenCollectionItem`: When `node.kind == RequestKind.GRPC` -> construct `GrpcRequestItem`.

---

## 3. UI/UX Workflow & Components

### Context & Problem
User interface interactions must match the established patterns in Clapi and the Insomnia gRPC reference designs (`insomnia_grpc_request.png`, `insomnia_grpc_select_proto.png`).

### Research Findings & Decision
- **Folder Context Menu**:
  - Clicking the "+" button on a collection folder currently triggers `onAddRequest`.
  - Update: Open an anchored `DropdownMenu` with two options:
    - "HTTP Request" (calls `onAddRequest(folder)`)
    - "gRPC Request" (calls `onAddGrpcRequest(folder)`)
- **Sidebar & Tab Badges**:
  - Introduce `GrpcBadge(compact: Boolean = false, modifier: Modifier = Modifier)`:
    - Text: "gRPC"
    - Colors: `ClapiColors.MethodPatch` (foreground text and 12% alpha background fill), matching the styling of `MethodBadge` and `McpMethodBadge`.
  - Render `GrpcBadge` in both `Sidebar.kt` and `RequestTabsBar.kt` whenever `item.kind == RequestKind.GRPC`.
- **gRPC Request Editor**:
  - Create `GrpcRequestEditor.kt` in `ui-shared/src/commonMain/kotlin/.../components/`:
    - Top Bar:
      - `GrpcBadge`
      - URL text field (supporting variable highlights and auto-complete)
      - Proto file button (opens "Select Proto File" dialog)
      - Method dropdown (lists parsed RPC methods from the active proto descriptor)
      - "Send" button with loading/cancel state
    - Tabs Section:
      - **Headers**: Table for `metadata` (`name`, `value`, `disabled`, `description`)
      - **Message**: JSON editor for input payload matching proto input message
      - **Scripts**: Pre-request and post-request test scripts editors
      - **Variables**: Variable overrides specific to the request
    - Response Panel:
      - Status badge (e.g. `OK 0`, `INVALID_ARGUMENT 3`)
      - Latency timer (ms)
      - Tabs: "Response" (pretty JSON body), "Headers" (returned metadata/trailers)
- **Select Proto File Dialog**:
  - Modal dialog with title "Select Proto File"
  - Displays currently selected file path (or "No proto file selected")
  - Action button: "Select Proto File" (invoking native file picker filtered to `*.proto`)
  - Action button: "Save" (commits `protoFilePath` to tab state and triggers schema parsing)

---

## 4. Test Mock Server & Automated QA Strategy

### Context & Problem
We need automated verification in `qa-tests` that tests end-to-end gRPC requests against a mock server and ensures round-trip collection import/export.

### Research Findings & Decision
- **DummyApiServer gRPC Endpoint**:
  - User requested `kotlinx.rpc` (see `kotlinx_rpc.md`).
  - Add standard gRPC service to `DummyApiServer` (or alongside Netty server):
    - Proto definition: `greeter.proto` or `dummy_service.proto` with `SayHello(HelloRequest) -> HelloReply` and `GetUser(UserRequest) -> UserReply`.
    - Host service on the test server with a dedicated or shared port.
- **Fixture Collections**:
  - Extend `clapi-test-collection.json` in `qa-tests/fixtures` to include a sample gRPC request item targeting `127.0.0.1:<grpc-port>`.
- **E2E Tests**:
  - Add `GrpcApiE2ETest.kt` in `qa-tests`:
    - Test 1: Load gRPC request from fixture, parse proto schema, execute unary call against `DummyApiServer`, assert JSON response matches expected schema.
    - Test 2: Verify custom metadata headers are sent and received.
    - Test 3: Round-trip serialization of collection with gRPC requests to JSON and YAML.
