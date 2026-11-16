package io.github.makeforge.clapi.ui.shared.shortcuts

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Represents a keyboard shortcut combination.
 *
 * When [ctrlOrMeta] is true, the shortcut matches if either Ctrl (Windows/Linux)
 * or Meta/Command (macOS) is pressed.
 */
data class KeyShortcut(
    val key: Key,
    val ctrlOrMeta: Boolean = false,
    val ctrl: Boolean = false,
    val meta: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
) {
    fun matches(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (event.key != key) return false

        if (ctrlOrMeta) {
            val hasModifier = event.isCtrlPressed || event.isMetaPressed
            if (!hasModifier) return false
        } else {
            if (event.isCtrlPressed != ctrl) return false
            if (event.isMetaPressed != meta) return false
        }

        if (event.isShiftPressed != shift) return false
        if (event.isAltPressed != alt) return false

        return true
    }
}

/**
 * Standard application hotkeys.
 * Easily extendable by adding new shortcuts or creating view-specific ones.
 */
object AppHotkeys {
    /** Save: Ctrl+S (or Cmd+S on macOS) */
    val Save = KeyShortcut(Key.S, ctrlOrMeta = true)

    /** Send request / Submit: Ctrl+Enter (or Cmd+Enter on macOS) */
    val Send = KeyShortcut(Key.Enter, ctrlOrMeta = true)

    /** Close active tab: Ctrl+W (or Cmd+W on macOS) */
    val CloseTab = KeyShortcut(Key.W, ctrlOrMeta = true)

    /** New tab: Ctrl+N (or Cmd+N on macOS) */
    val NewTab = KeyShortcut(Key.N, ctrlOrMeta = true)

    /** Open Settings dialog: Ctrl+, (or Cmd+, on macOS) */
    val Settings = KeyShortcut(Key.Comma, ctrlOrMeta = true)

    /** Find / Search: Ctrl+F (or Cmd+F on macOS) */
    val Find = KeyShortcut(Key.F, ctrlOrMeta = true)

    /** Move tab left: Ctrl+Shift+[ */
    val MoveTabLeft = KeyShortcut(Key.LeftBracket, ctrlOrMeta = true, shift = true)

    /** Move tab right: Ctrl+Shift+] */
    val MoveTabRight = KeyShortcut(Key.RightBracket, ctrlOrMeta = true, shift = true)

    /** Escape */
    val Escape = KeyShortcut(Key.Escape)
}

/**
 * Builder scope for declarative keyboard shortcut bindings.
 */
class HotkeyScope {
    private val bindings = mutableListOf<Pair<KeyShortcut, (KeyEvent) -> Boolean>>()

    /**
     * Registers an action for [shortcut] that consumes the event (returns true) upon invocation.
     */
    fun on(shortcut: KeyShortcut, action: () -> Unit) {
        bindings.add(shortcut to {
            action()
            true
        })
    }

    /**
     * Registers an action for [shortcut] that receives the [KeyEvent] and consumes the event upon invocation.
     */
    fun onKey(shortcut: KeyShortcut, action: (KeyEvent) -> Unit) {
        bindings.add(shortcut to { event ->
            action(event)
            true
        })
    }

    /**
     * Registers an action for [shortcut] that returns whether the event was handled.
     */
    fun onConditional(shortcut: KeyShortcut, action: (KeyEvent) -> Boolean) {
        bindings.add(shortcut to action)
    }

    /**
     * Dispatches [event] to the registered bindings in order.
     * Returns true if any binding handled the event, false otherwise.
     */
    fun handle(event: KeyEvent): Boolean {
        for ((shortcut, action) in bindings) {
            if (shortcut.matches(event)) {
                if (action(event)) return true
            }
        }
        return false
    }
}

/**
 * Attaches a declarative set of hotkey handlers to this modifier.
 */
fun Modifier.hotkeys(builder: HotkeyScope.() -> Unit): Modifier {
    val scope = HotkeyScope().apply(builder)
    return this.onPreviewKeyEvent { event ->
        scope.handle(event)
    }
}

/**
 * Convenience modifier to quickly attach a Save (Ctrl+S / Cmd+S) hotkey to any view or dialog.
 */
fun Modifier.onSaveHotkey(enabled: Boolean = true, onSave: () -> Unit): Modifier =
    if (enabled) {
        this.hotkeys {
            on(AppHotkeys.Save, onSave)
        }
    } else this
