// Modified by MakeForge (2026)
package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.makeforge.clapi.core.network.VariableResolver
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_close
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_visibility
import io.github.makeforge.clapi.ui.shared.platform.DocsLinks
import io.github.makeforge.clapi.ui.shared.platform.copyToClipboard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_arrow_drop_down
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_content_copy
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_refresh
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_save
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_send
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_stop
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily
import io.github.makeforge.clapi.ui.shared.theme.httpMethodColor
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

data class CopyFormatOption(
    val label: String,
    val available: Boolean,
    val reason: String? = null,
    val action: () -> Unit,
)

/**
 * The top bar of the request editor: method selector, URL field, Send, Save,
 * Retry, and Copy cURL buttons.
 */
@Composable
fun RequestBar(
    method: HttpMethodType,
    onMethodChanged: (HttpMethodType) -> Unit,
    url: String,
    onUrlChanged: (String) -> Unit,
    isLoading: Boolean,
    onSend: () -> Unit,
    onSave: () -> Unit,
    retryEnabled: Boolean,
    retryCount: Int,
    retryDelayMs: Long,
    onRetryEnabledChanged: (Boolean) -> Unit,
    onRetryCountChanged: (Int) -> Unit,
    onRetryDelayChanged: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** Called when the user clicks the Stop button while a request is in-flight (H-2). */
    onCancel: () -> Unit = {},
    copyFormats: List<CopyFormatOption> = emptyList(),
    state: AppState? = null,
    tab: RequestTabState? = state?.activeTab,
    /** Per-tab undo stack for the URL field. Pass [RequestTabState.urlUndoStack]. */
    urlUndoStack: ArrayDeque<String>? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ClapiColors.Surface)
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .testTag("request-bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MethodDropdown(method, onMethodChanged)

        // URL input (variable-aware: highlights {{token}} in orange)
        var showPreviewPopup by remember { mutableStateOf(false) }
        var urlTextWidth by remember { mutableStateOf(0) }
        val density = LocalDensity.current

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(ClapiColors.SurfaceContainer)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                .onSizeChanged { urlTextWidth = it.width },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    VariableAwareTextField(
                        value = url,
                        onValueChange = onUrlChanged,
                        placeholder = "Enter request URL…",
                        textStyle = TextStyle(
                            color = ClapiColors.OnSurface,
                            fontSize = 14.sp,
                            fontFamily = CodeFontFamily,
                        ),
                        state = state,
                        tab = tab,
                        undoStack = urlUndoStack,
                        modifier = Modifier.fillMaxWidth().testTag("url-input"),
                    )
                }

                IconButton(
                    onClick = { showPreviewPopup = !showPreviewPopup },
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("preview-resolved-url-btn")
                        .handPointerHover(),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_visibility),
                        contentDescription = "Preview resolved URL",
                        tint = if (showPreviewPopup) ClapiColors.Primary else ClapiColors.OnSurfaceDim,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            if (showPreviewPopup) {
                val resolvedUrl = remember(url, state, tab, showPreviewPopup) {
                    if (state != null) {
                        val layers = state.activeVariableLayers(tab)
                        val dynamicVars = state.activeDynamicVariables(tab)
                        VariableResolver.resolve(
                            value = url,
                            variableLayers = layers,
                            removeUnresolved = false,
                            userDynamicVariables = dynamicVars,
                        )
                    } else {
                        url
                    }
                }
                val popupWidthDp = with(density) { urlTextWidth.toDp() }

                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(x = 0, y = 38),
                    properties = PopupProperties(focusable = false),
                    onDismissRequest = { showPreviewPopup = false },
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ClapiColors.Surface,
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ClapiColors.Border),
                        modifier = Modifier
                            .width(popupWidthDp.coerceAtLeast(360.dp))
                            .testTag("preview-resolved-url-popup"),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Resolved URL Preview",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ClapiColors.OnSurface,
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconButton(
                                        onClick = {
                                            runCatching { copyToClipboard(resolvedUrl) }
                                        },
                                        modifier = Modifier.size(24.dp).testTag("copy-resolved-url-btn"),
                                    ) {
                                        Icon(
                                            painter = painterResource(Res.drawable.ic_content_copy),
                                            contentDescription = "Copy resolved URL",
                                            tint = ClapiColors.OnSurfaceDim,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                    IconButton(
                                        onClick = { showPreviewPopup = false },
                                        modifier = Modifier.size(24.dp).testTag("close-resolved-url-popup-btn"),
                                    ) {
                                        Icon(
                                            painter = painterResource(Res.drawable.ic_close),
                                            contentDescription = "Close",
                                            tint = ClapiColors.OnSurfaceDim,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }

                            SelectionContainer {
                                Text(
                                    text = resolvedUrl.ifBlank { "(empty URL)" },
                                    fontFamily = CodeFontFamily,
                                    fontSize = 13.sp,
                                    color = if (resolvedUrl.isBlank()) ClapiColors.OnSurfaceDim else ClapiColors.Primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(ClapiColors.SurfaceContainer)
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                        .testTag("resolved-url-text"),
                                )
                            }
                        }
                    }
                }
            }
        }

        SendButton(isLoading, onSend, onCancel)
        SaveButton(isLoading = isLoading, onClick = onSave)
        RetryControlsButton(
            retryEnabled = retryEnabled,
            retryCount = retryCount,
            retryDelayMs = retryDelayMs,
            isLoading = isLoading,
            onRetryEnabledChanged = onRetryEnabledChanged,
            onRetryCountChanged = onRetryCountChanged,
            onRetryDelayChanged = onRetryDelayChanged,
        )
        CopyCurlButton(isLoading = isLoading, copyFormats = copyFormats)
    }
}

// ── Method dropdown ─────────────────────────────────────────────

@Composable
private fun MethodDropdown(method: HttpMethodType, onMethodChanged: (HttpMethodType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val color = httpMethodColor(method)

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.12f))
                .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .handPointerHover()
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("method-dropdown"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(method.name, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.width(2.dp))
            Icon(painter = painterResource(Res.drawable.ic_arrow_drop_down), contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            HttpMethodType.entries.forEach { m ->
                DenseDropdownMenuItem(
                    text = { Text(m.name, color = httpMethodColor(m), fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    onClick = { onMethodChanged(m); expanded = false },
                )
            }
        }
    }
}

// ── Action buttons ──────────────────────────────────────────────

@Composable
private fun SendButton(isLoading: Boolean, onClick: () -> Unit, onCancel: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    if (isLoading) {
        // H-2: When a request is in-flight, show a Stop / Cancel button so the user
        // can abort it immediately without having to close the tab.
        Box(
            modifier = Modifier
                .widthIn(min = 80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isHovered) androidx.compose.ui.graphics.Color(0xFFD32F2F)
                    else androidx.compose.ui.graphics.Color(0xFFC62828)
                )
                .hoverable(interactionSource)
                .handPointerHover()
                .clickable(onClick = onCancel)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("stop-button"),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(painter = painterResource(Res.drawable.ic_stop),
                    contentDescription = Strings.t("stop_request"),
                    tint = ClapiColors.OnPrimary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = Strings.t("stop"),
                    color = ClapiColors.OnPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .widthIn(min = 80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isHovered) ClapiColors.Primary.copy(alpha = 0.9f) else ClapiColors.Primary)
                .hoverable(interactionSource)
                .handPointerHover()
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("send-button"),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = Strings.send,
                    color = ClapiColors.OnPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                Icon(painter = painterResource(Res.drawable.ic_send), contentDescription = null, tint = ClapiColors.OnPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
internal fun SaveButton(isLoading: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .testTag("save-button")
            .widthIn(min = 56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isHovered) ClapiColors.SurfaceHigh else ClapiColors.SurfaceContainer)
            .hoverable(interactionSource)
            .handPointerHover(!isLoading)
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = Strings.save,
                color = ClapiColors.OnSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            Icon(painter = painterResource(Res.drawable.ic_save), contentDescription = null, tint = ClapiColors.OnSurface, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun CopyCurlButton(isLoading: Boolean, copyFormats: List<CopyFormatOption>) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var expanded by remember { mutableStateOf(false) }

    Box {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isHovered) ClapiColors.SurfaceHigh else ClapiColors.SurfaceContainer)
                .hoverable(interactionSource)
                .handPointerHover(!isLoading)
                .clickable(enabled = !isLoading) {
                    if (copyFormats.size == 1 && copyFormats.first().available) copyFormats.first().action()
                    else if (copyFormats.isNotEmpty()) expanded = true
                    else expanded = true
                }
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .testTag("copy-curl-button"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painter = painterResource(Res.drawable.ic_content_copy), contentDescription = Strings.t("copy_as"), tint = ClapiColors.OnSurface, modifier = Modifier.size(15.dp))
        }

        if (copyFormats.isNotEmpty()) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                copyFormats.forEach { format ->
                    DenseDropdownMenuItem(
                        modifier = Modifier.testTag("copy-format-${format.label.lowercase()}"),
                        text = {
                            Column {
                                Text(format.label, fontSize = 13.sp)
                                format.reason?.let { Text(it, fontSize = 11.sp, color = ClapiColors.OnSurfaceDim) }
                            }
                        },
                        enabled = format.available,
                        onClick = { format.action(); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun RetryControlsButton(
    retryEnabled: Boolean,
    retryCount: Int,
    retryDelayMs: Long,
    isLoading: Boolean,
    onRetryEnabledChanged: (Boolean) -> Unit,
    onRetryCountChanged: (Int) -> Unit,
    onRetryDelayChanged: (Long) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var showDialog by remember { mutableStateOf(false) }
    val dragState = rememberDraggableDialogState()

    var localRetryEnabled by remember(showDialog, retryEnabled) { mutableStateOf(retryEnabled) }
    var localRetryCount by remember(showDialog, retryCount) { mutableStateOf(retryCount.toString()) }
    var localRetryDelayMs by remember(showDialog, retryDelayMs) { mutableStateOf(retryDelayMs.toString()) }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isHovered) ClapiColors.SurfaceHigh else ClapiColors.SurfaceContainer)
                .hoverable(interactionSource)
                .handPointerHover(!isLoading)
                .clickable(enabled = !isLoading) { showDialog = true }
                .padding(horizontal = 7.dp, vertical = 8.dp)
                .testTag("retry-menu-button"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painter = painterResource(Res.drawable.ic_refresh),
                contentDescription = if (retryEnabled) {
                    "${Strings.retry} ($retryCount, ${retryDelayMs}ms)"
                } else {
                    Strings.retry
                },
                tint = ClapiColors.OnSurface,
                modifier = Modifier.size(15.dp),
            )
        }

        if (showDialog) {
            Dialog(onDismissRequest = { showDialog = false }) {
                Box(
                    modifier = Modifier.draggableDialogBackdrop(dragState, onDismiss = { showDialog = false }),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .draggableDialogCard(dragState, cornerRadius = 12.dp)
                            .widthIn(min = 420.dp, max = 560.dp)
                            .testTag("retry-config-dialog"),
                    ) {
                        DialogTitleBar(
                            title = Strings.retry,
                            onClose = { showDialog = false },
                            onDrag = dragState::onDrag,
                        )

                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = localRetryEnabled,
                                    onCheckedChange = { localRetryEnabled = it },
                                    modifier = Modifier.testTag("retry-enabled-checkbox"),
                                )
                                Text(Strings.t("retry_enable"), color = ClapiColors.OnSurface)
                            }

                        OutlinedTextField(
                            value = localRetryCount,
                            onValueChange = { localRetryCount = it.filter { ch -> ch.isDigit() } },
                            singleLine = true,
                            enabled = localRetryEnabled,
                            label = { Text(Strings.t("attempts")) },
                            modifier = Modifier.fillMaxWidth().testTag("retry-attempts-input"),
                        )

                        OutlinedTextField(
                            value = localRetryDelayMs,
                            onValueChange = { localRetryDelayMs = it.filter { ch -> ch.isDigit() } },
                            singleLine = true,
                            enabled = localRetryEnabled,
                            label = { Text("${Strings.t("delay")} (ms)") },
                            modifier = Modifier.fillMaxWidth().testTag("retry-delay-input"),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ClapiColors.SurfaceContainer)
                                    .clickable { showDialog = false }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .testTag("retry-config-cancel"),
                            ) {
                                Text(Strings.cancel, color = ClapiColors.OnSurface, fontWeight = FontWeight.Medium)
                            }

                            Spacer(Modifier.width(8.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ClapiColors.Primary)
                                    .clickable {
                                        val attempts = localRetryCount.toIntOrNull()?.coerceAtLeast(1) ?: retryCount
                                        val delayMs = localRetryDelayMs.toLongOrNull()?.coerceAtLeast(0L) ?: retryDelayMs
                                        onRetryEnabledChanged(localRetryEnabled)
                                        onRetryCountChanged(attempts)
                                        onRetryDelayChanged(delayMs)
                                        showDialog = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .testTag("retry-config-save"),
                            ) {
                                Text(Strings.save, color = ClapiColors.OnPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
}
