package io.github.crustack.clapi.core.network.azure

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

class AzureKeyVaultClientTest {

    private fun createMockClient(
        handler: (requestUrl: String, headers: Map<String, List<String>>) -> Pair<HttpStatusCode, String>,
    ): KtorAzureKeyVaultClient {
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
        return KtorAzureKeyVaultClient(httpClient)
    }

    @Test
    fun fetchSecret_with_bypass_fetches_json_secret_and_parses_all_keys() = runTest {
        val client = createMockClient { url, headers ->
            assertTrue(url.contains("/secrets/payment-foo?api-version=7.4"))
            assertEquals("Bearer dummy-token", headers["Authorization"]?.firstOrNull())
            HttpStatusCode.OK to """
                {
                    "value": "{\"client_id\":\"azure_client_999\",\"client_secret\":\"sk_azure_secret_888\"}",
                    "id": "https://my-vault.vault.azure.net/secrets/payment-foo/v1",
                    "contentType": "application/json"
                }
            """.trimIndent()
        }

        val config = AzureKeyVaultConfig(
            vaultName = "my-vault",
            secretName = "payment-foo",
            tenantId = "test-tenant",
            clientId = "test-client",
            clientSecret = "test-secret",
            authorityHost = "bypass",
        )

        val secrets = client.fetchSecret(config)
        assertNotNull(secrets)
        assertEquals(2, secrets.size)
        assertEquals("azure_client_999", secrets["client_id"])
        assertEquals("sk_azure_secret_888", secrets["client_secret"])
    }

    @Test
    fun fetchSecret_with_propertyKey_filters_to_single_key() = runTest {
        val client = createMockClient { _, _ ->
            HttpStatusCode.OK to """
                {
                    "value": "{\"client_id\":\"azure_client_999\",\"client_secret\":\"sk_azure_secret_888\"}",
                    "contentType": "application/json"
                }
            """.trimIndent()
        }

        val config = AzureKeyVaultConfig(
            vaultName = "https://localhost:8443",
            secretName = "payment-foo",
            propertyKey = "client_secret",
            tenantId = "test-tenant",
            clientId = "test-client",
            clientSecret = "test-secret",
            authorityHost = "bypass",
        )

        val secrets = client.fetchSecret(config)
        assertNotNull(secrets)
        assertEquals(1, secrets.size)
        assertEquals("sk_azure_secret_888", secrets["client_secret"])
    }

    @Test
    fun fetchSecret_fetches_plain_text_secret_under_secret_key() = runTest {
        val client = createMockClient { _, _ ->
            HttpStatusCode.OK to """
                {
                    "value": "plain-secret-value-123",
                    "contentType": "text/plain"
                }
            """.trimIndent()
        }

        val config = AzureKeyVaultConfig(
            vaultName = "my-vault",
            secretName = "plain-token",
            tenantId = "test-tenant",
            clientId = "test-client",
            clientSecret = "test-secret",
            authorityHost = "bypass",
        )

        val secrets = client.fetchSecret(config)
        assertNotNull(secrets)
        assertEquals(1, secrets.size)
        assertEquals("plain-secret-value-123", secrets["secret"])
    }

    @Test
    fun fetchSecret_acquires_token_when_authorityHost_not_bypass() = runTest {
        val client = createMockClient { url, headers ->
            if (url.contains("/oauth2/v2.0/token")) {
                HttpStatusCode.OK to """
                    {
                        "access_token": "live-entra-token-777",
                        "token_type": "Bearer",
                        "expires_in": 3600
                    }
                """.trimIndent()
            } else {
                assertEquals("Bearer live-entra-token-777", headers["Authorization"]?.firstOrNull())
                HttpStatusCode.OK to """
                    {
                        "value": "{\"api_key\":\"live_key_999\"}",
                        "contentType": "application/json"
                    }
                """.trimIndent()
            }
        }

        val config = AzureKeyVaultConfig(
            vaultName = "prod-vault",
            secretName = "api-secret",
            tenantId = "entra-tenant-uuid",
            clientId = "entra-client-id",
            clientSecret = "entra-client-secret",
            authorityHost = "https://login.microsoftonline.com",
        )

        val secrets = client.fetchSecret(config)
        assertEquals("live_key_999", secrets["api_key"])
    }

    @Test
    fun fetchSecret_throws_on_404_not_found() = runTest {
        val client = createMockClient { _, _ ->
            HttpStatusCode.NotFound to """
                {
                    "error": {
                        "code": "SecretNotFound",
                        "message": "Secret not found: missing-secret"
                    }
                }
            """.trimIndent()
        }

        val config = AzureKeyVaultConfig(
            vaultName = "my-vault",
            secretName = "missing-secret",
            tenantId = "test-tenant",
            clientId = "test-client",
            clientSecret = "test-secret",
            authorityHost = "bypass",
        )

        val ex = assertFailsWith<IllegalStateException> {
            client.fetchSecret(config)
        }
        assertTrue(ex.message!!.contains("External secret not found in Azure Key Vault"))
    }

    @Test
    fun fetchSecret_throws_on_missing_propertyKey() = runTest {
        val client = createMockClient { _, _ ->
            HttpStatusCode.OK to """
                {
                    "value": "{\"client_id\":\"id_only\"}",
                    "contentType": "application/json"
                }
            """.trimIndent()
        }

        val config = AzureKeyVaultConfig(
            vaultName = "my-vault",
            secretName = "single-key-secret",
            propertyKey = "missing_field",
            tenantId = "test-tenant",
            clientId = "test-client",
            clientSecret = "test-secret",
            authorityHost = "bypass",
        )

        val ex = assertFailsWith<IllegalStateException> {
            client.fetchSecret(config)
        }
        assertTrue(ex.message!!.contains("Field 'missing_field' not found in Azure secret"))
    }
}
