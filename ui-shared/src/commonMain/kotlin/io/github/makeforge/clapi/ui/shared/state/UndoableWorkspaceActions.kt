package io.github.makeforge.clapi.ui.shared.state

import io.github.makeforge.clapi.ui.shared.components.sidebar.NodePlacement
import io.github.makeforge.clapi.ui.shared.components.sidebar.captureNodePlacements
import io.github.makeforge.clapi.ui.shared.components.sidebar.reinsertNodeAt
import io.github.makeforge.clapi.ui.shared.components.sidebar.restoreNodePlacements
import io.github.makeforge.clapi.ui.shared.i18n.Strings

/** Snapshot of a deleted collection, folder (with full subtree), or request node. */
data class DeletedNodeSnapshot(
    val node: CollectionNode,
    val parentId: String?,
    val index: Int,
)

/** Snapshot of a deleted environment and its prior selection index. */
data class DeletedEnvironmentSnapshot(
    val environment: EnvState,
    val index: Int,
    val previousSelectedEnvIndex: Int,
)

/** Snapshot of a removed history item. */
data class DeletedHistorySnapshot(
    val item: HistoryItem,
    val index: Int,
)

/**
 * Centralized helpers for registering undoable workspace actions with [AppState.snackbar].
 * Undo callbacks themselves do not re-trigger snackbars.
 */
object UndoableWorkspaceActions {

    fun showDeletedNodesSnackbar(
        state: AppState,
        snapshots: List<DeletedNodeSnapshot>,
        onAfterUndo: () -> Unit = {},
    ) {
        if (snapshots.isEmpty()) return
        val lang = state.settings.language
        val message = if (snapshots.size == 1) {
            Strings.resolve(lang, "snackbar_deleted_item").replace("{name}", snapshots.first().node.name)
        } else {
            Strings.resolve(lang, "snackbar_deleted_items").replace("{count}", snapshots.size.toString())
        }
        state.snackbar.showUndoable(
            message = message,
            undoLabel = Strings.resolve(lang, "undo"),
            failureMessage = Strings.resolve(lang, "snackbar_undo_failed"),
            onUndo = {
                var restoredAny = false
                for (snapshot in snapshots.sortedBy { it.index }) {
                    if (reinsertNodeAt(state.collections, snapshot.node, snapshot.parentId, snapshot.index)) {
                        restoredAny = true
                    }
                }
                if (restoredAny) {
                    state.notifyCollectionsChanged()
                    onAfterUndo()
                }
                restoredAny
            },
        )
    }

    fun showDeletedEnvironmentSnackbar(
        state: AppState,
        snapshot: DeletedEnvironmentSnapshot,
        onAfterUndo: () -> Unit = {},
    ) {
        val lang = state.settings.language
        val message = Strings.resolve(lang, "snackbar_deleted_item")
            .replace("{name}", snapshot.environment.name)
        state.snackbar.showUndoable(
            message = message,
            undoLabel = Strings.resolve(lang, "undo"),
            failureMessage = Strings.resolve(lang, "snackbar_undo_failed"),
            onUndo = {
                val targetIndex = snapshot.index.coerceIn(0, state.environments.size)
                state.environments.add(targetIndex, snapshot.environment)
                state.selectedEnvIndex = snapshot.previousSelectedEnvIndex
                    .coerceIn(0, state.environments.lastIndex)
                onAfterUndo()
                true
            },
        )
    }

    fun showDeletedHistorySnackbar(
        state: AppState,
        snapshots: List<DeletedHistorySnapshot>,
    ) {
        if (snapshots.isEmpty()) return
        val lang = state.settings.language
        val message = if (snapshots.size == 1) {
            Strings.resolve(lang, "snackbar_deleted_item").replace("{name}", snapshots.first().item.name)
        } else {
            Strings.resolve(lang, "snackbar_deleted_items").replace("{count}", snapshots.size.toString())
        }
        state.snackbar.showUndoable(
            message = message,
            undoLabel = Strings.resolve(lang, "undo"),
            failureMessage = Strings.resolve(lang, "snackbar_undo_failed"),
            onUndo = {
                for (snapshot in snapshots.sortedBy { it.index }) {
                    val idx = snapshot.index.coerceIn(0, state.historyItems.size)
                    state.historyItems.add(idx, snapshot.item)
                }
                state.historyRevision++
                true
            },
        )
    }

    /**
     * Shows an undoable snackbar after moving one or more nodes, suppressing no-op moves where
     * every node ended up in its exact original `(parentId, index)` placement.
     */
    fun showMovedNodesSnackbar(
        state: AppState,
        beforePlacements: List<NodePlacement>,
        movedNames: List<String>,
        onAfterUndo: () -> Unit = {},
    ) {
        if (beforePlacements.isEmpty()) return
        val afterPlacements = captureNodePlacements(state.collections, beforePlacements.map { it.nodeId })
        if (afterPlacements == beforePlacements) return

        val lang = state.settings.language
        val message = if (beforePlacements.size == 1 && movedNames.isNotEmpty()) {
            Strings.resolve(lang, "snackbar_moved_item").replace("{name}", movedNames.first())
        } else {
            Strings.resolve(lang, "snackbar_moved_items").replace("{count}", beforePlacements.size.toString())
        }
        state.snackbar.showUndoable(
            message = message,
            undoLabel = Strings.resolve(lang, "undo"),
            failureMessage = Strings.resolve(lang, "snackbar_undo_failed"),
            onUndo = {
                val restored = restoreNodePlacements(state.collections, beforePlacements)
                if (restored) {
                    state.notifyCollectionsChanged()
                    onAfterUndo()
                }
                restored
            },
        )
    }

    /** Looks up the display name of any node (request, folder, or collection) by ID. */
    fun findNodeNameById(collections: List<CollectionNode>, nodeId: String): String? {
        for (node in collections) {
            if (node.id == nodeId) return node.name
            if (node.isFolder && node.children.isNotEmpty()) {
                val found = findNodeNameById(node.children, nodeId)
                if (found != null) return found
            }
        }
        return null
    }

    /**
     * Shows an undoable snackbar after renaming an item, suppressing no-op renames where
     * [oldName] == [newName].
     */
    fun showRenamedItemSnackbar(
        state: AppState,
        oldName: String,
        newName: String,
        onUndo: () -> Boolean,
    ) {
        if (oldName == newName) return
        val lang = state.settings.language
        val message = Strings.resolve(lang, "snackbar_renamed_item")
            .replace("{oldName}", oldName)
            .replace("{newName}", newName)
        state.snackbar.showUndoable(
            message = message,
            undoLabel = Strings.resolve(lang, "undo"),
            failureMessage = Strings.resolve(lang, "snackbar_undo_failed"),
            onUndo = onUndo,
        )
    }
}
