package io.github.makeforge.clapi.core.network.oauth2

import io.github.makeforge.clapi.core.model.opencollection.OAuth2CredentialsPlacement
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OAuth2TokenClientTest {

    @Test
    fun testClientCredentialsFlow_withBasicAuthPlacement() = runTest {
        var capturedAuthHeader: String? = null
        var capturedBody: String? = null

        val engine = MockEngine { request ->
            capturedAuthHeader = request.headers[HttpHeaders.Authorization]
            capturedBody = request.body.toByteArray().decodeToString()

            respond(
                content = """{"access_token":"mock-access-token","token_type":"Bearer","expires_in":3600,"scope":"read"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = KtorOAuth2TokenClient(HttpClient(engine))
        val result = client.fetchClientCredentialsToken(
            tokenUrl = "https://auth.example.com/oauth/token",
            clientId = "my-client",
            clientSecret = "my-secret",
            placement = OAuth2CredentialsPlacement.BASIC_AUTH_HEADER,
            scope = "read",
        )

        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertEquals("mock-access-token", response.accessToken)
        assertEquals(3600L, response.expiresIn)
        assertNotNull(capturedAuthHeader)
        assertTrue(capturedAuthHeader!!.startsWith("Basic "))
        assertNotNull(capturedBody)
        assertTrue(capturedBody!!.contains("grant_type=client_credentials"))
        assertTrue(capturedBody!!.contains("scope=read"))
        assertFalse(capturedBody!!.contains("client_id"))
    }

    @Test
    fun testClientCredentialsFlow_withBodyPlacement() = runTest {
        var capturedAuthHeader: String? = null
        var capturedBody: String? = null

        val engine = MockEngine { request ->
            capturedAuthHeader = request.headers[HttpHeaders.Authorization]
            capturedBody = request.body.toByteArray().decodeToString()

            respond(
                content = """{"access_token":"body-access-token","token_type":"Bearer","expires_in":1800}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = KtorOAuth2TokenClient(HttpClient(engine))
        val result = client.fetchClientCredentialsToken(
            tokenUrl = "https://auth.example.com/oauth/token",
            clientId = "my-client",
            clientSecret = "my-secret",
            placement = OAuth2CredentialsPlacement.BODY,
        )

        assertTrue(result.isSuccess)
        assertEquals("body-access-token", result.getOrThrow().accessToken)
        assertEquals(null, capturedAuthHeader)
        assertNotNull(capturedBody)
        assertTrue(capturedBody!!.contains("client_id=my-client"))
        assertTrue(capturedBody!!.contains("client_secret=my-secret"))
        assertTrue(capturedBody!!.contains("grant_type=client_credentials"))
    }

    @Test
    fun testPasswordFlow_success() = runTest {
        var capturedBody: String? = null

        val engine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()

            respond(
                content = """{
                    "access_token": "pw-access-token",
                    "refresh_token": "pw-refresh-token",
                    "id_token": "pw-id-token",
                    "expires_in": 300,
                    "token_type": "Bearer"
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = KtorOAuth2TokenClient(HttpClient(engine))
        val result = client.fetchPasswordToken(
            tokenUrl = "https://auth.example.com/oauth/token",
            clientId = "my-app",
            clientSecret = "app-secret",
            placement = OAuth2CredentialsPlacement.BASIC_AUTH_HEADER,
            username = "alice",
            password = "secretpassword",
            scope = "openid profile",
        )

        assertTrue(result.isSuccess)
        val token = result.getOrThrow()
        assertEquals("pw-access-token", token.accessToken)
        assertEquals("pw-refresh-token", token.refreshToken)
        assertEquals("pw-id-token", token.idToken)
        assertNotNull(capturedBody)
        assertTrue(capturedBody!!.contains("grant_type=password"))
        assertTrue(capturedBody!!.contains("username=alice"))
        assertTrue(capturedBody!!.contains("password=secretpassword"))
        assertTrue(capturedBody!!.contains("scope=openid+profile") || capturedBody!!.contains("scope=openid%20profile"))
    }

    @Test
    fun testRefreshTokenFlow_success() = runTest {
        var capturedBody: String? = null

        val engine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()

            respond(
                content = """{"access_token":"new-access-token","refresh_token":"new-refresh-token","expires_in":600}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = KtorOAuth2TokenClient(HttpClient(engine))
        val result = client.refreshToken(
            tokenUrl = "https://auth.example.com/oauth/token",
            refreshToken = "existing-refresh-token",
            clientId = "my-client",
            clientSecret = "my-secret",
            placement = OAuth2CredentialsPlacement.BASIC_AUTH_HEADER,
        )

        assertTrue(result.isSuccess)
        val token = result.getOrThrow()
        assertEquals("new-access-token", token.accessToken)
        assertEquals("new-refresh-token", token.refreshToken)
        assertNotNull(capturedBody)
        assertTrue(capturedBody!!.contains("grant_type=refresh_token"))
        assertTrue(capturedBody!!.contains("refresh_token=existing-refresh-token"))
    }

    @Test
    fun testErrorResponse_throwsOAuth2Exception() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":"invalid_client","error_description":"Client authentication failed"}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = KtorOAuth2TokenClient(HttpClient(engine))
        val result = client.fetchClientCredentialsToken(
            tokenUrl = "https://auth.example.com/oauth/token",
            clientId = "wrong-client",
            clientSecret = "wrong-secret",
        )

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as? OAuth2Exception
        assertNotNull(ex)
        assertEquals("invalid_client", ex.error)
        assertEquals("Client authentication failed", ex.errorDescription)
        assertEquals(401, ex.statusCode)
    }

    @Test
    fun testOAuth2TokenState_isValidCalculations() {
        val now = 1_000_000L
        val unexpiredState = OAuth2TokenState(
            accessToken = "token123",
            expiresAtEpochMs = now + 60_000L, // 60s in future
        )
        // With 30s buffer, 60s in future is valid
        assertTrue(unexpiredState.isValid(bufferMs = 30_000L, nowEpochMs = now))

        val expiringSoonState = OAuth2TokenState(
            accessToken = "token123",
            expiresAtEpochMs = now + 20_000L, // 20s in future
        )
        // With 30s buffer, 20s in future is considered expired/invalid
        assertFalse(expiringSoonState.isValid(bufferMs = 30_000L, nowEpochMs = now))

        val expiredState = OAuth2TokenState(
            accessToken = "token123",
            expiresAtEpochMs = now - 5_000L, // 5s in past
        )
        assertFalse(expiredState.isValid(bufferMs = 30_000L, nowEpochMs = now))

        val emptyTokenState = OAuth2TokenState(
            accessToken = "",
            expiresAtEpochMs = now + 60_000L,
        )
        assertFalse(emptyTokenState.isValid(nowEpochMs = now))
    }

    @Test
    fun testExtractJwtExpiryEpochMs() {
        // Payload: {"sub":"123","name":"Test","exp":1893456000} (2030-01-01)
        // base64url of {"sub":"123","name":"Test","exp":1893456000} -> eyJzdWIiOiIxMjMiLCJuYW1lIjoiVGVzdCIsImV4cCI6MTg5MzQ1NjAwMH0
        val mockJwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMiLCJuYW1lIjoiVGVzdCIsImV4cCI6MTg5MzQ1NjAwMH0.mockSig"
        val expiry = extractJwtExpiryEpochMs(mockJwt)
        assertEquals(1893456000_000L, expiry)

        val nonJwt = "opaque-token-string"
        assertEquals(null, extractJwtExpiryEpochMs(nonJwt))
    }
}
