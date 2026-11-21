package io.github.makeforge.clapi.ui.desktop.persistence

import io.github.makeforge.clapi.core.model.opencollection.ExternalSecretType
import io.github.makeforge.clapi.ui.shared.persistence.SettingsRepository
import io.github.makeforge.clapi.ui.shared.platform.PlatformStorage
import io.github.makeforge.clapi.ui.shared.state.AppSettings
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class SettingsRepositorySecretsTest {

    private val vaultKeys = listOf(
        "settings.vaultProviderType",
        "settings.vaultServerUrl",
        "settings.vaultMountPath",
        "settings.vaultNamespace",
        "settings.vaultToken",
        "settings.awsRegion",
        "settings.awsAccessKeyId",
        "settings.awsSecretAccessKey",
        "settings.awsSessionToken",
        "settings.awsEndpointUrl",
        "settings.awsVersionStage",
    )

    @Before
    fun setUp() {
        vaultKeys.forEach { PlatformStorage.remove(it) }
    }

    @After
    fun tearDown() {
        vaultKeys.forEach { PlatformStorage.remove(it) }
    }

    @Test
    fun default_vault_settings_are_populated_on_empty_storage() {
        val settings = AppSettings()
        SettingsRepository.load(settings)

        assertEquals(ExternalSecretType.HASHICORP_VAULT_SERVER, settings.vaultProviderType)
        assertEquals("", settings.vaultServerUrl)
        assertEquals("secret", settings.vaultMountPath)
        assertEquals("", settings.vaultNamespace)
        assertEquals("", settings.vaultToken)
    }

    @Test
    fun save_and_load_roundtrips_vault_settings() {
        val original = AppSettings().apply {
            vaultProviderType = ExternalSecretType.HASHICORP_VAULT_SERVER
            vaultServerUrl = "https://vault.corp.internal:8200"
            vaultMountPath = "custom-secrets"
            vaultNamespace = "finance/billing"
            vaultToken = "hvs.CAESIJ7..."
        }

        SettingsRepository.save(original)

        val restored = AppSettings()
        SettingsRepository.load(restored)

        assertEquals(original.vaultProviderType, restored.vaultProviderType)
        assertEquals(original.vaultServerUrl, restored.vaultServerUrl)
        assertEquals(original.vaultMountPath, restored.vaultMountPath)
        assertEquals(original.vaultNamespace, restored.vaultNamespace)
        assertEquals(original.vaultToken, restored.vaultToken)
    }

    @Test
    fun vault_token_is_persisted_in_encrypted_storage_not_plain_text() {
        val sensitiveToken = "hvs.super-secret-vault-token-xyz"
        val settings = AppSettings().apply {
            vaultServerUrl = "http://127.0.0.1:8200"
            vaultToken = sensitiveToken
        }

        SettingsRepository.save(settings)

        // Sensitive token must be retrievable from getSecretString
        assertEquals(sensitiveToken, PlatformStorage.getSecretString("settings.vaultToken"))

        // Sensitive token must NOT be present as plaintext in getString
        assertNotEquals(sensitiveToken, PlatformStorage.getString("settings.vaultToken"))
    }

    @Test
    fun partial_updates_to_vault_settings_persist_cleanly() {
        val settings = AppSettings().apply {
            vaultServerUrl = "http://localhost:8200"
            vaultToken = "token-1"
        }
        SettingsRepository.save(settings)

        settings.vaultServerUrl = "https://prod-vault:8200"
        settings.vaultToken = "token-2-updated"
        settings.vaultMountPath = "v2"
        SettingsRepository.save(settings)

        val reloaded = AppSettings()
        SettingsRepository.load(reloaded)

        assertEquals("https://prod-vault:8200", reloaded.vaultServerUrl)
        assertEquals("v2", reloaded.vaultMountPath)
        assertEquals("token-2-updated", reloaded.vaultToken)
    }

    @Test
    fun default_aws_settings_are_populated_on_empty_storage() {
        val settings = AppSettings()
        SettingsRepository.load(settings)

        assertEquals("us-east-1", settings.awsRegion)
        assertEquals("", settings.awsAccessKeyId)
        assertEquals("", settings.awsSecretAccessKey)
        assertEquals("", settings.awsSessionToken)
        assertEquals("", settings.awsEndpointUrl)
        assertEquals("AWSCURRENT", settings.awsVersionStage)
    }

    @Test
    fun save_and_load_roundtrips_aws_settings() {
        val original = AppSettings().apply {
            vaultProviderType = ExternalSecretType.AWS_SECRETS_MANAGER
            awsRegion = "eu-central-1"
            awsAccessKeyId = "AKIAEXAMPLE123"
            awsSecretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
            awsSessionToken = "AQoDYXdzEJr1EXAMPLETOKEN"
            awsEndpointUrl = "http://localhost:4566"
            awsVersionStage = "AWSPREVIOUS"
        }

        SettingsRepository.save(original)

        val restored = AppSettings()
        SettingsRepository.load(restored)

        assertEquals(ExternalSecretType.AWS_SECRETS_MANAGER, restored.vaultProviderType)
        assertEquals("eu-central-1", restored.awsRegion)
        assertEquals("AKIAEXAMPLE123", restored.awsAccessKeyId)
        assertEquals("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", restored.awsSecretAccessKey)
        assertEquals("AQoDYXdzEJr1EXAMPLETOKEN", restored.awsSessionToken)
        assertEquals("http://localhost:4566", restored.awsEndpointUrl)
        assertEquals("AWSPREVIOUS", restored.awsVersionStage)
    }

    @Test
    fun aws_credentials_are_persisted_in_encrypted_storage_not_plain_text() {
        val sensitiveKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
        val sensitiveToken = "AQoDYXdzEJr1EXAMPLETOKEN"
        val settings = AppSettings().apply {
            awsSecretAccessKey = sensitiveKey
            awsSessionToken = sensitiveToken
        }

        SettingsRepository.save(settings)

        assertEquals(sensitiveKey, PlatformStorage.getSecretString("settings.awsSecretAccessKey"))
        assertNotEquals(sensitiveKey, PlatformStorage.getString("settings.awsSecretAccessKey"))

        assertEquals(sensitiveToken, PlatformStorage.getSecretString("settings.awsSessionToken"))
        assertNotEquals(sensitiveToken, PlatformStorage.getString("settings.awsSessionToken"))
    }
}
