package io.github.crustack.clapi.qa

import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.core.model.KeyValueEntry
import io.github.crustack.clapi.core.model.RequestDefinition
import io.github.crustack.clapi.core.network.KtorApiClient
import io.github.crustack.clapi.core.network.NetworkEvent
import io.github.crustack.clapi.core.network.RetryPolicy
import io.github.crustack.clapi.core.network.gcp.GcpSecretManagerConfig
import io.github.crustack.clapi.core.network.gcp.KtorGcpSecretManagerClient
import io.github.crustack.clapi.core.network.vault.SecretResolutionService
import io.github.crustack.clapi.core.storage.opencollection.OpenCollectionSerializer
import io.github.crustack.clapi.feature.requests.RequestExecutionService
import io.github.crustack.clapi.server.module
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.isSuccess
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

class GcpSecretManagerIntegrationTest {

    companion object {
        const val EMULATOR_URL = "http://localhost:8085"
        const val PROJECT_ID = "my-test-project"
        const val ACCESS_TOKEN = "emulator-dummy-token"

        private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
        private var serverPort: Int = 0
        private lateinit var httpClient: HttpClient
        private lateinit var client: KtorGcpSecretManagerClient

        @BeforeClass
        @JvmStatic
        fun setUp() {
            ensureRunning()
            httpClient = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
            }
            client = KtorGcpSecretManagerClient(httpClient)

            serverPort = ServerSocket(0).use { it.localPort }
            server = embeddedServer(Netty, port = serverPort, module = { module() }).start(wait = false)
            Thread.sleep(1000)
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            server?.stop(500, 1000)
            httpClient.close()
        }

        fun ensureRunning() {
            val testClient = HttpClient(CIO)
            try {
                val isHealthy = runBlocking {
                    try {
                        val response = testClient.get("$EMULATOR_URL/v1/projects")
                        response.status.isSuccess()
                    } catch (e: Exception) {
                        false
                    }
                }
                if (!isHealthy) {
                    val composeFile = listOf(
                        File("qa-tests/docker/secret-managers-compose.yml"),
                        File("docker/secret-managers-compose.yml"),
                        File("../qa-tests/docker/secret-managers-compose.yml"),
                    ).firstOrNull { it.exists() }

                    if (composeFile != null) {
                        val pb = ProcessBuilder("docker", "compose", "-f", composeFile.absolutePath, "up", "-d", "gcp-secretmanager", "gcp-seeder")
                        pb.redirectErrorStream(true)
                        val proc = pb.start()
                        proc.waitFor()
                        Thread.sleep(3000)
                    }
                }
            } finally {
                testClient.close()
            }
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

    private fun createBaseConfig(secretId: String = "", propertyKey: String? = null, version: String = "latest"): GcpSecretManagerConfig {
        return GcpSecretManagerConfig(
            projectId = PROJECT_ID,
            secretId = secretId,
            version = version,
            propertyKey = propertyKey,
            accessToken = ACCESS_TOKEN,
            endpointUrl = EMULATOR_URL,
        )
    }

    @Test
    fun testFetchJsonSecretAllKeys() = runBlocking {
        val config = createBaseConfig(secretId = "payment-foo")
        val secrets = client.fetchSecret(config)

        assertNotNull(secrets)
        assertEquals(2, secrets.size)
        assertEquals("gcp_client_789", secrets["client_id"])
        assertEquals("sk_gcp_secret_999", secrets["client_secret"])
    }

    @Test
    fun testFetchJsonSecretWithPropertyKey() = runBlocking {
        val config = createBaseConfig(
            secretId = "payment-foo",
            propertyKey = "client_secret",
        )
        val secrets = client.fetchSecret(config)

        assertNotNull(secrets)
        assertEquals(1, secrets.size)
        assertEquals("sk_gcp_secret_999", secrets["client_secret"])
    }

    @Test
    fun testFetchPlainTextSecret() = runBlocking {
        val config = createBaseConfig(secretId = "plain-token")
        val secrets = client.fetchSecret(config)

        assertNotNull(secrets)
        assertEquals(1, secrets.size)
        assertEquals("gcp-plain-token-value-12345", secrets["secret"])
    }

    @Test
    fun testMissingSecretThrowsException() {
        val config = createBaseConfig(secretId = "non-existent-secret-xyz")
        assertFailsWith<IllegalStateException> {
            runBlocking {
                client.fetchSecret(config)
            }
        }
    }

    @Test
    fun testMissingPropertyKeyThrowsException() {
        val config = createBaseConfig(
            secretId = "payment-foo",
            propertyKey = "non_existent_key",
        )
        assertFailsWith<NoSuchElementException> {
            runBlocking {
                client.fetchSecret(config)
            }
        }
    }

    @Test
    fun execute_request_with_external_secrets_substitutes_values_from_gcp_secret_manager() = runBlocking {
        val collectionFile = loadCollectionDocument()
        val doc = OpenCollectionSerializer.deserializeJson(collectionFile.readText())
        val env = doc.config?.environments?.firstOrNull { it.name == "GCP Staging" }
        assertNotNull(env, "Expected 'GCP Staging' environment in clapi-test-collection.json")
        val externalSecrets = env.externalSecrets?.variables.orEmpty()
        assertTrue(externalSecrets.isNotEmpty(), "Expected externalSecrets declared in GCP Staging environment")

        val apiClient = KtorApiClient(
            httpClient = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
            },
            retryPolicy = RetryPolicy(maxAttempts = 1),
        )

        val service = RequestExecutionService(
            apiClient = apiClient,
            secretResolutionService = SecretResolutionService(gcpClient = client),
        )

        val request = RequestDefinition(
            id = "gcp-test-1",
            name = "Echo Request with GCP Secrets",
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
            gcpProjectId = PROJECT_ID,
            gcpAccessToken = ACCESS_TOKEN,
            gcpEndpointUrl = EMULATOR_URL,
            gcpClient = client,
        ).toList()

        val successEvent = events.filterIsInstance<NetworkEvent.Success>().firstOrNull()
        assertNotNull(successEvent, "Expected NetworkEvent.Success from sample server")
        assertEquals(200, successEvent.response.statusCode)

        val responseBody = successEvent.response.bodyText
        assertTrue(responseBody.contains("gcp_client_789"), "Expected client_id to be substituted in response")
        assertTrue(responseBody.contains("sk_gcp_secret_999"), "Expected client_secret to be substituted in response")
        assertTrue(responseBody.contains("gcp-plain-token-value-12345"), "Expected plain_token to be substituted in response")
    }
}
