package io.github.crustack.clapi.ui.shared.components

import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.EnvState
import io.github.crustack.clapi.ui.shared.state.MutableExternalSecret
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequestEditorGcpSecretsAutocompleteTest {

    @Test
    fun getAvailableVariables_includes_gcp_external_secrets_with_prefix() {
        val state = AppState()
        val env = EnvState(
            name = "GcpEnv",
            externalSecretsProvider = "gcp-secrets-manager",
            externalSecrets = listOf(
                MutableExternalSecret(
                    name = "db_pass",
                    path = "my-secret-password",
                    targetField = "",
                    url = "",
                    enabled = true,
                ),
                MutableExternalSecret(
                    name = "api_key",
                    path = "api-credentials",
                    targetField = "key",
                    url = "my-custom-project",
                    enabled = true,
                ),
                MutableExternalSecret(
                    name = "disabled_secret",
                    path = "disabled-secret",
                    targetField = "",
                    url = "",
                    enabled = false,
                ),
            ),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState()
        val available = getAvailableVariables(state, tab)

        val secretKeys = available.map { it.first }
        assertTrue(secretKeys.contains("_secret.db_pass"), "Enabled GCP secret must be in available variables")
        assertTrue(secretKeys.contains("_secret.api_key"), "Enabled GCP secret with project override must be in available variables")
        assertFalse(secretKeys.contains("_secret.disabled_secret"), "Disabled GCP secret must not be in available variables")

        val dbEntry = available.first { it.first == "_secret.db_pass" }
        assertEquals("my-secret-password", dbEntry.second)

        val apiEntry = available.first { it.first == "_secret.api_key" }
        assertEquals("my-custom-project/api-credentials", apiEntry.second)
    }
}
