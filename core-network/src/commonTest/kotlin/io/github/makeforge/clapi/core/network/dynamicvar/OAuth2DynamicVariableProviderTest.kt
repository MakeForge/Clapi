package io.github.makeforge.clapi.core.network.dynamicvar

import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.model.opencollection.OAuth2CredentialsPlacement
import io.github.makeforge.clapi.core.model.opencollection.OAuth2Flow
import io.github.makeforge.clapi.core.network.oauth2.OAuth2Exception
import io.github.makeforge.clapi.core.network.oauth2.OAuth2TokenClient
import io.github.makeforge.clapi.core.network.oauth2.OAuth2TokenResponse
import io.github.makeforge.clapi.core.network.oauth2.OAuth2TokenState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OAuth2DynamicVariableProviderTest {

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
            scope: String?,
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
            scope: String?,
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
            scope: String?,
        ): Result<OAuth2TokenResponse> {
            refreshCallCount++
            return nextRefreshResult ?: error("nextRefreshResult not set")
        }
    }

    @Test
    fun supports_only_oauth2_access_token() {
        val provider = OAuth2DynamicVariableProvider()
        assertTrue(provider.supports("_auth.oauth2.accessToken"))
        assertFalse(provider.supports("_auth.oauth2.refreshToken"))
        assertFalse(provider.supports("_secret.token"))
        assertFalse(provider.supports("accessToken"))
    }

    @Test
    fun throws_exception_when_oauth2_not_configured() = runTest {
        val provider = OAuth2DynamicVariableProvider()
        val context = DynamicVariableContext(effectiveAuthType = AuthType.NONE)

        val ex = assertFailsWith<DynamicVariableResolutionException> {
            provider.resolve(setOf("_auth.oauth2.accessToken"), context)
        }
        assertTrue(ex.message!!.contains("OAuth2 authentication"), "Message was: ${ex.message}")
    }

    @Test
    fun returns_cached_token_when_valid() = runTest {
        val provider = OAuth2DynamicVariableProvider()
        val fakeClient = FakeOAuth2TokenClient()
        val validTokenState = OAuth2TokenState(
            accessToken = "cached-valid-token",
            expiresAtEpochMs = io.github.makeforge.clapi.core.network.oauth2.currentEpochMillis() + 60_000L,
        )
        val context = DynamicVariableContext(
            effectiveAuthType = AuthType.OAUTH2,
            oauth2TokenState = validTokenState,
            oauth2TokenClient = fakeClient,
        )

        val result = provider.resolve(setOf("_auth.oauth2.accessToken"), context)

        assertEquals("cached-valid-token", result["_auth.oauth2.accessToken"])
        assertEquals(0, fakeClient.clientCredentialsCallCount)
        assertEquals(0, fakeClient.refreshCallCount)
    }

    @Test
    fun auto_refreshes_token_when_expired_and_refresh_token_available() = runTest {
        val provider = OAuth2DynamicVariableProvider()
        val fakeClient = FakeOAuth2TokenClient().apply {
            nextRefreshResult = Result.success(
                OAuth2TokenResponse(
                    accessToken = "refreshed-token-123",
                    expiresIn = 3600,
                )
            )
        }
        val expiredTokenState = OAuth2TokenState(
            accessToken = "expired-token",
            refreshToken = "refresh-token-xyz",
            expiresAtEpochMs = 1000L,
        )
        var updatedToken: String? = null
        val context = DynamicVariableContext(
            effectiveAuthType = AuthType.OAUTH2,
            oauth2Config = OAuth2ResolvedConfig(
                tokenUrl = "https://auth.example.com/token",
                clientId = "client-1",
                clientSecret = "secret-1",
            ),
            oauth2TokenState = expiredTokenState,
            oauth2TokenClient = fakeClient,
            onOAuth2TokenUpdated = { newAccess, _, _, _ ->
                updatedToken = newAccess
            },
        )

        val result = provider.resolve(setOf("_auth.oauth2.accessToken"), context)

        assertEquals("refreshed-token-123", result["_auth.oauth2.accessToken"])
        assertEquals("refreshed-token-123", updatedToken)
        assertEquals(1, fakeClient.refreshCallCount)
    }

    @Test
    fun fetches_new_token_via_client_credentials_when_token_missing() = runTest {
        val provider = OAuth2DynamicVariableProvider()
        val fakeClient = FakeOAuth2TokenClient().apply {
            nextClientCredentialsResult = Result.success(
                OAuth2TokenResponse(
                    accessToken = "new-client-cred-token",
                    expiresIn = 300,
                )
            )
        }
        var updatedToken: String? = null
        val context = DynamicVariableContext(
            effectiveAuthType = AuthType.OAUTH2,
            oauth2Config = OAuth2ResolvedConfig(
                flow = OAuth2Flow.CLIENT_CREDENTIALS,
                tokenUrl = "https://auth.example.com/token",
                clientId = "my-client",
                clientSecret = "my-secret",
            ),
            oauth2TokenState = OAuth2TokenState(accessToken = ""),
            oauth2TokenClient = fakeClient,
            onOAuth2TokenUpdated = { newAccess, _, _, _ ->
                updatedToken = newAccess
            },
        )

        val result = provider.resolve(setOf("_auth.oauth2.accessToken"), context)

        assertEquals("new-client-cred-token", result["_auth.oauth2.accessToken"])
        assertEquals("new-client-cred-token", updatedToken)
        assertEquals(1, fakeClient.clientCredentialsCallCount)
    }

    @Test
    fun throws_exception_when_token_fetch_fails() = runTest {
        val provider = OAuth2DynamicVariableProvider()
        val fakeClient = FakeOAuth2TokenClient().apply {
            nextClientCredentialsResult = Result.failure(OAuth2Exception("invalid_client", "Bad credentials"))
        }
        val context = DynamicVariableContext(
            effectiveAuthType = AuthType.OAUTH2,
            oauth2Config = OAuth2ResolvedConfig(
                flow = OAuth2Flow.CLIENT_CREDENTIALS,
                tokenUrl = "https://auth.example.com/token",
                clientId = "bad-client",
            ),
            oauth2TokenState = OAuth2TokenState(accessToken = ""),
            oauth2TokenClient = fakeClient,
        )

        val ex = assertFailsWith<DynamicVariableResolutionException> {
            provider.resolve(setOf("_auth.oauth2.accessToken"), context)
        }
        assertTrue(ex.message!!.contains("invalid_client") || ex.message!!.contains("Bad credentials"), "Message was: ${ex.message}")
    }
}
