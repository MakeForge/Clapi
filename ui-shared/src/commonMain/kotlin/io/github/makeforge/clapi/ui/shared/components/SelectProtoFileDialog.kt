package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.makeforge.clapi.core.storage.git.ProtoPathResolver
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_close
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_description
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_folder_open
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_help_outline
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_open_in_new
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.platform.DiscoveredProtoFile
import io.github.makeforge.clapi.ui.shared.platform.DocsLinks
import io.github.makeforge.clapi.ui.shared.platform.PlatformLazyVerticalScrollbar
import io.github.makeforge.clapi.ui.shared.platform.listProtoFilesInDirectory
import io.github.makeforge.clapi.ui.shared.platform.pickProtoDirectory
import io.github.makeforge.clapi.ui.shared.platform.pickProtoFile
import io.github.makeforge.clapi.ui.shared.shortcuts.AppHotkeys
import io.github.makeforge.clapi.ui.shared.shortcuts.hotkeys
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily
import org.jetbrains.compose.resources.painterResource

/**
 * Dialog for selecting and saving a `.proto` schema file (single file, proto folder + target file,
 * or collection-level proto file) and its Git sync preference for a gRPC request.
 */
@Composable
fun SelectProtoFileDialog(
    currentProtoFilePath: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    currentProtoFolderPath: String? = null,
    errorMessage: String? = null,
    isLoading: Boolean = false,
    collectionProtoFiles: List<String> = emptyList(),
    isGitSyncEnabled: Boolean = false,
    initialSyncToGit: Boolean = false,
    onSave: (protoFilePath: String) -> Unit = {},
    onSaveWithSync: ((protoFilePath: String, syncToGit: Boolean) -> Unit)? = null,
    onSaveWithClose: ((protoFilePath: String, syncToGit: Boolean, closeDialog: Boolean) -> Unit)? = null,
    onSaveSelection: ((protoFilePath: String, protoFolderPath: String?, syncToGit: Boolean, closeDialog: Boolean) -> Unit)? = null,
    onPickFile: ((onSelected: (String) -> Unit) -> Unit)? = null,
    onPickFolder: ((onSelected: (String) -> Unit) -> Unit)? = null,
    onListProtoFiles: ((dirPath: String) -> List<DiscoveredProtoFile>)? = null,
) {
    var selectedPath by remember(currentProtoFilePath) {
        mutableStateOf(currentProtoFilePath.orEmpty())
    }
    var selectedFolderPath by remember(currentProtoFolderPath) {
        mutableStateOf(currentProtoFolderPath.orEmpty())
    }
    var syncToGit by remember(initialSyncToGit) {
        mutableStateOf(initialSyncToGit)
    }
    var baselinePath by remember(currentProtoFilePath) {
        mutableStateOf(currentProtoFilePath.orEmpty())
    }
    var baselineFolderPath by remember(currentProtoFolderPath) {
        mutableStateOf(currentProtoFolderPath.orEmpty())
    }
    var baselineSync by remember(initialSyncToGit) {
        mutableStateOf(initialSyncToGit)
    }
    var showUnsavedConfirm by remember { mutableStateOf(false) }

    val discoveredProtos = remember(selectedFolderPath, onListProtoFiles) {
        if (selectedFolderPath.isBlank()) {
            emptyList()
        } else {
            val lister = onListProtoFiles ?: ::listProtoFilesInDirectory
            lister(selectedFolderPath)
        }
    }

    val isCollectionProtoSelected = selectedPath.isNotBlank() &&
        selectedFolderPath.isBlank() &&
        collectionProtoFiles.any { it == selectedPath }

    val canSave = selectedPath.isNotBlank() &&
        !isLoading &&
        (selectedFolderPath.isBlank() || discoveredProtos.isNotEmpty())

    fun isDirty(): Boolean {
        val syncChanged = isGitSyncEnabled && !isCollectionProtoSelected && syncToGit != baselineSync
        return selectedPath.trim() != baselinePath.trim() ||
            selectedFolderPath.trim() != baselineFolderPath.trim() ||
            syncChanged
    }

    fun handleSave(closeDialog: Boolean = false) {
        val trimmedPath = selectedPath.trim()
        val trimmedFolder = selectedFolderPath.trim().takeIf { it.isNotEmpty() }
        val effectiveSync = if (isCollectionProtoSelected) false else syncToGit
        if (canSave && trimmedPath.isNotBlank()) {
            baselinePath = trimmedPath
            baselineFolderPath = trimmedFolder.orEmpty()
            baselineSync = effectiveSync
            if (onSaveSelection != null) {
                onSaveSelection(trimmedPath, trimmedFolder, effectiveSync, closeDialog)
            } else if (onSaveWithClose != null) {
                onSaveWithClose(trimmedPath, effectiveSync, closeDialog)
            } else if (onSaveWithSync != null) {
                onSaveWithSync(trimmedPath, effectiveSync)
                if (closeDialog) onDismiss()
            } else {
                onSave(trimmedPath)
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

    val dragState = rememberDraggableDialogState()

    Dialog(onDismissRequest = { requestDismiss() }) {
        Box(
            modifier = Modifier.draggableDialogBackdrop(dragState, onDismiss = { requestDismiss() }),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = modifier
                    .draggableDialogCard(dragState, cornerRadius = 12.dp)
                    .widthIn(min = 460.dp, max = 580.dp)
                    .hotkeys {
                        on(AppHotkeys.Save) {
                            handleSave(closeDialog = false)
                        }
                        on(AppHotkeys.Escape) {
                            requestDismiss()
                        }
                    }
                    .testTag("select-proto-file-dialog"),
            ) {
                DialogTitleBar(
                    title = "Select Proto File",
                    actions = {
                        DocsHelpButton(
                            url = DocsLinks.protobufSettings,
                            tooltip = "Protobuf documentation",
                            tag = "proto-dialog-docs-btn",
                            size = 28.dp,
                        )
                    },
                    onClose = { requestDismiss() },
                    closeButtonTag = "proto-dialog-close",
                    onDrag = dragState::onDrag,
                )

                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
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
                        Icon(
                            painter = painterResource(Res.drawable.ic_description),
                            contentDescription = null,
                            tint = ClapiColors.MethodPatch,
                            modifier = Modifier.size(16.dp),
                        )
                        DialogBasicTextField(
                            value = selectedPath,
                            onValueChange = { newValue ->
                                selectedPath = newValue
                                if (selectedFolderPath.isNotBlank()) {
                                    selectedFolderPath = ""
                                }
                            },
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
                                        text = if (selectedFolderPath.isNotBlank()) {
                                            Strings.t("proto_folder_select_file_hint")
                                        } else {
                                            "No proto file selected"
                                        },
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

                    // Action buttons: Select Proto File & Select Proto Folder
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClapiColors.SurfaceVariant)
                                .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable(enabled = !isLoading) {
                                    val picker = onPickFile ?: { cb -> pickProtoFile(cb) }
                                    picker { pickedPath ->
                                        selectedFolderPath = ""
                                        selectedPath = pickedPath
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("proto-dialog-pick-button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_description),
                                contentDescription = null,
                                tint = ClapiColors.OnSurface,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = if (selectedPath.isBlank() || selectedFolderPath.isNotBlank()) {
                                    "Select Proto File"
                                } else {
                                    "Replace Proto File"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = ClapiColors.OnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClapiColors.SurfaceVariant)
                                .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable(enabled = !isLoading) {
                                    val picker = onPickFolder ?: { cb -> pickProtoDirectory(cb) }
                                    picker { pickedDir ->
                                        selectedFolderPath = pickedDir
                                        selectedPath = ""
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("proto-dialog-pick-folder-button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_folder_open),
                                contentDescription = null,
                                tint = ClapiColors.OnSurface,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = if (selectedFolderPath.isBlank()) {
                                    Strings.t("select_proto_folder")
                                } else {
                                    Strings.t("replace_proto_folder")
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = ClapiColors.OnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // Selected proto folder section
                    if (selectedFolderPath.isNotBlank()) {
                        Text(
                            text = Strings.t("proto_folder_label"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = ClapiColors.OnSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClapiColors.SurfaceContainer)
                                .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("proto-dialog-folder-display"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_folder_open),
                                contentDescription = null,
                                tint = ClapiColors.Primary,
                                modifier = Modifier.size(15.dp),
                            )
                            SelectionContainer(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedFolderPath,
                                    fontSize = 12.sp,
                                    fontFamily = CodeFontFamily,
                                    color = ClapiColors.OnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = {
                                    selectedFolderPath = ""
                                    selectedPath = ""
                                },
                                modifier = Modifier
                                    .size(22.dp)
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .testTag("proto-dialog-clear-folder-button"),
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_close),
                                    contentDescription = "Clear proto folder",
                                    tint = ClapiColors.OnSurfaceDim,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }

                        Text(
                            text = Strings.t("proto_folder_files_label"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = ClapiColors.OnSurfaceVariant,
                        )

                        if (discoveredProtos.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ClapiColors.SurfaceContainer)
                                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 14.dp)
                                    .testTag("proto-dialog-folder-empty"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = Strings.t("proto_folder_empty"),
                                    fontSize = 12.sp,
                                    color = ClapiColors.OnSurfaceDim,
                                )
                            }
                        } else {
                            val listState = rememberLazyListState()
                            val isRelativeGitFolder = ProtoPathResolver.isRelativeGitProtoPath(selectedFolderPath)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp, max = 180.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ClapiColors.SurfaceContainer)
                                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                                    .testTag("proto-dialog-folder-file-list"),
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 4.dp, top = 4.dp, bottom = 4.dp, end = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    items(
                                        items = discoveredProtos,
                                        key = { it.relativePath },
                                    ) { entry ->
                                        val candidatePath = if (isRelativeGitFolder) {
                                            "${selectedFolderPath.trimEnd('/', '\\')}/${entry.relativePath}"
                                        } else {
                                            entry.absolutePath
                                        }
                                        val normalizedSelected = selectedPath.replace('\\', '/')
                                        val isSelected = normalizedSelected == candidatePath.replace('\\', '/') ||
                                            normalizedSelected == entry.absolutePath.replace('\\', '/')
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    if (isSelected) ClapiColors.Primary.copy(alpha = 0.15f)
                                                    else Color.Transparent
                                                )
                                                .pointerHoverIcon(PointerIcon.Hand)
                                                .clickable { selectedPath = candidatePath }
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                                .testTag("proto-dialog-folder-item-${entry.relativePath}"),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                painter = painterResource(Res.drawable.ic_description),
                                                contentDescription = null,
                                                tint = if (isSelected) ClapiColors.Primary else ClapiColors.OnSurfaceDim,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Text(
                                                text = entry.relativePath,
                                                fontSize = 12.sp,
                                                fontFamily = CodeFontFamily,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isSelected) ClapiColors.Primary else ClapiColors.OnSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }
                                PlatformLazyVerticalScrollbar(
                                    listState = listState,
                                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                    testTag = "proto-dialog-folder-scrollbar",
                                )
                            }
                        }
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
                                val isSelected = selectedFolderPath.isBlank() && selectedPath == path
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSelected) ClapiColors.Primary.copy(alpha = 0.15f) else Color.Transparent)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable {
                                            selectedFolderPath = ""
                                            selectedPath = path
                                            syncToGit = false
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                        .testTag("proto-dialog-collection-item-$path"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_description),
                                        contentDescription = null,
                                        tint = if (isSelected) ClapiColors.Primary else ClapiColors.OnSurfaceDim,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    SelectionContainer(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
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
                        }
                    }

                    if (isGitSyncEnabled && !isCollectionProtoSelected) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .pointerHoverIcon(PointerIcon.Hand)
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
                            if (selectedFolderPath.isNotBlank()) {
                                Text(
                                    text = Strings.t("proto_folder_sync_to_git_hint"),
                                    fontSize = 11.sp,
                                    color = ClapiColors.OnSurfaceDim,
                                    modifier = Modifier
                                        .padding(start = 28.dp)
                                        .testTag("proto-dialog-folder-sync-hint"),
                                )
                            }
                        }
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        SelectionContainer {
                            Text(
                                text = errorMessage,
                                color = ClapiColors.Error,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("proto-dialog-error"),
                            )
                        }
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
                                    .pointerHoverIcon(PointerIcon.Hand)
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
                                .pointerHoverIcon(PointerIcon.Hand)
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
                                    if (canSave) ClapiColors.Primary
                                    else ClapiColors.Primary.copy(alpha = 0.5f)
                                )
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable(enabled = canSave) {
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
