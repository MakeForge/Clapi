package io.github.crustack.clapi.feature.requests

import io.github.crustack.clapi.core.model.RequestDefinition
import io.github.crustack.clapi.core.model.opencollection.FolderItem
import io.github.crustack.clapi.core.model.opencollection.HttpRequestItem
import io.github.crustack.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.crustack.clapi.core.network.ApiClient
import io.github.crustack.clapi.core.network.NetworkEvent
import kotlinx.coroutines.flow.Flow

class RequestExecutionService(
    private val apiClient: ApiClient,
    private val adapter: HttpExecutionAdapter = DefaultHttpExecutionAdapter,
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
}
