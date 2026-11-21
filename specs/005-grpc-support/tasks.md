# Tasks: gRPC Support

**Input**: Design documents from `specs/005-grpc-support/` (`spec.md`, `plan.md`, `data-model.md`, `contracts/`, `research.md`, `quickstart.md`)

**Prerequisites**: `plan.md` (required), `spec.md` (required for user stories), `data-model.md`, `contracts/`, `research.md`, `quickstart.md`

**Tests**: Test tasks are included as explicitly requested in user requirements (QA tests, fixtures, and unit test suites).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `- [ ] [TaskID] [P?] [Story?] Description with file path`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., [US1], [US2], [US3]...)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Dependencies & Project Configuration)

**Purpose**: Dependency configuration and build setup for Protobuf and gRPC runtime libraries

- [ ] T001 Add Protobuf and gRPC dependencies (`grpc-netty-shaded`, `grpc-protobuf`, `grpc-stub`, `protobuf-java`, `protobuf-java-util`, `protoc-jar`) in `gradle/libs.versions.toml`
- [ ] T002 Add gRPC, Protobuf, and coroutines-guava dependencies to `core-network/build.gradle.kts`
- [ ] T003 [P] Add test dependencies and proto resource path configuration in `qa-tests/build.gradle.kts`

---

## Phase 2: Foundational (Core Models & Schemas)

**Purpose**: Core data entities and polymorphic serializers that block all user stories

**⚠️ CRITICAL**: No user story work can begin until this foundational phase is complete

- [ ] T004 Extend `RequestKind` enum with `GRPC` in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/McpModels.kt`
- [ ] T005 Implement `GrpcRequestItem`, `GrpcRequestInfo`, `GrpcRequestDetails`, `GrpcRequestRuntime`, and `GrpcMetadata` data classes in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/OpenCollectionItem.kt`
- [ ] T006 Update `OpenCollectionItemSerializer` in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/OpenCollectionItem.kt` to handle polymorphic deserialization for `GrpcRequestItem` when element has `grpc` or `info.type == "grpc"`
- [ ] T007 [P] Define gRPC runtime models (`ProtoMethodInfo`, `ProtoServiceInfo`, `ProtoSchemaDefinition`, `GrpcCallRequest`, `GrpcCallResponse`) in `core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/grpc/GrpcModels.kt`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Create and Identify gRPC Requests in Collection Sidebar (Priority: P1) 🎯 MVP

**Goal**: Enable users to create gRPC requests via folder context menu and view them in sidebar and tabs with a styled `GrpcBadge`.

**Independent Test**: Click "+" on any collection folder in sidebar, select "New gRPC Request", and verify item is created and rendered with a `gRPC` badge displaying `MethodPatch` accent color.

### Tests for User Story 1
- [ ] T008 [P] [US1] Create UI unit test verifying `GrpcBadge` rendering and folder "+" context menu in `ui-desktop/src/desktopTest/kotlin/io/github/crustack/clapi/ui/desktop/components/SidebarGrpcBadgeTest.kt`

### Implementation for User Story 1
- [ ] T009 [P] [US1] Create `GrpcBadge` composable with `ClapiColors.MethodPatch` styling in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/Sidebar.kt`
- [ ] T010 [US1] Update `CollectionTreeNode` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/Sidebar.kt` to display `GrpcBadge` when `node.kind == RequestKind.GRPC`
- [ ] T011 [P] [US1] Update `RequestTabsBar` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/RequestTabsBar.kt` to render `GrpcBadge` for tabs with `RequestKind.GRPC`
- [ ] T012 [US1] Update folder "+" button action in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/Sidebar.kt` to display a dropdown context menu offering "HTTP Request" and "gRPC Request"
- [ ] T013 [US1] Add handler `onAddGrpcRequest` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/Sidebar.kt` and `AppState` to create a `CollectionNode` with `kind = RequestKind.GRPC`

**Checkpoint**: User Story 1 is functional and testable independently (MVP ready).

---

## Phase 4: User Story 2 - Configure gRPC Target URL and Select Proto Methods (Priority: P1)

**Goal**: Enable users to enter endpoint URL, choose a `.proto` file via dialog, parse schema methods, and select an RPC method from a dropdown.

**Independent Test**: Open a gRPC request, pick a valid `.proto` file using the "Select Proto File" dialog, click Save, and verify the method dropdown lists all parsed RPC methods.

### Tests for User Story 2
- [ ] T014 [P] [US2] Add unit test for `ProtocJarParser` with sample `.proto` file in `core-network/src/desktopTest/kotlin/io/github/crustack/clapi/core/network/grpc/ProtocJarParserTest.kt`

### Implementation for User Story 2
- [ ] T015 [P] [US2] Implement `ProtoParser` interface and `ProtocJarParser` in `core-network/src/desktopMain/kotlin/io/github/crustack/clapi/core/network/grpc/ProtocJarParser.kt` using `protoc-jar` to compile `.proto` into a `FileDescriptorSet` and extract services and methods
- [ ] T016 [P] [US2] Implement `SelectProtoFileDialog` composable in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/SelectProtoFileDialog.kt` with current file path display, file picker action, and save button
- [ ] T017 [US2] Extend `RequestTabState` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt` with fields for `protoFilePath`, `grpcMethod`, `availableMethods`, and proto loading state
- [ ] T018 [US2] Create `GrpcRequestEditor` top request bar in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/GrpcRequestEditor.kt` with URL input, Proto selection dialog button, and dynamic Method selector dropdown

**Checkpoint**: User Stories 1 and 2 operate together independently.

---

## Phase 5: User Story 3 - Author gRPC Metadata, JSON Message Payload, and Scripts (Priority: P1)

**Goal**: Provide editors for gRPC metadata headers, JSON message body matching protobuf schema, and pre/post-request scripts with variable substitution.

**Independent Test**: Enter JSON in the Message editor, add a metadata entry in the Headers tab with a `{{variable}}`, and confirm that variables are substituted and saved.

### Implementation for User Story 3
- [ ] T019 [US3] Extend `RequestTabState` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt` with `grpcMetadata` list, `grpcMessageBody` string, and scripting hooks
- [ ] T020 [US3] Implement "Headers" metadata grid tab in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/GrpcRequestEditor.kt` allowing users to add, edit, disable, and remove gRPC metadata key-values
- [ ] T021 [US3] Implement "Message" tab in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/GrpcRequestEditor.kt` providing a JSON code editor for the request payload
- [ ] T022 [US3] Implement "Scripts" tab in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/GrpcRequestEditor.kt` for pre-request and post-request test scripts
- [ ] T023 [US3] Add variable token substitution (`{{var}}`) across URL, metadata values, and message JSON text in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/GrpcRequestEditor.kt`

**Checkpoint**: gRPC authoring tabs and variable substitutions are complete and functional.

---

## Phase 6: User Story 4 - Execute Unary gRPC Call and View Response (Priority: P1)

**Goal**: Execute unary gRPC requests against remote or local servers using Protobuf `DynamicMessage` and display response status, latency, headers, and formatted JSON body.

**Independent Test**: Configure a unary RPC call targeting a live gRPC endpoint, click "Send", and verify response status, execution time, response metadata, and pretty-printed JSON payload appear in the response view.

### Tests for User Story 4
- [ ] T024 [P] [US4] Add unit tests for `NettyDynamicGrpcClient` in `core-network/src/desktopTest/kotlin/io/github/crustack/clapi/core/network/grpc/DynamicGrpcClientTest.kt`

### Implementation for User Story 4
- [ ] T025 [P] [US4] Implement `DynamicGrpcClient` in `core-network/src/desktopMain/kotlin/io/github/crustack/clapi/core/network/grpc/NettyDynamicGrpcClient.kt` using `ManagedChannel`, `JsonFormat.parser()`, `DynamicMessage`, and `ClientCalls.futureUnaryCall`
- [ ] T026 [US4] Implement metadata interceptor in `core-network/src/desktopMain/kotlin/io/github/crustack/clapi/core/network/grpc/NettyDynamicGrpcClient.kt` to attach custom headers/metadata to outgoing calls
- [ ] T027 [US4] Wire `DynamicGrpcClient` into `RequestExecutionService` in `feature-requests/src/commonMain/kotlin/io/github/crustack/clapi/feature/requests/RequestExecutionService.kt`
- [ ] T028 [US4] Implement the response viewer panel in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/GrpcRequestEditor.kt` displaying status code, latency, response metadata, and pretty-printed JSON response body
- [ ] T029 [US4] Update `RequestEditor.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/RequestEditor.kt` to render `GrpcRequestEditor` when `tabState.kind == RequestKind.GRPC`

**Checkpoint**: End-to-end execution of gRPC calls from the UI is fully operational.

---

## Phase 7: User Story 5 - Import and Export gRPC Requests in OpenCollection Format (Priority: P2)

**Goal**: Support seamless round-trip export and import of collections containing gRPC requests in OpenCollection JSON and YAML formats.

**Independent Test**: Export a collection with gRPC requests to OpenCollection format, re-import it, and verify that `url`, `method`, `protoFilePath`, metadata, message, and scripts match exactly.

### Tests for User Story 5
- [ ] T030 [P] [US5] Create round-trip serialization unit tests in `core-storage/src/commonTest/kotlin/io/github/crustack/clapi/core/storage/opencollection/OpenCollectionGrpcStorageTest.kt`

### Implementation for User Story 5
- [ ] T031 [US5] Update `OpenCollectionSerializer.kt` in `core-storage/src/commonMain/kotlin/io/github/crustack/clapi/core/storage/opencollection/OpenCollectionSerializer.kt` to include `KNOWN_GRPC_REQUEST_KEYS` and serialize/deserialize `GrpcRequestItem` in JSON and YAML
- [ ] T032 [US5] Update `OpenCollectionModelMapping.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/OpenCollectionModelMapping.kt` to map between `CollectionNode` and `GrpcRequestItem`

**Checkpoint**: Collections containing gRPC requests persist, import, and export losslessly.

---

## Phase 8: User Story 6 - Automated QA Test Suite and Mock gRPC Server Integration (Priority: P2)

**Goal**: Provide an automated QA suite with mock gRPC service in `DummyApiServer` using `kotlinx.rpc` / gRPC, fixture collections, and automated end-to-end regression tests.

**Independent Test**: Run `./gradlew :qa-tests:test --tests "io.github.crustack.clapi.qa.GrpcApiE2ETest"` and verify tests pass.

### Tests & Implementation for User Story 6
- [ ] T033 [P] [US6] Create test proto definition `greeter.proto` in `qa-tests/src/test/resources/greeter.proto`
- [ ] T034 [US6] Add gRPC test endpoint to `DummyApiServer` in `qa-tests/src/test/kotlin/io/github/crustack/clapi/testsupport/DummyApiServer.kt` hosting the greeter service (using `kotlinx.rpc` / gRPC)
- [ ] T035 [P] [US6] Add sample gRPC request item to `qa-tests/fixtures/clapi-test-collection.json`
- [ ] T036 [US6] Create end-to-end integration test `GrpcApiE2ETest.kt` in `qa-tests/src/test/kotlin/io/github/crustack/clapi/qa/GrpcApiE2ETest.kt` verifying execution, status codes, latency, response JSON, and metadata roundtrip against `DummyApiServer`

**Checkpoint**: Automated regression suite verifies gRPC requests against the mock server in CI.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Cleanup, formatting, resource safety, and validation

- [ ] T037 [P] Run linter and formatting across modified modules (`core-model`, `core-network`, `core-storage`, `ui-shared`, `qa-tests`)
- [ ] T038 Add temp file cleanup in `ProtocJarParser` in `core-network/src/desktopMain/kotlin/io/github/crustack/clapi/core/network/grpc/ProtocJarParser.kt`
- [ ] T039 Execute quickstart scenarios per `specs/005-grpc-support/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3-8)**:
  - US1 (Sidebar & Creation) depends on Phase 2
  - US2 (URL & Proto Selection) depends on Phase 2
  - US3 (Headers, Message, Scripts) depends on US2
  - US4 (Execution & Response) depends on US2 and US3
  - US5 (Import/Export) depends on Phase 2
  - US6 (QA & Mock Server) depends on US4 and US5
- **Polish (Phase 9)**: Depends on all user stories being complete

### User Story Completion Order

```text
Foundational (Phase 2)
      │
      ├──► US1: Sidebar & Folder Creation (P1) [MVP]
      │
      ├──► US2: URL & Proto Selection (P1)
      │         │
      │         ▼
      │     US3: Metadata, Message, Scripts (P1)
      │         │
      │         ▼
      │     US4: Execution & Response (P1)
      │         │
      ├──► US5: OpenCollection Import/Export (P2)
      │         │
      └─────────┴──► US6: QA Tests & DummyApiServer (P2)
                           │
                           ▼
                     Polish (Phase 9)
```

---

## Parallel Execution Opportunities

- **Phase 1**: T001, T002, T003 can be prepared together
- **Phase 2**: T004, T005, T006 (core-model) can proceed in parallel with T007 (core-network)
- **Phase 3 (US1)**: T008 (test) and T009 (badge) can be developed in parallel before T010/T012
- **Phase 4 (US2)**: T014 (test), T015 (parser), and T016 (proto dialog) can run in parallel
- **Phase 6 (US4)**: T024 (test) and T025 (client) can run in parallel before T027/T028
- **Phase 7 (US5)**: T030 (test) and T031 (OpenCollectionSerializer) can run in parallel
- **Phase 8 (US6)**: T033 (proto file) and T035 (fixture JSON) can run in parallel

---

## Implementation Strategy

### MVP First (User Story 1 Only)
1. Complete Phase 1: Setup dependencies
2. Complete Phase 2: Foundational models (`GrpcRequestItem`, `RequestKind.GRPC`)
3. Complete Phase 3: User Story 1 (Folder "+" menu, `GrpcBadge`, creation)
4. **Validate**: Verify gRPC requests can be created and displayed in the sidebar tree with the distinct badge.

### Incremental Delivery
1. Add User Story 2: Proto selection & method discovery
2. Add User Story 3: Message payload & metadata authoring
3. Add User Story 4: Dynamic execution & response viewer
4. Add User Story 5: OpenCollection import/export persistence
5. Add User Story 6: Automated QA integration test suite with `DummyApiServer`
