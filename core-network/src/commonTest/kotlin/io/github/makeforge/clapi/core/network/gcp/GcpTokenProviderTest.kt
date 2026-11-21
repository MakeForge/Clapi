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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.*

class GcpTokenProviderTest {

    private fun generateRsaKeyPair(): Pair<String, java.security.PublicKey> {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        val privBytes = kp.private.encoded
        val base64Priv = Base64.getEncoder().encodeToString(privBytes)
        val pem = "-----BEGIN PRIVATE KEY-----\n$base64Priv\n-----END PRIVATE KEY-----"
        return pem to kp.public
    }

    @Test
    fun service_account_jwt_generation_and_token_exchange() = runTest {
        val (privateKeyPem, publicKey) = generateRsaKeyPair()
        val clientEmail = "clapi-test@project-123.iam.gserviceaccount.com"
        val saJson = """
            {
                "type": "service_account",
                "project_id": "project-123",
                "private_key_id": "key-id-456",
                "private_key": "$privateKeyPem",
                "client_email": "$clientEmail",
                "token_uri": "https://oauth2.googleapis.com/token"
            }
        """.trimIndent()

        var postCount = 0
        var capturedAssertion = ""

        val mockEngine = MockEngine { request ->
            if (request.url.toString() == "https://oauth2.googleapis.com/token") {
                postCount++
                val bodyText = (request.body as io.ktor.client.request.forms.FormDataContent)
                val params = bodyText.formData
                assertEquals("urn:ietf:params:oauth:grant-type:jwt-bearer", params["grant_type"])
                capturedAssertion = params["assertion"].orEmpty()

                respond(
                    content = """{"access_token":"ya29.mock-sa-token","expires_in":3600,"token_type":"Bearer"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond("Not Found", HttpStatusCode.NotFound)
            }
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        var currentTime = 1_000_000_000_000L // arbitrary fixed time
        val provider = DefaultGcpTokenProvider(
            httpClient = httpClient,
            timeProvider = { currentTime },
        )

        // 1. Initial token fetch
        val token = provider.getAccessToken(serviceAccountKey = saJson)
        assertEquals("ya29.mock-sa-token", token)
        assertEquals(1, postCount)

        // 2. Verify JWT structure and signature
        val jwtParts = capturedAssertion.split(".")
        assertEquals(3, jwtParts.size)
        val headerJson = Base64.getUrlDecoder().decode(jwtParts[0]).decodeToString()
        val payloadJson = Base64.getUrlDecoder().decode(jwtParts[1]).decodeToString()

        assertTrue(headerJson.contains(""""alg":"RS256""""))
        assertTrue(payloadJson.contains(""""iss":"$clientEmail""""))
        assertTrue(payloadJson.contains(""""scope":"https://www.googleapis.com/auth/cloud-platform""""))
        assertTrue(payloadJson.contains(""""aud":"https://oauth2.googleapis.com/token""""))

        // Verify RSA signature using the public key
        val sigVerifier = Signature.getInstance("SHA256withRSA")
        sigVerifier.initVerify(publicKey)
        sigVerifier.update("${jwtParts[0]}.${jwtParts[1]}".encodeToByteArray())
        val sigBytes = Base64.getUrlDecoder().decode(jwtParts[2])
        assertTrue(sigVerifier.verify(sigBytes), "JWT signature must verify with public key")

        // 3. Test caching (~55 min window)
        // Advance 30 minutes (well before 55 min)
        currentTime += 30 * 60 * 1000L
        val cachedToken = provider.getAccessToken(serviceAccountKey = saJson)
        assertEquals("ya29.mock-sa-token", cachedToken)
        assertEquals(1, postCount, "Cached token should not trigger a second HTTP post")

        // 4. Test auto-refresh when past ~55 minutes
        // Advance another 26 minutes (total 56 minutes elapsed)
        currentTime += 26 * 60 * 1000L
        val refreshedToken = provider.getAccessToken(serviceAccountKey = saJson)
        assertEquals("ya29.mock-sa-token", refreshedToken)
        assertEquals(2, postCount, "Expired token should auto-refresh with new POST")

        // 5. Test forceRefresh
        provider.getAccessToken(serviceAccountKey = saJson, forceRefresh = true)
        assertEquals(3, postCount, "forceRefresh=true must trigger new POST")
    }

    @Test
    fun application_default_credentials_token_exchange_and_caching() = runTest {
        val tempAdcFile = File.createTempFile("adc", ".json")
        try {
            tempAdcFile.writeText(
                """
                {
                    "account": "user@example.com",
                    "client_id": "test-client-id.apps.googleusercontent.com",
                    "client_secret": "test-client-secret",
                    "refresh_token": "1//test-refresh-token",
                    "type": "authorized_user",
                    "universe_domain": "googleapis.com"
                }
                """.trimIndent()
            )

            var postCount = 0
            val mockEngine = MockEngine { request ->
                postCount++
                val formData = (request.body as io.ktor.client.request.forms.FormDataContent).formData
                assertEquals("test-client-id.apps.googleusercontent.com", formData["client_id"])
                assertEquals("test-client-secret", formData["client_secret"])
                assertEquals("1//test-refresh-token", formData["refresh_token"])
                assertEquals("refresh_token", formData["grant_type"])

                respond(
                    content = """{"access_token":"ya29.mock-adc-token","expires_in":3600,"token_type":"Bearer"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }

            val httpClient = HttpClient(mockEngine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }

            val provider = DefaultGcpTokenProvider(httpClient = httpClient)
            val token = provider.getAccessToken(
                authMethod = GcpAuthMethod.APPLICATION_DEFAULT_CREDENTIALS,
                adcFilePath = tempAdcFile.absolutePath,
            )
            assertEquals("ya29.mock-adc-token", token)
            assertEquals(1, postCount)

            // Cache check
            val token2 = provider.getAccessToken(
                authMethod = GcpAuthMethod.APPLICATION_DEFAULT_CREDENTIALS,
                adcFilePath = tempAdcFile.absolutePath,
            )
            assertEquals("ya29.mock-adc-token", token2)
            assertEquals(1, postCount)
        } finally {
            tempAdcFile.delete()
        }
    }

    @Test
    fun extractProjectId_extracts_from_json_and_file() {
        val provider = DefaultGcpTokenProvider()
        val json = """{"project_id": "spherical-voice-999", "type": "service_account"}"""
        assertEquals("spherical-voice-999", provider.extractProjectId(json))

        val temp = File.createTempFile("sa_key", ".json")
        try {
            temp.writeText(json)
            assertEquals("spherical-voice-999", provider.extractProjectId(temp.absolutePath))
        } finally {
            temp.delete()
        }
    }

    @Test
    fun oauth_error_response_throws_descriptive_exception() = runTest {
        val (privateKeyPem, _) = generateRsaKeyPair()
        val saJson = """
            {
                "type": "service_account",
                "project_id": "project-123",
                "private_key": "$privateKeyPem",
                "client_email": "test@project-123.iam.gserviceaccount.com"
            }
        """.trimIndent()

        val mockEngine = MockEngine { _ ->
            respond(
                content = """{"error":"invalid_grant","error_description":"Invalid JWT signature"}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val provider = DefaultGcpTokenProvider(httpClient = httpClient)
        val ex = assertFailsWith<IllegalStateException> {
            provider.getAccessToken(serviceAccountKey = saJson)
        }
        assertTrue(ex.message!!.contains("invalid_grant"))
        assertTrue(ex.message!!.contains("Invalid JWT signature"))
    }
}
