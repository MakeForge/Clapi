package io.github.makeforge.clapi.ui.shared.components

import io.github.makeforge.clapi.core.model.opencollection.ExternalSecretType
import io.github.makeforge.clapi.ui.shared.persistence.SettingsRepository
import io.github.makeforge.clapi.ui.shared.platform.PlatformStorage
import io.github.makeforge.clapi.ui.shared.state.AppSettings
import io.github.makeforge.clapi.ui.shared.state.KeeperConnectionStatus
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class KeeperSettingsTest {

    @AfterTest
    fun cleanup() {
        PlatformStorage.remove("settings.vaultProviderType")
        PlatformStorage.remove("settings.keeperConfig")
    }

    @Test
    fun appSettings_keeper_defaults_are_correct() {
        val s = AppSettings()
        assertEquals("", s.keeperConfigJson)
        assertEquals(KeeperConnectionStatus.UNKNOWN, s.keeperConnectionStatus)
    }

    @Test
    fun settings_repository_persists_and_restores_keeper_settings() {
        val fakeConfig = """
            {
                "hostname": "keepersecurity.eu",
                "clientId": "test-client-id-123",
                "privateKey": "test-private-key-abc",
                "appKey": "test-app-key-xyz"
            }
        """.trimIndent()

        val original = AppSettings().apply {
            vaultProviderType = ExternalSecretType.KEEPER_SECURITY_CLOUD
            keeperConfigJson = fakeConfig
        }

        SettingsRepository.save(original)

        val loaded = AppSettings()
        SettingsRepository.load(loaded)

        assertEquals(ExternalSecretType.KEEPER_SECURITY_CLOUD, loaded.vaultProviderType)
        assertEquals(fakeConfig, loaded.keeperConfigJson)

        // Verify secret is secured: getSecretString retrieves decrypted value
        assertEquals(fakeConfig, PlatformStorage.getSecretString("settings.keeperConfig"))
        // Plain getString must not reveal the secret plaintext
        assertNotEquals(fakeConfig, PlatformStorage.getString("settings.keeperConfig"))

        // Verify one-time token is never stored in PlatformStorage
        assertNull(PlatformStorage.getString("settings.oneTimeToken"))
        assertNull(PlatformStorage.getSecretString("settings.oneTimeToken"))
    }
}
