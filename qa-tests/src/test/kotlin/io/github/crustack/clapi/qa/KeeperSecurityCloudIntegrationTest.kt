package io.github.crustack.clapi.qa

import com.keepersecurity.secretsManager.core.TestStubs
import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.core.model.KeyValueEntry
import io.github.crustack.clapi.core.model.RequestDefinition
import io.github.crustack.clapi.core.model.opencollection.KeeperSecurityExternalSecret
import io.github.crustack.clapi.core.network.KtorApiClient
import io.github.crustack.clapi.core.network.NetworkEvent
import io.github.crustack.clapi.core.network.RetryPolicy
import io.github.crustack.clapi.core.network.keeper.KeeperClient
import io.github.crustack.clapi.core.network.keeper.KeeperConnectionStatusState
import io.github.crustack.clapi.core.network.keeper.KeeperRecordFieldSummary
import io.github.crustack.clapi.core.network.keeper.KeeperRecordFilter
import io.github.crustack.clapi.core.network.keeper.KeeperRecordSummary
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KeeperSecurityCloudIntegrationTest {

    companion object {
        private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
        private var serverPort: Int = 0
        private lateinit var httpClient: HttpClient
        private lateinit var apiClient: KtorApiClient
        private lateinit var executionService: RequestExecutionService
        private lateinit var keeperClient: KeeperClient

        @BeforeClass
        @JvmStatic
        fun setUp() {
            serverPort = ServerSocket(0).use { it.localPort }
            server = embeddedServer(Netty, port = serverPort, module = { module() }).start(wait = false)
            Thread.sleep(1000)

            httpClient = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
            }
            apiClient = KtorApiClient(httpClient, retryPolicy = RetryPolicy(maxAttempts = 1))
            executionService = RequestExecutionService(apiClient)

            val recordSummaries = loadTestRecords()
            keeperClient = object : KeeperClient {
                override suspend fun checkStatus() = KeeperConnectionStatusState.Connected
                override suspend fun testConnection() = true
                override suspend fun getRecords(recordsFilter: List<String>) = recordSummaries
                override suspend fun countMatchingRecords(recordTitle: String, customFilters: Map<String, String>) =
                    KeeperRecordFilter.filterRecords(recordSummaries, recordTitle, customFilters).size
                override suspend fun disconnect() {}
            }
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            server?.stop(500, 1000)
            httpClient.close()
        }

        private fun loadTestRecords(): List<KeeperRecordSummary> {
            val jsonStream = KeeperSecurityCloudIntegrationTest::class.java.getResourceAsStream("/secrets/keeper/test-records.json")
            val recordsJson = jsonStream?.bufferedReader()?.readText()
                ?: listOf(
                    File("qa-tests/src/test/resources/secrets/keeper/test-records.json"),
                    File("src/test/resources/secrets/keeper/test-records.json"),
                    File("../qa-tests/src/test/resources/secrets/keeper/test-records.json"),
                ).first { it.exists() }.readText()

            val jsonArr = Json.parseToJsonElement(recordsJson).jsonArray
            return jsonArr.map { elem ->
                val obj = elem.jsonObject
                val uid = obj["recordUid"]?.jsonPrimitive?.content ?: ""
                val title = obj["title"]?.jsonPrimitive?.content ?: ""
                val type = obj["type"]?.jsonPrimitive?.content ?: ""
                val password = obj["password"]?.jsonPrimitive?.contentOrNull
                val dataObj = obj["data"]?.jsonObject
                val fieldsArr = dataObj?.get("fields")?.jsonArray ?: JsonArray(emptyList())
                val customArr = dataObj?.get("custom")?.jsonArray ?: JsonArray(emptyList())

                fun parseFields(arr: JsonArray): List<KeeperRecordFieldSummary> {
                    return arr.map { fElem ->
                        val fObj = fElem.jsonObject
                        val label = fObj["label"]?.jsonPrimitive?.contentOrNull
                        val valArr = fObj["value"]?.jsonArray
                        val values = valArr?.map { vElem ->
                            if (vElem is JsonObject) vElem.toString()
                            else vElem.jsonPrimitive.content
                        } ?: emptyList()
                        val fieldType = when {
                            label != null -> label
                            values.any { it.contains("hostName") } -> "host"
                            values.any { it.startsWith("http") } -> "url"
                            type == "login" && values.any { it.contains("user") } -> "login"
                            else -> "text"
                        }
                        KeeperRecordFieldSummary(type = fieldType, label = label, values = values)
                    }
                }

                val fields = parseFields(fieldsArr)
                val customFields = parseFields(customArr)
                val effectiveCustom = (customFields + fields.filter { it.label != null }).distinctBy { it.label ?: it.type }

                KeeperRecordSummary(
                    recordUid = uid,
                    title = title,
                    type = type,
                    password = password,
                    fields = fields,
                    customFields = effectiveCustom,
                )
            }
        }
    }

    @Test
    fun testKeeperSdkTestStubsAccessibilityViaFriendPaths() {
        // Validates compiler friendPaths configuration
        TestStubs.transmissionKeyStub = { ByteArray(32) }
        assertTrue(TestStubs.transmissionKeyStubReady())
    }

    @Test
    fun execute_request_resolves_login_record_fields() = runBlocking {
        val externalSecrets = listOf(
            KeeperSecurityExternalSecret(
                name = "test_user_password",
                recordTitle = "clapi-test-user",
                targetField = "password",
            ),
            KeeperSecurityExternalSecret(
                name = "test_user_login",
                recordTitle = "clapi-test-user",
                targetField = "login",
            ),
            KeeperSecurityExternalSecret(
                name = "test_user_url",
                recordTitle = "clapi-test-user",
                targetField = "url",
            ),
        )

        val request = RequestDefinition(
            id = "req-test-1",
            name = "Test User Request",
            url = "http://localhost:$serverPort/api/echo-headers",
            method = HttpMethodType.GET,
            headers = listOf(
                KeyValueEntry(key = "X-Secret-Pass", value = "{{_secret.test_user_password}}"),
                KeyValueEntry(key = "X-Secret-Login", value = "{{_secret.test_user_login}}"),
                KeyValueEntry(key = "X-Secret-Url", value = "{{_secret.test_user_url}}"),
            ),
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
        )

        val events = executionService.executeWithSecrets(
            request = request,
            externalSecrets = externalSecrets,
            keeperConfigJson = "{}",
            keeperClient = keeperClient,
        ).toList()

        val completed = events.filterIsInstance<NetworkEvent.Success>().firstOrNull()
        assertNotNull(completed, "Expected Success network event")
        assertEquals(200, completed.response.statusCode)

        val body = completed.response.bodyText
        val jsonBody = Json.parseToJsonElement(body).jsonObject
        val receivedHeaders = jsonBody["receivedHeaders"]?.jsonObject
        assertNotNull(receivedHeaders)

        fun headerValue(name: String): String? =
            receivedHeaders.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.jsonPrimitive?.content

        assertEquals("clapi-password", headerValue("X-Secret-Pass"))
        assertEquals("clapi-test-user", headerValue("X-Secret-Login"))
        assertEquals("https://clapi.api.com", headerValue("X-Secret-Url"))
    }

    @Test
    fun execute_request_resolves_server_credentials_dotted_host_fields() = runBlocking {
        val externalSecrets = listOf(
            KeeperSecurityExternalSecret(
                name = "server_host_name",
                recordTitle = "clapi-test-server",
                targetField = "host.hostName",
            ),
            KeeperSecurityExternalSecret(
                name = "server_port_num",
                recordTitle = "clapi-test-server",
                targetField = "host.port",
            ),
            KeeperSecurityExternalSecret(
                name = "server_pwd",
                recordTitle = "clapi-test-server",
                targetField = "password",
            ),
        )

        val request = RequestDefinition(
            id = "req-test-2",
            name = "Test Server Request",
            url = "http://localhost:$serverPort/api/echo-headers",
            method = HttpMethodType.GET,
            headers = listOf(
                KeyValueEntry(key = "X-Host", value = "{{_secret.server_host_name}}"),
                KeyValueEntry(key = "X-Port", value = "{{_secret.server_port_num}}"),
                KeyValueEntry(key = "X-Pass", value = "{{_secret.server_pwd}}"),
            ),
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
        )

        val events = executionService.executeWithSecrets(
            request = request,
            externalSecrets = externalSecrets,
            keeperConfigJson = "{}",
            keeperClient = keeperClient,
        ).toList()

        val completed = events.filterIsInstance<NetworkEvent.Success>().firstOrNull()
        assertNotNull(completed)
        assertEquals(200, completed.response.statusCode)

        val body = completed.response.bodyText
        val jsonBody = Json.parseToJsonElement(body).jsonObject
        val receivedHeaders = jsonBody["receivedHeaders"]?.jsonObject
        assertNotNull(receivedHeaders)

        fun headerValue(name: String): String? =
            receivedHeaders.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.jsonPrimitive?.content

        assertEquals("clapi.api.com", headerValue("X-Host"))
        assertEquals("1234", headerValue("X-Port"))
        assertEquals("clapi-password", headerValue("X-Pass"))
    }

    @Test
    fun execute_request_resolves_custom_oauth_client_credentials_with_filters() = runBlocking {
        val externalSecrets = listOf(
            KeeperSecurityExternalSecret(
                name = "oauth_cid",
                recordTitle = "clapi-test-custom-client-credentials",
                targetField = "clientId",
                customFilters = mapOf("clientId" to "{{expected_cid}}"),
            ),
            KeeperSecurityExternalSecret(
                name = "oauth_csec",
                recordTitle = "clapi-test-custom-client-credentials",
                targetField = "clientSecret",
            ),
        )

        val request = RequestDefinition(
            id = "req-test-3",
            name = "Test Custom OAuth Request",
            url = "http://localhost:$serverPort/api/echo-headers",
            method = HttpMethodType.GET,
            headers = listOf(
                KeyValueEntry(key = "X-Client-Id", value = "{{_secret.oauth_cid}}"),
                KeyValueEntry(key = "X-Client-Secret", value = "{{_secret.oauth_csec}}"),
            ),
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
        )

        val events = executionService.executeWithSecrets(
            request = request,
            variableLayers = listOf(mapOf("expected_cid" to "clapi-client-id")),
            externalSecrets = externalSecrets,
            keeperConfigJson = "{}",
            keeperClient = keeperClient,
        ).toList()

        val completed = events.filterIsInstance<NetworkEvent.Success>().firstOrNull()
        assertNotNull(completed)
        assertEquals(200, completed.response.statusCode)

        val body = completed.response.bodyText
        val jsonBody = Json.parseToJsonElement(body).jsonObject
        val receivedHeaders = jsonBody["receivedHeaders"]?.jsonObject
        assertNotNull(receivedHeaders)

        fun headerValue(name: String): String? =
            receivedHeaders.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.jsonPrimitive?.content

        assertEquals("clapi-client-id", headerValue("X-Client-Id"))
        assertEquals("clapie-client-secret", headerValue("X-Client-Secret"))
    }

    @Test
    fun execute_request_fails_when_custom_filter_does_not_match() {
        val externalSecrets = listOf(
            KeeperSecurityExternalSecret(
                name = "mismatched_secret",
                recordTitle = "clapi-test-user",
                targetField = "password",
                customFilters = mapOf("url" to "https://mismatched.domain.com"),
            ),
        )

        val request = RequestDefinition(
            id = "req-test-4",
            name = "Test Mismatched Request",
            url = "http://localhost:$serverPort/api/echo-headers",
            method = HttpMethodType.GET,
            headers = listOf(
                KeyValueEntry(key = "X-Pass", value = "{{_secret.mismatched_secret}}"),
            ),
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
        )

        assertFailsWith<IllegalStateException> {
            runBlocking {
                executionService.executeWithSecrets(
                    request = request,
                    externalSecrets = externalSecrets,
                    keeperConfigJson = "{}",
                    keeperClient = keeperClient,
                ).toList()
            }
        }
    }
}
