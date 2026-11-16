package io.github.crustack.clapi.ui.shared.components.sidebar

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.crustack.clapi.ui.shared.generated.resources.Res
import io.github.crustack.clapi.ui.shared.generated.resources.ic_add
import io.github.crustack.clapi.ui.shared.generated.resources.ic_bolt
import io.github.crustack.clapi.ui.shared.generated.resources.ic_content_copy
import io.github.crustack.clapi.ui.shared.generated.resources.ic_delete
import io.github.crustack.clapi.ui.shared.generated.resources.ic_edit
import io.github.crustack.clapi.ui.shared.generated.resources.ic_folder
import io.github.crustack.clapi.ui.shared.generated.resources.ic_folder_open
import io.github.crustack.clapi.ui.shared.generated.resources.ic_hub
import io.github.crustack.clapi.ui.shared.generated.resources.ic_input
import io.github.crustack.clapi.ui.shared.generated.resources.ic_keyboard_arrow_down
import io.github.crustack.clapi.ui.shared.generated.resources.ic_keyboard_arrow_up
import io.github.crustack.clapi.ui.shared.generated.resources.ic_unfold_less
import io.github.crustack.clapi.ui.shared.generated.resources.ic_unfold_more
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.CollectionNode
import org.jetbrains.compose.resources.painterResource

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
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.testTag("folder-add-menu-${node.id}"),
    ) {
        DropdownMenuItem(
            text = { Text(Strings.t("add_folder")) },
            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(16.dp)) },
            onClick = {
                onDismissRequest()
                onAddFolder(node)
            },
            modifier = Modifier.testTag("folder-menu-new-folder"),
        )
        DropdownMenuItem(
            text = { Text(Strings.t("new_http_request")) },
            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = null, modifier = Modifier.size(16.dp)) },
            onClick = {
                onDismissRequest()
                onAddRequest(node)
            },
            modifier = Modifier.testTag("folder-menu-new-http"),
        )
        DropdownMenuItem(
            text = { Text(Strings.t("new_grpc_request")) },
            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = null, modifier = Modifier.size(16.dp)) },
            onClick = {
                onDismissRequest()
                onAddGrpcRequest(node)
            },
            modifier = Modifier.testTag("folder-menu-new-grpc"),
        )
        DropdownMenuItem(
            text = { Text(Strings.t("new_websocket_request")) },
            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = null, modifier = Modifier.size(16.dp)) },
            onClick = {
                onDismissRequest()
                onAddWebSocketRequest(node)
            },
            modifier = Modifier.testTag("folder-menu-new-websocket"),
        )
        DropdownMenuItem(
            text = { Text(Strings.t("new_graphql_request")) },
            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = null, modifier = Modifier.size(16.dp)) },
            onClick = {
                onDismissRequest()
                onAddGraphQLRequest(node)
            },
            modifier = Modifier.testTag("folder-menu-new-graphql"),
        )
    }
}

@Composable
fun CollectionTreeNodeContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    node: CollectionNode,
    isFolderNode: Boolean,
    isRequest: Boolean,
    isCollectionRoot: Boolean,
    hasMultipleFolderSelection: Boolean,
    hasMultipleRequestSelection: Boolean,
    state: AppState,
    onDuplicateSelectedFolders: () -> Unit,
    onDeleteSelectedFolders: () -> Unit,
    onDuplicateSelectedRequests: () -> Unit,
    onDeleteSelectedRequests: () -> Unit,
    onAddFolder: (CollectionNode) -> Unit,
    onAddRequest: (CollectionNode) -> Unit,
    onAddGrpcRequest: (CollectionNode) -> Unit,
    onAddWebSocketRequest: (CollectionNode) -> Unit,
    onAddGraphQLRequest: (CollectionNode) -> Unit,
    onAddMcpConnection: (CollectionNode) -> Unit,
    onAddSseRequest: (CollectionNode) -> Unit,
    onExportCollection: (CollectionNode) -> Unit,
    onDuplicateCollection: (CollectionNode) -> Unit,
    onRenameCollection: (CollectionNode) -> Unit,
    onDeleteCollection: (CollectionNode) -> Unit,
    onDuplicateRequest: (CollectionNode) -> Unit,
    onRenameRequest: (CollectionNode) -> Unit,
    onMoveRequest: (requestId: String, direction: Int) -> Unit,
    onDeleteRequest: (CollectionNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        scrollState = rememberScrollState(),
        modifier = modifier,
    ) {
        if (hasMultipleFolderSelection) {
            DropdownMenuItem(
                text = { Text(Strings.duplicateFolder) },
                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_content_copy), contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    onDismissRequest()
                    onDuplicateSelectedFolders()
                },
                modifier = Modifier.testTag("collection-menu-multi-duplicate"),
            )
            DropdownMenuItem(
                text = { Text(Strings.delete) },
                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    onDismissRequest()
                    onDeleteSelectedFolders()
                },
                modifier = Modifier.testTag("collection-menu-multi-delete"),
            )
        } else if (hasMultipleRequestSelection) {
            DropdownMenuItem(
                text = { Text(Strings.t("duplicate_request")) },
                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_content_copy), contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    onDismissRequest()
                    onDuplicateSelectedRequests()
                },
                modifier = Modifier.testTag("collection-menu-multi-duplicate"),
            )
            DropdownMenuItem(
                text = { Text(Strings.t("delete_request")) },
                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    onDismissRequest()
                    onDeleteSelectedRequests()
                },
                modifier = Modifier.testTag("collection-menu-multi-delete"),
            )
        } else if (isFolderNode) {
            DropdownMenuItem(
                text = { Text(Strings.t("add_folder")) },
                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = { onDismissRequest(); onAddFolder(node) },
                modifier = Modifier.testTag("collection-menu-add-folder"),
            )
            DropdownMenuItem(
                text = { Text(Strings.t("expand")) },
                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_unfold_more), contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    onDismissRequest()
                    state.collectionExpandedState[node.id] = true
                },
            )
            DropdownMenuItem(
                text = { Text(Strings.t("collapse")) },
                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_unfold_less), contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    onDismissRequest()
                    state.collectionExpandedState[node.id] = false
                },
            )
            DropdownMenuItem(
                text = { Text(Strings.expandAll) },
                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_folder_open), contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    onDismissRequest()
                    state.expandAllCollections()
                },
            )
            DropdownMenuItem(
                text = { Text(Strings.collapseAll) },
                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    onDismissRequest()
                    state.collapseAllCollections()
                },
            )
            DropdownMenuItem(
                text = { Text(Strings.t("add_request")) },
                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = { onDismissRequest(); onAddRequest(node) },
                modifier = Modifier.testTag("collection-menu-add-request"),
            )
            DropdownMenuItem(
                text = { Text(Strings.t("new_grpc_request")) },
                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = { onDismissRequest(); onAddGrpcRequest(node) },
                modifier = Modifier.testTag("collection-menu-new-grpc"),
            )
            DropdownMenuItem(
                text = { Text(Strings.t("new_websocket_request")) },
                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = { onDismissRequest(); onAddWebSocketRequest(node) },
                modifier = Modifier.testTag("collection-menu-new-websocket"),
            )
            DropdownMenuItem(
                text = { Text(Strings.t("new_graphql_request")) },
                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = { onDismissRequest(); onAddGraphQLRequest(node) },
                modifier = Modifier.testTag("collection-menu-new-graphql"),
            )
            DropdownMenuItem(
                text = { Text(Strings.t("new_mcp_connection")) },
                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_hub), contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    onDismissRequest()
                    onAddMcpConnection(node)
                },
                modifier = Modifier.testTag("collection-menu-new-mcp"),
            )
            DropdownMenuItem(
                text = { Text(Strings.t("new_sse_request")) },
                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_bolt), contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    onDismissRequest()
                    onAddSseRequest(node)
                },
                modifier = Modifier.testTag("collection-menu-new-sse"),
            )
            if (isCollectionRoot) {
                DropdownMenuItem(
                    text = { Text(Strings.exportFolder) },
                    leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_input), contentDescription = null, modifier = Modifier.size(16.dp)) },
                    onClick = { onDismissRequest(); onExportCollection(node) },
                )
                DropdownMenuItem(
                    text = { Text(Strings.duplicateFolder) },
                    leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_content_copy), contentDescription = null, modifier = Modifier.size(16.dp)) },
                    onClick = { onDismissRequest(); onDuplicateCollection(node) },
                )
            }
            DropdownMenuItem(
                text = { Text(Strings.t("rename")) },
                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = { onDismissRequest(); onRenameCollection(node) },
            )
            DropdownMenuItem(
                text = { Text(Strings.delete) },
                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = { onDismissRequest(); onDeleteCollection(node) },
            )
        } else if (isRequest) {
            DropdownMenuItem(
                text = { Text(Strings.t("duplicate_request")) },
                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_content_copy), contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = { onDismissRequest(); onDuplicateRequest(node) },
            )
            DropdownMenuItem(
                text = { Text(Strings.t("rename_request")) },
                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = { onDismissRequest(); onRenameRequest(node) },
            )
            DropdownMenuItem(
                text = { Text(Strings.t("move_up")) },
                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_keyboard_arrow_up), contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = { onDismissRequest(); onMoveRequest(node.id, -1) },
            )
            DropdownMenuItem(
                text = { Text(Strings.t("move_down")) },
                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_keyboard_arrow_down), contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = { onDismissRequest(); onMoveRequest(node.id, 1) },
            )
            DropdownMenuItem(
                text = { Text(Strings.t("delete_request")) },
                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = { onDismissRequest(); onDeleteRequest(node) },
            )
        }
    }
}
