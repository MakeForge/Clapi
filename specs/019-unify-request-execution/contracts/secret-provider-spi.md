# Contract: External Secret Provider SPI & SecretResolutionService

**Feature**: `019-unify-request-execution`  
**Module**: `:core-network` (`io.github.crustack.clapi.core.network.secrets`)

---

## 1. `ExternalSecretProvider` SPI

```kotlin
package io.github.crustack.clapi.core.network.secrets

interface ExternalSecretProvider {
    val providerId: String

    fun isConfigured(context: SecretResolutionContext): Boolean

    suspend fun resolveSecret(
        secretName: String,
        fieldKey: String?,
        context: SecretResolutionContext,
    ): String?
}
```

### Registered Providers
1. **`VaultSecretProvider`** (`providerId = "vault"`):
   - Configured when `context.vault.url.isNotBlank() && context.vault.token.isNotBlank()`.
   - Resolves KV v2 secrets via `VaultClient`.
2. **`AwsSecretProvider`** (`providerId = "aws"`):
   - Configured when `context.aws.region.isNotBlank() && context.aws.accessKeyId.isNotBlank() && context.aws.secretAccessKey.isNotBlank()`.
   - Resolves secrets via `AwsSecretsManagerClient`.
3. **`AzureSecretProvider`** (`providerId = "azure"`):
   - Configured when `context.azure.vaultUrl.isNotBlank() && context.azure.tenantId.isNotBlank() && context.azure.clientId.isNotBlank() && context.azure.clientSecret.isNotBlank()`.
   - Resolves secrets via `AzureKeyVaultClient`.
4. **`GcpSecretProvider`** (`providerId = "gcp"`):
   - Configured when `context.gcp.projectId.isNotBlank() && (context.gcp.serviceAccountJson.isNotBlank() || context.gcp.accessToken.isNotBlank())`.
   - Resolves secrets via `GcpSecretManagerClient`.
5. **`KeeperSecretProvider`** (`providerId = "keeper"`):
   - Configured when `context.keeper.configJson.isNotBlank()`.
   - Resolves secrets via `KeeperSecretsManagerClient` using Keeper notation (`recordUid/field/label` or `secretName` + `fieldKey`).
6. **`KeepassXcSecretProvider`** (`providerId = "keepassxc"`):
   - Configured when `context.keepassXc.dbPath.isNotBlank() && (context.keepassXc.masterKey.isNotBlank() || context.keepassXc.keyFilePath.isNotBlank())`.
   - Resolves secrets via `KeepassXcClient`.

---

## 2. `SecretResolutionService` API

```kotlin
package io.github.crustack.clapi.core.network.secrets

class SecretResolutionService(
    private val providers: List<ExternalSecretProvider>,
    private val customFilterClient: CustomSecretFilterClient? = null,
) {
    /**
     * Scans [texts] for `{{_secret.<provider>.<name>.<key>}}` and `{{_secret.<name>.<key>}}`
     * placeholders and resolves them using [context].
     */
    suspend fun resolveSecrets(
        texts: List<String>,
        context: SecretResolutionContext,
    ): Map<String, String>

    /**
     * Resolves environment variables marked with `secret = true` across configured providers
     * in fallback order (Vault -> AWS -> Azure -> GCP -> Keeper -> KeePassXC).
     */
    suspend fun resolveEnvironmentSecrets(
        secretVarNames: List<String>,
        context: SecretResolutionContext,
    ): Map<String, String>
}
```

### Invariants
- **Custom Filter Interpolation**: Before evaluating custom regex filters on `texts`, each text string MUST be interpolated via `VariableResolver.resolve(text, context.variableLayers)` so whitespace-padded variables (e.g., `{{ myVar }}`) and nested variables are expanded prior to regex matching.
- **No Unresolved Token Destruction**: If a secret cannot be resolved (provider not configured or secret missing), `resolveSecrets` MUST NOT map the key to `""`; it omits the key from the returned map so `VariableResolver` preserves `{{_secret....}}` untouched for clear user diagnostics.
