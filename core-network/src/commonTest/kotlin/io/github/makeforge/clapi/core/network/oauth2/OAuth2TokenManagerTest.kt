package io.github.makeforge.clapi.core.network.oauth2

import io.github.makeforge.clapi.core.model.AuthConfig
import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.model.opencollection.OAuth2CredentialsPlacement
import io.github.makeforge.clapi.core.model.opencollection.OAuth2Flow
import io.github.makeforge.clapi.core.network.dynamicvar.DynamicVariableContext
import io.github.makeforge.clapi.core.network.dynamicvar.OAuth2DynamicVariableProvider
import io.github.makeforge.clapi.core.network.dynamicvar.OAuth2ResolvedConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OAuth2TokenManagerTest {

    private class CountingTokenClient(
        private val refreshResponse: Result<OAuth2TokenResponse>? = null,
        private val clientCredentialsResponse: Result<OAuth2TokenResponse> = Result.success(
            OAuth2TokenResponse(accessToken = "cc-token-1", expiresIn = 3600),
        ),
        private val passwordResponse: Result<OAuth2TokenResponse> = Result.success(
            OAuth2TokenResponse(accessToken = "pwd-token-1", expiresIn = 3600),
        ),
        private val artificialDelayMs: Long = 0L,
    ) : OAuth2TokenClient {
        var refreshCallCount = 0
        var clientCredentialsCallCount = 0
        var passwordCallCount = 0

        override suspend fun fetchClientCredentialsToken(
            tokenUrl: String,
            clientId: String,
            clientSecret: String,
            placement: OAuth2CredentialsPlacement,
            scope: String?,
        ): Result<OAuth2TokenResponse> {
            if (artificialDelayMs > 0) delay(artificialDelayMs)
            clientCredentialsCallCount++
            return clientCredentialsResponse
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
            if (artificialDelayMs > 0) delay(artificialDelayMs)
            passwordCallCount++
            return passwordResponse
        }

        override suspend fun refreshToken(
            tokenUrl: String,
            refreshToken: String,
            clientId: String?,
            clientSecret: String?,
            placement: OAuth2CredentialsPlacement,
            scope: String?,
        ): Result<OAuth2TokenResponse> {
            if (artificialDelayMs > 0) delay(artificialDelayMs)
            refreshCallCount++
            return refreshResponse ?: Result.failure(OAuth2Exception("invalid_grant", "Refresh token expired"))
        }
    }

    @Test
    fun ensureValidToken_returns_existing_token_when_outside_expiry_buffer_and_refreshes_when_inside_buffer() = runTest {
        var nowMs = 100_000L
        val client = CountingTokenClient(
            refreshResponse = Result.success(
                OAuth2TokenResponse(accessToken = "refreshed-token-2", refreshToken = "rt-2", expiresIn = 3600),
            ),
        )
        val manager = OAuth2TokenManager(defaultTokenClient = client, clock = { nowMs })
        val config = OAuth2ResolvedConfig(
            flow = OAuth2Flow.CLIENT_CREDENTIALS,
            tokenUrl = "https://auth.example.com/token",
            clientId = "cid",
            clientSecret = "csec",
        )

        // Token expires in 45 seconds (> 30s buffer) -> valid, no refresh
        val validState = OAuth2TokenState(
            accessToken = "valid-token",
            refreshToken = "rt-1",
            expiresAtEpochMs = nowMs + 45_000L,
            fetchedAtEpochMs = nowMs - 10_000L,
        )
        val res1 = manager.ensureValidToken(config, validState).getOrThrow()
        assertFalse(res1.refreshed)
        assertEquals("valid-token", res1.accessToken)
        assertEquals(0, client.refreshCallCount)

        // Advance time by 20 seconds -> expires in 25 seconds (< 30s buffer) -> triggers refresh
        nowMs += 20_000L
        val res2 = manager.ensureValidToken(config, validState).getOrThrow()
        assertTrue(res2.refreshed)
        assertEquals("refreshed-token-2", res2.accessToken)
        assertEquals("rt-2", res2.refreshToken)
        assertEquals(1, client.refreshCallCount)
    }

    @Test
    fun ensureValidToken_falls_back_to_client_credentials_or_password_when_refresh_token_fails() = runTest {
        val client = CountingTokenClient(
            refreshResponse = Result.failure(OAuth2Exception("invalid_grant", "Refresh failed")),
            passwordResponse = Result.success(OAuth2TokenResponse(accessToken = "fallback-pwd-token", expiresIn = 3600)),
        )
        val manager = OAuth2TokenManager(defaultTokenClient = client, clock = { 100_000L })
        val config = OAuth2ResolvedConfig(
            flow = OAuth2Flow.RESOURCE_OWNER_PASSWORD_CREDENTIALS,
            tokenUrl = "https://auth.example.com/token",
            clientId = "cid",
            clientSecret = "csec",
            username = "alice",
            password = "secret",
        )
        val expiredState = OAuth2TokenState(
            accessToken = "old-token",
            refreshToken = "expired-rt",
            expiresAtEpochMs = 50_000L,
            fetchedAtEpochMs = 10_000L,
        )

        val result = manager.ensureValidToken(config, expiredState).getOrThrow()
        assertTrue(result.refreshed)
        assertEquals("fallback-pwd-token", result.accessToken)
        assertEquals(1, client.refreshCallCount)
        assertEquals(1, client.passwordCallCount)
    }

    @Test
    fun ensureValidToken_deduplicates_concurrent_and_back_to_back_refreshes_for_dynamic_var_and_auth_config() = runTest {
        val client = CountingTokenClient(
            refreshResponse = Result.success(
                OAuth2TokenResponse(accessToken = "single-refreshed-jwt", refreshToken = "new-rt", expiresIn = 3600),
            ),
            artificialDelayMs = 20L,
        )
        val manager = OAuth2TokenManager(defaultTokenClient = client, clock = { 200_000L })
        val config = OAuth2ResolvedConfig(
            flow = OAuth2Flow.CLIENT_CREDENTIALS,
            tokenUrl = "https://auth.example.com/token",
            clientId = "app-client",
            clientSecret = "app-secret",
        )
        val staleState = OAuth2TokenState(
            accessToken = "expired-jwt",
            refreshToken = "old-rt",
            expiresAtEpochMs = 100_000L,
            fetchedAtEpochMs = 50_000L,
        )

        val provider = OAuth2DynamicVariableProvider(defaultTokenManager = manager)
        val dynCtx = DynamicVariableContext(
            effectiveAuthType = AuthType.OAUTH2,
            oauth2Config = config,
            oauth2TokenState = staleState,
            oauth2TokenClient = client,
            oauth2TokenManager = manager,
        )

        val authConfig = AuthConfig(
            type = AuthType.OAUTH2,
            params = mapOf(
                "grantType" to OAuth2Flow.CLIENT_CREDENTIALS.value,
                "accessTokenUrl" to "https://auth.example.com/token",
                "clientId" to "app-client",
                "clientSecret" to "app-secret",
                "accessToken" to "expired-jwt",
                "refreshToken" to "old-rt",
                "expiresAtEpochMs" to "100000",
            ),
        )

        // Launch both dynamic variable resolution AND AuthConfig token validation concurrently with the same expired state
        val deferredDyn = async {
            provider.resolve(setOf(OAuth2DynamicVariableProvider.ACCESS_TOKEN_VARIABLE), dynCtx)
        }
        val deferredAuth = async {
            manager.ensureValidToken(authConfig = authConfig, tokenClient = client)
        }

        val dynMap = deferredDyn.await()
        val authRes = deferredAuth.await()

        assertEquals("single-refreshed-jwt", dynMap[OAuth2DynamicVariableProvider.ACCESS_TOKEN_VARIABLE])
        assertEquals("single-refreshed-jwt", authRes.accessToken)
        assertEquals("single-refreshed-jwt", authRes.updatedAuthConfig?.params?.get("accessToken"))
        // Only ONE HTTP refresh call must have been executed!
        assertEquals(1, client.refreshCallCount)
    }
}
