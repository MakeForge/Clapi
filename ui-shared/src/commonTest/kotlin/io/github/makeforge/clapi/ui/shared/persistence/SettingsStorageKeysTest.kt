package io.github.makeforge.clapi.ui.shared.persistence

import io.github.makeforge.clapi.core.network.vault.HashiCorpVaultClient
import io.github.makeforge.clapi.core.network.vault.SecretResolutionService
import io.github.makeforge.clapi.ui.shared.components.executor.resolveExternalSecretsForTab
import io.github.makeforge.clapi.ui.shared.platform.PlatformStorage
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.MutableExternalSecret
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SettingsStorageKeysTest {

    @AfterTest
    fun tearDown() {
        PlatformStorage.remove(SettingsStorageKeys.VAULT_TOKEN)
        PlatformStorage.remove(SettingsStorageKeys.LEGACY_VAULT_TOKEN)
        PlatformStorage.remove(SettingsStorageKeys.AWS_SECRET_ACCESS_KEY)
        PlatformStorage.remove(SettingsStorageKeys.LEGACY_AWS_SECRET_ACCESS_KEY)
    }

    @Test
    fun getSecretWithFallback_reads_canonical_settings_keys_and_falls_back_to_legacy_clapi_settings_keys() {
        PlatformStorage.remove(SettingsStorageKeys.VAULT_TOKEN)
        PlatformStorage.putSecretString(SettingsStorageKeys.LEGACY_VAULT_TOKEN, "legacy-vault-tok")

        assertEquals(
            "legacy-vault-tok",
            SettingsStorageKeys.getSecretWithFallback(SettingsStorageKeys.VAULT_TOKEN),
        )

        PlatformStorage.putSecretString(SettingsStorageKeys.VAULT_TOKEN, "canonical-vault-tok")
        assertEquals(
            "canonical-vault-tok",
            SettingsStorageKeys.getSecretWithFallback(SettingsStorageKeys.VAULT_TOKEN),
        )
    }

    @Test
    fun resolveExternalSecretsForTab_reads_vault_token_persisted_by_SettingsRepository() = runTest {
        val state = AppState(withDemoData = false)
        state.settings.vaultServerUrl = "http://127.0.0.1:8200"
        state.settings.vaultToken = "persisted-vault-token-123"
        SettingsRepository.save(state.settings)

        // Simulate runtime state where in-memory settings.vaultToken is blank and fallback lookup is triggered
        state.settings.vaultToken = ""

        val env = EnvState(
            name = "VaultEnv",
            externalSecretsProvider = "hashicorp-vault-server",
            externalSecrets = listOf(
                MutableExternalSecret(name = "db_pass", path = "secret/db", targetField = "password", enabled = true),
            ),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        var capturedToken: String? = null
        val fakeVault = object : HashiCorpVaultClient {
            override suspend fun fetchSecret(
                vaultUrl: String,
                token: String,
                secretPath: String,
                mountPath: String,
                namespace: String?,
            ): Map<String, String> {
                capturedToken = token
                return mapOf("password" to "resolved-db-pass")
            }
        }

        val tab = RequestTabState(url = "https://example.com?p={{_secret.db_pass}}")
        val resolved = resolveExternalSecretsForTab(
            state = state,
            tab = tab,
            textsToScan = listOf(tab.url),
            secretResolutionService = SecretResolutionService(vaultClient = fakeVault),
        )

        assertNotNull(resolved)
        assertEquals("persisted-vault-token-123", capturedToken)
        assertEquals("resolved-db-pass", resolved["_secret.db_pass"])
    }
}
