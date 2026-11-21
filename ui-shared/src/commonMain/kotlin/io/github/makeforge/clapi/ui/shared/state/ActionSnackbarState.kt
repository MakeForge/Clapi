package io.github.makeforge.clapi.ui.shared.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

const val DEFAULT_SNACKBAR_AUTO_DISMISS_SEC = 5
const val MIN_SNACKBAR_AUTO_DISMISS_SEC = 1
const val MAX_SNACKBAR_AUTO_DISMISS_SEC = 60

/**
 * Immutable representation of a single transient action snackbar displayed in the bottom-right corner.
 */
data class ActionSnackbar(
    val id: Long,
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Boolean)? = null,
    val durationSecOverride: Int? = null,
    val isError: Boolean = false,
)

/**
 * Centralized, extensible snackbar controller enforcing:
 * - At most one visible snackbar at any time (new snackbars immediately replace any active one).
 * - Monotonically increasing IDs so timers always reset and stale dismissals are ignored.
 * - At-most-once execution of interactive actions (e.g., Undo) with graceful error fallback.
 */
class ActionSnackbarController {
    var current: ActionSnackbar? by mutableStateOf(null)
        private set

    private var nextId: Long = 0L
    private var consumedId: Long? = null
    private var currentFailureMessage: String = "Could not undo action"

    /**
     * Shows a new snackbar, immediately dismissing any currently visible snackbar.
     * Blank messages are ignored and return null.
     */
    fun show(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Boolean)? = null,
        durationSecOverride: Int? = null,
        isError: Boolean = false,
    ): Long? {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return null
        val effectiveLabel = if (onAction != null) actionLabel?.takeIf { it.isNotBlank() } else null
        val effectiveAction = if (effectiveLabel != null) onAction else null
        val id = ++nextId
        consumedId = null
        currentFailureMessage = "Could not undo action"
        current = ActionSnackbar(
            id = id,
            message = trimmed,
            actionLabel = effectiveLabel,
            onAction = effectiveAction,
            durationSecOverride = durationSecOverride?.coerceIn(
                MIN_SNACKBAR_AUTO_DISMISS_SEC,
                MAX_SNACKBAR_AUTO_DISMISS_SEC,
            ),
            isError = isError,
        )
        return id
    }

    /**
     * Convenience entry point for undoable user actions.
     * [onUndo] returns `true` when restoration succeeds, or `false` to show [failureMessage].
     */
    fun showUndoable(
        message: String,
        undoLabel: String = "Undo",
        failureMessage: String = "Could not undo action",
        onUndo: () -> Boolean,
    ): Long? {
        val id = show(
            message = message,
            actionLabel = undoLabel,
            onAction = onUndo,
        ) ?: return null
        currentFailureMessage = failureMessage
        return id
    }

    /**
     * Triggers the action associated with [id] at most once, immediately dismissing the snackbar.
     * Stale or repeated invocations are safely ignored.
     */
    fun triggerAction(id: Long) {
        val active = current ?: return
        if (active.id != id || consumedId == id) return
        val action = active.onAction ?: return
        val failureMessage = currentFailureMessage
        consumedId = id
        current = null
        val succeeded = runCatching { action() }.getOrElse { false }
        if (!succeeded) {
            show(message = failureMessage, isError = true)
        }
    }

    /**
     * Dismisses the currently visible snackbar. If [id] is provided, only dismisses when it
     * matches [current]'s id so stale timers cannot dismiss a newer snackbar.
     */
    fun dismiss(id: Long? = null) {
        val active = current ?: return
        if (id == null || active.id == id) {
            current = null
        }
    }
}
