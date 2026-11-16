package io.github.makeforge.clapi.core.network.vault

import io.github.makeforge.clapi.core.network.createPlatformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

interface HashiCorpVaultClient {
    /**
     * Fetches a secret map from HashiCorp Vault KV v2 secret engine.
     *
     * @param vaultUrl Base URL of Vault server (e.g., "http://127.0.0.1:8200").
     * @param token Vault authentication token for X-Vault-Token header.
     * @param secretPath Path of the secret (e.g., "payment/foo").
     * @param mountPath KV v2 mount point (defaults to "secret").
     * @param namespace Vault enterprise namespace (optional).
     * @return Map of string keys to string values extracted from data.data envelope.
     * @throws IllegalStateException when response status is not 2xx or response cannot be parsed.
     */
    suspend fun fetchSecret(
        vaultUrl: String,
        token: String,
        secretPath: String,
        mountPath: String = "secret",
        namespace: String? = null,
    ): Map<String, String>
}

private val sharedVaultHttpClient: HttpClient by lazy {
    createPlatformHttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
}

class KtorHashiCorpVaultClient(
    private val httpClient: HttpClient = sharedVaultHttpClient,
) : HashiCorpVaultClient {
    companion object {
        val Shared: KtorHashiCorpVaultClient by lazy { KtorHashiCorpVaultClient() }
    }

    override suspend fun fetchSecret(
        vaultUrl: String,
        token: String,
        secretPath: String,
        mountPath: String,
        namespace: String?,
    ): Map<String, String> {
        val cleanUrl = vaultUrl.trim().trimEnd('/')
        val cleanMount = mountPath.trim().trim('/')
        val cleanPath = secretPath.trim().trimStart('/')
        val fullUrl = "$cleanUrl/v1/$cleanMount/data/$cleanPath"

        val response = httpClient.get(fullUrl) {
            header("X-Vault-Token", token.trim())
            if (!namespace.isNullOrBlank()) {
                header("X-Vault-Namespace", namespace.trim())
            }
            header("Accept", "application/json")
        }

        if (response.status.isSuccess()) {
            val body = response.body<VaultKvV2Response>()
            val result = mutableMapOf<String, String>()
            for ((key, element) in body.data.data) {
                result[key] = if (element is JsonPrimitive) element.content else element.toString()
            }
            return result
        }

        val code = response.status.value
        val relativePath = "$cleanMount/data/$cleanPath"
        when (response.status) {
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> {
                throw IllegalStateException("Vault authentication failed [HTTP $code]: permission denied")
            }
            HttpStatusCode.NotFound -> {
                throw IllegalStateException("Vault secret not found [HTTP 404] at path '$relativePath'")
            }
            else -> {
                val errorBody = try { response.bodyAsText() } catch (_: Exception) { "" }
                throw IllegalStateException("Vault request failed [HTTP $code]: $errorBody")
            }
        }
    }
}
