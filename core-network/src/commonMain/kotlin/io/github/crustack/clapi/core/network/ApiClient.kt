package io.github.crustack.clapi.core.network

import io.github.crustack.clapi.core.model.RequestDefinition
import kotlinx.coroutines.flow.Flow

interface ApiClient {
    fun execute(
        request: RequestDefinition,
        variableLayers: List<Map<String, String>> = emptyList()
    ): Flow<NetworkEvent>
}
