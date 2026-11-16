package io.github.makeforge.clapi.ui.shared.components.executor

import io.github.makeforge.clapi.core.model.storage.StorageType
import io.github.makeforge.clapi.ui.shared.persistence.TabsRepository
import io.github.makeforge.clapi.ui.shared.persistence.WorkspaceRepository
import io.github.makeforge.clapi.ui.shared.platform.ioDispatcher
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.LogLevel
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun saveRequest(
    scope: CoroutineScope,
    state: AppState,
    tab: RequestTabState,
    onSaved: (() -> Unit)? = null,
) {
    tab.syncSystemHeaders()
    scope.launch {
        val checkpoint = tab.captureSaveCheckpoint()
        if (tab.isCollectionTab && tab.storageType == StorageType.GIT && tab.gitRemoteUrl.isNotBlank()) {
            try {
                withContext(ioDispatcher) {
                    state.gitRepositoryManager.ensureClonedOrCheckedOut(
                        tab.gitRemoteUrl.trim(),
                        tab.gitBranch.trim().ifBlank { "main" },
                    )
                }
                tab.storageError = null
            } catch (e: Exception) {
                tab.storageError = e.message ?: "Failed to clone or checkout Git repository"
                state.log("✗ Failed to configure Git storage: ${tab.storageError}", LogLevel.ERROR)
                return@launch
            }
        }
        val collectionSynced = tab.collectionName == null || state.syncTabToCollectionNode(tab)
        // The clean marker must be part of the persisted tab snapshot. Previously
        // TabsRepository.save ran first, so an immediate reload restored a dirty tab.
        tab.markSaved()
        val tabsSaved = withContext(ioDispatcher) { TabsRepository.save(state) }
        val workspaceSaved = if ((tab.isFolderTab || tab.isCollectionTab || tab.collectionName != null) && collectionSynced) {
            withContext(ioDispatcher) { WorkspaceRepository.save(state) }
        } else {
            collectionSynced
        }
        val ok = tabsSaved && workspaceSaved
        if (ok) {
            state.log("✓ Request saved: ${tab.name}", LogLevel.SUCCESS)
            onSaved?.invoke()
        } else {
            tab.restoreSaveCheckpoint(checkpoint.copy(isDirty = true))
            // Best effort: if tabs were written before workspace failed, put the
            // original dirty marker back on disk as well as in memory.
            withContext(ioDispatcher) { TabsRepository.save(state) }
            state.log("✗ Failed to save request: ${tab.name}", LogLevel.ERROR)
            state.showError(
                title = "Save failed",
                message = "The request could not be saved. Please try again.",
            )
        }
    }
}

// ── Internal helpers ────────────────────────────────────────────
