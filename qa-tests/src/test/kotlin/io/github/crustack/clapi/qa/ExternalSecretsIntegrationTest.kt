package io.github.crustack.clapi.qa

import io.github.crustack.clapi.core.model.AuthConfig
import io.github.crustack.clapi.core.model.AuthType
import io.github.crustack.clapi.core.model.BodyType
import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.core.model.KeyValueEntry
import io.github.crustack.clapi.core.model.RequestBody
import io.github.crustack.clapi.core.model.RequestDefinition
import io.github.crustack.clapi.core.model.opencollection.HashicorpVaultExternalSecret
import io.github.crustack.clapi.core.model.opencollection.SecretVariable
import io.github.crustack.clapi.core.network.KtorApiClient
import io.github.crustack.clapi.core.network.NetworkEvent
import io.github.crustack.clapi.core.network.RetryPolicy
import io.github.crustack.clapi.core.network.vault.KtorHashiCorpVaultClient
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExternalSecretsIntegrationTest {

    companion object {
        private const val VAULT_URL = "http://127.0.0.1:8200"
        private const val VAULT_TOKEN = "root-dev-token"

        private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
        private var serverPort: Int = 0

        @BeforeClass
        @JvmStatic
        fun startServer() {
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
    fun live_vault_connection_fetches_seeded_secret() = runBlocking {
        val vaultClient = KtorHashiCorpVaultClient()
        val data = vaultClient.fetchSecret(
            vaultUrl = VAULT_URL,
            token = VAULT_TOKEN,
            secretPath = "payment/foo",
            mountPath = "secret",
        )

        assertEquals("foo_client_123", data["client_id"])
        assertEquals("sk_live_secret_456", data["client_secret"])
    }

    @Test
    fun execute_request_with_external_secrets_substitutes_values_from_vault() = runBlocking {
        val collectionFile = loadCollectionDocument()
        val doc = OpenCollectionSerializer.deserializeJson(collectionFile.readText())
        val env = doc.config?.environments?.firstOrNull { it.name == "Vault Staging" }
        assertNotNull(env, "Expected 'Vault Staging' environment in clapi-test-collection.json")
        val externalSecrets = env.externalSecrets?.variables.orEmpty()
        assertTrue(externalSecrets.isNotEmpty(), "Expected externalSecrets declared in Vault Staging environment")

        val client = KtorApiClient(
            httpClient = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
            },
            retryPolicy = RetryPolicy(maxAttempts = 1),
        )

        val service = RequestExecutionService(
            apiClient = client,
            secretResolutionService = SecretResolutionService(KtorHashiCorpVaultClient()),
        )

        // Make a request to the local sample-server with secret in header and query param
        val request = RequestDefinition(
            id = "vault-test-1",
            name = "Echo Request with Secrets",
            method = HttpMethodType.GET,
            url = "http://127.0.0.1:$serverPort/api/echo-headers?secret_client={{_secret.client_id}}",
            headers = listOf(
                KeyValueEntry("X-Client-Id", "{{_secret.client_id}}"),
                KeyValueEntry("X-Client-Secret", "{{_secret.client_secret}}"),
            ),
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )

        val events = service.executeWithSecrets(
            request = request,
            variableLayers = emptyList(),
            externalSecrets = externalSecrets,
            vaultUrl = VAULT_URL,
            vaultToken = VAULT_TOKEN,
        ).toList()

        val successEvent = events.filterIsInstance<NetworkEvent.Success>().firstOrNull()
        assertNotNull(successEvent, "Expected NetworkEvent.Success from sample server")
        assertEquals(200, successEvent.response.statusCode)

        val responseBody = successEvent.response.bodyText
        assertTrue(responseBody.contains("foo_client_123"), "Expected client_id to be substituted in response")
        assertTrue(responseBody.contains("sk_live_secret_456"), "Expected client_secret to be substituted in response")
    }

    @Test
    fun invalid_vault_token_aborts_with_permission_denied() = runBlocking {
        val vaultClient = KtorHashiCorpVaultClient()
        val ex = assertFailsWith<IllegalStateException> {
            vaultClient.fetchSecret(
                vaultUrl = VAULT_URL,
                token = "invalid-token",
                secretPath = "payment/foo",
            )
        }
        assertTrue(ex.message?.contains("permission denied") == true || ex.message?.contains("403") == true)
    }

    @Test
    fun missing_vault_path_aborts_with_not_found() = runBlocking {
        val vaultClient = KtorHashiCorpVaultClient()
        val ex = assertFailsWith<IllegalStateException> {
            vaultClient.fetchSecret(
                vaultUrl = VAULT_URL,
                token = VAULT_TOKEN,
                secretPath = "payment/nonexistent_path_123",
            )
        }
        assertTrue(ex.message?.contains("404") == true)
    }

    @Test
    fun resolves_secret_variable_against_live_vault_when_not_in_process_env() = runBlocking {
        val httpClient = HttpClient(CIO) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val apiClient = KtorApiClient(httpClient, RetryPolicy(maxAttempts = 1))
        val service = RequestExecutionService(
            apiClient = apiClient,
            secretResolutionService = SecretResolutionService(KtorHashiCorpVaultClient()),
        )

        val request = RequestDefinition(
            id = "req-secret-var",
            name = "Secret Variable Echo",
            method = HttpMethodType.GET,
            url = "http://127.0.0.1:$serverPort/api/echo-headers?secret_client={{client_id}}",
            headers = listOf(
                KeyValueEntry("X-Secret-Client", "{{client_id}}"),
            ),
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )

        val externalSecrets = listOf(
            HashicorpVaultExternalSecret(name = "client_id", path = "payment/foo"),
        )
        val secretVariables = listOf(
            SecretVariable(name = "client_id"),
        )

        val events = service.executeWithSecrets(
            request = request,
            externalSecrets = externalSecrets,
            secretVariables = secretVariables,
            vaultUrl = VAULT_URL,
            vaultToken = VAULT_TOKEN,
            processEnvProvider = { null },
        ).toList()

        val successEvent = events.filterIsInstance<NetworkEvent.Success>().firstOrNull()
        assertNotNull(successEvent, "Expected NetworkEvent.Success from sample server")
        assertEquals(200, successEvent.response.statusCode)
        assertTrue(successEvent.response.bodyText.contains("foo_client_123"), "Expected client_id resolved from Vault")
    }

    @Test
    fun unreferenced_secret_variable_does_not_shadow_lower_priority_collection_variable_and_post_script_secret_in_auth_params_is_resolved() = runBlocking {
        val httpClient = HttpClient(CIO) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val apiClient = KtorApiClient(httpClient, RetryPolicy(maxAttempts = 1))
        val service = RequestExecutionService(
            apiClient = apiClient,
            secretResolutionService = SecretResolutionService(KtorHashiCorpVaultClient()),
        )

        val request = RequestDefinition(
            id = "req-secret-auth-and-layer",
            name = "Secret Auth and Layer Echo",
            method = HttpMethodType.GET,
            url = "http://127.0.0.1:$serverPort/api/echo-headers",
            headers = listOf(
                KeyValueEntry("X-Fallback-Var", "{{SHARED_VAR}}"),
            ),
            auth = AuthConfig(
                type = AuthType.BEARER,
                params = mapOf("token" to "{{_secret.client_secret}}"),
            ),
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )

        val externalSecrets = listOf(
            HashicorpVaultExternalSecret(name = "client_secret", path = "payment/foo"),
        )
        // UNUSED_SECRET is declared as a secret variable, while SHARED_VAR is in a lower variable layer
        val secretVariables = listOf(
            SecretVariable(name = "UNUSED_SECRET"),
        )

        val events = service.executeWithSecrets(
            request = request,
            variableLayers = listOf(
                mapOf("SHARED_VAR" to "collection-fallback-value"),
            ),
            externalSecrets = externalSecrets,
            secretVariables = secretVariables,
            vaultUrl = VAULT_URL,
            vaultToken = VAULT_TOKEN,
            processEnvProvider = { null },
        ).toList()

        val successEvent = events.filterIsInstance<NetworkEvent.Success>().firstOrNull()
        assertNotNull(successEvent)
        assertEquals(200, successEvent.response.statusCode)
        assertTrue(successEvent.response.bodyText.contains("collection-fallback-value"))
        assertTrue(successEvent.response.bodyText.contains("sk_live_secret_456"))
        apiClient.close()
    }
}
