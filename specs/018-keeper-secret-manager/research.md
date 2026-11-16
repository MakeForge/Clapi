# Research: Keeper Security Cloud Secret Manager Integration

**Branch**: `018-keeper-secret-manager` | **Date**: 2026-11-13

## Overview

This document resolves all technical decisions and unknown integration points for integrating Keeper Security Cloud (`keeper-security-cloud`) as an external secret provider in Clapi, covering:
1. Keeper Secrets Manager (KSM) Java SDK usage and dependency setup
2. One-Time Token (OTT) redemption, binding lifecycle, and KSafe encrypted config persistence
3. Lazy loading and zero static overhead when Keeper is unused
4. Environment-aware lifecycle management (`KeeperClientManager`)
5. Dynamic record field filtering and target field extraction for standard and custom record types
6. Offline end-to-end testing strategy in `qa-tests` using `TestStubs` and friend paths

---

## 1. Keeper Secrets Manager SDK & Crypto Overhead

### Research Questions
- What dependency is required to interact with Keeper Secrets Manager?
- Does Keeper SDK require BouncyCastle FIPS, or can it use Java's default crypto provider?
- How to ensure there is zero overhead or classloading of crypto/SDK when Keeper is not used?

### Findings & Analysis
- **Artifact**: `com.keepersecurity.secrets-manager:core:17.2.0` (or `17.2.1+`).
- **Cryptographic Provider**: As documented in `scratch/keeper/keeper_sdk.md` and confirmed in `scratch/keeper/sdk/.../CryptoUtils.kt`, Keeper SDK checks for `BCFIPS` or `BC`, and if absent, defaults cleanly to Java JCE (`KeyFactory.getInstance("EC")`, `KeyPairGenerator.getInstance("EC")`, `AlgorithmParameters.getInstance("EC")`, and `MessageDigest.getInstance("SHA-256")`). Java 17+ and 21 standard runtime JCE fully supports EC with curve `secp256r1`. Thus, **no external Bouncy Castle library or static provider registration is required**.
- **Static Overhead Prevention**:
  - Do NOT call `Security.addProvider(...)` or touch any Keeper classes on application startup.
  - Keep all Keeper SDK calls encapsulated within `KeeperClient` / `KeeperSecretsManagerClient` and `KeeperClientManager`.
  - When Keeper is not configured or an environment does not use `keeper-security-cloud`, zero Keeper classes are loaded.

### Decisions
- **Decision**: Add `com.keepersecurity.secrets-manager:core:17.2.0` to `gradle/libs.versions.toml` and depend on it only in `core-network` (under `desktopMain`) and `qa-tests`. Do NOT include BouncyCastle libraries.
- **Rationale**: Eliminates unnecessary JAR size and avoids static crypto provider initialization. Standard JVM 21 JCE handles all elliptic curve operations required by Keeper.

---

## 2. One-Time Token Redemption, Storage Binding & KSafe Persistence

### Research Questions
- How does Keeper Secrets Manager initialize configuration from a one-time token?
- When is the one-time token redeemed?
- How is the resulting client configuration structured and stored?

### Findings & Analysis
- **Token Redemption & Binding Lifecycle**:
  - `initializeStorage(storage: KeyValueStorage, oneTimeToken: String)` parses the token (region prefix e.g. `US`, `EU`, or custom host), generates an EC keypair, computes `clientId`, and writes `hostname`, `clientId`, `clientKey`, and `privateKey` to storage.
  - The one-time token is **not yet redeemed** until the first network call (`fetchAndDecryptSecrets` via `getSecrets`).
  - During `getSecrets(options)`:
    - The client sends a request signed with `clientKey` to the Keeper API (`get_secret`).
    - The server binds the application, redeems the OTT, and returns `encryptedAppKey`.
    - The SDK decrypts `encryptedAppKey` with `clientKey` to obtain `appKey`.
    - The SDK deletes `clientKey` and `publicKey` from storage and writes `appKey` and `appOwnerPublicKey`.
  - At this point, the storage contains the fully bound configuration:
    - `hostname`: e.g. `keepersecurity.eu`
    - `clientId`: Base64 string
    - `privateKey`: DER encoded private key
    - `appKey`: Base64 string
    - `appOwnerPublicKey`: Base64 string
    - `serverPublicKeyId`: e.g. `"10"`
- **JSON Configuration Serialization**:
  - The SDK's `InMemoryStorage` stores these keys in an internal map.
  - The bound configuration can be represented as a JSON string:
    ```json
    {
      "hostname": "keepersecurity.eu",
      "clientId": "...",
      "privateKey": "...",
      "appKey": "...",
      "appOwnerPublicKey": "...",
      "serverPublicKeyId": "10"
    }
    ```
- **KSafe Storage**:
  - Clapi's `PlatformStorage.putSecretString("settings.keeperConfig", configJson)` securely encrypts and persists this JSON string using KSafe (`KSafe(fileName = "clapi_secrets")`).
  - The one-time token is discarded immediately upon clicking "Connect" and is NEVER stored in preferences, KSafe, or collection files.
  - To reconnect, the user enters a new one-time token, which overwrites the previous config in KSafe.

### Decisions
- **Decision**: In `SettingsDialog`, clicking "Connect" executes `initializeStorage(storage, token)` and immediately triggers an initial `getSecrets(options)` call. If successful, the bound configuration is extracted as JSON and saved to `PlatformStorage.putSecretString(PREFIX + "keeperConfig", json)`.
- **Rationale**: Validates the connection immediately, redeems the OTT, confirms permissions, and ensures KSafe only stores a fully bound and working configuration.

---

## 3. Environment Lifecycle and Resource Cleanup

### Research Questions
- How to ensure only the currently used secret manager client is loaded in memory?
- How to release Keeper client resources when switching environments?

### Findings & Analysis
- In Clapi, `AppState` manages the active environment index (`_selectedEnvIndex`).
- For KeePassXC, `DefaultKeePassXcClientManager.Shared.onEnvironmentSwitched(hasKeePassXcSecrets)` is called when switching away.
- A similar manager pattern (`DefaultKeeperClientManager.Shared`) can manage the lifecycle of the Keeper client:
  - `suspend fun getClient(configJson: String): KeeperClient`
  - `suspend fun onEnvironmentSwitched(hasKeeperSecrets: Boolean)`
  - `suspend fun shutdown()`
  - `fun isConnected(): Boolean`
- When switching to an environment where `externalSecretsProvider != "keeper-security-cloud"` or where no Keeper secrets are enabled, `KeeperClientManager.onEnvironmentSwitched(false)` is invoked to release client instances, cached record trees, and network sessions.

### Decisions
- **Decision**: Implement `KeeperClientManager` and `DefaultKeeperClientManager.Shared` in `core-network`. Wire it into `AppState.kt` so that on environment switch, both `KeePassXcClientManager` and `KeeperClientManager` release unused clients.

---

## 4. Dynamic Field Filtering and Target Field Extraction

### Research Questions
- What record types exist in Keeper and how are their fields represented?
- How should custom filters work against Keeper records?
- How should target fields be extracted?

### Findings & Analysis
- Inspecting `scratch/keeper/records.json` and `qa-tests/src/test/resources/secrets/keeper/test-records.json`:
  1. **Standard `login` record** (`clapi-test-user`):
     - `title`: `clapi-test-user`
     - `type`: `login`
     - Fields:
       - `type: "login"`, value: `["clapi-test-user"]`
       - `type: "password"`, value: `["clapi-password"]`
       - `type: "url"`, value: `["https://clapi.api.com"]`
  2. **Standard `serverCredentials` record** (`clapi-test-server`):
     - `title`: `clapi-test-server`
     - `type`: `serverCredentials`
     - Fields:
       - `type: "host"`, value: `[{"hostName": "clapi.api.com", "port": "1234"}]`
       - `type: "login"`, value: `["clapi-user"]`
       - `type: "password"`, value: `["clapi-password"]`
  3. **Custom `OAuth Client Credentials` record** (`clapi-test-custom-client-credentials`):
     - `title`: `clapi-test-custom-client-credentials`
     - `type`: `OAuth Client Credentials`
     - Fields:
       - `type: "text"`, label: `"clientId"`, value: `["clapi-client-id"]`
       - `type: "password"`, label: `"clientSecret"`, value: `["clapie-client-secret"]`
- **Matching Rules (`matchesFilter`)**:
  - A record matches if its `title` or `recordUid` equals `declaration.recordTitle` (if specified).
  - All `customFilters` must match:
    - `filterKey` matches `title`, `type`, or `recordUid`.
    - `filterKey` matches any field's `label` (e.g. `clientId == "clapi-client-id"`).
    - `filterKey` matches any field's `type` (e.g. `url == "https://clapi.api.com"`).
    - For host fields, matching on hostName or port.
- **Target Field Extraction (`extractTargetField`)**:
  - Default: `"password"`. Checks `record.getPassword()` or field of type `Password` or label `"password"`.
  - `"login"` or `"username"`: Checks field of type `Login` or label `"login"`.
  - Label match: If `targetField` equals a field's `label` (e.g. `"clientId"`, `"clientSecret"`), extract that field's value.
  - Field type match: If `targetField` equals a field's `type` (e.g. `"url"`), extract that field's value.
  - Nested property match: For dotted notation like `"host.hostName"` or `"host.port"`, extract the object property from the host field.
  - Notation match: Standard Keeper notation (e.g. `field/login` or `clapi-test-user/field/url`) fallback via `Notation.getValue`.

### Decisions
- **Decision**: Create `KeeperRecordFilter` in `core-network` with `matchesFilter` and `extractTargetFieldValue`, supporting standard fields, custom labelled fields, dotted host properties, and Keeper notation.

---

## 5. Offline E2E Testing Strategy in `qa-tests`

### Research Questions
- Keeper Cloud has no official Docker emulator. How can we write comprehensive, offline E2E tests in `qa-tests`?
- How to access the Keeper SDK's internal `TestStubs` from `qa-tests`?

### Findings & Analysis
- **Kotlin Friend Paths**:
  In Kotlin, internal symbols across Gradle modules/JARs are hidden by default unless declared as friend paths. By configuring `qa-tests/build.gradle.kts`:
  ```kotlin
  tasks.named<org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile>("compileTestKotlin") {
      friendPaths.from(
          configurations.testCompileClasspath.map { classpath ->
              classpath.filter { file ->
                  file.name.startsWith("core-17.") && file.path.contains("keepersecurity")
              }
          }
      )
  }
  ```
  The test classes in `qa-tests` can directly access `com.keepersecurity.secretsManager.core.TestStubs` without visibility errors.
- **Offline Stubbing / Emulation**:
  - `TestStubs.transmissionKeyStub` allows providing the transmission key stub.
  - `SecretsManagerOptions(storage, testPostFunction)` accepts a custom `postFunction` lambda to return canned HTTP responses.
  - In addition, for direct secret resolution E2E tests, `KeeperClient` can be mocked or backed by an offline implementation that loads `qa-tests/src/test/resources/secrets/keeper/test-records.json` and runs full request execution pipelines with `RequestExecutionService` and local Netty server, testing `login`, `serverCredentials`, and `OAuth Client Credentials` records end-to-end.

### Decisions
- **Decision**: Configure `friendPaths` in `qa-tests/build.gradle.kts`. Implement `KeeperSecurityCloudIntegrationTest` in `qa-tests` verifying all 3 record types against a local mock server and full variable substitution flow.
