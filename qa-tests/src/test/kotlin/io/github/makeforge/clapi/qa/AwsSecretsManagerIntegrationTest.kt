package io.github.makeforge.clapi.qa

import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.core.model.KeyValueEntry
import io.github.makeforge.clapi.core.model.RequestDefinition
import io.github.makeforge.clapi.core.model.opencollection.AwsSecretsManagerExternalSecret
import io.github.makeforge.clapi.core.model.opencollection.SecretVariable
import io.github.makeforge.clapi.core.network.KtorApiClient
import io.github.makeforge.clapi.core.network.NetworkEvent
import io.github.makeforge.clapi.core.network.RetryPolicy
import io.github.makeforge.clapi.core.network.aws.AwsSecretsManagerConfig
import io.github.makeforge.clapi.core.network.aws.KtorAwsSecretsManagerClient
import io.github.makeforge.clapi.core.network.vault.SecretResolutionService
import io.github.makeforge.clapi.core.storage.opencollection.OpenCollectionSerializer
import io.github.makeforge.clapi.core.network.execution.RequestExecutionService
import io.github.makeforge.clapi.server.module
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

class AwsSecretsManagerIntegrationTest {

    companion object {
        private const val LOCALSTACK_URL = "http://localhost:4566"
        private const val AWS_ACCESS_KEY = "test"
        private const val AWS_SECRET_KEY = "test"
        private const val AWS_REGION = "us-east-1"

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
    fun live_localstack_connection_fetches_seeded_secret() = runBlocking {
        val client = KtorAwsSecretsManagerClient()
        val config = AwsSecretsManagerConfig(
            secretId = "local/payment/foo",
            accessKey = AWS_ACCESS_KEY,
            secretKey = AWS_SECRET_KEY,
            region = AWS_REGION,
            endpointUrl = LOCALSTACK_URL,
        )
        val data = client.fetchSecret(config)

        assertEquals("foo_client_123", data["client_id"])
        assertEquals("sk_live_secret_456", data["client_secret"])
    }

    @Test
    fun execute_request_with_external_secrets_substitutes_values_from_aws() = runBlocking {
        val collectionFile = loadCollectionDocument()
        val doc = OpenCollectionSerializer.deserializeJson(collectionFile.readText())
        val env = doc.config?.environments?.firstOrNull { it.name == "AWS Staging" }
        assertNotNull(env, "Expected 'AWS Staging' environment in clapi-test-collection.json")
        val externalSecrets = env.externalSecrets?.variables.orEmpty()
        assertTrue(externalSecrets.isNotEmpty(), "Expected externalSecrets declared in AWS Staging environment")

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
            secretResolutionService = SecretResolutionService(awsClient = KtorAwsSecretsManagerClient()),
        )

        val request = RequestDefinition(
            id = "aws-test-1",
            name = "Echo Request with AWS Secrets",
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
            awsRegion = AWS_REGION,
            awsAccessKeyId = AWS_ACCESS_KEY,
            awsSecretAccessKey = AWS_SECRET_KEY,
            awsEndpointUrl = LOCALSTACK_URL,
        ).toList()

        val successEvent = events.filterIsInstance<NetworkEvent.Success>().firstOrNull()
        assertNotNull(successEvent, "Expected NetworkEvent.Success from sample server")
        assertEquals(200, successEvent.response.statusCode)

        val responseBody = successEvent.response.bodyText
        assertTrue(responseBody.contains("foo_client_123"), "Expected client_id to be substituted in response")
        assertTrue(responseBody.contains("sk_live_secret_456"), "Expected client_secret to be substituted in response")
    }

    @Test
    fun missing_aws_secret_aborts_with_not_found() = runBlocking {
        val client = KtorAwsSecretsManagerClient()
        val config = AwsSecretsManagerConfig(
            secretId = "non/existent/secret",
            accessKey = AWS_ACCESS_KEY,
            secretKey = AWS_SECRET_KEY,
            region = AWS_REGION,
            endpointUrl = LOCALSTACK_URL,
        )
        val ex = assertFailsWith<IllegalStateException> {
            client.fetchSecret(config)
        }
        assertTrue(ex.message!!.contains("not found in AWS Secrets Manager", ignoreCase = true))
    }

    @Test
    fun missing_target_field_in_aws_secret_aborts_with_descriptive_error() = runBlocking {
        val httpClient = HttpClient(CIO) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val apiClient = KtorApiClient(httpClient, RetryPolicy(maxAttempts = 1))
        val service = RequestExecutionService(
            apiClient = apiClient,
            secretResolutionService = SecretResolutionService(awsClient = KtorAwsSecretsManagerClient()),
        )

        val request = RequestDefinition(
            id = "aws-missing-field",
            name = "Missing Field Test",
            method = HttpMethodType.GET,
            url = "http://127.0.0.1:$serverPort/api/echo-headers?token={{_secret.wrong_field}}",
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )

        val externalSecrets = listOf(
            AwsSecretsManagerExternalSecret(
                name = "wrong_field",
                secretName = "local/payment/foo",
                targetField = "non_existent_key",
            )
        )

        val ex = assertFailsWith<IllegalStateException> {
            service.executeWithSecrets(
                request = request,
                externalSecrets = externalSecrets,
                awsRegion = AWS_REGION,
                awsAccessKeyId = AWS_ACCESS_KEY,
                awsSecretAccessKey = AWS_SECRET_KEY,
                awsEndpointUrl = LOCALSTACK_URL,
            ).toList()
        }
        assertTrue(ex.message!!.contains("Field 'non_existent_key' not found in AWS secret"))
    }

    @Test
    fun resolves_secret_variable_against_live_localstack_when_not_in_process_env() = runBlocking {
        val httpClient = HttpClient(CIO) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val apiClient = KtorApiClient(httpClient, RetryPolicy(maxAttempts = 1))
        val service = RequestExecutionService(
            apiClient = apiClient,
            secretResolutionService = SecretResolutionService(awsClient = KtorAwsSecretsManagerClient()),
        )

        val request = RequestDefinition(
            id = "req-aws-secret-var",
            name = "AWS Secret Variable Echo",
            method = HttpMethodType.GET,
            url = "http://127.0.0.1:$serverPort/api/echo-headers?secret_client={{client_id}}",
            headers = listOf(
                KeyValueEntry("X-Secret-Client", "{{client_id}}"),
            ),
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )

        val externalSecrets = listOf(
            AwsSecretsManagerExternalSecret(
                name = "client_id",
                secretName = "local/payment/foo",
                targetField = "client_id",
            ),
        )
        val secretVariables = listOf(
            SecretVariable(name = "client_id"),
        )

        val events = service.executeWithSecrets(
            request = request,
            externalSecrets = externalSecrets,
            secretVariables = secretVariables,
            awsRegion = AWS_REGION,
            awsAccessKeyId = AWS_ACCESS_KEY,
            awsSecretAccessKey = AWS_SECRET_KEY,
            awsEndpointUrl = LOCALSTACK_URL,
            processEnvProvider = { null },
        ).toList()

        val successEvent = events.filterIsInstance<NetworkEvent.Success>().firstOrNull()
        assertNotNull(successEvent, "Expected NetworkEvent.Success from sample server")
        assertEquals(200, successEvent.response.statusCode)
        assertTrue(successEvent.response.bodyText.contains("foo_client_123"), "Expected client_id resolved from LocalStack")
    }
}
