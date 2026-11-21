package io.github.crustack.clapi.core.model.opencollection

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
enum class VariableType(val value: String) {
    @SerialName("string")
    STRING("string"),
    @SerialName("number")
    NUMBER("number"),
    @SerialName("boolean")
    BOOLEAN("boolean"),
    @SerialName("null")
    NULL("null"),
    @SerialName("object")
    OBJECT("object");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): VariableType? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

@Serializable(with = VariableValueSerializer::class)
sealed interface VariableValue {
    val asString: String

    companion object {
        fun of(value: String): VariableValue = StringVariableValue(value)
        fun of(type: VariableType, data: String): VariableValue = TypedVariableValue(type, data)
        fun of(type: String, data: String): VariableValue = TypedVariableValue(type, data)
    }
}

@Serializable
data class StringVariableValue(val value: String) : VariableValue {
    override val asString: String get() = value
}

@Serializable
data class TypedVariableValue(
    val type: VariableType = VariableType.STRING,
    val data: String
) : VariableValue {
    override val asString: String get() = data

    constructor(type: String, data: String) : this(
        type = VariableType.fromValue(type) ?: VariableType.STRING,
        data = data
    )
}

object VariableValueSerializer : KSerializer<VariableValue> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("VariableValue", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): VariableValue {
        return if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            when (element) {
                is JsonPrimitive -> StringVariableValue(element.content)
                is JsonObject -> {
                    val typeStr = element["type"]?.jsonPrimitive?.content ?: "string"
                    val type = VariableType.fromValue(typeStr) ?: VariableType.STRING
                    val data = element["data"]?.jsonPrimitive?.content ?: ""
                    TypedVariableValue(type = type, data = data)
                }
                else -> StringVariableValue(element.toString())
            }
        } else {
            StringVariableValue(decoder.decodeString())
        }
    }

    override fun serialize(encoder: Encoder, value: VariableValue) {
        if (encoder is JsonEncoder) {
            when (value) {
                is StringVariableValue -> encoder.encodeJsonElement(JsonPrimitive(value.value))
                is TypedVariableValue -> {
                    val obj = JsonObject(
                        mapOf(
                            "type" to JsonPrimitive(value.type.value),
                            "data" to JsonPrimitive(value.data)
                        )
                    )
                    encoder.encodeJsonElement(obj)
                }
            }
        } else {
            encoder.encodeString(value.asString)
        }
    }
}

@Serializable(with = VariableEntrySerializer::class)
sealed interface VariableEntry {
    val name: String
    val description: Description?
    val disabled: Boolean?
}

@Serializable
data class RegularVariable(
    override val name: String,
    val value: VariableValue? = null,
    override val description: Description? = null,
    override val disabled: Boolean? = null,
) : VariableEntry

@Serializable
data class SecretVariable(
    override val name: String,
    val type: VariableType = VariableType.STRING,
    override val description: Description? = null,
    override val disabled: Boolean? = null,
    val secret: Boolean = true,
) : VariableEntry {
    constructor(
        name: String,
        type: String,
        description: Description? = null,
        disabled: Boolean? = null,
        secret: Boolean = true
    ) : this(
        name = name,
        type = VariableType.fromValue(type) ?: VariableType.STRING,
        description = description,
        disabled = disabled,
        secret = secret
    )
}

object VariableEntrySerializer : JsonContentPolymorphicSerializer<VariableEntry>(VariableEntry::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<VariableEntry> {
        val obj = element.jsonObject
        if (obj["secret"]?.jsonPrimitive?.booleanOrNull == true) {
            return SecretVariable.serializer()
        }
        return RegularVariable.serializer()
    }
}

@Serializable
enum class CertificateType(val value: String) {
    @SerialName("pem")
    PEM("pem"),
    @SerialName("pkcs12")
    PKCS12("pkcs12");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): CertificateType? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

@Serializable(with = ClientCertificateSerializer::class)
sealed interface ClientCertificate {
    val domain: String
    val type: CertificateType
    val passphrase: String?
}

@Serializable
data class PemCertificate(
    override val domain: String,
    override val type: CertificateType = CertificateType.PEM,
    val certificateFilePath: String,
    val privateKeyFilePath: String,
    override val passphrase: String? = null,
) : ClientCertificate {
    constructor(
        domain: String,
        type: String,
        certificateFilePath: String,
        privateKeyFilePath: String,
        passphrase: String? = null
    ) : this(
        domain = domain,
        type = CertificateType.fromValue(type) ?: CertificateType.PEM,
        certificateFilePath = certificateFilePath,
        privateKeyFilePath = privateKeyFilePath,
        passphrase = passphrase
    )
}

@Serializable
data class Pkcs12Certificate(
    override val domain: String,
    override val type: CertificateType = CertificateType.PKCS12,
    val pkcs12FilePath: String,
    override val passphrase: String? = null,
) : ClientCertificate {
    constructor(
        domain: String,
        type: String,
        pkcs12FilePath: String,
        passphrase: String? = null
    ) : this(
        domain = domain,
        type = CertificateType.fromValue(type) ?: CertificateType.PKCS12,
        pkcs12FilePath = pkcs12FilePath,
        passphrase = passphrase
    )
}

object ClientCertificateSerializer : JsonContentPolymorphicSerializer<ClientCertificate>(ClientCertificate::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ClientCertificate> {
        val type = element.jsonObject["type"]?.jsonPrimitive?.content
        return if (type == CertificateType.PKCS12.value || type == "pkcs12") {
            Pkcs12Certificate.serializer()
        } else {
            PemCertificate.serializer()
        }
    }
}

@Serializable
data class Environment(
    val name: String,
    val color: String? = null,
    val description: Description? = null,
    val variables: List<VariableEntry> = emptyList(),
    val clientCertificates: List<ClientCertificate> = emptyList(),
    val extends: String? = null,
    val dotEnvFilePath: String? = null,
    val externalSecrets: ExternalSecrets? = null,
)

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

@Serializable
data class ExternalSecrets(
    val type: ExternalSecretType = ExternalSecretType.HASHICORP_VAULT_SERVER,
    val variables: List<ExternalSecretVariable> = emptyList(),
) {
    constructor(type: String, variables: List<ExternalSecretVariable> = emptyList()) : this(
        type = ExternalSecretType.fromValue(type) ?: ExternalSecretType.HASHICORP_VAULT_SERVER,
        variables = variables
    )
}

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

@Serializable
data class KeePassXcExternalSecret(
    override val name: String,
    val url: String,
    val targetField: String = "password",
    val customFilters: Map<String, String> = emptyMap(),
    override val description: Description? = null,
    override val disabled: Boolean? = null,
) : ExternalSecretVariable

@Serializable
data class AwsSecretsManagerExternalSecret(
    override val name: String,
    val secretName: String,
    val targetField: String = "",
    val customFilters: Map<String, String> = emptyMap(),
    override val description: Description? = null,
    override val disabled: Boolean? = null,
) : ExternalSecretVariable

@Serializable
data class AzureKeyVaultExternalSecret(
    override val name: String,
    val secretName: String,
    val vaultName: String = "",
    val targetField: String = "",
    val customFilters: Map<String, String> = emptyMap(),
    override val description: Description? = null,
    override val disabled: Boolean? = null,
) : ExternalSecretVariable

@Serializable
data class GcpSecretsManagerExternalSecret(
    override val name: String,
    val secretId: String,
    val version: String = "latest",
    val targetField: String = "",
    val projectId: String = "",
    val customFilters: Map<String, String> = emptyMap(),
    override val description: Description? = null,
    override val disabled: Boolean? = null,
) : ExternalSecretVariable

@Serializable
data class KeeperSecurityExternalSecret(
    override val name: String,
    val recordTitle: String = "",
    val targetField: String = "password",
    val customFilters: Map<String, String> = emptyMap(),
    override val description: Description? = null,
    override val disabled: Boolean? = null,
) : ExternalSecretVariable

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
