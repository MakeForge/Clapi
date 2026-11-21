package io.github.crustack.clapi.core.network.vault

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HashiCorpVaultClientTest {

    private fun createClient(engine: MockEngine): HashiCorpVaultClient {
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
        return KtorHashiCorpVaultClient(httpClient)
    }

    @Test
    fun fetchSecret_success_returns_key_value_map() = runTest {
        var capturedUrl = ""
        var capturedToken = ""
        var capturedNamespace: String? = null

        val responseJson = """
            {
              "request_id": "c138f6d2-0000-0000-0000-000000000000",
              "data": {
                "data": {
                  "client_id": "foo_client_123",
                  "client_secret": "sk_live_secret_456"
                },
                "metadata": {
                  "created_time": "2026-11-08T22:00:00Z",
                  "version": 1
                }
              }
            }
        """.trimIndent()

        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            capturedToken = request.headers["X-Vault-Token"] ?: ""
            capturedNamespace = request.headers["X-Vault-Namespace"]
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = createClient(engine)
        val result = client.fetchSecret(
            vaultUrl = "http://127.0.0.1:8200/",
            token = "test-vault-token",
            secretPath = "/payment/foo",
            mountPath = "/secret/",
            namespace = "team-a",
        )

        assertEquals("http://127.0.0.1:8200/v1/secret/data/payment/foo", capturedUrl)
        assertEquals("test-vault-token", capturedToken)
        assertEquals("team-a", capturedNamespace)
        assertEquals("foo_client_123", result["client_id"])
        assertEquals("sk_live_secret_456", result["client_secret"])
    }

    @Test
    fun fetchSecret_omits_namespace_header_when_null_or_blank() = runTest {
        var hasNamespaceHeader = false
        val engine = MockEngine { request ->
            hasNamespaceHeader = request.headers.contains("X-Vault-Namespace")
            respond(
                content = """{"data":{"data":{"key":"val"}}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = createClient(engine)
        client.fetchSecret(
            vaultUrl = "http://127.0.0.1:8200",
            token = "token",
            secretPath = "app/config",
            namespace = "",
        )

        assertTrue(!hasNamespaceHeader)
    }

    @Test
    fun fetchSecret_permission_denied_throws_illegal_state_exception() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"errors":["permission denied"]}""",
                status = HttpStatusCode.Forbidden,
            )
        }

        val client = createClient(engine)
        val ex = assertFailsWith<IllegalStateException> {
            client.fetchSecret("http://127.0.0.1:8200", "bad-token", "payment/foo")
        }
        assertTrue(ex.message?.contains("permission denied") == true)
        assertTrue(ex.message?.contains("403") == true)
    }

    @Test
    fun fetchSecret_not_found_throws_illegal_state_exception() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"errors":[]}""",
                status = HttpStatusCode.NotFound,
            )
        }

        val client = createClient(engine)
        val ex = assertFailsWith<IllegalStateException> {
            client.fetchSecret("http://127.0.0.1:8200", "token", "missing/secret", mountPath = "secret")
        }
        assertTrue(ex.message?.contains("Vault secret not found [HTTP 404]") == true)
        assertTrue(ex.message?.contains("secret/data/missing/secret") == true)
    }
}
