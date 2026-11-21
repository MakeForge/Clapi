# Internal UI & State Contract: Action Snackbar

**Feature**: `033-undo-action-snackbar` | **Date**: 2026-11-21

This contract defines the centralized, extensible API for triggering and rendering action snackbars across Clapi's `ui-shared` module, plus the test-tag contract for automated UI tests.

---

## 1. Controller API (`ActionSnackbarController`)

Location: `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/ActionSnackbarState.kt`

```kotlin
const val DEFAULT_SNACKBAR_AUTO_DISMISS_SEC = 5
const val MIN_SNACKBAR_AUTO_DISMISS_SEC = 1
const val MAX_SNACKBAR_AUTO_DISMISS_SEC = 60

data class ActionSnackbar(
    val id: Long,
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Boolean)? = null,
    val durationSecOverride: Int? = null,
    val isError: Boolean = false,
)

class ActionSnackbarController {
    var current: ActionSnackbar? by mutableStateOf(null)
        private set

    /**
     * Immediately replaces any visible snackbar with a new one and assigns a fresh [ActionSnackbar.id].
     */
    fun show(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Boolean)? = null,
        durationSecOverride: Int? = null,
        isError: Boolean = false,
    ): Long?

    /**
     * Convenience entry point for undoable actions.
     * [onUndo] returns true if restoration succeeded, or false to show [failureMessage].
     */
    fun showUndoable(
        message: String,
        undoLabel: String = "Undo",
        failureMessage: String = "Could not undo action",
        onUndo: () -> Boolean,
    ): Long?

    /**
     * Executes the action for [id] at most once, dismissing the snackbar immediately.
     * Ignored if [current] has a different id or was already consumed.
     */
    fun triggerAction(id: Long)

    /**
     * Dismisses the current snackbar if [id] is null or matches [current.id].
     */
    fun dismiss(id: Long? = null)
}
```

### Behavioral Guarantees
1. **Single Active Snackbar**: Calling `show(...)` or `showUndoable(...)` while `current != null` synchronously replaces `current` with a new `ActionSnackbar` carrying `id = ++nextId`.
2. **At-Most-Once Execution**: Calling `triggerAction(id)` twice for the same `id` invokes `onAction` only on the first call.
3. **Stale Timer Safety**: Calling `dismiss(oldId)` after `current` has already been replaced by `newId` is a no-op.
4. **Error Resilience**: If `onAction()` throws an exception or returns `false`, the controller catches it safely and displays a non-undoable error snackbar (`isError = true`).

---

## 2. Host Composable Contract (`ActionSnackbarHost`)

Location: `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ActionSnackbarHost.kt`

```kotlin
@Composable
fun ActionSnackbarHost(
    controller: ActionSnackbarController,
    autoDismissSec: Int,
    modifier: Modifier = Modifier,
)
```

### Layout & Interaction Contract
- Anchored inside `MainScreen`'s root `Box` with `Modifier.align(Alignment.BottomEnd).padding(16.dp)`.
- Non-modal: does not intercept pointer or keyboard events outside its own bounds.
- Auto-dismisses via `LaunchedEffect(active?.id, effectiveDurationMs)` calling `delay(effectiveDurationMs)` followed by `controller.dismiss(active.id)`.
- Renders at most one snackbar at any time (no crossfade between two snackbars).
- Truncates long messages cleanly with `TextOverflow.Ellipsis` (`maxLines = 2`) while keeping the **Undo** button and close icon always visible and clickable.

---

## 3. UI Test Tags Contract

| Element | `testTag` |
|---|---|
| Active snackbar container | `action-snackbar` |
| Snackbar message text | `action-snackbar-message` |
| Snackbar Undo action button | `action-snackbar-undo` |
| Snackbar manual dismiss button | `action-snackbar-dismiss` |
| General Settings auto-dismiss duration input | `snackbar-auto-dismiss-input` |

---

## 4. Settings Storage & Docs Contract

- **Settings Field**: `AppSettings.snackbarAutoDismissSec: Int` (default `5`, bounded `1..60`).
- **Storage Key**: `"settings.snackbarAutoDismissSec"` in `SettingsRepository`.
- **i18n Keys** (all 4 locales: `en.json`, `de.json`, `es.json`, `fr.json`):
  - `undo`
  - `snackbar_auto_dismiss_seconds`
  - `settings_snackbar_auto_dismiss_desc`
  - `snackbar_deleted_item`, `snackbar_deleted_items`
  - `snackbar_moved_item`, `snackbar_moved_items`
  - `snackbar_renamed_item`
  - `snackbar_undo_failed`
- **Documentation**: `docs/settings/settings.md` documents the **Snackbar Auto-Dismiss Duration** setting under General settings.
