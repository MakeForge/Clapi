package io.github.crustack.clapi.ui.shared.components

import io.github.crustack.clapi.core.model.opencollection.Description
import io.github.crustack.clapi.core.model.opencollection.Environment
import io.github.crustack.clapi.core.model.opencollection.ExternalSecretType
import io.github.crustack.clapi.core.model.opencollection.ExternalSecrets
import io.github.crustack.clapi.core.model.opencollection.HashicorpVaultExternalSecret
import io.github.crustack.clapi.core.model.opencollection.KeePassXcExternalSecret
import io.github.crustack.clapi.core.model.opencollection.AwsSecretsManagerExternalSecret
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
        assertEquals(ExternalSecretType.HASHICORP_VAULT_SERVER, env.externalSecrets?.type)
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

    @Test
    fun keepassxc_external_secrets_maps_to_and_from_env_state() {
        val original = Environment(
            name = "KeePassEnv",
            externalSecrets = ExternalSecrets(
                type = "keepassxc",
                variables = listOf(
                    KeePassXcExternalSecret(
                        name = "api_key",
                        url = "https://api.gateway.internal",
                        targetField = "password",
                        customFilters = mapOf("login" to "admin_user", "env" to "prod"),
                        description = Description("Admin Gateway Secret"),
                        disabled = false,
                    ),
                    KeePassXcExternalSecret(
                        name = "db_user",
                        url = "https://db.internal",
                        targetField = "login",
                        disabled = true,
                    ),
                ),
            ),
        )

        val envState = original.toEnvState()
        assertEquals("keepassxc", envState.externalSecretsProvider)
        assertEquals(2, envState.externalSecrets.size)

        val first = envState.externalSecrets[0]
        assertEquals("api_key", first.name)
        assertEquals("https://api.gateway.internal", first.url)
        assertEquals("password", first.targetField)
        assertEquals("admin_user", first.customFilters["login"])
        assertEquals("prod", first.customFilters["env"])
        assertEquals("Admin Gateway Secret", first.description)
        assertTrue(first.enabled)

        val second = envState.externalSecrets[1]
        assertEquals("db_user", second.name)
        assertEquals("https://db.internal", second.url)
        assertEquals("login", second.targetField)
        assertFalse(second.enabled)

        // Roundtrip back to Environment
        val mappedBack = envState.toOpenCollectionEnvironment()
        assertNotNull(mappedBack.externalSecrets)
        assertEquals(ExternalSecretType.KEEPASSXC, mappedBack.externalSecrets?.type)
        assertEquals(2, mappedBack.externalSecrets?.variables?.size)

        val k1 = mappedBack.externalSecrets!!.variables[0] as KeePassXcExternalSecret
        assertEquals("api_key", k1.name)
        assertEquals("https://api.gateway.internal", k1.url)
        assertEquals("password", k1.targetField)
        assertEquals("admin_user", k1.customFilters["login"])
        assertNull(k1.disabled)

        val k2 = mappedBack.externalSecrets!!.variables[1] as KeePassXcExternalSecret
        assertEquals("db_user", k2.name)
        assertEquals("https://db.internal", k2.url)
        assertEquals("login", k2.targetField)
        assertEquals(true, k2.disabled)
    }

    @Test
    fun mutable_external_secret_custom_filter_list_allows_key_renaming_and_syncing() {
        val secret = MutableExternalSecret(
            name = "test_sec",
            url = "https://example.com",
            customFilters = mapOf("initialKey" to "initialVal"),
        )

        assertEquals(1, secret.customFilterList.size)
        assertEquals("initialKey", secret.customFilterList[0].key)
        assertEquals("initialVal", secret.customFilterList[0].value)

        // Edit key name
        secret.customFilterList[0].key = "renamedKey"
        secret.syncCustomFiltersFromList()

        assertFalse(secret.customFilters.containsKey("initialKey"))
        assertTrue(secret.customFilters.containsKey("renamedKey"))
        assertEquals("initialVal", secret.customFilters["renamedKey"])

        // Add new filter
        secret.customFilterList.add(io.github.crustack.clapi.ui.shared.state.MutableKeyValue(key = "newField", value = "newVal"))
        secret.syncCustomFiltersFromList()

        assertEquals(2, secret.customFilters.size)
        assertEquals("newVal", secret.customFilters["newField"])

        // Remove filter
        secret.customFilterList.removeAt(0)
        secret.syncCustomFiltersFromList()

        assertEquals(1, secret.customFilters.size)
        assertFalse(secret.customFilters.containsKey("renamedKey"))
        assertEquals("newVal", secret.customFilters["newField"])
    }

    @Test
    fun excluded_variables_filters_out_currently_edited_secret_and_variable() {
        val allVars = listOf(
            "api_host" to "localhost",
            "api_port" to "8080",
            "_secret.my_keepass_secret" to "https://entry.url",
            "_secret.other_secret" to "secret/path",
        )

        // Excluding the currently edited secret
        val secretExcludes = setOf("my_keepass_secret", "_secret.my_keepass_secret")
            .flatMap { listOf(it.trim(), it.trim().removePrefix("_secret."), "_secret.${it.trim().removePrefix("_secret.")}") }
            .toSet()

        val filteredForSecret = allVars.filter { (name, _) ->
            val trimmed = name.trim()
            trimmed !in secretExcludes && trimmed.removePrefix("_secret.") !in secretExcludes
        }

        assertEquals(3, filteredForSecret.size)
        assertFalse(filteredForSecret.any { it.first == "_secret.my_keepass_secret" })
        assertTrue(filteredForSecret.any { it.first == "_secret.other_secret" })
        assertTrue(filteredForSecret.any { it.first == "api_host" })

        // Excluding the currently edited variable
        val varExcludes = setOf("api_host")
            .flatMap { listOf(it.trim(), it.trim().removePrefix("_secret."), "_secret.${it.trim().removePrefix("_secret.")}") }
            .toSet()

        val filteredForVar = allVars.filter { (name, _) ->
            val trimmed = name.trim()
            trimmed !in varExcludes && trimmed.removePrefix("_secret.") !in varExcludes
        }

        assertEquals(3, filteredForVar.size)
        assertFalse(filteredForVar.any { it.first == "api_host" })
        assertTrue(filteredForVar.any { it.first == "api_port" })
        assertTrue(filteredForVar.any { it.first == "_secret.my_keepass_secret" })
    }

    @Test
    fun environment_with_aws_external_secrets_maps_to_env_state_and_back() {
        val original = Environment(
            name = "AWS Staging",
            externalSecrets = ExternalSecrets(
                type = "aws-secrets-manager",
                variables = listOf(
                    AwsSecretsManagerExternalSecret(
                        name = "gateway_id",
                        secretName = "local/payment/foo",
                        targetField = "client_id",
                        customFilters = mapOf("tier" to "staging"),
                        description = Description("Staging Client ID"),
                        disabled = false,
                    ),
                    AwsSecretsManagerExternalSecret(
                        name = "gateway_secret",
                        secretName = "local/payment/foo",
                        targetField = "client_secret",
                        disabled = true,
                    ),
                ),
            ),
        )

        val envState = original.toEnvState()

        assertEquals("AWS Staging", envState.name)
        assertEquals("aws-secrets-manager", envState.externalSecretsProvider)
        assertEquals(2, envState.externalSecrets.size)

        val first = envState.externalSecrets[0]
        assertEquals("gateway_id", first.name)
        assertEquals("local/payment/foo", first.path)
        assertEquals("client_id", first.targetField)
        assertEquals("staging", first.customFilters["tier"])
        assertEquals("Staging Client ID", first.description)
        assertTrue(first.enabled)

        val second = envState.externalSecrets[1]
        assertEquals("gateway_secret", second.name)
        assertEquals("local/payment/foo", second.path)
        assertEquals("client_secret", second.targetField)
        assertFalse(second.enabled)

        // Map back to OpenCollection Environment
        val mappedBack = envState.toOpenCollectionEnvironment()
        assertNotNull(mappedBack.externalSecrets)
        assertEquals(ExternalSecretType.AWS_SECRETS_MANAGER, mappedBack.externalSecrets?.type)
        assertEquals(2, mappedBack.externalSecrets?.variables?.size)

        val a1 = mappedBack.externalSecrets!!.variables[0] as AwsSecretsManagerExternalSecret
        assertEquals("gateway_id", a1.name)
        assertEquals("local/payment/foo", a1.secretName)
        assertEquals("client_id", a1.targetField)
        assertEquals("staging", a1.customFilters["tier"])
        assertEquals("Staging Client ID", a1.description?.content)
        assertNull(a1.disabled)

        val a2 = mappedBack.externalSecrets!!.variables[1] as AwsSecretsManagerExternalSecret
        assertEquals("gateway_secret", a2.name)
        assertEquals("local/payment/foo", a2.secretName)
        assertEquals("client_secret", a2.targetField)
        assertEquals(true, a2.disabled)
    }
}
