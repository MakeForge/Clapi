package io.github.crustack.clapi.ui.desktop.components

import io.github.crustack.clapi.ui.shared.components.getAvailableVariables
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.EnvState
import io.github.crustack.clapi.ui.shared.state.MutableExternalSecret
import io.github.crustack.clapi.ui.shared.state.MutableKeyValue
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VariableHighlightSecretsTest {

    @Test
    fun getAvailableVariables_includes_enabled_external_secrets_formatted_as_secret_name() {
        val state = AppState()
        val env = EnvState(
            name = "Staging",
            variables = listOf(
                MutableKeyValue(key = "baseUrl", value = "https://example.com", enabled = true),
            ),
            externalSecrets = listOf(
                MutableExternalSecret(
                    name = "client_id",
                    path = "payment/foo",
                    enabled = true,
                ),
                MutableExternalSecret(
                    name = "client_secret",
                    path = "payment/foo",
                    enabled = true,
                ),
            ),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val vars = getAvailableVariables(state, null)

        assertEquals(3, vars.size)
        // Regular variables first
        assertEquals("baseUrl" to "https://example.com", vars[0])
        // Secret variables formatted as _secret.<name>
        assertEquals("_secret.client_id" to "payment/foo", vars[1])
        assertEquals("_secret.client_secret" to "payment/foo", vars[2])
    }

    @Test
    fun getAvailableVariables_excludes_disabled_external_secrets() {
        val state = AppState()
        val env = EnvState(
            name = "Staging",
            externalSecrets = listOf(
                MutableExternalSecret(name = "enabled_sec", path = "auth/creds", enabled = true),
                MutableExternalSecret(name = "disabled_sec", path = "auth/creds", enabled = false),
            ),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val vars = getAvailableVariables(state, null)

        assertEquals(1, vars.size)
        assertEquals("_secret.enabled_sec" to "auth/creds", vars[0])
    }

    @Test
    fun getAvailableVariables_excludes_blank_or_whitespace_only_names() {
        val state = AppState()
        val env = EnvState(
            name = "Staging",
            externalSecrets = listOf(
                MutableExternalSecret(name = "   ", path = "auth/creds", enabled = true),
                MutableExternalSecret(name = "valid_sec", path = "auth/creds", enabled = true),
            ),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val vars = getAvailableVariables(state, null)

        assertEquals(1, vars.size)
        assertEquals("_secret.valid_sec" to "auth/creds", vars[0])
    }

    @Test
    fun getAvailableVariables_orders_regular_variables_before_secrets() {
        val state = AppState()
        val env = EnvState(
            name = "Dev",
            variables = listOf(
                MutableKeyValue(key = "zoo", value = "1", enabled = true),
                MutableKeyValue(key = "alpha", value = "2", enabled = true),
            ),
            externalSecrets = listOf(
                MutableExternalSecret(name = "beta_secret", path = "kv/beta", enabled = true),
                MutableExternalSecret(name = "alpha_secret", path = "kv/alpha", enabled = true),
            ),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val vars = getAvailableVariables(state, null)

        assertEquals(4, vars.size)
        assertEquals("alpha" to "2", vars[0])
        assertEquals("zoo" to "1", vars[1])
        assertEquals("_secret.alpha_secret" to "kv/alpha", vars[2])
        assertEquals("_secret.beta_secret" to "kv/beta", vars[3])
    }

    @Test
    fun getAvailableVariables_returns_empty_when_no_environment_or_state_is_null() {
        val state = AppState()
        state.selectedEnvIndex = -1

        val varsWithNullEnv = getAvailableVariables(state, null)
        assertTrue(varsWithNullEnv.isEmpty())

        val varsWithNullState = getAvailableVariables(null, null)
        assertTrue(varsWithNullState.isEmpty())
    }
}
