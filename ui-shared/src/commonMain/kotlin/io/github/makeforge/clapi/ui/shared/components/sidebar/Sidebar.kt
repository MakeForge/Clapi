package io.github.makeforge.clapi.ui.shared.components.sidebar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.ui.shared.components.HoverTooltip
import io.github.makeforge.clapi.ui.shared.components.IconButtonWithTooltip
import io.github.makeforge.clapi.ui.shared.components.SharedTooltipCoordinator
import io.github.makeforge.clapi.ui.shared.components.SidebarTooltipOverlay
import io.github.makeforge.clapi.ui.shared.components.sharedSidebarTooltipState
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_add
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_article
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_close
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_download
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_history
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_input
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_keyboard_arrow_down
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_keyboard_arrow_right
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_more_vert
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_search
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_settings
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_upload
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.persistence.ImportExportNaming
import io.github.makeforge.clapi.ui.shared.persistence.ImportExportRepository
import io.github.makeforge.clapi.ui.shared.persistence.WorkspaceRepository
import io.github.makeforge.clapi.ui.shared.platform.ioDispatcher
import io.github.makeforge.clapi.ui.shared.platform.pickFileForImport
import io.github.makeforge.clapi.ui.shared.platform.saveFileForExport
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.LogLevel
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Sidebar(
    state: AppState,
    modifier: Modifier = Modifier,
) {
    SharedTooltipCoordinator()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val collectionsRevision = state.collectionsRevision
    val dragState = remember { SidebarDragState() }

    // ── Sidebar LazyColumn state + bounds for edge-scroll ─────────────────
    val lazyListState = rememberLazyListState()
    var sidebarTopPx by remember { mutableStateOf(0f) }
    var sidebarHeightPx by remember { mutableStateOf(0) }
    val edgeZonePx = with(density) { 72.dp.toPx() }

    // Auto-scroll the sidebar list when the drag cursor is near the top/bottom edge.
    // Uses dragCursorAbsY which is always the true pointer position (set on drag-start,
    // accumulated on every pointer delta). After each scroll tick we shift stored hit-area
    // positions by the same amount so the insertion indicator re-evaluates immediately
    // rather than waiting for the next Compose layout pass.
    LaunchedEffect(dragState.draggedRequestId, dragState.draggedCollectionId) {
        if (dragState.draggedRequestId == null && dragState.draggedCollectionId == null) return@LaunchedEffect
        while (true) {
            val relY = dragState.dragCursorAbsY - sidebarTopPx
            val scrollPx = when {
                relY >= 0f && relY < edgeZonePx ->
                    -((edgeZonePx - relY) / edgeZonePx * 18f)
                relY > sidebarHeightPx - edgeZonePx && relY <= sidebarHeightPx ->
                    (relY - (sidebarHeightPx - edgeZonePx)) / edgeZonePx * 18f
                else -> 0f
            }
            if (scrollPx != 0f) {
                lazyListState.scrollBy(scrollPx)
                // Items shifted on screen by -scrollPx. Adjust stored hit areas so
                // the drop-target lookup below uses up-to-date positions (the real
                // onGloballyPositioned update will arrive on the next layout pass).
                val shift = -scrollPx
                val activeRequestId   = dragState.draggedRequestId
                val activeCollectionId = dragState.draggedCollectionId
                val cursorY = dragState.dragCursorAbsY
                if (activeRequestId != null) {
                    dragState.hitAreas.keys.toList().forEach { id ->
                        dragState.hitAreas[id]?.let { dragState.hitAreas[id] = it.copy(top = it.top + shift, bottom = it.bottom + shift) }
                    }
                    val movingRequestIds = if (activeRequestId in state.selectedRequestIds) {
                        state.selectedRequestIds.toList()
                    } else {
                        listOf(activeRequestId)
                    }
                    val target = resolveClosestHitArea(
                        areas = dragState.hitAreas.values.filter { it.id !in movingRequestIds },
                        cursorY = cursorY,
                    )
                    if (target != null) {
                        if (target.isFolder) {
                            dragState.dropTargetCollectionId = target.id
                            dragState.dropTargetRequestId    = null
                            dragState.dropInsertAfter        = false
                        } else {
                            val midY = (target.top + target.bottom) / 2f
                            dragState.dropInsertAfter        = cursorY >= midY
                            dragState.dropTargetRequestId    = target.id
                            dragState.dropTargetCollectionId = target.parentCollectionId
                        }
                    }
                } else if (activeCollectionId != null) {
                    dragState.collectionHitAreas.keys.toList().forEach { id ->
                        dragState.collectionHitAreas[id]?.let { dragState.collectionHitAreas[id] = it.copy(top = it.top + shift, bottom = it.bottom + shift) }
                    }
                    val movingFolderIds = if (activeCollectionId in state.selectedFolderIds) {
                        state.selectedFolderIds.toList()
                    } else {
                        listOf(activeCollectionId)
                    }
                    val target = resolveClosestHitArea(
                        areas = dragState.collectionHitAreas.values.filter {
                            it.id !in movingFolderIds &&
                                it.parentCollectionId == dragState.collectionHitAreas[activeCollectionId]?.parentCollectionId &&
                                isValidFolderGroupDropTarget(state.collections, movingFolderIds, it.id)
                        },
                        cursorY = cursorY,
                    )
                    if (target != null) {
                        val midY = (target.top + target.bottom) / 2f
                        dragState.dropCollectionInsertAfter = cursorY >= midY
                        dragState.dropCollectionTargetId    = target.id
                    }
                }
            }
            delay(16L)
        }
    }

    var renameRequestTarget by remember { mutableStateOf<CollectionNode?>(null) }
    var renameRequestValue by remember { mutableStateOf("") }

    var renameCollectionTarget by remember { mutableStateOf<CollectionNode?>(null) }
    var renameCollectionValue by remember { mutableStateOf("") }
    var inlineRenamingId by remember { mutableStateOf<String?>(null) }
    var addFolderTarget by remember { mutableStateOf<CollectionNode?>(null) }
    var addFolderValue by remember { mutableStateOf("") }

    var renameEnvironmentIndex by remember { mutableStateOf(-1) }
    var renameEnvironmentValue by remember { mutableStateOf("") }
    var showCreateEnvironmentDialog by remember { mutableStateOf(false) }
    var createEnvironmentValue by remember { mutableStateOf("") }

    // Remove stale rows from previous composition states (collapsed/filter changes)
    // so drag target resolution only considers currently rendered hit areas.
    LaunchedEffect(state.collectionsRevision, state.sidebarSearchQuery, state.collectionsExpanded) {
        dragState.clearHitAreas()
    }

    fun launchTracked(
        title: String,
        message: String,
        block: suspend () -> Unit,
    ) {
        val job = scope.launch(ioDispatcher) {
            runCatching { block() }
                .onFailure { e ->
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        state.showError(title = "Import/Export error", message = e.message ?: "Unknown error")
                        state.log("$title failed: ${e.message}", LogLevel.ERROR)
                    }
                }
                .also {
                    withContext(kotlinx.coroutines.Dispatchers.Main) { state.finishOperation() }
                }
        }
        state.startOperation(title = title, message = message, job = job)
    }

    fun persistWorkspaceAsync() {
        scope.launch(ioDispatcher) {
            WorkspaceRepository.save(state)
        }
    }

    val onCommitInlineRename: (CollectionNode, String) -> Unit = { target, newName ->
        val trimmed = newName.trim()
        if (trimmed.isNotEmpty() && trimmed != target.name) {
            val isTopLevelCollection = target.isCollection || state.collections.any { it.id == target.id && it.isCollection }
            if (isTopLevelCollection) {
                state.renameCollection(target.id, trimmed)
                persistWorkspaceAsync()
            } else if (target.isFolder) {
                val oldName = target.name
                val isRootCollection = state.collections.any { it.id == target.id }
                val renamed = renameFolderInCollections(state.collections, target.id, trimmed)
                if (renamed) {
                    val actualNewName = state.findFolder(target.id)?.name ?: trimmed
                    state.openTabs.filter { it.id == target.id && it.isFolderTab }.forEach { tab ->
                        tab.name = actualNewName
                        tab.reanchorSavedSnapshotAfterRename(oldName)
                    }
                    if (isRootCollection) {
                        state.updateTabsCollectionName(oldName, actualNewName)
                    }
                    state.notifyCollectionsChanged()
                    persistWorkspaceAsync()
                }
            } else {
                state.renameRequestEverywhere(target.id, trimmed)
                persistWorkspaceAsync()
            }
        }
        inlineRenamingId = null
    }

    LaunchedEffect(
        state.sidebarScrollToRequestId,
        state.collectionsRevision,
        state.collectionsExpanded,
        state.historyExpanded,
        state.historyRevision,
        state.sidebarSearchQuery,
    ) {
        val targetRequestId = state.sidebarScrollToRequestId ?: return@LaunchedEffect
        if (!state.collectionsExpanded) return@LaunchedEffect

        // Pre-scroll LazyColumn to the root collection item first so offscreen
        // request rows are composed. Row-level bringIntoView will then finish
        // precise scrolling to the request itself.
        val targetRootId = findRootCollectionIdForRequest(state.collections, targetRequestId) ?: return@LaunchedEffect

        val historyQuery = normalizeSidebarSearchQuery(state.sidebarSearchQuery)
        val visibleHistoryCount = if (!state.historyExpanded) {
            0
        } else if (historyQuery.isBlank()) {
            state.historyItems.size
        } else {
            state.historyItems.count { item ->
                item.name.contains(historyQuery, ignoreCase = true) ||
                    item.url.contains(historyQuery, ignoreCase = true)
            }
        }

        val collectionsQuery = normalizeSidebarSearchQuery(state.sidebarSearchQuery)
        val visibleCollections = if (collectionsQuery.isBlank()) {
            state.collections
        } else {
            state.collections.mapNotNull { filterCollectionNode(it, collectionsQuery) }
        }
        val rootVisibleIndex = visibleCollections.indexOfFirst { it.id == targetRootId }
        if (rootVisibleIndex < 0) return@LaunchedEffect

        val lazyColumnIndex = 1 + // History header
            (if (state.historyExpanded) visibleHistoryCount + 1 else 0) + // history rows + spacer
            1 + // Collections header
            rootVisibleIndex

        runCatching { lazyListState.animateScrollToItem(lazyColumnIndex) }
    }

    Box(
        modifier = modifier
            .width(state.sidebarWidth.dp)
            .fillMaxHeight()
            .background(ClapiColors.SurfaceVariant)
            .testTag("sidebar")
            .onGloballyPositioned { coords ->
                val rootPos = coords.positionInRoot()
                sharedSidebarTooltipState.containerRootY = rootPos.y.toInt()
                sidebarTopPx = rootPos.y
                sidebarHeightPx = coords.size.height
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchBar(
                query = state.sidebarSearchQuery,
                onQueryChanged = { state.sidebarSearchQuery = normalizeSidebarSearchQuery(it) },
                modifier = Modifier.padding(8.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 6.dp)
                    .testTag("sidebar-button-bar"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        state.showImportSpecificationDialog = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .testTag("collection-import-button"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ClapiColors.OnSurface,
                    ),
                    border = BorderStroke(1.dp, ClapiColors.Border),
                ) {
                    Icon(painter = painterResource(Res.drawable.ic_download),
                        contentDescription = Strings.t("import"),
                        modifier = Modifier.size(14.dp).testTag("collection-import-icon"),
                        tint = ClapiColors.OnSurfaceDim,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = Strings.t("import"),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    var showExportMenu by remember { mutableStateOf(false) }

                    OutlinedButton(
                        onClick = { showExportMenu = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .testTag("sidebar-export-button")
                            .onPointerEvent(PointerEventType.Press) {
                                if (it.buttons.isSecondaryPressed) showExportMenu = true
                            },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ClapiColors.OnSurface,
                        ),
                        border = BorderStroke(1.dp, ClapiColors.Border),
                    ) {
                        Icon(painter = painterResource(Res.drawable.ic_upload),
                            contentDescription = Strings.t("export"),
                            modifier = Modifier.size(14.dp),
                            tint = ClapiColors.OnSurfaceDim,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = Strings.t("export"),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }

                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false },
                        modifier = Modifier.testTag("sidebar-export-menu"),
                    ) {
                        DropdownMenuItem(
                            text = { Text("OpenCollection YAML Format") },
                            leadingIcon = {
                                Icon(painter = painterResource(Res.drawable.ic_upload), contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            onClick = {
                                showExportMenu = false
                                scope.launch {
                                    launchTracked("Exporting OpenCollection", "Exporting workspace as OpenCollection YAML...") {
                                        val yamlStr = ImportExportRepository.exportWorkspaceToOpenCollectionYaml(state)
                                        withContext(Dispatchers.Main) {
                                            saveFileForExport(yamlStr, "opencollection.yaml")
                                            state.log("Exported OpenCollection YAML", LogLevel.SUCCESS)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.testTag("export-opencollection-yaml-item"),
                        )
                        DropdownMenuItem(
                            text = { Text("OpenAPI YAML") },
                            leadingIcon = {
                                Icon(painter = painterResource(Res.drawable.ic_upload), contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            onClick = {
                                showExportMenu = false
                                scope.launch {
                                    launchTracked("Exporting OpenAPI", "Exporting workspace as OpenAPI YAML...") {
                                        val yamlStr = ImportExportRepository.exportWorkspaceToOpenApiYaml(state)
                                        withContext(Dispatchers.Main) {
                                            saveFileForExport(yamlStr, "openapi.yaml")
                                            state.log("Exported OpenAPI YAML", LogLevel.SUCCESS)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.testTag("export-openapi-yaml-item"),
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 4.dp, end = 8.dp),
                ) {

            sidebarHistorySection(state = state)

            sidebarCollectionsSection(
                state = state,
                scope = scope,
                dragState = dragState,
                persistWorkspaceAsync = ::persistWorkspaceAsync,
                launchTracked = ::launchTracked,
                inlineRenamingId = inlineRenamingId,
                onInlineRenamingIdChange = { inlineRenamingId = it },
                onCommitInlineRename = onCommitInlineRename,
                onAddFolder = { target ->
                    addFolderTarget = target
                    addFolderValue = "New Folder"
                },
            )

                sidebarEnvironmentsSection(
                    state = state,
                    scope = scope,
                    launchTracked = ::launchTracked,
                    onCreateEnvironment = {
                        createEnvironmentValue = ""
                        showCreateEnvironmentDialog = true
                    },
                    onRenameEnvironment = { index, name ->
                        renameEnvironmentIndex = index
                        renameEnvironmentValue = name
                    },
                )
            }

            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(lazyListState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 2.dp),
            )
        }

            SidebarFooter(state = state)
        }
        // Single tooltip overlay — not a Popup, so no hover-exit flicker.
        SidebarTooltipOverlay()
    }

    if (renameCollectionTarget != null) {
        RenameItemDialog(
            title = "Rename folder",
            value = renameCollectionValue,
            onValueChange = { renameCollectionValue = it },
            onDismiss = {
                renameCollectionTarget = null
                renameCollectionValue = ""
            },
            onConfirm = {
                val target = renameCollectionTarget ?: return@RenameItemDialog
                val trimmed = renameCollectionValue.trim()
                if (trimmed.isNotEmpty()) {
                    val oldName = target.name
                    val isRootCollection = state.collections.any { it.id == target.id }
                    val renamed = renameFolderInCollections(state.collections, target.id, trimmed)
                    if (renamed) {
                        val actualNewName = state.findFolder(target.id)?.name ?: trimmed
                        state.openTabs.filter { it.id == target.id && it.isFolderTab }.forEach { tab ->
                            tab.name = actualNewName
                            tab.reanchorSavedSnapshotAfterRename(oldName)
                        }
                        if (isRootCollection) {
                            state.updateTabsCollectionName(oldName, actualNewName)
                        }
                        state.notifyCollectionsChanged()
                        persistWorkspaceAsync()
                    }
                }
                renameCollectionTarget = null
                renameCollectionValue = ""
            },
        )
    }

    if (addFolderTarget != null) {
        RenameItemDialog(
            title = "Add folder",
            value = addFolderValue,
            onValueChange = { addFolderValue = it },
            onDismiss = {
                addFolderTarget = null
                addFolderValue = ""
            },
            onConfirm = {
                val target = addFolderTarget ?: return@RenameItemDialog
                val trimmed = addFolderValue.trim()
                if (trimmed.isNotEmpty()) {
                    val created = addSubfolderInCollections(state.collections, target.id, trimmed)
                    if (created != null) {
                        state.notifyCollectionsChanged()
                        persistWorkspaceAsync()
                        state.log("Folder added: ${created.name}", LogLevel.SUCCESS)
                    }
                }
                addFolderTarget = null
                addFolderValue = ""
            },
        )
    }

    if (renameRequestTarget != null) {
        RenameItemDialog(
            title = "Rename request",
            value = renameRequestValue,
            onValueChange = { renameRequestValue = it },
            onDismiss = {
                renameRequestTarget = null
                renameRequestValue = ""
            },
            onConfirm = {
                val target = renameRequestTarget ?: return@RenameItemDialog
                val trimmed = renameRequestValue.trim()
                if (trimmed.isNotEmpty()) {
                    // Use renameRequestEverywhere so open tabs are also updated (Bug 2 fix)
                    state.renameRequestEverywhere(target.id, trimmed)
                    persistWorkspaceAsync()
                }
                renameRequestTarget = null
                renameRequestValue = ""
            },
        )
    }

    if (renameEnvironmentIndex >= 0) {
        RenameItemDialog(
            title = "Rename environment",
            value = renameEnvironmentValue,
            onValueChange = { renameEnvironmentValue = it },
            onDismiss = {
                renameEnvironmentIndex = -1
                renameEnvironmentValue = ""
            },
            onConfirm = {
                val idx = renameEnvironmentIndex
                if (idx in state.environments.indices) {
                    val trimmed = renameEnvironmentValue.trim()
                    if (trimmed.isNotEmpty()) {
                        val existing = state.environments
                            .filterIndexed { envIndex, _ -> envIndex != idx }
                            .map { it.name }
                            .toSet()
                        state.environments[idx].name = ImportExportNaming.generateUniqueEnvironmentName(trimmed, existing)
                    }
                }
                renameEnvironmentIndex = -1
                renameEnvironmentValue = ""
            },
        )
    }

    if (showCreateEnvironmentDialog) {
        RenameItemDialog(
            title = "Create environment",
            value = createEnvironmentValue,
            onValueChange = { createEnvironmentValue = it },
            confirmText = "Create",
            onDismiss = {
                showCreateEnvironmentDialog = false
                createEnvironmentValue = ""
            },
            onConfirm = {
                val trimmed = createEnvironmentValue.trim()
                if (trimmed.isNotEmpty()) {
                    val existing = state.environments.map { it.name }.toSet()
                    val unique = ImportExportNaming.generateUniqueEnvironmentName(trimmed, existing)
                    state.environments.add(EnvState(unique))
                    state.selectedEnvIndex = state.environments.lastIndex
                    state.log("Environment created: $unique", LogLevel.SUCCESS)
                }
                showCreateEnvironmentDialog = false
                createEnvironmentValue = ""
            },
        )
    }
}

