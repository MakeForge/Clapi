package io.github.crustack.clapi.core.model.opencollection

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
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

object OpenCollectionItemSerializer : JsonContentPolymorphicSerializer<OpenCollectionItem>(OpenCollectionItem::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<OpenCollectionItem> {
        val obj = element.jsonObject
        if (obj.containsKey("http")) {
            return HttpRequestItem.serializer()
        }
        if (obj.containsKey("grpc")) {
            return GrpcRequestItem.serializer()
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
        return FolderItem.serializer()
    }
}
