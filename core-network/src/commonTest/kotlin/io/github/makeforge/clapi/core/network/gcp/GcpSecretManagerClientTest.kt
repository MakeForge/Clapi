package io.github.makeforge.clapi.core.network.gcp

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GcpSecretManagerClientTest {

    private fun createMockClient(
        handler: (requestUrl: String, headers: Map<String, List<String>>) -> Pair<HttpStatusCode, String>,
    ): KtorGcpSecretManagerClient {
        val mockEngine = MockEngine { request ->
            val (status, body) = handler(request.url.toString(), request.headers.entries().associate { it.key to it.value })
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return KtorGcpSecretManagerClient(httpClient)
    }

    @Test
    fun fetchSecret_fetches_json_secret_and_parses_all_keys() = runTest {
        val client = createMockClient { url, headers ->
            assertTrue(url.contains("/v1/projects/my-test-project/secrets/payment-foo/versions/latest:access"))
            assertEquals("Bearer dummy-access-token", headers["Authorization"]?.firstOrNull())
            HttpStatusCode.OK to """
                {
                    "name": "projects/my-test-project/secrets/payment-foo/versions/1",
                    "payload": {
                        "data": "eyJjbGllbnRfaWQiOiJnY3BfY2xpZW50Xzc4OSIsImNsaWVudF9zZWNyZXQiOiJza19nY3Bfc2VjcmV0Xzk5OSJ9"
                    }
                }
            """.trimIndent()
        }

        val config = GcpSecretManagerConfig(
            projectId = "my-test-project",
            secretId = "payment-foo",
            accessToken = "dummy-access-token",
            endpointUrl = "http://localhost:8085",
        )

        val secrets = client.fetchSecret(config)
        assertNotNull(secrets)
        assertEquals(2, secrets.size)
        assertEquals("gcp_client_789", secrets["client_id"])
        assertEquals("sk_gcp_secret_999", secrets["client_secret"])
    }

    @Test
    fun fetchSecret_with_propertyKey_filters_to_single_key() = runTest {
        val client = createMockClient { _, _ ->
            HttpStatusCode.OK to """
                {
                    "name": "projects/my-test-project/secrets/payment-foo/versions/latest",
                    "payload": {
                        "data": "eyJjbGllbnRfaWQiOiJnY3BfY2xpZW50Xzc4OSIsImNsaWVudF9zZWNyZXQiOiJza19nY3Bfc2VjcmV0Xzk5OSJ9"
                    }
                }
            """.trimIndent()
        }

        val config = GcpSecretManagerConfig(
            projectId = "my-test-project",
            secretId = "payment-foo",
            propertyKey = "client_secret",
            accessToken = "dummy-access-token",
        )

        val secrets = client.fetchSecret(config)
        assertNotNull(secrets)
        assertEquals(1, secrets.size)
        assertEquals("sk_gcp_secret_999", secrets["client_secret"])
    }

    @Test
    fun fetchSecret_fetches_plaintext_secret_under_default_secret_key() = runTest {
        val client = createMockClient { _, _ ->
            HttpStatusCode.OK to """
                {
                    "name": "projects/my-test-project/secrets/plain-token/versions/1",
                    "payload": {
                        "data": "Z2NwLXBsYWluLXRva2VuLXZhbHVlLTEyMzQ1"
                    }
                }
            """.trimIndent()
        }

        val config = GcpSecretManagerConfig(
            projectId = "my-test-project",
            secretId = "plain-token",
            version = "1",
            accessToken = "dummy-access-token",
        )

        val secrets = client.fetchSecret(config)
        assertNotNull(secrets)
        assertEquals(1, secrets.size)
        assertEquals("gcp-plain-token-value-12345", secrets["secret"])
    }

    @Test
    fun fetchSecret_throws_on_http_error() = runTest {
        val client = createMockClient { _, _ ->
            HttpStatusCode.NotFound to """
                {
                    "error": {
                        "code": 404,
                        "message": "Secret [projects/my-test-project/secrets/not-found] not found"
                    }
                }
            """.trimIndent()
        }

        val config = GcpSecretManagerConfig(
            projectId = "my-test-project",
            secretId = "not-found",
            accessToken = "dummy-access-token",
        )

        val ex = assertFailsWith<IllegalStateException> {
            client.fetchSecret(config)
        }
        assertTrue(ex.message!!.contains("GCP Secret Manager request failed [HTTP 404]"))
    }

    @Test
    fun fetchSecret_throws_on_missing_propertyKey() = runTest {
        val client = createMockClient { _, _ ->
            HttpStatusCode.OK to """
                {
                    "name": "projects/my-test-project/secrets/payment-foo/versions/latest",
                    "payload": {
                        "data": "eyJjbGllbnRfaWQiOiJnY3BfY2xpZW50Xzc4OSJ9"
                    }
                }
            """.trimIndent()
        }

        val config = GcpSecretManagerConfig(
            projectId = "my-test-project",
            secretId = "payment-foo",
            propertyKey = "non_existent_field",
            accessToken = "dummy-access-token",
        )

        val ex = assertFailsWith<NoSuchElementException> {
            client.fetchSecret(config)
        }
        assertTrue(ex.message!!.contains("Key 'non_existent_field' not found in GCP secret"))
    }

    @Test
    fun fetchSecret_throws_when_required_config_blank() = runTest {
        val client = createMockClient { _, _ -> HttpStatusCode.OK to "{}" }

        assertFailsWith<IllegalArgumentException> {
            client.fetchSecret(GcpSecretManagerConfig(projectId = "", secretId = "foo", accessToken = "token"))
        }
        assertFailsWith<IllegalArgumentException> {
            client.fetchSecret(GcpSecretManagerConfig(projectId = "proj", secretId = "", accessToken = "token"))
        }
        assertFailsWith<IllegalArgumentException> {
            client.fetchSecret(GcpSecretManagerConfig(projectId = "proj", secretId = "foo", accessToken = ""))
        }
    }
}
