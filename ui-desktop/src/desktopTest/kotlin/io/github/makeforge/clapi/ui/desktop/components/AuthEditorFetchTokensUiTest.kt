package io.github.makeforge.clapi.ui.desktop.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.model.opencollection.OAuth2CredentialsPlacement
import io.github.makeforge.clapi.core.model.opencollection.OAuth2Flow
import io.github.makeforge.clapi.core.network.oauth2.OAuth2Exception
import io.github.makeforge.clapi.core.network.oauth2.OAuth2TokenClient
import io.github.makeforge.clapi.core.network.oauth2.OAuth2TokenResponse
import io.github.makeforge.clapi.ui.shared.components.AuthEditor
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthEditorFetchTokensUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakeOAuth2TokenClient(
        var result: Result<OAuth2TokenResponse>,
    ) : OAuth2TokenClient {
        var fetchCalls = 0

        override suspend fun fetchClientCredentialsToken(
            tokenUrl: String,
            clientId: String,
            clientSecret: String,
            placement: OAuth2CredentialsPlacement,
            scope: String?,
        ): Result<OAuth2TokenResponse> {
            fetchCalls++
            return result
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
            fetchCalls++
            return result
        }

        override suspend fun refreshToken(
            tokenUrl: String,
            refreshToken: String,
            clientId: String?,
            clientSecret: String?,
            placement: OAuth2CredentialsPlacement,
            scope: String?,
        ): Result<OAuth2TokenResponse> {
            fetchCalls++
            return result
        }
    }

    @Test
    fun testFetchTokens_success_populatesTokenFields() {
        val fakeClient = FakeOAuth2TokenClient(
            result = Result.success(
                OAuth2TokenResponse(
                    accessToken = "mock-access-jwt-token",
                    refreshToken = "mock-refresh-token",
                    idToken = "mock-id-token",
                    expiresIn = 3600,
                )
            )
        )

        val state = AppState()
        val tab = RequestTabState(id = "tab-fetch-1", name = "Test Fetch").apply {
            authType = AuthType.OAUTH2
            authOAuth2Flow = OAuth2Flow.CLIENT_CREDENTIALS
            authOAuth2AccessTokenUrl = "https://auth.example.com/oauth/token"
            authOAuth2ClientId = "client-1"
            authOAuth2ClientSecret = "secret-1"
        }

        composeRule.setContent {
            AuthEditor(tab = tab, state = state, tokenClient = fakeClient) {}
        }
        composeRule.waitForIdle()

        // Button should exist
        val fetchButton = composeRule.onNodeWithTag("auth-oauth2-fetch-tokens-button")
        fetchButton.assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertEquals(1, fakeClient.fetchCalls)
        assertEquals("mock-access-jwt-token", tab.authOAuth2AccessToken)
        assertEquals("mock-refresh-token", tab.authOAuth2RefreshToken)
        assertEquals("mock-id-token", tab.authOAuth2IdToken)
        assertNotNull(tab.authOAuth2ExpiresAtEpochMs)

        // Tokens should be displayed in the UI
        composeRule.onNodeWithTag("auth-oauth2-access-token", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("auth-oauth2-refresh-token", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("auth-oauth2-id-token", useUnmergedTree = true).assertIsDisplayed()

        // Copy buttons should exist
        composeRule.onNodeWithTag("auth-oauth2-copy-access-token").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("auth-oauth2-copy-refresh-token").assertIsDisplayed().performClick()
    }

    @Test
    fun testFetchTokens_failure_displaysErrorBanner() {
        val fakeClient = FakeOAuth2TokenClient(
            result = Result.failure(OAuth2Exception("invalid_client", "Bad client credentials", 401))
        )

        val state = AppState()
        val tab = RequestTabState(id = "tab-fetch-2", name = "Test Fail").apply {
            authType = AuthType.OAUTH2
            authOAuth2Flow = OAuth2Flow.CLIENT_CREDENTIALS
            authOAuth2AccessTokenUrl = "https://auth.example.com/oauth/token"
            authOAuth2ClientId = "wrong-client"
            authOAuth2ClientSecret = "wrong-secret"
        }

        composeRule.setContent {
            AuthEditor(tab = tab, state = state, tokenClient = fakeClient) {}
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("auth-oauth2-fetch-tokens-button").performClick()
        composeRule.waitForIdle()

        assertEquals(1, fakeClient.fetchCalls)
        assertEquals("Bad client credentials", tab.authOAuth2FetchError)

        composeRule.onNodeWithTag("auth-oauth2-error-banner", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Bad client credentials", useUnmergedTree = true).assertIsDisplayed()
    }
}
