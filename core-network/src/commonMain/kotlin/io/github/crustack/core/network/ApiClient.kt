package io.github.crustack.core.network

import io.github.crustack.core.model.RequestDefinition
import kotlinx.coroutines.flow.Flow

interface ApiClient {
    fun execute(
        request: RequestDefinition,
        variableLayers: List<Map<String, String>> = emptyList()
    ): Flow<NetworkEvent>
}
