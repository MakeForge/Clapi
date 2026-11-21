# Quickstart: Validating Dynamic OAuth2 Access Token Variables

This guide describes how to validate that `{{_auth.oauth2.accessToken}}` is dynamically resolved during HTTP and gRPC request execution.

---

## 1. Prerequisites

- Project built with JDK 21 / 17.
- Gradle test suite accessible (`./gradlew check`).

---

## 2. Automated Test Validation

### A. Dynamic Variable Unit Tests
Run unit tests verifying the dynamic variable provider and registry:
```bash
./gradlew :core-network:test --tests "io.github.makeforge.clapi.core.network.dynamicvar.*"
```
**Expected outcome**:
- `OAuth2DynamicVariableProviderTest` verifies valid token reuse, token acquisition on expiration, and errors when OAuth2 is unconfigured.
- `DynamicVariableRegistryTest` verifies custom provider registration and resolution.

### B. Execution Pipeline Component Tests
Run pipeline tests in `ui-shared`:
```bash
./gradlew :ui-shared:test --tests "io.github.makeforge.clapi.ui.shared.components.DynamicVariableExecutionPipelineTest"
```
**Expected outcome**:
- Validates that a gRPC request configured with metadata `authorization: Bearer {{_auth.oauth2.accessToken}}` sends the resolved Bearer token.
- Validates that HTTP requests correctly substitute `{{_auth.oauth2.accessToken}}` across headers, URL, and body.

### C. End-to-End gRPC Integration Tests
Run QA integration tests with `DummyApiServer`:
```bash
./gradlew :qa-tests:test --tests "io.github.makeforge.clapi.qa.GrpcApiE2ETest.resolves_oauth2_access_token_in_grpc_metadata_authorization"
```
**Expected outcome**:
- The test spins up `DummyApiServer`, configures a gRPC request with metadata header `authorization = "Bearer {{_auth.oauth2.accessToken}}"`, executes the call, and verifies that `DummyApiServer` logged the call with the actual resolved token value.

---

## 3. Manual UI Verification

### Validating gRPC Metadata Resolution
1. Launch Clapi (`./gradlew :ui-desktop:run`).
2. Create a new collection or folder and configure OAuth2 authentication in its Auth tab (Client Credentials flow).
3. Create a new gRPC request inside the collection.
4. In the gRPC request editor:
   - Notice the **Auth** tab displaying "Inherit Auth from Parent" (showing the parent's OAuth2 settings).
   - In the **Headers** tab, add a metadata entry:
     - Key: `authorization`
     - Value: `Bearer {{_auth.oauth2.accessToken}}`
   - Notice autocomplete suggests `_auth.oauth2.accessToken` with the `AUTH` badge.
5. Click **Execute** / **Send**.
6. Check the **Console** / **Logs** tab: verify that the gRPC request was dispatched with the resolved bearer token in its metadata.
