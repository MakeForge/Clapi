package io.github.crustack.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.platform.handleCtrlSpaceKeyEvent
import io.github.crustack.clapi.ui.shared.platform.isRecentCtrlSpaceTriggered
import io.github.crustack.clapi.ui.shared.platform.resetCtrlSpaceTrigger
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.EnvState
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import kotlin.math.roundToInt
import io.github.crustack.clapi.ui.shared.state.MutableKeyValue
import io.github.crustack.clapi.ui.shared.theme.CodeFontFamily
import io.github.crustack.clapi.ui.shared.theme.ClapiColors

// ── Constants ─────────────────────────────────────────────────────

/**
 * Variable token pattern used across URL/body/header editors.
 *
 * Postman exports and real-world projects frequently use names like
 * `{{base-url}}`, `{{api.version}}`, or `{{ my_var }}`. We accept any
 * non-brace content inside `{{...}}` and normalize surrounding whitespace.
 */
private val VARIABLE_REGEX = Regex("""\{\{\s*([^{}\r\n]+?)\s*\}\}""")
private const val VARIABLE_TAG = "variable"
private fun normalizeVariableDisplayName(rawName: String): String =
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
) {
    // Resolved variable names from whatever environment/folder/collection layers are active.
    // Reading mutableState properties of AppState inside the composable body automatically
    // registers this composable for recomposition when the environment changes.
    val currentTab = tab ?: state?.activeTab
    val definedNames: Set<String> = if (state != null)
        state.activeVariableLayers(currentTab).flatMap { it.keys }.toSet()
    else
        emptySet()

    // Internal TextFieldValue that carries the highlighted AnnotatedString.
    var fieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                if (state != null) highlightVariablesWithStatus(value, definedNames)
                else AnnotatedString(value),
            ),
        )
    }

    // Popup visibility — null means hidden. Set ONLY from the click handler
    // inside onValueChange. Never set during initialisation (fixes Issue 1).
    var popupVariable by remember { mutableStateOf<String?>(null) }
    var showVariableDropdown by remember { mutableStateOf(false) }
    var selectedVariableIndex by remember { mutableIntStateOf(0) }
    val availableVariables = remember(state, currentTab, definedNames) {
        getAvailableVariables(state, currentTab)
    }

    val insertVariableAtCursor: (String) -> Unit = { varName ->
        val token = "{{$varName}}"
        val currentText = fieldValue.text
        val selStart = fieldValue.selection.min.coerceIn(0, currentText.length)
        val selEnd = fieldValue.selection.max.coerceIn(0, currentText.length)
        val newText = currentText.replaceRange(selStart, selEnd, token)
        val newCursor = selStart + token.length
        val annotated = if (state != null) highlightVariablesWithStatus(newText, definedNames)
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
        if (fieldValue.text != value) {
            val annotated = if (state != null) highlightVariablesWithStatus(value, definedNames)
                            else AnnotatedString(value)
            fieldValue = TextFieldValue(annotatedString = annotated, selection = TextRange(annotated.length))
        }
    }

    // Re-colour when the active environment/variables change without a text change
    // (e.g. user activates a different env or adds a new variable).
    LaunchedEffect(definedNames) {
        if (state != null) {
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
                val annotated = if (state != null) highlightVariablesWithStatus(new.text, definedNames)
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
}

private fun KeyEvent.isShowVariableEvent(): Boolean =
    isCtrlPressed && !isAltPressed && !isMetaPressed &&
            (key == Key.Spacebar)

private fun KeyEvent.isUndoEvent(): Boolean =
    type == KeyEventType.KeyDown && (isMetaPressed || isCtrlPressed) && !isShiftPressed && key == Key.Z

/**
 * Returns all currently available variables as (name, resolvedValue) pairs,
 * ordered alphabetically by variable name.
 */
fun getAvailableVariables(state: AppState?, tab: RequestTabState?): List<Pair<String, String>> {
    if (state == null) return emptyList()
    val layers = state.activeVariableLayers(tab)
    val map = linkedMapOf<String, String>()
    for (layer in layers) {
        for ((k, v) in layer) {
            if (k.isNotBlank() && !map.containsKey(k)) {
                map[k] = v
            }
        }
    }
    return map.entries.map { it.key to it.value }.sortedBy { it.first }
}

@Composable
fun VariableDropdownList(
    variables: List<Pair<String, String>>,
    selectedIndex: Int,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in variables.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    Box(
        modifier = modifier
            .widthIn(min = 260.dp, max = 380.dp)
            .shadow(8.dp, RoundedCornerShape(8.dp))
            .background(ClapiColors.SurfaceContainer, RoundedCornerShape(8.dp))
            .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
            .padding(vertical = 4.dp),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp),
        ) {
            itemsIndexed(variables) { index, (name, value) ->
                val isSelected = index == selectedIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) ClapiColors.SelectedItem else Color.Transparent)
                        .clickable { onSelect(name) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("variable-dropdown-item-$name"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "{{ $name }}",
                        color = VariableHighlightColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = CodeFontFamily,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (value.isNotEmpty()) {
                        Text(
                            text = "${Strings.value}: $value",
                            color = ClapiColors.OnSurfaceDim,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 140.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Variable editor popup ─────────────────────────────────────────────

data class VariableSourceInfo(
    val cleanName: String,
    val environmentName: String?,
    val environmentValue: String?,
    val hasEnvironmentValue: Boolean,
    val folderName: String?,
    val folderValue: String?,
    val hasFolderValue: Boolean,
    val currentResolvedValue: String?,
)

fun resolveVariableSourceInfo(
    variableName: String,
    state: AppState,
    tab: RequestTabState? = state.activeTab,
): VariableSourceInfo {
    val cleanName = normalizeVariableDisplayName(variableName)
    val env = state.selectedEnvironment
    val envName = env?.name
    val envVar = env?.variables?.firstOrNull { it.key == cleanName && it.enabled }
    val envValue = envVar?.value
    val hasEnvValue = envVar != null

    val targetTab = tab ?: state.activeTab
    var folderName: String? = null
    var folderValue: String? = null
    var hasFolderValue = false

    if (targetTab != null) {
        if (targetTab.isFolderTab) {
            folderName = targetTab.name
            val fVar = targetTab.variables.firstOrNull { it.key == cleanName && it.enabled }
            if (fVar != null) {
                folderValue = fVar.value
                hasFolderValue = true
            }
        } else {
            val ancestors = state.getAncestorFolders(targetTab)
            val currentFolderNode = ancestors.lastOrNull { it.isFolder }
            if (currentFolderNode != null) {
                val definingFolderNode = ancestors.asReversed().firstOrNull { fNode ->
                    val openTab = state.openTabs.firstOrNull { it.id == fNode.id && it.isFolderTab }
                    if (openTab != null) {
                        openTab.variables.any { it.key == cleanName && it.enabled }
                    } else {
                        fNode.variableEntries?.any { it.key == cleanName && it.enabled } == true
                    }
                } ?: currentFolderNode

                folderName = definingFolderNode.name
                val openTab = state.openTabs.firstOrNull { it.id == definingFolderNode.id && it.isFolderTab }
                val fVarValue = if (openTab != null) {
                    openTab.variables.firstOrNull { it.key == cleanName && it.enabled }?.value
                } else {
                    definingFolderNode.variableEntries?.firstOrNull { it.key == cleanName && it.enabled }?.value
                }
                if (fVarValue != null) {
                    folderValue = fVarValue
                    hasFolderValue = true
                }
            }
        }
    }

    // Current value: environment always takes precedence over folder variable
    val currentResolvedValue = if (hasEnvValue) {
        envValue
    } else if (hasFolderValue) {
        folderValue
    } else {
        state.activeVariableLayers(targetTab).firstNotNullOfOrNull { it[cleanName] }
    }

    return VariableSourceInfo(
        cleanName = cleanName,
        environmentName = envName,
        environmentValue = envValue,
        hasEnvironmentValue = hasEnvValue,
        folderName = folderName,
        folderValue = folderValue,
        hasFolderValue = hasFolderValue,
        currentResolvedValue = currentResolvedValue,
    )
}

/**
 * Floating popup for inline environment variable editing.
 *
 * Displays:
 *  - Current resolved value (environment takes precedence over folder variable)
 *  - Source values: Environment and Current Folder
 *  - Edit field to save/update the value in the active environment
 */
@Composable
fun VariableEditorPopup(
    variableName: String,
    state: AppState,
    onDismiss: () -> Unit,
    tab: RequestTabState? = state.activeTab,
    initialOffset: IntOffset = IntOffset(40, 32),
    onPositionChanged: (IntOffset) -> Unit = {},
) {
    val cleanName = remember(variableName) { normalizeVariableDisplayName(variableName) }
    val info = resolveVariableSourceInfo(cleanName, state, tab)
    val env = state.selectedEnvironment

    var editValue by remember(cleanName) {
        mutableStateOf(TextFieldValue(info.environmentValue ?: info.folderValue ?: ""))
    }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var popupSize by remember { mutableStateOf(IntSize.Zero) }
    var popupOffsetX by remember(cleanName) { mutableStateOf(initialOffset.x.toFloat()) }
    var popupOffsetY by remember(cleanName) { mutableStateOf(initialOffset.y.toFloat()) }

    fun movePopupBy(dx: Float, dy: Float) {
        val rawX = popupOffsetX + dx
        val rawY = popupOffsetY + dy
        val (cx, cy) = clampPopupOffsetToViewportF(
            x = rawX,
            y = rawY,
            popupSize = popupSize,
            viewportSize = viewportSize,
        )
        popupOffsetX = cx
        popupOffsetY = cy
        onPositionChanged(IntOffset(cx.roundToInt(), cy.roundToInt()))
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .onSizeChanged { size ->
                    viewportSize = size
                    val (cx, cy) = clampPopupOffsetToViewportF(
                        x = popupOffsetX, y = popupOffsetY,
                        popupSize = popupSize, viewportSize = size,
                    )
                    popupOffsetX = cx
                    popupOffsetY = cy
                }
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                .testTag("variable-popup-backdrop"),
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(popupOffsetX.roundToInt(), popupOffsetY.roundToInt()) }
                    .widthIn(min = 340.dp, max = 520.dp)
                    .shadow(16.dp, RoundedCornerShape(12.dp), clip = false)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ClapiColors.Surface)
                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(12.dp))
                    .onSizeChanged { size ->
                        popupSize = size
                        val (cx, cy) = clampPopupOffsetToViewportF(
                            x = popupOffsetX, y = popupOffsetY,
                            popupSize = size, viewportSize = viewportSize,
                        )
                        popupOffsetX = cx
                        popupOffsetY = cy
                    }
                    .draggableNoSlop(key = cleanName) { dx, dy -> movePopupBy(dx, dy) }
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(16.dp)
                    .testTag("variable-editor-popup"),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ClapiColors.SurfaceContainer)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("variable-popup-title-bar"),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "${Strings.t("variable")}: $cleanName",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VariableHighlightColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${Strings.environments}: ${info.environmentName ?: Strings.t("none")}",
                                fontSize = 11.sp,
                                color = ClapiColors.OnSurfaceDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(24.dp).testTag("variable-popup-close"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = Strings.t("close_variable_editor"),
                                tint = ClapiColors.OnSurfaceDim,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ClapiColors.SurfaceVariant)
                            .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = Strings.t("current_value"),
                                fontSize = 11.sp,
                                color = ClapiColors.OnSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                            )
                            val sourceBadge = when {
                                info.hasEnvironmentValue -> "Environment"
                                info.hasFolderValue -> "Folder"
                                else -> null
                            }
                            if (sourceBadge != null) {
                                Text(
                                    text = sourceBadge,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ClapiColors.Primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ClapiColors.Primary.copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .testTag("variable-popup-active-source"),
                                )
                            }
                        }
                        Text(
                            text = info.currentResolvedValue ?: Strings.t("not_defined"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (info.currentResolvedValue == null) ClapiColors.Error else ClapiColors.OnSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("variable-popup-current-value"),
                        )

                        HorizontalDivider(color = ClapiColors.Border.copy(alpha = 0.6f))

                        Row(
                            modifier = Modifier.fillMaxWidth().testTag("variable-popup-env-row"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val envLabel = if (info.environmentName != null) "Environment (${info.environmentName})" else "Environment"
                            Text(
                                text = envLabel,
                                fontSize = 11.sp,
                                color = ClapiColors.OnSurfaceDim,
                            )
                            Text(
                                text = if (info.hasEnvironmentValue) info.environmentValue!! else Strings.t("not_defined"),
                                fontSize = 11.sp,
                                fontFamily = CodeFontFamily,
                                fontWeight = if (info.hasEnvironmentValue) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (info.hasEnvironmentValue) ClapiColors.OnSurface else ClapiColors.OnSurfaceDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("variable-popup-env-value"),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().testTag("variable-popup-folder-row"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val folderLabel = if (info.folderName != null) "Folder (${info.folderName})" else "Folder"
                            Text(
                                text = folderLabel,
                                fontSize = 11.sp,
                                color = ClapiColors.OnSurfaceDim,
                            )
                            Text(
                                text = if (info.hasFolderValue) info.folderValue!! else if (info.folderName != null) Strings.t("not_defined") else Strings.t("none"),
                                fontSize = 11.sp,
                                fontFamily = CodeFontFamily,
                                fontWeight = if (info.hasFolderValue) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (info.hasFolderValue) ClapiColors.OnSurface else ClapiColors.OnSurfaceDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("variable-popup-folder-value"),
                            )
                        }
                    }

                    HorizontalDivider(color = ClapiColors.Border)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = Strings.value,
                                fontSize = 11.sp,
                                color = ClapiColors.OnSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "Sets in environment",
                                fontSize = 10.sp,
                                color = ClapiColors.OnSurfaceDim,
                            )
                        }
                        BasicTextField(
                            value = editValue,
                            onValueChange = { editValue = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = ClapiColors.OnSurface,
                                fontSize = 13.sp,
                                fontFamily = CodeFontFamily,
                            ),
                            cursorBrush = SolidColor(ClapiColors.Primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClapiColors.SurfaceContainer)
                                .border(1.dp, ClapiColors.BorderLight, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .testTag("variable-popup-value-input"),
                            decorationBox = { inner ->
                                if (editValue.text.isEmpty()) {
                                    Text(Strings.t("enter_value"), color = ClapiColors.OnSurfaceDim, fontSize = 13.sp)
                                }
                                inner()
                            },
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = Strings.t("open_in_environments"),
                            fontSize = 12.sp,
                            color = ClapiColors.Primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ClapiColors.Primary.copy(alpha = 0.10f))
                                .clickable {
                                    val idx = env?.let { state.environments.indexOf(it) } ?: -1
                                    if (idx >= 0) state.openEnvEdit(idx)
                                    onDismiss()
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("variable-popup-open-env"),
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClapiColors.SurfaceContainer)
                                .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("variable-popup-cancel"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Cancel",
                                fontSize = 12.sp,
                                color = ClapiColors.OnSurface,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClapiColors.Primary)
                                .clickable {
                                    val targetEnv = env ?: run {
                                        val newEnv = EnvState("Default")
                                        state.environments.add(newEnv)
                                        state.selectedEnvIndex = state.environments.size - 1
                                        newEnv
                                    }
                                    val existing = targetEnv.variables.firstOrNull { it.key == cleanName }
                                    if (existing != null) {
                                        existing.value = editValue.text
                                        existing.enabled = true
                                    } else {
                                        targetEnv.variables.add(MutableKeyValue(key = cleanName, value = editValue.text, enabled = true))
                                    }
                                    onDismiss()
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("variable-popup-save"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Save",
                                fontSize = 12.sp,
                                color = ClapiColors.OnPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
