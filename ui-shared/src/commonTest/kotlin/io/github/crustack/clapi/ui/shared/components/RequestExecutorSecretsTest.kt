package io.github.crustack.clapi.ui.shared.components

import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.EnvState
import io.github.crustack.clapi.ui.shared.state.MutableExternalSecret
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RequestExecutorSecretsTest {

    @Test
    fun sendRequest_halts_and_sets_error_when_vault_not_configured() = runTest {
        val state = AppState()
        val env = EnvState(
            name = "TestEnv",
            externalSecrets = listOf(
                MutableExternalSecret(name = "api_key", path = "secret/key", enabled = true),
            ),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(url = "https://example.com/api?token={{_secret.api_key}}")
        state.openTabs.add(tab)
        state.activeTabIndex = 0

        sendRequest(this, state, tab)
        tab.currentJob?.join()

        assertFalse(tab.isLoading)
        assertNotNull(tab.lastError)
        assertTrue(tab.lastError!!.contains("Vault URL is not configured") || tab.lastError!!.contains("Vault token is not configured"))
    }

    @Test
    fun sendRequest_halts_and_sets_error_when_secret_is_disabled() = runTest {
        val state = AppState()
        state.settings.vaultServerUrl = "http://127.0.0.1:8200"
        state.settings.vaultToken = "token"

        val env = EnvState(
            name = "TestEnv",
            externalSecrets = listOf(
                MutableExternalSecret(name = "disabled_sec", path = "secret/key", enabled = false),
            ),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(url = "https://example.com/api?token={{_secret.disabled_sec}}")
        state.openTabs.add(tab)
        state.activeTabIndex = 0

        sendRequest(this, state, tab)
        tab.currentJob?.join()

        assertFalse(tab.isLoading)
        assertNotNull(tab.lastError)
        assertTrue(tab.lastError!!.contains("disabled"))
    }

    @Test
    fun sendRequest_halts_and_sets_error_when_secret_not_defined_in_env() = runTest {
        val state = AppState()
        state.settings.vaultServerUrl = "http://127.0.0.1:8200"
        state.settings.vaultToken = "token"

        val env = EnvState(name = "TestEnv")
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(url = "https://example.com/api?token={{_secret.unknown_sec}}")
        state.openTabs.add(tab)
        state.activeTabIndex = 0

        sendRequest(this, state, tab)
        tab.currentJob?.join()

        assertFalse(tab.isLoading)
        assertNotNull(tab.lastError)
        assertTrue(tab.lastError!!.contains("not defined in the active environment"))
    }
}
