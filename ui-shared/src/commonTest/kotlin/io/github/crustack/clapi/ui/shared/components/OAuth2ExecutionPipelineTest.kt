package io.github.crustack.clapi.ui.shared.components

import io.github.crustack.clapi.core.model.AuthType
import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.core.model.opencollection.OAuth2CredentialsPlacement
import io.github.crustack.clapi.core.model.opencollection.OAuth2Flow
import io.github.crustack.clapi.core.network.oauth2.OAuth2Exception
import io.github.crustack.clapi.core.network.oauth2.OAuth2TokenClient
import io.github.crustack.clapi.core.network.oauth2.OAuth2TokenResponse
import io.github.crustack.clapi.ui.shared.platform.currentTimeMillis
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.CollectionNode
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OAuth2ExecutionPipelineTest {

    private class FakeOAuth2TokenClient : OAuth2TokenClient {
        var clientCredentialsCallCount = 0
        var passwordCallCount = 0
        var refreshCallCount = 0

        var nextClientCredentialsResult: Result<OAuth2TokenResponse>? = null
        var nextPasswordResult: Result<OAuth2TokenResponse>? = null
        var nextRefreshResult: Result<OAuth2TokenResponse>? = null

        override suspend fun fetchClientCredentialsToken(
            tokenUrl: String,
            clientId: String,
            clientSecret: String,
            placement: OAuth2CredentialsPlacement,
            scope: String?
        ): Result<OAuth2TokenResponse> {
            clientCredentialsCallCount++
            return nextClientCredentialsResult ?: error("nextClientCredentialsResult not set")
        }

        override suspend fun fetchPasswordToken(
            tokenUrl: String,
            clientId: String,
            clientSecret: String,
            placement: OAuth2CredentialsPlacement,
            username: String,
            password: String,
            scope: String?
        ): Result<OAuth2TokenResponse> {
            passwordCallCount++
            return nextPasswordResult ?: error("nextPasswordResult not set")
        }

        override suspend fun refreshToken(
            tokenUrl: String,
            refreshToken: String,
            clientId: String?,
            clientSecret: String?,
            placement: OAuth2CredentialsPlacement,
            scope: String?
        ): Result<OAuth2TokenResponse> {
            refreshCallCount++
            return nextRefreshResult ?: error("nextRefreshResult not set")
        }
    }

    @Test
    fun valid_token_is_reused_without_fetching() = runBlocking {
        val state = AppState()
        val tab = RequestTabState(id = "tab1", name = "Test Request", url = "https://api.example.com/data")
        tab.authType = AuthType.OAUTH2
        tab.authOAuth2AccessToken = "existing-valid-token"
        tab.authOAuth2ExpiresAtEpochMs = currentTimeMillis() + 60_000L // 60s in future (> 30s buffer)
        tab.authOAuth2AccessTokenUrl = "https://auth.example.com/token"

        val fakeClient = FakeOAuth2TokenClient()

        val valid = ensureValidOAuth2Token(
            state = state,
            tab = tab,
            variableLayers = emptyList(),
            tokenClient = fakeClient,
        )

        assertTrue(valid)
        assertEquals(0, fakeClient.clientCredentialsCallCount)
        assertEquals(0, fakeClient.refreshCallCount)

        val authConfig = buildAuthConfig(tab, state)
        assertEquals(AuthType.OAUTH2, authConfig.type)
        assertEquals("existing-valid-token", authConfig.params["accessToken"])
    }

    @Test
    fun expired_token_triggers_auto_refresh_when_refresh_token_available() = runBlocking {
        val state = AppState()
        val tab = RequestTabState(id = "tab2", name = "Test Request", url = "https://api.example.com/data")
        tab.authType = AuthType.OAUTH2
        tab.authOAuth2AccessToken = "expired-token"
        tab.authOAuth2ExpiresAtEpochMs = currentTimeMillis() - 5000L // expired
        tab.authOAuth2RefreshToken = "active-refresh-token"
        tab.authOAuth2AccessTokenUrl = "https://auth.example.com/token"

        val fakeClient = FakeOAuth2TokenClient()
        fakeClient.nextRefreshResult = Result.success(
            OAuth2TokenResponse(
                accessToken = "newly-refreshed-access-token",
                refreshToken = "rotated-refresh-token",
                expiresIn = 3600,
            )
        )

        val valid = ensureValidOAuth2Token(
            state = state,
            tab = tab,
            variableLayers = emptyList(),
            tokenClient = fakeClient,
        )

        assertTrue(valid)
        assertEquals(1, fakeClient.refreshCallCount)
        assertEquals(0, fakeClient.clientCredentialsCallCount)
        assertEquals("newly-refreshed-access-token", tab.authOAuth2AccessToken)
        assertEquals("rotated-refresh-token", tab.authOAuth2RefreshToken)
        assertNotNull(tab.authOAuth2ExpiresAtEpochMs)

        val authConfig = buildAuthConfig(tab, state)
        assertEquals("newly-refreshed-access-token", authConfig.params["accessToken"])
    }

    @Test
    fun refresh_failure_falls_back_to_primary_grant() = runBlocking {
        val state = AppState()
        val tab = RequestTabState(id = "tab3", name = "Test Request", url = "https://api.example.com/data")
        tab.authType = AuthType.OAUTH2
        tab.authOAuth2Flow = OAuth2Flow.CLIENT_CREDENTIALS
        tab.authOAuth2AccessToken = "stale-token"
        tab.authOAuth2ExpiresAtEpochMs = currentTimeMillis() - 1000L
        tab.authOAuth2RefreshToken = "expired-refresh-token"
        tab.authOAuth2AccessTokenUrl = "https://auth.example.com/token"
        tab.authOAuth2ClientId = "client-id"
        tab.authOAuth2ClientSecret = "client-secret"

        val fakeClient = FakeOAuth2TokenClient()
        fakeClient.nextRefreshResult = Result.failure(OAuth2Exception("invalid_grant", "refresh token expired", 400))
        fakeClient.nextClientCredentialsResult = Result.success(
            OAuth2TokenResponse(
                accessToken = "fallback-cc-token",
                expiresIn = 1800,
            )
        )

        val valid = ensureValidOAuth2Token(
            state = state,
            tab = tab,
            variableLayers = emptyList(),
            tokenClient = fakeClient,
        )

        assertTrue(valid)
        assertEquals(1, fakeClient.refreshCallCount)
        assertEquals(1, fakeClient.clientCredentialsCallCount)
        assertEquals("fallback-cc-token", tab.authOAuth2AccessToken)
    }

    @Test
    fun missing_token_triggers_client_credentials_grant() = runBlocking {
        val state = AppState()
        val tab = RequestTabState(id = "tab4", name = "Test Request", url = "https://api.example.com/data")
        tab.authType = AuthType.OAUTH2
        tab.authOAuth2Flow = OAuth2Flow.CLIENT_CREDENTIALS
        tab.authOAuth2AccessToken = ""
        tab.authOAuth2AccessTokenUrl = "https://auth.example.com/token"
        tab.authOAuth2ClientId = "my-client"
        tab.authOAuth2ClientSecret = "my-secret"

        val fakeClient = FakeOAuth2TokenClient()
        fakeClient.nextClientCredentialsResult = Result.success(
            OAuth2TokenResponse(
                accessToken = "acquired-cc-token",
                expiresIn = 300,
            )
        )

        val valid = ensureValidOAuth2Token(
            state = state,
            tab = tab,
            variableLayers = emptyList(),
            tokenClient = fakeClient,
        )

        assertTrue(valid)
        assertEquals(1, fakeClient.clientCredentialsCallCount)
        assertEquals("acquired-cc-token", tab.authOAuth2AccessToken)
    }

    @Test
    fun missing_token_triggers_password_grant() = runBlocking {
        val state = AppState()
        val tab = RequestTabState(id = "tab5", name = "Test Request", url = "https://api.example.com/data")
        tab.authType = AuthType.OAUTH2
        tab.authOAuth2Flow = OAuth2Flow.RESOURCE_OWNER_PASSWORD_CREDENTIALS
        tab.authOAuth2AccessToken = ""
        tab.authOAuth2AccessTokenUrl = "https://auth.example.com/token"
        tab.authOAuth2ClientId = "pwd-client"
        tab.authOAuth2ClientSecret = "pwd-secret"
        tab.authOAuth2Username = "user"
        tab.authOAuth2Password = "pass"

        val fakeClient = FakeOAuth2TokenClient()
        fakeClient.nextPasswordResult = Result.success(
            OAuth2TokenResponse(
                accessToken = "acquired-pwd-token",
                refreshToken = "acquired-pwd-rt",
                expiresIn = 300,
            )
        )

        val valid = ensureValidOAuth2Token(
            state = state,
            tab = tab,
            variableLayers = emptyList(),
            tokenClient = fakeClient,
        )

        assertTrue(valid)
        assertEquals(1, fakeClient.passwordCallCount)
        assertEquals("acquired-pwd-token", tab.authOAuth2AccessToken)
        assertEquals("acquired-pwd-rt", tab.authOAuth2RefreshToken)
    }

    @Test
    fun token_fetch_failure_halts_with_descriptive_error() = runBlocking {
        val state = AppState()
        val tab = RequestTabState(id = "tab6", name = "Test Request", url = "https://api.example.com/data")
        tab.authType = AuthType.OAUTH2
        tab.authOAuth2Flow = OAuth2Flow.CLIENT_CREDENTIALS
        tab.authOAuth2AccessToken = ""
        tab.authOAuth2AccessTokenUrl = "https://auth.example.com/token"
        tab.authOAuth2ClientId = "bad-client"

        val fakeClient = FakeOAuth2TokenClient()
        fakeClient.nextClientCredentialsResult = Result.failure(OAuth2Exception("invalid_client", "Unauthorized", 401))

        val valid = ensureValidOAuth2Token(
            state = state,
            tab = tab,
            variableLayers = emptyList(),
            tokenClient = fakeClient,
        )

        assertFalse(valid)
        assertNotNull(tab.lastError)
        assertTrue(tab.lastError!!.contains("Unauthorized"))
        assertEquals(tab.lastError, tab.authOAuth2FetchError)
    }

    @Test
    fun inherited_oauth2_auth_is_properly_validated_and_built() = runBlocking {
        val state = AppState()
        val folder = CollectionNode(
            id = "parent-folder",
            name = "Parent Folder",
            isFolder = true,
            authType = AuthType.OAUTH2,
            authOAuth2Flow = OAuth2Flow.CLIENT_CREDENTIALS,
            authOAuth2AccessTokenUrl = "https://auth.example.com/token",
            authOAuth2ClientId = "inherited-client",
            authOAuth2ClientSecret = "inherited-secret",
        )
        val childNode = CollectionNode(
            id = "child-tab",
            name = "Child Request",
            isFolder = false,
            method = HttpMethodType.GET,
            url = "https://api.example.com/child",
            authType = AuthType.INHERIT,
        )
        folder.children.add(childNode)
        state.collections.add(folder)

        val tab = RequestTabState(
            id = "child-tab",
            name = "Child Request",
            url = "https://api.example.com/child",
            requestRef = "child-req",
        )
        tab.authType = AuthType.INHERIT

        val fakeClient = FakeOAuth2TokenClient()
        fakeClient.nextClientCredentialsResult = Result.success(
            OAuth2TokenResponse(accessToken = "inherited-acquired-token", expiresIn = 3600)
        )

        val valid = ensureValidOAuth2Token(
            state = state,
            tab = tab,
            variableLayers = emptyList(),
            tokenClient = fakeClient,
        )

        assertTrue(valid)
        assertEquals("inherited-acquired-token", tab.authOAuth2AccessToken)

        val authConfig = buildAuthConfig(tab, state)
        assertEquals(AuthType.OAUTH2, authConfig.type)
        assertEquals("inherited-acquired-token", authConfig.params["accessToken"])
        assertEquals("inherited-client", authConfig.params["clientId"])
    }
}
