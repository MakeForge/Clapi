package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.yield

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
 * Requests initial focus for an input field when composed, typically used for the primary
 * input field in dialogs and modal forms so the user can immediately begin typing.
 *
 * @param enabled whether autofocus should occur (defaults to true).
 * @param focusRequester optional [FocusRequester] to attach; if omitted, one is remembered internally.
 */
@Composable
fun Modifier.autoFocus(
    enabled: Boolean = true,
    focusRequester: FocusRequester = remember { FocusRequester() },
): Modifier {
    if (!enabled) return this
    LaunchedEffect(focusRequester) {
        yield()
        val success = runCatching { focusRequester.requestFocus() }.isSuccess
        if (!success) {
            yield()
            runCatching { focusRequester.requestFocus() }
        }
    }
    return this.focusRequester(focusRequester)
}

/**
 * Remembers a [TextFieldValue] initialized with its entire text selected (if non-empty)
 * so that when the input receives auto-focus, the user can immediately overwrite the default value.
 *
 * Automatically synchronizes changes from the external [value] while maintaining user edits.
 *
 * @param value the external text value.
 * @param onValueChange callback invoked when the text changes.
 * @param selectAllOnInitial whether to select the entire initial text (defaults to true).
 * @return a pair of current [TextFieldValue] and an `onValueChange` handler accepting [TextFieldValue].
 */
@Composable
fun rememberAutoSelectTextFieldValue(
    value: String,
    onValueChange: (String) -> Unit,
    selectAllOnInitial: Boolean = true,
): Pair<TextFieldValue, (TextFieldValue) -> Unit> {
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = value,
                selection = if (selectAllOnInitial && value.isNotEmpty()) {
                    TextRange(0, value.length)
                } else {
                    TextRange(value.length)
                },
            ),
        )
    }

    var hasSelectedInitial by remember { mutableStateOf(value.isNotEmpty() && selectAllOnInitial) }

    LaunchedEffect(value) {
        if (!hasSelectedInitial && selectAllOnInitial && value.isNotEmpty()) {
            hasSelectedInitial = true
            textFieldValue = TextFieldValue(
                text = value,
                selection = TextRange(0, value.length),
            )
        } else if (value != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(
                text = value,
                selection = TextRange(value.length),
            )
        }
    }

    val onInternalChange: (TextFieldValue) -> Unit = { newTfv ->
        hasSelectedInitial = true
        textFieldValue = newTfv
        if (newTfv.text != value) {
            onValueChange(newTfv.text)
        }
    }

    return Pair(textFieldValue, onInternalChange)
}

/**
 * A [BasicTextField] tailored for dialogs and modal popups.
 * It automatically requests focus via [autoFocus] and selects all text if a default
 * value is present, allowing the user to immediately type over the default value.
 */
@Composable
fun DialogBasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
    focusRequester: FocusRequester? = null,
    selectAllOnInitial: Boolean = true,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = TextStyle.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    cursorBrush: Brush = SolidColor(Color.Black),
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit = @Composable { inner -> inner() },
) {
    val (textFieldValue, onTextFieldValueChange) = rememberAutoSelectTextFieldValue(
        value = value,
        onValueChange = onValueChange,
        selectAllOnInitial = selectAllOnInitial,
    )
    BasicTextField(
        value = textFieldValue,
        onValueChange = onTextFieldValueChange,
        modifier = modifier.autoFocus(
            enabled = autoFocus,
            focusRequester = focusRequester ?: remember { FocusRequester() },
        ),
        singleLine = singleLine,
        readOnly = readOnly,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        cursorBrush = cursorBrush,
        decorationBox = decorationBox,
    )
}

/**
 * Sets the desktop pointer hover icon to [PointerIcon.Hand] when [enabled],
 * or standard [PointerIcon.Default] when disabled.
 */
fun Modifier.handPointerHover(enabled: Boolean = true): Modifier =
    this.pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)


