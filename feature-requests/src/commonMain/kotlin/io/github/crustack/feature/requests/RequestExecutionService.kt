package io.github.crustack.feature.requests

import io.github.crustack.core.model.RequestDefinition
import io.github.crustack.core.network.ApiClient
import io.github.crustack.core.network.NetworkEvent
import kotlinx.coroutines.flow.Flow

class RequestExecutionService(
    private val apiClient: ApiClient
) {
    fun execute(
        request: RequestDefinition,
        variableLayers: List<Map<String, String>> = emptyList()
    ): Flow<NetworkEvent> = apiClient.execute(request, variableLayers)
}
