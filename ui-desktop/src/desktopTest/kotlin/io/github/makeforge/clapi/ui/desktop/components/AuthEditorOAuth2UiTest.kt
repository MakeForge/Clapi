package io.github.makeforge.clapi.ui.desktop.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.model.opencollection.OAuth2CredentialsPlacement
import io.github.makeforge.clapi.core.model.opencollection.OAuth2Flow
import io.github.makeforge.clapi.ui.shared.components.AuthEditor
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthEditorOAuth2UiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun testSelectOAuth2_rendersFlowAndCredentialFields() {
        val state = AppState()
        val tab = RequestTabState(id = "tab-1", name = "Test Tab")
        var dirtyCalled = false

        composeRule.setContent {
            AuthEditor(tab = tab, state = state) {
                dirtyCalled = true
            }
        }
        composeRule.waitForIdle()

        // Click OAuth2 chip
        composeRule.onNodeWithTag("auth-chip-oauth2").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertEquals(AuthType.OAUTH2, tab.authType)
        assertTrue(dirtyCalled)

        // Flow chips are displayed
        composeRule.onNodeWithTag("oauth2-flow-client_credentials").assertIsDisplayed()
        composeRule.onNodeWithTag("oauth2-flow-resource_owner_password_credentials").assertIsDisplayed()

        // Common OAuth2 fields exist
        composeRule.onNodeWithTag("oauth2-client-secret-field", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("oauth2-placement-basic_auth_header").assertIsDisplayed()
        composeRule.onNodeWithTag("oauth2-placement-body").assertIsDisplayed()
    }

    @Test
    fun testSwitchingFlow_togglesResourceOwnerFields() {
        val state = AppState()
        val tab = RequestTabState(id = "tab-2", name = "Test Tab").apply {
            authType = AuthType.OAUTH2
            authOAuth2Flow = OAuth2Flow.CLIENT_CREDENTIALS
        }
        var dirtyCount = 0

        composeRule.setContent {
            AuthEditor(tab = tab, state = state) {
                dirtyCount++
            }
        }
        composeRule.waitForIdle()

        // Password flow username/password shouldn't exist in Client Credentials flow
        composeRule.onNodeWithTag("oauth2-password-field", useUnmergedTree = true).assertDoesNotExist()

        // Switch to password flow
        composeRule.onNodeWithTag("oauth2-flow-resource_owner_password_credentials").performClick()
        composeRule.waitForIdle()

        assertEquals(OAuth2Flow.RESOURCE_OWNER_PASSWORD_CREDENTIALS, tab.authOAuth2Flow)
        assertTrue(dirtyCount > 0)

        // Now username/password fields exist
        composeRule.onNodeWithTag("oauth2-password-field", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun testSwitchingPlacement_updatesTabState() {
        val state = AppState()
        val tab = RequestTabState(id = "tab-3", name = "Test Tab").apply {
            authType = AuthType.OAUTH2
            authOAuth2CredentialsPlacement = OAuth2CredentialsPlacement.BASIC_AUTH_HEADER
        }

        composeRule.setContent {
            AuthEditor(tab = tab, state = state) {}
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("oauth2-placement-body").performClick()
        composeRule.waitForIdle()

        assertEquals(OAuth2CredentialsPlacement.BODY, tab.authOAuth2CredentialsPlacement)
    }
}
