package io.github.crustack.clapi.ui.shared.components

import io.github.crustack.clapi.core.network.keeper.KeeperClient
import io.github.crustack.clapi.core.network.keeper.KeeperConnectionStatusState
import io.github.crustack.clapi.core.network.keeper.KeeperRecordFieldSummary
import io.github.crustack.clapi.core.network.keeper.KeeperRecordSummary
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

class RequestExecutorKeeperSecretsTest {

    private class FakeKeeperClient(
        private val records: List<KeeperRecordSummary>,
    ) : KeeperClient {
        val calls = mutableListOf<List<String>>()

        override suspend fun checkStatus(): KeeperConnectionStatusState = KeeperConnectionStatusState.Connected
        override suspend fun testConnection(): Boolean = true
        override suspend fun getRecords(recordsFilter: List<String>): List<KeeperRecordSummary> {
            calls.add(recordsFilter)
            return records
        }
        override suspend fun countMatchingRecords(recordTitle: String, customFilters: Map<String, String>): Int = records.size
        override suspend fun disconnect() {}
    }

    @Test
    fun resolveExternalSecretsForTab_resolves_keeper_secrets_and_interpolates_filter_variables() = runTest {
        val fakeKeeper = FakeKeeperClient(
            listOf(
                KeeperRecordSummary(
                    recordUid = "uid-123",
                    title = "clapi-test-server",
                    type = "serverCredentials",
                    password = "live_keeper_pass_777",
                    fields = listOf(
                        KeeperRecordFieldSummary(type = "login", values = listOf("admin")),
                        KeeperRecordFieldSummary(type = "url", values = listOf("https://qa.example.com")),
                    ),
                )
            )
        )

        val state = AppState()
        state.settings.keeperConfigJson = """{"hostname":"keepersecurity.eu"}"""

        val env = EnvState(
            name = "KeeperEnv",
            externalSecretsProvider = "keeper-security-cloud",
            variables = listOf(
                MutableKeyValue(key = "target_host", value = "qa.example.com", enabled = true),
            ),
            externalSecrets = listOf(
                MutableExternalSecret(
                    name = "server_pass",
                    path = "clapi-test-server",
                    targetField = "password",
                    customFilters = mapOf("url" to "https://{{target_host}}"),
                    enabled = true,
                )
            )
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(url = "https://example.com/api?pass={{_secret.server_pass}}")
        state.openTabs.add(tab)
        state.activeTabIndex = 0

        val resolved = resolveExternalSecretsForTab(
            state = state,
            tab = tab,
            textsToScan = listOf(tab.url),
            keeperClient = fakeKeeper,
        )

        assertNotNull(resolved)
        assertEquals("live_keeper_pass_777", resolved["_secret.server_pass"])
        assertEquals(1, fakeKeeper.calls.size)
    }

    @Test
    fun resolveExternalSecretsForTab_sets_error_when_keeper_config_missing() = runTest {
        val state = AppState()
        // No keeper configuration in settings
        val env = EnvState(
            name = "KeeperEnv",
            externalSecretsProvider = "keeper-security-cloud",
            externalSecrets = listOf(
                MutableExternalSecret(
                    name = "server_pass",
                    path = "clapi-test-server",
                    targetField = "password",
                    enabled = true,
                )
            )
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(url = "https://example.com/api?pass={{_secret.server_pass}}")
        state.openTabs.add(tab)
        state.activeTabIndex = 0

        val resolved = resolveExternalSecretsForTab(
            state = state,
            tab = tab,
            textsToScan = listOf(tab.url),
        )

        assertNull(resolved)
        assertNotNull(tab.lastError)
        assertTrue(tab.lastError!!.contains("Keeper Secrets Manager configuration is missing"))
    }
}
