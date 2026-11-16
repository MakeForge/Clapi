package io.github.crustack.clapi.ui.shared.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import io.github.crustack.clapi.ui.shared.shortcuts.AppHotkeys
import io.github.crustack.clapi.ui.shared.shortcuts.hotkeys
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.core.model.RequestKind
import io.github.crustack.clapi.ui.shared.generated.resources.Res
import io.github.crustack.clapi.ui.shared.generated.resources.ic_add
import io.github.crustack.clapi.ui.shared.generated.resources.ic_article
import io.github.crustack.clapi.ui.shared.generated.resources.ic_bolt
import io.github.crustack.clapi.ui.shared.generated.resources.ic_close
import io.github.crustack.clapi.ui.shared.generated.resources.ic_content_copy
import io.github.crustack.clapi.ui.shared.generated.resources.ic_delete
import io.github.crustack.clapi.ui.shared.generated.resources.ic_download
import io.github.crustack.clapi.ui.shared.generated.resources.ic_drag_indicator
import io.github.crustack.clapi.ui.shared.generated.resources.ic_edit
import io.github.crustack.clapi.ui.shared.generated.resources.ic_folder
import io.github.crustack.clapi.ui.shared.generated.resources.ic_folder_open
import io.github.crustack.clapi.ui.shared.generated.resources.ic_fork_right
import io.github.crustack.clapi.ui.shared.generated.resources.ic_history
import io.github.crustack.clapi.ui.shared.generated.resources.ic_hub
import io.github.crustack.clapi.ui.shared.generated.resources.ic_input
import io.github.crustack.clapi.ui.shared.generated.resources.ic_keyboard_arrow_down
import io.github.crustack.clapi.ui.shared.generated.resources.ic_keyboard_arrow_right
import io.github.crustack.clapi.ui.shared.generated.resources.ic_keyboard_arrow_up
import io.github.crustack.clapi.ui.shared.generated.resources.ic_more_vert
import io.github.crustack.clapi.ui.shared.generated.resources.ic_search
import io.github.crustack.clapi.ui.shared.generated.resources.ic_settings
import io.github.crustack.clapi.ui.shared.generated.resources.ic_unfold_less
import io.github.crustack.clapi.ui.shared.generated.resources.ic_unfold_more
import io.github.crustack.clapi.ui.shared.generated.resources.ic_upload
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.persistence.ImportExportNaming
import io.github.crustack.clapi.ui.shared.persistence.ImportExportRepository
import io.github.crustack.clapi.ui.shared.persistence.WorkspaceRepository
import io.github.crustack.clapi.ui.shared.platform.formatTimestamp
import io.github.crustack.clapi.ui.shared.platform.generateUuid
import io.github.crustack.clapi.ui.shared.platform.ioDispatcher
import io.github.crustack.clapi.ui.shared.platform.isPointerDoubleClick
import io.github.crustack.clapi.ui.shared.platform.pickFileForImport
import io.github.crustack.clapi.ui.shared.platform.saveFileForExport
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.CollectionNode
import io.github.crustack.clapi.ui.shared.state.EnvState
import io.github.crustack.clapi.ui.shared.state.HistoryItem
import io.github.crustack.clapi.ui.shared.state.LogLevel
import io.github.crustack.clapi.ui.shared.state.hasSseAccept
import io.github.crustack.clapi.ui.shared.theme.ClapiColors
import io.github.crustack.clapi.ui.shared.theme.CodeFontFamily
import io.github.crustack.clapi.ui.shared.theme.httpMethodColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

private data class TreeNodeHitArea(
    val id: String,
    val isFolder: Boolean,
    val parentCollectionId: String?,
    val top: Float,
    val bottom: Float,
    val label: String,
)

private fun resolveClosestHitArea(
    areas: Collection<TreeNodeHitArea>,
    cursorY: Float,
): TreeNodeHitArea? {
    // Prefer rows that currently contain the cursor. If none contain it,
    // fall back to nearest row center to keep drag interactions continuous.
    val inside = areas.filter { cursorY >= it.top && cursorY <= it.bottom }
    if (inside.isNotEmpty()) {
        return inside.minByOrNull {
            kotlin.math.abs(cursorY - ((it.top + it.bottom) / 2f))
        }
    }
    return areas.minByOrNull {
        kotlin.math.abs(cursorY - ((it.top + it.bottom) / 2f))
    }
}

private fun normalizeSidebarSearchQuery(raw: String): String {
    // Strip invisible formatting characters so visually empty input cannot
    // keep a stale filter active (e.g., zero-width spaces from paste/IME).
    return raw
        .filterNot { ch ->
            ch == '\u200B' || ch == '\u200C' || ch == '\u200D' || ch == '\uFEFF'
        }
        .trim()
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Sidebar(state: AppState) {
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
        modifier = Modifier
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
                    modifier = Modifier.weight(1f).height(30.dp).testTag("collection-import-button"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ClapiColors.OnSurface,
                    ),
                    border = BorderStroke(1.dp, ClapiColors.Border),
                ) {
                    Icon(painter = painterResource(Res.drawable.ic_download),
                        contentDescription = "Import",
                        modifier = Modifier.size(14.dp).testTag("collection-import-icon"),
                        tint = ClapiColors.OnSurfaceDim,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Import",
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
                            contentDescription = "Export",
                            modifier = Modifier.size(14.dp),
                            tint = ClapiColors.OnSurfaceDim,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Export",
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

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
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
            .clickable(onClick = { onClick?.invoke() ?: onToggle() })
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (expanded) painterResource(Res.drawable.ic_keyboard_arrow_down) else painterResource(Res.drawable.ic_keyboard_arrow_right),
            contentDescription = null,
            tint = ClapiColors.OnSurfaceDim,
            modifier = Modifier.size(16.dp).clickable(
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
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
private fun HistoryRow(state: AppState, item: HistoryItem, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var showMenu by remember { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val tooltipIndentPx = with(density) { 28.dp.roundToPx() }

    LaunchedEffect(isHovered) {
        val tooltipId = "history-${item.requestId}-${item.timestamp}"
        if (isHovered) {
            sharedSidebarTooltipState.onHoverEnter(tooltipId, "${item.name}  ${item.url}")
        } else {
            sharedSidebarTooltipState.onHoverExit(tooltipId)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isHovered) ClapiColors.HoverOverlay else Color.Transparent
            )
            .onGloballyPositioned { coordinates ->
                if (sharedSidebarTooltipState.hoveredItemId == "history-${item.requestId}-${item.timestamp}") {
                    val position = coordinates.positionInRoot()
                    sharedSidebarTooltipState.updateHoverPosition(
                        rowRootY = position.y.toInt(),
                        rowHeightPx = coordinates.size.height,
                        indentXPx = tooltipIndentPx,
                    )
                }
            }
            .hoverable(interactionSource)
            .onPointerEvent(PointerEventType.Press) {
                if (it.buttons.isSecondaryPressed) {
                    showMenu = true
                }
            }
            .clickable(onClick = onClick)
            .testTag("history-row-${item.requestId}")
            .padding(start = 28.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MethodBadge(item.method, compact = true)
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            color = ClapiColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // M-6: Show the request timestamp so users can identify when each call was made.
        Text(
            text = formatTimestamp(item.timestamp),
            style = MaterialTheme.typography.bodySmall,
            color = ClapiColors.OnSurfaceDim,
            fontSize = 10.sp,
        )
        Box {
            IconButtonWithTooltip(
                onClick = { showMenu = true },
                tooltip = Strings.t("history_actions"),
                modifier = Modifier.size(20.dp).testTag("history-actions-${item.requestId}"),
            ) {
                Icon(painter = painterResource(Res.drawable.ic_more_vert),
                    contentDescription = Strings.t("history_actions"),
                    tint = ClapiColors.OnSurfaceDim,
                    modifier = Modifier.size(14.dp),
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(Strings.t("open")) },
                    onClick = {
                        showMenu = false
                        onClick()
                    },
                )
                DropdownMenuItem(
                    text = { Text(Strings.t("open_in_sidebar")) },
                    onClick = {
                        showMenu = false
                        state.goToCollectionFromHistory(item)
                    },
                )
                DropdownMenuItem(
                    text = { Text(Strings.goToFolder) },
                    onClick = {
                        showMenu = false
                        state.goToCollectionFromHistory(item)
                    },
                )
                DropdownMenuItem(
                    text = { Text(Strings.t("remove_from_history")) },
                    onClick = {
                        showMenu = false
                        state.removeHistoryItem(item.requestId)
                    },
                )
            }
        }
    }
}

@Composable
fun MethodBadge(
    method: HttpMethodType,
    compact: Boolean = false,
    sse: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val color = httpMethodColor(method)
    Text(
        text = if (sse) "SSE" else if (compact) method.name.take(3) else method.name,
        color = color,
        fontSize = if (compact) 10.sp else 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 2.dp),
    )
}

@Composable
fun McpMethodBadge(compact: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text = "MCP",
        color = ClapiColors.Primary,
        fontSize = if (compact) 10.sp else 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ClapiColors.Primary.copy(alpha = 0.12f))
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 2.dp),
    )
}

@Composable
fun GrpcBadge(compact: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text = "gRPC",
        color = ClapiColors.MethodPatch,
        fontSize = if (compact) 10.sp else 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ClapiColors.MethodPatch.copy(alpha = 0.12f))
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 2.dp),
    )
}

@Composable
fun WebSocketBadge(compact: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text = "WS",
        color = ClapiColors.MethodWebSocket,
        fontSize = if (compact) 10.sp else 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ClapiColors.MethodWebSocket.copy(alpha = 0.12f))
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 2.dp),
    )
}

@Composable
fun GraphQLBadge(compact: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text = "GQL",
        color = ClapiColors.MethodGraphQl,
        fontSize = if (compact) 10.sp else 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ClapiColors.MethodGraphQl.copy(alpha = 0.12f))
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 2.dp),
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CollectionTreeNode(
    node: CollectionNode,
    depth: Int,
    parentCollectionId: String?,
    state: AppState,
    onExportCollection: (CollectionNode) -> Unit,
    onDuplicateCollection: (CollectionNode) -> Unit,
    onRenameCollection: (CollectionNode) -> Unit,
    onDuplicateRequest: (CollectionNode) -> Unit,
    onDeleteCollection: (CollectionNode) -> Unit,
    onAddFolder: (CollectionNode) -> Unit,
    onAddRequest: (CollectionNode) -> Unit,
    onAddGrpcRequest: (CollectionNode) -> Unit = {},
    onAddWebSocketRequest: (CollectionNode) -> Unit = {},
    onAddGraphQLRequest: (CollectionNode) -> Unit = {},
    onAddMcpConnection: (CollectionNode) -> Unit,
    onAddSseRequest: (CollectionNode) -> Unit,
    onRenameRequest: (CollectionNode) -> Unit,
    onDeleteRequest: (CollectionNode) -> Unit,
    onMoveRequest: (requestId: String, direction: Int) -> Unit,
    onDuplicateSelectedFolders: () -> Unit = {},
    onDeleteSelectedFolders: () -> Unit = {},
    onDuplicateSelectedRequests: () -> Unit = {},
    onDeleteSelectedRequests: () -> Unit = {},
    draggedRequestId: String?,
    dropTargetCollectionId: String?,
    dropTargetRequestId: String?,
    dropInsertAfter: Boolean,
    onDragStart: (String) -> Unit,
    onDragDelta: (String, Float) -> Unit,
    onNodePositioned: (TreeNodeHitArea) -> Unit,
    onDragEnd: () -> Unit,
    // ── Collection-level drag params (Bug 3) ──────────────────────────────
    draggedCollectionId: String? = null,
    dropCollectionTargetId: String? = null,
    dropCollectionInsertAfter: Boolean = false,
    onCollectionDragStart: (String) -> Unit = {},
    onCollectionDragDelta: (String, Float) -> Unit = { _, _ -> },
    onCollectionHitAreaPositioned: (TreeNodeHitArea) -> Unit = {},
    onCollectionDragEnd: () -> Unit = {},
    inlineRenamingId: String? = null,
    onStartInlineRename: (String) -> Unit = {},
    onCommitInlineRename: (CollectionNode, String) -> Unit = { _, _ -> },
    onCancelInlineRename: () -> Unit = {},
) {
    // Read from the session-persistent map rather than a local remember so that
    // Collapse All / Expand All and cross-recomposition state changes take effect.
    val expanded = state.collectionExpandedState[node.id] ?: true
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    // Keep root collection close to the left rail, and increase indentation step
    // for descendants so folder/request rows are clearly offset from parents.
    val indent = (22 + depth * 22).dp
    val isFolderNode = node.isFolder
    val isCollectionRoot = depth == 0 && node.isFolder
    val isRequest = !node.isFolder && (node.kind == RequestKind.GRPC || node.kind == RequestKind.MCP || node.kind == RequestKind.WEBSOCKET || node.kind == RequestKind.GRAPHQL || (node.method != null && node.url != null))
    val isSelectedRequest = isRequest && state.selectedRequestId == node.id
    val isSidebarFolderSelected = isFolderNode && node.id in state.selectedFolderIds
    val isSidebarRequestSelected = isRequest && node.id in state.selectedRequestIds
    val isSidebarSelected = isSidebarFolderSelected || isSidebarRequestSelected
    val hasMultipleFolderSelection = isSidebarFolderSelected && state.selectedFolderIds.size > 1
    val hasMultipleRequestSelection = isSidebarRequestSelected && state.selectedRequestIds.size > 1

    val isDragSource = draggedRequestId == node.id
    val isDragSourceGroup = draggedRequestId != null && (isDragSource || (isRequest && node.id in state.selectedRequestIds && draggedRequestId in state.selectedRequestIds))
    val isCollectionDragSource = isFolderNode && draggedCollectionId == node.id
    val isCollectionDragSourceGroup = draggedCollectionId != null && (isCollectionDragSource || (isFolderNode && node.id in state.selectedFolderIds && draggedCollectionId in state.selectedFolderIds))

    val isDropCollectionTarget = node.isFolder && node.id == dropTargetCollectionId
    val isDropTarget = isRequest && node.id == dropTargetRequestId && !isDragSourceGroup
    val showInsertionAbove = isDropTarget && !dropInsertAfter
    val showInsertionBelow = isDropTarget && dropInsertAfter
    val showCollectionInsertionAbove = isFolderNode && node.id == dropCollectionTargetId && !dropCollectionInsertAfter
    val showCollectionInsertionBelow = isFolderNode && node.id == dropCollectionTargetId && dropCollectionInsertAfter

    var showMenu by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var modifierHeldOnPress by remember { mutableStateOf(false) }

    val handleRowPointerPress: (PointerEvent) -> Unit = { event ->
        if (event.buttons.isSecondaryPressed && (isFolderNode || isRequest)) {
            showMenu = true
        } else if (event.buttons.isPrimaryPressed && (isFolderNode || isRequest)) {
            val isCtrl = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed
            val isShift = event.keyboardModifiers.isShiftPressed
            if (isCtrl || isShift) {
                modifierHeldOnPress = true
                if (isFolderNode) {
                    state.selectSidebarFolder(
                        folderId = node.id,
                        isCtrlPressed = isCtrl,
                        isShiftPressed = isShift,
                        visibleFolders = getVisibleFolderIds(state.collections, state.collectionExpandedState, state.sidebarSearchQuery),
                    )
                } else if (isRequest) {
                    state.selectSidebarRequest(
                        requestId = node.id,
                        isCtrlPressed = isCtrl,
                        isShiftPressed = isShift,
                        visibleRequests = getVisibleRequestIds(state.collections, state.collectionExpandedState, state.sidebarSearchQuery),
                    )
                }
            } else {
                modifierHeldOnPress = false
            }
        }
    }

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val indentPx = with(density) { indent.roundToPx() }

    // Tooltip: communicate hovered item + text to the shared overlay.
    LaunchedEffect(isHovered, isRequest) {
        if (isHovered && isRequest) {
            sharedSidebarTooltipState.onHoverEnter(node.id, node.name)
        } else {
            sharedSidebarTooltipState.onHoverExit(node.id)
        }
    }

    LaunchedEffect(state.sidebarScrollToRequestId, node.id) {
        if (state.sidebarScrollToRequestId == node.id) {
            // A single bringIntoView can run before expanded rows finish layout.
            // Retry across a few frames so target rows in other collections with
            // duplicate names still scroll reliably into view.
            repeat(3) {
                androidx.compose.runtime.withFrameNanos { }
                runCatching { bringIntoViewRequester.bringIntoView() }
            }
            state.sidebarScrollToRequestId = null
        }
    }

    Column {
        // Insertion line ABOVE the row (Postman-style drop indicator for requests)
        if (showInsertionAbove) {
            Box(
                modifier = Modifier
                    .padding(start = indent, end = 4.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(ClapiColors.Primary, RoundedCornerShape(1.dp))
                    .testTag("drop-indicator-above-${node.id}"),
            )
        }
        // Insertion line ABOVE the row for collection drag
        if (showCollectionInsertionAbove) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(ClapiColors.Primary, RoundedCornerShape(1.dp))
                    .testTag("collection-drop-indicator-above-${node.id}"),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (isDragSourceGroup || isCollectionDragSourceGroup) 0.4f else 1f)
                .bringIntoViewRequester(bringIntoViewRequester)
                .background(
                    color = when {
                        isDropCollectionTarget -> ClapiColors.Primary.copy(alpha = 0.14f)
                        isDragSourceGroup || isCollectionDragSourceGroup -> ClapiColors.SurfaceHigh
                        isSidebarSelected -> ClapiColors.HoverOverlay
                        isSelectedRequest -> ClapiColors.SelectedItem
                        isHovered -> ClapiColors.HoverOverlay
                        else -> Color.Transparent
                    },
                    shape = RoundedCornerShape(6.dp),
                )
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInRoot()
                    onNodePositioned(
                        TreeNodeHitArea(
                            id = node.id,
                            isFolder = node.isFolder,
                            parentCollectionId = parentCollectionId,
                            top = position.y,
                            bottom = position.y + coordinates.size.height,
                            label = node.name,
                        )
                    )
                    // Register every folder as a folder-drag target (roots + subfolders).
                    if (isFolderNode) {
                        onCollectionHitAreaPositioned(
                            TreeNodeHitArea(
                                id = node.id,
                                isFolder = true,
                                parentCollectionId = parentCollectionId,
                                top = position.y,
                                bottom = position.y + coordinates.size.height,
                                label = node.name,
                            )
                        )
                    }
                    // Keep tooltip position current (e.g. after scroll).
                    if (sharedSidebarTooltipState.hoveredItemId == node.id) {
                        sharedSidebarTooltipState.updateHoverPosition(
                            rowRootY    = position.y.toInt(),
                            rowHeightPx = coordinates.size.height,
                            indentXPx   = indentPx,
                        )
                    }
                }
                .hoverable(interactionSource)
                .onPointerEvent(PointerEventType.Press) { handleRowPointerPress(it) }
                .pointerInput(node.id, isRequest, isFolderNode) {
                    when {
                        isRequest -> detectDragGestures(
                            onDragStart = { onDragStart(node.id) },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, _ ->
                                onDragDelta(node.id, change.positionChange().y)
                                change.consume()
                            },
                        )
                        isFolderNode -> detectDragGestures(
                            onDragStart = { onCollectionDragStart(node.id) },
                            onDragEnd = { onCollectionDragEnd() },
                            onDragCancel = { onCollectionDragEnd() },
                            onDrag = { change, _ ->
                                onCollectionDragDelta(node.id, change.positionChange().y)
                                change.consume()
                            },
                        )
                    }
                }
                .onPointerEvent(PointerEventType.Press) {
                    if (it.buttons.isPrimaryPressed && isPointerDoubleClick(node.id, it)) {
                        onStartInlineRename(node.id)
                    }
                }
                .clickable {
                    if (modifierHeldOnPress) {
                        modifierHeldOnPress = false
                        return@clickable
                    }
                    if (inlineRenamingId == node.id) return@clickable
                    if (node.isCollection) {
                        state.selectSidebarFolder(
                            folderId = node.id,
                            isCtrlPressed = false,
                            isShiftPressed = false,
                        )
                        state.openCollection(node.id)
                    } else if (node.isFolder) {
                        state.selectSidebarFolder(
                            folderId = node.id,
                            isCtrlPressed = false,
                            isShiftPressed = false,
                        )
                        state.openFolder(node.id)
                    } else if (isRequest) {
                        state.selectSidebarRequest(
                            requestId = node.id,
                            isCtrlPressed = false,
                            isShiftPressed = false,
                        )
                        state.openRequest(requestId = node.id, name = node.name, method = node.method ?: HttpMethodType.POST, url = node.url ?: "")
                    }
                }
                .padding(start = indent, end = 8.dp, top = 5.dp, bottom = 5.dp)
                .testTag("collection-node-${node.id}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (node.isFolder) {
                // Drag indicator for folders (roots + nested folders)
                if (isFolderNode) {
                    Icon(painter = painterResource(Res.drawable.ic_drag_indicator),
                        contentDescription = Strings.t("drag_to_reorder"),
                        tint = ClapiColors.OnSurfaceDim.copy(alpha = 0.4f),
                        modifier = Modifier.size(12.dp).testTag("folder-drag-handle-${node.id}"),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                state.collectionExpandedState[node.id] = !expanded
                                if (isCollectionRoot) state.selectedCollectionId = node.id
                            },
                        )
                        .testTag("folder-toggle-${node.id}"),
                ) {
                    Icon(
                        if (expanded) painterResource(Res.drawable.ic_keyboard_arrow_down) else painterResource(Res.drawable.ic_keyboard_arrow_right),
                        contentDescription = null,
                        tint = ClapiColors.OnSurfaceDim,
                        modifier = Modifier.size(14.dp).testTag("folder-chevron-${node.id}"),
                    )
                    Icon(
                        painter = if (node.isCollection) painterResource(Res.drawable.ic_article)
                            else if (depth == 0) painterResource(Res.drawable.ic_folder_open)
                            else painterResource(Res.drawable.ic_folder),
                        contentDescription = null,
                        tint = ClapiColors.Tertiary,
                        modifier = Modifier
                            .size(14.dp)
                            .testTag(if (node.isCollection) "collection-icon-${node.id}" else if (depth == 0) "collection-root-icon-${node.id}" else "collection-subfolder-icon-${node.id}"),
                    )
                }
            } else if (node.kind == RequestKind.MCP) {
                Icon(painter = painterResource(Res.drawable.ic_drag_indicator),
                    contentDescription = Strings.t("drag_to_reorder"),
                    tint = ClapiColors.OnSurfaceDim.copy(alpha = 0.4f),
                    modifier = Modifier.size(12.dp),
                )
                McpMethodBadge(compact = true, modifier = Modifier.testTag("mcp-badge-${node.id}"))
            } else if (node.kind == RequestKind.GRPC) {
                Icon(painter = painterResource(Res.drawable.ic_drag_indicator),
                    contentDescription = Strings.t("drag_to_reorder"),
                    tint = ClapiColors.OnSurfaceDim.copy(alpha = 0.4f),
                    modifier = Modifier.size(12.dp),
                )
                GrpcBadge(compact = true, modifier = Modifier.testTag("grpc-badge-${node.id}"))
            } else if (node.kind == RequestKind.WEBSOCKET) {
                Icon(painter = painterResource(Res.drawable.ic_drag_indicator),
                    contentDescription = Strings.t("drag_to_reorder"),
                    tint = ClapiColors.OnSurfaceDim.copy(alpha = 0.4f),
                    modifier = Modifier.size(12.dp),
                )
                WebSocketBadge(compact = true, modifier = Modifier.testTag("websocket-badge-${node.id}"))
            } else if (node.kind == RequestKind.GRAPHQL) {
                Icon(painter = painterResource(Res.drawable.ic_drag_indicator),
                    contentDescription = Strings.t("drag_to_reorder"),
                    tint = ClapiColors.OnSurfaceDim.copy(alpha = 0.4f),
                    modifier = Modifier.size(12.dp),
                )
                GraphQLBadge(compact = true, modifier = Modifier.testTag("graphql-badge-${node.id}"))
            } else if (node.method != null) {
                Icon(painter = painterResource(Res.drawable.ic_drag_indicator),
                    contentDescription = Strings.t("drag_to_reorder"),
                    tint = ClapiColors.OnSurfaceDim.copy(alpha = 0.4f),
                    modifier = Modifier.size(12.dp),
                )
                val openTab = state.openTabs.find { it.id == node.id }
                val sse = openTab?.hasSseAccept() ?: node.hasSseAccept()
                MethodBadge(
                    node.method,
                    compact = true,
                    sse = sse,
                    modifier = if (sse) Modifier.testTag("sse-badge-${node.id}") else Modifier,
                )
            }
            if (inlineRenamingId == node.id) {
                var renameText by remember(node.id) {
                    mutableStateOf(TextFieldValue(node.name, TextRange(0, node.name.length)))
                }
                val focusRequester = remember { FocusRequester() }
                var hadFocus by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
                BasicTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = ClapiColors.OnSurface,
                        fontSize = 12.sp,
                        fontFamily = CodeFontFamily,
                    ),
                    cursorBrush = SolidColor(ClapiColors.Primary),
                    modifier = Modifier
                        .weight(1f)
                        .background(ClapiColors.SurfaceContainer, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                hadFocus = true
                            } else if (hadFocus && inlineRenamingId == node.id) {
                                onCommitInlineRename(node, renameText.text)
                            }
                        }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.Enter, Key.NumPadEnter -> {
                                        onCommitInlineRename(node, renameText.text)
                                        true
                                    }
                                    Key.Escape -> {
                                        onCancelInlineRename()
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .testTag(if (node.isFolder) "folder-rename-input-${node.id}" else "request-rename-input-${node.id}"),
                )
            } else {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = ClapiColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .onPointerEvent(PointerEventType.Press) {
                            handleRowPointerPress(it)
                            if (it.buttons.isPrimaryPressed && isPointerDoubleClick(node.id, it)) {
                                onStartInlineRename(node.id)
                            }
                        }
                        .then(
                            if (node.isFolder) {
                                Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            if (modifierHeldOnPress) {
                                                modifierHeldOnPress = false
                                                return@clickable
                                            }
                                            state.selectSidebarFolder(
                                                folderId = node.id,
                                                isCtrlPressed = false,
                                                isShiftPressed = false,
                                            )
                                            state.collectionExpandedState[node.id] = true
                                            if (node.isCollection) {
                                                state.openCollection(node.id)
                                            } else {
                                                state.openFolder(node.id)
                                            }
                                        },
                                    )
                                    .testTag("folder-name-${node.id}")
                            } else {
                                Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            if (modifierHeldOnPress) {
                                                modifierHeldOnPress = false
                                                return@clickable
                                            }
                                            state.selectSidebarRequest(
                                                requestId = node.id,
                                                isCtrlPressed = false,
                                                isShiftPressed = false,
                                            )
                                            state.openRequest(requestId = node.id, name = node.name, method = node.method ?: HttpMethodType.POST, url = node.url ?: "")
                                        },
                                    )
                                    .testTag("request-name-${node.id}")
                            }
                        ),
                )
            }
            if (isSelectedRequest) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(ClapiColors.Primary)
                        .testTag("selected-request-indicator-${node.id}"),
                )
            }

            if (isFolderNode || isRequest) {
            val actionsInteraction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier.clickable(
                    interactionSource = actionsInteraction,
                    indication = null,
                    onClick = { /* consume so the folder row does not toggle */ },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            if (isFolderNode) {
                Box {
                    IconButtonWithTooltip(
                        onClick = { showAddMenu = true },
                        tooltip = Strings.t("add_request"),
                        modifier = Modifier.size(20.dp).testTag("collection-add-${node.id}"),
                    ) {
                        Icon(painter = painterResource(Res.drawable.ic_add),
                            contentDescription = Strings.t("add_request"),
                            tint = ClapiColors.OnSurfaceDim,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = showAddMenu,
                        onDismissRequest = { showAddMenu = false },
                        modifier = Modifier.testTag("folder-add-menu-${node.id}"),
                    ) {
                        DropdownMenuItem(
                            text = { Text(Strings.t("add_folder")) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showAddMenu = false
                                onAddFolder(node)
                            },
                            modifier = Modifier.testTag("folder-menu-new-folder"),
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.t("new_http_request")) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showAddMenu = false
                                onAddRequest(node)
                            },
                            modifier = Modifier.testTag("folder-menu-new-http"),
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.t("new_grpc_request")) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showAddMenu = false
                                onAddGrpcRequest(node)
                            },
                            modifier = Modifier.testTag("folder-menu-new-grpc"),
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.t("new_websocket_request")) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showAddMenu = false
                                onAddWebSocketRequest(node)
                            },
                            modifier = Modifier.testTag("folder-menu-new-websocket"),
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.t("new_graphql_request")) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showAddMenu = false
                                onAddGraphQLRequest(node)
                            },
                            modifier = Modifier.testTag("folder-menu-new-graphql"),
                        )
                    }
                }
            }
            val actionsTooltip = if (isCollectionRoot) Strings.folderActions else Strings.t("request_actions")
            Box {
                IconButtonWithTooltip(
                    onClick = { showMenu = true },
                    tooltip = actionsTooltip,
                    modifier = Modifier.size(20.dp).testTag("collection-actions-${node.id}"),
                ) {
                    Icon(painter = painterResource(Res.drawable.ic_more_vert),
                        contentDescription = actionsTooltip,
                        tint = ClapiColors.OnSurfaceDim,
                        modifier = Modifier.size(14.dp),
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    scrollState = rememberScrollState(),
                ) {
                    if (hasMultipleFolderSelection) {
                        DropdownMenuItem(
                            text = { Text(Strings.duplicateFolder) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_content_copy), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                onDuplicateSelectedFolders()
                            },
                            modifier = Modifier.testTag("collection-menu-multi-duplicate"),
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.delete) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                onDeleteSelectedFolders()
                            },
                            modifier = Modifier.testTag("collection-menu-multi-delete"),
                        )
                    } else if (hasMultipleRequestSelection) {
                        DropdownMenuItem(
                            text = { Text(Strings.t("duplicate_request")) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_content_copy), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                onDuplicateSelectedRequests()
                            },
                            modifier = Modifier.testTag("collection-menu-multi-duplicate"),
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.t("delete_request")) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                onDeleteSelectedRequests()
                            },
                            modifier = Modifier.testTag("collection-menu-multi-delete"),
                        )
                    } else if (isFolderNode) {
                        DropdownMenuItem(
                            text = { Text(Strings.t("add_folder")) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = { showMenu = false; onAddFolder(node) },
                            modifier = Modifier.testTag("collection-menu-add-folder"),
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.t("expand")) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_unfold_more), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                state.collectionExpandedState[node.id] = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.t("collapse")) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_unfold_less), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                state.collectionExpandedState[node.id] = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.expandAll) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_folder_open), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                state.expandAllCollections()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.collapseAll) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                state.collapseAllCollections()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.t("add_request")) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = { showMenu = false; onAddRequest(node) },
                            modifier = Modifier.testTag("collection-menu-add-request"),
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.t("new_grpc_request")) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = { showMenu = false; onAddGrpcRequest(node) },
                            modifier = Modifier.testTag("collection-menu-new-grpc"),
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.t("new_websocket_request")) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = { showMenu = false; onAddWebSocketRequest(node) },
                            modifier = Modifier.testTag("collection-menu-new-websocket"),
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.t("new_graphql_request")) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = { showMenu = false; onAddGraphQLRequest(node) },
                            modifier = Modifier.testTag("collection-menu-new-graphql"),
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.t("new_mcp_connection")) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_hub), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                onAddMcpConnection(node)
                            },
                            modifier = Modifier.testTag("collection-menu-new-mcp"),
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.t("new_sse_request")) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_bolt), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                onAddSseRequest(node)
                            },
                            modifier = Modifier.testTag("collection-menu-new-sse"),
                        )
                        if (isCollectionRoot) {
                            DropdownMenuItem(
                                text = { Text(Strings.exportFolder) },
                                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_input), contentDescription = null, modifier = Modifier.size(16.dp)) },
                                onClick = { showMenu = false; onExportCollection(node) },
                            )
                            DropdownMenuItem(
                                text = { Text(Strings.duplicateFolder) },
                                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_content_copy), contentDescription = null, modifier = Modifier.size(16.dp)) },
                                onClick = { showMenu = false; onDuplicateCollection(node) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(Strings.t("rename")) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = { showMenu = false; onRenameCollection(node) },
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.delete) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = { showMenu = false; onDeleteCollection(node) },
                        )
                    } else if (isRequest) {
                        DropdownMenuItem(
                            text = { Text(Strings.t("duplicate_request")) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_content_copy), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = { showMenu = false; onDuplicateRequest(node) },
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.t("rename_request")) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = { showMenu = false; onRenameRequest(node) },
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.t("move_up")) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_keyboard_arrow_up), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = { showMenu = false; onMoveRequest(node.id, -1) },
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.t("move_down")) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_keyboard_arrow_down), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = { showMenu = false; onMoveRequest(node.id, 1) },
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.t("delete_request")) },
                            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = { showMenu = false; onDeleteRequest(node) },
                        )
                    }
                }
            }
            }
        }
        } // end Row

        // Insertion line BELOW the row (Postman-style drop indicator for requests)
        if (showInsertionBelow) {
            Box(
                modifier = Modifier
                    .padding(start = indent, end = 4.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(ClapiColors.Primary, RoundedCornerShape(1.dp))
                    .testTag("drop-indicator-below-${node.id}"),
            )
        }
        // Insertion line BELOW the row for collection drag
        if (showCollectionInsertionBelow) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(ClapiColors.Primary, RoundedCornerShape(1.dp))
                    .testTag("collection-drop-indicator-below-${node.id}"),
            )
        }

        AnimatedVisibility(
            visible = node.isFolder && expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                node.children.forEach { child ->
                    CollectionTreeNode(
                        node = child,
                        depth = depth + 1,
                        parentCollectionId = if (node.isFolder) node.id else parentCollectionId,
                        state = state,
                        onExportCollection = onExportCollection,
                        onDuplicateCollection = onDuplicateCollection,
                        onRenameCollection = onRenameCollection,
                        onDuplicateRequest = onDuplicateRequest,
                        onDeleteCollection = onDeleteCollection,
                        onAddRequest = onAddRequest,
                        onAddGrpcRequest = onAddGrpcRequest,
                        onAddWebSocketRequest = onAddWebSocketRequest,
                        onAddGraphQLRequest = onAddGraphQLRequest,
                        onAddMcpConnection = onAddMcpConnection,
                        onAddSseRequest = onAddSseRequest,
                        onRenameRequest = onRenameRequest,
                        onDeleteRequest = onDeleteRequest,
                        onAddFolder = onAddFolder,
                        onMoveRequest = onMoveRequest,
                        onDuplicateSelectedFolders = onDuplicateSelectedFolders,
                        onDeleteSelectedFolders = onDeleteSelectedFolders,
                        onDuplicateSelectedRequests = onDuplicateSelectedRequests,
                        onDeleteSelectedRequests = onDeleteSelectedRequests,
                        draggedRequestId = draggedRequestId,
                        dropTargetCollectionId = dropTargetCollectionId,
                        dropTargetRequestId = dropTargetRequestId,
                        dropInsertAfter = dropInsertAfter,
                        onDragStart = onDragStart,
                        onDragDelta = onDragDelta,
                        onNodePositioned = onNodePositioned,
                        onDragEnd = onDragEnd,
                        // Forward collection drag params so nested folders participate in
                        // same-level reorder and register their hit areas correctly.
                        draggedCollectionId = draggedCollectionId,
                        dropCollectionTargetId = dropCollectionTargetId,
                        dropCollectionInsertAfter = dropCollectionInsertAfter,
                        onCollectionDragStart = onCollectionDragStart,
                        onCollectionDragDelta = onCollectionDragDelta,
                        onCollectionHitAreaPositioned = onCollectionHitAreaPositioned,
                        onCollectionDragEnd = onCollectionDragEnd,
                        inlineRenamingId = inlineRenamingId,
                        onStartInlineRename = onStartInlineRename,
                        onCommitInlineRename = onCommitInlineRename,
                        onCancelInlineRename = onCancelInlineRename,
                    )
                }
            }
        }
    } // end outer Column
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EnvironmentRow(
    envName: String,
    active: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var showMenu by remember { mutableStateOf(false) }

    var envRowRootY  by remember { mutableStateOf(0) }
    var envRowHeight by remember { mutableStateOf(0) }
    val envDensity   = androidx.compose.ui.platform.LocalDensity.current
    val envIndentPx  = with(envDensity) { 28.dp.roundToPx() }

    LaunchedEffect(isHovered) {
        if (isHovered) {
            sharedSidebarTooltipState.onHoverEnter("env-$envName", envName)
            sharedSidebarTooltipState.updateHoverPosition(
                rowRootY    = envRowRootY,
                rowHeightPx = envRowHeight,
                indentXPx   = envIndentPx,
            )
        } else {
            sharedSidebarTooltipState.onHoverExit("env-$envName")
        }
    }

    val rowBackground by animateColorAsState(
        targetValue = when {
            active -> ClapiColors.SelectedItem
            isHovered -> ClapiColors.HoverOverlay
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 120),
        label = "envRowBg",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(rowBackground)
            .hoverable(interactionSource)
            .onPointerEvent(PointerEventType.Press) { if (it.buttons.isSecondaryPressed) showMenu = true }
            .combinedClickable(onClick = onClick, onDoubleClick = onDoubleClick)
            .padding(start = 28.dp, end = 4.dp, top = 5.dp, bottom = 5.dp)
            .testTag("env-row-$envName")
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                envRowRootY  = pos.y.toInt()
                envRowHeight = coords.size.height
                if (sharedSidebarTooltipState.hoveredItemId == "env-$envName") {
                    sharedSidebarTooltipState.updateHoverPosition(
                        rowRootY    = pos.y.toInt(),
                        rowHeightPx = coords.size.height,
                        indentXPx   = envIndentPx,
                    )
                }
            },

        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(if (active) ClapiColors.Secondary else ClapiColors.OnSurfaceDim),
        )
        Text(
            text = envName,
            style = MaterialTheme.typography.bodySmall,
            color = if (active) ClapiColors.OnSurface else ClapiColors.OnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag("env-name-text-$envName"),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButtonWithTooltip(
                onClick = onEdit,
                tooltip = Strings.t("edit_environment"),
                modifier = Modifier
                    .size(24.dp)
                    .alpha(if (isHovered || active || showMenu) 1f else 0f)
                    .testTag("env-edit-$envName"),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_edit),
                    contentDescription = Strings.t("edit_environment"),
                    tint = ClapiColors.OnSurfaceDim,
                    modifier = Modifier.size(14.dp),
                )
            }
            Box(modifier = Modifier.width(24.dp)) {
                IconButtonWithTooltip(
                    onClick = { showMenu = true },
                    tooltip = Strings.t("environment_actions"),
                    modifier = Modifier
                        .size(24.dp)
                        .alpha(if (isHovered || active || showMenu) 1f else 0f)
                        .testTag("env-actions-$envName"),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_more_vert),
                        contentDescription = Strings.t("environment_actions"),
                        tint = ClapiColors.OnSurfaceDim,
                        modifier = Modifier.size(14.dp),
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(Strings.t("duplicate_environment")) },
                        leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_content_copy), contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showMenu = false
                            onDuplicate()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(Strings.t("rename_environment")) },
                        leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showMenu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(Strings.t("delete_environment")) },
                        leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameItemDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    confirmText: String = "Save",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 560.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ClapiColors.Surface)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(12.dp))
                .hotkeys {
                    on(AppHotkeys.Save) { onConfirm() }
                    on(AppHotkeys.Escape) { onDismiss() }
                }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, color = ClapiColors.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
                cursorBrush = SolidColor(ClapiColors.Primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(ClapiColors.SurfaceContainer)
                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("rename-dialog-input"),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                Text(
                    text = Strings.cancel,
                    color = ClapiColors.OnSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ClapiColors.SurfaceContainer)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("rename-dialog-cancel"),
                )
                Text(
                    text = confirmText,
                    color = ClapiColors.OnPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ClapiColors.Primary)
                        .clickable(onClick = onConfirm)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("rename-dialog-save"),
                )
            }
        }
    }
}

private fun duplicateNode(node: CollectionNode, rootNameOverride: String? = null): CollectionNode {
    // Use copy() so every field (including future additions) is guaranteed to be duplicated.
    // Only the id and name are different; children are recursively duplicated with new ids.
    val duplicatedChildren = node.children.map { duplicateNode(it) }
    return node.copy(
        id = generateUuid(),
        requestRef = if (node.isFolder) null else generateUuid(),
        name = rootNameOverride ?: node.name,
        children = androidx.compose.runtime.mutableStateListOf<CollectionNode>().also { it.addAll(duplicatedChildren) },
    )
}

private fun findRootCollectionIdForRequest(
    collections: List<CollectionNode>,
    requestId: String,
): String? {
    collections.forEach { root ->
        if (!root.isFolder) return@forEach
        if (containsRequestId(root, requestId)) return root.id
    }
    return null
}

private fun containsRequestId(node: CollectionNode, requestId: String): Boolean {
    if (!node.isFolder) return node.id == requestId
    node.children.forEach { child ->
        if (containsRequestId(child, requestId)) return true
    }
    return false
}

fun filterCollectionNode(node: CollectionNode, query: String): CollectionNode? {
    val loweredQuery = query.trim().lowercase()
    if (loweredQuery.isEmpty()) return node

    val selfMatches = node.name.lowercase().contains(loweredQuery) ||
        (node.url?.lowercase()?.contains(loweredQuery) == true)

    // If the folder/collection name itself matches, show it with ALL of its children
    // so the user can see every request inside the matched container.
    if (selfMatches && node.isFolder) return node

    val filteredChildren = node.children.mapNotNull { filterCollectionNode(it, loweredQuery) }.toMutableList()
    return if (selfMatches || filteredChildren.isNotEmpty()) {
        node.copy(children = filteredChildren)
    } else {
        null
    }
}

fun getVisibleSidebarNodes(
    collections: List<CollectionNode>,
    expandedState: Map<String, Boolean>,
    query: String = "",
): List<CollectionNode> {
    val q = normalizeSidebarSearchQuery(query)
    val rootNodes = if (q.isBlank()) collections else collections.mapNotNull { filterCollectionNode(it, q) }
    val result = mutableListOf<CollectionNode>()
    fun traverse(nodes: List<CollectionNode>) {
        for (node in nodes) {
            result.add(node)
            if (node.isFolder && (expandedState[node.id] ?: true)) {
                traverse(node.children)
            }
        }
    }
    traverse(rootNodes)
    return result
}

fun getVisibleFolderIds(
    collections: List<CollectionNode>,
    expandedState: Map<String, Boolean>,
    query: String = "",
): List<String> = getVisibleSidebarNodes(collections, expandedState, query).filter { it.isFolder }.map { it.id }

fun getVisibleRequestIds(
    collections: List<CollectionNode>,
    expandedState: Map<String, Boolean>,
    query: String = "",
): List<String> = getVisibleSidebarNodes(collections, expandedState, query).filter { !it.isFolder }.map { it.id }

fun duplicateRequestInCollections(collections: MutableList<CollectionNode>, requestId: String): String? {
    return duplicateRequestInNodeList(collections, requestId)
}

private fun duplicateRequestInNodeList(nodes: MutableList<CollectionNode>, requestId: String): String? {
    nodes.indices.forEach { index ->
        val node = nodes[index]
        if (!node.isFolder && node.id == requestId) {
            val siblingNames = nodes.map { it.name }.toSet()
            val duplicateName = generateUniqueNodeName("${node.name} Copy", siblingNames)
            val duplicated = duplicateNode(node, rootNameOverride = duplicateName)
            nodes.add(index + 1, duplicated)
            return duplicateName
        }
        if (node.children.isNotEmpty()) {
            val duplicatedName = duplicateRequestInNodeList(node.children, requestId)
            if (duplicatedName != null) return duplicatedName
        }
    }
    return null
}

private fun generateUniqueNodeName(base: String, existingNames: Set<String>): String {
    if (base !in existingNames) return base
    var index = 2
    while ("$base $index" in existingNames) {
        index++
    }
    return "$base $index"
}



@Composable
private fun SectionSpacer() {
    Spacer(Modifier.height(8.dp))
}

fun deleteRequestFromCollections(collections: MutableList<CollectionNode>, requestId: String): Boolean {
    val iterator = collections.iterator()
    while (iterator.hasNext()) {
        val node = iterator.next()
        if (!node.isFolder && node.id == requestId) {
            iterator.remove()
            return true
        }
        if (node.children.isNotEmpty()) {
            if (deleteRequestFromCollections(node.children, requestId)) return true
        }
    }
    return false
}

fun renameRequestInCollections(collections: MutableList<CollectionNode>, requestId: String, newName: String) {
    collections.forEachIndexed { index, node ->
        if (!node.isFolder && node.id == requestId) {
            collections[index] = node.copy(name = newName)
            return
        }
        if (node.children.isNotEmpty()) {
            renameRequestInCollections(node.children, requestId, newName)
        }
    }
}

fun addSubfolderInCollections(
    collections: MutableList<CollectionNode>,
    parentFolderId: String,
    folderName: String,
): CollectionNode? {
    val parent = findCollectionById(collections, parentFolderId) ?: return null
    if (!parent.isFolder) return null
    val siblingNames = parent.children.filter { it.isFolder }.map { it.name }.toSet()
    val uniqueName = ImportExportNaming.generateUniqueCollectionName(folderName, siblingNames)
    val node = CollectionNode(
        id = generateUuid(),
        name = uniqueName,
        isFolder = true,
        children = androidx.compose.runtime.mutableStateListOf(),
    )
    parent.children.add(node)
    return node
}

fun renameFolderInCollections(
    collections: MutableList<CollectionNode>,
    folderId: String,
    newName: String,
): Boolean {
    return renameFolderInNodeList(collections, folderId, newName)
}

private fun renameFolderInNodeList(
    nodes: MutableList<CollectionNode>,
    folderId: String,
    newName: String,
): Boolean {
    val index = nodes.indexOfFirst { it.isFolder && it.id == folderId }
    if (index >= 0) {
        val siblings = nodes.filterIndexed { i, n -> i != index && n.isFolder }.map { it.name }.toSet()
        val uniqueName = ImportExportNaming.generateUniqueCollectionName(newName, siblings)
        nodes[index] = nodes[index].copy(name = uniqueName)
        return true
    }
    nodes.forEach { node ->
        if (node.isFolder && node.children.isNotEmpty()) {
            if (renameFolderInNodeList(node.children, folderId, newName)) return true
        }
    }
    return false
}

fun duplicateFolderInCollections(collections: MutableList<CollectionNode>, folderId: String): String? {
    return duplicateFolderInNodeList(collections, folderId)
}

private fun duplicateFolderInNodeList(nodes: MutableList<CollectionNode>, folderId: String): String? {
    nodes.indices.forEach { index ->
        val node = nodes[index]
        if (node.isFolder && node.id == folderId) {
            val siblingNames = nodes.filter { it.isFolder }.map { it.name }.toSet()
            val duplicateName = ImportExportNaming.generateUniqueCollectionName(node.name, siblingNames)
            val duplicated = duplicateNode(node, rootNameOverride = duplicateName)
            nodes.add(index + 1, duplicated)
            return duplicateName
        }
        if (node.children.isNotEmpty()) {
            val duplicatedName = duplicateFolderInNodeList(node.children, folderId)
            if (duplicatedName != null) return duplicatedName
        }
    }
    return null
}

fun deleteFolderFromCollections(
    collections: MutableList<CollectionNode>,
    folderId: String,
): CollectionNode? = deleteFolderFromNodeList(collections, folderId)

/** Recursively collects all node IDs (both folder and request) from a [CollectionNode] subtree. */
fun collectAllRequestIds(node: CollectionNode): List<String> {
    val result = mutableListOf(node.id)
    node.children.forEach { result.addAll(collectAllRequestIds(it)) }
    return result
}

private fun deleteFolderFromNodeList(
    nodes: MutableList<CollectionNode>,
    folderId: String,
): CollectionNode? {
    val index = nodes.indexOfFirst { it.isFolder && it.id == folderId }
    if (index >= 0) return nodes.removeAt(index)
    nodes.forEach { node ->
        if (node.isFolder && node.children.isNotEmpty()) {
            val deleted = deleteFolderFromNodeList(node.children, folderId)
            if (deleted != null) return deleted
        }
    }
    return null
}

fun countRequestsInFolder(folder: CollectionNode): Int {
    if (!folder.isFolder) return 0
    return folder.children.sumOf { child ->
        when {
            child.isFolder -> countRequestsInFolder(child)
            else -> 1
        }
    }
}

/** Move a request up (direction = -1) or down (direction = 1) within its parent list. */
fun moveRequestInCollections(collections: MutableList<CollectionNode>, requestId: String, direction: Int): Boolean {
    val index = collections.indexOfFirst { !it.isFolder && it.id == requestId }
    if (index >= 0) {
        val targetIndex = index + direction
        if (targetIndex in collections.indices) {
            val item = collections.removeAt(index)
            collections.add(targetIndex, item)
            return true
        }
        return false
    }
    for (node in collections) {
        if (node.children.isNotEmpty()) {
            if (moveRequestInCollections(node.children, requestId, direction)) return true
        }
    }
    return false
}

fun moveRequestsBeforeRequest(
    collections: MutableList<CollectionNode>,
    requestIds: List<String>,
    targetRequestId: String,
): Boolean {
    if (requestIds.isEmpty() || targetRequestId in requestIds) return false
    val extracted = mutableListOf<CollectionNode>()
    for (id in requestIds) {
        val node = extractRequestNode(collections, id) ?: return false
        extracted.add(node)
    }
    val target = findRequestParentAndIndex(collections, targetRequestId) ?: return false
    val insertIndex = target.second.coerceIn(0, target.first.size)
    target.first.addAll(insertIndex, extracted)
    return true
}

fun moveRequestBeforeRequest(
    collections: MutableList<CollectionNode>,
    requestId: String,
    targetRequestId: String,
): Boolean = moveRequestsBeforeRequest(collections, listOf(requestId), targetRequestId)

fun moveRequestsAfterRequest(
    collections: MutableList<CollectionNode>,
    requestIds: List<String>,
    targetRequestId: String,
): Boolean {
    if (requestIds.isEmpty() || targetRequestId in requestIds) return false
    val extracted = mutableListOf<CollectionNode>()
    for (id in requestIds) {
        val node = extractRequestNode(collections, id) ?: return false
        extracted.add(node)
    }
    val target = findRequestParentAndIndex(collections, targetRequestId) ?: return false
    val insertIndex = (target.second + 1).coerceIn(0, target.first.size)
    target.first.addAll(insertIndex, extracted)
    return true
}

fun moveRequestAfterRequest(
    collections: MutableList<CollectionNode>,
    requestId: String,
    targetRequestId: String,
): Boolean = moveRequestsAfterRequest(collections, listOf(requestId), targetRequestId)

fun moveRequestsToCollection(
    collections: MutableList<CollectionNode>,
    requestIds: List<String>,
    targetCollectionId: String,
): Boolean {
    if (requestIds.isEmpty()) return false
    val targetCollection = findCollectionById(collections, targetCollectionId) ?: return false
    val extracted = mutableListOf<CollectionNode>()
    for (id in requestIds) {
        val node = extractRequestNode(collections, id) ?: return false
        extracted.add(node)
    }
    targetCollection.children.addAll(extracted)
    return true
}

fun moveRequestToCollection(
    collections: MutableList<CollectionNode>,
    requestId: String,
    targetCollectionId: String,
): Boolean = moveRequestsToCollection(collections, listOf(requestId), targetCollectionId)

private fun extractRequestNode(nodes: MutableList<CollectionNode>, requestId: String): CollectionNode? {
    val index = nodes.indexOfFirst { !it.isFolder && it.id == requestId }
    if (index >= 0) {
        return nodes.removeAt(index)
    }
    for (node in nodes) {
        if (node.children.isNotEmpty()) {
            val extracted = extractRequestNode(node.children, requestId)
            if (extracted != null) return extracted
        }
    }
    return null
}

private fun findRequestParentAndIndex(
    nodes: MutableList<CollectionNode>,
    requestId: String,
): Pair<MutableList<CollectionNode>, Int>? {
    val index = nodes.indexOfFirst { !it.isFolder && it.id == requestId }
    if (index >= 0) return nodes to index
    for (node in nodes) {
        if (node.children.isNotEmpty()) {
            val found = findRequestParentAndIndex(node.children, requestId)
            if (found != null) return found
        }
    }
    return null
}

private fun findCollectionById(nodes: List<CollectionNode>, collectionId: String): CollectionNode? {
    nodes.forEach { node ->
        if (node.isFolder && node.id == collectionId) {
            return node
        }
        if (node.children.isNotEmpty()) {
            val child = findCollectionById(node.children, collectionId)
            if (child != null) return child
        }
    }
    return null
}

fun isValidFolderDropTarget(
    collections: List<CollectionNode>,
    sourceFolderId: String,
    targetFolderId: String,
): Boolean {
    if (sourceFolderId == targetFolderId) return false
    val sourceNode = findCollectionById(collections, sourceFolderId) ?: return false
    if (!sourceNode.isFolder) return false
    return !containsFolderId(sourceNode, targetFolderId)
}

/**
 * Recursively sets the expanded state to false for a node and all its
 * folder children, so imported collections appear fully collapsed.
 */
internal fun collapseNodeRecursively(node: CollectionNode, state: AppState) {
    if (node.isFolder) {
        state.collectionExpandedState[node.id] = false
        for (child in node.children) {
            collapseNodeRecursively(child, state)
        }
    }
}

// ── Collection-level drag-to-reorder helpers (Bug 3) ──────────────────────────

/**
 * Moves the collection identified by [collectionId] to be immediately BEFORE
 * the collection identified by [targetCollectionId] in [collections].
 * Returns true if the move was performed.
 */
fun moveCollectionBeforeCollection(
    collections: MutableList<CollectionNode>,
    collectionId: String,
    targetCollectionId: String,
): Boolean {
    val fromIndex = collections.indexOfFirst { it.id == collectionId }
    val toIndex   = collections.indexOfFirst { it.id == targetCollectionId }
    if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return false
    val item = collections.removeAt(fromIndex)
    val insertIndex = if (fromIndex < toIndex) toIndex - 1 else toIndex
    collections.add(insertIndex, item)
    return true
}

/**
 * Moves the collection identified by [collectionId] to be immediately AFTER
 * the collection identified by [targetCollectionId] in [collections].
 * Returns true if the move was performed.
 */
fun moveCollectionAfterCollection(
    collections: MutableList<CollectionNode>,
    collectionId: String,
    targetCollectionId: String,
): Boolean {
    val fromIndex = collections.indexOfFirst { it.id == collectionId }
    val toIndex   = collections.indexOfFirst { it.id == targetCollectionId }
    if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return false
    val item = collections.removeAt(fromIndex)
    val insertIndex = if (fromIndex < toIndex) toIndex else toIndex + 1
    collections.add(insertIndex, item)
    return true
}

// ── Folder drag-to-reorder helpers (roots + nested folders) ─────────────────

fun isValidFolderGroupDropTarget(
    collections: List<CollectionNode>,
    movingFolderIds: List<String>,
    targetFolderId: String,
): Boolean {
    if (targetFolderId in movingFolderIds) return false
    for (id in movingFolderIds) {
        val node = findCollectionById(collections, id) ?: return false
        if (!node.isFolder) return false
        if (containsFolderId(node, targetFolderId)) return false
    }
    return true
}

fun moveFoldersBeforeFolder(
    collections: MutableList<CollectionNode>,
    folderIds: List<String>,
    targetFolderId: String,
): Boolean {
    if (folderIds.isEmpty() || targetFolderId in folderIds) return false
    if (!isValidFolderGroupDropTarget(collections, folderIds, targetFolderId)) return false
    val extracted = mutableListOf<CollectionNode>()
    for (id in folderIds) {
        val node = extractFolderNode(collections, id) ?: return false
        extracted.add(node)
    }
    val target = findFolderParentAndIndex(collections, targetFolderId) ?: return false
    val insertIndex = target.second.coerceIn(0, target.first.size)
    target.first.addAll(insertIndex, extracted)
    return true
}

fun moveFolderBeforeFolder(
    collections: MutableList<CollectionNode>,
    folderId: String,
    targetFolderId: String,
): Boolean = moveFoldersBeforeFolder(collections, listOf(folderId), targetFolderId)

fun moveFoldersAfterFolder(
    collections: MutableList<CollectionNode>,
    folderIds: List<String>,
    targetFolderId: String,
): Boolean {
    if (folderIds.isEmpty() || targetFolderId in folderIds) return false
    if (!isValidFolderGroupDropTarget(collections, folderIds, targetFolderId)) return false
    val extracted = mutableListOf<CollectionNode>()
    for (id in folderIds) {
        val node = extractFolderNode(collections, id) ?: return false
        extracted.add(node)
    }
    val target = findFolderParentAndIndex(collections, targetFolderId) ?: return false
    val insertIndex = (target.second + 1).coerceIn(0, target.first.size)
    target.first.addAll(insertIndex, extracted)
    return true
}

fun moveFolderAfterFolder(
    collections: MutableList<CollectionNode>,
    folderId: String,
    targetFolderId: String,
): Boolean = moveFoldersAfterFolder(collections, listOf(folderId), targetFolderId)

private fun extractFolderNode(nodes: MutableList<CollectionNode>, folderId: String): CollectionNode? {
    val index = nodes.indexOfFirst { it.isFolder && it.id == folderId }
    if (index >= 0) return nodes.removeAt(index)
    for (node in nodes) {
        if (node.isFolder && node.children.isNotEmpty()) {
            val extracted = extractFolderNode(node.children, folderId)
            if (extracted != null) return extracted
        }
    }
    return null
}

private fun findFolderParentAndIndex(
    nodes: MutableList<CollectionNode>,
    folderId: String,
): Pair<MutableList<CollectionNode>, Int>? {
    val index = nodes.indexOfFirst { it.isFolder && it.id == folderId }
    if (index >= 0) return nodes to index
    for (node in nodes) {
        if (node.isFolder && node.children.isNotEmpty()) {
            val found = findFolderParentAndIndex(node.children, folderId)
            if (found != null) return found
        }
    }
    return null
}

private fun containsFolderId(node: CollectionNode, folderId: String): Boolean {
    if (node.id == folderId) return true
    node.children.forEach { child ->
        if (child.isFolder && containsFolderId(child, folderId)) return true
    }
    return false
}

@Composable
fun SidebarGitStatusIndicator(
    state: AppState,
    modifier: Modifier = Modifier,
) {
    val gitCollection = state.activeGitCollectionNode ?: return
    val gitStatus = state.activeGitStatus
    val branchName = gitStatus?.branch?.ifBlank { gitCollection.gitBranch } ?: gitCollection.gitBranch
    val isDirty = gitStatus != null && (!gitStatus.isClean || gitStatus.hasConflicts)

    LaunchedEffect(
        gitCollection.id,
        gitCollection.gitRemoteUrl,
        gitCollection.gitBranch,
        state.activeTabIndex,
        state.selectedCollectionId,
        state.selectedRequestId,
    ) {
        state.refreshGitStatusForCollection(gitCollection)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ClapiColors.Surface)
            .clickable { state.showGitSyncDialog = true }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("sidebar-git-indicator"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_fork_right),
            contentDescription = Strings.gitSync,
            tint = ClapiColors.Primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = branchName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = ClapiColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag("sidebar-git-branch-label"),
        )
        if (isDirty) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (gitStatus?.hasConflicts == true) ClapiColors.Error else ClapiColors.Primary)
                    .testTag("sidebar-git-dirty-dot"),
            )
        }
    }
}

