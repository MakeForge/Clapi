package io.github.makeforge.clapi.core.model.opencollection

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class HttpRequestHeader(
    val name: String,
    val value: String,
    val description: Description? = null,
    val disabled: Boolean? = null,
)

@Serializable
data class HttpResponseHeader(
    val name: String,
    val value: String,
)

@Serializable
enum class HttpRequestParamType(val value: String) {
    @SerialName("query")
    QUERY("query"),
    @SerialName("path")
    PATH("path");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): HttpRequestParamType? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

@Serializable
data class HttpRequestParam(
    val name: String,
    val value: String,
    val description: Description? = null,
    val type: HttpRequestParamType = HttpRequestParamType.QUERY,
    val disabled: Boolean? = null,
) {
    constructor(
        name: String,
        value: String,
        description: Description? = null,
        type: String,
        disabled: Boolean? = null
    ) : this(
        name = name,
        value = value,
        description = description,
        type = HttpRequestParamType.fromValue(type) ?: HttpRequestParamType.QUERY,
        disabled = disabled
    )
}

@Serializable
enum class RequestBodyType(val value: String) {
    @SerialName("json")
    JSON("json"),
    @SerialName("text")
    TEXT("text"),
    @SerialName("xml")
    XML("xml"),
    @SerialName("sparql")
    SPARQL("sparql"),
    @SerialName("form-urlencoded")
    FORM_URLENCODED("form-urlencoded"),
    @SerialName("multipart-form")
    MULTIPART_FORM("multipart-form"),
    @SerialName("file")
    FILE("file");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): RequestBodyType? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

@Serializable(with = HttpRequestBodySerializer::class)
sealed interface HttpRequestBody {
    val type: RequestBodyType
}

@Serializable
data class RawBody(
    override val type: RequestBodyType = RequestBodyType.JSON,
    val data: String = ""
) : HttpRequestBody {
    constructor(type: String, data: String = "") : this(
        type = RequestBodyType.fromValue(type) ?: RequestBodyType.JSON,
        data = data
    )
}

@Serializable
data class FormParamEntry(
    val name: String,
    val value: String,
    val description: Description? = null,
    val disabled: Boolean? = null,
)

@Serializable
data class FormUrlEncodedBody(
    override val type: RequestBodyType = RequestBodyType.FORM_URLENCODED,
    val data: List<FormParamEntry> = emptyList()
) : HttpRequestBody

@Serializable
enum class MultipartPartType(val value: String) {
    @SerialName("text")
    TEXT("text"),
    @SerialName("file")
    FILE("file");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): MultipartPartType? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

@Serializable
data class MultipartPartEntry(
    val name: String,
    val type: MultipartPartType = MultipartPartType.TEXT,
    val value: JsonElement, // string or array of strings
    val description: Description? = null,
    val contentType: String? = null,
    val disabled: Boolean? = null,
) {
    constructor(
        name: String,
        type: String,
        value: JsonElement,
        description: Description? = null,
        contentType: String? = null,
        disabled: Boolean? = null
    ) : this(
        name = name,
        type = MultipartPartType.fromValue(type) ?: MultipartPartType.TEXT,
        value = value,
        description = description,
        contentType = contentType,
        disabled = disabled
    )
}

@Serializable
data class MultipartFormBody(
    override val type: RequestBodyType = RequestBodyType.MULTIPART_FORM,
    val data: List<MultipartPartEntry> = emptyList()
) : HttpRequestBody

@Serializable
data class FileBodyVariant(
    val filePath: String,
    val contentType: String,
    val selected: Boolean = true
)

@Serializable
data class FileBody(
    override val type: RequestBodyType = RequestBodyType.FILE,
    val data: List<FileBodyVariant> = emptyList()
) : HttpRequestBody

object HttpRequestBodySerializer : JsonContentPolymorphicSerializer<HttpRequestBody>(HttpRequestBody::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<HttpRequestBody> {
        val typeStr = element.jsonObject["type"]?.jsonPrimitive?.content
        val type = RequestBodyType.fromValue(typeStr)
        return when (type) {
            RequestBodyType.FORM_URLENCODED -> FormUrlEncodedBody.serializer()
            RequestBodyType.MULTIPART_FORM -> MultipartFormBody.serializer()
            RequestBodyType.FILE -> FileBody.serializer()
            else -> RawBody.serializer()
        }
    }
}

@Serializable
data class HttpRequestSettings(
    val encodeUrl: JsonElement? = null, // boolean or "inherit"
    val timeout: JsonElement? = null, // number or "inherit"
    val followRedirects: JsonElement? = null, // boolean or "inherit"
    val maxRedirects: JsonElement? = null, // number or "inherit"
)

@Serializable
data class ExampleHttpRequest(
    val url: String? = null,
    val method: String? = null,
    val headers: List<HttpRequestHeader> = emptyList(),
    val params: List<HttpRequestParam> = emptyList(),
    val body: HttpRequestBody? = null,
)

@Serializable
enum class ResponseBodyType(val value: String) {
    @SerialName("json")
    JSON("json"),
    @SerialName("text")
    TEXT("text"),
    @SerialName("xml")
    XML("xml"),
    @SerialName("html")
    HTML("html"),
    @SerialName("binary")
    BINARY("binary");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): ResponseBodyType? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

@Serializable
data class ExampleResponseBody(
    val type: ResponseBodyType = ResponseBodyType.JSON,
    val data: String = ""
) {
    constructor(type: String, data: String = "") : this(
        type = ResponseBodyType.fromValue(type) ?: ResponseBodyType.JSON,
        data = data
    )
}

@Serializable
data class ExampleHttpResponse(
    val status: Int? = null,
    val statusText: String? = null,
    val headers: List<HttpResponseHeader> = emptyList(),
    val body: ExampleResponseBody? = null,
)

@Serializable
data class HttpRequestExample(
    val name: String? = null,
    val description: Description? = null,
    val request: ExampleHttpRequest? = null,
    val response: ExampleHttpResponse? = null,
)

@Serializable
data class HttpRequestDetails(
    val method: String,
    val url: String,
    val headers: List<HttpRequestHeader> = emptyList(),
    val params: List<HttpRequestParam> = emptyList(),
    val body: HttpRequestBody? = null,
    val auth: AuthConfig? = null,
)
