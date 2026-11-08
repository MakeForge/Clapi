package io.github.crustack.clapi.ui.shared.components

import io.github.crustack.clapi.core.model.opencollection.Description
import io.github.crustack.clapi.core.model.opencollection.Environment
import io.github.crustack.clapi.core.model.opencollection.ExternalSecrets
import io.github.crustack.clapi.core.model.opencollection.HashicorpVaultExternalSecret
import io.github.crustack.clapi.ui.shared.persistence.toEnvState
import io.github.crustack.clapi.ui.shared.persistence.toOpenCollectionEnvironment
import io.github.crustack.clapi.ui.shared.state.EnvState
import io.github.crustack.clapi.ui.shared.state.MutableExternalSecret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnvironmentEditorSecretsTest {

    @Test
    fun environment_with_external_secrets_maps_to_env_state() {
        val original = Environment(
            name = "Staging",
            externalSecrets = ExternalSecrets(
                type = "hashicorp-vault-server",
                variables = listOf(
                    HashicorpVaultExternalSecret(
                        name = "api_key",
                        path = "secret/data/billing",
                        description = Description("Billing API Key"),
                        disabled = false,
                    ),
                    HashicorpVaultExternalSecret(
                        name = "db_pass",
                        path = "secret/data/database",
                        disabled = true,
                    ),
                ),
            ),
        )

        val envState = original.toEnvState()

        assertEquals("Staging", envState.name)
        assertEquals("hashicorp-vault-server", envState.externalSecretsProvider)
        assertEquals(2, envState.externalSecrets.size)

        val first = envState.externalSecrets[0]
        assertEquals("api_key", first.name)
        assertEquals("secret/data/billing", first.path)
        assertEquals("Billing API Key", first.description)
        assertTrue(first.enabled)

        val second = envState.externalSecrets[1]
        assertEquals("db_pass", second.name)
        assertEquals("secret/data/database", second.path)
        assertEquals("", second.description)
        assertFalse(second.enabled)
    }

    @Test
    fun env_state_with_external_secrets_maps_to_opencollection_environment() {
        val envState = EnvState(
            name = "Production",
            externalSecretsProvider = "hashicorp-vault-server",
            externalSecrets = listOf(
                MutableExternalSecret(
                    name = "client_secret",
                    path = "payment/gateway",
                    description = "OAuth secret",
                    enabled = true,
                ),
                MutableExternalSecret(
                    name = "disabled_secret",
                    path = "payment/gateway",
                    description = "",
                    enabled = false,
                ),
            ),
        )

        val env = envState.toOpenCollectionEnvironment()

        assertNotNull(env.externalSecrets)
        assertEquals("hashicorp-vault-server", env.externalSecrets?.type)
        assertEquals(2, env.externalSecrets?.variables?.size)

        val v1 = env.externalSecrets!!.variables[0] as HashicorpVaultExternalSecret
        assertEquals("client_secret", v1.name)
        assertEquals("payment/gateway", v1.path)
        assertEquals("OAuth secret", v1.description?.content)
        assertNull(v1.disabled)

        val v2 = env.externalSecrets!!.variables[1] as HashicorpVaultExternalSecret
        assertEquals("disabled_secret", v2.name)
        assertEquals("payment/gateway", v2.path)
        assertNull(v2.description)
        assertEquals(true, v2.disabled)
    }

    @Test
    fun blank_secret_rows_are_filtered_out_when_mapping_to_environment() {
        val envState = EnvState(
            name = "TestEnv",
            externalSecrets = listOf(
                MutableExternalSecret(name = "   ", path = "valid/path"),
                MutableExternalSecret(name = "valid_name", path = "  "),
                MutableExternalSecret(name = "valid_key", path = "valid/path"),
            ),
        )

        val env = envState.toOpenCollectionEnvironment()

        assertNotNull(env.externalSecrets)
        assertEquals(1, env.externalSecrets?.variables?.size)
        val v = env.externalSecrets!!.variables[0] as HashicorpVaultExternalSecret
        assertEquals("valid_key", v.name)
        assertEquals("valid/path", v.path)
    }

    @Test
    fun empty_external_secrets_maps_to_null_externalSecrets() {
        val envState = EnvState(name = "EmptyEnv")
        val env = envState.toOpenCollectionEnvironment()
        assertNull(env.externalSecrets)
    }

    @Test
    fun roundtrip_preserves_external_secrets() {
        val original = Environment(
            name = "Roundtrip",
            externalSecrets = ExternalSecrets(
                type = "hashicorp-vault-server",
                variables = listOf(
                    HashicorpVaultExternalSecret(
                        name = "token",
                        path = "app/tokens",
                        description = Description("App token"),
                        disabled = false,
                    )
                ),
            ),
        )

        val roundtrip = original.toEnvState().toOpenCollectionEnvironment()
        assertNotNull(roundtrip.externalSecrets)
        assertEquals(original.externalSecrets?.type, roundtrip.externalSecrets?.type)
        assertEquals(1, roundtrip.externalSecrets?.variables?.size)
        val v = roundtrip.externalSecrets!!.variables[0] as HashicorpVaultExternalSecret
        assertEquals("token", v.name)
        assertEquals("app/tokens", v.path)
        assertEquals("App token", v.description?.content)
    }
}
