package io.github.crustack.clapi.qa

import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.core.model.KeyValueEntry
import io.github.crustack.clapi.core.model.RequestDefinition
import io.github.crustack.clapi.core.network.KtorApiClient
import io.github.crustack.clapi.core.network.NetworkEvent
import io.github.crustack.clapi.core.network.RetryPolicy
import io.github.crustack.clapi.core.network.vault.SecretResolutionService
import io.github.crustack.clapi.core.storage.opencollection.OpenCollectionSerializer
import io.github.crustack.clapi.core.network.execution.RequestExecutionService
import io.github.crustack.clapi.server.module
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AzureRequestExecutionIntegrationTest {

    companion object {
        private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
        private var serverPort: Int = 0

        @BeforeClass
        @JvmStatic
        fun startServer() {
            LowkeyVaultTestFixture.ensureRunning()
            serverPort = ServerSocket(0).use { it.localPort }
            server = embeddedServer(Netty, port = serverPort, module = { module() }).start(wait = false)
            Thread.sleep(1000)
        }

        @AfterClass
        @JvmStatic
        fun stopServer() {
            server?.stop(500, 1000)
        }

        private fun loadCollectionDocument(): File {
            val candidates = listOf(
                File("qa-tests/fixtures/clapi-test-collection.json"),
                File("fixtures/clapi-test-collection.json"),
                File("../qa-tests/fixtures/clapi-test-collection.json"),
            )
            return candidates.firstOrNull { it.exists() }
                ?: error("clapi-test-collection.json fixture not found")
        }
    }

    @Test
    fun execute_request_with_external_secrets_substitutes_values_from_azure_key_vault() = runBlocking {
        val collectionFile = loadCollectionDocument()
        val doc = OpenCollectionSerializer.deserializeJson(collectionFile.readText())
        val env = doc.config?.environments?.firstOrNull { it.name == "Azure Staging" }
        assertNotNull(env, "Expected 'Azure Staging' environment in clapi-test-collection.json")
        val externalSecrets = env.externalSecrets?.variables.orEmpty()
        assertTrue(externalSecrets.isNotEmpty(), "Expected externalSecrets declared in Azure Staging environment")

        val client = KtorApiClient(
            httpClient = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
            },
            retryPolicy = RetryPolicy(maxAttempts = 1),
        )

        val azureClient = LowkeyVaultTestFixture.createAzureClient()
        val service = RequestExecutionService(
            apiClient = client,
            secretResolutionService = SecretResolutionService(azureClient = azureClient),
        )

        val request = RequestDefinition(
            id = "azure-test-1",
            name = "Echo Request with Azure Secrets",
            method = HttpMethodType.GET,
            url = "http://127.0.0.1:$serverPort/api/echo-headers?secret_client={{_secret.client_id}}",
            headers = listOf(
                KeyValueEntry("X-Client-Id", "{{_secret.client_id}}"),
                KeyValueEntry("X-Client-Secret", "{{_secret.client_secret}}"),
                KeyValueEntry("X-Plain-Token", "{{_secret.plain_token}}"),
            ),
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )

        val events = service.executeWithSecrets(
            request = request,
            variableLayers = emptyList(),
            externalSecrets = externalSecrets,
            azureVaultName = LowkeyVaultTestFixture.VAULT_URL,
            azureTenantId = LowkeyVaultTestFixture.TENANT_ID,
            azureClientId = LowkeyVaultTestFixture.CLIENT_ID,
            azureClientSecret = LowkeyVaultTestFixture.CLIENT_SECRET,
            azureAuthorityHost = LowkeyVaultTestFixture.AUTHORITY_HOST,
            azureApiVersion = LowkeyVaultTestFixture.API_VERSION,
            azureClient = azureClient,
        ).toList()

        val successEvent = events.filterIsInstance<NetworkEvent.Success>().firstOrNull()
        assertNotNull(successEvent, "Expected NetworkEvent.Success from sample server")
        assertEquals(200, successEvent.response.statusCode)

        val responseBody = successEvent.response.bodyText
        assertTrue(responseBody.contains("azure_client_999"), "Expected client_id to be substituted in response")
        assertTrue(responseBody.contains("sk_azure_secret_888"), "Expected client_secret to be substituted in response")
        assertTrue(responseBody.contains("super-secret-plain-token-12345"), "Expected plain_token to be substituted in response")
    }
}
