package io.github.crustack.clapi.core.network.vault

import io.github.crustack.clapi.core.network.azure.AzureKeyVaultClient
import io.github.crustack.clapi.core.network.azure.AzureKeyVaultConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SecretResolutionServiceAzureTest {

    private class FakeAzureKeyVaultClient(
        private val secrets: Map<String, Map<String, String>>,
    ) : AzureKeyVaultClient {
        val calls = mutableListOf<AzureKeyVaultConfig>()

        override suspend fun fetchSecret(config: AzureKeyVaultConfig): Map<String, String> {
            calls.add(config)
            return secrets[config.secretName]
                ?: throw IllegalStateException("External secret not found in Azure Key Vault: '${config.secretName}'")
        }
    }

    @Test
    fun resolveAzureSecrets_batches_calls_by_secret_name() = runTest {
        val fakeClient = FakeAzureKeyVaultClient(
            mapOf(
                "payment-foo" to mapOf(
                    "client_id" to "id_123",
                    "client_secret" to "sec_456",
                ),
            ),
        )
        val service = SecretResolutionService(azureClient = fakeClient)

        val declarations = listOf(
            SecretDeclaration(
                name = "client_id",
                path = "payment-foo",
                targetField = "client_id",
                enabled = true,
                providerType = "azure-key-vault",
            ),
            SecretDeclaration(
                name = "client_secret",
                path = "payment-foo",
                targetField = "client_secret",
                enabled = true,
                providerType = "azure-key-vault",
            ),
        )

        val resolved = service.resolveAzureSecrets(
            requiredSecretNames = setOf("client_id", "client_secret"),
            declarations = declarations,
            vaultName = "https://my-vault.vault.azure.net",
            tenantId = "tenant-1",
            clientId = "client-1",
            clientSecret = "secret-1",
            authorityHost = "bypass",
            client = fakeClient,
        )

        // Only one fetch call made for secret name "payment-foo"
        assertEquals(1, fakeClient.calls.size)
        assertEquals("payment-foo", fakeClient.calls[0].secretName)
        assertEquals("https://my-vault.vault.azure.net", fakeClient.calls[0].vaultName)

        assertEquals("id_123", resolved["_secret.client_id"])
        assertEquals("sec_456", resolved["_secret.client_secret"])
    }

    @Test
    fun resolveAzureSecrets_resolves_raw_plain_text() = runTest {
        val fakeClient = FakeAzureKeyVaultClient(
            mapOf(
                "plain-token" to mapOf("value" to "plain-token-val-999"),
            ),
        )
        val service = SecretResolutionService(azureClient = fakeClient)

        val declarations = listOf(
            SecretDeclaration(
                name = "token",
                path = "plain-token",
                targetField = "",
                enabled = true,
                providerType = "azure-key-vault",
            ),
        )

        val resolved = service.resolveAzureSecrets(
            requiredSecretNames = setOf("token"),
            declarations = declarations,
            vaultName = "https://my-vault.vault.azure.net",
            tenantId = "tenant-1",
            clientId = "client-1",
            clientSecret = "secret-1",
            authorityHost = "bypass",
            client = fakeClient,
        )

        assertEquals("plain-token-val-999", resolved["_secret.token"])
    }

    @Test
    fun resolveAzureSecrets_evaluates_custom_filters_and_fails_on_mismatch() = runTest {
        val fakeClient = FakeAzureKeyVaultClient(
            mapOf(
                "multi-meta" to mapOf(
                    "apiKey" to "key-123",
                    "stage" to "prod",
                ),
            ),
        )
        val service = SecretResolutionService(azureClient = fakeClient)

        val declarationsMatching = listOf(
            SecretDeclaration(
                name = "api_key",
                path = "multi-meta",
                targetField = "apiKey",
                customFilters = mapOf("stage" to "prod"),
                enabled = true,
                providerType = "azure-key-vault",
            ),
        )

        val resolved = service.resolveAzureSecrets(
            requiredSecretNames = setOf("api_key"),
            declarations = declarationsMatching,
            vaultName = "https://my-vault.vault.azure.net",
            tenantId = "tenant-1",
            clientId = "client-1",
            clientSecret = "secret-1",
            authorityHost = "bypass",
            client = fakeClient,
        )
        assertEquals("key-123", resolved["_secret.api_key"])

        // Mismatched filter
        val declarationsMismatch = listOf(
            SecretDeclaration(
                name = "api_key",
                path = "multi-meta",
                targetField = "apiKey",
                customFilters = mapOf("stage" to "dev"),
                enabled = true,
                providerType = "azure-key-vault",
            ),
        )

        assertFailsWith<IllegalStateException> {
            service.resolveAzureSecrets(
                requiredSecretNames = setOf("api_key"),
                declarations = declarationsMismatch,
                vaultName = "https://my-vault.vault.azure.net",
                tenantId = "tenant-1",
                clientId = "client-1",
                clientSecret = "secret-1",
                authorityHost = "bypass",
                client = fakeClient,
            )
        }
    }

    @Test
    fun resolveAzureSecrets_respects_vault_name_override() = runTest {
        val fakeClient = FakeAzureKeyVaultClient(
            mapOf(
                "db-secret" to mapOf("password" to "super-pass"),
            ),
        )
        val service = SecretResolutionService(azureClient = fakeClient)

        val declarations = listOf(
            SecretDeclaration(
                name = "db_pass",
                path = "db-secret",
                url = "https://custom-override-vault.vault.azure.net",
                targetField = "password",
                enabled = true,
                providerType = "azure-key-vault",
            ),
        )

        val resolved = service.resolveAzureSecrets(
            requiredSecretNames = setOf("db_pass"),
            declarations = declarations,
            vaultName = "https://default-vault.vault.azure.net",
            tenantId = "tenant-1",
            clientId = "client-1",
            clientSecret = "secret-1",
            authorityHost = "bypass",
            client = fakeClient,
        )

        assertEquals("super-pass", resolved["_secret.db_pass"])
        assertEquals(1, fakeClient.calls.size)
        assertEquals("https://custom-override-vault.vault.azure.net", fakeClient.calls[0].vaultName)
    }

    @Test
    fun resolveAllSecrets_routes_azure_provider_declarations() = runTest {
        val fakeClient = FakeAzureKeyVaultClient(
            mapOf(
                "app-secret" to mapOf("token" to "az-token-abc"),
            ),
        )
        val service = SecretResolutionService(azureClient = fakeClient)

        val declarations = listOf(
            SecretDeclaration(
                name = "my_token",
                path = "app-secret",
                targetField = "token",
                enabled = true,
                providerType = "azure-key-vault",
            ),
        )

        val resolved = service.resolveAllSecrets(
            texts = listOf("Bearer {{_secret.my_token}}"),
            declarations = declarations,
            azureVaultName = "https://my-vault.vault.azure.net",
            azureTenantId = "tenant-1",
            azureClientId = "client-1",
            azureClientSecret = "secret-1",
            azureAuthorityHost = "bypass",
            azureClient = fakeClient,
        )

        assertEquals("az-token-abc", resolved["_secret.my_token"])
    }
}
