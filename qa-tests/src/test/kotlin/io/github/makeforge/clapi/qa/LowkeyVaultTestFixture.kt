package io.github.makeforge.clapi.qa

import io.github.makeforge.clapi.core.network.azure.AzureKeyVaultConfig
import io.github.makeforge.clapi.core.network.azure.KtorAzureKeyVaultClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

object LowkeyVaultTestFixture {
    const val VAULT_URL = "https://localhost:8443"
    const val TENANT_ID = "00000000-0000-0000-0000-000000000000"
    const val CLIENT_ID = "test-client-id"
    const val CLIENT_SECRET = "test-secret"
    const val AUTHORITY_HOST = "bypass"
    const val API_VERSION = "7.4"

    fun createInsecureHttpClient(): HttpClient {
        return HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            engine {
                https {
                    trustManager = object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    }
                }
            }
        }
    }

    fun createAzureClient(httpClient: HttpClient = createInsecureHttpClient()): KtorAzureKeyVaultClient {
        return KtorAzureKeyVaultClient(httpClient)
    }

    fun createBaseConfig(secretName: String = "", propertyKey: String? = null): AzureKeyVaultConfig {
        return AzureKeyVaultConfig(
            vaultName = VAULT_URL,
            secretName = secretName,
            propertyKey = propertyKey,
            tenantId = TENANT_ID,
            clientId = CLIENT_ID,
            clientSecret = CLIENT_SECRET,
            authorityHost = AUTHORITY_HOST,
            apiVersion = API_VERSION,
        )
    }

    fun ensureRunning() {
        val client = createInsecureHttpClient()
        try {
            val isHealthy = runBlocking {
                try {
                    val response = client.get("$VAULT_URL/ping")
                    response.status.isSuccess()
                } catch (e: Exception) {
                    false
                }
            }
            if (!isHealthy) {
                val composeFile = listOf(
                    File("qa-tests/docker/azure-keyvault-compose.yml"),
                    File("docker/azure-keyvault-compose.yml"),
                    File("../qa-tests/docker/azure-keyvault-compose.yml"),
                ).firstOrNull { it.exists() }

                if (composeFile != null) {
                    val pb = ProcessBuilder("docker", "compose", "-f", composeFile.absolutePath, "up", "-d")
                    pb.redirectErrorStream(true)
                    val proc = pb.start()
                    proc.waitFor()
                    Thread.sleep(3000)
                }
            }
        } finally {
            client.close()
        }
    }
}
