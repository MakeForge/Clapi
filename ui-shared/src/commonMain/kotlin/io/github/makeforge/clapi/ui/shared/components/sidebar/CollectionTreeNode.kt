package io.github.makeforge.clapi.ui.shared.components.sidebar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.ui.shared.components.GraphQLBadge
import io.github.makeforge.clapi.ui.shared.components.GrpcBadge
import io.github.makeforge.clapi.ui.shared.components.IconButtonWithTooltip
import io.github.makeforge.clapi.ui.shared.components.McpMethodBadge
import io.github.makeforge.clapi.ui.shared.components.MethodBadge
import io.github.makeforge.clapi.ui.shared.components.WebSocketBadge
import io.github.makeforge.clapi.ui.shared.components.handPointerHover
import io.github.makeforge.clapi.ui.shared.components.sharedSidebarTooltipState
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_add
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_article
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_bolt
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_content_copy
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_delete
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_drag_indicator
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_edit
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_folder
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_folder_open
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_hub
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_input
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_keyboard_arrow_down
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_keyboard_arrow_right
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_keyboard_arrow_up
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_more_vert
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_unfold_less
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_unfold_more
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.platform.isPointerDoubleClick
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import io.github.makeforge.clapi.ui.shared.state.hasSseAccept
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun CollectionTreeNode(
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
    modifier: Modifier = Modifier,
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
    val density = LocalDensity.current
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

    Column(modifier = modifier) {
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
                .handPointerHover()
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
                        .handPointerHover()
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
                                    .handPointerHover()
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
                                    .handPointerHover()
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
                    CollectionTreeNodeAddMenu(
                        expanded = showAddMenu,
                        onDismissRequest = { showAddMenu = false },
                        node = node,
                        onAddFolder = onAddFolder,
                        onAddRequest = onAddRequest,
                        onAddGrpcRequest = onAddGrpcRequest,
                        onAddWebSocketRequest = onAddWebSocketRequest,
                        onAddGraphQLRequest = onAddGraphQLRequest,
                    )
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
                CollectionTreeNodeContextMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    node = node,
                    isFolderNode = isFolderNode,
                    isRequest = isRequest,
                    isCollectionRoot = isCollectionRoot,
                    hasMultipleFolderSelection = hasMultipleFolderSelection,
                    hasMultipleRequestSelection = hasMultipleRequestSelection,
                    state = state,
                    onDuplicateSelectedFolders = onDuplicateSelectedFolders,
                    onDeleteSelectedFolders = onDeleteSelectedFolders,
                    onDuplicateSelectedRequests = onDuplicateSelectedRequests,
                    onDeleteSelectedRequests = onDeleteSelectedRequests,
                    onAddFolder = onAddFolder,
                    onAddRequest = onAddRequest,
                    onAddGrpcRequest = onAddGrpcRequest,
                    onAddWebSocketRequest = onAddWebSocketRequest,
                    onAddGraphQLRequest = onAddGraphQLRequest,
                    onAddMcpConnection = onAddMcpConnection,
                    onAddSseRequest = onAddSseRequest,
                    onExportCollection = onExportCollection,
                    onDuplicateCollection = onDuplicateCollection,
                    onRenameCollection = onRenameCollection,
                    onDeleteCollection = onDeleteCollection,
                    onDuplicateRequest = onDuplicateRequest,
                    onRenameRequest = onRenameRequest,
                    onMoveRequest = onMoveRequest,
                    onDeleteRequest = onDeleteRequest,
                )
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

