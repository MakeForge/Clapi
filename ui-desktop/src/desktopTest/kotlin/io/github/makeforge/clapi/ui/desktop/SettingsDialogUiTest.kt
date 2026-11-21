package io.github.makeforge.clapi.ui.desktop

import io.github.makeforge.clapi.core.model.opencollection.ExternalSecretType
import io.github.makeforge.clapi.ui.shared.MainScreen

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.i18n.AppLanguage
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import io.github.makeforge.clapi.ui.shared.state.AppStateExamples

/**
 * Compose UI tests covering:
 *  - Settings dialog opens / closes via toolbar button
 *  - Confirm-delete dialog shows, dismisses, and calls the supplied action
 */
class SettingsDialogUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── Settings dialog ─────────────────────────────────────────────────────

    @Test
    fun settings_dialog_opens_when_settings_button_is_clicked() {
        composeRule.setContent { MainScreen() }

        composeRule.onNodeWithTag("settings-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings-dialog", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun settings_dialog_closes_when_close_button_is_clicked() {
        composeRule.setContent { MainScreen() }

        // Open
        composeRule.onNodeWithTag("settings-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-dialog", useUnmergedTree = true).assertIsDisplayed()

        // Close
        composeRule.onNodeWithTag("settings-close-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings-dialog", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun editor_settings_exposes_and_enables_position_indicator_toggle() {
        val state = AppState()
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("settings-button", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("settings-section-editor", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("editor-position-indicator-toggle", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        assertTrue(state.settings.showEditorPositionIndicator)
    }

    // ── Confirm delete dialog ───────────────────────────────────────────────

    @Test
    fun confirm_dialog_is_shown_when_state_showConfirm_is_called() {
        val state = AppState()
        composeRule.setContent { MainScreen(state = state) }

        composeRule.runOnUiThread {
            state.showConfirm("Delete item?", "This cannot be undone.") { /* no-op */ }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("confirm-dialog", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun confirm_dialog_dismisses_on_cancel_without_invoking_action() {
        val state = AppState()
        var actionCalled = false
        composeRule.setContent { MainScreen(state = state) }

        composeRule.runOnUiThread {
            state.showConfirm("Delete?", "Are you sure?") { actionCalled = true }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("confirm-cancel-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("confirm-dialog", useUnmergedTree = true).assertDoesNotExist()
        assert(!actionCalled) { "Cancel should NOT invoke the pending action" }
    }

    @Test
    fun confirm_dialog_invokes_action_on_confirm_click() {
        val state = AppState()
        var actionCalled = false
        composeRule.setContent { MainScreen(state = state) }

        composeRule.runOnUiThread {
            state.showConfirm("Delete?", "Are you sure?") { actionCalled = true }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("confirm-ok-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("confirm-dialog", useUnmergedTree = true).assertDoesNotExist()
        assert(actionCalled) { "Confirm should invoke the pending action" }
    }

    @Test
    fun clear_history_requires_confirmation_and_clears_entries() {
        val state = AppStateExamples.demo()
        composeRule.setContent { MainScreen(state = state) }

        composeRule.onNodeWithTag("settings-button", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Data", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("clear-history-action", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("confirm-dialog", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Are you sure you want to clear request history?", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("confirm-ok-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assert(state.historyItems.isEmpty()) { "History should be cleared after confirmation" }
        composeRule.onAllNodesWithText("List users", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun language_selection_updates_settings_language() {
        val state = AppState()
        composeRule.setContent { MainScreen(state = state) }

        composeRule.onNodeWithTag("settings-button", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Language", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("language-es", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assert(state.settings.language == AppLanguage.ES) { "Language should switch to Spanish" }
    }

    @Test
    fun language_switch_updates_sidebar_label_without_restart() {
        val state = AppState(openDefaultTab = false)
        composeRule.setContent {
            ClapiTheme(appTheme = state.settings.theme, language = state.settings.language) {
                MainScreen(state = state)
            }
        }

        composeRule.onNodeWithText("History", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("settings-button", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Language", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("language-es", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Historial", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun json5_body_toggle_is_displayed_and_click_disables_setting() {
        val state = AppState()
        composeRule.setContent { MainScreen(state = state) }

        composeRule.onNodeWithTag("settings-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("json5-body-toggle", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(state.settings.allowJson5InJsonBodies)

        composeRule.onNodeWithTag("json5-body-toggle", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertFalse(state.settings.allowJson5InJsonBodies)
    }

    @Test
    fun secrets_manager_settings_tab_displays_vault_fields_and_persists_changes() {
        val state = AppState()
        composeRule.setContent { MainScreen(state = state) }

        composeRule.onNodeWithTag("settings-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings-section-secrets", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("vault-server-url", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("vault-mount-path", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("vault-namespace", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("vault-token", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("vault-token-toggle", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun secrets_manager_settings_tab_allows_switching_providers_via_dropdown() {
        val state = AppState()
        composeRule.setContent { MainScreen(state = state) }

        composeRule.onNodeWithTag("settings-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings-section-secrets", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        // Selection box should be displayed
        composeRule.onNodeWithTag("secrets-provider-selector", useUnmergedTree = true).assertIsDisplayed()

        // Default HashiCorp Vault connection status badge & test button should be displayed
        composeRule.onNodeWithTag("vault-status-badge", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("vault-test-connection-btn", useUnmergedTree = true).assertIsDisplayed()

        // Switch to AWS Secrets Manager
        composeRule.onNodeWithTag("secrets-provider-selector", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("secrets-provider-option-aws-secrets-manager", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        kotlin.test.assertEquals(ExternalSecretType.AWS_SECRETS_MANAGER, state.settings.vaultProviderType)
        composeRule.onNodeWithTag("aws-status-badge", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("aws-test-connection-btn", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("aws-region", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("aws-access-key-id", useUnmergedTree = true).assertIsDisplayed()

        // Switch to Azure Key Vault
        composeRule.onNodeWithTag("secrets-provider-selector", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("secrets-provider-option-azure-key-vault", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        kotlin.test.assertEquals(ExternalSecretType.AZURE_KEY_VAULT, state.settings.vaultProviderType)
        composeRule.onNodeWithTag("azure-status-badge", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("azure-test-connection-btn", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("azure-vault-name", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("azure-tenant-id", useUnmergedTree = true).assertIsDisplayed()

        // Switch to GCP Secret Manager
        composeRule.onNodeWithTag("secrets-provider-selector", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("secrets-provider-option-gcp-secrets-manager", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        kotlin.test.assertEquals(ExternalSecretType.GCP_SECRETS_MANAGER, state.settings.vaultProviderType)
        composeRule.onNodeWithTag("gcp-status-badge", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("gcp-test-connection-btn", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("gcp-project-id", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("gcp-service-account-key", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("gcp-endpoint-url", useUnmergedTree = true).assertIsDisplayed()

        // Switch to KeePassXC
        composeRule.onNodeWithTag("secrets-provider-selector", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("secrets-provider-option-keepassxc", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        kotlin.test.assertEquals(ExternalSecretType.KEEPASSXC, state.settings.vaultProviderType)
        composeRule.onNodeWithTag("keepassxc-status-badge", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("keepassxc-test-connection-btn", useUnmergedTree = true).assertIsDisplayed()

        // Switch to Keeper Security Cloud
        composeRule.onNodeWithTag("secrets-provider-selector", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("secrets-provider-option-keeper-security-cloud", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        kotlin.test.assertEquals(ExternalSecretType.KEEPER_SECURITY_CLOUD, state.settings.vaultProviderType)
        composeRule.onNodeWithTag("keeper-status-badge", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("keeper-test-connection-btn", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun data_settings_displays_app_logs_path_and_open_button() {
        val state = AppStateExamples.demo()
        composeRule.setContent { MainScreen(state = state) }

        composeRule.onNodeWithTag("settings-button", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Data", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        val expectedLogDir = System.getProperty("clapi.log.dir")
            ?: (System.getProperty("user.home") + "/.clapi/logs")
        val expectedLogPath = java.io.File(expectedLogDir, "clapi.log").absolutePath

        composeRule.onNodeWithText("App logs", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("app-logs-path", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(expectedLogPath, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("open-log-file-button", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun data_settings_open_log_file_button_invokes_opener() {
        val state = AppState()
        var openedPath: String? = null
        val expectedLogDir = System.getProperty("clapi.log.dir")
            ?: (System.getProperty("user.home") + "/.clapi/logs")
        val expectedLogPath = java.io.File(expectedLogDir, "clapi.log").absolutePath

        composeRule.setContent {
            io.github.makeforge.clapi.ui.shared.components.dialogs.settings.DataSettings(
                state = state,
                onOpenFile = { openedPath = it },
            )
        }

        composeRule.onNodeWithTag("open-log-file-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        kotlin.test.assertEquals(expectedLogPath, openedPath)
    }
}
