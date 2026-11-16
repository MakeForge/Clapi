# Quickstart & Verification Guide: gRPC Support

## Overview
This guide provides runnable instructions to verify that gRPC request support works end-to-end, including creation, editing, execution against `DummyApiServer`, and collection round-trip serialization.

---

## 1. Automated Test Execution

Run the complete test suite including gRPC E2E tests:

```bash
# Run unit tests across core modules
./gradlew :core-model:test :core-storage:test :core-network:desktopTest

# Run end-to-end gRPC QA tests
./gradlew :qa-tests:test --tests "io.github.crustack.clapi.qa.GrpcApiE2ETest"
```

---

## 2. Testing OpenCollection Round-Trip Serialization

To verify that gRPC requests serialize to and from OpenCollection JSON and YAML without data loss:

```bash
./gradlew :core-storage:test --tests "io.github.crustack.clapi.core.storage.opencollection.OpenCollectionGrpcStorageTest"
```

---

## 3. Manual Verification in Desktop UI

1. **Launch Desktop App**:
   ```bash
   ./gradlew :ui-desktop:run
   ```

2. **Create a gRPC Request**:
   - In the sidebar, hover over any collection folder and click the `+` button.
   - In the context menu, click "New gRPC Request".
   - Notice the new item in the folder with a purple `gRPC` badge (`MethodPatch` color).

3. **Configure & Select Proto**:
   - Click the new gRPC request to open it in a tab.
   - Enter URL: `localhost:50051`.
   - Click the proto file icon/button in the request bar.
   - In the "Select Proto File" dialog, click "Select Proto File", choose an example `.proto` file (e.g., from `qa-tests/src/test/resources/greeter.proto`), and click "Save".
   - Verify the method dropdown populates with the service methods (e.g. `demo.GreeterService/SayHello`).
   - Select `demo.GreeterService/SayHello`.

4. **Author Message Payload & Send**:
   - In the "Message" tab, enter:
     ```json
     {
       "name": "World"
     }
     ```
   - In the "Headers" tab, add a metadata entry: `authorization` = `Bearer my-secret-token`.
   - Start the test gRPC server or execute against `DummyApiServer`.
   - Click "Send".
   - Verify the response status displays `OK 0`, latency is displayed, and the response body displays the JSON response message.
