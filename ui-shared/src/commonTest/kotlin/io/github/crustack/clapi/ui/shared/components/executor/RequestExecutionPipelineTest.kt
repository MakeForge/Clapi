package io.github.crustack.clapi.ui.shared.components.executor

import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.core.model.RequestDefinition
import io.github.crustack.clapi.core.model.ResponseDefinition
import io.github.crustack.clapi.core.model.ResponseMetrics
import io.github.crustack.clapi.core.network.ApiClient
import io.github.crustack.clapi.core.network.NetworkEvent
import io.github.crustack.clapi.core.network.VariableResolver
import io.github.crustack.clapi.core.network.vault.HashiCorpVaultClient
import io.github.crustack.clapi.core.network.vault.SecretResolutionService
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.EnvState
import io.github.crustack.clapi.ui.shared.state.MutableExternalSecret
import io.github.crustack.clapi.ui.shared.state.MutableKeyValue
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestExecutionPipelineTest {

    private class RecordingClient : ApiClient {
        var lastRequest: RequestDefinition? = null
        var lastLayers: List<Map<String, String>> = emptyList()
        var closedCount: Int = 0

        override fun close() {
            closedCount++
        }

        override fun execute(
            request: RequestDefinition,
            variableLayers: List<Map<String, String>>,
        ): Flow<NetworkEvent> {
            lastRequest = request
            lastLayers = variableLayers
            return flowOf(
                NetworkEvent.Started(request.id, 1L),
                NetworkEvent.Success(
                    ResponseDefinition(
                        requestId = request.id,
                        statusCode = 200,
                        statusText = "OK",
                        headers = emptyList(),
                        cookies = emptyList(),
                        bodyText = "{\"ok\":true}",
                        contentType = "application/json",
                        executedAtEpochMillis = 100L,
                        metrics = ResponseMetrics(200, 10L, 11L),
                    )
                ),
            )
        }
    }

    @Test
    fun pre_request_script_requestScopedScriptVars_are_included_at_layer_0_on_wire_and_override_environment() = runTest {
        val state = AppState(withDemoData = false)
        val env = EnvState(
            name = "Dev",
            variables = listOf(
                MutableKeyValue("host", "env.example.com", enabled = true),
                MutableKeyValue("ephemeralToken", "env-fallback", enabled = true),
            ),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(
            name = "Script Scoped Vars Test",
            method = HttpMethodType.GET,
            url = "https://{{host}}/items",
        ).apply {
            headers.clear()
            headers.add(MutableKeyValue("Authorization", "Bearer {{ephemeralToken}}", enabled = true))
            preRequestScript = """
                clapi.variables.set("ephemeralToken", "script-layer0-token");
            """.trimIndent()
        }

        val client = RecordingClient()
        sendRequest(
            scope = this,
            state = state,
            tab = tab,
            clientFactory = { _, _, _ -> client },
        )
        tab.currentJob?.join()

        // Layer 0 on the wire must contain ephemeralToken = "script-layer0-token"
        assertEquals("script-layer0-token", client.lastLayers.first()["ephemeralToken"])
        val resolvedHeader = VariableResolver.resolve(
            client.lastRequest!!.headers.first { it.key == "Authorization" }.value,
            client.lastLayers,
        )
        assertEquals("Bearer script-layer0-token", resolvedHeader)
    }

    @Test
    fun pre_request_script_reads_resolved_secret_true_environment_variable_instead_of_empty_string() = runTest {
        val state = AppState(withDemoData = false)
        val env = EnvState(
            name = "SecretEnv",
            variables = listOf(
                MutableKeyValue(key = "API_SECRET", value = "", enabled = true, secret = true),
            ),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(
            name = "Read Secret In Pre-Script",
            method = HttpMethodType.GET,
            url = "https://example.com/api",
        ).apply {
            preRequestScript = """
                var sec = clapi.variables.get("API_SECRET");
                clapi.request.headers.add("X-Derived-Secret", "derived-" + sec);
            """.trimIndent()
        }

        val client = RecordingClient()
        sendRequest(
            scope = this,
            state = state,
            tab = tab,
            clientFactory = { _, _, _ -> client },
            processEnvProvider = { name -> if (name == "API_SECRET") "real_secret_value" else null },
        )
        tab.currentJob?.join()

        val derivedHeader = client.lastRequest!!.headers.first { it.key == "X-Derived-Secret" }.value
        assertEquals("derived-real_secret_value", derivedHeader)
    }

    @Test
    fun post_script_pass_2_scans_and_resolves_external_secret_tokens_injected_by_pre_request_script_mutation() = runTest {
        val state = AppState(withDemoData = false)
        state.settings.vaultServerUrl = "http://127.0.0.1:8200"
        state.settings.vaultToken = "vault-tok"

        val env = EnvState(
            name = "VaultEnv",
            externalSecretsProvider = "hashicorp-vault-server",
            externalSecrets = listOf(
                MutableExternalSecret(name = "dynamic_vault_key", path = "secret/app", targetField = "api_key", enabled = true),
            ),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(
            name = "Post-Script Secret Scan Test",
            method = HttpMethodType.GET,
            url = "https://example.com/data",
        ).apply {
            preRequestScript = """
                clapi.request.headers.add("X-Vault-Injected", "{{_secret.dynamic_vault_key}}");
            """.trimIndent()
        }

        val fakeVault = object : HashiCorpVaultClient {
            override suspend fun fetchSecret(
                vaultUrl: String,
                token: String,
                secretPath: String,
                mountPath: String,
                namespace: String?,
            ): Map<String, String> = mapOf("api_key" to "vault-injected-999")
        }

        val client = RecordingClient()
        sendRequest(
            scope = this,
            state = state,
            tab = tab,
            clientFactory = { _, _, _ -> client },
            secretResolutionService = SecretResolutionService(vaultClient = fakeVault),
        )
        tab.currentJob?.join()

        val rawHeader = client.lastRequest!!.headers.first { it.key == "X-Vault-Injected" }.value
        val wireHeader = VariableResolver.resolve(rawHeader, client.lastLayers)
        assertEquals("vault-injected-999", wireHeader)
    }

    @Test
    fun sendRequest_without_subRequest_does_not_instantiate_subReqClient_and_closes_per_request_client() = runTest {
        val state = AppState(withDemoData = false)
        val tab = RequestTabState(
            name = "Client Lifecycle Check",
            method = HttpMethodType.GET,
            url = "https://example.com/ping",
        )

        var factoryCallCount = 0
        val client = RecordingClient()
        sendRequest(
            scope = this,
            state = state,
            tab = tab,
            clientFactory = { _, _, _ ->
                factoryCallCount++
                client
            },
        )
        tab.currentJob?.join()

        assertEquals(1, factoryCallCount, "subReqClient must not be eagerly instantiated when clapi.sendRequest() is not used")
        assertEquals(1, client.closedCount, "Per-request ApiClient must be closed in finally block")
    }
}
