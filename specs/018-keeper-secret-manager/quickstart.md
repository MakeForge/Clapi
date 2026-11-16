# Quickstart Validation: Keeper Security Cloud Secret Manager

**Branch**: `018-keeper-secret-manager` | **Date**: 2026-11-13

This guide provides step-by-step procedures to validate Keeper Security Cloud secret manager integration across unit, UI, and end-to-end scenarios.

---

## 1. Prerequisites

- JDK 21 installed (`java -version`)
- Project dependencies built:
  ```bash
  rtk gradlew :core-model:compileKotlinDesktop :core-network:compileKotlinDesktop :ui-shared:compileKotlinDesktop
  ```

---

## 2. Validation Scenario 1: One-Time Token Binding and KSafe Persistence

### Objective
Verify that entering a one-time token binds the KSM configuration and saves it encrypted into KSafe without persisting the OTT.

### Steps
1. Launch Clapi desktop application:
   ```bash
   rtk gradlew :ui-desktop:run
   ```
2. Open Settings (Ctrl+, or Settings toolbar icon) -> Navigate to **Secrets Manager**.
3. Select **Keeper Security Cloud** in the Secret Provider dropdown.
4. Verify the setup instructions and link to Keeper PAM documentation are rendered.
5. Enter a valid one-time token in the token input field and click **Connect**.
6. Verify:
   - The token field is cleared.
   - The status updates to **Connected to Keeper Cloud**.
   - Inspection of preferences reveals no plain token stored; `settings.keeperConfig` is encrypted in KSafe (`~/.clapi/clapi_secrets.json` or OS vault).
7. Re-open Settings -> Secrets Manager. Verify the brief connection check runs and confirms **Connected to Keeper Cloud**.

---

## 3. Validation Scenario 2: Environment Secret Definition & Custom Field Filtering

### Objective
Verify that external secrets can be defined with custom filters and target fields in an environment.

### Steps
1. Open an environment for editing (e.g. "Staging") and click the **External Secrets** tab.
2. Select **Keeper Security Cloud** as the provider.
3. Add a secret row:
   - Name: `clapi_user_password`
   - Record / Title: `clapi-test-user`
   - Target Field: `password`
   - Status indicates match against Keeper records.
4. Add another row with Custom Filters:
   - Name: `oauth_client_secret`
   - Record / Title: `clapi-test-custom-client-credentials`
   - Target Field: `clientSecret`
   - Expand **Filters** -> Add filter `clientId = clapi-client-id`.
5. Save the environment and export the collection.
6. Verify the exported JSON contains `keeper-security-cloud` without leaking credentials.

---

## 4. Validation Scenario 3: Request Execution with Secret Resolution

### Objective
Verify that requests referencing `{{_secret.<name>}}` resolve secret values dynamically at runtime.

### Steps
1. Create a GET request to an echo endpoint (or sample server):
   - URL: `http://127.0.0.1:8080/api/echo-headers`
   - Header: `Authorization: Bearer {{_secret.clapi_user_password}}`
   - Header: `X-Client-Secret: {{_secret.oauth_client_secret}}`
2. Select the Keeper-configured environment.
3. Send the request.
4. Verify response headers reflect the decrypted secret values (`clapi-password` and `clapie-client-secret`).

---

## 5. Validation Scenario 4: Automated Offline E2E Tests in `qa-tests`

### Objective
Verify that integration tests pass offline without external Keeper network dependencies or Docker containers.

### Execution
Run the automated Keeper integration test suite:
```bash
rtk gradlew :qa-tests:test --tests "io.github.makeforge.clapi.qa.KeeperSecurityCloudIntegrationTest"
```

### Expected Outcome
- Test runs against `qa-tests/src/test/resources/secrets/keeper/test-records.json`.
- Validates:
  - `login` record (`clapi-test-user`): resolves `login`, `password`, `url`.
  - `serverCredentials` record (`clapi-test-server`): resolves `login`, `password`, `host.hostName`.
  - Custom `OAuth Client Credentials` record (`clapi-test-custom-client-credentials`): resolves `clientId`, `clientSecret`.
  - Dynamic filter matching and error handling for missing/unmatched records.
- All tests pass with 0 failures.

---

## 6. Validation Scenario 5: Lazy Loading & Memory Cleanup

### Objective
Verify that Keeper classes and crypto are not loaded when unused, and that switching away releases memory.

### Steps
1. Run `KeeperClientManagerTest`:
   ```bash
   rtk gradlew :core-network:test --tests "*KeeperClientManagerTest*"
   ```
2. Verify switching to an environment where `hasKeeperSecrets = false` triggers `shutdown()` and clears the active client reference.
