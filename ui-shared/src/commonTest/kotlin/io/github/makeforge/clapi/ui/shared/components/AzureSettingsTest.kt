package io.github.makeforge.clapi.ui.shared.components

import io.github.makeforge.clapi.core.model.opencollection.ExternalSecretType
import io.github.makeforge.clapi.ui.shared.persistence.SettingsRepository
import io.github.makeforge.clapi.ui.shared.platform.PlatformStorage
import io.github.makeforge.clapi.ui.shared.state.AppSettings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class AzureSettingsTest {

    @AfterTest
    fun cleanup() {
        PlatformStorage.remove("settings.vaultProviderType")
        PlatformStorage.remove("settings.azureVaultName")
        PlatformStorage.remove("settings.azureTenantId")
        PlatformStorage.remove("settings.azureClientId")
        PlatformStorage.remove("settings.azureClientSecret")
        PlatformStorage.remove("settings.azureAuthorityHost")
        PlatformStorage.remove("settings.azureApiVersion")
    }

    @Test
    fun appSettings_azure_defaults_are_correct() {
        val s = AppSettings()
        assertEquals("", s.azureVaultName)
        assertEquals("", s.azureTenantId)
        assertEquals("", s.azureClientId)
        assertEquals("", s.azureClientSecret)
        assertEquals("https://login.microsoftonline.com", s.azureAuthorityHost)
        assertEquals("7.4", s.azureApiVersion)
    }

    @Test
    fun settings_repository_persists_and_restores_azure_settings() {
        val original = AppSettings().apply {
            vaultProviderType = ExternalSecretType.AZURE_KEY_VAULT
            azureVaultName = "https://prod-vault.vault.azure.net"
            azureTenantId = "11111111-2222-3333-4444-555555555555"
            azureClientId = "client-app-id-xyz"
            azureClientSecret = "top-secret-value-1234"
            azureAuthorityHost = "https://login.microsoftonline.com"
            azureApiVersion = "7.5"
        }

        SettingsRepository.save(original)

        val loaded = AppSettings()
        SettingsRepository.load(loaded)

        assertEquals(ExternalSecretType.AZURE_KEY_VAULT, loaded.vaultProviderType)
        assertEquals("https://prod-vault.vault.azure.net", loaded.azureVaultName)
        assertEquals("11111111-2222-3333-4444-555555555555", loaded.azureTenantId)
        assertEquals("client-app-id-xyz", loaded.azureClientId)
        assertEquals("top-secret-value-1234", loaded.azureClientSecret)
        assertEquals("https://login.microsoftonline.com", loaded.azureAuthorityHost)
        assertEquals("7.5", loaded.azureApiVersion)

        // Verify secret is secured: getSecretString retrieves decrypted value
        assertEquals("top-secret-value-1234", PlatformStorage.getSecretString("settings.azureClientSecret"))
        // Plain getString must not reveal the secret plaintext
        assertNotEquals("top-secret-value-1234", PlatformStorage.getString("settings.azureClientSecret"))
    }
}
