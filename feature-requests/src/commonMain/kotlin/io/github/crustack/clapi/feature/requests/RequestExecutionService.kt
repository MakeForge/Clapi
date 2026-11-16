package io.github.crustack.clapi.feature.requests

import io.github.crustack.clapi.core.model.RequestDefinition
import io.github.crustack.clapi.core.model.opencollection.FolderItem
import io.github.crustack.clapi.core.model.opencollection.GrpcRequestItem
import io.github.crustack.clapi.core.model.opencollection.HttpRequestItem
import io.github.crustack.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.crustack.clapi.core.network.ApiClient
import io.github.crustack.clapi.core.network.NetworkEvent
import io.github.crustack.clapi.core.network.VariableResolver
import io.github.crustack.clapi.core.network.grpc.DynamicGrpcClient
import io.github.crustack.clapi.core.network.grpc.GrpcCallRequest
import io.github.crustack.clapi.core.network.grpc.GrpcCallResponse
import io.github.crustack.clapi.core.network.grpc.parseGrpcEndpoint
import kotlinx.coroutines.flow.Flow

class RequestExecutionService(
    private val apiClient: ApiClient,
    private val adapter: HttpExecutionAdapter = DefaultHttpExecutionAdapter,
    private val grpcClient: DynamicGrpcClient? = null,
) {
    fun execute(
        request: RequestDefinition,
        variableLayers: List<Map<String, String>> = emptyList()
    ): Flow<NetworkEvent> = apiClient.execute(request, variableLayers)

    fun execute(
        requestItem: HttpRequestItem,
        parentHierarchy: List<FolderItem> = emptyList(),
        collectionRoot: OpenCollectionDocument? = null,
        variableLayers: List<Map<String, String>> = emptyList()
    ): Flow<NetworkEvent> {
        val request = adapter.toExecutableRequest(requestItem, parentHierarchy, collectionRoot)
        val layers = if (variableLayers.isNotEmpty()) {
            variableLayers
        } else {
            adapter.buildVariableLayers(
                requestItem = requestItem,
                parentHierarchy = parentHierarchy,
                collectionRoot = collectionRoot
            )
        }
        return apiClient.execute(request, layers)
    }

    suspend fun executeGrpc(
        request: GrpcCallRequest,
        variableLayers: List<Map<String, String>> = emptyList(),
    ): GrpcCallResponse {
        val client = requireNotNull(grpcClient) { "DynamicGrpcClient is not configured on RequestExecutionService" }
        val resolvedPayload = VariableResolver.resolve(request.jsonPayload, variableLayers, removeUnresolved = false)
        val resolvedMetadata = request.metadata.map { (k, v) ->
            VariableResolver.resolve(k, variableLayers, removeUnresolved = false) to
                VariableResolver.resolve(v, variableLayers, removeUnresolved = false)
        }
        return client.executeUnary(
            request.copy(
                jsonPayload = resolvedPayload,
                metadata = resolvedMetadata,
            )
        )
    }

    suspend fun executeGrpc(
        requestItem: GrpcRequestItem,
        protoFilePathOverride: String? = null,
        variableLayers: List<Map<String, String>> = emptyList(),
    ): GrpcCallResponse {
        val resolvedUrl = VariableResolver.resolve(requestItem.grpc.url, variableLayers, removeUnresolved = false)
        val endpoint = parseGrpcEndpoint(resolvedUrl)
        val effectiveProtoPath = protoFilePathOverride
            ?: requestItem.grpc.protoFilePath.orEmpty()
        val resolvedPayload = VariableResolver.resolve(requestItem.grpc.message.orEmpty(), variableLayers, removeUnresolved = false)
        val resolvedMetadata = mutableListOf<Pair<String, String>>()
        for (entry in requestItem.grpc.metadata) {
            if (entry.disabled != true && entry.name.isNotBlank()) {
                resolvedMetadata.add(
                    VariableResolver.resolve(entry.name, variableLayers, removeUnresolved = false) to
                        VariableResolver.resolve(entry.value, variableLayers, removeUnresolved = false)
                )
            }
        }
        when (val auth = requestItem.grpc.auth) {
            is io.github.crustack.clapi.core.model.opencollection.AuthBearer -> {
                val token = VariableResolver.resolve(auth.token.orEmpty(), variableLayers, removeUnresolved = false)
                if (token.isNotBlank() && resolvedMetadata.none { it.first.equals("authorization", ignoreCase = true) }) {
                    resolvedMetadata.add("authorization" to "Bearer $token")
                }
            }
            is io.github.crustack.clapi.core.model.opencollection.AuthApiKey -> {
                val key = VariableResolver.resolve(auth.key.orEmpty(), variableLayers, removeUnresolved = false)
                val value = VariableResolver.resolve(auth.value.orEmpty(), variableLayers, removeUnresolved = false)
                if (key.isNotBlank() && resolvedMetadata.none { it.first.equals(key, ignoreCase = true) }) {
                    resolvedMetadata.add(key to value)
                }
            }
            else -> Unit
        }

        return executeGrpc(
            request = GrpcCallRequest(
                requestId = requestItem.info.name,
                host = endpoint.host,
                port = endpoint.port,
                useTls = endpoint.useTls,
                protoFilePath = effectiveProtoPath,
                fullMethodName = requestItem.grpc.method.orEmpty(),
                jsonPayload = resolvedPayload,
                metadata = resolvedMetadata,
            ),
            variableLayers = emptyList(),
        )
    }
}
