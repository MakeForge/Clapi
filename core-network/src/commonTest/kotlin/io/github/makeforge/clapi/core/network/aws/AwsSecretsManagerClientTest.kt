package io.github.makeforge.clapi.core.network.aws

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
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AwsSecretsManagerClientTest {

    private fun createClient(engine: MockEngine): AwsSecretsManagerClient {
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
        return KtorAwsSecretsManagerClient(httpClient)
    }

    @Test
    fun signer_generates_valid_sigv4_headers() {
        val fixedTime = ZonedDateTime.of(2026, 11, 11, 12, 0, 0, 0, ZoneOffset.UTC)
        val headers = mapOf(
            "host" to "secretsmanager.us-east-1.amazonaws.com",
            "x-amz-target" to "secretsmanager.GetSecretValue",
            "content-type" to "application/x-amz-json-1.1",
        )

        val signed = AwsSigV4Signer.sign(
            service = "secretsmanager",
            region = "us-east-1",
            method = "POST",
            path = "/",
            headers = headers,
            payload = "{\"SecretId\":\"local/payment/foo\"}",
            accessKey = "TEST_KEY",
            secretKey = "TEST_SECRET",
            sessionToken = "TEST_SESSION_TOKEN",
            now = fixedTime,
        )

        assertEquals("20261111T120000Z", signed["x-amz-date"])
        assertEquals("TEST_SESSION_TOKEN", signed["x-amz-security-token"])
        val auth = signed["Authorization"]
        assertNotNull(auth)
        assertTrue(auth.startsWith("AWS4-HMAC-SHA256 Credential=TEST_KEY/20261111/us-east-1/secretsmanager/aws4_request"))
        assertTrue(auth.contains("SignedHeaders=content-type;host;x-amz-date;x-amz-security-token;x-amz-target"))
        assertTrue(auth.contains("Signature="))
    }

    @Test
    fun fetchSecret_success_with_json_secret_string() = runTest {
        var capturedTarget = ""
        var capturedAuth = ""

        val responseJson = """
            {
              "ARN": "arn:aws:secretsmanager:us-east-1:123456789012:secret:local/payment/foo-123",
              "Name": "local/payment/foo",
              "SecretString": "{\"client_id\":\"foo_client_123\",\"client_secret\":\"sk_live_secret_456\"}",
              "VersionId": "v1"
            }
        """.trimIndent()

        val engine = MockEngine { request ->
            capturedTarget = request.headers["x-amz-target"].orEmpty()
            capturedAuth = request.headers["Authorization"].orEmpty()
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/x-amz-json-1.1"),
            )
        }

        val client = createClient(engine)
        val result = client.fetchSecret(
            secretId = "local/payment/foo",
            accessKey = "ak",
            secretKey = "sk",
            region = "us-east-1",
            endpointUrl = "http://localhost:4566",
        )

        assertEquals("secretsmanager.GetSecretValue", capturedTarget)
        assertTrue(capturedAuth.startsWith("AWS4-HMAC-SHA256 Credential=ak/"))
        assertEquals("foo_client_123", result["client_id"])
        assertEquals("sk_live_secret_456", result["client_secret"])
    }

    @Test
    fun fetchSecret_success_with_plain_text_secret_string() = runTest {
        val responseJson = """
            {
              "ARN": "arn:aws:secretsmanager:us-east-1:123456789012:secret:raw-token",
              "Name": "raw-token",
              "SecretString": "my-plain-api-key-12345"
            }
        """.trimIndent()

        val engine = MockEngine {
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/x-amz-json-1.1"),
            )
        }

        val client = createClient(engine)
        val result = client.fetchSecret(
            secretId = "raw-token",
            accessKey = "ak",
            secretKey = "sk",
        )

        assertEquals("my-plain-api-key-12345", result["secret"])
    }

    @Test
    fun fetchSecret_with_config_object_delegates_properly() = runTest {
        val responseJson = """
            {
              "Name": "test-secret",
              "SecretString": "{\"token\":\"tok_123\"}"
            }
        """.trimIndent()

        val engine = MockEngine {
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/x-amz-json-1.1"),
            )
        }

        val client = createClient(engine)
        val config = AwsSecretsManagerConfig(
            secretId = "test-secret",
            accessKey = "ak",
            secretKey = "sk",
            region = "eu-west-1",
            endpointUrl = "http://localhost:4566",
        )
        val result = client.fetchSecret(config)
        assertEquals("tok_123", result["token"])
    }

    @Test
    fun fetchSecret_throws_on_resource_not_found() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"__type":"ResourceNotFoundException","Message":"Secret not found"}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/x-amz-json-1.1"),
            )
        }

        val client = createClient(engine)
        val ex = assertFailsWith<IllegalStateException> {
            client.fetchSecret(
                secretId = "missing/secret",
                accessKey = "ak",
                secretKey = "sk",
            )
        }
        assertTrue(ex.message!!.contains("External secret not found in AWS Secrets Manager (Secret ID: 'missing/secret')"))
    }

    @Test
    fun fetchSecret_throws_on_auth_failure() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"__type":"UnrecognizedClientException","Message":"The security token included in the request is invalid"}""",
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, "application/x-amz-json-1.1"),
            )
        }

        val client = createClient(engine)
        val ex = assertFailsWith<IllegalStateException> {
            client.fetchSecret(
                secretId = "secret",
                accessKey = "bad_key",
                secretKey = "bad_secret",
            )
        }
        assertTrue(ex.message!!.contains("AWS authentication failed"))
    }

    @Test
    fun testConnection_success_returns_success_result() = runTest {
        var requestedTarget = ""
        val engine = MockEngine { request ->
            requestedTarget = request.headers["x-amz-target"] ?: ""
            respond(
                content = """{"SecretList":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/x-amz-json-1.1"),
            )
        }

        val client = createClient(engine)
        val result = client.testConnection("AKIA...", "secret-key", region = "us-east-1")
        assertTrue(result.isSuccess)
        assertEquals("Connected to AWS Secrets Manager", result.getOrNull())
        assertEquals("secretsmanager.ListSecrets", requestedTarget)
    }

    @Test
    fun testConnection_auth_failure_returns_failure() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"__type":"UnrecognizedClientException","Message":"The security token is invalid"}""",
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, "application/x-amz-json-1.1"),
            )
        }

        val client = createClient(engine)
        val result = client.testConnection("bad_key", "bad_secret")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("AWS authentication failed") == true)
    }

    @Test
    fun testConnection_blank_credentials_fail_early() = runTest {
        val engine = MockEngine { respond("") }
        val client = createClient(engine)
        assertTrue(client.testConnection("", "sk").isFailure)
        assertTrue(client.testConnection("ak", "").isFailure)
    }
}
