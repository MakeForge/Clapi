package io.github.makeforge.clapi.qa

import io.github.makeforge.clapi.core.model.AuthConfig
import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.core.model.RequestDefinition
import io.github.makeforge.clapi.core.model.opencollection.OAuth2CredentialsPlacement
import io.github.makeforge.clapi.core.model.opencollection.OAuth2Flow
import io.github.makeforge.clapi.core.network.KtorApiClient
import io.github.makeforge.clapi.core.network.NetworkEvent
import io.github.makeforge.clapi.core.network.RetryPolicy
import io.github.makeforge.clapi.core.network.oauth2.KtorOAuth2TokenClient
import io.github.makeforge.clapi.core.network.oauth2.OAuth2TokenState
import io.github.makeforge.clapi.core.network.oauth2.extractJwtExpiryEpochMs
import io.github.makeforge.clapi.ui.shared.components.executor.buildAuthConfig
import io.github.makeforge.clapi.ui.shared.components.executor.ensureValidOAuth2Token
import io.github.makeforge.clapi.ui.shared.platform.currentTimeMillis
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.github.makeforge.clapi.testsupport.CiTestGuard
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OAuth2IntegrationTest {

    companion object {
        private const val KEYCLOAK_HOST = "127.0.0.1"
        private const val KEYCLOAK_PORT = 8080
        private const val TOKEN_URL = "http://localhost:8080/realms/test-realm/protocol/openid-connect/token"
        private const val USERINFO_URL = "http://localhost:8080/realms/test-realm/protocol/openid-connect/userinfo"
    }

    private fun isKeycloakAvailable(): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(KEYCLOAK_HOST, KEYCLOAK_PORT), 1500)
                true
            }
        }.getOrDefault(false)
    }

    @Before
    fun checkKeycloakUp() {
        CiTestGuard.requireServiceOrSkip(
            serviceName = "Keycloak OAuth2",
            endpoint = "$KEYCLOAK_HOST:$KEYCLOAK_PORT",
            isReachable = isKeycloakAvailable(),
            localHint = "qa-tests/docker/keycloak-oauth2-compose.yml",
        )
    }

    @Test
    fun client_credentials_grant_fetches_valid_access_token() = runBlocking {
        val client = KtorOAuth2TokenClient()
        val result = client.fetchClientCredentialsToken(
            tokenUrl = TOKEN_URL,
            clientId = "client-credentials-client",
            clientSecret = "client-credentials-secret",
            placement = OAuth2CredentialsPlacement.BASIC_AUTH_HEADER,
            scope = "email profile",
        )

        assertTrue(result.isSuccess, "Client Credentials grant should succeed: ${result.exceptionOrNull()?.message}")
        val tokenResponse = result.getOrThrow()
        assertTrue(tokenResponse.accessToken.isNotBlank(), "Access token must not be blank")
        assertEquals("Bearer", tokenResponse.tokenType)
        assertTrue(tokenResponse.expiresIn != null && tokenResponse.expiresIn!! > 0L)

        val tokenState = OAuth2TokenState.fromResponse(tokenResponse)
        assertTrue(tokenState.isValid(), "Token state should be valid upon retrieval")
    }

    @Test
    fun resource_owner_password_credentials_grant_fetches_tokens_and_extracts_jwt_expiry() = runBlocking {
        val client = KtorOAuth2TokenClient()
        val result = client.fetchPasswordToken(
            tokenUrl = TOKEN_URL,
            clientId = "password-flow-client",
            clientSecret = "password-flow-secret",
            placement = OAuth2CredentialsPlacement.BODY,
            username = "testuser",
            password = "password123",
            scope = "openid email profile",
        )

        assertTrue(result.isSuccess, "Password grant should succeed: ${result.exceptionOrNull()?.message}")
        val tokenResponse = result.getOrThrow()
        assertTrue(tokenResponse.accessToken.isNotBlank(), "Access token must not be blank")
        assertNotNull(tokenResponse.refreshToken, "Refresh token should be present for password flow")
        assertNotNull(tokenResponse.idToken, "ID token should be present when openid scope requested")

        val jwtExpiryMs = extractJwtExpiryEpochMs(tokenResponse.accessToken)
        assertNotNull(jwtExpiryMs, "JWT access token should contain valid 'exp' claim")
        assertTrue(jwtExpiryMs > currentTimeMillis(), "JWT expiration timestamp should be in the future")
    }

    @Test
    fun refresh_token_grant_exchanges_refresh_token_for_new_access_token() = runBlocking {
        val client = KtorOAuth2TokenClient()
        val initialResult = client.fetchPasswordToken(
            tokenUrl = TOKEN_URL,
            clientId = "password-flow-client",
            clientSecret = "password-flow-secret",
            placement = OAuth2CredentialsPlacement.BASIC_AUTH_HEADER,
            username = "testuser",
            password = "password123",
            scope = "openid",
        )
        assertTrue(initialResult.isSuccess)
        val initialRefreshToken = initialResult.getOrThrow().refreshToken
        assertNotNull(initialRefreshToken)

        val refreshResult = client.refreshToken(
            tokenUrl = TOKEN_URL,
            refreshToken = initialRefreshToken,
            clientId = "password-flow-client",
            clientSecret = "password-flow-secret",
            placement = OAuth2CredentialsPlacement.BASIC_AUTH_HEADER,
            scope = "openid",
        )

        assertTrue(refreshResult.isSuccess, "Refresh token grant should succeed: ${refreshResult.exceptionOrNull()?.message}")
        val refreshedToken = refreshResult.getOrThrow()
        assertTrue(refreshedToken.accessToken.isNotBlank())
        assertEquals("Bearer", refreshedToken.tokenType)
    }

    @Test
    fun access_protected_userinfo_endpoint_with_bearer_token() = runBlocking {
        val tokenClient = KtorOAuth2TokenClient()
        val tokenResult = tokenClient.fetchPasswordToken(
            tokenUrl = TOKEN_URL,
            clientId = "password-flow-client",
            clientSecret = "password-flow-secret",
            username = "testuser",
            password = "password123",
            scope = "openid email profile",
        )
        val accessToken = tokenResult.getOrThrow().accessToken

        val apiClient = KtorApiClient(
            httpClient = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
            },
            retryPolicy = RetryPolicy(maxAttempts = 1),
        )

        val request = RequestDefinition(
            id = "userinfo-test",
            name = "Keycloak UserInfo",
            method = HttpMethodType.GET,
            url = USERINFO_URL,
            auth = AuthConfig(
                type = AuthType.OAUTH2,
                params = mapOf("accessToken" to accessToken),
            ),
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )

        val events = apiClient.execute(request, emptyList())
        val successEvent = events.filterIsInstance<NetworkEvent.Success>().first()
        assertEquals(200, successEvent.response.statusCode)

        val bodyText = successEvent.response.bodyText
        val json = Json.parseToJsonElement(bodyText).jsonObject
        assertEquals("testuser", json["preferred_username"]?.jsonPrimitive?.content)
        assertEquals("testuser@example.com", json["email"]?.jsonPrimitive?.content)
    }

    @Test
    fun e2e_request_execution_auto_acquires_and_refreshes_token() = runBlocking {
        val state = AppState()
        val tab = RequestTabState(
            id = "tab-e2e-oauth2",
            name = "Auto OAuth2 Request",
            url = USERINFO_URL,
        )
        tab.authType = AuthType.OAUTH2
        tab.authOAuth2Flow = OAuth2Flow.RESOURCE_OWNER_PASSWORD_CREDENTIALS
        tab.authOAuth2AccessTokenUrl = TOKEN_URL
        tab.authOAuth2ClientId = "password-flow-client"
        tab.authOAuth2ClientSecret = "password-flow-secret"
        tab.authOAuth2Username = "testuser"
        tab.authOAuth2Password = "password123"
        tab.authOAuth2Scope = "openid email profile"
        // Initially no access token
        tab.authOAuth2AccessToken = ""

        // 1. Pre-execution token check: must automatically acquire token
        val acquired = ensureValidOAuth2Token(
            state = state,
            tab = tab,
            variableLayers = emptyList(),
            tokenClient = KtorOAuth2TokenClient(),
        )
        assertTrue(acquired, "ensureValidOAuth2Token must succeed")
        assertTrue(tab.authOAuth2AccessToken.isNotBlank(), "Access token should now be populated")
        assertTrue(tab.authOAuth2RefreshToken.isNotBlank(), "Refresh token should now be populated")

        val initialAccessToken = tab.authOAuth2AccessToken

        // 2. Simulate token expiration
        tab.authOAuth2AccessToken = "expired_access_token_value"
        tab.authOAuth2ExpiresAtEpochMs = currentTimeMillis() - 10_000L // 10s in past

        // 3. Pre-execution check again: must auto-refresh via refresh token
        val refreshed = ensureValidOAuth2Token(
            state = state,
            tab = tab,
            variableLayers = emptyList(),
            tokenClient = KtorOAuth2TokenClient(),
        )
        assertTrue(refreshed, "ensureValidOAuth2Token must successfully refresh expired token")
        assertTrue(tab.authOAuth2AccessToken.isNotBlank())
        assertTrue(tab.authOAuth2AccessToken != "expired_access_token_value")

        // 4. Build auth config and execute against Keycloak userinfo endpoint
        val authConfig = buildAuthConfig(tab, state)
        assertEquals(AuthType.OAUTH2, authConfig.type)
        assertEquals(tab.authOAuth2AccessToken, authConfig.params["accessToken"])

        val apiClient = KtorApiClient(
            httpClient = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
            },
            retryPolicy = RetryPolicy(maxAttempts = 1),
        )

        val request = RequestDefinition(
            id = "auto-e2e-userinfo",
            name = "Auto E2E Keycloak UserInfo",
            method = HttpMethodType.GET,
            url = USERINFO_URL,
            auth = authConfig,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )

        val events = apiClient.execute(request, emptyList())
        val successEvent = events.filterIsInstance<NetworkEvent.Success>().first()
        assertEquals(200, successEvent.response.statusCode)

        val json = Json.parseToJsonElement(successEvent.response.bodyText).jsonObject
        assertEquals("testuser", json["preferred_username"]?.jsonPrimitive?.content)
    }
}
