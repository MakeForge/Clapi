package io.github.crustack.clapi.core.network.vault

import io.github.crustack.clapi.core.network.gcp.GcpSecretManagerClient
import io.github.crustack.clapi.core.network.gcp.GcpSecretManagerConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SecretResolutionServiceGcpTest {

    private class FakeGcpSecretManagerClient(
        private val secrets: Map<String, Map<String, String>>,
    ) : GcpSecretManagerClient {
        val calls = mutableListOf<GcpSecretManagerConfig>()

        override suspend fun fetchSecret(config: GcpSecretManagerConfig): Map<String, String> {
            calls.add(config)
            return secrets[config.secretId]
                ?: throw IllegalStateException("External secret not found in GCP Secret Manager: '${config.secretId}'")
        }
    }

    @Test
    fun resolveGcpSecrets_batches_calls_by_secret_id_and_version() = runTest {
        val fakeClient = FakeGcpSecretManagerClient(
            mapOf(
                "payment-foo" to mapOf(
                    "client_id" to "id_123",
                    "client_secret" to "sec_456",
                ),
            ),
        )
        val service = SecretResolutionService(gcpClient = fakeClient)

        val declarations = listOf(
            SecretDeclaration(
                name = "client_id",
                path = "payment-foo",
                targetField = "client_id",
                enabled = true,
                providerType = "gcp-secrets-manager",
            ),
            SecretDeclaration(
                name = "client_secret",
                path = "payment-foo",
                targetField = "client_secret",
                enabled = true,
                providerType = "gcp-secrets-manager",
            ),
        )

        val resolved = service.resolveGcpSecrets(
            requiredSecretNames = setOf("client_id", "client_secret"),
            declarations = declarations,
            projectId = "my-test-project",
            accessToken = "dummy-token",
            endpointUrl = "http://localhost:8085",
            client = fakeClient,
        )

        // Only one fetch call made for secret ID "payment-foo"
        assertEquals(1, fakeClient.calls.size)
        assertEquals("payment-foo", fakeClient.calls[0].secretId)
        assertEquals("my-test-project", fakeClient.calls[0].projectId)
        assertEquals("latest", fakeClient.calls[0].version)

        assertEquals("id_123", resolved["_secret.client_id"])
        assertEquals("sec_456", resolved["_secret.client_secret"])
    }

    @Test
    fun resolveGcpSecrets_resolves_raw_plain_text() = runTest {
        val fakeClient = FakeGcpSecretManagerClient(
            mapOf(
                "plain-token" to mapOf("secret" to "plain-token-val-999"),
            ),
        )
        val service = SecretResolutionService(gcpClient = fakeClient)

        val declarations = listOf(
            SecretDeclaration(
                name = "token",
                path = "plain-token",
                targetField = "",
                enabled = true,
                providerType = "gcp-secrets-manager",
            ),
        )

        val resolved = service.resolveGcpSecrets(
            requiredSecretNames = setOf("token"),
            declarations = declarations,
            projectId = "my-test-project",
            accessToken = "dummy-token",
            client = fakeClient,
        )

        assertEquals("plain-token-val-999", resolved["_secret.token"])
    }

    @Test
    fun resolveGcpSecrets_evaluates_custom_filters_and_fails_on_mismatch() = runTest {
        val fakeClient = FakeGcpSecretManagerClient(
            mapOf(
                "multi-meta" to mapOf(
                    "apiKey" to "key-123",
                    "stage" to "prod",
                ),
            ),
        )
        val service = SecretResolutionService(gcpClient = fakeClient)

        val declarationsMatching = listOf(
            SecretDeclaration(
                name = "api_key",
                path = "multi-meta",
                targetField = "apiKey",
                customFilters = mapOf("stage" to "prod"),
                enabled = true,
                providerType = "gcp-secrets-manager",
            ),
        )

        val resolved = service.resolveGcpSecrets(
            requiredSecretNames = setOf("api_key"),
            declarations = declarationsMatching,
            projectId = "my-test-project",
            accessToken = "dummy-token",
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
                providerType = "gcp-secrets-manager",
            ),
        )

        assertFailsWith<IllegalStateException> {
            service.resolveGcpSecrets(
                requiredSecretNames = setOf("api_key"),
                declarations = declarationsMismatch,
                projectId = "my-test-project",
                accessToken = "dummy-token",
                client = fakeClient,
            )
        }
    }

    @Test
    fun resolveGcpSecrets_respects_project_id_override() = runTest {
        val fakeClient = FakeGcpSecretManagerClient(
            mapOf(
                "db-secret" to mapOf("password" to "super-pass"),
            ),
        )
        val service = SecretResolutionService(gcpClient = fakeClient)

        val declarations = listOf(
            SecretDeclaration(
                name = "db_pass",
                path = "db-secret",
                url = "custom-override-project",
                targetField = "password",
                enabled = true,
                providerType = "gcp-secrets-manager",
            ),
        )

        val resolved = service.resolveGcpSecrets(
            requiredSecretNames = setOf("db_pass"),
            declarations = declarations,
            projectId = "default-project",
            accessToken = "dummy-token",
            client = fakeClient,
        )

        assertEquals("super-pass", resolved["_secret.db_pass"])
        assertEquals(1, fakeClient.calls.size)
        assertEquals("custom-override-project", fakeClient.calls[0].projectId)
    }

    @Test
    fun resolveAllSecrets_routes_gcp_provider_declarations() = runTest {
        val fakeClient = FakeGcpSecretManagerClient(
            mapOf(
                "app-secret" to mapOf("token" to "gcp-token-xyz"),
            ),
        )
        val service = SecretResolutionService(gcpClient = fakeClient)

        val declarations = listOf(
            SecretDeclaration(
                name = "my_token",
                path = "app-secret",
                targetField = "token",
                enabled = true,
                providerType = "gcp-secrets-manager",
            ),
        )

        val resolved = service.resolveAllSecrets(
            texts = listOf("Bearer {{_secret.my_token}}"),
            declarations = declarations,
            gcpProjectId = "my-test-project",
            gcpAccessToken = "dummy-token",
            gcpClient = fakeClient,
        )

        assertEquals("gcp-token-xyz", resolved["_secret.my_token"])
    }
}
