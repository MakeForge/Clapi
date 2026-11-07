package io.github.crustack.core.model.opencollection

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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = AuthConfigSerializer::class)
sealed interface AuthConfig

@Serializable
data class AuthBasic(
    val type: String = "basic",
    val username: String = "",
    val password: String = ""
) : AuthConfig

@Serializable
data class AuthBearer(
    val type: String = "bearer",
    val token: String = ""
) : AuthConfig

@Serializable
data class AuthApiKey(
    val type: String = "apikey",
    val key: String = "",
    val value: String = "",
    val placement: String = "header" // "header" or "query"
) : AuthConfig

@Serializable
data class OAuth2ClientCredentials(
    val clientId: String? = null,
    val clientSecret: String? = null,
    val placement: String = "basic_auth_header" // "basic_auth_header" or "body"
)

@Serializable
data class OAuth2ResourceOwner(
    val username: String? = null,
    val password: String? = null
)

@Serializable
data class OAuth2TokenConfig(
    val id: String? = null,
    val placement: JsonElement? = null,
    val source: String = "access_token"
)

@Serializable
data class OAuth2Settings(
    val autoFetchToken: Boolean? = null,
    val autoRefreshToken: Boolean? = null
)

@Serializable
data class AuthOAuth2(
    val type: String = "oauth2",
    val flow: String = "client_credentials",
    val accessTokenUrl: String? = null,
    val refreshTokenUrl: String? = null,
    val authorizationUrl: String? = null,
    val redirectUri: String? = null,
    val credentials: OAuth2ClientCredentials? = null,
    val resourceOwner: OAuth2ResourceOwner? = null,
    val scope: String? = null,
    val tokenConfig: OAuth2TokenConfig? = null,
    val settings: OAuth2Settings? = null
) : AuthConfig

@Serializable
data class AuthAwsV4(
    val type: String = "awsv4",
    val accessKeyId: String? = null,
    val secretAccessKey: String? = null,
    val sessionToken: String? = null,
    val service: String? = null,
    val region: String? = null,
    val profileName: String? = null
) : AuthConfig

@Serializable
data class AuthDigest(
    val type: String = "digest",
    val username: String = "",
    val password: String = ""
) : AuthConfig

@Serializable
data class AuthNTLM(
    val type: String = "ntlm",
    val username: String = "",
    val password: String = "",
    val domain: String? = null
) : AuthConfig

@Serializable
data class AuthWsse(
    val type: String = "wsse",
    val username: String = "",
    val password: String = ""
) : AuthConfig

@Serializable
data class AuthOAuth1(
    val type: String = "oauth1",
    val consumerKey: String? = null,
    val consumerSecret: String? = null,
    val accessToken: String? = null,
    val accessTokenSecret: String? = null,
    val signatureMethod: String? = null,
    val placement: String = "header"
) : AuthConfig

@Serializable
data object AuthInherit : AuthConfig

object AuthConfigSerializer : KSerializer<AuthConfig> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("AuthConfig", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): AuthConfig {
        if (decoder !is JsonDecoder) {
            val str = decoder.decodeString()
            return if (str == "inherit") AuthInherit else AuthBearer(token = str)
        }
        val element = decoder.decodeJsonElement()
        if (element is JsonPrimitive) {
            if (element.content == "inherit") {
                return AuthInherit
            }
            return AuthBearer(token = element.content)
        }
        val obj = element.jsonObject
        val type = obj["type"]?.jsonPrimitive?.content
        val json = decoder.json
        return when (type) {
            "basic" -> json.decodeFromJsonElement(AuthBasic.serializer(), obj)
            "bearer" -> json.decodeFromJsonElement(AuthBearer.serializer(), obj)
            "apikey" -> json.decodeFromJsonElement(AuthApiKey.serializer(), obj)
            "oauth2" -> json.decodeFromJsonElement(AuthOAuth2.serializer(), obj)
            "awsv4" -> json.decodeFromJsonElement(AuthAwsV4.serializer(), obj)
            "digest" -> json.decodeFromJsonElement(AuthDigest.serializer(), obj)
            "ntlm" -> json.decodeFromJsonElement(AuthNTLM.serializer(), obj)
            "wsse" -> json.decodeFromJsonElement(AuthWsse.serializer(), obj)
            "oauth1" -> json.decodeFromJsonElement(AuthOAuth1.serializer(), obj)
            else -> AuthBearer(token = "")
        }
    }

    override fun serialize(encoder: Encoder, value: AuthConfig) {
        if (value is AuthInherit) {
            if (encoder is JsonEncoder) {
                encoder.encodeJsonElement(JsonPrimitive("inherit"))
            } else {
                encoder.encodeString("inherit")
            }
            return
        }
        val json = if (encoder is JsonEncoder) encoder.json else null
        when (value) {
            is AuthBasic -> encoder.encodeSerializableValue(AuthBasic.serializer(), value)
            is AuthBearer -> encoder.encodeSerializableValue(AuthBearer.serializer(), value)
            is AuthApiKey -> encoder.encodeSerializableValue(AuthApiKey.serializer(), value)
            is AuthOAuth2 -> encoder.encodeSerializableValue(AuthOAuth2.serializer(), value)
            is AuthAwsV4 -> encoder.encodeSerializableValue(AuthAwsV4.serializer(), value)
            is AuthDigest -> encoder.encodeSerializableValue(AuthDigest.serializer(), value)
            is AuthNTLM -> encoder.encodeSerializableValue(AuthNTLM.serializer(), value)
            is AuthWsse -> encoder.encodeSerializableValue(AuthWsse.serializer(), value)
            is AuthOAuth1 -> encoder.encodeSerializableValue(AuthOAuth1.serializer(), value)
            AuthInherit -> Unit
        }
    }
}
