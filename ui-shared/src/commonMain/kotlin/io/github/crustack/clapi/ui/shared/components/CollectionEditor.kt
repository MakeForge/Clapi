package io.github.crustack.clapi.ui.shared.components

import org.jetbrains.compose.resources.painterResource
import io.github.crustack.clapi.ui.shared.generated.resources.Res

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import io.github.crustack.clapi.core.model.storage.StorageType
import io.github.crustack.clapi.core.storage.git.GitErrorTranslator
import io.github.crustack.clapi.ui.shared.generated.resources.ic_add
import io.github.crustack.clapi.ui.shared.generated.resources.ic_article
import io.github.crustack.clapi.ui.shared.generated.resources.ic_delete
import io.github.crustack.clapi.ui.shared.generated.resources.ic_description
import io.github.crustack.clapi.ui.shared.generated.resources.ic_folder
import io.github.crustack.clapi.ui.shared.generated.resources.ic_help_outline
import io.github.crustack.clapi.ui.shared.generated.resources.ic_open_in_new
import io.github.crustack.clapi.ui.shared.generated.resources.ic_save
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.persistence.TabsRepository
import io.github.crustack.clapi.ui.shared.persistence.WorkspaceRepository
import io.github.crustack.clapi.ui.shared.platform.ioDispatcher
import io.github.crustack.clapi.ui.shared.platform.openFileInDefaultApp
import io.github.crustack.clapi.ui.shared.platform.pickProtoFiles
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.LogLevel
import io.github.crustack.clapi.ui.shared.state.ProtoFileItemState
import io.github.crustack.clapi.ui.shared.state.ProtoImportPathState
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import io.github.crustack.clapi.ui.shared.theme.ClapiColors
import io.github.crustack.clapi.ui.shared.theme.CodeFontFamily
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CollectionEditorTab(val label: String) {
    INFO("Info"),
    STORAGE("Storage"),
    VARIABLES("Variables"),
    HEADERS("Headers"),
    AUTH("Auth"),
    PRE_REQUEST("Pre-request"),
    TESTS("Tests"),
    PROTOBUF("Protobuf"),
}

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
    var selectedTab by remember { mutableStateOf(CollectionEditorTab.INFO) }

    fun markDirty() {
        tab.markDirty()
        tab.recomputeDirty()
    }

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
                        .clickable { selectedTab = editorTab }
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
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
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
                label = { Text("Name") },
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
                label = { Text("Summary") },
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
                label = { Text("Version") },
                placeholder = { Text("1.0.0") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("collection-info-version"),
            )
        }
    }
}

@Composable
fun CollectionStorageSettings(
    tab: RequestTabState,
    state: AppState,
    onFieldChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var savedBannerMessage by remember(tab.id) { mutableStateOf<String?>(null) }

    val canSaveGit = tab.storageType == StorageType.LOCAL ||
        (tab.gitRemoteUrl.isNotBlank() && tab.gitBranch.isNotBlank())

    fun handleSaveStorage() {
        tab.storageError = null
        savedBannerMessage = null
        if (tab.storageType == StorageType.GIT) {
            if (tab.gitRemoteUrl.isBlank()) {
                tab.storageError = "Git remoteUrl cannot be blank"
                return
            }
            if (tab.gitBranch.isBlank()) {
                tab.storageError = "Git branch cannot be blank"
                return
            }
        }
        tab.isSavingStorage = true
        scope.launch {
            try {
                if (tab.storageType == StorageType.GIT) {
                    val remoteUrl = tab.gitRemoteUrl.trim()
                    val branch = tab.gitBranch.trim().ifBlank { "main" }
                    withContext(ioDispatcher) {
                        state.gitRepositoryManager.ensureClonedOrCheckedOut(remoteUrl, branch)
                    }
                }
                state.syncTabToCollectionNode(tab)
                val colNode = state.collections.firstOrNull {
                    it.id == tab.id || it.id == tab.collectionId || "collection_${it.id}" == tab.id
                }
                if (tab.storageType == StorageType.GIT && colNode != null) {
                    withContext(ioDispatcher) {
                        WorkspaceRepository.exportCollectionToGitRepository(state, colNode)
                    }
                    state.refreshGitStatusForCollection(colNode)
                }
                tab.markSaved()
                withContext(ioDispatcher) {
                    TabsRepository.save(state)
                    WorkspaceRepository.save(state)
                }
                savedBannerMessage = "Storage settings saved."
                state.log("Storage settings saved for '${tab.name}'", LogLevel.SUCCESS)
            } catch (e: Exception) {
                tab.storageError = GitErrorTranslator.translate(e)
                state.log("Failed to configure Git storage: ${tab.storageError}", LogLevel.ERROR)
            } finally {
                tab.isSavingStorage = false
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("collection-storage-panel"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = Strings.storageType,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = ClapiColors.OnSurface,
            )
            Text(
                text = "Configure how this collection is stored and synchronized.",
                style = MaterialTheme.typography.bodySmall,
                color = ClapiColors.OnSurfaceDim,
            )
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            tab.storageType = StorageType.LOCAL
                            tab.storageError = null
                            savedBannerMessage = null
                            onFieldChange()
                        }
                        .padding(end = 8.dp)
                        .testTag("storage-radio-local"),
                ) {
                    RadioButton(
                        selected = tab.storageType == StorageType.LOCAL,
                        onClick = {
                            tab.storageType = StorageType.LOCAL
                            tab.storageError = null
                            savedBannerMessage = null
                            onFieldChange()
                        },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = ClapiColors.Primary,
                            unselectedColor = ClapiColors.OnSurfaceDim,
                        ),
                    )
                    Text(
                        text = Strings.storageLocal,
                        color = ClapiColors.OnSurface,
                        fontSize = 14.sp,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            tab.storageType = StorageType.GIT
                            tab.storageError = null
                            savedBannerMessage = null
                            onFieldChange()
                        }
                        .padding(end = 8.dp)
                        .testTag("storage-radio-git"),
                ) {
                    RadioButton(
                        selected = tab.storageType == StorageType.GIT,
                        onClick = {
                            tab.storageType = StorageType.GIT
                            tab.storageError = null
                            savedBannerMessage = null
                            onFieldChange()
                        },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = ClapiColors.Primary,
                            unselectedColor = ClapiColors.OnSurfaceDim,
                        ),
                    )
                    Text(
                        text = Strings.storageGit,
                        color = ClapiColors.OnSurface,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        if (tab.storageType == StorageType.LOCAL) {
            item {
                Text(
                    text = Strings.storageLocalDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = ClapiColors.OnSurfaceDim,
                    modifier = Modifier.testTag("storage-local-description"),
                )
            }
        } else {
            item {
                OutlinedTextField(
                    value = tab.gitRemoteUrl,
                    onValueChange = {
                        tab.gitRemoteUrl = it
                        tab.storageError = null
                        savedBannerMessage = null
                        onFieldChange()
                    },
                    label = { Text(Strings.storageGitRemoteUrl) },
                    placeholder = { Text("git@github.com:org/repo.git or https://github.com/org/repo.git") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("storage-git-remote-url"),
                )
            }

            item {
                OutlinedTextField(
                    value = tab.gitBranch,
                    onValueChange = {
                        tab.gitBranch = it
                        tab.storageError = null
                        savedBannerMessage = null
                        onFieldChange()
                    },
                    label = { Text(Strings.storageGitBranch) },
                    placeholder = { Text("main") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("storage-git-branch"),
                )
            }

            item {
                Text(
                    text = Strings.storageGitCredentialsNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = ClapiColors.OnSurfaceDim,
                    modifier = Modifier.testTag("storage-git-credentials-note"),
                )
            }
        }

        if (tab.storageError != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(ClapiColors.Error.copy(alpha = 0.15f))
                        .padding(12.dp)
                        .testTag("storage-error-banner"),
                ) {
                    Text(
                        text = tab.storageError!!,
                        color = ClapiColors.Error,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        if (savedBannerMessage != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(ClapiColors.Primary.copy(alpha = 0.15f))
                        .padding(12.dp)
                        .testTag("storage-saved-banner"),
                ) {
                    Text(
                        text = savedBannerMessage!!,
                        color = ClapiColors.Primary,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { handleSaveStorage() },
                    enabled = !tab.isSavingStorage && canSaveGit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ClapiColors.Primary,
                        contentColor = ClapiColors.OnPrimary,
                        disabledContainerColor = ClapiColors.SurfaceContainer,
                        disabledContentColor = ClapiColors.OnSurfaceDim,
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("storage-save-button"),
                ) {
                    if (tab.isSavingStorage) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = ClapiColors.OnPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(Strings.storageSaveSettings, fontSize = 13.sp)
                }
            }
        }
    }
}

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
}

@Composable
private fun ProtoFileRow(
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
            modifier = Modifier.size(24.dp).testTag("open-file-btn-${item.path}"),
        ) {
            Icon(painter = painterResource(Res.drawable.ic_open_in_new),
                contentDescription = Strings.openFileExternally,
                tint = ClapiColors.OnSurfaceDim,
                modifier = Modifier.size(14.dp),
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(24.dp).testTag("delete-proto-btn-${item.path}"),
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
private fun ProtoImportPathRow(
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
            modifier = Modifier.size(24.dp).testTag("open-import-btn-${item.path}"),
        ) {
            Icon(painter = painterResource(Res.drawable.ic_open_in_new),
                contentDescription = Strings.openFileExternally,
                tint = ClapiColors.OnSurfaceDim,
                modifier = Modifier.size(14.dp),
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(24.dp).testTag("delete-import-btn-${item.path}"),
        ) {
            Icon(painter = painterResource(Res.drawable.ic_delete),
                contentDescription = Strings.delete,
                tint = ClapiColors.Error,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
