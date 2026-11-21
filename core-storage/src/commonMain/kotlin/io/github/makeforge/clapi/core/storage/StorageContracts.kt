// Modified by MakeForge (2026)
package io.github.makeforge.clapi.core.storage

import io.github.makeforge.clapi.core.model.EnvironmentDefinition
import io.github.makeforge.clapi.core.model.HistoryEntry
import io.github.makeforge.clapi.core.model.RequestDefinition
import kotlinx.coroutines.flow.Flow

interface RequestRepository {
    fun observeAll(): Flow<List<RequestDefinition>>
    suspend fun upsert(request: RequestDefinition)
    suspend fun delete(requestId: String)
}

interface EnvironmentRepository {
    fun observeAll(): Flow<List<EnvironmentDefinition>>
    suspend fun upsert(environment: EnvironmentDefinition)
    suspend fun delete(environmentId: String)
}

interface HistoryRepository {
    fun observeRecent(limit: Int = 200): Flow<List<HistoryEntry>>
    suspend fun append(entry: HistoryEntry)
    suspend fun clear()
}
