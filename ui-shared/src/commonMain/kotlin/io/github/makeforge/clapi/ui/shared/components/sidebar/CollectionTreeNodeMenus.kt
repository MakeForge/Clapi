package io.github.makeforge.clapi.ui.shared.components.sidebar

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqOperationMode
import io.github.makeforge.clapi.ui.shared.components.DenseDropdownMenuItem
import io.github.makeforge.clapi.ui.shared.components.GraphQLBadge
import io.github.makeforge.clapi.ui.shared.components.GrpcBadge
import io.github.makeforge.clapi.ui.shared.components.HttpBadge
import io.github.makeforge.clapi.ui.shared.components.McpMethodBadge
import io.github.makeforge.clapi.ui.shared.components.MethodBadge
import io.github.makeforge.clapi.ui.shared.components.RabbitMqBadge
import io.github.makeforge.clapi.ui.shared.components.WebSocketBadge
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_content_copy
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_delete
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_edit
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_folder
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_input
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_keyboard_arrow_down
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_keyboard_arrow_up
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_unfold_less
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_unfold_more
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Callbacks for creating folders and protocol requests under a folder/collection node.
 * Shared between the `+` add menu and the folder context menu so both stay in sync.
 */
data class NodeCreationActions(
    val onAddFolder: (CollectionNode) -> Unit,
    val onAddRequest: (CollectionNode) -> Unit,
    val onAddGrpcRequest: (CollectionNode) -> Unit,
    val onAddWebSocketRequest: (CollectionNode) -> Unit,
    val onAddGraphQLRequest: (CollectionNode) -> Unit,
    val onAddRabbitMqRequest: (CollectionNode, RabbitMqOperationMode) -> Unit = { _, _ -> },
    val onAddMcpConnection: (CollectionNode) -> Unit = {},
    val onAddSseRequest: (CollectionNode) -> Unit = {},
)

enum class MultiSelectionTarget {
    FOLDERS,
    REQUESTS,
}

/**
 * Strongly-typed menu variants for [CollectionTreeNodeContextMenu], carrying only
 * the data and callbacks relevant to each node/selection kind.
 */
sealed interface CollectionTreeContextMenuModel {
    data class MultiSelection(
        val target: MultiSelectionTarget,
        val onDuplicate: () -> Unit,
        val onDelete: () -> Unit,
    ) : CollectionTreeContextMenuModel

    data class Folder(
        val node: CollectionNode,
        val isCollectionRoot: Boolean,
        val creationActions: NodeCreationActions,
        val onRename: (CollectionNode) -> Unit,
        val onExpandAll: () -> Unit,
        val onCollapseAll: () -> Unit,
        val onExport: (CollectionNode) -> Unit,
        val onDuplicate: (CollectionNode) -> Unit,
        val onDelete: (CollectionNode) -> Unit,
    ) : CollectionTreeContextMenuModel

    data class Request(
        val node: CollectionNode,
        val onDuplicate: (CollectionNode) -> Unit,
        val onRename: (CollectionNode) -> Unit,
        val onMove: (requestId: String, direction: Int) -> Unit,
        val onDelete: (CollectionNode) -> Unit,
    ) : CollectionTreeContextMenuModel
}

@Composable
fun CollectionTreeNodeAddMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    node: CollectionNode,
    actions: NodeCreationActions,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.testTag("folder-add-menu-${node.id}"),
    ) {
        BadgeMenuItem(
            label = Strings.folder,
            badge = {
                Icon(
                    painter = painterResource(Res.drawable.ic_folder),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
            onDismissRequest = onDismissRequest,
            onClick = { actions.onAddFolder(node) },
            testTag = "folder-menu-new-folder",
        )
        RequestCreationMenuItems(
            node = node,
            actions = actions,
            httpLabel = Strings.httpRequest,
            httpTestTag = "folder-menu-new-http",
            testTagPrefix = "folder-menu",
            onDismissRequest = onDismissRequest,
        )
    }
}

@Composable
fun CollectionTreeNodeAddMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    node: CollectionNode,
    onAddFolder: (CollectionNode) -> Unit,
    onAddRequest: (CollectionNode) -> Unit,
    onAddGrpcRequest: (CollectionNode) -> Unit,
    onAddWebSocketRequest: (CollectionNode) -> Unit,
    onAddGraphQLRequest: (CollectionNode) -> Unit,
    onAddRabbitMqRequest: (CollectionNode, RabbitMqOperationMode) -> Unit = { _, _ -> },
    onAddMcpConnection: (CollectionNode) -> Unit = {},
    onAddSseRequest: (CollectionNode) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    CollectionTreeNodeAddMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        node = node,
        actions = NodeCreationActions(
            onAddFolder = onAddFolder,
            onAddRequest = onAddRequest,
            onAddGrpcRequest = onAddGrpcRequest,
            onAddWebSocketRequest = onAddWebSocketRequest,
            onAddGraphQLRequest = onAddGraphQLRequest,
            onAddRabbitMqRequest = onAddRabbitMqRequest,
            onAddMcpConnection = onAddMcpConnection,
            onAddSseRequest = onAddSseRequest,
        ),
        modifier = modifier,
    )
}

@Composable
fun CollectionTreeNodeContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    model: CollectionTreeContextMenuModel?,
    modifier: Modifier = Modifier,
) {
    if (model == null) return
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        scrollState = rememberScrollState(),
        modifier = modifier,
    ) {
        when (model) {
            is CollectionTreeContextMenuModel.MultiSelection ->
                MultiSelectionMenuItems(model, onDismissRequest)
            is CollectionTreeContextMenuModel.Folder ->
                FolderContextMenuItems(model, onDismissRequest)
            is CollectionTreeContextMenuModel.Request ->
                RequestContextMenuItems(model, onDismissRequest)
        }
    }
}

@Composable
private fun MultiSelectionMenuItems(
    model: CollectionTreeContextMenuModel.MultiSelection,
    onDismissRequest: () -> Unit,
) {
    val duplicateLabel = when (model.target) {
        MultiSelectionTarget.FOLDERS -> Strings.duplicateFolder
        MultiSelectionTarget.REQUESTS -> Strings.t("duplicate_request")
    }
    val deleteLabel = when (model.target) {
        MultiSelectionTarget.FOLDERS -> Strings.delete
        MultiSelectionTarget.REQUESTS -> Strings.t("delete_request")
    }
    IconMenuItem(
        label = duplicateLabel,
        icon = Res.drawable.ic_content_copy,
        onDismissRequest = onDismissRequest,
        onClick = model.onDuplicate,
        testTag = "collection-menu-multi-duplicate",
    )
    IconMenuItem(
        label = deleteLabel,
        icon = Res.drawable.ic_delete,
        onDismissRequest = onDismissRequest,
        onClick = model.onDelete,
        testTag = "collection-menu-multi-delete",
    )
}

@Composable
private fun FolderContextMenuItems(
    model: CollectionTreeContextMenuModel.Folder,
    onDismissRequest: () -> Unit,
) {
    val node = model.node
    IconMenuItem(
        label = Strings.t("add_folder"),
        icon = Res.drawable.ic_folder,
        onDismissRequest = onDismissRequest,
        onClick = { model.creationActions.onAddFolder(node) },
        testTag = "collection-menu-add-folder",
    )
    IconMenuItem(
        label = Strings.t("rename"),
        icon = Res.drawable.ic_edit,
        onDismissRequest = onDismissRequest,
        onClick = { model.onRename(node) },
    )
    RequestCreationMenuItems(
        node = node,
        actions = model.creationActions,
        httpLabel = Strings.t("add_request"),
        httpTestTag = "collection-menu-add-request",
        testTagPrefix = "collection-menu",
        onDismissRequest = onDismissRequest,
    )
    IconMenuItem(
        label = Strings.expandAll,
        icon = Res.drawable.ic_unfold_more,
        onDismissRequest = onDismissRequest,
        onClick = model.onExpandAll,
    )
    IconMenuItem(
        label = Strings.collapseAll,
        icon = Res.drawable.ic_unfold_less,
        onDismissRequest = onDismissRequest,
        onClick = model.onCollapseAll,
    )
    if (model.isCollectionRoot) {
        IconMenuItem(
            label = Strings.exportFolder,
            icon = Res.drawable.ic_input,
            onDismissRequest = onDismissRequest,
            onClick = { model.onExport(node) },
        )
        IconMenuItem(
            label = Strings.duplicateFolder,
            icon = Res.drawable.ic_content_copy,
            onDismissRequest = onDismissRequest,
            onClick = { model.onDuplicate(node) },
        )
    }
    IconMenuItem(
        label = Strings.delete,
        icon = Res.drawable.ic_delete,
        onDismissRequest = onDismissRequest,
        onClick = { model.onDelete(node) },
    )
}

@Composable
private fun RequestContextMenuItems(
    model: CollectionTreeContextMenuModel.Request,
    onDismissRequest: () -> Unit,
) {
    val node = model.node
    IconMenuItem(
        label = Strings.t("duplicate_request"),
        icon = Res.drawable.ic_content_copy,
        onDismissRequest = onDismissRequest,
        onClick = { model.onDuplicate(node) },
    )
    IconMenuItem(
        label = Strings.t("rename_request"),
        icon = Res.drawable.ic_edit,
        onDismissRequest = onDismissRequest,
        onClick = { model.onRename(node) },
    )
    IconMenuItem(
        label = Strings.t("move_up"),
        icon = Res.drawable.ic_keyboard_arrow_up,
        onDismissRequest = onDismissRequest,
        onClick = { model.onMove(node.id, -1) },
    )
    IconMenuItem(
        label = Strings.t("move_down"),
        icon = Res.drawable.ic_keyboard_arrow_down,
        onDismissRequest = onDismissRequest,
        onClick = { model.onMove(node.id, 1) },
    )
    IconMenuItem(
        label = Strings.t("delete_request"),
        icon = Res.drawable.ic_delete,
        onDismissRequest = onDismissRequest,
        onClick = { model.onDelete(node) },
    )
}

@Composable
private fun RequestCreationMenuItems(
    node: CollectionNode,
    actions: NodeCreationActions,
    httpLabel: String,
    httpTestTag: String,
    testTagPrefix: String,
    onDismissRequest: () -> Unit,
) {
    BadgeMenuItem(
        label = httpLabel,
        badge = { HttpBadge(compact = true) },
        onDismissRequest = onDismissRequest,
        onClick = { actions.onAddRequest(node) },
        testTag = httpTestTag,
    )
    BadgeMenuItem(
        label = Strings.grpcRequest,
        badge = { GrpcBadge(compact = true) },
        onDismissRequest = onDismissRequest,
        onClick = { actions.onAddGrpcRequest(node) },
        testTag = "$testTagPrefix-new-grpc",
    )
    BadgeMenuItem(
        label = Strings.websocketRequest,
        badge = { WebSocketBadge(compact = true) },
        onDismissRequest = onDismissRequest,
        onClick = { actions.onAddWebSocketRequest(node) },
        testTag = "$testTagPrefix-new-websocket",
    )
    BadgeMenuItem(
        label = Strings.graphqlRequest,
        badge = { GraphQLBadge(compact = true) },
        onDismissRequest = onDismissRequest,
        onClick = { actions.onAddGraphQLRequest(node) },
        testTag = "$testTagPrefix-new-graphql",
    )
    RabbitMqSubMenuMenuItem(
        node = node,
        testTagPrefix = testTagPrefix,
        onDismissParent = onDismissRequest,
        onAddRabbitMqRequest = actions.onAddRabbitMqRequest,
    )
    BadgeMenuItem(
        label = Strings.t("new_mcp_connection"),
        badge = { McpMethodBadge(compact = true) },
        onDismissRequest = onDismissRequest,
        onClick = { actions.onAddMcpConnection(node) },
        testTag = "$testTagPrefix-new-mcp",
    )
    BadgeMenuItem(
        label = Strings.t("new_sse_request"),
        badge = { MethodBadge(HttpMethodType.GET, compact = true, sse = true) },
        onDismissRequest = onDismissRequest,
        onClick = { actions.onAddSseRequest(node) },
        testTag = "$testTagPrefix-new-sse",
    )
}

@Composable
private fun RabbitMqSubMenuMenuItem(
    node: CollectionNode,
    testTagPrefix: String,
    onDismissParent: () -> Unit,
    onAddRabbitMqRequest: (CollectionNode, RabbitMqOperationMode) -> Unit,
) {
    var subMenuExpanded by remember { mutableStateOf(false) }
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    LaunchedEffect(isHovered) {
        if (isHovered) subMenuExpanded = true
    }

    Box(modifier = Modifier.hoverable(hoverInteraction)) {
        DenseDropdownMenuItem(
            text = { Text("RabbitMQ") },
            leadingIcon = {
                Box(modifier = Modifier.width(38.dp), contentAlignment = Alignment.CenterStart) {
                    RabbitMqBadge(modeBadge = "RMQ", compact = true)
                }
            },
            trailingIcon = { Text("▸", color = ClapiColors.OnSurfaceDim) },
            onClick = { subMenuExpanded = true },
            modifier = Modifier.testTag("$testTagPrefix-new-rabbitmq"),
        )
        DropdownMenu(
            expanded = subMenuExpanded,
            onDismissRequest = { subMenuExpanded = false },
            offset = DpOffset(x = 200.dp, y = (-40).dp),
            modifier = Modifier.testTag("$testTagPrefix-rabbitmq-submenu"),
        ) {
            val dismissAll = {
                subMenuExpanded = false
                onDismissParent()
            }
            BadgeMenuItem(
                label = "RabbitMQ Publish Request",
                badge = { RabbitMqBadge(modeBadge = "PUB", compact = true) },
                onDismissRequest = dismissAll,
                onClick = { onAddRabbitMqRequest(node, RabbitMqOperationMode.PUBLISH) },
                testTag = "$testTagPrefix-new-rabbitmq-publish",
            )
            BadgeMenuItem(
                label = "RabbitMQ Subscribe Request",
                badge = { RabbitMqBadge(modeBadge = "SUB", compact = true) },
                onDismissRequest = dismissAll,
                onClick = { onAddRabbitMqRequest(node, RabbitMqOperationMode.SUBSCRIBE) },
                testTag = "$testTagPrefix-new-rabbitmq-subscribe",
            )
            BadgeMenuItem(
                label = "RabbitMQ RPC Request",
                badge = { RabbitMqBadge(modeBadge = "RPC", compact = true) },
                onDismissRequest = dismissAll,
                onClick = { onAddRabbitMqRequest(node, RabbitMqOperationMode.RPC) },
                testTag = "$testTagPrefix-new-rabbitmq-rpc",
            )
        }
    }
}

@Composable
private fun IconMenuItem(
    label: String,
    icon: DrawableResource,
    onDismissRequest: () -> Unit,
    onClick: () -> Unit,
    testTag: String? = null,
) {
    DenseDropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
        onClick = {
            onDismissRequest()
            onClick()
        },
        modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier,
    )
}

@Composable
private fun BadgeMenuItem(
    label: String,
    badge: @Composable () -> Unit,
    onDismissRequest: () -> Unit,
    onClick: () -> Unit,
    testTag: String? = null,
) {
    DenseDropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Box(modifier = Modifier.width(38.dp), contentAlignment = Alignment.CenterStart) {
                badge()
            }
        },
        onClick = {
            onDismissRequest()
            onClick()
        },
        modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier,
    )
}
