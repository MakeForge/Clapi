package io.github.makeforge.clapi.ui.shared.components

import io.github.makeforge.clapi.core.model.opencollection.Description
import io.github.makeforge.clapi.core.model.opencollection.Environment
import io.github.makeforge.clapi.core.model.opencollection.ExternalSecretType
import io.github.makeforge.clapi.core.model.opencollection.ExternalSecrets
import io.github.makeforge.clapi.core.model.opencollection.KeeperSecurityExternalSecret
import io.github.makeforge.clapi.ui.shared.persistence.ImportExportRepository
import io.github.makeforge.clapi.ui.shared.persistence.toEnvState
import io.github.makeforge.clapi.ui.shared.persistence.toOpenCollectionEnvironment
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.MutableExternalSecret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnvironmentEditorKeeperTest {

    @Test
    fun keeper_security_external_secrets_maps_to_and_from_env_state() {
        val original = Environment(
            name = "Keeper Staging",
            externalSecrets = ExternalSecrets(
                type = "keeper-security-cloud",
                variables = listOf(
                    KeeperSecurityExternalSecret(
                        name = "api_key",
                        recordTitle = "clapi-test-server",
                        targetField = "password",
                        customFilters = mapOf("url" to "https://api.example.com"),
                        description = Description("Staging API password"),
                        disabled = false,
                    ),
                    KeeperSecurityExternalSecret(
                        name = "oauth_client",
                        recordTitle = "clapi-test-custom-client-credentials",
                        targetField = "clientId",
                        disabled = true,
                    ),
                ),
            ),
        )

        val envState = original.toEnvState()

        assertEquals("Keeper Staging", envState.name)
        assertEquals("keeper-security-cloud", envState.externalSecretsProvider)
        assertEquals(2, envState.externalSecrets.size)

        val first = envState.externalSecrets[0]
        assertEquals("api_key", first.name)
        assertEquals("clapi-test-server", first.path)
        assertEquals("password", first.targetField)
        assertEquals("https://api.example.com", first.customFilters["url"])
        assertEquals("Staging API password", first.description)
        assertTrue(first.enabled)

        val second = envState.externalSecrets[1]
        assertEquals("oauth_client", second.name)
        assertEquals("clapi-test-custom-client-credentials", second.path)
        assertEquals("clientId", second.targetField)
        assertFalse(second.enabled)

        // Map back to OpenCollection Environment
        val mappedBack = envState.toOpenCollectionEnvironment()
        assertNotNull(mappedBack.externalSecrets)
        assertEquals(ExternalSecretType.KEEPER_SECURITY_CLOUD, mappedBack.externalSecrets?.type)
        assertEquals(2, mappedBack.externalSecrets?.variables?.size)

        val k1 = mappedBack.externalSecrets!!.variables[0] as KeeperSecurityExternalSecret
        assertEquals("api_key", k1.name)
        assertEquals("clapi-test-server", k1.recordTitle)
        assertEquals("password", k1.targetField)
        assertEquals("https://api.example.com", k1.customFilters["url"])
        assertEquals("Staging API password", k1.description?.content)
        assertNull(k1.disabled)

        val k2 = mappedBack.externalSecrets!!.variables[1] as KeeperSecurityExternalSecret
        assertEquals("oauth_client", k2.name)
        assertEquals("clapi-test-custom-client-credentials", k2.recordTitle)
        assertEquals("clientId", k2.targetField)
        assertEquals(true, k2.disabled)
    }

    @Test
    fun blank_keeper_secret_rows_are_filtered_out() {
        val envState = EnvState(
            name = "TestEnv",
            externalSecretsProvider = "keeper-security-cloud",
            externalSecrets = listOf(
                MutableExternalSecret(name = "   ", path = "clapi-test-user"),
                MutableExternalSecret(name = "user_pass", path = "   "),
                MutableExternalSecret(name = "valid_secret", path = "clapi-test-server", targetField = "password"),
            ),
        )

        val env = envState.toOpenCollectionEnvironment()
        assertNotNull(env.externalSecrets)
        assertEquals(1, env.externalSecrets?.variables?.size)

        val sec = env.externalSecrets!!.variables[0] as KeeperSecurityExternalSecret
        assertEquals("valid_secret", sec.name)
        assertEquals("clapi-test-server", sec.recordTitle)
        assertEquals("password", sec.targetField)
    }

    @Test
    fun default_target_field_is_password_when_blank() {
        val envState = EnvState(
            name = "TestEnv",
            externalSecretsProvider = "keeper-security-cloud",
            externalSecrets = listOf(
                MutableExternalSecret(name = "my_secret", path = "clapi-test-user", targetField = ""),
            ),
        )

        val env = envState.toOpenCollectionEnvironment()
        val sec = env.externalSecrets!!.variables[0] as KeeperSecurityExternalSecret
        assertEquals("password", sec.targetField)
    }

    @Test
    fun import_export_repository_roundtrip_keeper_secrets() {
        val envState = EnvState(
            name = "Keeper Export Test",
            externalSecretsProvider = "keeper-security-cloud",
            externalSecrets = listOf(
                MutableExternalSecret(
                    name = "db_pass",
                    path = "clapi-test-server",
                    targetField = "password",
                    customFilters = mapOf("host" to "db.local"),
                    description = "Database pass",
                    enabled = true,
                ),
            ),
        )

        val exportedJson = ImportExportRepository.exportEnvironmentToString(envState)
        assertTrue(exportedJson.contains("keeper-security-cloud"))
        assertTrue(exportedJson.contains("clapi-test-server"))

        val appState = io.github.makeforge.clapi.ui.shared.state.AppState()
        val importedName = ImportExportRepository.importEnvironmentFromString(appState, exportedJson)
        assertEquals("Keeper Export Test", importedName)
        assertEquals(1, appState.environments.size)

        val importedEnv = appState.environments[0]
        assertEquals("keeper-security-cloud", importedEnv.externalSecretsProvider)
        assertEquals(1, importedEnv.externalSecrets.size)

        val sec = importedEnv.externalSecrets[0]
        assertEquals("db_pass", sec.name)
        assertEquals("clapi-test-server", sec.path)
        assertEquals("password", sec.targetField)
        assertEquals("db.local", sec.customFilters["host"])
        assertEquals("Database pass", sec.description)
        assertTrue(sec.enabled)
    }
}
