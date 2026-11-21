package io.github.crustack.clapi.ui.shared.components.executor

import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.core.model.RequestDefinition
import io.github.crustack.clapi.core.model.RequestKind
import io.github.crustack.clapi.core.model.ResponseDefinition
import io.github.crustack.clapi.core.model.ResponseMetrics
import io.github.crustack.clapi.core.network.ApiClient
import io.github.crustack.clapi.core.network.NetworkEvent
import io.github.crustack.clapi.core.network.RetryPolicy
import io.github.crustack.clapi.core.network.VariableResolver
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.CollectionNode
import io.github.crustack.clapi.ui.shared.state.EnvState
import io.github.crustack.clapi.ui.shared.state.MutableKeyValue
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GraphQlRequestExecutorTest {

    private class CapturingApiClient(
        private val responseStatusCode: Int = 200,
        private val responseBody: String = "{\"data\":{}}",
    ) : ApiClient {
        val capturedRequests = mutableListOf<RequestDefinition>()
        val capturedVariableLayers = mutableListOf<List<Map<String, String>>>()

        override fun execute(
            request: RequestDefinition,
            variableLayers: List<Map<String, String>>,
        ): Flow<NetworkEvent> {
            capturedRequests.add(request)
            capturedVariableLayers.add(variableLayers)
            return flowOf(
                NetworkEvent.Started(request.id, 1L),
                NetworkEvent.Success(
                    ResponseDefinition(
                        requestId = request.id,
                        statusCode = responseStatusCode,
                        statusText = "OK",
                        headers = emptyList(),
                        cookies = emptyList(),
                        bodyText = responseBody,
                        contentType = "application/json",
                        executedAtEpochMillis = 100L,
                        metrics = ResponseMetrics(
                            statusCode = responseStatusCode,
                            responseTimeMs = 10L,
                            responseSizeBytes = responseBody.length.toLong(),
                        ),
                    )
                ),
            )
        }
    }

    @Test
    fun graphql_request_honors_tab_retry_policy_when_enabled() = runTest {
        val state = AppState(withDemoData = false)
        val tab = RequestTabState(
            name = "GraphQL Retry",
            url = "https://api.example.com/graphql",
            kind = RequestKind.GRAPHQL,
        ).apply {
            retryEnabled = true
            retryCount = 4
            retryDelayMs = 250L
        }

        var capturedRetryPolicy: RetryPolicy? = null
        val fakeClient = CapturingApiClient()

        sendRequest(
            scope = this,
            state = state,
            tab = tab,
            clientFactory = { _, _, retry ->
                capturedRetryPolicy = retry
                fakeClient
            },
        )
        tab.currentJob?.join()

        assertNotNull(capturedRetryPolicy)
        assertEquals(4, capturedRetryPolicy!!.maxAttempts)
        assertEquals(250L, capturedRetryPolicy!!.baseDelayMs)
    }

    @Test
    fun graphql_get_request_performs_single_pass_variable_resolution_without_secondary_expansion() = runTest {
        val state = AppState(withDemoData = false)
        val env = EnvState(
            name = "TestEnv",
            variables = listOf(
                MutableKeyValue("outerVar", "literal {{innerVar}}", enabled = true),
                MutableKeyValue("innerVar", "SHOULD_NOT_EXPAND", enabled = true),
            ),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(
            name = "GraphQL GET",
            url = "https://api.example.com/graphql",
            kind = RequestKind.GRAPHQL,
        ).apply {
            graphQlMethod = "GET"
            effectiveGraphQlQuery = "query { echo(msg: \"{{outerVar}}\") }"
            effectiveGraphQlVariables = "{\"input\": \"{{outerVar}}\"}"
        }

        val fakeClient = CapturingApiClient()
        sendRequest(
            scope = this,
            state = state,
            tab = tab,
            clientFactory = { _, _, _ -> fakeClient },
        )
        tab.currentJob?.join()

        val wireReq = fakeClient.capturedRequests.single()
        val layers = fakeClient.capturedVariableLayers.single()

        val queryParamVal = wireReq.queryParams.first { it.key == "query" }.value
        val variablesParamVal = wireReq.queryParams.first { it.key == "variables" }.value

        val finalWireQuery = VariableResolver.resolve(queryParamVal, layers)
        val finalWireVariables = VariableResolver.resolve(variablesParamVal, layers)

        assertEquals("query { echo(msg: \"literal {{innerVar}}\") }", finalWireQuery)
        assertEquals("{\"input\": \"literal {{innerVar}}\"}", finalWireVariables)
    }

    @Test
    fun graphql_request_includes_requestScopedScriptVars_at_layer_0_and_propagates_setNextRequest() = runTest {
        val state = AppState(withDemoData = false)
        val env = EnvState(
            name = "Env",
            variables = listOf(MutableKeyValue("token", "env-token", enabled = true)),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val targetNode = CollectionNode(
            id = "req-next",
            name = "FollowUpHttp",
            method = HttpMethodType.GET,
            url = "https://api.example.com/followup",
            kind = RequestKind.HTTP,
        )
        val rootCol = CollectionNode(
            id = "col-1",
            name = "MyCol",
            isFolder = true,
            kind = RequestKind.COLLECTION,
        ).also { it.children.add(targetNode) }
        state.collections.add(rootCol)

        val tab = RequestTabState(
            id = "req-gql",
            name = "FirstGraphQL",
            url = "https://api.example.com/graphql",
            kind = RequestKind.GRAPHQL,
            collectionId = "col-1",
            collectionName = "MyCol",
        ).apply {
            headers.clear()
            headers.add(MutableKeyValue("X-Scoped", "{{token}}", enabled = true))
            preRequestScript = """
                clapi.variables.set("token", "script-scoped-token");
            """.trimIndent()
            testScript = """
                clapi.execution.setNextRequest("FollowUpHttp");
            """.trimIndent()
        }
        state.openTabs.add(tab)
        state.activeTabIndex = 0

        val fakeClient = CapturingApiClient()
        sendRequest(
            scope = this,
            state = state,
            tab = tab,
            clientFactory = { _, _, _ -> fakeClient },
        )
        tab.currentJob?.join()
        state.activeTab?.currentJob?.join()

        assertEquals(2, fakeClient.capturedRequests.size, "Both FirstGraphQL and FollowUpHttp should execute")
        val gqlLayers = fakeClient.capturedVariableLayers[0]
        assertEquals("script-scoped-token", gqlLayers[0]["token"])
        assertEquals("FollowUpHttp", fakeClient.capturedRequests[1].name)
    }
}
