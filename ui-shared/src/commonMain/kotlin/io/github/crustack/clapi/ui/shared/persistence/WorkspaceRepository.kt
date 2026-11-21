package io.github.crustack.clapi.ui.shared.persistence

import androidx.compose.runtime.toMutableStateList
import io.github.crustack.clapi.core.model.RequestKind
import io.github.crustack.clapi.core.model.opencollection.FolderItem
import io.github.crustack.clapi.core.model.storage.StorageType
import io.github.crustack.clapi.core.storage.opencollection.CanonicalCollectionRepository
import io.github.crustack.clapi.core.storage.opencollection.CollectionStorageException
import io.github.crustack.clapi.core.storage.opencollection.CollectionValidationException
import io.github.crustack.clapi.core.storage.opencollection.DefaultCanonicalCollectionRepository
import io.github.crustack.clapi.core.storage.opencollection.OpenCollectionSerializer
import io.github.crustack.clapi.core.storage.opencollection.fileExists
import io.github.crustack.clapi.core.storage.opencollection.readTextFile
import io.github.crustack.clapi.core.storage.opencollection.writeTextFile
import io.github.crustack.clapi.ui.shared.platform.generateUuid
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.CollectionNode
import io.github.crustack.clapi.ui.shared.state.ProtoFileItemState
import io.github.crustack.clapi.ui.shared.state.ProtoImportPathState
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
            exportGitSyncedCollections(state)
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

    fun exportCollectionToGitRepository(state: AppState, collection: CollectionNode): String {
        val manager = state.gitRepositoryManager
        val colDir = manager.getCollectionDirectory(collection.gitRemoteUrl, collection.name)
        val doc = collection.toOpenCollectionDocumentForGitSync(colDir)
        val yaml = OpenCollectionSerializer.serializeYaml(doc)
        val yamlPath = "$colDir/collection.opencollection.yaml"
        writeTextFile(yamlPath, yaml)
        return yamlPath
    }

    private fun exportGitSyncedCollections(state: AppState) {
        val gitCollections = state.collections.filter {
            it.isCollection && it.storageType == StorageType.GIT && it.gitRemoteUrl.isNotBlank()
        }
        for (col in gitCollections) {
            runCatching {
                exportCollectionToGitRepository(state, col)
            }
        }
        if (gitCollections.isNotEmpty()) {
            state.refreshAllGitStatusesAsync()
        }
    }

    fun reloadCollectionsFromGitRepository(state: AppState, remoteUrl: String) {
        val manager = state.gitRepositoryManager
        var changed = false
        for (i in state.collections.indices) {
            val col = state.collections[i]
            if (col.isCollection && col.storageType == StorageType.GIT && col.gitRemoteUrl.trim() == remoteUrl.trim()) {
                val colDir = manager.getCollectionDirectory(col.gitRemoteUrl, col.name)
                val yamlPath = "$colDir/collection.opencollection.yaml"
                if (fileExists(yamlPath)) {
                    runCatching {
                        val content = readTextFile(yamlPath)
                        val doc = OpenCollectionSerializer.deserializeYaml(content)
                        val reloadedNode = doc.toCollectionNode(
                            nameOverride = col.name,
                            preserveIds = true,
                        ).copy(
                            id = col.id,
                            storageExtension = col.storageExtension,
                        )
                        state.collections[i] = reloadedNode
                        changed = true
                    }
                }
            }
        }
        if (changed) {
            state.notifyCollectionsChanged()
            runCatching {
                val globalDoc = state.toGlobalOpenCollectionDocument()
                runBlocking {
                    canonicalRepository.saveGlobalCollection(globalDoc)
                }
            }
        }
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
                val loadedItems = globalDoc.items.mapIndexed { index, item ->
                    item.toCollectionNode(
                        preserveIds = true,
                        clapiRequests = clapiRequests,
                        path = index.toString(),
                        existingIds = existingIds,
                    ).also { existingIds = existingIds + it.id }
                }

                // T028: Detect legacy single-collection files and wrap items into a primary collection on load
                val hasLegacySingleCollection = loadedItems.isNotEmpty() && loadedItems.any { !it.isCollection }
                if (hasLegacySingleCollection) {
                    val primaryName = if (globalDoc.info.name != "Workspace" && globalDoc.info.name.isNotBlank()) {
                        globalDoc.info.name
                    } else {
                        "Collection"
                    }
                    val primaryCol = CollectionNode(
                        id = generateUuid(),
                        name = primaryName,
                        kind = RequestKind.COLLECTION,
                        isFolder = true,
                        summary = globalDoc.info.summary,
                        version = globalDoc.info.version,
                        children = loadedItems.toMutableStateList(),
                    )
                    state.collections.add(primaryCol)
                } else {
                    state.collections.addAll(loadedItems)
                }

                state.environments.clear()
                state.environments.addAll(
                    globalDoc.config?.environments.orEmpty().map { it.toEnvState() }
                )

                state.collectionProtoFiles.clear()
                state.collectionProtoFiles.addAll(
                    globalDoc.config?.protobuf?.protoFiles.orEmpty()
                        .map { ProtoFileItemState(path = it.path) }
                )

                state.collectionImportPaths.clear()
                state.collectionImportPaths.addAll(
                    globalDoc.config?.protobuf?.importPaths.orEmpty()
                        .map { ProtoImportPathState(path = it.path, enabled = it.disabled != true) }
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
