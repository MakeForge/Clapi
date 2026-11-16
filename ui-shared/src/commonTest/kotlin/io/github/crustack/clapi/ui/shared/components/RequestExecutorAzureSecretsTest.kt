package io.github.crustack.clapi.ui.shared.components

import io.github.crustack.clapi.core.network.azure.AzureKeyVaultClient
import io.github.crustack.clapi.core.network.azure.AzureKeyVaultConfig
import io.github.crustack.clapi.ui.shared.components.executor.resolveExternalSecretsForTab
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.EnvState
import io.github.crustack.clapi.ui.shared.state.MutableExternalSecret
import io.github.crustack.clapi.ui.shared.state.MutableKeyValue
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestExecutorAzureSecretsTest {

    private class FakeAzureClient(
        private val secrets: Map<String, Map<String, String>>,
    ) : AzureKeyVaultClient {
        val calls = mutableListOf<String>()

        override suspend fun fetchSecret(config: AzureKeyVaultConfig): Map<String, String> {
            calls.add(config.secretName)
            return secrets[config.secretName]
                ?: throw IllegalStateException("External secret not found in Azure Key Vault: '${config.secretName}'")
        }
    }

    @Test
    fun resolveExternalSecretsForTab_resolves_azure_secrets_and_interpolates_filter_variables() = runTest {
        val fakeAzure = FakeAzureClient(
            mapOf(
                "payment-foo" to mapOf(
                    "stage" to "sandbox",
                    "client_secret" to "live_secret_azure_999",
                )
            )
        )

        val state = AppState()
        state.settings.azureVaultName = "https://my-vault.vault.azure.net"
        state.settings.azureTenantId = "tenant-1"
        state.settings.azureClientId = "client-1"
        state.settings.azureClientSecret = "secret-1"

        val env = EnvState(
            name = "AzureEnv",
            externalSecretsProvider = "azure-key-vault",
            variables = listOf(
                MutableKeyValue(key = "stage", value = "sandbox", enabled = true),
            ),
            externalSecrets = listOf(
                MutableExternalSecret(
                    name = "payment_key",
                    path = "payment-foo",
                    targetField = "client_secret",
                    customFilters = mapOf("stage" to "{{stage}}"),
                    enabled = true,
                )
            )
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(url = "https://example.com/charge?token={{_secret.payment_key}}")
        state.openTabs.add(tab)
        state.activeTabIndex = 0

        val resolved = resolveExternalSecretsForTab(
            state = state,
            tab = tab,
            textsToScan = listOf(tab.url),
            azureClient = fakeAzure,
        )

        assertNotNull(resolved)
        assertEquals("live_secret_azure_999", resolved["_secret.payment_key"])
        assertEquals(1, fakeAzure.calls.size)
    }

    @Test
    fun resolveExternalSecretsForTab_sets_error_when_azure_credentials_missing() = runTest {
        val state = AppState()
        // No Azure credentials set
        val env = EnvState(
            name = "AzureEnv",
            externalSecretsProvider = "azure-key-vault",
            externalSecrets = listOf(
                MutableExternalSecret(
                    name = "payment_key",
                    path = "payment-foo",
                    enabled = true,
                )
            )
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(url = "https://example.com/charge?token={{_secret.payment_key}}")
        state.openTabs.add(tab)
        state.activeTabIndex = 0

        val resolved = resolveExternalSecretsForTab(
            state = state,
            tab = tab,
            textsToScan = listOf(tab.url),
        )

        assertNull(resolved)
        assertNotNull(tab.lastError)
        assertTrue(tab.lastError!!.contains("Azure Key Vault Name is not configured"))
    }

    @Test
    fun resolveExternalSecretsForTab_sets_error_when_azure_secret_target_field_missing() = runTest {
        val fakeAzure = FakeAzureClient(
            mapOf(
                "payment-foo" to mapOf(
                    "client_id" to "123",
                )
            )
        )

        val state = AppState()
        state.settings.azureVaultName = "https://my-vault.vault.azure.net"
        state.settings.azureTenantId = "tenant-1"
        state.settings.azureClientId = "client-1"
        state.settings.azureClientSecret = "secret-1"

        val env = EnvState(
            name = "AzureEnv",
            externalSecretsProvider = "azure-key-vault",
            externalSecrets = listOf(
                MutableExternalSecret(
                    name = "payment_key",
                    path = "payment-foo",
                    targetField = "missing_secret_field",
                    enabled = true,
                )
            )
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(url = "https://example.com/charge?token={{_secret.payment_key}}")
        state.openTabs.add(tab)
        state.activeTabIndex = 0

        val resolved = resolveExternalSecretsForTab(
            state = state,
            tab = tab,
            textsToScan = listOf(tab.url),
            azureClient = fakeAzure,
        )

        assertNull(resolved)
        assertNotNull(tab.lastError)
        assertTrue(tab.lastError!!.contains("Field 'missing_secret_field' not found in Azure Key Vault secret"))
    }
}
