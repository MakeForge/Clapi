package io.github.crustack.core.model.opencollection

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
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
data class HttpRequestParam(
    val name: String,
    val value: String,
    val description: Description? = null,
    val type: String = "query", // "query" or "path"
    val disabled: Boolean? = null,
)

@Serializable(with = HttpRequestBodySerializer::class)
sealed interface HttpRequestBody {
    val type: String
}

@Serializable
data class RawBody(
    override val type: String = "json", // "json", "text", "xml", "sparql"
    val data: String = ""
) : HttpRequestBody

@Serializable
data class FormParamEntry(
    val name: String,
    val value: String,
    val description: Description? = null,
    val disabled: Boolean? = null,
)

@Serializable
data class FormUrlEncodedBody(
    override val type: String = "form-urlencoded",
    val data: List<FormParamEntry> = emptyList()
) : HttpRequestBody

@Serializable
data class MultipartPartEntry(
    val name: String,
    val type: String = "text", // "text" or "file"
    val value: JsonElement, // string or array of strings
    val description: Description? = null,
    val contentType: String? = null,
    val disabled: Boolean? = null,
)

@Serializable
data class MultipartFormBody(
    override val type: String = "multipart-form",
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
    override val type: String = "file",
    val data: List<FileBodyVariant> = emptyList()
) : HttpRequestBody

object HttpRequestBodySerializer : JsonContentPolymorphicSerializer<HttpRequestBody>(HttpRequestBody::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<HttpRequestBody> {
        val type = element.jsonObject["type"]?.jsonPrimitive?.content
        return when (type) {
            "form-urlencoded" -> FormUrlEncodedBody.serializer()
            "multipart-form" -> MultipartFormBody.serializer()
            "file" -> FileBody.serializer()
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
data class ExampleResponseBody(
    val type: String = "json",
    val data: String = ""
)

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
