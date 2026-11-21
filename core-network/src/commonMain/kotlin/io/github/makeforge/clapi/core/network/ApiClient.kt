// Modified by MakeForge (2026)
package io.github.makeforge.clapi.core.network

import io.github.makeforge.clapi.core.model.RequestDefinition
import kotlinx.coroutines.flow.Flow

interface ApiClient : AutoCloseable {
    fun execute(
        request: RequestDefinition,
        variableLayers: List<Map<String, String>> = emptyList()
    ): Flow<NetworkEvent>

    override fun close() {}
}
