package io.github.makeforge.clapi.core.network.azure

import io.github.makeforge.clapi.core.network.createPlatformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

interface AzureKeyVaultClient {
    /**
     * Retrieves secret data from Azure Key Vault using a configuration object.
     */
    suspend fun fetchSecret(config: AzureKeyVaultConfig): Map<String, String>

    /**
     * Retrieves secret data from Azure Key Vault using explicit parameters.
     */
    suspend fun fetchSecret(
        vaultName: String,
        secretName: String,
        tenantId: String,
        clientId: String,
        clientSecret: String,
        authorityHost: String = "https://login.microsoftonline.com",
        secretVersion: String? = null,
        propertyKey: String? = null,
        apiVersion: String = "7.4",
    ): Map<String, String> = fetchSecret(
        AzureKeyVaultConfig(
            vaultName = vaultName,
            secretName = secretName,
            tenantId = tenantId,
            clientId = clientId,
            clientSecret = clientSecret,
            authorityHost = authorityHost,
            secretVersion = secretVersion,
            propertyKey = propertyKey,
            apiVersion = apiVersion,
        )
    )
}

@Serializable
private data class AzureTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
)

@Serializable
private data class AzureSecretResponse(
    val value: String? = null,
    val id: String? = null,
    val contentType: String? = null,
)

private val sharedAzureHttpClient: HttpClient by lazy {
    createPlatformHttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
}

class KtorAzureKeyVaultClient(
    private val httpClient: HttpClient = sharedAzureHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : AzureKeyVaultClient {
    companion object {
        val Shared: KtorAzureKeyVaultClient by lazy { KtorAzureKeyVaultClient() }
    }

    override suspend fun fetchSecret(config: AzureKeyVaultConfig): Map<String, String> {
        return fetchSecret(
            vaultName = config.vaultName,
            secretName = config.secretName,
            tenantId = config.tenantId,
            clientId = config.clientId,
            clientSecret = config.clientSecret,
            authorityHost = config.authorityHost,
            secretVersion = config.secretVersion,
            propertyKey = config.propertyKey,
            apiVersion = config.apiVersion,
        )
    }

    override suspend fun fetchSecret(
        vaultName: String,
        secretName: String,
        tenantId: String,
        clientId: String,
        clientSecret: String,
        authorityHost: String,
        secretVersion: String?,
        propertyKey: String?,
        apiVersion: String,
    ): Map<String, String> {
        require(vaultName.isNotBlank()) { "vaultName must not be blank" }
        require(secretName.isNotBlank()) { "secretName must not be blank" }

        val token = acquireToken(
            authorityHost = authorityHost,
            tenantId = tenantId,
            clientId = clientId,
            clientSecret = clientSecret,
        )

        val vaultUrl = formatVaultUrl(vaultName)
        val versionSegment = secretVersion?.takeIf { it.isNotBlank() }?.let { "/$it" } ?: ""
        val url = "$vaultUrl/secrets/$secretName$versionSegment?api-version=$apiVersion"

        val response = httpClient.get(url) {
            header("Authorization", "Bearer $token")
            header("Accept", "application/json")
        }

        val rawBody = response.bodyAsText()

        if (!response.status.isSuccess()) {
            if (response.status.value == 404 || rawBody.contains("SecretNotFound")) {
                throw IllegalStateException("External secret not found in Azure Key Vault (Secret Name: '$secretName')")
            }
            if (response.status.value in listOf(401, 403) || rawBody.contains("Unauthorized")) {
                throw IllegalStateException("Azure Key Vault authentication failed. Please check your credentials.")
            }
            throw IllegalStateException("Azure Key Vault request failed [HTTP ${response.status.value}]: $rawBody")
        }

        val secretResponse = json.decodeFromString(AzureSecretResponse.serializer(), rawBody)
        val rawValue = secretResponse.value ?: ""
        val contentType = secretResponse.contentType?.lowercase()

        val resultMap = parseSecretValue(rawValue, contentType)

        return if (!propertyKey.isNullOrBlank()) {
            val single = resultMap[propertyKey]
                ?: throw IllegalStateException("Field '$propertyKey' not found in Azure secret '$secretName'. Available keys: ${resultMap.keys.joinToString(", ")}")
            mapOf(propertyKey to single)
        } else {
            resultMap
        }
    }

    private suspend fun acquireToken(
        authorityHost: String,
        tenantId: String,
        clientId: String,
        clientSecret: String,
    ): String {
        if (authorityHost.equals("bypass", ignoreCase = true) || authorityHost.startsWith("mock://")) {
            return "dummy-token"
        }

        require(tenantId.isNotBlank()) { "tenantId must not be blank" }
        require(clientId.isNotBlank()) { "clientId must not be blank" }
        require(clientSecret.isNotBlank()) { "clientSecret must not be blank" }

        val cleanHost = authorityHost.trimEnd('/')
        val tokenEndpoint = "$cleanHost/$tenantId/oauth2/v2.0/token"

        val response = httpClient.submitForm(
            url = tokenEndpoint,
            formParameters = Parameters.build {
                append("grant_type", "client_credentials")
                append("client_id", clientId)
                append("client_secret", clientSecret)
                append("scope", "https://vault.azure.net/.default")
            },
        )

        val rawBody = response.bodyAsText()

        if (!response.status.isSuccess()) {
            throw IllegalStateException("Failed to authenticate with Azure Entra ID [HTTP ${response.status.value}]: $rawBody")
        }

        val parsed = json.decodeFromString(AzureTokenResponse.serializer(), rawBody)
        return parsed.accessToken
    }

    private fun formatVaultUrl(vaultName: String): String {
        val clean = vaultName.trim().trimEnd('/')
        return if (clean.startsWith("http://") || clean.startsWith("https://")) {
            clean
        } else {
            "https://$clean.vault.azure.net"
        }
    }

    private fun parseSecretValue(raw: String, contentType: String?): Map<String, String> {
        val trimmed = raw.trim()

        if (contentType == "text/plain") {
            return mapOf("secret" to trimmed)
        }

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
