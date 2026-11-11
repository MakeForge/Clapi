package io.github.crustack.clapi.ui.shared.components

import io.github.crustack.clapi.ui.shared.components.executor.*

import io.github.crustack.clapi.core.model.AuthType
import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.core.model.opencollection.OAuth2CredentialsPlacement
import io.github.crustack.clapi.core.model.opencollection.OAuth2Flow
import io.github.crustack.clapi.core.network.oauth2.OAuth2TokenClient
import io.github.crustack.clapi.core.network.oauth2.OAuth2TokenResponse
import io.github.crustack.clapi.ui.shared.platform.currentTimeMillis
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.MutableKeyValue
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DynamicVariableExecutionPipelineTest {

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
    fun http_request_with_oauth2_access_token_variable_resolves_existing_valid_token() = runTest {
        val state = AppState()
        val tab = RequestTabState(url = "https://example.com/api")
        tab.authType = AuthType.OAUTH2
        tab.authOAuth2AccessToken = "valid-token-999"
        tab.authOAuth2ExpiresAtEpochMs = currentTimeMillis() + 60_000L
        tab.authOAuth2AccessTokenUrl = "https://auth.example.com/token"

        val fakeClient = FakeOAuth2TokenClient()
        val textsToScan = listOf("Bearer {{_auth.oauth2.accessToken}}")

        val resolved = resolveDynamicVariablesForTab(
            state = state,
            tab = tab,
            textsToScan = textsToScan,
            variableLayers = emptyList(),
            tokenClient = fakeClient,
        )

        assertNotNull(resolved)
        assertEquals("valid-token-999", resolved["_auth.oauth2.accessToken"])
        assertEquals(0, fakeClient.clientCredentialsCallCount)
        assertEquals(0, fakeClient.refreshCallCount)
    }

    @Test
    fun http_request_with_oauth2_access_token_variable_refreshes_when_expired() = runTest {
        val state = AppState()
        val tab = RequestTabState(url = "https://example.com/api")
        tab.authType = AuthType.OAUTH2
        tab.authOAuth2AccessToken = "expired-token"
        tab.authOAuth2RefreshToken = "valid-refresh-token"
        tab.authOAuth2ExpiresAtEpochMs = 1000L
        tab.authOAuth2AccessTokenUrl = "https://auth.example.com/token"

        val fakeClient = FakeOAuth2TokenClient().apply {
            nextRefreshResult = Result.success(
                OAuth2TokenResponse(
                    accessToken = "freshly-refreshed-token",
                    expiresIn = 3600,
                )
            )
        }
        val textsToScan = listOf("Bearer {{_auth.oauth2.accessToken}}")

        val resolved = resolveDynamicVariablesForTab(
            state = state,
            tab = tab,
            textsToScan = textsToScan,
            variableLayers = emptyList(),
            tokenClient = fakeClient,
        )

        assertNotNull(resolved)
        assertEquals("freshly-refreshed-token", resolved["_auth.oauth2.accessToken"])
        assertEquals("freshly-refreshed-token", tab.authOAuth2AccessToken)
        assertEquals(1, fakeClient.refreshCallCount)
    }

    @Test
    fun http_request_halts_with_error_when_oauth2_is_not_enabled() = runTest {
        val state = AppState()
        val tab = RequestTabState(url = "https://example.com/api")
        tab.authType = AuthType.NONE
        tab.headers.add(MutableKeyValue("X-Token", "{{_auth.oauth2.accessToken}}"))

        state.openTabs.add(tab)
        state.activeTabIndex = 0

        val fakeClient = FakeOAuth2TokenClient()

        sendRequest(this, state, tab, tokenClient = fakeClient)
        tab.currentJob?.join()

        assertFalse(tab.isLoading)
        assertNotNull(tab.lastError)
        assertTrue(tab.lastError!!.contains("OAuth2 authentication"), "Expected OAuth2 error, got: ${tab.lastError}")
    }

    @Test
    fun grpc_request_metadata_authorization_resolves_oauth2_access_token() = runTest {
        val state = AppState()
        val tab = RequestTabState(
            url = "localhost:50051",
            kind = io.github.crustack.clapi.core.model.RequestKind.GRPC,
        )
        tab.authType = AuthType.OAUTH2
        tab.authOAuth2AccessToken = "grpc-valid-access-token"
        tab.authOAuth2ExpiresAtEpochMs = currentTimeMillis() + 60_000L
        tab.grpcMetadata.add(MutableKeyValue("authorization", "Bearer {{_auth.oauth2.accessToken}}"))

        val fakeClient = FakeOAuth2TokenClient()
        val textsToScan = tab.grpcMetadata.flatMap { listOf(it.key, it.value) }

        val resolved = resolveDynamicVariablesForTab(
            state = state,
            tab = tab,
            textsToScan = textsToScan,
            variableLayers = emptyList(),
            tokenClient = fakeClient,
        )

        assertNotNull(resolved)
        assertEquals("grpc-valid-access-token", resolved["_auth.oauth2.accessToken"])

        val grpcCallRequest = resolveGrpcCallRequest(
            tab = tab,
            state = state,
            requestScopedVars = resolved,
            urlOverride = "localhost:50051",
            metadataOverride = tab.grpcMetadata.map { io.github.crustack.clapi.core.model.KeyValueEntry(it.key, it.value) },
            bodyOverride = "{}",
        )

        val authHeader = grpcCallRequest.metadata.firstOrNull { it.first.equals("authorization", ignoreCase = true) }
        assertNotNull(authHeader, "Metadata must contain authorization header")
        assertEquals("Bearer grpc-valid-access-token", authHeader.second)
    }

    @Test
    fun grpc_request_halts_with_error_when_oauth2_is_not_enabled() = runTest {
        val state = AppState()
        val tab = RequestTabState(
            url = "localhost:50051",
            kind = io.github.crustack.clapi.core.model.RequestKind.GRPC,
        )
        tab.protoFilePath = "/tmp/fake.proto"
        tab.grpcMethod = "Greeter/SayHello"
        tab.authType = AuthType.NONE
        tab.grpcMetadata.add(MutableKeyValue("authorization", "Bearer {{_auth.oauth2.accessToken}}"))

        state.openTabs.add(tab)
        state.activeTabIndex = 0

        val fakeClient = FakeOAuth2TokenClient()

        sendRequest(this, state, tab, tokenClient = fakeClient)
        tab.currentJob?.join()

        assertFalse(tab.isLoading)
        assertNotNull(tab.lastError)
        assertTrue(tab.lastError!!.contains("OAuth2 authentication"), "Expected OAuth2 error, got: ${tab.lastError}")
    }
}
