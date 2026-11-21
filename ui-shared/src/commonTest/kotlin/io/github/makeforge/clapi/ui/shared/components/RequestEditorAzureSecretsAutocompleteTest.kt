package io.github.makeforge.clapi.ui.shared.components

import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.MutableExternalSecret
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequestEditorAzureSecretsAutocompleteTest {

    @Test
    fun getAvailableVariables_includes_azure_external_secrets_with_prefix() {
        val state = AppState()
        val env = EnvState(
            name = "AzureEnv",
            externalSecretsProvider = "azure-key-vault",
            externalSecrets = listOf(
                MutableExternalSecret(
                    name = "db_pass",
                    path = "database-password",
                    targetField = "",
                    enabled = true,
                ),
                MutableExternalSecret(
                    name = "api_key",
                    path = "payment-key",
                    url = "https://custom.vault.azure.net",
                    targetField = "key",
                    enabled = true,
                ),
                MutableExternalSecret(
                    name = "disabled_secret",
                    path = "disabled-key",
                    targetField = "",
                    enabled = false,
                ),
            ),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState()
        val available = getAvailableVariables(state, tab)

        val secretKeys = available.map { it.first }
        assertTrue(secretKeys.contains("_secret.db_pass"), "Enabled Azure secret must be in available variables")
        assertTrue(secretKeys.contains("_secret.api_key"), "Enabled Azure secret with vault override must be in available variables")
        assertFalse(secretKeys.contains("_secret.disabled_secret"), "Disabled Azure secret must not be in available variables")

        val dbEntry = available.first { it.first == "_secret.db_pass" }
        assertEquals("database-password", dbEntry.second)

        val apiEntry = available.first { it.first == "_secret.api_key" }
        assertEquals("https://custom.vault.azure.net/payment-key", apiEntry.second)
    }
}
