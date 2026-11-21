package io.github.makeforge.clapi.core.model.opencollection

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
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

@Serializable
enum class AuthType(val value: String) {
    @SerialName("basic")
    BASIC("basic"),
    @SerialName("bearer")
    BEARER("bearer"),
    @SerialName("apikey")
    APIKEY("apikey"),
    @SerialName("oauth2")
    OAUTH2("oauth2"),
    @SerialName("awsv4")
    AWSV4("awsv4"),
    @SerialName("digest")
    DIGEST("digest"),
    @SerialName("ntlm")
    NTLM("ntlm"),
    @SerialName("wsse")
    WSSE("wsse"),
    @SerialName("oauth1")
    OAUTH1("oauth1"),
    @SerialName("inherit")
    INHERIT("inherit");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): AuthType? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

typealias OpenCollectionAuthType = AuthType

@Serializable
enum class ApiKeyPlacement(val value: String) {
    @SerialName("header")
    HEADER("header"),
    @SerialName("query")
    QUERY("query");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): ApiKeyPlacement? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

@Serializable
enum class OAuth2CredentialsPlacement(val value: String) {
    @SerialName("basic_auth_header")
    BASIC_AUTH_HEADER("basic_auth_header"),
    @SerialName("body")
    BODY("body");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): OAuth2CredentialsPlacement? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

@Serializable
enum class OAuth2TokenSource(val value: String) {
    @SerialName("access_token")
    ACCESS_TOKEN("access_token"),
    @SerialName("id_token")
    ID_TOKEN("id_token");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): OAuth2TokenSource? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

@Serializable
enum class OAuth2Flow(val value: String) {
    @SerialName("client_credentials")
    CLIENT_CREDENTIALS("client_credentials"),
    @SerialName("resource_owner_password_credentials")
    RESOURCE_OWNER_PASSWORD_CREDENTIALS("resource_owner_password_credentials"),
    @SerialName("authorization_code")
    AUTHORIZATION_CODE("authorization_code"),
    @SerialName("implicit")
    IMPLICIT("implicit");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): OAuth2Flow? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

@Serializable
enum class OAuth1SignatureMethod(val value: String) {
    @SerialName("HMAC-SHA1")
    HMAC_SHA1("HMAC-SHA1"),
    @SerialName("HMAC-SHA256")
    HMAC_SHA256("HMAC-SHA256"),
    @SerialName("HMAC-SHA512")
    HMAC_SHA512("HMAC-SHA512"),
    @SerialName("RSA-SHA1")
    RSA_SHA1("RSA-SHA1"),
    @SerialName("RSA-SHA256")
    RSA_SHA256("RSA-SHA256"),
    @SerialName("RSA-SHA512")
    RSA_SHA512("RSA-SHA512"),
    @SerialName("PLAINTEXT")
    PLAINTEXT("PLAINTEXT");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): OAuth1SignatureMethod? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

@Serializable
enum class OAuth1Placement(val value: String) {
    @SerialName("header")
    HEADER("header"),
    @SerialName("query")
    QUERY("query"),
    @SerialName("body")
    BODY("body");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): OAuth1Placement? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

@Serializable(with = AuthConfigSerializer::class)
sealed interface AuthConfig {
    val type: AuthType
}

@Serializable
data class AuthBasic(
    override val type: AuthType = AuthType.BASIC,
    val username: String = "",
    val password: String = ""
) : AuthConfig {
    constructor(type: String, username: String = "", password: String = "") : this(
        type = AuthType.fromValue(type) ?: AuthType.BASIC,
        username = username,
        password = password
    )
}

@Serializable
data class AuthBearer(
    override val type: AuthType = AuthType.BEARER,
    val token: String = ""
) : AuthConfig {
    constructor(type: String, token: String = "") : this(
        type = AuthType.fromValue(type) ?: AuthType.BEARER,
        token = token
    )
}

@Serializable
data class AuthApiKey(
    override val type: AuthType = AuthType.APIKEY,
    val key: String = "",
    val value: String = "",
    val placement: ApiKeyPlacement = ApiKeyPlacement.HEADER
) : AuthConfig {
    constructor(
        key: String,
        value: String,
        placement: String
    ) : this(
        type = AuthType.APIKEY,
        key = key,
        value = value,
        placement = ApiKeyPlacement.fromValue(placement) ?: ApiKeyPlacement.HEADER
    )
}

@Serializable
data class OAuth2ClientCredentials(
    val clientId: String? = null,
    val clientSecret: String? = null,
    val placement: OAuth2CredentialsPlacement = OAuth2CredentialsPlacement.BASIC_AUTH_HEADER
) {
    constructor(clientId: String?, clientSecret: String?, placement: String) : this(
        clientId = clientId,
        clientSecret = clientSecret,
        placement = OAuth2CredentialsPlacement.fromValue(placement) ?: OAuth2CredentialsPlacement.BASIC_AUTH_HEADER
    )
}

@Serializable
data class OAuth2ResourceOwner(
    val username: String? = null,
    val password: String? = null
)

@Serializable
data class OAuth2TokenConfig(
    val id: String? = null,
    val placement: JsonElement? = null,
    val source: OAuth2TokenSource = OAuth2TokenSource.ACCESS_TOKEN
) {
    constructor(id: String?, placement: JsonElement?, source: String) : this(
        id = id,
        placement = placement,
        source = OAuth2TokenSource.fromValue(source) ?: OAuth2TokenSource.ACCESS_TOKEN
    )
}

@Serializable
data class OAuth2Settings(
    val autoFetchToken: Boolean? = null,
    val autoRefreshToken: Boolean? = null
)

@Serializable
data class AuthOAuth2(
    override val type: AuthType = AuthType.OAUTH2,
    val flow: OAuth2Flow = OAuth2Flow.CLIENT_CREDENTIALS,
    val accessTokenUrl: String? = null,
    val refreshTokenUrl: String? = null,
    val authorizationUrl: String? = null,
    val redirectUri: String? = null,
    val credentials: OAuth2ClientCredentials? = null,
    val resourceOwner: OAuth2ResourceOwner? = null,
    val scope: String? = null,
    val tokenConfig: OAuth2TokenConfig? = null,
    val settings: OAuth2Settings? = null
) : AuthConfig {
    constructor(
        flow: String,
        accessTokenUrl: String? = null,
        refreshTokenUrl: String? = null,
        authorizationUrl: String? = null,
        redirectUri: String? = null,
        credentials: OAuth2ClientCredentials? = null,
        resourceOwner: OAuth2ResourceOwner? = null,
        scope: String? = null,
        tokenConfig: OAuth2TokenConfig? = null,
        settings: OAuth2Settings? = null
    ) : this(
        type = AuthType.OAUTH2,
        flow = OAuth2Flow.fromValue(flow) ?: OAuth2Flow.CLIENT_CREDENTIALS,
        accessTokenUrl = accessTokenUrl,
        refreshTokenUrl = refreshTokenUrl,
        authorizationUrl = authorizationUrl,
        redirectUri = redirectUri,
        credentials = credentials,
        resourceOwner = resourceOwner,
        scope = scope,
        tokenConfig = tokenConfig,
        settings = settings
    )
}

@Serializable
data class AuthAwsV4(
    override val type: AuthType = AuthType.AWSV4,
    val accessKeyId: String? = null,
    val secretAccessKey: String? = null,
    val sessionToken: String? = null,
    val service: String? = null,
    val region: String? = null,
    val profileName: String? = null
) : AuthConfig

@Serializable
data class AuthDigest(
    override val type: AuthType = AuthType.DIGEST,
    val username: String = "",
    val password: String = ""
) : AuthConfig {
    constructor(type: String, username: String = "", password: String = "") : this(
        type = AuthType.fromValue(type) ?: AuthType.DIGEST,
        username = username,
        password = password
    )
}

@Serializable
data class AuthNTLM(
    override val type: AuthType = AuthType.NTLM,
    val username: String = "",
    val password: String = "",
    val domain: String? = null
) : AuthConfig {
    constructor(type: String, username: String = "", password: String = "", domain: String? = null) : this(
        type = AuthType.fromValue(type) ?: AuthType.NTLM,
        username = username,
        password = password,
        domain = domain
    )
}

@Serializable
data class AuthWsse(
    override val type: AuthType = AuthType.WSSE,
    val username: String = "",
    val password: String = ""
) : AuthConfig {
    constructor(type: String, username: String = "", password: String = "") : this(
        type = AuthType.fromValue(type) ?: AuthType.WSSE,
        username = username,
        password = password
    )
}

@Serializable
data class AuthOAuth1(
    override val type: AuthType = AuthType.OAUTH1,
    val consumerKey: String? = null,
    val consumerSecret: String? = null,
    val accessToken: String? = null,
    val accessTokenSecret: String? = null,
    val signatureMethod: OAuth1SignatureMethod? = null,
    val placement: OAuth1Placement = OAuth1Placement.HEADER
) : AuthConfig {
    constructor(
        consumerKey: String? = null,
        consumerSecret: String? = null,
        accessToken: String? = null,
        accessTokenSecret: String? = null,
        signatureMethod: String? = null,
        placement: String = "header"
    ) : this(
        type = AuthType.OAUTH1,
        consumerKey = consumerKey,
        consumerSecret = consumerSecret,
        accessToken = accessToken,
        accessTokenSecret = accessTokenSecret,
        signatureMethod = signatureMethod?.let { OAuth1SignatureMethod.fromValue(it) },
        placement = OAuth1Placement.fromValue(placement) ?: OAuth1Placement.HEADER
    )
}

@Serializable
data object AuthInherit : AuthConfig {
    override val type: AuthType get() = AuthType.INHERIT
}

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
        val authType = AuthType.fromValue(type)
        val json = decoder.json
        return when (authType) {
            AuthType.BASIC -> json.decodeFromJsonElement(AuthBasic.serializer(), obj)
            AuthType.BEARER -> json.decodeFromJsonElement(AuthBearer.serializer(), obj)
            AuthType.APIKEY -> json.decodeFromJsonElement(AuthApiKey.serializer(), obj)
            AuthType.OAUTH2 -> json.decodeFromJsonElement(AuthOAuth2.serializer(), obj)
            AuthType.AWSV4 -> json.decodeFromJsonElement(AuthAwsV4.serializer(), obj)
            AuthType.DIGEST -> json.decodeFromJsonElement(AuthDigest.serializer(), obj)
            AuthType.NTLM -> json.decodeFromJsonElement(AuthNTLM.serializer(), obj)
            AuthType.WSSE -> json.decodeFromJsonElement(AuthWsse.serializer(), obj)
            AuthType.OAUTH1 -> json.decodeFromJsonElement(AuthOAuth1.serializer(), obj)
            AuthType.INHERIT -> AuthInherit
            null -> AuthBearer(token = "")
        }
    }

    override fun serialize(encoder: Encoder, value: AuthConfig) {
        if (value is AuthInherit) {
            if (encoder is JsonEncoder) {
                encoder.encodeJsonElement(JsonPrimitive(AuthType.INHERIT.value))
            } else {
                encoder.encodeString(AuthType.INHERIT.value)
            }
            return
        }
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

