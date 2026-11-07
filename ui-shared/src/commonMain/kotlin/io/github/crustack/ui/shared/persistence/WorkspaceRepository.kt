package io.github.crustack.ui.shared.persistence

import io.github.crustack.core.storage.opencollection.CanonicalCollectionRepository
import io.github.crustack.core.storage.opencollection.DefaultCanonicalCollectionRepository
import io.github.crustack.ui.shared.platform.PlatformStorage
import io.github.crustack.ui.shared.state.AppState
import kotlinx.coroutines.runBlocking

/**
 * Saves and restores workspace state (collections + environments) using [CanonicalCollectionRepository]
 * and [PlatformStorage].
 * Keeps open tabs and runtime state out of persisted collection documents (FR-024).
 */
object WorkspaceRepository {

    private const val STORAGE_KEY = "clapi.workspace"

    var canonicalRepository: CanonicalCollectionRepository = DefaultCanonicalCollectionRepository()

    fun save(state: AppState): Boolean =
        runCatching {
            // Persist collections canonically as OpenCollection documents
            runBlocking {
                for (collection in state.collections) {
                    val doc = collection.toOpenCollectionDocument()
                    canonicalRepository.saveCollection(doc)
                }
            }
            // Persist workspace metadata (environments, global variables, history, expanded state)
            val json = ImportExportRepository.exportWorkspaceToString(state)
            PlatformStorage.putString(STORAGE_KEY, json)
            true
        }.getOrDefault(false)

    fun load(state: AppState) {
        runCatching {
            val json = PlatformStorage.getString(STORAGE_KEY) ?: return
            val workspace = ImportExportRepository.decodeWorkspace(json)
            ImportExportRepository.replaceWorkspaceState(state, workspace)
        }
    }
}

