package io.github.crustack.clapi.ui.shared.components.sidebar

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
import io.github.crustack.clapi.ui.shared.components.HoverTooltip
import io.github.crustack.clapi.ui.shared.components.IconButtonWithTooltip
import io.github.crustack.clapi.ui.shared.components.SharedTooltipCoordinator
import io.github.crustack.clapi.ui.shared.components.SidebarTooltipOverlay
import io.github.crustack.clapi.ui.shared.components.sharedSidebarTooltipState
import io.github.crustack.clapi.ui.shared.generated.resources.Res
import io.github.crustack.clapi.ui.shared.generated.resources.ic_add
import io.github.crustack.clapi.ui.shared.generated.resources.ic_article
import io.github.crustack.clapi.ui.shared.generated.resources.ic_close
import io.github.crustack.clapi.ui.shared.generated.resources.ic_download
import io.github.crustack.clapi.ui.shared.generated.resources.ic_history
import io.github.crustack.clapi.ui.shared.generated.resources.ic_input
import io.github.crustack.clapi.ui.shared.generated.resources.ic_keyboard_arrow_down
import io.github.crustack.clapi.ui.shared.generated.resources.ic_keyboard_arrow_right
import io.github.crustack.clapi.ui.shared.generated.resources.ic_more_vert
import io.github.crustack.clapi.ui.shared.generated.resources.ic_search
import io.github.crustack.clapi.ui.shared.generated.resources.ic_settings
import io.github.crustack.clapi.ui.shared.generated.resources.ic_upload
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.persistence.ImportExportNaming
import io.github.crustack.clapi.ui.shared.persistence.ImportExportRepository
import io.github.crustack.clapi.ui.shared.persistence.WorkspaceRepository
import io.github.crustack.clapi.ui.shared.platform.ioDispatcher
import io.github.crustack.clapi.ui.shared.platform.pickFileForImport
import io.github.crustack.clapi.ui.shared.platform.saveFileForExport
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.CollectionNode
import io.github.crustack.clapi.ui.shared.state.EnvState
import io.github.crustack.clapi.ui.shared.state.LogLevel
import io.github.crustack.clapi.ui.shared.theme.ClapiColors
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
    // Plain HashMap — never read inside composition, no state notifications needed.
    val hitAreas = remember { HashMap<String, TreeNodeHitArea>() }

    var draggedRequestId by remember { mutableStateOf<String?>(null) }
    var dropTargetCollectionId by remember { mutableStateOf<String?>(null) }
    var dropTargetRequestId by remember { mutableStateOf<String?>(null) }
    // true = insert after the target row; false = insert before
    var dropInsertAfter by remember { mutableStateOf(false) }
    // Cumulative Y-offset from drag start to compute current cursor Y
    var dragOffsetY by remember { mutableStateOf(0f) }
    // Absolute cursor Y in window coords — set on drag start, accumulated on every delta.
    // Used by the auto-scroll loop so it never drifts when the list scrolls under the cursor.
    var dragCursorAbsY by remember { mutableStateOf(0f) }

    // ── Collection-level drag-to-reorder state ─────────────────────────────
    var draggedCollectionId by remember { mutableStateOf<String?>(null) }
    var dragCollectionOffsetY by remember { mutableStateOf(0f) }
    var dropCollectionTargetId by remember { mutableStateOf<String?>(null) }
    var dropCollectionInsertAfter by remember { mutableStateOf(false) }
    // Hit areas for ALL folder nodes (roots + subfolders). parentCollectionId on each
    // entry records the direct parent so we can filter to same-level siblings only.
    val collectionHitAreas = remember { HashMap<String, TreeNodeHitArea>() }
    // parentCollectionId of the folder currently being dragged (null = root level).
    var draggedFolderParentId by remember { mutableStateOf<String?>(null) }
    var draggedFolderParentIdSet by remember { mutableStateOf(false) }

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
    LaunchedEffect(draggedRequestId, draggedCollectionId) {
        if (draggedRequestId == null && draggedCollectionId == null) return@LaunchedEffect
        while (true) {
            val relY = dragCursorAbsY - sidebarTopPx
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
                val activeRequestId   = draggedRequestId
                val activeCollectionId = draggedCollectionId
                val cursorY = dragCursorAbsY
                if (activeRequestId != null) {
                    hitAreas.keys.toList().forEach { id ->
                        hitAreas[id]?.let { hitAreas[id] = it.copy(top = it.top + shift, bottom = it.bottom + shift) }
                    }
                    val movingRequestIds = if (activeRequestId in state.selectedRequestIds) {
                        state.selectedRequestIds.toList()
                    } else {
                        listOf(activeRequestId)
                    }
                    val target = resolveClosestHitArea(
                        areas = hitAreas.values.filter { it.id !in movingRequestIds },
                        cursorY = cursorY,
                    )
                    if (target != null) {
                        if (target.isFolder) {
                            dropTargetCollectionId = target.id
                            dropTargetRequestId    = null
                            dropInsertAfter        = false
                        } else {
                            val midY = (target.top + target.bottom) / 2f
                            dropInsertAfter        = cursorY >= midY
                            dropTargetRequestId    = target.id
                            dropTargetCollectionId = target.parentCollectionId
                        }
                    }
                } else if (activeCollectionId != null) {
                    collectionHitAreas.keys.toList().forEach { id ->
                        collectionHitAreas[id]?.let { collectionHitAreas[id] = it.copy(top = it.top + shift, bottom = it.bottom + shift) }
                    }
                    val movingFolderIds = if (activeCollectionId in state.selectedFolderIds) {
                        state.selectedFolderIds.toList()
                    } else {
                        listOf(activeCollectionId)
                    }
                    val target = resolveClosestHitArea(
                        areas = collectionHitAreas.values.filter {
                            it.id !in movingFolderIds &&
                                it.parentCollectionId == collectionHitAreas[activeCollectionId]?.parentCollectionId &&
                                isValidFolderGroupDropTarget(state.collections, movingFolderIds, it.id)
                        },
                        cursorY = cursorY,
                    )
                    if (target != null) {
                        val midY = (target.top + target.bottom) / 2f
                        dropCollectionInsertAfter = cursorY >= midY
                        dropCollectionTargetId    = target.id
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
        hitAreas.clear()
        collectionHitAreas.clear()
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
            item {
                SectionHeader(
                    icon = painterResource(Res.drawable.ic_history),
                    title = Strings.history,
                    expanded = state.historyExpanded,
                    onToggle = { state.historyExpanded = !state.historyExpanded },
                )
            }
            if (state.historyExpanded) {
                // M-4: Filter history items by the sidebar search query so the search
                // bar works consistently across both Collections and History sections.
                val historyQuery = normalizeSidebarSearchQuery(state.sidebarSearchQuery)
                val visibleHistory = if (historyQuery.isBlank()) {
                    state.historyItems
                } else {
                    state.historyItems.filter { item ->
                        item.name.contains(historyQuery, ignoreCase = true) ||
                            item.url.contains(historyQuery, ignoreCase = true)
                    }
                }
                items(visibleHistory, key = { "hist-${it.requestId}-${it.timestamp}" }) { item ->
                    HistoryRow(state = state, item = item, onClick = {
                        state.openHistoryItem(item)
                    })
                }
                item { SectionSpacer() }
            }

            item {
                SectionHeader(
                    icon = painterResource(Res.drawable.ic_article),
                    title = Strings.collectionsHeader,
                    expanded = state.collectionsExpanded,
                    modifier = Modifier.testTag("sidebar-collections-header"),
                    onToggle = {
                        state.collectionsExpanded = !state.collectionsExpanded
                        state.settings.collectionsExpanded = state.collectionsExpanded
                    },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            var showHeaderMenu by remember { mutableStateOf(false) }
                            IconButtonWithTooltip(
                                onClick = {
                                    val created = state.createNewCollection()
                                    inlineRenamingId = created.id
                                    if (!state.collectionsExpanded) {
                                        state.collectionsExpanded = true
                                        state.settings.collectionsExpanded = true
                                    }
                                    persistWorkspaceAsync()
                                },
                                tooltip = Strings.addCollection,
                                modifier = Modifier.size(24.dp).testTag("sidebar-add-collection-button"),
                            ) {
                                Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = Strings.addCollection, tint = ClapiColors.OnSurfaceDim, modifier = Modifier.size(16.dp))
                            }
                            Box {
                                IconButtonWithTooltip(
                                    onClick = { showHeaderMenu = true },
                                    tooltip = Strings.folderActions,
                                    modifier = Modifier.size(24.dp).testTag("sidebar-collections-menu-button"),
                                ) {
                                    Icon(painter = painterResource(Res.drawable.ic_more_vert), contentDescription = Strings.folderActions, tint = ClapiColors.OnSurfaceDim, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(
                                    expanded = showHeaderMenu,
                                    onDismissRequest = { showHeaderMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(Strings.exportCollections, fontSize = 12.sp) },
                                        leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_download), contentDescription = null, modifier = Modifier.size(14.dp)) },
                                        onClick = {
                                            showHeaderMenu = false
                                            state.showExportCollectionsDialog = true
                                        },
                                        modifier = Modifier.testTag("menu-export-collections"),
                                    )
                                }
                            }
                        }
                    },
                )
            }
            if (state.collectionsExpanded) {
                val query = normalizeSidebarSearchQuery(state.sidebarSearchQuery)
                val visibleCollections = if (query.isBlank()) {
                    state.collections
                } else {
                    state.collections.mapNotNull { filterCollectionNode(it, query) }
                }

                val onDuplicateSelectedFolders: () -> Unit = {
                    val toDuplicate = state.selectedFolderIds.toList()
                    for (id in toDuplicate) {
                        duplicateFolderInCollections(state.collections, id)
                    }
                    state.notifyCollectionsChanged()
                    persistWorkspaceAsync()
                    state.log("Folder(s) duplicated: ${toDuplicate.size}", LogLevel.SUCCESS)
                }

                val onDeleteSelectedFolders: () -> Unit = {
                    val toDelete = state.selectedFolderIds.toList()
                    val action = {
                        val allRequestIdsToClose = mutableListOf<String>()
                        for (id in toDelete) {
                            val deleted = deleteFolderFromCollections(state.collections, id)
                            if (deleted != null) {
                                allRequestIdsToClose.addAll(collectAllRequestIds(deleted))
                            }
                        }
                        state.closeTabsByIds(allRequestIdsToClose)
                        state.selectedFolderIds.clear()
                        state.notifyCollectionsChanged()
                        persistWorkspaceAsync()
                        state.log("Folder(s) deleted: ${toDelete.size}", LogLevel.INFO)
                    }
                    if (state.settings.confirmBeforeDelete) {
                        state.showConfirm(
                            title = "Delete folders?",
                            message = "Delete ${toDelete.size} folder(s)? This cannot be undone.",
                            action = action,
                        )
                    } else {
                        action()
                    }
                }

                val onDuplicateSelectedRequests: () -> Unit = {
                    val toDuplicate = state.selectedRequestIds.toList()
                    for (id in toDuplicate) {
                        duplicateRequestInCollections(state.collections, id)
                    }
                    state.notifyCollectionsChanged()
                    persistWorkspaceAsync()
                    state.log("Request(s) duplicated: ${toDuplicate.size}", LogLevel.SUCCESS)
                }

                val onDeleteSelectedRequests: () -> Unit = {
                    val toDelete = state.selectedRequestIds.toList()
                    val action = {
                        for (id in toDelete) {
                            deleteRequestFromCollections(state.collections, id)
                        }
                        state.closeTabsByIds(toDelete)
                        state.selectedRequestIds.clear()
                        state.notifyCollectionsChanged()
                        persistWorkspaceAsync()
                        state.log("Request(s) deleted: ${toDelete.size}", LogLevel.INFO)
                    }
                    if (state.settings.confirmBeforeDelete) {
                        state.showConfirm(
                            title = "Delete requests?",
                            message = "Delete ${toDelete.size} request(s)? This cannot be undone.",
                            action = action,
                        )
                    } else {
                        action()
                    }
                }

                visibleCollections.forEach { node ->
                    item(key = node.id) {
                        CollectionTreeNode(
                            node = node,
                            depth = 0,
                            parentCollectionId = null,
                            state = state,
                            onExportCollection = { target ->
                                launchTracked("Exporting collection", "Exporting ${target.name}...") {
                                    val jsonStr = ImportExportRepository.exportCollectionToString(target)
                                    saveFileForExport(jsonStr, "${target.name}.json")
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        state.log("Collection exported: ${target.name}.json", LogLevel.SUCCESS)
                                    }
                                }
                            },
                            onDuplicateCollection = { target ->
                                if (target.isCollection) {
                                    val newName = state.disambiguateCollectionName(target.name)
                                    val duplicated = duplicateNode(target, rootNameOverride = newName)
                                    state.collections.add(duplicated)
                                    state.notifyCollectionsChanged()
                                    persistWorkspaceAsync()
                                    state.log("Collection duplicated: $newName", LogLevel.SUCCESS)
                                } else {
                                    val existingNames = state.collections.map { it.name }.toSet()
                                    val newName = ImportExportNaming.generateUniqueCollectionName(target.name, existingNames)
                                    val duplicated = duplicateNode(target, rootNameOverride = newName)
                                    state.collections.add(duplicated)
                                    state.notifyCollectionsChanged()
                                    persistWorkspaceAsync()
                                    state.log("Folder duplicated: $newName", LogLevel.SUCCESS)
                                }
                            },
                            onRenameCollection = { target ->
                                inlineRenamingId = target.id
                            },
                            onDuplicateRequest = { target ->
                                val duplicatedName = duplicateRequestInCollections(state.collections, target.id)
                                if (duplicatedName != null) {
                                    state.notifyCollectionsChanged()
                                    persistWorkspaceAsync()
                                    state.log("Request duplicated: $duplicatedName", LogLevel.SUCCESS)
                                }
                            },
                            onDeleteCollection = { target ->
                                if (target.isCollection) {
                                    val action = {
                                        state.deleteCollection(target.id)
                                        state.selectedFolderIds.remove(target.id)
                                        persistWorkspaceAsync()
                                        state.log("Collection deleted: ${target.name}", LogLevel.INFO)
                                    }
                                    if (state.settings.confirmBeforeDelete) {
                                        state.showConfirm(
                                            title = "Delete collection?",
                                            message = "Delete \"${target.name}\"? This cannot be undone.",
                                            action = action,
                                        )
                                    } else {
                                        action()
                                    }
                                } else {
                                    val requestCount = countRequestsInFolder(target)
                                    val action = {
                                        val deleted = deleteFolderFromCollections(state.collections, target.id)
                                        if (deleted != null) {
                                            // Close all tabs belonging to requests in the deleted folder
                                            val deletedIds = collectAllRequestIds(deleted)
                                            state.closeTabsByIds(deletedIds)
                                            state.selectedFolderIds.remove(target.id)
                                            state.notifyCollectionsChanged()
                                            persistWorkspaceAsync()
                                            state.log("Folder deleted: ${target.name}", LogLevel.INFO)
                                        }
                                    }
                                    if (state.settings.confirmBeforeDelete) {
                                        val message = if (requestCount > 0) {
                                            "Delete \"${target.name}\" and $requestCount request(s)? This cannot be undone."
                                        } else {
                                            "Delete \"${target.name}\"? This cannot be undone."
                                        }
                                        state.showConfirm(
                                            title = "Delete folder?",
                                            message = message,
                                            action = action,
                                        )
                                    } else {
                                        action()
                                    }
                                }
                            },
                            onAddFolder = { target ->
                                addFolderTarget = target
                                addFolderValue = "New Folder"
                            },
                            onAddRequest = { target ->
                                state.addRequestToCollection(target.id)
                                persistWorkspaceAsync()
                            },
                            onAddGrpcRequest = { target ->
                                state.addGrpcRequestToCollection(target.id)
                                persistWorkspaceAsync()
                            },
                            onAddWebSocketRequest = { target ->
                                state.addWebSocketRequestToCollection(target.id)
                                persistWorkspaceAsync()
                            },
                            onAddGraphQLRequest = { target ->
                                state.addGraphQLRequestToCollection(target.id)
                                persistWorkspaceAsync()
                            },
                            onAddMcpConnection = { target ->
                                state.addMcpConnectionToCollection(target.id)
                                persistWorkspaceAsync()
                            },
                            onAddSseRequest = { target ->
                                state.addSseRequestToCollection(target.id)
                                persistWorkspaceAsync()
                            },
                            onRenameRequest = { target ->
                                inlineRenamingId = target.id
                            },
                            onDeleteRequest = { target ->
                                val action = {
                                    deleteRequestFromCollections(state.collections, target.id)
                                    state.closeTabsByIds(listOf(target.id))
                                    state.selectedRequestIds.remove(target.id)
                                    state.notifyCollectionsChanged()
                                    persistWorkspaceAsync()
                                    state.log("Request deleted: ${target.name}", LogLevel.INFO)
                                }
                                if (state.settings.confirmBeforeDelete) {
                                    state.showConfirm(
                                        title = "Delete request?",
                                        message = "Delete \"${target.name}\"? This cannot be undone.",
                                        action = action,
                                    )
                                } else {
                                    action()
                                }
                            },
                            onMoveRequest = { requestId, direction ->
                                if (moveRequestInCollections(state.collections, requestId, direction)) {
                                    state.notifyCollectionsChanged()
                                    persistWorkspaceAsync()
                                }
                            },
                            onDuplicateSelectedFolders = onDuplicateSelectedFolders,
                            onDeleteSelectedFolders = onDeleteSelectedFolders,
                            onDuplicateSelectedRequests = onDuplicateSelectedRequests,
                            onDeleteSelectedRequests = onDeleteSelectedRequests,
                            draggedRequestId = draggedRequestId,
                            dropTargetCollectionId = dropTargetCollectionId,
                            dropTargetRequestId = dropTargetRequestId,
                            dropInsertAfter = dropInsertAfter,
                            onDragStart = { requestId ->
                                draggedRequestId = requestId
                                dragOffsetY = 0f
                                dragCursorAbsY = hitAreas[requestId]?.let { (it.top + it.bottom) / 2f } ?: 0f
                                dropTargetCollectionId = null
                                dropTargetRequestId = null
                                dropInsertAfter = false
                            },
                            onDragDelta = { requestId, deltaY ->
                                if (draggedRequestId == requestId) {
                                    dragOffsetY    += deltaY
                                    dragCursorAbsY += deltaY
                                    // Use absolute cursor position — avoids drift when the list
                                    // has scrolled (baseCenter + offsetY is wrong after a scroll).
                                    val currentY = dragCursorAbsY
                                    val movingRequestIds = if (requestId in state.selectedRequestIds) {
                                        state.selectedRequestIds.toList()
                                    } else {
                                        listOf(requestId)
                                    }
                                    val target = resolveClosestHitArea(
                                        areas = hitAreas.values.filter { it.id !in movingRequestIds },
                                        cursorY = currentY,
                                    )
                                    if (target != null) {
                                        if (target.isFolder) {
                                            dropTargetCollectionId = target.id
                                            dropTargetRequestId    = null
                                            dropInsertAfter        = false
                                        } else {
                                            val midY = (target.top + target.bottom) / 2f
                                            dropInsertAfter        = currentY >= midY
                                            dropTargetRequestId    = target.id
                                            dropTargetCollectionId = target.parentCollectionId
                                        }
                                    }
                                }
                            },
                            onNodePositioned = { area -> hitAreas[area.id] = area },
                            onDragEnd = {
                                dragCursorAbsY = 0f
                                val sourceId = draggedRequestId
                                val movingRequestIds = if (sourceId != null && sourceId in state.selectedRequestIds) {
                                    state.selectedRequestIds.toList()
                                } else if (sourceId != null) {
                                    listOf(sourceId)
                                } else {
                                    emptyList()
                                }
                                val targetRequestId = dropTargetRequestId
                                val targetCollectionId = dropTargetCollectionId
                                if (movingRequestIds.isNotEmpty()) {
                                    val moved = when {
                                        targetRequestId != null && targetRequestId !in movingRequestIds ->
                                            if (dropInsertAfter) {
                                                moveRequestsAfterRequest(state.collections, movingRequestIds, targetRequestId)
                                            } else {
                                                moveRequestsBeforeRequest(state.collections, movingRequestIds, targetRequestId)
                                            }
                                        targetCollectionId != null ->
                                            moveRequestsToCollection(state.collections, movingRequestIds, targetCollectionId)
                                        else -> false
                                    }
                                    if (moved) {
                                        state.notifyCollectionsChanged()
                                        persistWorkspaceAsync()
                                    }
                                }
                                draggedRequestId = null
                                dragOffsetY = 0f
                                dropTargetCollectionId = null
                                dropTargetRequestId = null
                                dropInsertAfter = false
                            },
                            // ── Collection-level drag callbacks ────────────────
                            draggedCollectionId = draggedCollectionId,
                            dropCollectionTargetId = dropCollectionTargetId,
                            dropCollectionInsertAfter = dropCollectionInsertAfter,
                            onCollectionDragStart = { collectionId ->
                                draggedCollectionId = collectionId
                                dragCollectionOffsetY = 0f
                                dragCursorAbsY = collectionHitAreas[collectionId]?.let { (it.top + it.bottom) / 2f } ?: 0f
                                draggedFolderParentId = collectionHitAreas[collectionId]?.parentCollectionId
                                draggedFolderParentIdSet = true
                                dropCollectionTargetId = null
                                dropCollectionInsertAfter = false
                            },
                            onCollectionDragDelta = { collectionId, deltaY ->
                                if (draggedCollectionId == collectionId) {
                                    dragCollectionOffsetY += deltaY
                                    dragCursorAbsY        += deltaY
                                    val currentY = dragCursorAbsY
                                    val parentId = draggedFolderParentId
                                    val movingFolderIds = if (collectionId in state.selectedFolderIds) {
                                        state.selectedFolderIds.toList()
                                    } else {
                                        listOf(collectionId)
                                    }
                                    val target = resolveClosestHitArea(
                                        areas = collectionHitAreas.values.filter {
                                            it.id !in movingFolderIds &&
                                                it.parentCollectionId == parentId &&
                                                isValidFolderGroupDropTarget(state.collections, movingFolderIds, it.id)
                                        },
                                        cursorY = currentY,
                                    )
                                    if (target != null) {
                                        val midY = (target.top + target.bottom) / 2f
                                        dropCollectionInsertAfter = currentY >= midY
                                        dropCollectionTargetId    = target.id
                                    }
                                }
                            },
                            onCollectionHitAreaPositioned = { area -> collectionHitAreas[area.id] = area },
                            onCollectionDragEnd = {
                                dragCursorAbsY = 0f
                                val sourceId = draggedCollectionId
                                val targetId = dropCollectionTargetId
                                val movingFolderIds = if (sourceId != null && sourceId in state.selectedFolderIds) {
                                    state.selectedFolderIds.toList()
                                } else if (sourceId != null) {
                                    listOf(sourceId)
                                } else {
                                    emptyList()
                                }
                                if (movingFolderIds.isNotEmpty() && targetId != null && targetId !in movingFolderIds) {
                                    val moved = if (dropCollectionInsertAfter) {
                                        moveFoldersAfterFolder(state.collections, movingFolderIds, targetId)
                                    } else {
                                        moveFoldersBeforeFolder(state.collections, movingFolderIds, targetId)
                                    }
                                    if (moved) {
                                        state.notifyCollectionsChanged()
                                        persistWorkspaceAsync()
                                    }
                                }
                                draggedCollectionId = null
                                dragCollectionOffsetY = 0f
                                draggedFolderParentId = null
                                draggedFolderParentIdSet = false
                                dropCollectionTargetId = null
                                dropCollectionInsertAfter = false
                            },
                            inlineRenamingId = inlineRenamingId,
                            onStartInlineRename = { inlineRenamingId = it },
                            onCommitInlineRename = onCommitInlineRename,
                            onCancelInlineRename = { inlineRenamingId = null },
                        )
                    }
                }
                item { SectionSpacer() }
            }

            item {
                SectionHeader(
                    icon = painterResource(Res.drawable.ic_settings),
                    title = Strings.environments,
                    expanded = state.environmentsExpanded,
                    onToggle = {
                        state.environmentsExpanded = !state.environmentsExpanded
                        state.settings.environmentsExpanded = state.environmentsExpanded
                    },
                    trailing = {
                        Row {
                            IconButtonWithTooltip(
                                onClick = {
                                    pickFileForImport { content ->
                                        scope.launch {
                                            launchTracked("Importing environment", "Importing environment...") {
                                                val importedName = ImportExportRepository.importEnvironmentFromString(state, content)
                                                withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    WorkspaceRepository.save(state)
                                                    state.log("Environment imported: $importedName", LogLevel.SUCCESS)
                                                }
                                            }
                                        }
                                    }
                                },
                                tooltip = Strings.t("import_environment"),
                                modifier = Modifier.size(24.dp).testTag("environment-import-button"),
                            ) {
                                Icon(painter = painterResource(Res.drawable.ic_input),
                                    contentDescription = Strings.t("import_environment"),
                                    tint = ClapiColors.OnSurfaceDim,
                                    modifier = Modifier.size(14.dp).testTag("environment-import-icon"),
                                )
                            }
                            IconButtonWithTooltip(
                                onClick = {
                                    createEnvironmentValue = ""
                                    showCreateEnvironmentDialog = true
                                },
                                tooltip = Strings.t("new_environment"),
                                modifier = Modifier.size(24.dp).testTag("sidebar-new-environment-button"),
                            ) {
                                Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = Strings.t("new_environment"), tint = ClapiColors.OnSurfaceDim, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                )
            }
            if (state.environmentsExpanded) {
                if (state.environments.isEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 28.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = Strings.noEnvironmentsConfigured,
                                style = MaterialTheme.typography.bodySmall,
                                color = ClapiColors.OnSurfaceDim,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = Strings.createEnvironment,
                                style = MaterialTheme.typography.bodySmall,
                                color = ClapiColors.Primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ClapiColors.Primary.copy(alpha = 0.10f))
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable {
                                        createEnvironmentValue = ""
                                        showCreateEnvironmentDialog = true
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                state.environments.forEachIndexed { index, env ->
                    item(key = "env_$index") {
                        EnvironmentRow(
                            envName = env.name,
                            active = index == state.selectedEnvIndex,
                            onClick = { state.selectedEnvIndex = index },
                            onDoubleClick = {
                                state.selectedEnvIndex = index
                                state.openEnvEdit(index)
                            },
                            onEdit = { state.openEnvEdit(index) },
                            onDuplicate = {
                                val names = state.environments.map { it.name }.toSet()
                                val uniqueName = ImportExportNaming.generateUniqueEnvironmentName(env.name, names)
                                val duplicated = EnvState(
                                    name = uniqueName,
                                    variables = env.variables.map { v ->
                                        io.github.crustack.clapi.ui.shared.state.MutableKeyValue(v.key, v.value, v.enabled, v.secret)
                                    }
                                )
                                state.environments.add(duplicated)
                                state.log("Environment duplicated: $uniqueName", LogLevel.SUCCESS)
                            },
                            onRename = {
                                renameEnvironmentIndex = index
                                renameEnvironmentValue = env.name
                            },
                            onDelete = {
                                val action = {
                                    if (state.environments.isNotEmpty()) {
                                        val deletedName = state.environments[index].name
                                        state.environments.removeAt(index)
                                        state.selectedEnvIndex = if (state.environments.isEmpty()) 0 else state.selectedEnvIndex.coerceIn(0, state.environments.lastIndex)
                                        state.log("Environment deleted: $deletedName", LogLevel.INFO)
                                    }
                                }
                                if (state.settings.confirmBeforeDelete) {
                                    state.showConfirm(
                                        title = "Delete environment?",
                                        message = "Delete \"${env.name}\"? This cannot be undone.",
                                        action = action,
                                    )
                                } else {
                                    action()
                                }
                            },
                        )
                    }
                }
            }
            }

            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(lazyListState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 2.dp),
            )
        }

            SidebarGitStatusIndicator(state = state)
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

@Composable
private fun SearchBar(query: String, onQueryChanged: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ClapiColors.SurfaceContainer)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(Res.drawable.ic_search), contentDescription = null, tint = ClapiColors.OnSurfaceDim, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChanged,
            singleLine = true,
            textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 13.sp),
            cursorBrush = SolidColor(ClapiColors.Primary),
            modifier = Modifier.weight(1f).testTag("sidebar-search-input"),
            decorationBox = { inner ->
                if (query.isEmpty()) Text("${Strings.search}…", color = ClapiColors.OnSurfaceDim, fontSize = 13.sp)
                inner()
            },
        )
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            HoverTooltip(
                tooltip = Strings.t("clear_search"),
            ) {
                Icon(painter = painterResource(Res.drawable.ic_close),
                    contentDescription = Strings.t("clear_search"),
                    tint = ClapiColors.OnSurfaceDim,
                    modifier = Modifier
                        .size(14.dp)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(onClick = { onQueryChanged("") })
                        .testTag("sidebar-search-clear"),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: Painter,
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isHovered) ClapiColors.HoverOverlay else Color.Transparent)
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = { onClick?.invoke() ?: onToggle() })
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (expanded) painterResource(Res.drawable.ic_keyboard_arrow_down) else painterResource(Res.drawable.ic_keyboard_arrow_right),
            contentDescription = null,
            tint = ClapiColors.OnSurfaceDim,
            modifier = Modifier
                .size(16.dp)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                ),
        )
        Spacer(Modifier.width(4.dp))
        Icon(icon, contentDescription = null, tint = ClapiColors.OnSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = ClapiColors.OnSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}


@Composable
private fun SectionSpacer(modifier: Modifier = Modifier) {
    Spacer(modifier.height(8.dp))
}
