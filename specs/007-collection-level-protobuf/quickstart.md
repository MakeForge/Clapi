# Quickstart & Validation Guide: Collection-Level Protobuf Management

This guide describes how to manually and automatically validate collection-level Protobuf features end-to-end.

---

## 1. Automated Verification (QA Tests)

Run the end-to-end gRPC and Protobuf test suite:

```bash
./gradlew :qa-tests:jvmTest --tests "io.github.makeforge.clapi.qa.GrpcApiE2ETest"
```

### Expected Results:
- `parses greeter proto and discovers SayHello and SayHelloAgain unary RPCs`: PASSES (verifies that `greeter.proto` importing `metadata.proto` parses successfully when include paths are provided).
- `executes SayHello and SayHelloAgain unary calls with metadata roundtrip against DummyApiServer`: PASSES.
- `executes GrpcRequestItem through RequestExecutionService with variable substitution and auth`: PASSES.
- Collection-level protobuf configuration tests: PASSES.

---

## 2. Unit & Model Serialization Tests

Verify OpenCollection model serialization and schema compliance:

```bash
./gradlew :core-model:desktopTest --tests "io.github.makeforge.clapi.core.model.opencollection.OpenCollectionModelTest"
./gradlew :core-storage:desktopTest --tests "io.github.makeforge.clapi.core.storage.opencollection.*"
./gradlew :core-network:desktopTest --tests "io.github.makeforge.clapi.core.network.grpc.ProtocJarParserTest"
```

### Expected Results:
- `CollectionConfig` serialization roundtrips `protobuf` with `protoFiles` and `importPaths`.
- Schema validator confirms compliance with `opencollection_v1.0.0.json`.
- `ProtocJarParserTest` compiles proto files importing nested dependencies across include paths.

---

## 3. Manual Interactive Verification (UI Walkthrough)

1. **Launch Clapi**:
   ```bash
   ./gradlew :ui-desktop:run
   ```
2. **Sidebar Header Inspection**:
   - Verify that the collection tree header displays **Collection** with an **Article** icon instead of "Folders".
3. **Open Collection Editor**:
   - Click on "Collection" in the sidebar.
   - Verify a new editor tab titled **Collection** opens with the Article icon.
   - Verify the editor shows the **Protobuf** tab.
4. **Manage Collection Proto Files**:
   - In the "Proto Files" section, click "Add Proto Files".
   - Select multiple `.proto` files (e.g. `qa-tests/src/test/resources/greeter.proto`).
   - Click the "Open File" button on an entry; verify the file opens in your default text editor or IDE.
5. **Manage Import Paths**:
   - In "Import Paths", click "Add Import Paths".
   - Select `qa-tests/src/test/resources/protobuf/common/metadata.proto`.
   - Verify the entry appears with a checked toggle (enabled).
   - Uncheck the toggle (disabled) and re-check it.
6. **Select Proto File in gRPC Request**:
   - Open or create a gRPC Request.
   - Open the "Select Proto File" dialog.
   - Verify that the collection-level proto file appears for quick selection.
   - Select it and click "Save".
   - Verify that services and methods (`helloworld.Greeter/SayHello`, etc.) appear in the method dropdown.
