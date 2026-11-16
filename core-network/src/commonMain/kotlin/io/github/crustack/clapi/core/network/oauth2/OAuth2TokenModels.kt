package io.github.crustack.clapi.core.network.oauth2

import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@Serializable
data class OAuth2TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("id_token") val idToken: String? = null,
    @SerialName("scope") val scope: String? = null,
)

data class OAuth2TokenState(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val refreshToken: String? = null,
    val idToken: String? = null,
    val scope: String? = null,
    val expiresAtEpochMs: Long? = null,
    val fetchedAtEpochMs: Long = currentEpochMillis(),
) {
    /**
     * Checks if the access token is valid and unexpired.
     * @param bufferMs Safety margin in milliseconds before actual expiration (default 30 seconds).
     * @param nowEpochMs Current epoch timestamp in milliseconds.
     */
    fun isValid(bufferMs: Long = 30_000L, nowEpochMs: Long = currentEpochMillis()): Boolean {
        if (accessToken.isBlank()) return false
        val expiresAt = expiresAtEpochMs ?: extractJwtExpiryEpochMs(accessToken)
        return expiresAt == null || expiresAt > (nowEpochMs + bufferMs)
    }

    companion object {
        fun fromResponse(
            response: OAuth2TokenResponse,
            nowEpochMs: Long = currentEpochMillis(),
        ): OAuth2TokenState {
            val expiresAt = response.expiresIn?.let { nowEpochMs + it * 1000L }
                ?: extractJwtExpiryEpochMs(response.accessToken)
            return OAuth2TokenState(
                accessToken = response.accessToken,
                tokenType = response.tokenType.ifBlank { "Bearer" },
                refreshToken = response.refreshToken,
                idToken = response.idToken,
                scope = response.scope,
                expiresAtEpochMs = expiresAt,
                fetchedAtEpochMs = nowEpochMs,
            )
        }
    }
}

internal fun currentEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()

private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Extracts expiration timestamp in epoch milliseconds from a JWT's 'exp' claim if present.
 */
fun extractJwtExpiryEpochMs(jwt: String): Long? {
    val parts = jwt.split('.')
    if (parts.size < 2) return null
    return runCatching {
        val payloadJson = decodeBase64UrlToString(parts[1])
        val element = jsonParser.parseToJsonElement(payloadJson)
        val exp = element.jsonObject["exp"]?.jsonPrimitive?.longOrNull ?: return null
        exp * 1000L
    }.getOrNull()
}

/**
 * Decodes a URL-safe Base64 string into UTF-8 text without external dependencies.
 */
fun decodeBase64UrlToString(input: String): String {
    var s = input.replace('-', '+').replace('_', '/')
    val remainder = s.length % 4
    if (remainder > 0) {
        s += "=".repeat(4 - remainder)
    }

    val output = ArrayList<Byte>((s.length * 3) / 4)
    var index = 0

    fun decodeChar(c: Char): Int = when (c) {
        in 'A'..'Z' -> c.code - 'A'.code
        in 'a'..'z' -> c.code - 'a'.code + 26
        in '0'..'9' -> c.code - '0'.code + 52
        '+' -> 62
        '/' -> 63
        '=' -> -2
        else -> -1
    }

    while (index < s.length) {
        val c0 = decodeChar(s[index++])
        val c1 = if (index < s.length) decodeChar(s[index++]) else -2
        val c2 = if (index < s.length) decodeChar(s[index++]) else -2
        val c3 = if (index < s.length) decodeChar(s[index++]) else -2

        if (c0 < 0 || c1 < 0 || c2 == -1 || c3 == -1) continue

        val b0 = (c0 shl 2) or (c1 ushr 4)
        output.add((b0 and 0xFF).toByte())

        if (c2 >= 0) {
            val b1 = ((c1 and 0x0F) shl 4) or (c2 ushr 2)
            output.add((b1 and 0xFF).toByte())
        }

        if (c3 >= 0 && c2 >= 0) {
            val b2 = ((c2 and 0x03) shl 6) or c3
            output.add((b2 and 0xFF).toByte())
        }
    }

    return output.toByteArray().decodeToString()
}
