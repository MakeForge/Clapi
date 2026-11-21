package io.github.makeforge.clapi.ui.shared.components

import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.MutableExternalSecret
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequestEditorSecretsAutocompleteTest {

    @Test
    fun getAvailableVariables_includes_keepassxc_external_secrets_with_prefix() {
        val state = AppState()
        val env = EnvState(
            name = "KeePassEnv",
            externalSecretsProvider = "keepassxc",
            externalSecrets = listOf(
                MutableExternalSecret(
                    name = "api_key",
                    url = "https://api.example.com",
                    targetField = "password",
                    enabled = true,
                ),
                MutableExternalSecret(
                    name = "db_pass",
                    url = "https://db.internal",
                    targetField = "password",
                    enabled = false,
                ),
            ),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState()
        val available = getAvailableVariables(state, tab)

        val secretKeys = available.map { it.first }
        assertTrue(secretKeys.contains("_secret.api_key"), "Enabled KeePassXC secret must be in available variables")
        assertFalse(secretKeys.contains("_secret.db_pass"), "Disabled KeePassXC secret must not be in available variables")

        val apiEntry = available.first { it.first == "_secret.api_key" }
        assertEquals("https://api.example.com", apiEntry.second)
    }

    @Test
    fun getAvailableVariables_includes_aws_secrets_manager_external_secrets_with_prefix() {
        val state = AppState()
        val env = EnvState(
            name = "AwsEnv",
            externalSecretsProvider = "aws-secrets-manager",
            externalSecrets = listOf(
                MutableExternalSecret(
                    name = "client_secret",
                    path = "prod/payment/stripe",
                    targetField = "secret_key",
                    enabled = true,
                ),
                MutableExternalSecret(
                    name = "disabled_stripe",
                    path = "prod/payment/stripe",
                    targetField = "secret_key",
                    enabled = false,
                ),
            ),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState()
        val available = getAvailableVariables(state, tab)

        val secretKeys = available.map { it.first }
        assertTrue(secretKeys.contains("_secret.client_secret"), "Enabled AWS secret must be in available variables")
        assertFalse(secretKeys.contains("_secret.disabled_stripe"), "Disabled AWS secret must not be in available variables")

        val stripeEntry = available.first { it.first == "_secret.client_secret" }
        assertEquals("prod/payment/stripe", stripeEntry.second)
    }
}
