package io.github.crustack.core.storage

import io.github.crustack.core.model.CollectionDefinition
import io.github.crustack.core.model.EnvironmentDefinition
import io.github.crustack.core.model.HistoryEntry
import io.github.crustack.core.model.RequestDefinition
import kotlinx.coroutines.flow.Flow

interface RequestRepository {
    fun observeAll(): Flow<List<RequestDefinition>>
    suspend fun upsert(request: RequestDefinition)
    suspend fun delete(requestId: String)
}

interface CollectionRepository {
    fun observeAll(): Flow<List<CollectionDefinition>>
    suspend fun upsert(collection: CollectionDefinition)
    suspend fun delete(collectionId: String)
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
