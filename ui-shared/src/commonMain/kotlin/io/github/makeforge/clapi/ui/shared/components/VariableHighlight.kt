package io.github.makeforge.clapi.ui.shared.components

import io.github.makeforge.clapi.core.network.VariableResolver
import io.github.makeforge.clapi.ui.shared.components.variable.VariableDropdownList as VariableDropdownListImpl
import io.github.makeforge.clapi.ui.shared.components.variable.VariableEditorPopup as VariableEditorPopupImpl
import io.github.makeforge.clapi.ui.shared.components.variable.VariableSourceInfo
import io.github.makeforge.clapi.ui.shared.components.variable.resolveVariableSourceInfo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_close
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.shortcuts.AppHotkeys
import io.github.makeforge.clapi.ui.shared.shortcuts.hotkeys
import io.github.makeforge.clapi.ui.shared.platform.handleCtrlSpaceKeyEvent
import io.github.makeforge.clapi.ui.shared.platform.isRecentCtrlSpaceTriggered
import io.github.makeforge.clapi.ui.shared.platform.resetCtrlSpaceTrigger
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

// ── Constants ─────────────────────────────────────────────────────

/**
 * Variable token pattern used across URL/body/header editors.
 *
 * Postman exports and real-world projects frequently use names like
 * `{{base-url}}`, `{{api.version}}`, or `{{ my_var }}`. We accept any
 * non-brace content inside `{{...}}` and normalize surrounding whitespace.
 */
private val VARIABLE_REGEX = Regex("""\{\{\s*([^{}\r\n]+?)\s*\}\}""")
internal const val VARIABLE_TAG = "variable"
internal fun normalizeVariableDisplayName(rawName: String): String =
    rawName.trim().trim('{', '}').trim()


/** Orange token colour — bright amber so it clearly stands out against both dark and light editor backgrounds. */
internal val VariableHighlightColor = Color(0xFFF97316)

/** Red token colour — shown when the variable has no value in the active environment. */
internal val VariableUnresolvedColor = Color(0xFFEF4444)

private val VARIABLE_SPAN_STYLE = SpanStyle(
    color = VariableHighlightColor,
    fontWeight = FontWeight.SemiBold,
    background = VariableHighlightColor.copy(alpha = 0.15f),
)

private val VARIABLE_UNRESOLVED_SPAN_STYLE = SpanStyle(
    color = VariableUnresolvedColor,
    fontWeight = FontWeight.SemiBold,
    background = VariableUnresolvedColor.copy(alpha = 0.12f),
)

// ── Public utilities (also used in unit tests) ─────────────────────

/** Returns every `{{name}}` variable name found in [text], in order. */
fun parseVariableNames(text: String): List<String> =
    VARIABLE_REGEX.findAll(text)
        .map { normalizeVariableDisplayName(it.groupValues[1]) }
        .filter { it.isNotEmpty() }
        .toList()

/** Returns the variable name under [offset], if the cursor is inside `{{...}}`. */
fun variableNameAtOffset(text: String, offset: Int): String? {
    if (offset < 0 || offset > text.length) return null
    for (match in VARIABLE_REGEX.findAll(text)) {
        val start = match.range.first
        val endExclusive = match.range.last + 1
        if (offset in start..endExclusive) {
            val name = normalizeVariableDisplayName(match.groupValues[1])
            if (name.isNotEmpty()) return name
        }
    }
    return null
}

/**
 * Returns per-character-range color spans for every `{{...}}` token in [line].
 *
 * Tokens whose name is present in [definedNames] use [VariableHighlightColor] (orange).
 * Tokens that are not defined anywhere use [VariableUnresolvedColor] (red), so the user
 * can immediately see which variables are missing from the active environment.
 */
fun variableRangesForLine(
    line: String,
    definedNames: Set<String>,
): List<Pair<IntRange, Color>> =
    VARIABLE_REGEX.findAll(line).mapNotNull { match ->
        val name = normalizeVariableDisplayName(match.groupValues[1])
        if (name.isEmpty()) return@mapNotNull null
        val color = if (name in definedNames) VariableHighlightColor else VariableUnresolvedColor
        match.range to color
    }.toList()

/**
 * Builds an [AnnotatedString] that colour-highlights every `{{variable}}`
 * token with [VARIABLE_SPAN_STYLE] (orange) and attaches a VARIABLE_TAG string
 * annotation containing the variable name for click/cursor detection.
 *
 * All tokens are treated as resolved. Use [highlightVariablesWithStatus] when
 * you have the set of defined variable names available.
 */
fun highlightVariables(text: String): AnnotatedString = buildAnnotatedString {
    var lastIndex = 0
    for (match in VARIABLE_REGEX.findAll(text)) {
        append(text.substring(lastIndex, match.range.first))
        val spanStart = length
        withStyle(VARIABLE_SPAN_STYLE) { append(match.value) }
        val variableName = normalizeVariableDisplayName(match.groupValues[1])
        if (variableName.isNotEmpty()) {
            addStringAnnotation(VARIABLE_TAG, variableName, spanStart, length)
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) append(text.substring(lastIndex))
}

/**
 * Builds an [AnnotatedString] that colour-highlights every `{{variable}}` token
 * using **two colours** depending on resolution status:
 *  - **Orange** ([VARIABLE_SPAN_STYLE]) — the name appears in [definedNames] (resolved)
 *  - **Red** ([VARIABLE_UNRESOLVED_SPAN_STYLE]) — the name is absent from [definedNames]
 *
 * Also attaches VARIABLE_TAG string annotations so click-to-edit works the same way.
 */
fun highlightVariablesWithStatus(text: String, definedNames: Set<String>): AnnotatedString =
    buildAnnotatedString {
        var lastIndex = 0
        for (match in VARIABLE_REGEX.findAll(text)) {
            append(text.substring(lastIndex, match.range.first))
            val spanStart = length
            val varName = normalizeVariableDisplayName(match.groupValues[1])
            val spanStyle = if (varName.isNotEmpty() && varName in definedNames)
                VARIABLE_SPAN_STYLE
            else
                VARIABLE_UNRESOLVED_SPAN_STYLE
            withStyle(spanStyle) { append(match.value) }
            if (varName.isNotEmpty()) {
                addStringAnnotation(VARIABLE_TAG, varName, spanStart, length)
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < text.length) append(text.substring(lastIndex))
    }

// ── VariableAwareTextField ────────────────────────────────────────────

/**
 * A drop-in replacement for a plain [BasicTextField] that:
 *
 *  1. **Colour-highlights** every `{{variable}}` token in orange.
 *  2. **Shows a floating popup ONLY when the user explicitly CLICKS** on a
 *     highlighted variable span — never on keyboard events, never on startup.
 *
 * ### Popup trigger design (Fixes Issues 1 & 4)
 *
 * The old implementation fired `LaunchedEffect(fieldValue.selection)` on every
 * cursor change, including typing, Backspace, and app initialisation, causing
 * the popup to appear on startup (Issue 1) and when pressing Backspace (Issue 4).
 *
 * The new design uses a two-step click-detection pattern:
 *  - A `pendingClickRef` flag (a plain non-State object, so toggling it never
 *    triggers recomposition) is set to `true` ONLY by real pointer-press events.
 *  - Inside `onValueChange`, the flag is read and immediately cleared. Only if
 *    it was set will the annotation at the new cursor position be checked.
 *  - Keyboard events (typing, Backspace, arrows) call `onValueChange` too, but
 *    they never set `pendingClickRef`, so they can never open the popup.
 *
 * When [state] is `null` the component degrades gracefully to a plain text
 * field with no highlighting or popup.
 */
@Composable
fun VariableAwareTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    /**
     * Optional per-instance undo stack. When non-null, Cmd/Ctrl+Z will pop the
     * last value and restore it. Supply [RequestTabState.urlUndoStack] from the
     * call site that has access to the tab.
     */
    undoStack: ArrayDeque<String>? = null,
    textStyle: TextStyle = TextStyle(
        color = ClapiColors.OnSurface,
        fontSize = 14.sp,
        fontFamily = CodeFontFamily,
    ),
    state: AppState? = null,
    tab: RequestTabState? = state?.activeTab,
    readOnly: Boolean = false,
    extraVariables: List<Pair<String, String>> = emptyList(),
    excludeVariables: Set<String> = emptySet(),
    selectAllOnStart: Boolean = false,
) {
    val normalizedExcludes = remember(excludeVariables) {
        if (excludeVariables.isEmpty()) emptySet()
        else excludeVariables
            .filter { it.isNotBlank() }
            .flatMap {
                val clean = it.trim().removeSurrounding("{{", "}}").trim()
                val withoutSecret = clean.removePrefix("_secret.")
                listOf(clean, withoutSecret, "_secret.$withoutSecret")
            }
            .toSet()
    }

    // Resolved variable names from whatever environment/folder/collection layers are active.
    // Memoized with derivedStateOf so typing inside the text field does not rebuild activeVariableLayers on every keystroke.
    val currentTab = tab ?: state?.activeTab
    val definedNames: Set<String> by remember(state, currentTab, extraVariables, normalizedExcludes) {
        derivedStateOf {
            if (state != null || extraVariables.isNotEmpty()) {
                val vars = if (state != null) {
                    val set = state.activeVariableLayers(currentTab).flatMap { it.keys }.toMutableSet()
                    state.selectedEnvironment?.externalSecrets?.filter { it.enabled && it.name.isNotBlank() }?.forEach {
                        set.add("_secret.${it.name.trim()}")
                    }
                    val effectiveAuthType = if (currentTab != null) {
                        if (currentTab.authType == AuthType.INHERIT) {
                            state.resolveInheritedAuth(currentTab)?.authType ?: AuthType.NONE
                        } else {
                            currentTab.authType
                        }
                    } else {
                        AuthType.NONE
                    }
                    if (effectiveAuthType == AuthType.OAUTH2) {
                        set.add("_auth.oauth2.accessToken")
                    }
                    VariableResolver.dynamicVariables.forEach { dv ->
                        set.add(dv.name)
                        set.add(dv.name.removePrefix("$"))
                    }
                    set
                } else {
                    mutableSetOf()
                }
                extraVariables.forEach { (k, _) ->
                    if (k.isNotBlank()) vars.add(k.trim())
                }
                if (normalizedExcludes.isNotEmpty()) {
                    vars.removeAll(normalizedExcludes)
                }
                vars.toSet()
            } else {
                emptySet()
            }
        }
    }

    // Internal TextFieldValue that carries the highlighted AnnotatedString.
    var hasSelectedInitial by remember { mutableStateOf(selectAllOnStart && value.isNotEmpty()) }
    var fieldValue by remember {
        val annotated = if (state != null || extraVariables.isNotEmpty()) highlightVariablesWithStatus(value, definedNames)
                        else AnnotatedString(value)
        mutableStateOf(
            TextFieldValue(
                annotatedString = annotated,
                selection = if (selectAllOnStart && value.isNotEmpty()) TextRange(0, value.length) else TextRange.Zero,
            ),
        )
    }

    // Popup visibility — null means hidden. Set ONLY from the click handler
    // inside onValueChange. Never set during initialisation (fixes Issue 1).
    var popupVariable by remember { mutableStateOf<String?>(null) }
    var showVariableDropdown by remember { mutableStateOf(false) }
    var selectedVariableIndex by remember { mutableIntStateOf(0) }
    val availableVariables = remember(state, currentTab, definedNames, extraVariables, normalizedExcludes) {
        val base = getAvailableVariables(state, currentTab)
        val allVars = if (extraVariables.isEmpty()) {
            base
        } else {
            val combinedMap = linkedMapOf<String, String>()
            for ((k, v) in extraVariables) {
                if (k.isNotBlank()) combinedMap[k] = v
            }
            for ((k, v) in base) {
                if (!combinedMap.containsKey(k)) combinedMap[k] = v
            }
            combinedMap.entries.map { it.key to it.value }.sortedBy { it.first }
        }
        if (normalizedExcludes.isEmpty()) {
            allVars
        } else {
            allVars.filter { (name, _) ->
                val trimmed = name.trim()
                trimmed !in normalizedExcludes &&
                    trimmed.removePrefix("_secret.") !in normalizedExcludes
            }
        }
    }

    val insertVariableAtCursor: (String) -> Unit = { varName ->
        val token = "{{$varName}}"
        val currentText = fieldValue.text
        val selStart = fieldValue.selection.min.coerceIn(0, currentText.length)
        val selEnd = fieldValue.selection.max.coerceIn(0, currentText.length)
        val textBefore = currentText.substring(0, selStart)
        val lastOpen = textBefore.lastIndexOf("{{")
        val lastClose = textBefore.lastIndexOf("}}")
        val replaceStart = if (lastOpen != -1 && lastOpen > lastClose) lastOpen else selStart
        val newText = currentText.replaceRange(replaceStart, selEnd, token)
        val newCursor = replaceStart + token.length
        val annotated = if (state != null || extraVariables.isNotEmpty()) highlightVariablesWithStatus(newText, definedNames)
                        else AnnotatedString(newText)
        if (undoStack != null) {
            undoStack.addLast(currentText)
            if (undoStack.size > 100) undoStack.removeFirst()
        }
        fieldValue = TextFieldValue(annotatedString = annotated, selection = TextRange(newCursor))
        onValueChange(newText)
    }

    // Non-State flag: toggled by pointer-press, read+cleared in onValueChange.
    // Using a plain object (not mutableStateOf) ensures setting it never
    // triggers an unnecessary recomposition.
    val pendingClickRef = remember { object { var value = false } }

    // Sync when the external text value changes (e.g. params table updates the URL).
    // Use cursor-at-end rather than copy(annotatedString) to avoid a stale selection
    // that is out-of-range for the new value (e.g. switching request tabs).
    LaunchedEffect(value) {
        if (!hasSelectedInitial && selectAllOnStart && value.isNotEmpty()) {
            hasSelectedInitial = true
            val annotated = if (state != null || extraVariables.isNotEmpty()) highlightVariablesWithStatus(value, definedNames)
                            else AnnotatedString(value)
            fieldValue = TextFieldValue(annotatedString = annotated, selection = TextRange(0, value.length))
        } else if (fieldValue.text != value) {
            val annotated = if (state != null || extraVariables.isNotEmpty()) highlightVariablesWithStatus(value, definedNames)
                            else AnnotatedString(value)
            fieldValue = TextFieldValue(annotatedString = annotated, selection = TextRange(annotated.length))
        }
    }

    // Re-colour when the active environment/variables change without a text change
    // (e.g. user activates a different env or adds a new variable).
    LaunchedEffect(definedNames) {
        if (state != null || extraVariables.isNotEmpty()) {
            val annotated = highlightVariablesWithStatus(fieldValue.text, definedNames)
            fieldValue = fieldValue.copy(annotatedString = annotated)
        }
    }

    // Box is a bare container (no modifier): it just groups BasicTextField and
    // the popup trigger so both are emitted at the same composable level.
    // The full caller `modifier` (including testTag) goes on BasicTextField
    // directly so that semantic actions like RequestFocus remain accessible —
    // critical for Compose test performTextInput() to locate and focus the field.
    Box {
        BasicTextField(
            value = fieldValue,
            readOnly = readOnly,
            onValueChange = { new ->
                // If Ctrl+Space was triggered and a whitespace was typed, suppress the whitespace.
                if (isRecentCtrlSpaceTriggered() && new.text.length == fieldValue.text.length + 1) {
                    val cursor = fieldValue.selection.min
                    if (cursor in new.text.indices && new.text[cursor] == ' ' &&
                        new.text.removeRange(cursor, cursor + 1) == fieldValue.text
                    ) {
                        resetCtrlSpaceTrigger()
                        fieldValue = fieldValue.copy(selection = fieldValue.selection)
                        return@BasicTextField
                    }
                }
                resetCtrlSpaceTrigger()
                val annotated = if (state != null || extraVariables.isNotEmpty()) highlightVariablesWithStatus(new.text, definedNames)
                                else AnnotatedString(new.text)
                val newFieldValue = TextFieldValue(
                    annotatedString = annotated,
                    selection = new.selection,
                    composition = new.composition,
                )
                // Push previous text to undo stack when text actually changes (not just cursor)
                if (undoStack != null && new.text != fieldValue.text) {
                    undoStack.addLast(fieldValue.text)
                    if (undoStack.size > 100) undoStack.removeFirst()
                }
                hasSelectedInitial = true
                fieldValue = newFieldValue
                onValueChange(new.text)

                // Evaluate popup only when a pointer click caused this change.
                // Keyboard events (typing, Backspace, arrow keys) never reach
                // this branch — they cannot open the popup (fixes Issues 1 & 4).
                if (pendingClickRef.value) {
                    pendingClickRef.value = false
                    if (state != null && new.selection.collapsed) {
                        val cursor = new.selection.start
                        val found = annotated
                            .getStringAnnotations(VARIABLE_TAG, cursor, cursor)
                            .firstOrNull()
                        // null → close any open popup (click outside a token)
                        popupVariable = found?.item
                    } else {
                        popupVariable = null
                    }
                }
            },
            singleLine = singleLine,
            textStyle = textStyle,
            cursorBrush = SolidColor(ClapiColors.Primary),
            // Apply the caller's modifier (layout + testTag) here so the
            // BasicTextField node owns the tag and its RequestFocus action.
            // The pointerInput observer is appended to detect real clicks only.
            modifier = modifier
                .onPreviewKeyEvent { event ->
                    if (handleCtrlSpaceKeyEvent(event) {
                        if (availableVariables.isNotEmpty()) {
                            selectedVariableIndex = 0
                            showVariableDropdown = true
                        }
                    }) {
                        return@onPreviewKeyEvent true
                    }
                    if (event.type == KeyEventType.KeyDown) {
                        if (showVariableDropdown) {
                            when (event.key) {
                                Key.DirectionDown -> {
                                    if (availableVariables.isNotEmpty()) {
                                        selectedVariableIndex = (selectedVariableIndex + 1).coerceAtMost(availableVariables.size - 1)
                                    }
                                    return@onPreviewKeyEvent true
                                }
                                Key.DirectionUp -> {
                                    if (availableVariables.isNotEmpty()) {
                                        selectedVariableIndex = (selectedVariableIndex - 1).coerceAtLeast(0)
                                    }
                                    return@onPreviewKeyEvent true
                                }
                                Key.Enter, Key.NumPadEnter -> {
                                    if (availableVariables.isNotEmpty() && selectedVariableIndex in availableVariables.indices) {
                                        insertVariableAtCursor(availableVariables[selectedVariableIndex].first)
                                        showVariableDropdown = false
                                    }
                                    return@onPreviewKeyEvent true
                                }
                                Key.Escape -> {
                                    showVariableDropdown = false
                                    return@onPreviewKeyEvent true
                                }
                            }
                        }
                    }
                    if (undoStack != null && event.isUndoEvent()) {
                        val prev = undoStack.removeLastOrNull()
                        if (prev != null) {
                            val annotated = if (state != null) highlightVariablesWithStatus(prev, definedNames)
                                            else AnnotatedString(prev)
                            fieldValue = TextFieldValue(annotatedString = annotated, selection = TextRange(annotated.length))
                            onValueChange(prev)
                        }
                        true
                    } else {
                        false
                    }
                }
                .pointerInput(state != null) {
                if (state == null) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) {
                            pendingClickRef.value = true
                        }
                    }
                }
            },
            decorationBox = { inner ->
                if (fieldValue.text.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = ClapiColors.OnSurfaceDim,
                        fontSize = textStyle.fontSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                inner()
            },
        )

        if (state != null) {
            popupVariable?.let { varName ->
                VariableEditorPopup(
                    variableName = varName,
                    state = state,
                    tab = currentTab,
                    onDismiss = { popupVariable = null },
                )
            }
        }

        if (showVariableDropdown && availableVariables.isNotEmpty()) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, 36),
                properties = PopupProperties(focusable = false),
                onDismissRequest = { showVariableDropdown = false },
            ) {
                VariableDropdownList(
                    variables = availableVariables,
                    selectedIndex = selectedVariableIndex,
                    onSelect = { varName ->
                        insertVariableAtCursor(varName)
                        showVariableDropdown = false
                    },
                    modifier = Modifier.testTag("variable-dropdown"),
                )
            }
        }
    }
}

private fun KeyEvent.isShowVariableEvent(): Boolean =
    isCtrlPressed && !isAltPressed && !isMetaPressed &&
            (key == Key.Spacebar)

private fun KeyEvent.isUndoEvent(): Boolean =
    type == KeyEventType.KeyDown && (isMetaPressed || isCtrlPressed) && !isShiftPressed && key == Key.Z

/**
 * Returns all currently available variables as (name, resolvedValue) pairs.
 * Regular variables are ordered alphabetically first, followed by external secrets
 * formatted as `_secret.<name>` ordered alphabetically.
 */
fun getAvailableVariables(state: AppState?, tab: RequestTabState?): List<Pair<String, String>> {
    val regularVars = if (state != null) {
        val layers = state.activeVariableLayers(tab)
        val regularMap = linkedMapOf<String, String>()
        for (layer in layers) {
            for ((k, v) in layer) {
                if (k.isNotBlank() && !regularMap.containsKey(k)) {
                    regularMap[k] = v
                }
            }
        }
        regularMap.entries.map { it.key to it.value }.sortedBy { it.first }
    } else {
        emptyList()
    }

    val secretVars = if (state != null) {
        val secretMap = linkedMapOf<String, String>()
        val env = state.selectedEnvironment
        if (env != null) {
            for (secret in env.externalSecrets) {
                if (secret.enabled && secret.name.isNotBlank()) {
                    val secretKey = "_secret.${secret.name.trim()}"
                    if (!secretMap.containsKey(secretKey)) {
                        val detail = when {
                            secret.path.isNotBlank() && secret.url.isNotBlank() -> "${secret.url.trim()}/${secret.path.trim()}"
                            secret.path.isNotBlank() -> secret.path.trim()
                            else -> secret.url.trim()
                        }
                        secretMap[secretKey] = detail
                    }
                }
            }
        }
        secretMap.entries.map { it.key to it.value }.sortedBy { it.first }
    } else {
        emptyList()
    }

    val authVars = mutableListOf<Pair<String, String>>()
    if (state != null) {
        val effectiveAuthType = if (tab != null) {
            if (tab.authType == AuthType.INHERIT) {
                state.resolveInheritedAuth(tab)?.authType ?: AuthType.NONE
            } else {
                tab.authType
            }
        } else {
            AuthType.NONE
        }
        if (effectiveAuthType == AuthType.OAUTH2) {
            authVars.add("_auth.oauth2.accessToken" to "OAuth2 Access Token")
        }
    }

    val dynamicVars = VariableResolver.dynamicVariables.map { it.name to it.description }

    return regularVars + secretVars + authVars + dynamicVars
}

@Composable
fun VariableDropdownList(
    variables: List<Pair<String, String>>,
    selectedIndex: Int,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    VariableDropdownListImpl(
        variables = variables,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        modifier = modifier,
    )
}

typealias VariableSourceInfo = io.github.makeforge.clapi.ui.shared.components.variable.VariableSourceInfo

fun resolveVariableSourceInfo(
    variableName: String,
    state: AppState,
    tab: RequestTabState? = state.activeTab,
): VariableSourceInfo = io.github.makeforge.clapi.ui.shared.components.variable.resolveVariableSourceInfo(
    variableName = variableName,
    state = state,
    tab = tab,
)

@Composable
fun VariableEditorPopup(
    variableName: String,
    state: AppState,
    onDismiss: () -> Unit,
    tab: RequestTabState? = state.activeTab,
    initialOffset: IntOffset = IntOffset(40, 32),
    onPositionChanged: (IntOffset) -> Unit = {},
) {
    VariableEditorPopupImpl(
        variableName = variableName,
        state = state,
        onDismiss = onDismiss,
        tab = tab,
        initialOffset = initialOffset,
        onPositionChanged = onPositionChanged,
    )
}
