package io.github.crustack.clapi.ui.shared.components

import io.github.crustack.clapi.core.network.vault.HashiCorpVaultClient
import io.github.crustack.clapi.core.network.vault.SecretResolutionService
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.EnvState
import io.github.crustack.clapi.ui.shared.state.MutableExternalSecret
import io.github.crustack.clapi.ui.shared.state.MutableKeyValue
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

    private class FakeTestVaultClient(
        private val secrets: Map<String, Map<String, String>>,
    ) : HashiCorpVaultClient {
        override suspend fun fetchSecret(
            vaultUrl: String,
            token: String,
            secretPath: String,
            mountPath: String,
            namespace: String?,
        ): Map<String, String> = secrets[secretPath] ?: emptyMap()
    }

    @Test
    fun resolveExternalSecretsForTab_resolves_secret_variable_from_process_env() = runTest {
        val state = AppState()
        val env = EnvState(
            name = "TestEnv",
            variables = listOf(
                MutableKeyValue(key = "MY_KEY", value = "", secret = true, enabled = true),
            ),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(url = "https://example.com/api?key={{MY_KEY}}")
        val resolutionService = SecretResolutionService(FakeTestVaultClient(emptyMap()))

        val resolved = resolveExternalSecretsForTab(
            state = state,
            tab = tab,
            textsToScan = listOf(tab.url),
            secretResolutionService = resolutionService,
            processEnvProvider = { if (it == "MY_KEY") "proc_key_123" else null },
        )

        assertNotNull(resolved)
        assertEquals("proc_key_123", resolved["MY_KEY"])
    }

    @Test
    fun resolveExternalSecretsForTab_falls_back_to_matching_external_secret() = runTest {
        val state = AppState()
        state.settings.vaultServerUrl = "http://127.0.0.1:8200"
        state.settings.vaultToken = "root-token"

        val env = EnvState(
            name = "TestEnv",
            variables = listOf(
                MutableKeyValue(key = "MY_KEY", value = "", secret = true, enabled = true),
            ),
            externalSecrets = listOf(
                MutableExternalSecret(name = "MY_KEY", path = "secret/my_key", enabled = true),
            ),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(url = "https://example.com/api?key={{MY_KEY}}")
        val resolutionService = SecretResolutionService(
            FakeTestVaultClient(mapOf("secret/my_key" to mapOf("MY_KEY" to "vault_val_789"))),
        )

        val resolved = resolveExternalSecretsForTab(
            state = state,
            tab = tab,
            textsToScan = listOf(tab.url),
            secretResolutionService = resolutionService,
            processEnvProvider = { null },
        )

        assertNotNull(resolved)
        assertEquals("vault_val_789", resolved["MY_KEY"])
    }

    @Test
    fun resolveExternalSecretsForTab_sets_error_when_secret_variable_cannot_be_resolved() = runTest {
        val state = AppState()
        val env = EnvState(
            name = "TestEnv",
            variables = listOf(
                MutableKeyValue(key = "UNRESOLVED_SECRET", value = "", secret = true, enabled = true),
            ),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(url = "https://example.com/api?key={{UNRESOLVED_SECRET}}")
        val resolutionService = SecretResolutionService(FakeTestVaultClient(emptyMap()))

        val resolved = resolveExternalSecretsForTab(
            state = state,
            tab = tab,
            textsToScan = listOf(tab.url),
            secretResolutionService = resolutionService,
            processEnvProvider = { null },
        )

        assertEquals(null, resolved)
        assertFalse(tab.isLoading)
        assertEquals(
            "Secret variable 'UNRESOLVED_SECRET' could not be resolved from process environment or external secrets.",
            tab.lastError,
        )
    }

    @Test
    fun sendRequest_halts_and_sets_error_when_secret_variable_fails_resolution() = runTest {
        val state = AppState()
        val env = EnvState(
            name = "TestEnv",
            variables = listOf(
                MutableKeyValue(key = "UNRESOLVED_SECRET", value = "", secret = true, enabled = true),
            ),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(url = "https://example.com/api?key={{UNRESOLVED_SECRET}}")
        state.openTabs.add(tab)
        state.activeTabIndex = 0

        sendRequest(this, state, tab)
        tab.currentJob?.join()

        assertFalse(tab.isLoading)
        assertNotNull(tab.lastError)
        assertEquals(
            "Secret variable 'UNRESOLVED_SECRET' could not be resolved from process environment or external secrets.",
            tab.lastError,
        )
    }
}
