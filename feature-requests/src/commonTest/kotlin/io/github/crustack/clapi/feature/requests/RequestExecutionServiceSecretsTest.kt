package io.github.crustack.clapi.feature.requests

import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.core.model.KeyValueEntry
import io.github.crustack.clapi.core.model.RequestDefinition
import io.github.crustack.clapi.core.model.opencollection.HashicorpVaultExternalSecret
import io.github.crustack.clapi.core.model.opencollection.SecretVariable
import io.github.crustack.clapi.core.network.ApiClient
import io.github.crustack.clapi.core.network.NetworkEvent
import io.github.crustack.clapi.core.network.vault.HashiCorpVaultClient
import io.github.crustack.clapi.core.network.vault.SecretResolutionService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RequestExecutionServiceSecretsTest {

    private class FakeVaultClient(
        private val secrets: Map<String, Map<String, String>>,
    ) : HashiCorpVaultClient {
        override suspend fun fetchSecret(
            vaultUrl: String,
            token: String,
            secretPath: String,
            mountPath: String,
            namespace: String?,
        ): Map<String, String> = secrets[secretPath] ?: emptyMap()
    }

    private class RecordingApiClient : ApiClient {
        var lastLayers: List<Map<String, String>> = emptyList()

        override fun execute(
            request: RequestDefinition,
            variableLayers: List<Map<String, String>>,
        ): Flow<NetworkEvent> {
            lastLayers = variableLayers
            return emptyFlow()
        }
    }

    @Test
    fun executeWithSecrets_resolves_and_prepends_secrets_to_variableLayers() = runTest {
        val vaultClient = FakeVaultClient(
            mapOf("payment/foo" to mapOf("client_id" to "cid_999")),
        )
        val resolutionService = SecretResolutionService(vaultClient)
        val apiClient = RecordingApiClient()
        val service = RequestExecutionService(
            apiClient = apiClient,
            secretResolutionService = resolutionService,
        )

        val request = RequestDefinition(
            id = "req-1",
            name = "Test",
            method = HttpMethodType.GET,
            url = "https://example.com/api?id={{_secret.client_id}}",
            headers = listOf(KeyValueEntry("X-Auth", "{{_secret.client_id}}")),
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )

        val secrets = listOf(
            HashicorpVaultExternalSecret(name = "client_id", path = "payment/foo"),
        )

        service.executeWithSecrets(
            request = request,
            variableLayers = listOf(mapOf("env" to "prod")),
            externalSecrets = secrets,
            vaultUrl = "http://127.0.0.1:8200",
            vaultToken = "test-token",
        )

        assertEquals(2, apiClient.lastLayers.size)
        assertEquals("cid_999", apiClient.lastLayers[0]["_secret.client_id"])
        assertEquals("prod", apiClient.lastLayers[1]["env"])
    }

    @Test
    fun executeWithSecrets_without_secret_tokens_does_not_call_vault() = runTest {
        val vaultClient = FakeVaultClient(emptyMap())
        val resolutionService = SecretResolutionService(vaultClient)
        val apiClient = RecordingApiClient()
        val service = RequestExecutionService(
            apiClient = apiClient,
            secretResolutionService = resolutionService,
        )

        val request = RequestDefinition(
            id = "req-1",
            name = "Test",
            method = HttpMethodType.GET,
            url = "https://example.com/plain",
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )

        service.executeWithSecrets(
            request = request,
            variableLayers = listOf(mapOf("env" to "prod")),
            externalSecrets = emptyList(),
            vaultUrl = null,
            vaultToken = null,
        )

        assertEquals(1, apiClient.lastLayers.size)
        assertEquals("prod", apiClient.lastLayers[0]["env"])
    }

    @Test
    fun executeWithSecrets_resolves_secret_variable_from_process_env() = runTest {
        val vaultClient = FakeVaultClient(emptyMap())
        val resolutionService = SecretResolutionService(vaultClient)
        val apiClient = RecordingApiClient()
        val service = RequestExecutionService(
            apiClient = apiClient,
            secretResolutionService = resolutionService,
        )

        val request = RequestDefinition(
            id = "req-1",
            name = "Test",
            method = HttpMethodType.GET,
            url = "https://example.com/api?token={{API_SECRET}}",
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )

        service.executeWithSecrets(
            request = request,
            secretVariables = listOf(SecretVariable(name = "API_SECRET")),
            processEnvProvider = { if (it == "API_SECRET") "process_token_123" else null },
        )

        assertEquals(1, apiClient.lastLayers.size)
        assertEquals("process_token_123", apiClient.lastLayers[0]["API_SECRET"])
    }

    @Test
    fun executeWithSecrets_falls_back_to_matching_external_secret_when_not_in_process_env() = runTest {
        val vaultClient = FakeVaultClient(
            mapOf("keys/api" to mapOf("API_SECRET" to "vault_secret_456")),
        )
        val resolutionService = SecretResolutionService(vaultClient)
        val apiClient = RecordingApiClient()
        val service = RequestExecutionService(
            apiClient = apiClient,
            secretResolutionService = resolutionService,
        )

        val request = RequestDefinition(
            id = "req-1",
            name = "Test",
            method = HttpMethodType.GET,
            url = "https://example.com/api?token={{API_SECRET}}",
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )

        service.executeWithSecrets(
            request = request,
            externalSecrets = listOf(HashicorpVaultExternalSecret(name = "API_SECRET", path = "keys/api")),
            secretVariables = listOf(SecretVariable(name = "API_SECRET")),
            vaultUrl = "http://127.0.0.1:8200",
            vaultToken = "tok",
            processEnvProvider = { null },
        )

        assertEquals(1, apiClient.lastLayers.size)
        assertEquals("vault_secret_456", apiClient.lastLayers[0]["API_SECRET"])
    }

    @Test
    fun executeWithSecrets_throws_when_secret_variable_cannot_be_resolved() = runTest {
        val vaultClient = FakeVaultClient(emptyMap())
        val resolutionService = SecretResolutionService(vaultClient)
        val apiClient = RecordingApiClient()
        val service = RequestExecutionService(
            apiClient = apiClient,
            secretResolutionService = resolutionService,
        )

        val request = RequestDefinition(
            id = "req-1",
            name = "Test",
            method = HttpMethodType.GET,
            url = "https://example.com/api?token={{API_SECRET}}",
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )

        val ex = assertFailsWith<IllegalStateException> {
            service.executeWithSecrets(
                request = request,
                externalSecrets = emptyList(),
                secretVariables = listOf(SecretVariable(name = "API_SECRET")),
                processEnvProvider = { null },
            )
        }

        assertEquals(
            "Secret variable 'API_SECRET' could not be resolved from process environment or external secrets.",
            ex.message,
        )
    }
}
