package io.github.crustack.clapi.core.model.opencollection

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
enum class ItemType(val value: String) {
    @SerialName("folder")
    FOLDER("folder"),
    @SerialName("http")
    HTTP("http"),
    @SerialName("grpc")
    GRPC("grpc"),
    @SerialName("graphql")
    GRAPHQL("graphql"),
    @SerialName("websocket")
    WEBSOCKET("websocket");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): ItemType? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

@Serializable
sealed interface ItemInfo {
    val name: String
    val description: Description?
    val type: ItemType
    val seq: Double?
    val tags: List<String>
}

@Serializable
data class FolderInfo(
    override val name: String,
    override val description: Description? = null,
    override val type: ItemType = ItemType.FOLDER,
    override val seq: Double? = null,
    override val tags: List<String> = emptyList(),
) : ItemInfo {
    constructor(
        name: String,
        description: Description? = null,
        type: String,
        seq: Double? = null,
        tags: List<String> = emptyList()
    ) : this(
        name = name,
        description = description,
        type = ItemType.fromValue(type) ?: ItemType.FOLDER,
        seq = seq,
        tags = tags
    )
}

@Serializable
data class HttpRequestInfo(
    override val name: String,
    override val description: Description? = null,
    override val type: ItemType = ItemType.HTTP,
    override val seq: Double? = null,
    override val tags: List<String> = emptyList(),
) : ItemInfo {
    constructor(
        name: String,
        description: Description? = null,
        type: String,
        seq: Double? = null,
        tags: List<String> = emptyList()
    ) : this(
        name = name,
        description = description,
        type = ItemType.fromValue(type) ?: ItemType.HTTP,
        seq = seq,
        tags = tags
    )
}

@Serializable
data class GrpcRequestInfo(
    override val name: String,
    override val description: Description? = null,
    override val type: ItemType = ItemType.GRPC,
    override val seq: Double? = null,
    override val tags: List<String> = emptyList(),
) : ItemInfo {
    constructor(
        name: String,
        description: Description? = null,
        type: String,
        seq: Double? = null,
        tags: List<String> = emptyList()
    ) : this(
        name = name,
        description = description,
        type = ItemType.fromValue(type) ?: ItemType.GRPC,
        seq = seq,
        tags = tags
    )
}

@Serializable
data class GrpcMetadata(
    val name: String,
    val value: String,
    val description: Description? = null,
    val disabled: Boolean? = null,
)

@Serializable
enum class GrpcMethodType(val value: String) {
    @SerialName("unary")
    UNARY("unary"),
    @SerialName("client-streaming")
    CLIENT_STREAMING("client-streaming"),
    @SerialName("server-streaming")
    SERVER_STREAMING("server-streaming"),
    @SerialName("bidi-streaming")
    BIDI_STREAMING("bidi-streaming");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): GrpcMethodType? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

@Serializable
data class GrpcRequestDetails(
    val url: String,
    val method: String? = null,
    val methodType: GrpcMethodType? = GrpcMethodType.UNARY,
    val protoFilePath: String? = null,
    val metadata: List<GrpcMetadata> = emptyList(),
    val message: String? = null,
    val auth: AuthConfig? = null,
) {
    constructor(
        url: String,
        method: String? = null,
        methodType: String?,
        protoFilePath: String? = null,
        metadata: List<GrpcMetadata> = emptyList(),
        message: String? = null,
        auth: AuthConfig? = null,
    ) : this(
        url = url,
        method = method,
        methodType = methodType?.let { GrpcMethodType.fromValue(it) ?: GrpcMethodType.UNARY },
        protoFilePath = protoFilePath,
        metadata = metadata,
        message = message,
        auth = auth,
    )
}

@Serializable
data class GrpcRequestRuntime(
    val variables: List<VariableEntry> = emptyList(),
    val scripts: List<Script> = emptyList(),
    val assertions: List<Assertion> = emptyList(),
)

@Serializable(with = OpenCollectionItemSerializer::class)
sealed interface OpenCollectionItem {
    val info: ItemInfo
    val extensions: Map<String, JsonElement>
}

@Serializable
data class FolderItem(
    override val info: FolderInfo,
    val items: List<OpenCollectionItem> = emptyList(),
    val request: RequestDefaults? = null,
    val docs: Documentation? = null,
    @Transient override val extensions: Map<String, JsonElement> = emptyMap(),
) : OpenCollectionItem

@Serializable
data class HttpRequestItem(
    override val info: HttpRequestInfo,
    val http: HttpRequestDetails,
    val runtime: HttpRequestRuntime? = null,
    val settings: HttpRequestSettings? = null,
    val docs: String? = null,
    val examples: List<HttpRequestExample> = emptyList(),
    @Transient override val extensions: Map<String, JsonElement> = emptyMap(),
) : OpenCollectionItem

@Serializable
data class GrpcRequestItem(
    override val info: GrpcRequestInfo,
    val grpc: GrpcRequestDetails,
    val runtime: GrpcRequestRuntime? = null,
    val docs: String? = null,
    @Transient override val extensions: Map<String, JsonElement> = emptyMap(),
) : OpenCollectionItem

@Serializable
enum class WebSocketMessageType(val value: String) {
    @SerialName("text")
    TEXT("text"),
    @SerialName("json")
    JSON("json"),
    @SerialName("xml")
    XML("xml"),
    @SerialName("binary")
    BINARY("binary");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): WebSocketMessageType =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: TEXT
    }
}

@Serializable
data class WebSocketMessage(
    val type: WebSocketMessageType = WebSocketMessageType.TEXT,
    val data: String = "",
) {
    constructor(type: String, data: String = "") : this(
        type = WebSocketMessageType.fromValue(type),
        data = data
    )
}

@Serializable
data class WebSocketMessageVariant(
    val title: String,
    val selected: Boolean? = null,
    val message: WebSocketMessage,
)

@Serializable(with = WebSocketMessagePayloadSerializer::class)
sealed interface WebSocketMessagePayload {
    val singleMessage: WebSocketMessage? get() = (this as? Single)?.message
    val variantList: List<WebSocketMessageVariant> get() = when (this) {
        is Single -> listOf(WebSocketMessageVariant(title = "Default", selected = true, message = message))
        is Variants -> variants
    }

    @Serializable
    data class Single(val message: WebSocketMessage) : WebSocketMessagePayload

    @Serializable
    data class Variants(val variants: List<WebSocketMessageVariant>) : WebSocketMessagePayload
}

object WebSocketMessagePayloadSerializer : KSerializer<WebSocketMessagePayload> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("io.github.crustack.clapi.core.model.opencollection.WebSocketMessagePayload")

    override fun deserialize(decoder: Decoder): WebSocketMessagePayload {
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            return when (element) {
                is JsonArray -> {
                    val variants = decoder.json.decodeFromJsonElement(
                        ListSerializer(WebSocketMessageVariant.serializer()),
                        element
                    )
                    WebSocketMessagePayload.Variants(variants)
                }
                is JsonObject -> {
                    val message = decoder.json.decodeFromJsonElement(
                        WebSocketMessage.serializer(),
                        element
                    )
                    WebSocketMessagePayload.Single(message)
                }
                else -> throw IllegalArgumentException("Expected JsonObject or JsonArray for WebSocketMessagePayload, got $element")
            }
        }
        throw UnsupportedOperationException("WebSocketMessagePayloadSerializer only supports JSON decoding")
    }

    override fun serialize(encoder: Encoder, value: WebSocketMessagePayload) {
        if (encoder is JsonEncoder) {
            when (value) {
                is WebSocketMessagePayload.Single -> {
                    encoder.encodeSerializableValue(WebSocketMessage.serializer(), value.message)
                }
                is WebSocketMessagePayload.Variants -> {
                    encoder.encodeSerializableValue(
                        ListSerializer(WebSocketMessageVariant.serializer()),
                        value.variants
                    )
                }
            }
        } else {
            when (value) {
                is WebSocketMessagePayload.Single -> encoder.encodeSerializableValue(WebSocketMessage.serializer(), value.message)
                is WebSocketMessagePayload.Variants -> encoder.encodeSerializableValue(
                    ListSerializer(WebSocketMessageVariant.serializer()),
                    value.variants
                )
            }
        }
    }
}

@Serializable
data class WebSocketRequestInfo(
    override val name: String,
    override val description: Description? = null,
    override val type: ItemType = ItemType.WEBSOCKET,
    override val seq: Double? = null,
    override val tags: List<String> = emptyList(),
) : ItemInfo {
    constructor(
        name: String,
        description: Description? = null,
        type: String,
        seq: Double? = null,
        tags: List<String> = emptyList()
    ) : this(
        name = name,
        description = description,
        type = ItemType.fromValue(type) ?: ItemType.WEBSOCKET,
        seq = seq,
        tags = tags
    )
}

@Serializable
data class WebSocketRequestDetails(
    val url: String,
    val headers: List<HttpRequestHeader> = emptyList(),
    val message: WebSocketMessagePayload? = null,
    val auth: AuthConfig? = null,
)

@Serializable
data class WebSocketRequestSettings(
    val timeout: JsonElement? = null,
    val keepAliveInterval: JsonElement? = null,
)

@Serializable
data class WebSocketRequestRuntime(
    val variables: List<VariableEntry> = emptyList(),
    val scripts: List<Script> = emptyList(),
)

@Serializable
data class WebSocketRequestItem(
    override val info: WebSocketRequestInfo,
    val websocket: WebSocketRequestDetails,
    val runtime: WebSocketRequestRuntime? = null,
    val settings: WebSocketRequestSettings? = null,
    val docs: String? = null,
    @Transient override val extensions: Map<String, JsonElement> = emptyMap(),
) : OpenCollectionItem

object OpenCollectionItemSerializer : JsonContentPolymorphicSerializer<OpenCollectionItem>(OpenCollectionItem::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<OpenCollectionItem> {
        val obj = element.jsonObject
        if (obj.containsKey("http")) {
            return HttpRequestItem.serializer()
        }
        if (obj.containsKey("grpc")) {
            return GrpcRequestItem.serializer()
        }
        if (obj.containsKey("websocket")) {
            return WebSocketRequestItem.serializer()
        }
        val infoObj = obj["info"]?.jsonObject
        val typeStr = infoObj?.get("type")?.jsonPrimitive?.content
        val type = ItemType.fromValue(typeStr)
        if (type == ItemType.HTTP) {
            return HttpRequestItem.serializer()
        }
        if (type == ItemType.GRPC) {
            return GrpcRequestItem.serializer()
        }
        if (type == ItemType.WEBSOCKET) {
            return WebSocketRequestItem.serializer()
        }
        return FolderItem.serializer()
    }
}
