package io.github.crustack.ui.shared.persistence

import io.github.crustack.ui.shared.platform.PlatformStorage
import io.github.crustack.ui.shared.state.AppState

/**
 * Saves and restores workspace state (collections + environments) using PlatformStorage.
 */
object WorkspaceRepository {

    private const val STORAGE_KEY = "clapi.workspace"

    fun save(state: AppState): Boolean =
        runCatching {
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
