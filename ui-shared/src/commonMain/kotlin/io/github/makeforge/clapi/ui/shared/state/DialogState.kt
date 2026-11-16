package io.github.makeforge.clapi.ui.shared.state

import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Job

/**
 * Feature dialogs owned by the shell. Several can be open at once (e.g. Help opened from Settings);
 * the last entry in [DialogState.dialogs] is the top-most one.
 */
sealed interface AppDialog {
    data object Settings : AppDialog
    data object Help : AppDialog
    data object GlobalVariables : AppDialog
    data object ImportSpecification : AppDialog
    data object ExportCollections : AppDialog
    data object GitSync : AppDialog
    /** [index] is the position in the environments list. */
    data class EnvironmentEdit(val index: Int) : AppDialog
}

/**
 * Alert-style dialogs (confirm / long-running operation / error). They are rendered above all
 * [AppDialog]s and each kind exists at most once, so a newer one replaces the previous one.
 */
sealed interface AppAlert {
    class Confirm(val title: String, val message: String, val onConfirm: () -> Unit) : AppAlert
    class Operation(val title: String, val message: String, val job: Job) : AppAlert
    class Error(val title: String, val message: String) : AppAlert
}

/**
 * Observable dialog state. Replaces one boolean (plus ad-hoc title/message fields) per dialog with
 * two small stacks; each alert carries its own data, which makes illegal combinations unrepresentable.
 *
 * [AppState] exposes boolean-compatible properties on top of this for source compatibility.
 */
class DialogState {
    private val _dialogs = mutableStateListOf<AppDialog>()
    private val _alerts = mutableStateListOf<AppAlert>()

    /** Open feature dialogs, bottom to top. */
    val dialogs: List<AppDialog> get() = _dialogs

    /** Open alerts, bottom to top. */
    val alerts: List<AppAlert> get() = _alerts

    // ── feature dialogs ─────────────────────────────────────────────────────

    fun isShown(dialog: AppDialog): Boolean = dialog in _dialogs

    /** Currently open environment editor, if any. */
    val environmentEdit: AppDialog.EnvironmentEdit?
        get() = _dialogs.filterIsInstance<AppDialog.EnvironmentEdit>().lastOrNull()

    /** Opens [dialog] on top. Already-open dialogs keep their position; only one environment editor exists. */
    fun show(dialog: AppDialog) {
        if (dialog is AppDialog.EnvironmentEdit) {
            _dialogs.removeAll { it is AppDialog.EnvironmentEdit }
        } else if (dialog in _dialogs) {
            return
        }
        _dialogs.add(dialog)
    }

    fun dismiss(dialog: AppDialog) {
        _dialogs.remove(dialog)
    }

    fun setShown(dialog: AppDialog, shown: Boolean) = if (shown) show(dialog) else dismiss(dialog)

    fun dismissEnvironmentEdit() {
        _dialogs.removeAll { it is AppDialog.EnvironmentEdit }
    }

    // ── alerts ──────────────────────────────────────────────────────────────

    inline fun <reified T : AppAlert> alert(): T? = alerts.filterIsInstance<T>().lastOrNull()

    private inline fun <reified T : AppAlert> replace(alert: T) {
        _alerts.removeAll { it is T }
        _alerts.add(alert)
    }

    private inline fun <reified T : AppAlert> remove() {
        _alerts.removeAll { it is T }
    }

    /** Show a confirm dialog and run [action] if the user confirms. */
    fun showConfirm(title: String, message: String, action: () -> Unit) =
        replace(AppAlert.Confirm(title, message, action))

    fun resolveConfirm(confirmed: Boolean) {
        val confirm = alert<AppAlert.Confirm>()
        // Remove first so the action may itself raise a new confirm dialog.
        remove<AppAlert.Confirm>()
        if (confirmed) confirm?.onConfirm?.invoke()
    }

    fun startOperation(title: String, message: String, job: Job) =
        replace(AppAlert.Operation(title, message, job))

    fun finishOperation() = remove<AppAlert.Operation>()

    /** Cancels the tracked operation job and closes the dialog. */
    fun cancelOperation() {
        alert<AppAlert.Operation>()?.job?.cancel()
        finishOperation()
    }

    fun showError(title: String, message: String) = replace(AppAlert.Error(title, message))

    fun dismissError() = remove<AppAlert.Error>()
}
