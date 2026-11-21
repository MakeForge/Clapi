package io.github.crustack.clapi.ui.shared.persistence

import io.github.crustack.clapi.core.model.opencollection.FolderItem
import io.github.crustack.clapi.core.storage.opencollection.CanonicalCollectionRepository
import io.github.crustack.clapi.core.storage.opencollection.CollectionStorageException
import io.github.crustack.clapi.core.storage.opencollection.CollectionValidationException
import io.github.crustack.clapi.core.storage.opencollection.DefaultCanonicalCollectionRepository
import io.github.crustack.clapi.ui.shared.state.AppState
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Saves and restores workspace state using [CanonicalCollectionRepository] for the single global
 * OpenCollection document (collections as top-level folders, environments in `config.environments`)
 * and [PrivateStateRepository] for global variables, request history, and expanded tree state.
 */
object WorkspaceRepository {

    var canonicalRepository: CanonicalCollectionRepository = DefaultCanonicalCollectionRepository()
        set(value) {
            field = value
            if (value is DefaultCanonicalCollectionRepository) {
                privateStateRepository = PrivateStateRepository(baseDirectory = value.baseDirectory)
            }
        }

    var privateStateRepository: PrivateStateRepository = PrivateStateRepository()

    private var lastReportedSaveError: String? = null

    /**
     * Persists collections and environments to the single global OpenCollection document, and
     * global variables, history, and expanded state to [privateStateRepository].
     * Returns false and reports the failure to the user if any step fails.
     */
    fun save(state: AppState): Boolean =
        runCatching {
            val globalDoc = state.toGlobalOpenCollectionDocument()
            runBlocking {
                canonicalRepository.saveGlobalCollection(globalDoc)
            }
            privateStateRepository.save(
                PrivateWorkspaceState(
                    globalVariables = state.globalVariables
                        .filter { it.key.trim().isNotEmpty() }
                        .map { it.toStoredVariable() },
                    history = state.historyItems.map { it.toStoredHistoryItem() },
                    expandedState = state.collectionExpandedState.toMap(),
                )
            )
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
            val globalDoc = runBlocking { canonicalRepository.loadGlobalCollection() }
            val privateState = privateStateRepository.load()
            if (globalDoc == null && privateState == null) return

            if (globalDoc != null) {
                val clapiRequests = (globalDoc.extensions["clapi"] as? JsonObject)?.get("requests")?.jsonObject
                state.collections.clear()
                var existingIds = emptySet<String>()
                globalDoc.items.filterIsInstance<FolderItem>().forEachIndexed { index, folder ->
                    val node = folder.toCollectionNode(
                        preserveIds = true,
                        clapiRequests = clapiRequests,
                        path = index.toString(),
                        existingIds = existingIds,
                    )
                    state.collections.add(node)
                    existingIds = existingIds + node.id
                }

                state.environments.clear()
                state.environments.addAll(
                    globalDoc.config?.environments.orEmpty().map { it.toEnvState() }
                )
                val savedName = state.settings.selectedEnvName
                state.selectedEnvIndex = when {
                    state.environments.isEmpty() -> 0
                    savedName.isNotEmpty() -> {
                        val idx = state.environments.indexOfFirst { it.name == savedName }
                        if (idx >= 0) idx else 0
                    }
                    else -> state.selectedEnvIndex.coerceIn(0, state.environments.lastIndex)
                }
            }

            if (privateState != null) {
                state.globalVariables.clear()
                state.globalVariables.addAll(
                    privateState.globalVariables.map { it.toMutableKeyValue() }
                )

                state.replaceHistoryItems(
                    privateState.history.map { it.toHistoryItem() }
                )

                state.collectionExpandedState.clear()
                state.collectionExpandedState.putAll(privateState.expandedState)
            }
        }
    }
}
