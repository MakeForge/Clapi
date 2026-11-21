# Feature Specification: Keeper Security Cloud Secret Manager Integration

**Feature Branch**: `018-keeper-secret-manager`

**Created**: 2026-11-13

**Status**: Draft

**Input**: User request to support Keeper Security Cloud (`keeper-security-cloud`) as an external secret manager provider similar to HashiCorp Vault, KeePassXC, AWS Secrets Manager, Azure Key Vault, and GCP Secret Manager.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Connect Keeper Secrets Manager in Global Settings (Priority: P1)

Users managing API environments with secrets stored in Keeper Security Cloud need a simple and secure setup flow in Clapi Settings:
1. In the application Settings dialog under "Secrets Manager", the user selects "Keeper Security Cloud" (`keeper-security-cloud`).
2. When not connected, the user sees an input field for a One-Time Token (OTT), a "Connect" button, and helpful instructions explaining how to obtain a token ("Login to Keeper > Secrets Manager > Create Application > Generate Access Token (Read Only Record Permissions are enough)"), with a help icon linking to `https://docs.keeper.io/keeperpam/secrets-manager/quick-start-guide#create-a-secrets-manager-application`.
3. Clicking "Connect" initializes local storage (`initializeStorage`) and completes the binding handshake with Keeper Secrets Manager (KSM), generating the persistent client configuration JSON (`hostname`, `clientId`, `privateKey`, `appKey`, `appOwnerPublicKey`, `serverPublicKeyId`).
4. The one-time token is discarded immediately and NEVER persisted.
5. The resulting client configuration is encrypted and stored securely in platform storage via KSafe.
6. When settings are opened and Keeper is already configured, the UI briefly checks connection health against Keeper Cloud and displays a status indicator (e.g. "Connected" or "Connection Failed").
7. Users can re-connect at any time by entering a new one-time token to overwrite the existing configuration, or clear the connection.

**Why this priority**: Without credentials and the initial binding configuration, Clapi cannot authenticate with Keeper Secrets Manager to retrieve any records.

**Independent Test**: Open Settings -> Secrets Manager, select Keeper Security Cloud. Enter a valid one-time token, click "Connect". Verify that the token is redeemed, the client configuration is encrypted in KSafe, the input field is cleared, and connection status is confirmed. Reopen settings to verify health indicator check.

**Acceptance Scenarios**:
1. **Given** global Settings is open, **When** selecting "Keeper Security Cloud", **Then** the UI displays the OTT input, "Connect" button, setup hint, and link to documentation.
2. **Given** a user inputs a one-time token and clicks "Connect", **Then** KSM initialization and token binding occur; if successful, the full client config is saved encrypted in KSafe and the one-time token is not stored.
3. **Given** a client configuration is already stored, **When** the user navigates to Keeper settings, **Then** a brief connection check executes and displays the connection state.
4. **Given** an already initialized config, **When** the user provides a new one-time token and clicks "Connect", **Then** the old config is replaced with the newly bound configuration.

---

### User Story 2 - Define Keeper External Secrets in Environment Editor (Priority: P1)

Users configuring environments in Clapi need to map secret variables to Keeper records:
1. In the Environment Editor's "External Secrets" tab, users choose "Keeper Security Cloud" (`keeper-security-cloud`) as the provider.
2. The UI presents a tabular editor styled similarly to KeePassXC external secrets, with columns:
   - Enabled checkbox
   - Secret Variable Name (e.g. `client_password`, `auth_token`)
   - Record Title / UID (identifier of the Keeper record, e.g. `clapi-test-user`, `clapi-test-server`, `clapi-test-custom-client-credentials`, or a record UID)
   - Target Field with placeholder hint `"password"` (e.g. `password`, `login`, `url`, `clientId`, `clientSecret`, `host.hostName`)
   - Status (matches found in Keeper vault, e.g. "✔ 1 match", "✖ 0 found")
   - Filters (expandable sub-panel for custom key-value filters with default key hint `'url'`)
   - Description (optional text)
   - Delete action
3. Custom filters allow matching records by arbitrary field types or labels (e.g. `url = https://clapi.api.com`, `type = login`, `login = clapi-user`), with variable interpolation (e.g. `url = {{base_url}}`).

**Why this priority**: Users must be able to declare secret variable mappings and filter rules cleanly in the UI for environments that resolve from Keeper.

**Independent Test**: In Environment Editor -> External Secrets, select Keeper Security Cloud. Add an external secret named `db_pass` targeting record `clapi-test-server`, target field `password`. Add another named `oauth_secret` targeting record `clapi-test-custom-client-credentials`, target field `clientSecret`. Save the environment and verify it serializes to OpenCollection format.

**Acceptance Scenarios**:
1. **Given** Environment Editor -> External Secrets, **When** selecting provider dropdown, **Then** "Keeper Security Cloud" (`keeper-security-cloud`) is available.
2. **Given** Keeper provider is selected, **When** viewing the table, **Then** the layout matches KeePassXC's layout, showing Name, Record/Title, Target Field (hint "password"), Status, Filters, and Description.
3. **Given** the user expands Filters, **When** adding a filter row, **Then** the key placeholder suggests `url` (e.g. `field (e.g. url)`) and the value field supports variable substitution.
4. **Given** configured Keeper secrets, **When** the environment is saved, **Then** it serializes into OpenCollection with `type: "keeper-security-cloud"` and `KeeperSecurityExternalSecret` definitions without leaking sensitive data.

---

### User Story 3 - Resolve Keeper Secrets at Request Execution Runtime (Priority: P1)

When a request is executed in an active environment referencing `{{_secret.<name>}}` or an environment variable with `secret: true`:
1. `SecretResolutionService` resolves Keeper secrets using the stored client configuration.
2. Records matching the declared record identifier (title or UID) and custom filters are evaluated.
3. The specified `targetField` is extracted from the record:
   - `"password"` retrieves password from standard record password field or custom password field.
   - `"login"`, `"username"` retrieves login string.
   - Dotted property (e.g. `"host.hostName"`, `"host.port"`) extracts nested object attributes.
   - Custom field label (e.g. `"clientId"`, `"clientSecret"`) extracts the matching custom field value.
   - Standard Keeper Notation (e.g. `clapi-test-user/field/url` or `field/login`) is supported.
4. Multiple secrets referencing the same record are fetched efficiently in a single operation.
5. Injected secrets are substituted into the request without leaking credentials to logs or saved collection files.

**Why this priority**: Dynamic runtime secret resolution is the core functional requirement of external secret manager support.

**Independent Test**: Configure active environment with Keeper external secrets. Send an HTTP request with `Authorization: Bearer {{_secret.my_password}}`. Assert that the request header contains the resolved secret value.

**Acceptance Scenarios**:
1. **Given** an enabled Keeper secret referencing record `clapi-test-user` and target field `password`, **When** a request referencing `{{_secret.clapi_user_password}}` executes, **Then** `clapi-password` is substituted and transmitted.
2. **Given** a serverCredentials record `clapi-test-server` with target field `host.hostName`, **When** resolved, **Then** `clapi.api.com` is extracted.
3. **Given** a custom record type `OAuth Client Credentials` with target field `clientSecret`, **When** resolved, **Then** `clapie-client-secret` is extracted.
4. **Given** a filter condition that does not match any record, **When** resolving secrets, **Then** request execution aborts with a clear, descriptive error.

---

### User Story 4 - Resource and Memory Optimization: No Overhead When Unused (Priority: P2)

Clapi allows only one active environment at a time, and that environment uses at most one secret provider:
1. Keeper SDK and cryptographic classes MUST NOT be loaded statically during application startup or when Keeper is not used.
2. If Keeper is not configured or the active environment does not use Keeper, no Keeper SDK network client, crypto providers, or connection objects reside in memory.
3. When the user switches to an environment that does not use Keeper, any active Keeper client or cached records are immediately shut down and released.
4. When the user switches to an environment using Keeper, the client is lazily created only on demand.

**Why this priority**: Avoid memory bloat and unnecessary cryptographic overhead for users who do not use Keeper or are using other secret providers.

**Independent Test**: Launch application without Keeper configured. Verify no Keeper classes or crypto providers are loaded. Switch between KeePassXC and Keeper environments and verify lifecycle cleanup callbacks execute on switch.

**Acceptance Scenarios**:
1. **Given** Clapi starts up, **When** Keeper is not configured, **Then** no Keeper SDK classes or cryptographic providers are initialized.
2. **Given** Keeper client was used, **When** switching to an environment with another secret provider (or none), **Then** `KeeperClientManager` releases resources and shuts down the active client.

---

### User Story 5 - Automated End-to-End Tests in `qa-tests` (Priority: P1)

Integration and E2E tests in `qa-tests` must validate Keeper Secret Manager functionality across various record types without relying on Docker (since Keeper Cloud has no official Docker emulator):
1. Use `qa-tests/src/test/resources/secrets/keeper/test-records.json` containing standard record types (`login`, `serverCredentials`) and custom record type (`OAuth Client Credentials`).
2. Utilize Kotlin compiler friend paths configuration in `qa-tests/build.gradle.kts` (`friendPaths.from(...)`) to access Keeper SDK internal `TestStubs` for transmission key handling and custom `postFunction` stubbing for offline execution, mirroring `SecretsManagerTest.kt`.
3. Provide end-to-end tests exercising secret resolution for:
   - `login` record (`clapi-test-user`): login, password, url
   - `serverCredentials` record (`clapi-test-server`): host, login, password
   - Custom `OAuth Client Credentials` record (`clapi-test-custom-client-credentials`): clientId, clientSecret
4. Verify dynamic field filtering and target field extraction across all record types.

**Why this priority**: Ensures continuous integration testing remains fast, completely offline, deterministic, and free from external cloud dependencies.

**Independent Test**: Run `rtk gradlew :qa-tests:test --tests "*Keeper*"`. Assert that all offline E2E scenarios pass successfully.

---

## Edge Cases

- **One-time token reuse**: A token can only be used once. If the user tries to use an already redeemed token, Keeper API rejects it with an informative error; Clapi displays the error and does not save invalid state.
- **Malformed OTT format**: Handle tokens with region prefix (e.g. `US:...`, `EU:...`) or custom hostnames gracefully.
- **Empty or missing target field**: If target field is omitted, default to `"password"`. If the record has no password field, report a clear error explaining which field was missing.
- **Multiple matching records**: If custom filters match multiple records, pick the first exact match or report an ambiguous match error depending on strictness.
- **Environment switching**: Switching environments disposes the active client cleanly without leaking memory or open connections.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST add `ExternalSecretType.KEEPER_SECURITY_CLOUD` (`"keeper-security-cloud"`) to `ExternalSecretType` enum and OpenCollection schema.
- **FR-002**: System MUST define `KeeperSecurityExternalSecret` conforming to `ExternalSecretVariable` in `core-model` and OpenCollection JSON schema.
- **FR-003**: System MUST provide UI in `SettingsDialog` for Keeper Security Cloud with OTT input, "Connect" button, brief connection status check on open, instructions hint, and help link.
- **FR-004**: System MUST NOT store the one-time token anywhere. The generated client configuration MUST be stored encrypted via KSafe (`PlatformStorage.putSecretString`).
- **FR-005**: System MUST allow users to re-connect by entering a new one-time token.
- **FR-006**: System MUST provide UI in `EnvironmentEditDialog` with columns for Name, Record/Title, Target Field (default hint `"password"`), Status (match indicator), Filters (custom key-value filters with default key hint `'url'`), and Description.
- **FR-007**: System MUST support target field extraction for standard types (`login`, `password`, `url`), nested properties (`host.hostName`), custom fields by label (`clientId`, `clientSecret`), and Keeper Notation.
- **FR-008**: System MUST isolate Keeper SDK loading and crypto initialization lazily so there is zero overhead when Keeper is not used.
- **FR-009**: System MUST shut down and release Keeper client resources when switching away from an environment using Keeper.
- **FR-010**: System MUST configure `qa-tests/build.gradle.kts` with `friendPaths` to enable `TestStubs` usage, and include offline E2E tests for `login`, `serverCredentials`, and `OAuth Client Credentials` records.
