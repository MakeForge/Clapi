package io.github.makeforge.clapi.ui.shared.components.collectioneditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.core.model.storage.StorageType
import io.github.makeforge.clapi.ui.shared.components.handPointerHover
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_add
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_delete
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_description
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_folder
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_help_outline
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_open_in_new
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.platform.PlatformLazyVerticalScrollbar
import io.github.makeforge.clapi.ui.shared.platform.openFileInDefaultApp
import io.github.makeforge.clapi.ui.shared.platform.pickProtoFiles
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.ProtoFileItemState
import io.github.makeforge.clapi.ui.shared.state.ProtoImportPathState
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily
import org.jetbrains.compose.resources.painterResource

@Composable
fun CollectionProtobufSettings(
    state: AppState,
    onSave: () -> Unit = {},
    tab: RequestTabState? = null,
    modifier: Modifier = Modifier,
    onPickProtoFiles: ((onSelected: (List<String>) -> Unit) -> Unit)? = null,
    onPickImportPaths: ((onSelected: (List<String>) -> Unit) -> Unit)? = null,
    onOpenFile: ((String) -> Unit)? = null,
) {
    val fileOpener = onOpenFile ?: { path -> openFileInDefaultApp(path) }
    val isGitSyncEnabled = (tab?.storageType ?: state.activeCollectionNode?.storageType) == StorageType.GIT

    val listState = rememberLazyListState()
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 12.dp, start = 16.dp, top = 16.dp, bottom = 16.dp)
                .testTag("collection-protobuf-panel"),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ── Section 1: Proto Files ──────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "Proto Files",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ClapiColors.OnSurface,
                        )
                        Text(
                            text = "Linked proto files available for gRPC request selection across the collection.",
                            fontSize = 12.sp,
                            color = ClapiColors.OnSurfaceDim,
                        )
                    }
                    Button(
                        onClick = {
                            val picker = onPickProtoFiles ?: { cb -> pickProtoFiles(cb) }
                            picker { pickedPaths ->
                                var changed = false
                                for (p in pickedPaths) {
                                    if (state.collectionProtoFiles.none { it.path == p }) {
                                        state.collectionProtoFiles.add(ProtoFileItemState(p))
                                        tab?.collectionProtoFiles?.add(ProtoFileItemState(p))
                                        changed = true
                                    }
                                }
                                if (changed) {
                                    tab?.markDirty() ?: onSave()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ClapiColors.SurfaceContainer,
                            contentColor = ClapiColors.OnSurface,
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(30.dp).testTag("add-proto-files-button"),
                    ) {
                        Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(Strings.addProtoFiles, fontSize = 12.sp)
                    }
                }
            }

            if (state.collectionProtoFiles.isEmpty()) {
                item {
                    Text(
                        text = "No collection-level proto files linked yet.",
                        fontSize = 12.sp,
                        color = ClapiColors.OnSurfaceDim,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(state.collectionProtoFiles, key = { it.path }) { protoFile ->
                    ProtoFileRow(
                        item = protoFile,
                        isGitSyncEnabled = isGitSyncEnabled,
                        onToggleSyncToGit = {
                            protoFile.syncToGit = !protoFile.syncToGit
                            tab?.collectionProtoFiles?.firstOrNull { it.path == protoFile.path }?.let {
                                it.syncToGit = protoFile.syncToGit
                            }
                            tab?.markDirty() ?: onSave()
                        },
                        onOpen = { fileOpener(protoFile.path) },
                        onDelete = {
                            state.collectionProtoFiles.remove(protoFile)
                            tab?.collectionProtoFiles?.removeAll { it.path == protoFile.path }
                            tab?.markDirty() ?: onSave()
                        },
                    )
                }
            }

            // ── Section 2: Import Paths ─────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "Import Paths",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ClapiColors.OnSurface,
                        )
                        Text(
                            text = "Proto files or directories used solely to resolve imports when compiling and executing gRPC requests.",
                            fontSize = 12.sp,
                            color = ClapiColors.OnSurfaceDim,
                        )
                    }
                    Button(
                        onClick = {
                            val picker = onPickImportPaths ?: { cb -> pickProtoFiles(cb) }
                            picker { pickedPaths ->
                                var changed = false
                                for (p in pickedPaths) {
                                    if (state.collectionImportPaths.none { it.path == p }) {
                                        state.collectionImportPaths.add(ProtoImportPathState(p, enabled = true))
                                        tab?.collectionImportPaths?.add(ProtoImportPathState(p, enabled = true))
                                        changed = true
                                    }
                                }
                                if (changed) {
                                    tab?.markDirty() ?: onSave()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ClapiColors.SurfaceContainer,
                            contentColor = ClapiColors.OnSurface,
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(30.dp).testTag("add-import-paths-button"),
                    ) {
                        Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(Strings.importPaths, fontSize = 12.sp)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(ClapiColors.SurfaceContainer)
                        .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("collection-protobuf-builtin-hint"),
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
                            text = "Standard Google protobuf types (e.g. google/protobuf/timestamp.proto) are supported by default without configuring extra import paths.",
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
                                .testTag("collection-protobuf-builtin-link"),
                        ) {
                            Text(
                                text = "https://protobuf.dev/reference/protobuf/google.protobuf",
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
            }

            if (state.collectionImportPaths.isEmpty()) {
                item {
                    Text(
                        text = "No protobuf import paths configured.",
                        fontSize = 12.sp,
                        color = ClapiColors.OnSurfaceDim,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(state.collectionImportPaths, key = { it.path }) { importPath ->
                    ProtoImportPathRow(
                        item = importPath,
                        isGitSyncEnabled = isGitSyncEnabled,
                        onToggle = {
                            importPath.enabled = !importPath.enabled
                            tab?.collectionImportPaths?.firstOrNull { it.path == importPath.path }?.let {
                                it.enabled = importPath.enabled
                            }
                            tab?.markDirty() ?: onSave()
                        },
                        onToggleSyncToGit = {
                            importPath.syncToGit = !importPath.syncToGit
                            tab?.collectionImportPaths?.firstOrNull { it.path == importPath.path }?.let {
                                it.syncToGit = importPath.syncToGit
                            }
                            tab?.markDirty() ?: onSave()
                        },
                        onOpen = { fileOpener(importPath.path) },
                        onDelete = {
                            state.collectionImportPaths.remove(importPath)
                            tab?.collectionImportPaths?.removeAll { it.path == importPath.path }
                            tab?.markDirty() ?: onSave()
                        },
                    )
                }
            }
        }
        PlatformLazyVerticalScrollbar(
            listState = listState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            testTag = "collection-protobuf-scrollbar",
        )
    }
}

@Composable
internal fun ProtoFileRow(
    item: ProtoFileItemState,
    isGitSyncEnabled: Boolean = false,
    onToggleSyncToGit: () -> Unit = {},
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(ClapiColors.SurfaceContainer)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("proto-file-row-${item.path}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painter = painterResource(Res.drawable.ic_description),
            contentDescription = null,
            tint = ClapiColors.MethodPatch,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = item.fileName,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = ClapiColors.OnSurface,
        )
        Text(
            text = item.path,
            fontSize = 11.sp,
            fontFamily = CodeFontFamily,
            color = ClapiColors.OnSurfaceDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isGitSyncEnabled) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .handPointerHover()
                    .clickable { onToggleSyncToGit() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .testTag("proto-sync-toggle-${item.path}"),
            ) {
                Checkbox(
                    checked = item.syncToGit,
                    onCheckedChange = { onToggleSyncToGit() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = ClapiColors.Primary,
                        checkmarkColor = ClapiColors.OnPrimary,
                        uncheckedColor = ClapiColors.OnSurfaceDim,
                    ),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = Strings.syncToGit,
                    fontSize = 11.sp,
                    color = if (item.syncToGit) ClapiColors.Primary else ClapiColors.OnSurfaceDim,
                )
            }
        }
        IconButton(
            onClick = onOpen,
            modifier = Modifier.size(24.dp).handPointerHover().testTag("open-file-btn-${item.path}"),
        ) {
            Icon(painter = painterResource(Res.drawable.ic_open_in_new),
                contentDescription = Strings.openFileExternally,
                tint = ClapiColors.OnSurfaceDim,
                modifier = Modifier.size(14.dp),
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(24.dp).handPointerHover().testTag("delete-proto-btn-${item.path}"),
        ) {
            Icon(painter = painterResource(Res.drawable.ic_delete),
                contentDescription = Strings.delete,
                tint = ClapiColors.Error,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
internal fun ProtoImportPathRow(
    item: ProtoImportPathState,
    isGitSyncEnabled: Boolean = false,
    onToggle: () -> Unit,
    onToggleSyncToGit: () -> Unit = {},
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(ClapiColors.SurfaceContainer)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("import-path-row-${item.path}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = item.enabled,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = ClapiColors.Primary,
                checkmarkColor = ClapiColors.OnPrimary,
                uncheckedColor = ClapiColors.OnSurfaceDim,
            ),
            modifier = Modifier.size(24.dp).testTag("import-path-toggle-${item.path}"),
        )
        Icon(
            if (item.path.endsWith(".proto", ignoreCase = true)) painterResource(Res.drawable.ic_description) else painterResource(Res.drawable.ic_folder),
            contentDescription = null,
            tint = if (item.enabled) ClapiColors.Tertiary else ClapiColors.OnSurfaceDim,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = item.path,
            fontSize = 12.sp,
            fontFamily = CodeFontFamily,
            color = if (item.enabled) ClapiColors.OnSurface else ClapiColors.OnSurfaceDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isGitSyncEnabled) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .handPointerHover()
                    .clickable { onToggleSyncToGit() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .testTag("import-sync-toggle-${item.path}"),
            ) {
                Checkbox(
                    checked = item.syncToGit,
                    onCheckedChange = { onToggleSyncToGit() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = ClapiColors.Primary,
                        checkmarkColor = ClapiColors.OnPrimary,
                        uncheckedColor = ClapiColors.OnSurfaceDim,
                    ),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = Strings.syncToGit,
                    fontSize = 11.sp,
                    color = if (item.syncToGit) ClapiColors.Primary else ClapiColors.OnSurfaceDim,
                )
            }
        }
        IconButton(
            onClick = onOpen,
            modifier = Modifier.size(24.dp).handPointerHover().testTag("open-import-btn-${item.path}"),
        ) {
            Icon(painter = painterResource(Res.drawable.ic_open_in_new),
                contentDescription = Strings.openFileExternally,
                tint = ClapiColors.OnSurfaceDim,
                modifier = Modifier.size(14.dp),
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(24.dp).handPointerHover().testTag("delete-import-btn-${item.path}"),
        ) {
            Icon(painter = painterResource(Res.drawable.ic_delete),
                contentDescription = Strings.delete,
                tint = ClapiColors.Error,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
