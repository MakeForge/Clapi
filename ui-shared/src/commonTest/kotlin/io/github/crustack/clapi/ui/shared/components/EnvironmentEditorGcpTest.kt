package io.github.crustack.clapi.ui.shared.components

import io.github.crustack.clapi.core.model.opencollection.Description
import io.github.crustack.clapi.core.model.opencollection.Environment
import io.github.crustack.clapi.core.model.opencollection.ExternalSecretType
import io.github.crustack.clapi.core.model.opencollection.ExternalSecrets
import io.github.crustack.clapi.core.model.opencollection.GcpSecretsManagerExternalSecret
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

class EnvironmentEditorGcpTest {

    @Test
    fun gcp_secrets_manager_external_secrets_maps_to_and_from_env_state() {
        val original = Environment(
            name = "GCP Staging",
            externalSecrets = ExternalSecrets(
                type = "gcp-secrets-manager",
                variables = listOf(
                    GcpSecretsManagerExternalSecret(
                        name = "api_key",
                        secretId = "payment-key",
                        version = "latest",
                        targetField = "key",
                        projectId = "my-custom-project",
                        customFilters = mapOf("tier" to "staging"),
                        description = Description("Staging API Key"),
                        disabled = false,
                    ),
                    GcpSecretsManagerExternalSecret(
                        name = "db_pass",
                        secretId = "database-password",
                        version = "1",
                        targetField = "",
                        disabled = true,
                    ),
                ),
            ),
        )

        val envState = original.toEnvState()

        assertEquals("GCP Staging", envState.name)
        assertEquals("gcp-secrets-manager", envState.externalSecretsProvider)
        assertEquals(2, envState.externalSecrets.size)

        val first = envState.externalSecrets[0]
        assertEquals("api_key", first.name)
        assertEquals("payment-key", first.path)
        assertEquals("my-custom-project", first.url)
        assertEquals("key", first.targetField)
        assertEquals("staging", first.customFilters["tier"])
        assertEquals("Staging API Key", first.description)
        assertTrue(first.enabled)

        val second = envState.externalSecrets[1]
        assertEquals("db_pass", second.name)
        assertEquals("database-password", second.path)
        assertEquals("", second.url)
        assertEquals("", second.targetField)
        assertFalse(second.enabled)

        // Map back to OpenCollection Environment
        val mappedBack = envState.toOpenCollectionEnvironment()
        assertNotNull(mappedBack.externalSecrets)
        assertEquals(ExternalSecretType.GCP_SECRETS_MANAGER, mappedBack.externalSecrets?.type)
        assertEquals(2, mappedBack.externalSecrets?.variables?.size)

        val g1 = mappedBack.externalSecrets!!.variables[0] as GcpSecretsManagerExternalSecret
        assertEquals("api_key", g1.name)
        assertEquals("payment-key", g1.secretId)
        assertEquals("my-custom-project", g1.projectId)
        assertEquals("key", g1.targetField)
        assertEquals("staging", g1.customFilters["tier"])
        assertEquals("Staging API Key", g1.description?.content)
        assertNull(g1.disabled)

        val g2 = mappedBack.externalSecrets!!.variables[1] as GcpSecretsManagerExternalSecret
        assertEquals("db_pass", g2.name)
        assertEquals("database-password", g2.secretId)
        assertEquals("", g2.projectId)
        assertEquals("", g2.targetField)
        assertEquals(true, g2.disabled)
    }

    @Test
    fun blank_gcp_secret_rows_are_filtered_out() {
        val envState = EnvState(
            name = "TestEnv",
            externalSecretsProvider = "gcp-secrets-manager",
            externalSecrets = listOf(
                MutableExternalSecret(name = "   ", path = "payment-key"),
                MutableExternalSecret(name = "valid_name", path = "  "),
                MutableExternalSecret(name = "valid_key", path = "valid-secret"),
            ),
        )

        val env = envState.toOpenCollectionEnvironment()
        assertNotNull(env.externalSecrets)
        assertEquals(1, env.externalSecrets?.variables?.size)

        val v = env.externalSecrets!!.variables[0] as GcpSecretsManagerExternalSecret
        assertEquals("valid_key", v.name)
        assertEquals("valid-secret", v.secretId)
    }
}
