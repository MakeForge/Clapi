package io.github.crustack.clapi.core.model.opencollection

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = DescriptionSerializer::class)
data class Description(
    val content: String,
    val type: String = "text/plain"
)

object DescriptionSerializer : KSerializer<Description> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Description", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Description {
        return if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            when (element) {
                is JsonPrimitive -> Description(content = element.content)
                is JsonObject -> {
                    val content = element["content"]?.jsonPrimitive?.content ?: ""
                    val type = element["type"]?.jsonPrimitive?.content ?: "text/plain"
                    Description(content = content, type = type)
                }
                else -> Description(content = element.toString())
            }
        } else {
            Description(content = decoder.decodeString())
        }
    }

    override fun serialize(encoder: Encoder, value: Description) {
        if (encoder is JsonEncoder) {
            if (value.type == "text/plain") {
                encoder.encodeJsonElement(JsonPrimitive(value.content))
            } else {
                val obj = JsonObject(
                    mapOf(
                        "content" to JsonPrimitive(value.content),
                        "type" to JsonPrimitive(value.type)
                    )
                )
                encoder.encodeJsonElement(obj)
            }
        } else {
            encoder.encodeString(value.content)
        }
    }
}

@Serializable(with = DocumentationSerializer::class)
data class Documentation(
    val content: String,
    val type: String = "text/markdown"
)

object DocumentationSerializer : KSerializer<Documentation> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Documentation", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Documentation {
        return if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            when (element) {
                is JsonPrimitive -> Documentation(content = element.content)
                is JsonObject -> {
                    val content = element["content"]?.jsonPrimitive?.content ?: ""
                    val type = element["type"]?.jsonPrimitive?.content ?: "text/markdown"
                    Documentation(content = content, type = type)
                }
                else -> Documentation(content = element.toString())
            }
        } else {
            Documentation(content = decoder.decodeString())
        }
    }

    override fun serialize(encoder: Encoder, value: Documentation) {
        if (encoder is JsonEncoder) {
            if (value.type == "text/markdown") {
                encoder.encodeJsonElement(JsonPrimitive(value.content))
            } else {
                val obj = JsonObject(
                    mapOf(
                        "content" to JsonPrimitive(value.content),
                        "type" to JsonPrimitive(value.type)
                    )
                )
                encoder.encodeJsonElement(obj)
            }
        } else {
            encoder.encodeString(value.content)
        }
    }
}
