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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import io.github.makeforge.clapi.ui.shared.platform.PlatformLazyVerticalScrollbar
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.makeforge.clapi.core.storage.opencollection.ExportFormat
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_article
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_close
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.platform.DocsLinks
import io.github.makeforge.clapi.ui.shared.persistence.ImportExportRepository
import io.github.makeforge.clapi.ui.shared.platform.pickDirectory
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import io.github.makeforge.clapi.ui.shared.state.LogLevel
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import org.jetbrains.compose.resources.painterResource

@Composable
fun ExportCollectionsDialog(
    state: AppState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onPickDirectory: (((directoryPath: String) -> Unit) -> Unit)? = null,
    onExportComplete: ((writtenFiles: List<String>) -> Unit)? = null,
) {
    val selectedIds = remember {
        mutableStateListOf<String>().apply {
            addAll(state.collections.map { it.id })
        }
    }
    var selectedFormat by remember { mutableStateOf(ExportFormat.JSON) }

    val allSelected = state.collections.isNotEmpty() && selectedIds.size == state.collections.size

    fun toggleSelectAll() {
        if (allSelected) {
            selectedIds.clear()
        } else {
            selectedIds.clear()
            selectedIds.addAll(state.collections.map { it.id })
        }
    }

    val dragState = rememberDraggableDialogState()

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.draggableDialogBackdrop(dragState, onDismiss = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = modifier
                    .draggableDialogCard(dragState, cornerRadius = 8.dp)
                    .widthIn(min = 460.dp, max = 560.dp)
                    .testTag("export-collections-dialog"),
            ) {
                DialogTitleBar(
                    title = Strings.exportCollections,
                    icon = painterResource(Res.drawable.ic_article),
                    iconTint = ClapiColors.Tertiary,
                    actions = {
                        DocsHelpButton(
                            url = DocsLinks.exportDocs,
                            tooltip = "Export documentation",
                            tag = "export-docs-btn",
                            size = 24.dp,
                        )
                    },
                    onClose = onDismiss,
                    closeButtonTag = "export-collections-close-button",
                    onDrag = dragState::onDrag,
                    cornerRadius = 8.dp,
                )

                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "Select collections to export (1 file per collection):",
                style = MaterialTheme.typography.bodySmall,
                color = ClapiColors.OnSurfaceDim,
            )

            // Select all row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { toggleSelectAll() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = allSelected,
                    onCheckedChange = { toggleSelectAll() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = ClapiColors.Primary,
                        checkmarkColor = ClapiColors.OnPrimary,
                    ),
                    modifier = Modifier.testTag("export-collections-select-all"),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Select All",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = ClapiColors.OnSurface,
                )
            }

            HorizontalDivider(color = ClapiColors.Border)

            // Collections list
            val listState = rememberLazyListState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.collections, key = { it.id }) { node ->
                        val isChecked = node.id in selectedIds
                        val folderCount = countFolders(node)
                        val requestCount = countRequests(node)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .handPointerHover()
                                .clickable {
                                    if (isChecked) selectedIds.remove(node.id)
                                    else selectedIds.add(node.id)
                                }
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                                .testTag("collection-export-item-${node.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = {
                                    if (it) selectedIds.add(node.id)
                                    else selectedIds.remove(node.id)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = ClapiColors.Primary,
                                    checkmarkColor = ClapiColors.OnPrimary,
                                ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = node.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = ClapiColors.OnSurface,
                                )
                                Text(
                                    text = "$folderCount folder(s), $requestCount request(s)",
                                    fontSize = 11.sp,
                                    color = ClapiColors.OnSurfaceDim,
                                )
                            }
                        }
                    }
                }
                PlatformLazyVerticalScrollbar(
                    listState = listState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    testTag = "export-collections-scrollbar",
                )
            }

            HorizontalDivider(color = ClapiColors.Border)

            // Format choice
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Format:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = ClapiColors.OnSurface,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { selectedFormat = ExportFormat.JSON },
                ) {
                    RadioButton(
                        selected = selectedFormat == ExportFormat.JSON,
                        onClick = { selectedFormat = ExportFormat.JSON },
                        colors = RadioButtonDefaults.colors(selectedColor = ClapiColors.Primary),
                        modifier = Modifier.testTag("export-format-json"),
                    )
                    Text("OpenCollection JSON", fontSize = 12.sp, color = ClapiColors.OnSurface)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { selectedFormat = ExportFormat.YAML },
                ) {
                    RadioButton(
                        selected = selectedFormat == ExportFormat.YAML,
                        onClick = { selectedFormat = ExportFormat.YAML },
                        colors = RadioButtonDefaults.colors(selectedColor = ClapiColors.Primary),
                        modifier = Modifier.testTag("export-format-yaml"),
                    )
                    Text("OpenCollection YAML", fontSize = 12.sp, color = ClapiColors.OnSurface)
                }
            }

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ClapiColors.SurfaceContainer,
                        contentColor = ClapiColors.OnSurface,
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("export-collections-cancel-button"),
                ) {
                    Text(Strings.cancel, fontSize = 12.sp)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val directoryPicker = onPickDirectory ?: { cb -> pickDirectory(cb) }
                        directoryPicker { selectedDir ->
                            val toExport = state.collections.filter { it.id in selectedIds }
                            val written = ImportExportRepository.exportCollectionsToDirectory(
                                collections = toExport,
                                targetDirectory = selectedDir,
                                format = selectedFormat,
                            )
                            state.log("Exported ${written.size} collection(s) to $selectedDir", LogLevel.SUCCESS)
                            onExportComplete?.invoke(written)
                            onDismiss()
                        }
                    },
                    enabled = selectedIds.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ClapiColors.Primary,
                        contentColor = ClapiColors.OnPrimary,
                        disabledContainerColor = ClapiColors.SurfaceContainer,
                        disabledContentColor = ClapiColors.OnSurfaceDim,
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("export-collections-confirm-button"),
                ) {
                    Text("${Strings.export} (${selectedIds.size})", fontSize = 12.sp)
                }
            }
        }
    }
}
}
}

private fun countFolders(node: CollectionNode): Int {
    var count = 0
    for (child in node.children) {
        if (child.isFolder) {
            count += 1 + countFolders(child)
        }
    }
    return count
}

private fun countRequests(node: CollectionNode): Int {
    var count = 0
    for (child in node.children) {
        if (!child.isFolder) {
            count += 1
        } else {
            count += countRequests(child)
        }
    }
    return count
}
