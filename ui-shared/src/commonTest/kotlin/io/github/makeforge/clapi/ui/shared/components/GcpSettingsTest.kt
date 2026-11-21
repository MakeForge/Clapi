package io.github.makeforge.clapi.ui.shared.components

import io.github.makeforge.clapi.core.model.opencollection.ExternalSecretType
import io.github.makeforge.clapi.core.network.gcp.GcpAuthMethod
import io.github.makeforge.clapi.ui.shared.persistence.SettingsRepository
import io.github.makeforge.clapi.ui.shared.platform.PlatformStorage
import io.github.makeforge.clapi.ui.shared.state.AppSettings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class GcpSettingsTest {

    @AfterTest
    fun cleanup() {
        PlatformStorage.remove("settings.vaultProviderType")
        PlatformStorage.remove("settings.gcpProjectId")
        PlatformStorage.remove("settings.gcpAccessToken")
        PlatformStorage.remove("settings.gcpEndpointUrl")
        PlatformStorage.remove("settings.gcpAuthMethod")
        PlatformStorage.remove("settings.gcpServiceAccountJson")
        PlatformStorage.remove("settings.gcpAdcFilePath")
    }

    @Test
    fun appSettings_gcp_defaults_are_correct() {
        val s = AppSettings()
        assertEquals("", s.gcpProjectId)
        assertEquals("", s.gcpAccessToken)
        assertEquals(GcpAuthMethod.SERVICE_ACCOUNT_KEY, s.gcpAuthMethod)
        assertEquals("", s.gcpServiceAccountKey)
        assertEquals("", s.gcpAdcFilePath)
        assertEquals("https://secretmanager.googleapis.com", s.gcpEndpointUrl)
    }

    @Test
    fun settings_repository_persists_and_restores_gcp_settings() {
        val original = AppSettings().apply {
            vaultProviderType = ExternalSecretType.GCP_SECRETS_MANAGER
            gcpProjectId = "my-test-project"
            gcpAccessToken = "gcp-secret-token-xyz-987"
            gcpAuthMethod = GcpAuthMethod.APPLICATION_DEFAULT_CREDENTIALS
            gcpServiceAccountKey = "{\"type\": \"service_account\", \"project_id\": \"proj-123\"}"
            gcpAdcFilePath = "/custom/path/application_default_credentials.json"
            gcpEndpointUrl = "http://localhost:8085"
        }

        SettingsRepository.save(original)

        val loaded = AppSettings()
        SettingsRepository.load(loaded)

        assertEquals(ExternalSecretType.GCP_SECRETS_MANAGER, loaded.vaultProviderType)
        assertEquals("my-test-project", loaded.gcpProjectId)
        assertEquals("gcp-secret-token-xyz-987", loaded.gcpAccessToken)
        assertEquals(GcpAuthMethod.APPLICATION_DEFAULT_CREDENTIALS, loaded.gcpAuthMethod)
        assertEquals("{\"type\": \"service_account\", \"project_id\": \"proj-123\"}", loaded.gcpServiceAccountKey)
        assertEquals("/custom/path/application_default_credentials.json", loaded.gcpAdcFilePath)
        assertEquals("http://localhost:8085", loaded.gcpEndpointUrl)

        // Verify secret is secured: getSecretString retrieves decrypted value
        assertEquals("gcp-secret-token-xyz-987", PlatformStorage.getSecretString("settings.gcpAccessToken"))
        // Plain getString must not reveal the secret plaintext
        assertNotEquals("gcp-secret-token-xyz-987", PlatformStorage.getString("settings.gcpAccessToken"))

        // Verify service account key is secured
        assertEquals("{\"type\": \"service_account\", \"project_id\": \"proj-123\"}", PlatformStorage.getSecretString("settings.gcpServiceAccountJson"))
        assertNotEquals("{\"type\": \"service_account\", \"project_id\": \"proj-123\"}", PlatformStorage.getString("settings.gcpServiceAccountJson"))
    }
}
