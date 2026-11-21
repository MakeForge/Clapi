package io.github.makeforge.clapi.ui.shared.components

import io.github.makeforge.clapi.ui.shared.components.codeeditor.CodeEditorSearchBar
import io.github.makeforge.clapi.ui.shared.components.codeeditor.CodeEditorGoToLineBar



import org.jetbrains.compose.resources.painterResource
import io.github.makeforge.clapi.ui.shared.generated.resources.Res

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import io.github.makeforge.clapi.ui.shared.platform.handleCtrlSpaceKeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.makeforge.clapi.editor.core.LanguageMode
import io.github.makeforge.clapi.editor.ui.EditorLabels
import io.github.makeforge.clapi.editor.ui.EditorRenderer
import io.github.makeforge.clapi.editor.ui.EditorTheme
import io.github.makeforge.clapi.editor.ui.EditorViewModel
import io.github.makeforge.clapi.editor.ui.SyntaxHighlighterRegistry
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_arrow_downward
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_arrow_upward
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_close
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_content_copy
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_done_all
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_download
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_find_replace
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_format_align_left
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_keyboard_arrow_down
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_keyboard_arrow_up
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_search
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_unfold_less
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_unfold_more
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_wrap_text
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.platform.copyToClipboard as platformCopyToClipboard
import io.github.makeforge.clapi.ui.shared.platform.readFromClipboard
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily
import io.github.makeforge.clapi.ui.shared.theme.LocalAppColors
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pretty-print of read-only bodies larger than this is offloaded off the composition thread. */
internal const val READ_ONLY_FORMAT_OFFLOAD_CHARS = 64_000

internal fun shouldOffloadReadOnlyFormat(length: Int): Boolean = length > READ_ONLY_FORMAT_OFFLOAD_CHARS

/** Read-only response bodies default to formatted; large bodies format off the UI thread. */
internal fun shouldAutoPrettyPrintReadOnly(@Suppress("UNUSED_PARAMETER") length: Int): Boolean = true

// ── Theme Helper ─────────────────────────────────────────────────

@Composable
private fun editorTheme(): EditorTheme {
    val p = LocalAppColors.current
    val isDark = p.background.red < 0.5f
    return EditorTheme(
        background       = p.surface,
        foreground       = p.onSurface,
        lineNumberFg     = p.onSurfaceDim,
        lineNumberBg     = p.surface,
        gutterBorder     = p.border,
        selectionBg      = p.selectedItem,
        cursorLine       = p.surfaceVariant,
        foldIndicator    = p.onSurfaceDim,
        indentGuide      = p.editorIndentGuide,
        bracketMatchBackground = p.editorBracketMatchBackground,
        bracketMatchBorder = p.editorBracketMatchBorder,
        errorUnderline   = p.error,
        warningUnderline = p.tertiary,
        accent           = p.primary,
        tokenColors      = if (isDark) EditorTheme.Dark.tokenColors else EditorTheme.Light.tokenColors,
    )
}

// ── Public API ──────────────────────────────────────────────────

/**
 * Full-featured code editor / viewer with syntax highlighting,
 * code folding, search, formatting, word wrap, copy, and download.
 *
 * @param text          Current content.
 * @param onTextChange  Callback when the user edits text.
 *                      When `null` the editor is **read-only**.
 * @param language      Determines syntax highlighting and fold detection.
 * @param showToolbar   Whether to show the toolbar row.
 * @param enableFolding Enable code folding (read-only mode only).
 * @param enableSearch  Show the search toggle in the toolbar.
 * @param enableFormat  Show the format/beautify toggle.
 * @param enableWordWrap Show the word-wrap toggle.
 * @param enableCopy    Show copy-to-clipboard button.
 * @param enableDownload Show download-to-file button.
 * @param showCursorPosition Show the line and column indicator below the editor.
 * @param onDownload    Callback for the download action.
 * @param placeholder   Placeholder text shown when the editor is empty.
 * @param testTagPrefix Prefix for Compose test tags.
 */
@kotlinx.serialization.ExperimentalSerializationApi
@Composable
fun CodeEditor(
    text: String,
    onTextChange: ((String) -> Unit)? = null,
    language: SyntaxLanguage = SyntaxLanguage.PLAIN,
    modifier: Modifier = Modifier,
    showToolbar: Boolean = true,
    enableFolding: Boolean = true,
    enableSearch: Boolean = true,
    enableFormat: Boolean = true,
    enableWordWrap: Boolean = true,
    enableCopy: Boolean = true,
    enableDownload: Boolean = false,
    showCursorPosition: Boolean = true,
    onDownload: (() -> Unit)? = null,
    placeholder: String = "",
    testTagPrefix: String = "code-editor",
    onCursorTap: ((Int) -> Unit)? = null,
    lineVariableSpans: ((lineText: String, lineStartOffset: Int) -> List<Pair<IntRange, androidx.compose.ui.graphics.Color>>)? = null,
    allowJson5: Boolean = false,
    availableVariables: List<Pair<String, String>> = emptyList(),
    variableTypes: Map<String, io.github.makeforge.clapi.core.model.opencollection.VariableType> = emptyMap(),
    /**
     * An already-created [EditorViewModel] to use instead of creating a new one.
     * When provided, [CodeEditor] does NOT dispose it on removal — the caller
     * (e.g. [BodyEditor] via [RequestTabState.getOrCreateBodyViewModel]) owns
     * the lifetime and disposes it when the tab is closed.
     *
     * Leave `null` (default) for all read-only viewers and script editors that
     * do not need persistent undo history across re-compositions.
     */
    externalViewModel: EditorViewModel? = null,
) {
    val isReadOnly = onTextChange == null

    // ── ViewModel (manages all document state) ─────────────────────
    val languageMode = language.toLanguageMode()
    // When an external VM is provided we skip creating an internal one entirely.
    // remember() key includes `hasExternal` so that switching from internal to
    // external (or vice-versa) correctly disposes the old internal VM.
    val hasExternal = externalViewModel != null
    val internalViewModel: EditorViewModel? = remember(languageMode, hasExternal) {
        if (hasExternal) null
        else {
            if (!SyntaxHighlighterRegistry.hasHighlighter(LanguageMode.PLAIN_TEXT)) {
                SyntaxHighlighterRegistry.registerBuiltinHighlighters()
            }
            EditorViewModel(initialText = text, languageMode = languageMode)
        }
    }
    // Only dispose the VM we created; external VMs are owned by RequestTabState.
    DisposableEffect(internalViewModel) { onDispose { internalViewModel?.dispose() } }
    val viewModel: EditorViewModel = externalViewModel ?: internalViewModel!!
    viewModel.updateVariableTypes(variableTypes)
    val editorState by viewModel.state.collectAsState()

    // ── Format / display state ───────────────────────────────
    // Read-only responses always default to formatted. Large payloads (>64 KB)
    // are formatted off the UI thread; their raw form remains visible meanwhile.
    var isFormatted by remember {
        mutableStateOf(isReadOnly && shouldAutoPrettyPrintReadOnly(text.length))
    }
    var offloadedFormatted by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(text, isReadOnly) {
        if (!isReadOnly) return@LaunchedEffect
        isFormatted = shouldAutoPrettyPrintReadOnly(text.length)
        offloadedFormatted = null
    }
    LaunchedEffect(text, isFormatted, language, allowJson5, isReadOnly) {
        if (!isReadOnly || !isFormatted || !shouldOffloadReadOnlyFormat(text.length)) {
            offloadedFormatted = null
            return@LaunchedEffect
        }
        offloadedFormatted = null
        val formatted = withContext(Dispatchers.Default) {
            autoFormat(text, language, allowJson5)
        }
        offloadedFormatted = formatted
    }
    val displayText = remember(text, isFormatted, language, allowJson5, offloadedFormatted, isReadOnly) {
        when {
            !isReadOnly || !isFormatted -> text
            shouldOffloadReadOnlyFormat(text.length) -> offloadedFormatted ?: text
            else -> autoFormat(text, language, allowJson5)
        }
    }
    // Keep doc in sync with external text (or formatted text for read-only),
    // but skip no-op updates. Re-applying identical text after paste can
    // trigger an extra cursor/layout cycle and visually shift the gutter.
    LaunchedEffect(displayText) {
        if (viewModel.getFullText() != displayText) {
            viewModel.onExternalTextChanged(displayText)
        }
    }

    // ── Toolbar state ────────────────────────────────────────
    var wordWrap by remember { mutableStateOf(true) }
    var showSearch by remember { mutableStateOf(false) }
    var showReplace by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var activeMatchIndex by remember { mutableIntStateOf(0) }
    var showGoToLine by remember { mutableStateOf(false) }
    var goToLineText by remember { mutableStateOf("") }
    var showVariableDropdown by remember { mutableStateOf(false) }
    var selectedVariableIndex by remember { mutableIntStateOf(0) }

    val fullText = viewModel.getFullText()
    val cursorOffset = editorState.cursorOffset
    val prefixContext = remember(fullText, cursorOffset, editorState.version, showVariableDropdown) {
        if (showVariableDropdown) {
            extractVariablePrefixContext(fullText, cursorOffset)
        } else {
            null
        }
    }
    val filteredVariables = remember(availableVariables, prefixContext) {
        if (prefixContext != null) {
            filterVariables(availableVariables, prefixContext.prefix)
        } else {
            availableVariables
        }
    }

    LaunchedEffect(filteredVariables) {
        if (selectedVariableIndex !in filteredVariables.indices) {
            selectedVariableIndex = 0
        }
    }

    val editorFocus = remember { FocusRequester() }
    // Bump a tick from click handlers; requestFocus in LaunchedEffect so it is
    // not nested inside IconButton/performClick (deadlocks desktop tests).
    var restoreFocusTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(restoreFocusTick) {
        if (restoreFocusTick == 0) return@LaunchedEffect
        try { editorFocus.requestFocus() } catch (_: Exception) { }
    }
    val restoreEditorFocus: () -> Unit = { restoreFocusTick++ }

    val insertVariable: (String) -> Unit = { varName ->
        val text = viewModel.getFullText()
        val offset = editorState.cursorOffset
        val ctx = prefixContext ?: extractVariablePrefixContext(text, offset)
        viewModel.replaceRange(ctx.replaceStart, ctx.replaceEnd, "{{$varName}}")
        showVariableDropdown = false
        restoreEditorFocus()
    }

    // ── Fold regions & search matches (offloaded for large payloads) ───────
    val editorScope = rememberCoroutineScope()
    val isLargeDisplay = shouldOffloadReadOnlyFormat(displayText.length)
    var asyncLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var asyncFoldRegions by remember { mutableStateOf<List<FoldRegion>>(emptyList()) }
    var asyncSearchMatches by remember { mutableStateOf<List<SearchMatch>>(emptyList()) }

    LaunchedEffect(displayText, language, enableFolding, isLargeDisplay) {
        if (!isLargeDisplay) {
            asyncLines = emptyList()
            asyncFoldRegions = emptyList()
            return@LaunchedEffect
        }
        val (lines, regions) = withContext(Dispatchers.Default) {
            val split = displayText.split('\n')
            val folds = if (enableFolding) detectFoldRegions(split, language) else emptyList()
            split to folds
        }
        asyncLines = lines
        asyncFoldRegions = regions
    }

    val allLines = remember(displayText, isLargeDisplay, asyncLines) {
        if (isLargeDisplay) asyncLines else displayText.split('\n')
    }
    val toolbarFoldRegions: List<FoldRegion> = remember(allLines, language, enableFolding, isLargeDisplay, asyncFoldRegions) {
        if (!enableFolding) emptyList()
        else if (isLargeDisplay) asyncFoldRegions
        else detectFoldRegions(allLines, language)
    }

    LaunchedEffect(allLines, searchQuery, isLargeDisplay) {
        if (!isLargeDisplay) {
            asyncSearchMatches = emptyList()
            return@LaunchedEffect
        }
        if (searchQuery.isEmpty() || allLines.isEmpty()) {
            asyncSearchMatches = emptyList()
            return@LaunchedEffect
        }
        asyncSearchMatches = withContext(Dispatchers.Default) {
            findSearchMatches(allLines, searchQuery)
        }
    }

    val visibleTexts = remember(allLines) { allLines }
    val searchMatches = remember(visibleTexts, searchQuery, isLargeDisplay, asyncSearchMatches) {
        if (isLargeDisplay) asyncSearchMatches else findSearchMatches(visibleTexts, searchQuery)
    }
    val searchMatchRangesByLine = remember(searchMatches) {
        searchMatches
            .groupBy { it.lineIndex }
            .mapValues { (_, matchesForLine) ->
                matchesForLine.map { it.startOffset until it.endOffset }
            }
    }
    val activeSearchMatch = remember(searchMatches, activeMatchIndex) {
        searchMatches.getOrNull(activeMatchIndex)?.let { it.lineIndex to (it.startOffset until it.endOffset) }
    }

    val toggleSearch: () -> Unit = {
        showSearch = !showSearch
        if (!showSearch) {
            searchQuery = ""
            showReplace = false
            restoreEditorFocus()
        }
    }
    LaunchedEffect(searchMatches.size) {
        activeMatchIndex = if (searchMatches.isNotEmpty())
            activeMatchIndex.coerceIn(0, searchMatches.size - 1) else 0
    }

    // Scroll the editor to the active search match so the LazyColumn in
    // EditorRenderer follows the cursor to the correct line.
    LaunchedEffect(activeMatchIndex, searchMatches) {
        val match = searchMatches.getOrNull(activeMatchIndex) ?: return@LaunchedEffect
        val lineStart = viewModel.document.lineStart(
            match.lineIndex.coerceIn(0, viewModel.document.lineCount - 1)
        )
        viewModel.moveCursorTo(lineStart + match.startOffset)
    }

    // ── Layout ───────────────────────────────────────────────
    Column(
        modifier = modifier
            .testTag(testTagPrefix)
            .onPreviewKeyEvent { event ->
                val isMeta = event.isMetaPressed || event.isCtrlPressed
                if (handleCtrlSpaceKeyEvent(event) {
                    if (!isReadOnly && availableVariables.isNotEmpty()) {
                        selectedVariableIndex = 0
                        showVariableDropdown = true
                    }
                }) {
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (showVariableDropdown && filteredVariables.isNotEmpty()) {
                    when (event.key) {
                        Key.DirectionDown -> {
                            selectedVariableIndex = (selectedVariableIndex + 1).coerceAtMost(filteredVariables.size - 1)
                            return@onPreviewKeyEvent true
                        }
                        Key.DirectionUp -> {
                            selectedVariableIndex = (selectedVariableIndex - 1).coerceAtLeast(0)
                            return@onPreviewKeyEvent true
                        }
                        Key.Enter, Key.NumPadEnter, Key.Tab -> {
                            val idx = selectedVariableIndex.coerceIn(0, filteredVariables.size - 1)
                            insertVariable(filteredVariables[idx].first)
                            return@onPreviewKeyEvent true
                        }
                        Key.Escape -> {
                            showVariableDropdown = false
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                if (enableSearch && isMeta && event.key == Key.F) {
                    toggleSearch()
                    true
                } else if (isMeta && event.key == Key.G) {
                    showGoToLine = !showGoToLine
                    if (!showGoToLine) restoreEditorFocus()
                    true
                } else if (event.key == Key.F3) {
                    if (!showSearch && enableSearch) showSearch = true
                    if (searchMatches.isNotEmpty()) {
                        activeMatchIndex = if (event.isShiftPressed) {
                            (activeMatchIndex - 1 + searchMatches.size) % searchMatches.size
                        } else {
                            (activeMatchIndex + 1) % searchMatches.size
                        }
                    }
                    true
                } else if (event.key == Key.Escape) {
                    when {
                        showSearch -> {
                            showSearch = false
                            searchQuery = ""
                            restoreEditorFocus()
                            true
                        }
                        showGoToLine -> {
                            showGoToLine = false
                            restoreEditorFocus()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
    ) {

        // Toolbar
        if (showToolbar) {
            CodeEditorToolbar(
                language = language,
                isReadOnly = isReadOnly,
                wordWrap = wordWrap,
                onToggleWordWrap = if (enableWordWrap) {
                    {
                        wordWrap = !wordWrap
                        restoreEditorFocus()
                    }
                } else null,
                isFormatted = isFormatted,
                onToggleFormat = if (enableFormat) {
                    {
                        if (isReadOnly) {
                            isFormatted = !isFormatted
                        } else {
                            val current = viewModel.getFullText()
                            if (shouldOffloadReadOnlyFormat(current.length)) {
                                editorScope.launch {
                                    val formatted = withContext(Dispatchers.Default) {
                                        autoFormat(current, language, allowJson5)
                                    }
                                    if (formatted != current && viewModel.getFullText() == current) {
                                        viewModel.replaceDocument(formatted)
                                    }
                                }
                            } else {
                                val formatted = autoFormat(current, language, allowJson5)
                                if (formatted != current) viewModel.replaceDocument(formatted)
                            }
                        }
                        restoreEditorFocus()
                    }
                } else null,
                showSearch = showSearch,
                onToggleSearch = if (enableSearch) {
                    toggleSearch
                } else null,
                onCopy = if (enableCopy) {
                    {
                        platformCopyToClipboard(viewModel.getFullText())
                        restoreEditorFocus()
                    }
                } else null,
                onDownload = if (enableDownload) {
                    {
                        onDownload?.invoke()
                        restoreEditorFocus()
                    }
                } else null,
                hasFoldRegions = toolbarFoldRegions.isNotEmpty(),
                onFoldAll   = if (enableFolding && toolbarFoldRegions.isNotEmpty()) {
                    { viewModel.foldAll(); restoreEditorFocus() }
                } else null,
                onUnfoldAll = if (enableFolding && toolbarFoldRegions.isNotEmpty()) {
                    { viewModel.unfoldAll(); restoreEditorFocus() }
                } else null,
                testTagPrefix = testTagPrefix,
            )
        }

        // Search bar
        if (showSearch) {
            CodeEditorSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it; activeMatchIndex = 0 },
                replaceQuery = replaceQuery,
                onReplaceQueryChange = { replaceQuery = it },
                showReplace = showReplace && !isReadOnly,
                onToggleReplace = if (!isReadOnly) {
                    { showReplace = !showReplace }
                } else null,
                matchCount = searchMatches.size,
                activeIndex = activeMatchIndex,
                onNext = {
                    if (searchMatches.isNotEmpty()) {
                        activeMatchIndex = (activeMatchIndex + 1) % searchMatches.size
                    }
                },
                onPrev = {
                    if (searchMatches.isNotEmpty()) {
                        activeMatchIndex = (activeMatchIndex - 1 + searchMatches.size) % searchMatches.size
                    }
                },
                onReplace = {
                    val match = searchMatches.getOrNull(activeMatchIndex) ?: return@CodeEditorSearchBar
                    val line = match.lineIndex.coerceIn(0, viewModel.document.lineCount - 1)
                    val start = viewModel.document.lineStart(line) + match.startOffset
                    viewModel.replaceRange(start, start + (match.endOffset - match.startOffset), replaceQuery)
                },
                onReplaceAll = {
                    viewModel.replaceAllMatches(searchQuery, replaceQuery)
                    activeMatchIndex = 0
                },
                onClose = { showSearch = false; showReplace = false; searchQuery = ""; restoreEditorFocus() },
                testTagPrefix = testTagPrefix,
            )
        }

        if (showGoToLine) {
            CodeEditorGoToLineBar(
                value = goToLineText,
                onValueChange = { goToLineText = it.filter { ch -> ch.isDigit() }.take(8) },
                onGo = {
                    goToLineText.toIntOrNull()?.let { viewModel.goToLine(it) }
                    showGoToLine = false
                    restoreEditorFocus()
                },
                onClose = { showGoToLine = false; restoreEditorFocus() },
                testTagPrefix = testTagPrefix,
            )
        }

        // ── Content (GapBuffer + LazyColumn) ──
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            EditorRenderer(
                viewModel      = viewModel,
                isReadOnly     = isReadOnly,
                language       = language.toLanguageMode(),
                theme          = editorTheme(),
                wordWrap       = wordWrap,
                testTagPrefix  = testTagPrefix,
                modifier       = Modifier
                    .fillMaxSize()
                    .testTag("$testTagPrefix-input"),
                onTextChange   = onTextChange,
                onPasteRequest = { readFromClipboard() },
                onCopyRequest  = { text -> platformCopyToClipboard(text) },
                searchMatchRangesByLine = searchMatchRangesByLine,
                activeSearchMatch = activeSearchMatch,
                onPrimaryTapOffset = onCursorTap,
                lineVariableSpans = lineVariableSpans,
                labels = EditorLabels(
                    expand = Strings.expand,
                    collapse = Strings.collapse,
                    diagnostic = Strings.diagnostic,
                    undo = Strings.undo,
                    redo = Strings.redo,
                    copy = Strings.copy,
                    cut = Strings.cut,
                    paste = Strings.paste,
                    selectAll = Strings.selectAll,
                    duplicateLine = Strings.duplicateLine,
                    toggleComment = Strings.toggleComment,
                ),
                focusRequester = editorFocus,
            )

            if (showVariableDropdown && filteredVariables.isNotEmpty()) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(60, 40),
                    properties = PopupProperties(focusable = false),
                    onDismissRequest = { showVariableDropdown = false },
                ) {
                    VariableDropdownList(
                        variables = filteredVariables,
                        selectedIndex = selectedVariableIndex.coerceIn(0, filteredVariables.size - 1),
                        onSelect = { varName ->
                            insertVariable(varName)
                        },
                        modifier = Modifier.testTag("code-editor-variable-dropdown"),
                    )
                }
            }
        }

        val cursorLine = viewModel.document.lineAt(
            editorState.cursorOffset.coerceIn(0, viewModel.document.length),
        )
        val cursorCol = editorState.cursorOffset -
            viewModel.document.lineStart(cursorLine)
        val statusDiag = editorState.diagnostics.firstOrNull { it.line - 1 == cursorLine }?.message
        // A quiet footer separates editor content from metadata without creating
        // the heavy, square status band that previously competed with the pane.
        // Diagnostics remain available even when the optional position indicator
        // is hidden in Settings.
        if (showCursorPosition || !statusDiag.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(ClapiColors.Surface)
                    .testTag("$testTagPrefix-status"),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ClapiColors.Border),
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showCursorPosition) {
                        Text(
                            text = "Ln ${cursorLine + 1}, Col ${cursorCol + 1}",
                            color = ClapiColors.OnSurfaceVariant,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ClapiColors.SurfaceContainer)
                                .border(1.dp, ClapiColors.BorderLight, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .testTag("$testTagPrefix-position-indicator"),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (!statusDiag.isNullOrEmpty()) {
                        Text(
                            text = statusDiag,
                            color = ClapiColors.Error,
                            fontSize = 11.sp,
                            maxLines = 1,
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(ClapiColors.Error.copy(alpha = 0.10f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Toolbar ─────────────────────────────────────────────────────

@Composable
private fun CodeEditorToolbar(
    language: SyntaxLanguage,
    isReadOnly: Boolean,
    wordWrap: Boolean,
    onToggleWordWrap: (() -> Unit)?,
    isFormatted: Boolean,
    onToggleFormat: (() -> Unit)?,
    showSearch: Boolean,
    onToggleSearch: (() -> Unit)?,
    onCopy: (() -> Unit)?,
    onDownload: (() -> Unit)?,
    hasFoldRegions: Boolean,
    onFoldAll: (() -> Unit)?,
    onUnfoldAll: (() -> Unit)?,
    testTagPrefix: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClapiColors.SurfaceContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("$testTagPrefix-toolbar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Language badge
        Text(
            text = language.name,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = ClapiColors.Primary,
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(ClapiColors.Primary.copy(alpha = 0.10f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )

        Spacer(Modifier.weight(1f))

        // Format / beautify
        if (onToggleFormat != null) {
            ToolbarBtn(
                icon = painterResource(Res.drawable.ic_format_align_left),
                contentDescription = if (isFormatted) Strings.raw else Strings.format,
                active = isFormatted && isReadOnly,
                onClick = onToggleFormat,
                testTag = "$testTagPrefix-format-toggle",
            )
        }

        // Word wrap
        if (onToggleWordWrap != null) {
            ToolbarBtn(
                icon = painterResource(Res.drawable.ic_wrap_text),
                contentDescription = if (wordWrap) Strings.disableWordWrap else Strings.enableWordWrap,
                active = wordWrap,
                onClick = onToggleWordWrap,
                testTag = "$testTagPrefix-word-wrap-toggle",
            )
        }

        // Search
        if (onToggleSearch != null) {
            ToolbarBtn(
                icon = painterResource(Res.drawable.ic_search),
                contentDescription = Strings.search,
                active = showSearch,
                onClick = onToggleSearch,
                testTag = "$testTagPrefix-search-toggle",
            )
        }

        // Fold / Unfold
        if (hasFoldRegions) {
            ToolbarBtn(
                icon = painterResource(Res.drawable.ic_unfold_less),
                contentDescription = Strings.collapseAll,
                onClick = onFoldAll ?: {},
                testTag = "$testTagPrefix-fold-all",
            )
            ToolbarBtn(
                icon = painterResource(Res.drawable.ic_unfold_more),
                contentDescription = Strings.expandAll,
                onClick = onUnfoldAll ?: {},
                testTag = "$testTagPrefix-unfold-all",
            )
        }

        // Separator before clipboard actions
        if (onCopy != null || onDownload != null) {
            Box(
                Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(ClapiColors.Border),
            )
        }

        // Copy
        if (onCopy != null) {
            ToolbarBtn(
                icon = painterResource(Res.drawable.ic_content_copy),
                contentDescription = Strings.copyBody,
                onClick = onCopy,
                testTag = "$testTagPrefix-copy-button",
            )
        }

        // Download
        if (onDownload != null) {
            ToolbarBtn(
                icon = painterResource(Res.drawable.ic_download),
                contentDescription = Strings.downloadResponse,
                onClick = onDownload,
                testTag = "$testTagPrefix-download-button",
            )
        }
    }

    Box(Modifier.fillMaxWidth().height(1.dp).background(ClapiColors.Border))
}

@Composable
private fun ToolbarBtn(
    icon: Painter,
    contentDescription: String,
    active: Boolean = false,
    onClick: () -> Unit,
    testTag: String = "",
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(28.dp)
            .focusProperties { canFocus = false }
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (active) ClapiColors.Primary else ClapiColors.OnSurfaceDim,
            modifier = Modifier.size(16.dp),
        )
    }
}


