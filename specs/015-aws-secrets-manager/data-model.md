# Data Model: AWS Secrets Manager Integration

## 1. Domain Entities & Schema Extensions

### 1.1 ExternalSecretType Enum
Extends / verifies `ExternalSecretType` in `core-model` (`OpenCollectionEnvironment.kt`):
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

### 1.2 AwsSecretsManagerExternalSecret
Extends `ExternalSecretVariable` in `core-model` (`OpenCollectionEnvironment.kt`):
```kotlin
@Serializable
data class AwsSecretsManagerExternalSecret(
    override val name: String,
    val secretName: String,
    val targetField: String = "",
    val customFilters: Map<String, String> = emptyMap(),
    override val description: Description? = null,
    override val disabled: Boolean? = null,
) : ExternalSecretVariable
```

#### Fields & Validation Rules
| Field | Type | Required | Default | Validation & Semantics |
|---|---|---|---|---|
| `name` | String | Yes | — | Identifier used in request templates as `{{_secret.<name>}}`. Must be non-blank. |
| `secretName` | String | Yes | — | The AWS Secrets Manager secret name or ARN (e.g. `local/payment/foo`, `prod/api/stripe`). Must be non-blank. |
| `targetField` | String | No | `""` | The key inside a multi-value JSON secret payload (e.g. `client_id`, `client_secret`). If blank or if the secret is a plain text string, the entire secret string is returned. |
| `customFilters` | Map<String, String> | No | `{}` | Optional key-value matching criteria. Filter values support environment variable placeholders (e.g. `{{stage}}`). |
| `description` | Description? | No | `null` | Optional documentation / human-readable note. |
| `disabled` | Boolean? | No | `null` | If `true`, secret is treated as disabled and will cause execution to halt if referenced in an active request. |

### 1.3 Polymorphic Serialization
Updated `ExternalSecretVariableSerializer` in `core-model`:
```kotlin
object ExternalSecretVariableSerializer :
    JsonContentPolymorphicSerializer<ExternalSecretVariable>(ExternalSecretVariable::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ExternalSecretVariable> {
        val obj = element.jsonObject
        return when {
            obj.containsKey("secretName") || obj.containsKey("secretId") -> AwsSecretsManagerExternalSecret.serializer()
            obj.containsKey("url") || (obj.containsKey("targetField") && !obj.containsKey("path")) -> KeePassXcExternalSecret.serializer()
            else -> HashicorpVaultExternalSecret.serializer()
        }
    }
}
```

---

## 2. Global Settings & In-Memory State Models

### 2.1 AWS Secrets Manager Global Configuration
Added to `AppSettings` in `ui-shared`:
```kotlin
// Secrets Manager (AWS)
var awsRegion          by mutableStateOf("us-east-1")
var awsAccessKeyId     by mutableStateOf("")
var awsSecretAccessKey by mutableStateOf("")
var awsSessionToken    by mutableStateOf("")
var awsEndpointUrl     by mutableStateOf("")
var awsVersionStage    by mutableStateOf("AWSCURRENT")
```

#### Settings Persistence (`PlatformStorage`)
| Setting Property | Storage Type | Storage Key | Masked in UI |
|---|---|---|---|
| `awsRegion` | Plain String | `settings.awsRegion` | No (default `us-east-1`) |
| `awsAccessKeyId` | Plain String | `settings.awsAccessKeyId` | No |
| `awsSecretAccessKey` | Encrypted Secret | `settings.awsSecretAccessKey` | Yes (Password input) |
| `awsSessionToken` | Encrypted Secret | `settings.awsSessionToken` | Yes (Password input) |
| `awsEndpointUrl` | Plain String | `settings.awsEndpointUrl` | No (Optional URL) |
| `awsVersionStage` | Plain String | `settings.awsVersionStage` | No (default `AWSCURRENT`) |

### 2.2 Environment UI State Model (`MutableExternalSecret`)
In `ui-shared/state/EnvironmentModels.kt`:
```kotlin
class MutableExternalSecret(
    name: String = "",
    path: String = "",
    description: String = "",
    enabled: Boolean = true,
    url: String = "",
    targetField: String = "",
    customFilters: Map<String, String> = emptyMap(),
)
```
For AWS Secrets Manager:
- `name`: Secret variable name.
- `path`: Stores the AWS Secret ID / Name (`secretName`).
- `targetField`: Stores the target JSON key.
- `customFilters`: Custom filter map.
- `description`: Description note.
- `enabled`: Enabled toggle.

---

## 3. Runtime Network & Resolution Models

### 3.1 AwsSecretsManagerConfig (Core Network)
In `core-network`:
```kotlin
data class AwsSecretsManagerConfig(
    val secretId: String,
    val accessKey: String,
    val secretKey: String,
    val region: String = "us-east-1",
    val endpointUrl: String? = null,
    val sessionToken: String? = null,
    val versionStage: String? = "AWSCURRENT",
)
```

### 3.2 SecretDeclaration
In `core-network/vault/SecretResolutionService.kt`:
```kotlin
data class SecretDeclaration(
    val name: String,
    val path: String = "",
    val enabled: Boolean = true,
    val providerType: String = "hashicorp-vault-server",
    val url: String = "",
    val targetField: String = "",
    val customFilters: Map<String, String> = emptyMap(),
    val secretId: String = path,
)
```

---

## 4. Entity Lifecycle & State Transitions

```
[User Configures Settings]
       │
       ▼
Global Settings Saved ──► PlatformStorage (credentials encrypted via KSafe)
       │
       ▼
[User Edits Environment]
       │
       ▼
Select Provider: aws-secrets-manager
Add Secret: Name, Secret ID, Target Field, Filters
Save Environment ──► OpenCollection Model (AwsSecretsManagerExternalSecret)
       │
       ▼
[Request Execution]
       │
       ├─► Extract {{_secret.<name>}} & secret: true variables
       ├─► Check Provider == aws-secrets-manager
       ├─► Group required secrets by Secret ID
       ├─► For each unique Secret ID:
       │     └─► KtorAwsSecretsManagerClient (AWS SigV4 POST to GetSecretValue)
       │     └─► Parse SecretString (JSON key-value map or raw string)
       ├─► Extract Target Field value for each variable
       ├─► Check Custom Filters (interpolating env variables)
       └─► Substitute resolved secrets into outgoing HTTP request
```
