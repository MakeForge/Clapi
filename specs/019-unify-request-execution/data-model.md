# Data Model: Unify Request Execution, Variable & Secret Resolution, and Module Boundaries

**Feature**: `019-unify-request-execution`  
**Date**: 2026-11-13  
**Spec**: [spec.md](file:///home/user/IdeaProjects/Clapi/specs/019-unify-request-execution/spec.md)

---

## 1. Secret Resolution Models (`:core-network`)

**Package**: `io.github.crustack.clapi.core.network.secrets`

### `SecretResolutionContext`
Replaces the 27-parameter signatures on `SecretResolutionService` with a single immutable context object holding provider configurations, variable layers, and custom filters.

```kotlin
data class SecretResolutionContext(
    val vault: VaultProviderConfig = VaultProviderConfig(),
    val aws: AwsProviderConfig = AwsProviderConfig(),
    val azure: AzureProviderConfig = AzureProviderConfig(),
    val gcp: GcpProviderConfig = GcpProviderConfig(),
    val keeper: KeeperProviderConfig = KeeperProviderConfig(),
    val keepassXc: KeepassXcProviderConfig = KeepassXcProviderConfig(),
    val variableLayers: List<Map<String, String>> = emptyList(),
    val customFilters: List<CustomSecretFilter> = emptyList(),
)

data class VaultProviderConfig(
    val url: String = "",
    val token: String = "",
    val namespace: String = "",
    val mountPath: String = "secret",
)

data class AwsProviderConfig(
    val region: String = "",
    val accessKeyId: String = "",
    val secretAccessKey: String = "",
    val sessionToken: String = "",
)

data class AzureProviderConfig(
    val vaultUrl: String = "",
    val tenantId: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
)

data class GcpProviderConfig(
    val projectId: String = "",
    val serviceAccountJson: String = "",
    val accessToken: String = "",
)

data class KeeperProviderConfig(
    val configJson: String = "",
    val proxyUrl: String = "",
)

data class KeepassXcProviderConfig(
    val dbPath: String = "",
    val masterKey: String = "",
    val keyFilePath: String = "",
)
```

### `ExternalSecretProvider`
Strategy interface implemented by each secret backend (`VaultSecretProvider`, `AwsSecretProvider`, `AzureSecretProvider`, `GcpSecretProvider`, `KeeperSecretProvider`, `KeepassXcSecretProvider`).

```kotlin
interface ExternalSecretProvider {
    /** Canonical provider identifier (e.g., "vault", "aws", "azure", "gcp", "keeper", "keepassxc"). */
    val providerId: String

    /** Returns true if required credentials/endpoints for this provider are present in [context]. */
    fun isConfigured(context: SecretResolutionContext): Boolean

    /**
     * Resolves a single secret by [secretName] and optional JSON [fieldKey] ("value" or null for raw string).
     * Returns null if the secret does not exist or resolution fails.
     */
    suspend fun resolveSecret(
        secretName: String,
        fieldKey: String?,
        context: SecretResolutionContext,
    ): String?
}
```

---

## 2. OAuth2 Token Management Models (`:core-network`)

**Package**: `io.github.crustack.clapi.core.network.oauth2`

### `OAuth2TokenManager`
Centralized service for checking expiry, refreshing via `refresh_token`, or acquiring a new token via `client_credentials` / `password` grants. Used by both `OAuth2DynamicVariableProvider` and request authentication preparation.

```kotlin
data class OAuth2TokenRefreshResult(
    val updatedAuth: AuthConfig,
    val accessToken: String?,
    val tokenType: String = "Bearer",
    val refreshedOrFetched: Boolean = false,
    val error: String? = null,
)
```

**State & Concurrency Rules**:
- Uses an internal `Mutex` so concurrent/back-to-back calls within the same request execution pass do not trigger duplicate HTTP token requests.
- Checks `expiresAt` (`epochMillis`): if `expiresAt == null || expiresAt <= 0L || nowMillis < (expiresAt - expiryBufferMillis)` and `accessToken.isNotBlank()`, returns the existing token without network I/O.
- If expired and `refreshToken.isNotBlank()` and `accessTokenUrl.isNotBlank()`, invokes `OAuth2Client.refreshToken(...)`.
- If no `accessToken` or refresh fails, and `autoFetchToken == true` with `grantType in setOf("client_credentials", "password")`, invokes `OAuth2Client.fetchClientCredentialsToken(...)` or `OAuth2Client.fetchPasswordToken(...)`.
- Invokes an optional `onTokenUpdated: ((AuthConfig) -> Unit)?` callback so `AppState` / `RequestTabState` can persist the refreshed token params.

---

## 3. Exhaustive Template Extraction (`:core-network`)

**Package**: `io.github.crustack.clapi.core.network.execution`

### `RequestDefinition.extractTemplateStrings()` & `GrpcCallRequest.extractTemplateStrings()`
Extension functions that return all candidate strings from a request that may contain `{{...}}` placeholders (for secret scanning and dynamic variable resolution):

- **`RequestDefinition.extractTemplateStrings(): List<String>`**:
  1. `url`
  2. `headers` (for all `enabled` entries: both `key` and `value`)
  3. `queryParams` (for all `enabled` entries: both `key` and `value`)
  4. `cookies` (for all `enabled` entries: both `key` and `value`)
  5. `auth.params.values`
  6. `body.content`
  7. `body.formEntries` (for all `enabled` entries: both `key` and `value`)
  8. `body.formDataEntries` (for all `enabled` entries: `key`, `value`, and `contentType`)
  9. `body.graphQl?.let { listOf(it.query, it.variables, it.operationName) }`
- **`GrpcCallRequest.extractTemplateStrings(): List<String>`**:
  1. `target`
  2. `serviceName`
  3. `methodName`
  4. `payloadJson`
  5. `payloadMessages`
  6. `metadata.entries` (`key` and `value`)

---

## 4. Variable Layer Hierarchy & Environment Secret Filtering (`:ui-shared` & `:core-network`)

### Deterministic Variable Precedence Order
When assembling `effectiveVariableLayers: List<Map<String, String>>` for transport and script resolution, layers are ordered from highest priority (index `0`) to lowest priority:

| Layer Index | Layer Name | Source |
|-------------|------------|--------|
| `0` | `RequestScopedScriptVars` | `clapi.variables.set(k, v)` mutated during pre-request scripts (`Map<String, String>`) |
| `1` | `DynamicVars` | Resolved `{{$...}}` and `{{_auth.*}}` tokens from `DynamicVariableRegistry` |
| `2` | `ResolvedSecrets` | Resolved `{{_secret.*}}` tokens and resolved `secret: true` environment variables from `SecretResolutionService` |
| `3` | `Environment` | Active `EnvState.toVariableMap()` (excluding unresolved blank `secret: true` placeholders) |
| `4..N` | `Folders (leaf-to-root)` | `FolderNode.variableEntries` from immediate parent folder up to root folder |
| `N+1` | `Active Collection` | Active collection's runtime variables (`AppState.getCollectionVariables(collectionId)`) overlaid on `CollectionNode.variableEntries` |
| `N+2` | `Global` | `AppState.globalVariables` |

### `EnvState.toVariableMap()` Filtering Rule
In `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/EnvironmentModels.kt`:
- **Previous (Buggy)**:
  ```kotlin
  fun toVariableMap(): Map<String, String> =
      variables.filter { it.enabled }.associate { it.key to it.value }
  ```
- **Updated**:
  ```kotlin
  fun toVariableMap(): Map<String, String> =
      variables
          .filter { it.enabled && (!it.secret || it.value.isNotBlank()) }
          .associate { it.key to it.value }
  ```
- **Why**: Imported OpenCollection `SecretVariable` entries have `secret = true` and `value = ""`. Filtering out blank secret entries prevents `""` from shadowing resolved secrets or lower-priority folder/collection/global variables.

### Collection-Scoped Variable Storage (`AppState`)
In `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt`:
- Add `collectionVariablesById = mutableStateMapOf<String, SnapshotStateMap<String, String>>()`.
- Provide collection-scoped accessors:
  - `fun collectionVariablesFor(collectionId: String?): SnapshotStateMap<String, String>`
  - Seeded lazily from `CollectionNode.variableEntries` (`filter { it.enabled }.associate { it.key to it.value }`) when first accessed for `collectionId`.
  - When `collectionId` is updated by `clapi.collectionVariables.set(k, v)` during pre-request or post-request scripts, only `collectionVariablesFor(activeCollectionId)` (and the matching `CollectionNode.variableEntries`) is mutated—never other collections.

---

## 5. Canonical Settings Storage Keys (`:ui-shared`)

**Package**: `io.github.crustack.clapi.ui.shared.state`

### `SettingsStorageKeys`
Eliminates key prefix drift between `SettingsRepository.kt` and `RequestVariableResolution.kt`:

```kotlin
object SettingsStorageKeys {
    const val SETTINGS_JSON = "clapi.settings.v1"
    const val VAULT_TOKEN = "clapi.settings.vaultToken"
    const val AWS_SECRET_ACCESS_KEY = "clapi.settings.awsSecretAccessKey"
    const val AWS_SESSION_TOKEN = "clapi.settings.awsSessionToken"
    const val AZURE_CLIENT_SECRET = "clapi.settings.azureClientSecret"
    const val GCP_SERVICE_ACCOUNT_JSON = "clapi.settings.gcpServiceAccountJson"
    const val GCP_ACCESS_TOKEN = "clapi.settings.gcpAccessToken"
    const val KEEPER_CONFIG = "clapi.settings.keeperConfig"
    const val KEEPASSXC_KEY = "clapi.settings.keepassxcKey"

    // Legacy fallback keys for transparent migration if a user previously stored under "settings.*"
    const val LEGACY_VAULT_TOKEN = "settings.vaultToken"
    const val LEGACY_AWS_SECRET_ACCESS_KEY = "settings.awsSecretAccessKey"
    const val LEGACY_AWS_SESSION_TOKEN = "settings.awsSessionToken"
}
```
