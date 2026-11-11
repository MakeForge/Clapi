package io.github.crustack.clapi.ui.shared.components

import io.github.crustack.clapi.core.network.keepassxc.KeePassXcClient
import io.github.crustack.clapi.core.network.keepassxc.KeePassXcEntry
import io.github.crustack.clapi.core.network.keepassxc.KeePassXcStatus
import io.github.crustack.clapi.ui.shared.state.AppSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SecretsManagerSettingsTest {

    private class MockKeePassXcClient(
        var mockStatus: KeePassXcStatus = KeePassXcStatus.Connected,
        var currentAssociateId: String? = "ClapiTestId",
        var proxyPathValue: String = "/run/user/1000/org.keepassxc.KeePassXC.BrowserServer",
    ) : KeePassXcClient {
        override suspend fun checkStatus(): KeePassXcStatus = mockStatus
        override suspend fun queryLogins(url: String, customFilters: Map<String, String>): List<KeePassXcEntry> = emptyList()
        override suspend fun countMatchingLogins(url: String, customFilters: Map<String, String>): Int = 0
        override suspend fun disconnect() {}

        override suspend fun getAssociateId(): String? = currentAssociateId
        override suspend fun associate(): String {
            currentAssociateId = "NewReassociatedId"
            mockStatus = KeePassXcStatus.Connected
            return "NewReassociatedId"
        }
        override suspend fun unassociate() {
            currentAssociateId = null
            mockStatus = KeePassXcStatus.NotAssociated(promptRequired = true)
        }
        override fun getProxyPath(): String = proxyPathValue
    }

    @Test
    fun secrets_manager_settings_toggle_switches_provider() {
        val settings = AppSettings()
        assertEquals("hashicorp-vault-server", settings.vaultProviderType)

        settings.vaultProviderType = "keepassxc"
        assertEquals("keepassxc", settings.vaultProviderType)

        settings.vaultProviderType = "aws-secrets-manager"
        assertEquals("aws-secrets-manager", settings.vaultProviderType)

        settings.vaultProviderType = "hashicorp-vault-server"
        assertEquals("hashicorp-vault-server", settings.vaultProviderType)
    }

    @Test
    fun keepassxc_client_manages_association_lifecycle() = runTest {
        val client = MockKeePassXcClient()

        assertEquals(KeePassXcStatus.Connected, client.checkStatus())
        assertEquals("ClapiTestId", client.getAssociateId())
        assertEquals("/run/user/1000/org.keepassxc.KeePassXC.BrowserServer", client.getProxyPath())

        // Test Unassociate
        client.unassociate()
        assertNull(client.getAssociateId())
        assertEquals(KeePassXcStatus.NotAssociated(promptRequired = true), client.checkStatus())

        // Test Reassociate
        val newId = client.associate()
        assertEquals("NewReassociatedId", newId)
        assertEquals("NewReassociatedId", client.getAssociateId())
        assertEquals(KeePassXcStatus.Connected, client.checkStatus())
    }

    @Test
    fun highlightVariablesWithStatus_resolves_working_variables() {
        val defined = setOf("baseUrl", "env")
        val annotated = highlightVariablesWithStatus("https://{{baseUrl}}/api/{{env}}/users", defined)

        assertEquals("https://{{baseUrl}}/api/{{env}}/users", annotated.text)
        val annotations = annotated.getStringAnnotations(VARIABLE_TAG, 0, annotated.length)
        assertEquals(2, annotations.size)
        assertEquals("baseUrl", annotations[0].item)
        assertEquals("env", annotations[1].item)
    }
}
