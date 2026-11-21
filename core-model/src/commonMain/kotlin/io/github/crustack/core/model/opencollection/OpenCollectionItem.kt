package io.github.crustack.core.model.opencollection

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
sealed interface ItemInfo {
    val name: String
    val description: Description?
    val type: String
    val seq: Double?
    val tags: List<String>
}

@Serializable
data class FolderInfo(
    override val name: String,
    override val description: Description? = null,
    override val type: String = "folder",
    override val seq: Double? = null,
    override val tags: List<String> = emptyList(),
) : ItemInfo

@Serializable
data class HttpRequestInfo(
    override val name: String,
    override val description: Description? = null,
    override val type: String = "http",
    override val seq: Double? = null,
    override val tags: List<String> = emptyList(),
) : ItemInfo

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

object OpenCollectionItemSerializer : JsonContentPolymorphicSerializer<OpenCollectionItem>(OpenCollectionItem::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<OpenCollectionItem> {
        val obj = element.jsonObject
        if (obj.containsKey("http")) {
            return HttpRequestItem.serializer()
        }
        val infoObj = obj["info"]?.jsonObject
        val type = infoObj?.get("type")?.jsonPrimitive?.content
        if (type == "http") {
            return HttpRequestItem.serializer()
        }
        return FolderItem.serializer()
    }
}
