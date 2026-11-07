package io.github.crustack.ui.shared.persistence

import io.github.crustack.core.storage.opencollection.CanonicalCollectionRepository
import io.github.crustack.core.storage.opencollection.CollectionStorageException
import io.github.crustack.core.storage.opencollection.CollectionValidationException
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

    private var lastReportedSaveError: String? = null

    /**
     * Persists collections and workspace metadata. Returns false and reports the failure to the user
     * if any step fails. Metadata is written only after every collection has been saved, so a rejected
     * document never leaves a partially updated workspace behind.
     */
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
            lastReportedSaveError = null
            true
        }.getOrElse { error ->
            reportSaveError(state, error)
            false
        }

    // Save runs on every state change, so the same failure is shown once until its message changes.
    private fun reportSaveError(state: AppState, error: Throwable) {
        val message = when (error) {
            is CollectionValidationException ->
                "A collection was not saved because it failed validation. ${error.message}"
            is CollectionStorageException ->
                "A collection could not be written to storage. ${error.message}"
            else -> "Workspace could not be saved. ${error.message}"
        }
        if (message == lastReportedSaveError) return
        lastReportedSaveError = message
        state.showError(title = "Save error", message = message)
    }

    fun load(state: AppState) {
        runCatching {
            val json = PlatformStorage.getString(STORAGE_KEY) ?: return
            val workspace = ImportExportRepository.decodeWorkspace(json)
            ImportExportRepository.replaceWorkspaceState(state, workspace)
        }
    }
}

