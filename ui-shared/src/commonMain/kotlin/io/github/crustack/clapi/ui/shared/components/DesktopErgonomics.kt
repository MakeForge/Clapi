package io.github.crustack.clapi.ui.shared.components

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

/**
 * Intercepts desktop keyboard `Key.Escape` events to dismiss a modal container.
 */
fun Modifier.onEscapeDismiss(
    enabled: Boolean = true,
    onDismiss: () -> Unit,
): Modifier {
    if (!enabled) return this
    return this.onKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
            onDismiss()
            true
        } else {
            false
        }
    }
}

/**
 * Ensures a modal container requests initial focus and intercepts `Key.Escape` to dismiss.
 */
@Composable
fun Modifier.dialogEscape(
    enabled: Boolean = true,
    onDismiss: () -> Unit,
): Modifier {
    if (!enabled) return this
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
    return this
        .focusRequester(focusRequester)
        .focusable()
        .onEscapeDismiss(enabled = enabled, onDismiss = onDismiss)
}

/**
 * Sets the desktop pointer hover icon to [PointerIcon.Hand] when [enabled],
 * or standard [PointerIcon.Default] when disabled.
 */
fun Modifier.handPointerHover(enabled: Boolean = true): Modifier =
    this.pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)

