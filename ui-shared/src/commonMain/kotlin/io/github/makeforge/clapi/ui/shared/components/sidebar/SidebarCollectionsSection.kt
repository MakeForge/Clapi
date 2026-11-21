package io.github.makeforge.clapi.ui.shared.components.sidebar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.ui.shared.components.IconButtonWithTooltip
import io.github.makeforge.clapi.ui.shared.generated.resources.*
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.persistence.ImportExportNaming
import io.github.makeforge.clapi.ui.shared.persistence.ImportExportRepository
import io.github.makeforge.clapi.ui.shared.platform.saveFileForExport
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import io.github.makeforge.clapi.ui.shared.state.LogLevel
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

internal fun LazyListScope.sidebarCollectionsSection(
    state: AppState,
    scope: CoroutineScope,
    dragState: SidebarDragState,
    persistWorkspaceAsync: () -> Unit,
    launchTracked: (String, String, suspend () -> Unit) -> Unit,
    inlineRenamingId: String?,
    onInlineRenamingIdChange: (String?) -> Unit,
    onCommitInlineRename: (CollectionNode, String) -> Unit,
    onAddFolder: (CollectionNode) -> Unit,
) {
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
                            onInlineRenamingIdChange(created.id)
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
            val toDuplicate = pruneRedundantDescendantFolders(state.collections, state.selectedFolderIds.toList())
            for (id in toDuplicate) {
                duplicateFolderInCollections(state.collections, id)
            }
            state.notifyCollectionsChanged()
            persistWorkspaceAsync()
            state.log("Folder(s) duplicated: ${toDuplicate.size}", LogLevel.SUCCESS)
        }

        val onDeleteSelectedFolders: () -> Unit = {
            val toDelete = pruneRedundantDescendantFolders(state.collections, state.selectedFolderIds.toList())
            val action = {
                val allRequestIdsToClose = mutableListOf<String>()
                for (id in toDelete) {
                    val deleted = deleteFolderFromCollections(state.collections, id)
                    if (deleted != null) {
                        allRequestIdsToClose.addAll(collectAllRequestIds(deleted))
                    }
                }
                state.closeTabsByIds(allRequestIdsToClose)
                state.clearSidebarSelection()
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
            val toDuplicate = state.selectedRequestIds.distinct()
            for (id in toDuplicate) {
                duplicateRequestInCollections(state.collections, id)
            }
            state.notifyCollectionsChanged()
            persistWorkspaceAsync()
            state.log("Request(s) duplicated: ${toDuplicate.size}", LogLevel.SUCCESS)
        }

        val onDeleteSelectedRequests: () -> Unit = {
            val toDelete = state.selectedRequestIds.distinct()
            val action = {
                for (id in toDelete) {
                    deleteRequestFromCollections(state.collections, id)
                }
                state.closeTabsByIds(toDelete)
                state.clearSidebarSelection()
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
                            withContext(Dispatchers.Main) {
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
                        onInlineRenamingIdChange(target.id)
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
                                val removedIds = collectAllRequestIds(target)
                                state.deleteCollection(target.id)
                                state.pruneSidebarSelection(removedIds)
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
                                    val deletedIds = collectAllRequestIds(deleted)
                                    state.closeTabsByIds(deletedIds)
                                    state.pruneSidebarSelection(deletedIds)
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
                    onAddFolder = onAddFolder,
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
                        onInlineRenamingIdChange(target.id)
                    },
                    onDeleteRequest = { target ->
                        val action = {
                            deleteRequestFromCollections(state.collections, target.id)
                            state.closeTabsByIds(listOf(target.id))
                            state.pruneSidebarSelection(listOf(target.id))
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
                    draggedRequestId = dragState.draggedRequestId,
                    dropTargetCollectionId = dragState.dropTargetCollectionId,
                    dropTargetRequestId = dragState.dropTargetRequestId,
                    dropInsertAfter = dragState.dropInsertAfter,
                    onDragStart = { requestId ->
                        dragState.draggedRequestId = requestId
                        dragState.dragOffsetY = 0f
                        dragState.dragCursorAbsY = dragState.hitAreas[requestId]?.let { (it.top + it.bottom) / 2f } ?: 0f
                        dragState.dropTargetCollectionId = null
                        dragState.dropTargetRequestId = null
                        dragState.dropInsertAfter = false
                    },
                    onDragDelta = { requestId, deltaY ->
                        if (dragState.draggedRequestId == requestId) {
                            dragState.dragOffsetY += deltaY
                            dragState.dragCursorAbsY += deltaY
                            val currentY = dragState.dragCursorAbsY
                            val movingRequestIds = if (requestId in state.selectedRequestIds) {
                                state.selectedRequestIds.toList()
                            } else {
                                listOf(requestId)
                            }
                            val target = resolveClosestHitArea(
                                areas = dragState.hitAreas.values.filter { it.id !in movingRequestIds },
                                cursorY = currentY,
                            )
                            if (target != null) {
                                if (target.isFolder) {
                                    dragState.dropTargetCollectionId = target.id
                                    dragState.dropTargetRequestId = null
                                    dragState.dropInsertAfter = false
                                } else {
                                    val midY = (target.top + target.bottom) / 2f
                                    dragState.dropInsertAfter = currentY >= midY
                                    dragState.dropTargetRequestId = target.id
                                    dragState.dropTargetCollectionId = target.parentCollectionId
                                }
                            }
                        }
                    },
                    onNodePositioned = { area -> dragState.hitAreas[area.id] = area },
                    onDragEnd = {
                        dragState.dragCursorAbsY = 0f
                        val sourceId = dragState.draggedRequestId
                        val movingRequestIds = if (sourceId != null && sourceId in state.selectedRequestIds) {
                            state.selectedRequestIds.toList()
                        } else if (sourceId != null) {
                            listOf(sourceId)
                        } else {
                            emptyList()
                        }
                        val targetRequestId = dragState.dropTargetRequestId
                        val targetCollectionId = dragState.dropTargetCollectionId
                        if (movingRequestIds.isNotEmpty()) {
                            val moved = when {
                                targetRequestId != null && targetRequestId !in movingRequestIds ->
                                    if (dragState.dropInsertAfter) {
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
                        dragState.draggedRequestId = null
                        dragState.dragOffsetY = 0f
                        dragState.dropTargetCollectionId = null
                        dragState.dropTargetRequestId = null
                        dragState.dropInsertAfter = false
                    },
                    // ── Collection-level drag callbacks ────────────────
                    draggedCollectionId = dragState.draggedCollectionId,
                    dropCollectionTargetId = dragState.dropCollectionTargetId,
                    dropCollectionInsertAfter = dragState.dropCollectionInsertAfter,
                    onCollectionDragStart = { collectionId ->
                        dragState.draggedCollectionId = collectionId
                        dragState.dragCollectionOffsetY = 0f
                        dragState.dragCursorAbsY = dragState.collectionHitAreas[collectionId]?.let { (it.top + it.bottom) / 2f } ?: 0f
                        dragState.draggedFolderParentId = dragState.collectionHitAreas[collectionId]?.parentCollectionId
                        dragState.draggedFolderParentIdSet = true
                        dragState.dropCollectionTargetId = null
                        dragState.dropCollectionInsertAfter = false
                    },
                    onCollectionDragDelta = { collectionId, deltaY ->
                        if (dragState.draggedCollectionId == collectionId) {
                            dragState.dragCollectionOffsetY += deltaY
                            dragState.dragCursorAbsY += deltaY
                            val currentY = dragState.dragCursorAbsY
                            val parentId = dragState.draggedFolderParentId
                            val movingFolderIds = if (collectionId in state.selectedFolderIds) {
                                state.selectedFolderIds.toList()
                            } else {
                                listOf(collectionId)
                            }
                            val target = resolveClosestHitArea(
                                areas = dragState.collectionHitAreas.values.filter {
                                    it.id !in movingFolderIds &&
                                        it.parentCollectionId == parentId &&
                                        isValidFolderGroupDropTarget(state.collections, movingFolderIds, it.id)
                                },
                                cursorY = currentY,
                            )
                            if (target != null) {
                                val midY = (target.top + target.bottom) / 2f
                                dragState.dropCollectionInsertAfter = currentY >= midY
                                dragState.dropCollectionTargetId = target.id
                            }
                        }
                    },
                    onCollectionHitAreaPositioned = { area -> dragState.collectionHitAreas[area.id] = area },
                    onCollectionDragEnd = {
                        dragState.dragCursorAbsY = 0f
                        val sourceId = dragState.draggedCollectionId
                        val targetId = dragState.dropCollectionTargetId
                        val movingFolderIds = if (sourceId != null && sourceId in state.selectedFolderIds) {
                            state.selectedFolderIds.toList()
                        } else if (sourceId != null) {
                            listOf(sourceId)
                        } else {
                            emptyList()
                        }
                        if (movingFolderIds.isNotEmpty() && targetId != null && targetId !in movingFolderIds) {
                            val moved = if (dragState.dropCollectionInsertAfter) {
                                moveFoldersAfterFolder(state.collections, movingFolderIds, targetId)
                            } else {
                                moveFoldersBeforeFolder(state.collections, movingFolderIds, targetId)
                            }
                            if (moved) {
                                state.notifyCollectionsChanged()
                                persistWorkspaceAsync()
                            }
                        }
                        dragState.draggedCollectionId = null
                        dragState.dragCollectionOffsetY = 0f
                        dragState.draggedFolderParentId = null
                        dragState.draggedFolderParentIdSet = false
                        dragState.dropCollectionTargetId = null
                        dragState.dropCollectionInsertAfter = false
                    },
                    inlineRenamingId = inlineRenamingId,
                    onStartInlineRename = { onInlineRenamingIdChange(it) },
                    onCommitInlineRename = onCommitInlineRename,
                    onCancelInlineRename = { onInlineRenamingIdChange(null) },
                )
            }
        }
        item { SectionSpacer() }
    }
}
