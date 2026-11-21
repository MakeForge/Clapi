package io.github.makeforge.clapi.ui.shared.components.executor

import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class Phase1SecurityAndCancellationTest {

    @Test
    fun sharedExecutionClients_returns_same_singleton_instances_across_calls() {
        assertSame(SharedExecutionClients.oauth2TokenClient, SharedExecutionClients.oauth2TokenClient)
        assertSame(SharedExecutionClients.vaultClient, SharedExecutionClients.vaultClient)
        assertSame(SharedExecutionClients.awsSecretsClient, SharedExecutionClients.awsSecretsClient)
        assertSame(SharedExecutionClients.gcpSecretsClient, SharedExecutionClients.gcpSecretsClient)
        assertSame(SharedExecutionClients.azureKeyVaultClient, SharedExecutionClients.azureKeyVaultClient)
        assertSame(SharedExecutionClients.secretResolutionService, SharedExecutionClients.secretResolutionService)
    }

    @Test
    fun secretLogRedactor_masks_secrets_of_length_3_or_more_sorted_by_descending_length() {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        val env = EnvState(name = "Prod").apply {
            variables.add(MutableKeyValue(key = "api_key", value = "sk-live-123456", enabled = true, secret = true))
            variables.add(MutableKeyValue(key = "short_secret", value = "ab", enabled = true, secret = true))
            variables.add(MutableKeyValue(key = "public_var", value = "visible-value", enabled = true, secret = false))
        }
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val resolvedSecrets = mapOf(
            "_secret.vault_token" to "sk-live-1234567890",
            "_secret.db_pass" to "mySecretP@ss",
        )

        val collected = SecretLogRedactor.collectSecretValues(
            state = state,
            resolvedSecrets = resolvedSecrets,
        )

        assertTrue("sk-live-123456" in collected)
        assertTrue("sk-live-1234567890" in collected)
        assertTrue("mySecretP@ss" in collected)
        assertFalse("ab" in collected, "Secrets shorter than 3 chars must not be masked")
        assertFalse("visible-value" in collected, "Non-secret variables must not be masked")

        val rawUrl = "https://api.example.com/v1/items?token=sk-live-1234567890&fallback=sk-live-123456&pass=mySecretP@ss&pub=visible-value"
        val redacted = SecretLogRedactor.redact(rawUrl, collected)

        assertEquals(
            "https://api.example.com/v1/items?token=••••••••&fallback=••••••••&pass=••••••••&pub=visible-value",
            redacted,
        )
    }

    @Test
    fun closeTab_and_closeTabsByIds_cancel_inFlight_currentJob_and_reset_isLoading() = runTest {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        state.addTab()
        state.addTab()
        state.addTab()

        val tab0 = state.openTabs[0]
        val tab1 = state.openTabs[1]
        val tab2 = state.openTabs[2]

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gate = CompletableDeferred<Unit>()

        val job0 = scope.launch { gate.await() }
        val job1 = scope.launch { gate.await() }
        val job2 = scope.launch { gate.await() }

        tab0.currentJob = job0
        tab0.isLoading = true
        tab1.currentJob = job1
        tab1.isLoading = true
        tab2.currentJob = job2
        tab2.isLoading = true

        // Close single tab
        state.closeTab(0)
        assertTrue(job0.isCancelled, "closeTab must cancel tab0.currentJob")
        assertNull(tab0.currentJob)
        assertFalse(tab0.isLoading)

        // Close multiple tabs by ID
        state.closeTabsByIds(listOf(tab1.id, tab2.id))
        assertTrue(job1.isCancelled, "closeTabsByIds must cancel tab1.currentJob")
        assertTrue(job2.isCancelled, "closeTabsByIds must cancel tab2.currentJob")
        assertNull(tab1.currentJob)
        assertNull(tab2.currentJob)
        assertFalse(tab1.isLoading)
        assertFalse(tab2.isLoading)
    }

    @Test
    fun resolveExternalSecretsForTab_rethrows_CancellationException() = runTest {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        state.addTab()
        val tab = state.activeTab!!
        val env = EnvState(name = "TestEnv").apply {
            variables.add(MutableKeyValue(key = "sec_var", value = "", enabled = true, secret = true))
        }
        state.environments.add(env)
        state.selectedEnvIndex = 0

        assertFailsWith<CancellationException> {
            resolveExternalSecretsForTab(
                state = state,
                tab = tab,
                textsToScan = listOf("{{sec_var}}"),
                processEnvProvider = { throw CancellationException("User cancelled secret resolution") },
            )
        }
    }
}
