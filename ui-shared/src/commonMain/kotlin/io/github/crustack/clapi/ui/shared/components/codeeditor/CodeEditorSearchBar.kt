package io.github.crustack.clapi.ui.shared.components.codeeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.crustack.clapi.ui.shared.components.IconButtonWithTooltip
import io.github.crustack.clapi.ui.shared.generated.resources.Res
import io.github.crustack.clapi.ui.shared.generated.resources.ic_arrow_downward
import io.github.crustack.clapi.ui.shared.generated.resources.ic_arrow_upward
import io.github.crustack.clapi.ui.shared.generated.resources.ic_close
import io.github.crustack.clapi.ui.shared.generated.resources.ic_done_all
import io.github.crustack.clapi.ui.shared.generated.resources.ic_find_replace
import io.github.crustack.clapi.ui.shared.generated.resources.ic_keyboard_arrow_down
import io.github.crustack.clapi.ui.shared.generated.resources.ic_keyboard_arrow_up
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.theme.ClapiColors
import io.github.crustack.clapi.ui.shared.theme.CodeFontFamily
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun CodeEditorSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    replaceQuery: String,
    onReplaceQueryChange: (String) -> Unit,
    showReplace: Boolean,
    onToggleReplace: (() -> Unit)?,
    matchCount: Int,
    activeIndex: Int,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
    testTagPrefix: String,
) {
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { searchFocusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClapiColors.SurfaceContainer)
            .testTag("$testTagPrefix-search-bar"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.DirectionUp && matchCount > 0 -> {
                            onPrev()
                            true
                        }
                        event.key == Key.DirectionDown && matchCount > 0 -> {
                            onNext()
                            true
                        }
                        event.key == Key.Enter -> {
                            if (event.isShiftPressed) onPrev() else onNext()
                            true
                        }
                        event.key == Key.Escape -> { onClose(); true }
                        else -> false
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = ClapiColors.OnSurface,
                    fontSize = 12.sp,
                    fontFamily = CodeFontFamily,
                ),
                cursorBrush = SolidColor(ClapiColors.Primary),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ClapiColors.Background)
                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .focusRequester(searchFocusRequester)
                    .testTag("$testTagPrefix-search-input"),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                Strings.searchInResponse,
                                color = ClapiColors.OnSurfaceDim,
                                fontSize = 12.sp,
                                fontFamily = CodeFontFamily,
                            )
                        }
                        inner()
                    }
                },
            )

            if (query.isNotEmpty()) {
                Text(
                    text = if (matchCount > 0) "${activeIndex + 1}/$matchCount" else Strings.noResults,
                    color = if (matchCount > 0) ClapiColors.OnSurfaceVariant else ClapiColors.OnSurfaceDim,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .widthIn(min = 60.dp)
                        .testTag("$testTagPrefix-search-match-count"),
                )
            }

            if (onToggleReplace != null) {
                IconButton(
                    onClick = onToggleReplace,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("$testTagPrefix-search-replace-toggle"),
                ) {
                    Icon(
                        painter = if (showReplace) painterResource(Res.drawable.ic_keyboard_arrow_up) else painterResource(Res.drawable.ic_keyboard_arrow_down),
                        contentDescription = if (showReplace) Strings.hideReplace else Strings.showReplace,
                        tint = ClapiColors.OnSurfaceDim,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            IconButton(
                onClick = onPrev,
                modifier = Modifier.size(24.dp).focusProperties { canFocus = false },
            ) {
                Icon(painter = painterResource(Res.drawable.ic_arrow_upward), Strings.previousMatch, tint = ClapiColors.OnSurfaceDim, modifier = Modifier.size(14.dp))
            }
            IconButton(
                onClick = onNext,
                modifier = Modifier.size(24.dp).focusProperties { canFocus = false },
            ) {
                Icon(painter = painterResource(Res.drawable.ic_arrow_downward), Strings.nextMatch, tint = ClapiColors.OnSurfaceDim, modifier = Modifier.size(14.dp))
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(24.dp)
                    .focusProperties { canFocus = false }
                    .testTag("$testTagPrefix-search-close"),
            ) {
                Icon(painter = painterResource(Res.drawable.ic_close), Strings.closeSearch, tint = ClapiColors.OnSurfaceDim, modifier = Modifier.size(14.dp))
            }
        }

        if (showReplace) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BasicTextField(
                    value = replaceQuery,
                    onValueChange = onReplaceQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = ClapiColors.OnSurface,
                        fontSize = 12.sp,
                        fontFamily = CodeFontFamily,
                    ),
                    cursorBrush = SolidColor(ClapiColors.Primary),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(ClapiColors.Background)
                        .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown || event.key != Key.Enter || query.isEmpty()) {
                                return@onPreviewKeyEvent false
                            }
                            if (event.isAltPressed) onReplaceAll() else onReplace()
                            true
                        }
                        .testTag("$testTagPrefix-search-replace-input"),
                    decorationBox = { inner ->
                        Box {
                            if (replaceQuery.isEmpty()) {
                                Text(
                                    Strings.replace,
                                    color = ClapiColors.OnSurfaceDim,
                                    fontSize = 12.sp,
                                    fontFamily = CodeFontFamily,
                                )
                            }
                            inner()
                        }
                    },
                )
                IconButtonWithTooltip(
                    onClick = onReplace,
                    enabled = query.isNotEmpty(),
                    tooltip = Strings.replace,
                    modifier = Modifier.size(28.dp).testTag("$testTagPrefix-search-replace"),
                    offset = IntOffset(0, 38),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_find_replace),
                        contentDescription = Strings.replace,
                        tint = if (query.isNotEmpty()) ClapiColors.OnSurfaceDim else ClapiColors.OnSurfaceDim.copy(alpha = 0.45f),
                        modifier = Modifier.size(17.dp),
                    )
                }
                IconButtonWithTooltip(
                    onClick = onReplaceAll,
                    enabled = query.isNotEmpty(),
                    tooltip = Strings.replaceAll,
                    modifier = Modifier.size(28.dp).testTag("$testTagPrefix-search-replace-all"),
                    offset = IntOffset(0, 38),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_done_all),
                        contentDescription = Strings.replaceAll,
                        tint = if (query.isNotEmpty()) ClapiColors.OnSurfaceDim else ClapiColors.OnSurfaceDim.copy(alpha = 0.45f),
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(ClapiColors.Border))
    }
}

@Composable
internal fun CodeEditorGoToLineBar(
    value: String,
    onValueChange: (String) -> Unit,
    onGo: () -> Unit,
    onClose: () -> Unit,
    testTagPrefix: String,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClapiColors.SurfaceContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("$testTagPrefix-goto-bar")
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    event.key == Key.Enter -> { onGo(); true }
                    event.key == Key.Escape -> { onClose(); true }
                    else -> false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(Strings.goToLine, color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = ClapiColors.OnSurface,
                fontSize = 12.sp,
                fontFamily = CodeFontFamily,
            ),
            cursorBrush = SolidColor(ClapiColors.Primary),
            modifier = Modifier
                .widthIn(min = 72.dp, max = 120.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ClapiColors.Background)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .focusRequester(focus)
                .testTag("$testTagPrefix-goto-input"),
        )
        TextButton(onClick = onGo, modifier = Modifier.testTag("$testTagPrefix-goto-go")) {
            Text(Strings.go, fontSize = 12.sp)
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(24.dp).focusProperties { canFocus = false },
        ) {
            Icon(painter = painterResource(Res.drawable.ic_close), Strings.close, tint = ClapiColors.OnSurfaceDim, modifier = Modifier.size(14.dp))
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(ClapiColors.Border))
}
