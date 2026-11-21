package io.github.makeforge.clapi.ui.shared.components

import io.github.makeforge.clapi.core.network.gcp.GcpSecretManagerClient
import io.github.makeforge.clapi.core.network.gcp.GcpSecretManagerConfig
import io.github.makeforge.clapi.ui.shared.components.executor.resolveExternalSecretsForTab
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.MutableExternalSecret
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestExecutorGcpSecretsTest {

    private class FakeGcpClient(
        private val secrets: Map<String, Map<String, String>>,
    ) : GcpSecretManagerClient {
        val calls = mutableListOf<String>()

        override suspend fun fetchSecret(config: GcpSecretManagerConfig): Map<String, String> {
            calls.add(config.secretId)
            return secrets[config.secretId]
                ?: throw IllegalStateException("External secret not found in GCP Secret Manager: '${config.secretId}'")
        }
    }

    @Test
    fun resolveExternalSecretsForTab_resolves_gcp_secrets_and_interpolates_filter_variables() = runTest {
        val fakeGcp = FakeGcpClient(
            mapOf(
                "payment-foo" to mapOf(
                    "stage" to "sandbox",
                    "client_secret" to "live_secret_gcp_999",
                )
            )
        )

        val state = AppState()
        state.settings.gcpProjectId = "my-test-project"
        state.settings.gcpAccessToken = "test-token"

        val env = EnvState(
            name = "GcpEnv",
            externalSecretsProvider = "gcp-secrets-manager",
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
            gcpClient = fakeGcp,
        )

        assertNotNull(resolved)
        assertEquals("live_secret_gcp_999", resolved["_secret.payment_key"])
        assertEquals(1, fakeGcp.calls.size)
    }

    @Test
    fun resolveExternalSecretsForTab_sets_error_when_gcp_project_id_missing() = runTest {
        val state = AppState()
        // No GCP project ID configured
        val env = EnvState(
            name = "GcpEnv",
            externalSecretsProvider = "gcp-secrets-manager",
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
        assertTrue(tab.lastError!!.contains("GCP Project ID is not configured"))
    }

    @Test
    fun resolveExternalSecretsForTab_sets_error_when_gcp_secret_target_field_missing() = runTest {
        val fakeGcp = FakeGcpClient(
            mapOf(
                "payment-foo" to mapOf(
                    "client_id" to "123",
                )
            )
        )

        val state = AppState()
        state.settings.gcpProjectId = "my-test-project"
        state.settings.gcpAccessToken = "test-token"

        val env = EnvState(
            name = "GcpEnv",
            externalSecretsProvider = "gcp-secrets-manager",
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
            gcpClient = fakeGcp,
        )

        assertNull(resolved)
        assertNotNull(tab.lastError)
        assertTrue(tab.lastError!!.contains("Field 'missing_secret_field' not found in GCP Secret Manager secret"))
    }
}
