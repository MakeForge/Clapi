# Data Model: Keeper Security Cloud Secret Manager Integration

**Branch**: `018-keeper-secret-manager` | **Date**: 2026-11-13

## 1. Domain Entities & Schemas

### 1.1 OpenCollection Model (`core-model`)

#### `ExternalSecretType` Enum Extension
```kotlin
@Serializable
enum class ExternalSecretType(val value: String) {
    @SerialName("hashicorp-vault-server")
    HASHICORP_VAULT_SERVER("hashicorp-vault-server"),
    @SerialName("hashicorp-vault-cloud")
    HASHICORP_VAULT_CLOUD("hashicorp-vault-cloud"),
    @SerialName("aws-secrets-manager")
    AWS_SECRETS_MANAGER("aws-secrets-manager"),
    @SerialName("azure-key-vault")
    AZURE_KEY_VAULT("azure-key-vault"),
    @SerialName("gcp-secrets-manager")
    GCP_SECRETS_MANAGER("gcp-secrets-manager"),
    @SerialName("one-password")
    ONE_PASSWORD("one-password"),
    @SerialName("keepassxc")
    KEEPASSXC("keepassxc"),
    @SerialName("keeper-security-cloud")
    KEEPER_SECURITY_CLOUD("keeper-security-cloud");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): ExternalSecretType? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}
```

#### `KeeperSecurityExternalSecret` Data Class
```kotlin
@Serializable
data class KeeperSecurityExternalSecret(
    override val name: String,
    val recordTitle: String = "",
    val targetField: String = "password",
    val customFilters: Map<String, String> = emptyMap(),
    override val description: Description? = null,
    override val disabled: Boolean? = null,
) : ExternalSecretVariable
```

#### `ExternalSecretVariableSerializer` Update
```kotlin
object ExternalSecretVariableSerializer :
    JsonContentPolymorphicSerializer<ExternalSecretVariable>(ExternalSecretVariable::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ExternalSecretVariable> {
        val obj = element.jsonObject
        return when {
            obj["type"]?.jsonPrimitive?.contentOrNull == "keeper-security-cloud" ||
                obj.containsKey("recordTitle") ->
                KeeperSecurityExternalSecret.serializer()
            obj["type"]?.jsonPrimitive?.contentOrNull == "gcp-secrets-manager" ||
                obj.containsKey("projectId") ||
                (obj.containsKey("secretId") && !obj.containsKey("secretName") && !obj.containsKey("vaultName")) ->
                GcpSecretsManagerExternalSecret.serializer()
            obj.containsKey("vaultName") || obj["type"]?.jsonPrimitive?.contentOrNull == "azure-key-vault" ->
                AzureKeyVaultExternalSecret.serializer()
            obj.containsKey("secretName") || obj["type"]?.jsonPrimitive?.contentOrNull == "aws-secrets-manager" || obj.containsKey("secretId") ->
                AwsSecretsManagerExternalSecret.serializer()
            obj.containsKey("url") || (obj.containsKey("targetField") && !obj.containsKey("path")) ->
                KeePassXcExternalSecret.serializer()
            else -> HashicorpVaultExternalSecret.serializer()
        }
    }
}
```

---

### 1.2 Keeper Client Configuration (`core-network`)

```kotlin
@Serializable
data class KeeperClientConfig(
    val hostname: String,
    val clientId: String,
    val privateKey: String,
    val appKey: String? = null,
    val clientKey: String? = null,
    val appOwnerPublicKey: String? = null,
    val serverPublicKeyId: String? = null,
    val serverPublicKey: String? = null,
) {
    val isBound: Boolean get() = !appKey.isNullOrBlank()
}
```

---

### 1.3 Resolution Secret Declaration (`core-network`)

Update `SecretDeclaration` in `SecretResolutionService.kt`:
```kotlin
data class SecretDeclaration(
    val name: String,
    val path: String = "",
    val enabled: Boolean = true,
    val providerType: String = "hashicorp-vault-server",
    val url: String = "",
    val targetField: String = "password",
    val customFilters: Map<String, String> = emptyMap(),
    val secretId: String = path,
    val version: String = "latest",
    val recordTitle: String = path.ifBlank { url },
)
```

---

### 1.4 UI State Representation (`ui-shared`)

#### `AppSettings`
- `var keeperConfigJson: String`: Stored encrypted in KSafe via `PlatformStorage.putSecretString("settings.keeperConfig", ...)`.
- Transient connection health state: `var keeperConnectionStatus: KeeperConnectionStatus`.

```kotlin
enum class KeeperConnectionStatus {
    UNKNOWN,
    CHECKING,
    CONNECTED,
    DISCONNECTED,
    ERROR,
}
```

#### `MutableExternalSecret` Mapping
In `MutableExternalSecret`, when `workingProvider == "keeper-security-cloud"`:
- `path`: Stores `recordTitle`
- `targetField`: Stores target field (defaults to `"password"`)
- `customFilters`: Stores custom key-value filters
- `enabled`: Toggle for active resolution
- `description`: Optional notes

---

## 2. Validation & Matching Rules

1. **Record Title / UID Matching**:
   - Matches record if `record.data.title.equals(recordTitle, ignoreCase = true) || record.recordUid == recordTitle`.
   - If `recordTitle` is blank, selection relies entirely on `customFilters`.

2. **Custom Filters Matching**:
   - Every entry `(key, value)` in `customFilters` must match the record:
     - Dotted or exact match against record title (`key.lowercase() == "title"`).
     - Match against record type (`key.lowercase() == "type"`).
     - Match against field label (`field.label.equals(key, ignoreCase = true)`).
     - Match against field type (`fieldType(field).equals(key, ignoreCase = true)`).
     - Dotted host property match (`key.lowercase() == "host.hostname" || key.lowercase() == "host.port"`).

3. **Target Field Resolution Hierarchy**:
   1. Standard password field if `targetField.lowercase() == "password"`: `record.getPassword()` or field of type `Password` or label `"password"`.
   2. Standard login field if `targetField.lowercase() in setOf("login", "user", "username")`: field of type `Login` or label `"login"`.
   3. Field label exact/case-insensitive match (e.g. `"clientId"`, `"clientSecret"`).
   4. Field type match (e.g. `"url"`, `"text"`).
   5. Dotted JSON/object property: e.g. `"host.hostName"` returns the hostName from the `host` field.
   6. Fallback to Keeper Notation: `Notation.getValue(secrets, query)`.
