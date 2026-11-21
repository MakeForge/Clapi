# Data Model: KeePassXC Secret Manager Integration

## 1. Domain Entities & Schema Extensions

### 1.1 ExternalSecretType Enum
Extends `ExternalSecretType` in `core-model` with `KEEPASSXC`:
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
    KEEPASSXC("keepassxc");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): ExternalSecretType? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}
```

### 1.2 KeePassXcExternalSecret
Extends `ExternalSecretVariable` in `core-model`:
```kotlin
@Serializable
data class KeePassXcExternalSecret(
    override val name: String,
    val url: String,
    val targetField: String = "password",
    val customFilters: Map<String, String> = emptyMap(),
    override val description: Description? = null,
    override val disabled: Boolean? = null,
) : ExternalSecretVariable
```

#### Fields & Validation Rules
| Field | Type | Required | Default | Validation & Semantics |
|---|---|---|---|---|
| `name` | String | Yes | - | Identifier used in `{{_secret.<name>}}`. Non-blank, must not contain whitespace. |
| `url` | String | Yes | - | URL matched against KeePassXC entry URL fields. Must not be blank. |
| `targetField` | String | Yes | `"password"` | Entry property to extract (`password`, `username`, `login`, `totp`, or custom attribute key). |
| `customFilters` | Map<String, String> | No | `{}` | Key-value pairs matching entry attributes. Values support variable placeholders (e.g. `{{env}}`). |
| `description` | Description? | No | `null` | Optional documentation metadata. |
| `disabled` | Boolean? | No | `null` (active) | If `true`, the secret is ignored and not resolved. |

### 1.3 Polymorphic Serialization
Updated `ExternalSecretVariableSerializer` in `core-model`:
```kotlin
object ExternalSecretVariableSerializer :
    JsonContentPolymorphicSerializer<ExternalSecretVariable>(ExternalSecretVariable::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ExternalSecretVariable> {
        val obj = element.jsonObject
        return if (obj.containsKey("url") || obj.containsKey("targetField") || obj.containsKey("customFilters")) {
            KeePassXcExternalSecret.serializer()
        } else {
            HashicorpVaultExternalSecret.serializer()
        }
    }
}
```

---

## 2. In-Memory & UI State Models

### 2.1 Extended MutableExternalSecret
Used by `AppState` and `EnvironmentEditDialog`:
```kotlin
class MutableExternalSecret(
    name: String = "",
    path: String = "",
    description: String = "",
    enabled: Boolean = true,
    url: String = "",
    targetField: String = "password",
    customFilters: Map<String, String> = emptyMap(),
) {
    var name by mutableStateOf(name)
    var path by mutableStateOf(path)
    var description by mutableStateOf(description)
    var enabled by mutableStateOf(enabled)
    var url by mutableStateOf(url)
    var targetField by mutableStateOf(targetField)
    val customFilters = mutableStateMapOf<String, String>().also { it.putAll(customFilters) }
}
```

### 2.2 KeePassXcEntry (Internal In-Memory Wire Model)
Represents a sanitized KeePassXC entry returned from proxy queries:
```kotlin
data class KeePassXcEntry(
    val name: String,
    val login: String,
    val password: String?,
    val uuid: String,
    val group: String? = null,
    val stringFields: Map<String, String> = emptyMap(),
    val totp: String? = null,
)
```

### 2.3 SecretMatchStatus (UI Non-Leaking Indicator)
Ephemeral state displayed in the environment editor:
```kotlin
sealed interface SecretMatchStatus {
    data object Idle : SecretMatchStatus
    data object Checking : SecretMatchStatus
    data class Matched(val count: Int) : SecretMatchStatus
    data object DatabaseLocked : SecretMatchStatus
    data class ConnectionError(val message: String) : SecretMatchStatus
}
```

---

## 3. Storage & Workstation Persistence

| Artifact | Location | Encryption | Exported to OpenCollection? |
|---|---|---|---|
| Environment External Secrets | Collection JSON/YAML (`externalSecrets`) | None (public declarations) | Yes |
| KeePassXC Associate ID | `PlatformStorage` (`clapi.keepassxc.associateId`) | Plain text (public identity) | No |
| KeePassXC Public Key Pair | `PlatformStorage` (`clapi.keepassxc.idKeyPairPublicKey`) | Plain text (public identity) | No |
| Secret Values | In-memory during request execution | Ephemeral (RAM only) | No |
