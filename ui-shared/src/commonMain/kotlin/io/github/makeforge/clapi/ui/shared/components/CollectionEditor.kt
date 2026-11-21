package io.github.makeforge.clapi.ui.shared.components

import io.github.makeforge.clapi.ui.shared.components.collectioneditor.CollectionProtobufSettings
import io.github.makeforge.clapi.ui.shared.components.collectioneditor.CollectionStorageSettings

import org.jetbrains.compose.resources.painterResource
import io.github.makeforge.clapi.ui.shared.generated.resources.Res

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
import io.github.makeforge.clapi.ui.shared.platform.DocsLinks
import io.github.makeforge.clapi.ui.shared.platform.PlatformLazyVerticalScrollbar
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.core.model.storage.StorageType
import io.github.makeforge.clapi.core.storage.git.GitErrorTranslator
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_add
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_article
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_delete
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_description
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_folder
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_help_outline
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_open_in_new
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_save
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.persistence.TabsRepository
import io.github.makeforge.clapi.ui.shared.persistence.WorkspaceRepository
import io.github.makeforge.clapi.ui.shared.platform.ioDispatcher
import io.github.makeforge.clapi.ui.shared.platform.openFileInDefaultApp
import io.github.makeforge.clapi.ui.shared.platform.pickProtoFiles
import io.github.makeforge.clapi.ui.shared.shortcuts.AppHotkeys
import io.github.makeforge.clapi.ui.shared.shortcuts.hotkeys
import io.github.makeforge.clapi.ui.shared.shortcuts.onSaveHotkey
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.LogLevel
import io.github.makeforge.clapi.ui.shared.state.ProtoFileItemState
import io.github.makeforge.clapi.ui.shared.state.ProtoImportPathState
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

typealias CollectionEditorTab = io.github.makeforge.clapi.ui.shared.state.CollectionEditorTab

/**
 * Dedicated editor for configuring collection-level resources and settings
 * (metadata, request defaults, and Protocol Buffer files).
 */
@Composable
fun CollectionEditor(
    tab: RequestTabState,
    state: AppState,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    onPickProtoFiles: ((onSelected: (List<String>) -> Unit) -> Unit)? = null,
    onPickImportPaths: ((onSelected: (List<String>) -> Unit) -> Unit)? = null,
    onOpenFile: ((String) -> Unit)? = null,
) {
    var selectedTab by remember(tab) { mutableStateOf(tab.selectedCollectionEditorTab) }

    fun markDirty() {
        tab.markDirty()
        tab.recomputeDirty()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClapiColors.Background)
            .onSaveHotkey { onSave() }
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
            Icon(painter = painterResource(Res.drawable.ic_article),
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
            DocsHelpButton(
                url = DocsLinks.collections,
                tooltip = "Collections documentation",
                tag = "collection-docs-btn",
            )
            Button(
                onClick = onSave,
                enabled = tab.isDirty,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClapiColors.Primary,
                    contentColor = ClapiColors.OnPrimary,
                    disabledContainerColor = ClapiColors.SurfaceContainer,
                    disabledContentColor = ClapiColors.OnSurfaceDim,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(32.dp).testTag("collection-save-button"),
            ) {
                Icon(painter = painterResource(Res.drawable.ic_save),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).padding(end = 4.dp),
                )
                Text(Strings.save, fontSize = 12.sp)
            }
        }

        // Tab bar for Collection Editor (Info first on the left)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(ClapiColors.Surface)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CollectionEditorTab.entries.forEach { editorTab ->
                val isSelected = selectedTab == editorTab
                val tag = when (editorTab) {
                    CollectionEditorTab.INFO -> "collection-tab-info"
                    CollectionEditorTab.STORAGE -> "collection-tab-storage"
                    CollectionEditorTab.VARIABLES -> "collection-tab-variables"
                    CollectionEditorTab.HEADERS -> "collection-tab-headers"
                    CollectionEditorTab.AUTH -> "collection-tab-auth"
                    CollectionEditorTab.PRE_REQUEST -> "collection-tab-pre-request"
                    CollectionEditorTab.TESTS -> "collection-tab-tests"
                    CollectionEditorTab.PROTOBUF -> "collection-tab-protobuf"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isSelected) ClapiColors.SurfaceContainer
                            else Color.Transparent
                        )
                        .clickable {
                            selectedTab = editorTab
                            tab.selectedCollectionEditorTab = editorTab
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag(tag),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = editorTab.label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) ClapiColors.OnSurface else ClapiColors.OnSurfaceDim,
                        )
                        val count = when (editorTab) {
                            CollectionEditorTab.VARIABLES -> tab.variables.count { it.enabled && it.key.isNotBlank() }
                            CollectionEditorTab.HEADERS -> tab.headers.count { it.enabled && it.key.isNotBlank() }
                            CollectionEditorTab.PROTOBUF -> tab.collectionProtoFiles.size
                            else -> 0
                        }
                        if (count > 0) {
                            Text(
                                text = count.toString(),
                                fontSize = 11.sp,
                                color = ClapiColors.Primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
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
                CollectionEditorTab.INFO -> {
                    CollectionInfoSettings(
                        tab = tab,
                        onFieldChange = { markDirty() },
                    )
                }
                CollectionEditorTab.STORAGE -> {
                    CollectionStorageSettings(
                        tab = tab,
                        state = state,
                        onFieldChange = { markDirty() },
                    )
                }
                CollectionEditorTab.VARIABLES -> {
                    KeyValueEditor(tab.variables, "variable", state = state, tab = tab) {
                        markDirty()
                    }
                }
                CollectionEditorTab.HEADERS -> {
                    KeyValueEditor(tab.headers, "header", state = state, tab = tab) {
                        markDirty()
                    }
                }
                CollectionEditorTab.AUTH -> {
                    AuthEditor(tab, state) {
                        markDirty()
                    }
                }
                CollectionEditorTab.PRE_REQUEST -> {
                    ScriptEditor(
                        script = tab.preRequestScript,
                        onScriptChanged = { tab.preRequestScript = it; markDirty() },
                        title = "Pre-request Script",
                        showCursorPosition = state.settings.showEditorPositionIndicator,
                        availableVariables = getAvailableVariables(state, tab),
                    )
                }
                CollectionEditorTab.TESTS -> {
                    ScriptEditor(
                        script = tab.testScript,
                        onScriptChanged = { tab.testScript = it; markDirty() },
                        title = "Post-request Script",
                        showCursorPosition = state.settings.showEditorPositionIndicator,
                        availableVariables = getAvailableVariables(state, tab),
                    )
                }
                CollectionEditorTab.PROTOBUF -> {
                    CollectionProtobufSettings(
                        tab = tab,
                        state = state,
                        onSave = onSave,
                        onPickProtoFiles = onPickProtoFiles,
                        onPickImportPaths = onPickImportPaths,
                        onOpenFile = onOpenFile,
                    )
                }
            }
        }
    }
}

@Composable
fun CollectionInfoSettings(
    tab: RequestTabState,
    onFieldChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 12.dp, start = 24.dp, top = 24.dp, bottom = 24.dp)
                .testTag("collection-info-panel"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "Collection Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ClapiColors.OnSurface,
                )
                Text(
                    text = "General metadata and descriptive details for this collection document.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ClapiColors.OnSurfaceDim,
                )
            }

            item {
                OutlinedTextField(
                    value = tab.name,
                    onValueChange = {
                        tab.name = it
                        onFieldChange()
                    },
                    label = { Text(Strings.name) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("collection-info-name"),
                )
            }

            item {
                OutlinedTextField(
                    value = tab.collectionSummary ?: "",
                    onValueChange = {
                        tab.collectionSummary = it
                        onFieldChange()
                    },
                    label = { Text(Strings.summary) },
                    placeholder = { Text("A brief overview or description of what this API collection covers...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("collection-info-summary"),
                )
            }

            item {
                OutlinedTextField(
                    value = tab.collectionVersion ?: "",
                    onValueChange = {
                        tab.collectionVersion = it
                        onFieldChange()
                    },
                    label = { Text(Strings.version) },
                    placeholder = { Text("1.0.0") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("collection-info-version"),
                )
            }
        }
        PlatformLazyVerticalScrollbar(
            listState = listState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            testTag = "collection-info-scrollbar",
        )
    }
}
