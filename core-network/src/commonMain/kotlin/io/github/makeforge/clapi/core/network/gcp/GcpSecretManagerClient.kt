package io.github.makeforge.clapi.core.network.gcp

import io.github.makeforge.clapi.core.network.createPlatformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

interface GcpSecretManagerClient {
    /**
     * Retrieves secret data from GCP Secret Manager using a configuration object.
     */
    suspend fun fetchSecret(config: GcpSecretManagerConfig): Map<String, String>

    /**
     * Retrieves secret data from GCP Secret Manager using explicit parameters.
     */
    suspend fun fetchSecret(
        projectId: String,
        secretId: String,
        version: String = "latest",
        propertyKey: String? = null,
        accessToken: String,
        endpointUrl: String = "https://secretmanager.googleapis.com",
    ): Map<String, String> = fetchSecret(
        GcpSecretManagerConfig(
            projectId = projectId,
            secretId = secretId,
            version = version,
            propertyKey = propertyKey,
            accessToken = accessToken,
            endpointUrl = endpointUrl,
        )
    )
}

private fun defaultGcpHttpClient(): HttpClient = createPlatformHttpClient {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }
}

class KtorGcpSecretManagerClient(
    private val httpClient: HttpClient = defaultGcpHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : GcpSecretManagerClient {

    override suspend fun fetchSecret(config: GcpSecretManagerConfig): Map<String, String> {
        require(config.projectId.isNotBlank()) { "GCP Project ID must not be blank" }
        require(config.secretId.isNotBlank()) { "GCP Secret ID must not be blank" }
        require(config.accessToken.isNotBlank()) { "GCP Access Token must not be blank" }

        val baseUrl = (config.endpointUrl.takeIf { it.isNotBlank() } ?: "https://secretmanager.googleapis.com")
            .trim()
            .trimEnd('/')
        val project = config.projectId.trim()
        val secretId = config.secretId.trim()
        val version = config.version.ifBlank { "latest" }.trim()

        val url = "$baseUrl/v1/projects/$project/secrets/$secretId/versions/$version:access"

        val response = httpClient.get(url) {
            header("Authorization", "Bearer ${config.accessToken}")
            header("Accept", "application/json")
        }

        val rawBody = response.bodyAsText()

        if (!response.status.isSuccess()) {
            throw IllegalStateException("GCP Secret Manager request failed [HTTP ${response.status.value}]: $rawBody")
        }

        val parsedResponse = json.decodeFromString(GcpAccessSecretVersionResponse.serializer(), rawBody)
        val base64Data = parsedResponse.payload?.data
            ?: throw IllegalStateException("GCP Secret Manager response did not contain payload data")

        val decodedRaw = decodeBase64ToString(base64Data.trim())
        val resultMap = parseSecretValue(decodedRaw)

        return if (!config.propertyKey.isNullOrBlank()) {
            val single = resultMap[config.propertyKey]
                ?: throw NoSuchElementException("Key '${config.propertyKey}' not found in GCP secret '$secretId'. Available keys: ${resultMap.keys.joinToString(", ")}")
            mapOf(config.propertyKey to single)
        } else {
            resultMap
        }
    }

    private fun parseSecretValue(raw: String): Map<String, String> {
        val trimmed = raw.trim()
        return try {
            val element = json.parseToJsonElement(trimmed)
            if (element is JsonObject) {
                element.mapValues { (_, value) ->
                    if (value is JsonPrimitive) value.content else value.toString()
                }
            } else {
                mapOf("secret" to trimmed)
            }
        } catch (_: Exception) {
            mapOf("secret" to trimmed)
        }
    }
}

/**
 * Decodes a Base64 string (standard or URL-safe) into UTF-8 text without external platform dependencies.
 */
internal fun decodeBase64ToString(input: String): String {
    val clean = input.filter { !it.isWhitespace() }
    var s = clean.replace('-', '+').replace('_', '/')
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
