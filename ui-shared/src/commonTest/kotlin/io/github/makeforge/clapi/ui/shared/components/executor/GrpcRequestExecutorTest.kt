package io.github.makeforge.clapi.ui.shared.components.executor

import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.core.model.RequestDefinition
import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.core.model.ResponseDefinition
import io.github.makeforge.clapi.core.model.ResponseMetrics
import io.github.makeforge.clapi.core.network.ApiClient
import io.github.makeforge.clapi.core.network.NetworkEvent
import io.github.makeforge.clapi.core.network.grpc.DynamicGrpcClient
import io.github.makeforge.clapi.core.network.grpc.GrpcCallRequest
import io.github.makeforge.clapi.core.network.grpc.GrpcCallResponse
import io.github.makeforge.clapi.ui.shared.components.resolveGrpcCallRequest
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GrpcRequestExecutorTest {

    @Test
    fun grpc_request_injects_bearer_basic_apikey_oauth2_and_inherited_auth_into_metadata() {
        val state = AppState(withDemoData = false)

        // 1. Bearer Auth
        val bearerTab = RequestTabState(
            name = "gRPC Bearer",
            url = "grpc://localhost:50051",
            kind = RequestKind.GRPC,
        ).apply {
            protoFilePath = "service.proto"
            grpcMethod = "pkg.Greeter/SayHello"
            authType = AuthType.BEARER
            authToken = "my-bearer-token"
        }
        val bearerReq = resolveGrpcCallRequest(bearerTab, state)
        assertEquals("Bearer my-bearer-token", bearerReq.metadata.toMap()["authorization"])

        // 2. Basic Auth
        val basicTab = RequestTabState(
            name = "gRPC Basic",
            url = "grpc://localhost:50051",
            kind = RequestKind.GRPC,
        ).apply {
            protoFilePath = "service.proto"
            grpcMethod = "pkg.Greeter/SayHello"
            authType = AuthType.BASIC
            authUsername = "alice"
            authPassword = "secret"
        }
        val basicReq = resolveGrpcCallRequest(basicTab, state)
        assertEquals("Basic YWxpY2U6c2VjcmV0", basicReq.metadata.toMap()["authorization"])

        // 3. API Key Auth
        val apiKeyTab = RequestTabState(
            name = "gRPC ApiKey",
            url = "grpc://localhost:50051",
            kind = RequestKind.GRPC,
        ).apply {
            protoFilePath = "service.proto"
            grpcMethod = "pkg.Greeter/SayHello"
            authType = AuthType.API_KEY
            authApiKey = "x-api-key"
            authApiValue = "key-999"
        }
        val apiKeyReq = resolveGrpcCallRequest(apiKeyTab, state)
        assertEquals("key-999", apiKeyReq.metadata.toMap()["x-api-key"])

        // 4. OAuth2 Auth
        val oauth2Tab = RequestTabState(
            name = "gRPC OAuth2",
            url = "grpc://localhost:50051",
            kind = RequestKind.GRPC,
        ).apply {
            protoFilePath = "service.proto"
            grpcMethod = "pkg.Greeter/SayHello"
            authType = AuthType.OAUTH2
            authOAuth2AccessToken = "oauth2-access-777"
        }
        val oauth2Req = resolveGrpcCallRequest(oauth2Tab, state)
        assertEquals("Bearer oauth2-access-777", oauth2Req.metadata.toMap()["authorization"])

        // 5. Inherited Auth from Collection
        val grpcNode = CollectionNode(
            id = "grpc-inh",
            name = "Inherited gRPC",
            kind = RequestKind.GRPC,
            url = "grpc://localhost:50051",
        )
        val rootCol = CollectionNode(
            id = "col-inh",
            name = "RootCol",
            isFolder = true,
            kind = RequestKind.COLLECTION,
            authType = AuthType.BEARER,
            authToken = "inherited-col-token",
        ).also { it.children.add(grpcNode) }
        state.collections.add(rootCol)

        val inheritTab = RequestTabState(
            id = "grpc-inh",
            name = "Inherited gRPC",
            url = "grpc://localhost:50051",
            kind = RequestKind.GRPC,
            collectionId = "col-inh",
            collectionName = "RootCol",
        ).apply {
            protoFilePath = "service.proto"
            grpcMethod = "pkg.Greeter/SayHello"
            authType = AuthType.INHERIT
        }
        val inheritReq = resolveGrpcCallRequest(inheritTab, state)
        assertEquals("Bearer inherited-col-token", inheritReq.metadata.toMap()["authorization"])
    }

    @Test
    fun grpc_post_request_script_propagates_setNextRequest_to_target_request() = runTest {
        val state = AppState(withDemoData = false)

        val nextNode = CollectionNode(
            id = "req-after-grpc",
            name = "AfterGrpcHttp",
            method = HttpMethodType.GET,
            url = "https://api.example.com/after-grpc",
            kind = RequestKind.HTTP,
        )
        val grpcNode = CollectionNode(
            id = "req-grpc-1",
            name = "StartGrpc",
            kind = RequestKind.GRPC,
            url = "grpc://localhost:50051",
        )
        val rootCol = CollectionNode(
            id = "col-grpc",
            name = "GrpcChainCol",
            isFolder = true,
            kind = RequestKind.COLLECTION,
        ).also {
            it.children.add(grpcNode)
            it.children.add(nextNode)
        }
        state.collections.add(rootCol)

        val grpcTab = RequestTabState(
            id = "req-grpc-1",
            name = "StartGrpc",
            url = "grpc://localhost:50051",
            kind = RequestKind.GRPC,
            collectionId = "col-grpc",
            collectionName = "GrpcChainCol",
        ).apply {
            protoFilePath = "service.proto"
            grpcMethod = "pkg.Greeter/SayHello"
            authType = AuthType.BEARER
            authToken = "grpc-wire-token"
            testScript = """
                clapi.execution.setNextRequest("AfterGrpcHttp");
            """.trimIndent()
        }
        state.openTabs.add(grpcTab)
        state.activeTabIndex = 0

        var capturedGrpcCall: GrpcCallRequest? = null
        val fakeGrpcClient = object : DynamicGrpcClient {
            override suspend fun executeUnary(request: GrpcCallRequest): GrpcCallResponse {
                capturedGrpcCall = request
                return GrpcCallResponse(
                    requestId = request.requestId,
                    statusCode = 0,
                    statusDescription = "OK",
                    responseJson = "{\"reply\":\"hello\"}",
                    durationMs = 5L,
                )
            }

            override suspend fun shutdown() {}
        }

        val capturedHttpRequests = mutableListOf<RequestDefinition>()
        val fakeHttpClient = object : ApiClient {
            override fun execute(
                request: RequestDefinition,
                variableLayers: List<Map<String, String>>,
            ): Flow<NetworkEvent> {
                capturedHttpRequests.add(request)
                return flowOf(
                    NetworkEvent.Started(request.id, 1L),
                    NetworkEvent.Success(
                        ResponseDefinition(
                            requestId = request.id,
                            statusCode = 200,
                            statusText = "OK",
                            headers = emptyList(),
                            cookies = emptyList(),
                            bodyText = "{}",
                            contentType = "application/json",
                            executedAtEpochMillis = 100L,
                            metrics = ResponseMetrics(200, 5L, 2L),
                        )
                    ),
                )
            }
        }

        sendRequest(
            scope = this,
            state = state,
            tab = grpcTab,
            clientFactory = { _, _, _ -> fakeHttpClient },
            grpcClientFactory = { fakeGrpcClient },
        )
        grpcTab.currentJob?.join()
        state.activeTab?.currentJob?.join()

        assertNotNull(capturedGrpcCall)
        assertEquals("Bearer grpc-wire-token", capturedGrpcCall!!.metadata.toMap()["authorization"])
        assertTrue(capturedHttpRequests.any { it.name == "AfterGrpcHttp" }, "gRPC test script setNextRequest must execute AfterGrpcHttp")
    }
}
