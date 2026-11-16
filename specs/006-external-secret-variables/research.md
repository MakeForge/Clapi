# Research: External Secret Variables

This document consolidates architectural decisions, technology selections, and integration patterns for implementing External Secret Variables with HashiCorp Vault Server in Clapi.

---

## 1. Encrypted Storage for Vault Token (`ksafe`)

### Context
Vault tokens provide administrative or service-level access to secret backends. Storing tokens in plain text in preferences or collection files poses significant security vulnerabilities. `PlatformStorage` needs an extension to securely encrypt and decrypt sensitive strings across platforms.

### Decision
Use `eu.anifantakis:ksafe:3.3.0` (latest stable version on Maven Central).
Extend `PlatformStorage` with:
- `putSecretString(key: String, value: String)`
- `getSecretString(key: String): String?`
- `removeSecretString(key: String)` (or unified with `remove(key)`)

On desktop JVM, instantiate an isolated `KSafe` instance (e.g., `KSafe(fileName = "clapi-secrets")`) and use synchronous direct access:
```kotlin
private val ksafe by lazy { KSafe(fileName = "clapi-secrets") }

actual fun putSecretString(key: String, value: String) {
    ksafe.putDirect(key, value)
}

actual fun getSecretString(key: String): String? {
    return ksafe.getDirect<String?>(key, null)
}
```

### Rationale
- `ksafe` is a pure Kotlin Multiplatform library implementing AES-GCM payload encryption with platform-native OS keyring custody (Linux Secret Service / GNOME Keyring / KWallet, macOS Keychain, Windows DPAPI).
- `putDirect` and `getDirect` provide non-blocking, synchronous operations matching `PlatformStorage`'s existing API contract without leaking `runBlocking` or blocking coroutines.
- If a secure keyring tier is temporarily unavailable, `ksafe` fails closed or degrades safely to documented permissions-protected local files (0700 permissions).

### Alternatives Considered
- *Plain Java Preferences*: Plaintext on disk; rejected for security reasons.
- *Java Cryptography Architecture (JCE) with hardcoded master key*: Obfuscation rather than true key custody; susceptible to memory and file analysis.
- *Custom OS Keyring integrations*: High maintenance overhead and complex cross-platform JNI bindings compared to the tested `ksafe` library.

---

## 2. OpenCollection v1.0.0 Schema Conformance for External Secrets

### Context
OpenCollection v1.0.0 specification defines `externalSecrets` at the `Environment` level (`scratch/opencollection_v1.0.0.json`). It specifies provider types and concrete secret models.

### Decision
Conform strictly to OpenCollection v1.0.0:
1. `Environment` schema is extended with:
   ```kotlin
   val externalSecrets: ExternalSecrets? = null
   ```
2. `ExternalSecrets` definition:
   ```kotlin
   @Serializable
   data class ExternalSecrets(
       val type: String, // "hashicorp-vault-server"
       val variables: List<ExternalSecretVariable> = emptyList(),
   )
   ```
3. `ExternalSecretVariable` polymorphism:
   ```kotlin
   @Serializable(with = ExternalSecretVariableSerializer::class)
   sealed interface ExternalSecretVariable {
       val name: String
       val description: Description?
       val disabled: Boolean?
   }

   @Serializable
   data class HashicorpVaultExternalSecret(
       override val name: String,
       val path: String,
       override val description: Description? = null,
       override val disabled: Boolean? = null,
   ) : ExternalSecretVariable
   ```
4. Serialization and Deserialization:
   - When deserializing `ExternalSecretVariable`, the presence of `path` (or provider type `hashicorp-vault-server`) selects `HashicorpVaultExternalSecret.serializer()`.

### Rationale
- Full fidelity with OpenCollection ensures interoperability across API client tooling.
- Collections remain completely shareable: only variable declarations (`name`, `path`, `disabled`, `description`) are serialized; tokens and provider URLs remain private to local machine settings.

### Alternatives Considered
- *Embedding Vault credentials inside Collection JSON*: Violates zero-credential leakage principles; would accidentally leak tokens into git repositories.
- *Ad-hoc proprietary JSON format*: Breaks OpenCollection compatibility.

---

## 3. HashiCorp Vault HTTP Client & KV v2 Engine

### Context
HashiCorp Vault Server stores key-value pairs in the Key-Value Version 2 (KV v2) secret engine. When resolving secrets at runtime, Clapi needs to query the Vault HTTP API.

### Decision
Implement `HashiCorpVaultClient` using Ktor Client (`io.ktor:ktor-client-core` with `ContentNegotiation` and `kotlinx.serialization.json`):
```kotlin
interface HashiCorpVaultClient {
    suspend fun fetchSecret(
        vaultUrl: String,
        token: String,
        secretPath: String,
        mountPath: String = "secret",
        namespace: String? = null,
    ): Map<String, String>
}
```
Endpoint format:
`$cleanUrl/v1/$cleanMount/data/$cleanPath`
Headers:
- `X-Vault-Token`: `token`
- `X-Vault-Namespace`: `namespace` (if non-blank)
- `Accept`: `application/json`

Response parsing:
```kotlin
@Serializable
data class VaultKvV2Response(
    val data: VaultKvV2Data,
)

@Serializable
data class VaultKvV2Data(
    val data: JsonObject,
    val metadata: JsonObject? = null,
)
```
Convert `JsonObject` primitives into `Map<String, String>` where each string or primitive value is represented as a string.

### Rationale
- Existing Ktor client infrastructure in `core-network` can be reused directly without introducing extra HTTP libraries.
- The standard Vault KV v2 API wraps payloads in a `data.data` envelope.
- Read-only queries use HTTP `GET`; no write (`POST`/`PUT`) operations are supported or permitted.

### Alternatives Considered
- *Third-party Java Vault driver (e.g. BetterCloud vault-java-driver)*: Heavyweight, brings outdated Jackson and Apache HTTP client dependencies, and is not Kotlin Multiplatform compatible.

---

## 4. Secret Variable Resolution Pipeline & Template Syntax

### Context
Users need to reference external secrets using `{{_secret.secretName}}`. When a request is executed, these tokens must be resolved from the active environment's external secrets and the configured secret manager.

### Decision
1. **Naming & Syntax**:
   - Variables are referenced in templates as `{{_secret.secretName}}` (e.g. `{{_secret.client_id}}`).
   - `_secret.` is the reserved prefix indicating external secret resolution.
2. **Resolution Pipeline**:
   - Before executing a request (in `RequestExecutor` / `RequestExecutionService`), identify any `{{_secret.<name>}}` tokens present in the request definition (URL, query params, headers, body, auth).
   - If secrets are required:
     - Check the active environment for an `externalSecrets` block of type `hashicorp-vault-server`.
     - Find the corresponding `HashicorpVaultExternalSecret` declaration by `name`.
     - If the secret is not found or is marked `disabled: true`, throw a user-visible execution error.
     - Look up HashiCorp Vault settings in `AppSettings` (Server URL, mount path, namespace, token). If unconfigured or missing token, abort with a clear configuration error.
     - Group requested secrets by `path` to fetch each unique Vault secret path only once per request execution (in-memory per-execution cache).
     - Fetch secrets from Vault; extract the key matching `name` (or fallback to the single value if the secret contains only one key-value pair).
     - Inject a resolved secret layer `mapOf("_secret.<name>" to secretValue)` into the `variableLayers` passed to `VariableResolver.resolve()`.
3. **Variable Fallback (`secret: true`)**:
   - If an environment variable has `secret: true` without an inline value, resolution checks process environment variables (`System.getenv()`), then active environment `externalSecrets` matching the variable name.

### Rationale
- Resolving secrets asynchronously before the network dispatch ensures that `VariableResolver.resolve()` remains pure, synchronous, and fast.
- Path-based grouping prevents duplicate network requests to Vault when multiple variables (e.g. `client_id` and `client_secret`) reside in the same Vault secret path (`payment/foo`).
- Clear error messages explain whether the problem is missing configuration, invalid token, network timeout, or non-existent secret path.

### Alternatives Considered
- *Synchronous blocking fetch inside `VariableResolver`*: Blocks UI threads and breaks variable preview rendering.
- *Prefetching all secrets in the background on environment change*: Inefficient, causes unnecessary Vault token usage, and risks rate limiting.

---

## 5. UI Integration: Settings & Environment Editor

### Context
Users need UI controls to configure Vault credentials globally and declare external secrets per environment.

### Decision
1. **Global Settings Dialog (`SettingsDialog.kt`)**:
   - Add new section `SECRETS` (label: "Secrets Manager", icon: `Icons.Default.VpnKey` or `Icons.Default.Security`).
   - Provider dropdown: "HashiCorp Vault (Server)" (`hashicorp-vault-server`).
   - Input fields:
     - Server URL (with placeholder `http://127.0.0.1:8200`)
     - Mount Path (placeholder/default `secret`)
     - Namespace (optional)
     - Vault Token (masked with password visual transformation and visibility toggle)
   - Stored in `AppSettings` and persisted via `SettingsRepository` using `PlatformStorage.putString` and `PlatformStorage.putSecretString`.
2. **Environment Edit Dialog (`EnvironmentEditDialog.kt`)**:
   - Introduce tab selector at the top of the dialog:
     - "Variables" tab (existing variable table)
     - "External Secrets" tab (new table)
   - "External Secrets" tab:
     - Provider dropdown: "HashiCorp Vault (Server)"
     - Table listing: Enabled checkbox, Variable Name, Secret Path, Description, Delete button
     - "Add Secret" button to append a new row
     - Backed by `EnvState.externalSecrets` and serialized into `Environment.externalSecrets`.
3. **Editor Autocomplete (`VariableHighlight.kt`)**:
   - In `getAvailableVariables()` or `VariableDropdownList`:
   - Inspect `state.selectedEnvironment?.externalSecrets`.
   - Append active external secrets formatted as `_secret.<name>` below regular variables with a visual "Secret" badge.

### Rationale
- Keeps application-level secrets separated from collection-level environment data.
- Provides a clean, familiar UX consistent with existing table and dialog designs in Clapi.

---

## 6. QA Test Fixture & Verification

### Context
`qa-tests/secret-managers-compose.yml` provides a local HashiCorp Vault instance seeded with `secret/payment/foo`:
`client_id="foo_client_123"`
`client_secret="sk_live_secret_456"`.

### Decision
1. Add integration tests in `qa-tests` that connect to Vault on `http://127.0.0.1:8200` with token `root-dev-token`.
2. Verify that:
   - `HashiCorpVaultClient` fetches `payment/foo` and deserializes `client_id` and `client_secret`.
   - `RequestExecutionService` / `RequestExecutor` resolves `{{_secret.client_id}}` and `{{_secret.client_secret}}` in headers/body.
   - Disabled secrets and invalid tokens produce proper error feedback.
   - OpenCollection export preserves `externalSecrets` but omits `root-dev-token` and server URL.

### Rationale
- Validates the entire flow against real Vault behavior using Docker Compose without mocked compromises.
