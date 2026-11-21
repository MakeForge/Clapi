package io.github.crustack.clapi.core.model.opencollection

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = VariableValueSerializer::class)
sealed interface VariableValue {
    val asString: String

    companion object {
        fun of(value: String): VariableValue = StringVariableValue(value)
        fun of(type: String, data: String): VariableValue = TypedVariableValue(type, data)
    }
}

@Serializable
data class StringVariableValue(val value: String) : VariableValue {
    override val asString: String get() = value
}

@Serializable
data class TypedVariableValue(val type: String, val data: String) : VariableValue {
    override val asString: String get() = data
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
                    val type = element["type"]?.jsonPrimitive?.content ?: "string"
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
                            "type" to JsonPrimitive(value.type),
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
    val type: String = "string",
    override val description: Description? = null,
    override val disabled: Boolean? = null,
    val secret: Boolean = true,
) : VariableEntry

object VariableEntrySerializer : JsonContentPolymorphicSerializer<VariableEntry>(VariableEntry::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<VariableEntry> {
        val obj = element.jsonObject
        if (obj["secret"]?.jsonPrimitive?.booleanOrNull == true) {
            return SecretVariable.serializer()
        }
        return RegularVariable.serializer()
    }
}

@Serializable(with = ClientCertificateSerializer::class)
sealed interface ClientCertificate {
    val domain: String
    val type: String
    val passphrase: String?
}

@Serializable
data class PemCertificate(
    override val domain: String,
    override val type: String = "pem",
    val certificateFilePath: String,
    val privateKeyFilePath: String,
    override val passphrase: String? = null,
) : ClientCertificate

@Serializable
data class Pkcs12Certificate(
    override val domain: String,
    override val type: String = "pkcs12",
    val pkcs12FilePath: String,
    override val passphrase: String? = null,
) : ClientCertificate

object ClientCertificateSerializer : JsonContentPolymorphicSerializer<ClientCertificate>(ClientCertificate::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ClientCertificate> {
        val type = element.jsonObject["type"]?.jsonPrimitive?.content
        return if (type == "pkcs12") {
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
)
