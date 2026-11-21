package io.github.crustack.clapi.ui.shared.components

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.crustack.clapi.ui.shared.generated.resources.Res
import io.github.crustack.clapi.ui.shared.generated.resources.ic_close
import io.github.crustack.clapi.ui.shared.generated.resources.ic_description
import io.github.crustack.clapi.ui.shared.generated.resources.ic_folder_open
import io.github.crustack.clapi.ui.shared.generated.resources.ic_help_outline
import io.github.crustack.clapi.ui.shared.generated.resources.ic_open_in_new
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.platform.pickProtoFile
import io.github.crustack.clapi.ui.shared.shortcuts.AppHotkeys
import io.github.crustack.clapi.ui.shared.shortcuts.hotkeys
import io.github.crustack.clapi.ui.shared.theme.ClapiColors
import io.github.crustack.clapi.ui.shared.theme.CodeFontFamily
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/**
 * Dialog for selecting and saving a `.proto` schema file for a gRPC request.
 */
@Composable
fun SelectProtoFileDialog(
    currentProtoFilePath: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    isLoading: Boolean = false,
    collectionProtoFiles: List<String> = emptyList(),
    isGitSyncEnabled: Boolean = false,
    initialSyncToGit: Boolean = false,
    onSave: (protoFilePath: String) -> Unit = {},
    onSaveWithSync: ((protoFilePath: String, syncToGit: Boolean) -> Unit)? = null,
    onSaveWithClose: ((protoFilePath: String, syncToGit: Boolean, closeDialog: Boolean) -> Unit)? = null,
    onPickFile: ((onSelected: (String) -> Unit) -> Unit)? = null,
) {
    var selectedPath by remember(currentProtoFilePath) {
        mutableStateOf(currentProtoFilePath.orEmpty())
    }
    var syncToGit by remember(initialSyncToGit) {
        mutableStateOf(initialSyncToGit)
    }
    var baselinePath by remember(currentProtoFilePath) {
        mutableStateOf(currentProtoFilePath.orEmpty())
    }
    var baselineSync by remember(initialSyncToGit) {
        mutableStateOf(initialSyncToGit)
    }
    var showUnsavedConfirm by remember { mutableStateOf(false) }

    fun isDirty(): Boolean =
        selectedPath.trim() != baselinePath.trim() || (isGitSyncEnabled && syncToGit != baselineSync)

    fun handleSave(closeDialog: Boolean = false) {
        val trimmed = selectedPath.trim()
        if (trimmed.isNotBlank() && !isLoading) {
            baselinePath = trimmed
            baselineSync = syncToGit
            if (onSaveWithClose != null) {
                onSaveWithClose(trimmed, syncToGit, closeDialog)
            } else if (onSaveWithSync != null) {
                onSaveWithSync(trimmed, syncToGit)
                if (closeDialog) onDismiss()
            } else {
                onSave(trimmed)
                if (closeDialog) onDismiss()
            }
        }
    }

    fun requestDismiss() {
        if (isDirty()) {
            showUnsavedConfirm = true
        } else {
            onDismiss()
        }
    }

    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }

    Dialog(onDismissRequest = { requestDismiss() }) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .dialogEscape(onDismiss = { requestDismiss() })
                .onSizeChanged { viewportSize = it }
                .pointerInput(Unit) { detectTapGestures { requestDismiss() } },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .widthIn(min = 440.dp, max = 560.dp)
                    .onSizeChanged { cardSize = it }
                    .clip(RoundedCornerShape(12.dp))
                    .background(ClapiColors.Surface)
                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(12.dp))
                    .draggableNoSlop { dx, dy ->
                        val (cx, cy) = clampDialogOffsetFromCenter(
                            offsetX = offsetX + dx,
                            offsetY = offsetY + dy,
                            cardSize = cardSize,
                            viewportSize = viewportSize,
                        )
                        offsetX = cx
                        offsetY = cy
                    }
                    .pointerInput(Unit) { detectTapGestures { } }
                    .hotkeys {
                        on(AppHotkeys.Save) {
                            handleSave(closeDialog = false)
                        }
                        on(AppHotkeys.Escape) {
                            requestDismiss()
                        }
                    }
                    .padding(24.dp)
                    .testTag("select-proto-file-dialog"),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Header row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Select Proto File",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ClapiColors.OnSurface,
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp).testTag("proto-dialog-close"),
                        ) {
                            Icon(painter = painterResource(Res.drawable.ic_close),
                                contentDescription = "Close",
                                tint = ClapiColors.OnSurfaceDim,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    Text(
                        text = "Using Proto File",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = ClapiColors.OnSurfaceVariant,
                    )

                    // Current file path display / input box
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ClapiColors.SurfaceContainer)
                            .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("proto-dialog-path-display"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(painter = painterResource(Res.drawable.ic_description),
                            contentDescription = null,
                            tint = ClapiColors.MethodPatch,
                            modifier = Modifier.size(16.dp),
                        )
                        BasicTextField(
                            value = selectedPath,
                            onValueChange = { selectedPath = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = ClapiColors.OnSurface,
                                fontSize = 13.sp,
                                fontFamily = CodeFontFamily,
                            ),
                            cursorBrush = SolidColor(ClapiColors.Primary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("proto-dialog-path-input"),
                            decorationBox = { inner ->
                                if (selectedPath.isEmpty()) {
                                    Text(
                                        text = "No proto file selected",
                                        color = ClapiColors.OnSurfaceDim,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                inner()
                            },
                        )
                    }

                    if (collectionProtoFiles.isNotEmpty()) {
                        Text(
                            text = "Collection Proto Files",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = ClapiColors.OnSurfaceVariant,
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClapiColors.SurfaceContainer)
                                .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                                .padding(4.dp)
                                .testTag("proto-dialog-collection-list"),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            collectionProtoFiles.forEach { path ->
                                val fileName = path.substringAfterLast(java.io.File.separatorChar).substringAfterLast('/')
                                val isSelected = selectedPath == path
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSelected) ClapiColors.Primary.copy(alpha = 0.15f) else Color.Transparent)
                                        .clickable { selectedPath = path }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                        .testTag("proto-dialog-collection-item-$path"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(painter = painterResource(Res.drawable.ic_description),
                                        contentDescription = null,
                                        tint = if (isSelected) ClapiColors.Primary else ClapiColors.OnSurfaceDim,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = fileName,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) ClapiColors.Primary else ClapiColors.OnSurface,
                                    )
                                    Text(
                                        text = path,
                                        fontSize = 11.sp,
                                        fontFamily = CodeFontFamily,
                                        color = ClapiColors.OnSurfaceDim,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }

                    // Select Proto File button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ClapiColors.SurfaceVariant)
                            .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                            .clickable(enabled = !isLoading) {
                                val picker = onPickFile ?: { cb -> pickProtoFile(cb) }
                                picker { pickedPath ->
                                    selectedPath = pickedPath
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("proto-dialog-pick-button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(painter = painterResource(Res.drawable.ic_folder_open),
                            contentDescription = null,
                            tint = ClapiColors.OnSurface,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = if (selectedPath.isBlank()) "Select Proto File" else "Replace Proto File",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = ClapiColors.OnSurface,
                        )
                    }

                    if (isGitSyncEnabled) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { syncToGit = !syncToGit }
                                .padding(vertical = 4.dp)
                                .testTag("proto-dialog-sync-to-git-toggle"),
                        ) {
                            Checkbox(
                                checked = syncToGit,
                                onCheckedChange = { syncToGit = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = ClapiColors.Primary,
                                    checkmarkColor = ClapiColors.OnPrimary,
                                    uncheckedColor = ClapiColors.OnSurfaceDim,
                                ),
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = Strings.syncToGit,
                                fontSize = 13.sp,
                                color = ClapiColors.OnSurface,
                            )
                        }
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Text(
                            text = errorMessage,
                            color = ClapiColors.Error,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("proto-dialog-error"),
                        )
                    }

                    // Built-in types hint
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(ClapiColors.SurfaceContainer)
                            .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("proto-dialog-builtin-hint"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_help_outline),
                            contentDescription = null,
                            tint = ClapiColors.OnSurfaceDim,
                            modifier = Modifier.size(16.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Standard Google protobuf types (e.g. google/protobuf/timestamp.proto) are supported by default.",
                                fontSize = 11.sp,
                                color = ClapiColors.OnSurfaceDim,
                                lineHeight = 15.sp,
                            )
                            val uriHandler = LocalUriHandler.current
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clickable {
                                        runCatching {
                                            uriHandler.openUri("https://protobuf.dev/reference/protobuf/google.protobuf")
                                        }
                                    }
                                    .testTag("proto-dialog-builtin-link"),
                            ) {
                                Text(
                                    text = "protobuf.dev/reference",
                                    fontSize = 11.sp,
                                    color = ClapiColors.Primary,
                                    textDecoration = TextDecoration.Underline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    painter = painterResource(Res.drawable.ic_open_in_new),
                                    contentDescription = "Open Link",
                                    tint = ClapiColors.Primary,
                                    modifier = Modifier.size(11.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Action buttons: Cancel & Save
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ClapiColors.SurfaceContainer)
                                .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                                .clickable(enabled = !isLoading, onClick = { requestDismiss() })
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .testTag("proto-dialog-cancel"),
                        ) {
                            Text(
                                text = "Cancel",
                                fontSize = 13.sp,
                                color = ClapiColors.OnSurface,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (selectedPath.isNotBlank() && !isLoading) ClapiColors.Primary
                                    else ClapiColors.Primary.copy(alpha = 0.5f)
                                )
                                .clickable(enabled = selectedPath.isNotBlank() && !isLoading) {
                                    handleSave(closeDialog = true)
                                }
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                                .testTag("proto-dialog-save"),
                        ) {
                            Text(
                                text = if (isLoading) "Loading…" else "Save",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ClapiColors.OnPrimary,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showUnsavedConfirm) {
        DirtyCloseDialog(
            title = "Unsaved Changes",
            message = Strings.t("request_unsaved_changes_message"),
            onSave = {
                handleSave(closeDialog = true)
                showUnsavedConfirm = false
            },
            onDiscard = {
                showUnsavedConfirm = false
                onDismiss()
            },
            onCancel = {
                showUnsavedConfirm = false
            },
        )
    }
}
