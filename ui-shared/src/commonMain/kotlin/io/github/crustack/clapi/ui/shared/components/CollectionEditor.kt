package io.github.crustack.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.platform.openFileInDefaultApp
import io.github.crustack.clapi.ui.shared.platform.pickProtoFiles
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.ProtoFileItemState
import io.github.crustack.clapi.ui.shared.state.ProtoImportPathState
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import io.github.crustack.clapi.ui.shared.theme.ClapiColors
import io.github.crustack.clapi.ui.shared.theme.CodeFontFamily

enum class CollectionEditorTab(val label: String) {
    PROTOBUF("Protobuf"),
}

/**
 * Dedicated editor for configuring collection-level resources and settings
 * (such as shared Protocol Buffer files and import paths).
 */
@Composable
fun CollectionEditor(
    tab: RequestTabState,
    state: AppState,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(CollectionEditorTab.PROTOBUF) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClapiColors.Background)
            .testTag("collection-editor"),
    ) {
        // Header bar displaying article icon, title, and Save button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(ClapiColors.Surface)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Article,
                contentDescription = null,
                tint = ClapiColors.Tertiary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = tab.name.ifBlank { "Collection" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = ClapiColors.OnSurface,
                modifier = Modifier.weight(1f).testTag("collection-editor-title"),
            )
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClapiColors.Primary,
                    contentColor = ClapiColors.OnPrimary,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(32.dp).testTag("collection-save-button"),
            ) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).padding(end = 4.dp),
                )
                Text(Strings.save, fontSize = 12.sp)
            }
        }

        // Tab bar for Collection Editor
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(ClapiColors.Surface)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (selectedTab == CollectionEditorTab.PROTOBUF) ClapiColors.SurfaceContainer
                        else Color.Transparent
                    )
                    .clickable { selectedTab = CollectionEditorTab.PROTOBUF }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("collection-tab-protobuf"),
            ) {
                Text(
                    text = "Protobuf",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (selectedTab == CollectionEditorTab.PROTOBUF) ClapiColors.OnSurface else ClapiColors.OnSurfaceDim,
                )
            }
        }

        // Tab content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("collection-editor-content"),
        ) {
            when (selectedTab) {
                CollectionEditorTab.PROTOBUF -> {
                    CollectionProtobufSettings(
                        state = state,
                        onSave = onSave,
                    )
                }
            }
        }
    }
}

@Composable
fun CollectionProtobufSettings(
    state: AppState,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
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
                        pickProtoFiles { pickedPaths ->
                            var changed = false
                            for (p in pickedPaths) {
                                if (state.collectionProtoFiles.none { it.path == p }) {
                                    state.collectionProtoFiles.add(ProtoFileItemState(p))
                                    changed = true
                                }
                            }
                            if (changed) onSave()
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
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
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
                    onOpen = { openFileInDefaultApp(protoFile.path) },
                    onDelete = {
                        state.collectionProtoFiles.remove(protoFile)
                        onSave()
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
                        pickProtoFiles { pickedPaths ->
                            var changed = false
                            for (p in pickedPaths) {
                                if (state.collectionImportPaths.none { it.path == p }) {
                                    state.collectionImportPaths.add(ProtoImportPathState(p, enabled = true))
                                    changed = true
                                }
                            }
                            if (changed) onSave()
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
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(Strings.importPaths, fontSize = 12.sp)
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
                    onToggle = {
                        importPath.enabled = !importPath.enabled
                        onSave()
                    },
                    onOpen = { openFileInDefaultApp(importPath.path) },
                    onDelete = {
                        state.collectionImportPaths.remove(importPath)
                        onSave()
                    },
                )
            }
        }
    }
}

@Composable
private fun ProtoFileRow(
    item: ProtoFileItemState,
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
        Icon(
            Icons.Default.Description,
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
        IconButton(
            onClick = onOpen,
            modifier = Modifier.size(24.dp).testTag("open-file-btn-${item.path}"),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = Strings.openFileExternally,
                tint = ClapiColors.OnSurfaceDim,
                modifier = Modifier.size(14.dp),
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(24.dp).testTag("delete-proto-btn-${item.path}"),
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = Strings.delete,
                tint = ClapiColors.Error,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun ProtoImportPathRow(
    item: ProtoImportPathState,
    onToggle: () -> Unit,
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
            if (item.path.endsWith(".proto", ignoreCase = true)) Icons.Default.Description else Icons.Default.Folder,
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
        IconButton(
            onClick = onOpen,
            modifier = Modifier.size(24.dp).testTag("open-import-btn-${item.path}"),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = Strings.openFileExternally,
                tint = ClapiColors.OnSurfaceDim,
                modifier = Modifier.size(14.dp),
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(24.dp).testTag("delete-import-btn-${item.path}"),
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = Strings.delete,
                tint = ClapiColors.Error,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
