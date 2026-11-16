package io.github.makeforge.clapi.ui.shared.components.mcp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.core.model.*
import io.github.makeforge.clapi.core.network.mcp.*
import io.github.makeforge.clapi.ui.shared.components.*
import io.github.makeforge.clapi.ui.shared.components.executor.buildAuthConfig
import io.github.makeforge.clapi.ui.shared.generated.resources.*
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.mcp.*
import io.github.makeforge.clapi.ui.shared.platform.*
import io.github.makeforge.clapi.ui.shared.state.*
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun ConnectionBar(
    tab: RequestTabState,
    state: AppState,
    session: io.github.makeforge.clapi.ui.shared.mcp.McpSessionState,
    connection: McpConnectionState,
    reconnectNeeded: Boolean,
    showStdioConfirm: Boolean,
    onShowStdioConfirm: (Boolean) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
    onConfirmStdio: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusDot(connection)
            Text(
                when {
                    reconnectNeeded -> Strings.t("mcp_reconnect")
                    connection == McpConnectionState.CONNECTED -> Strings.t("mcp_connected")
                    connection == McpConnectionState.CONNECTING -> Strings.t("mcp_connecting")
                    connection == McpConnectionState.ERROR -> Strings.t("mcp_status_error")
                    else -> Strings.t("mcp_disconnected")
                },
                color = ClapiColors.OnSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        val negotiated = session.negotiatedLabel()
        val sessionId = session.sessionId()
        if (negotiated.isNotBlank() || sessionId != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (negotiated.isNotBlank()) {
                    ConnectionMetaChip(
                        text = negotiated,
                        modifier = Modifier.weight(1f),
                        fillText = true,
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                sessionId?.let { sid -> SessionIdChip(sid) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (tab.mcpConfig.transport == McpTransportType.STDIO) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ClapiColors.Surface)
                        .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    VariableAwareTextField(
                        value = tab.mcpConfig.command,
                        onValueChange = { tab.mcpConfig = tab.mcpConfig.copy(command = it); tab.markDirty() },
                        placeholder = Strings.t("mcp_command"),
                        state = state,
                        modifier = Modifier.fillMaxWidth().testTag("mcp-command"),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ClapiColors.Surface)
                        .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    VariableAwareTextField(
                        value = tab.mcpConfig.url.ifBlank { tab.url },
                        onValueChange = {
                            tab.mcpConfig = tab.mcpConfig.copy(url = it)
                            tab.url = it
                            syncParamsFromUrl(tab, it)
                            tab.markDirty()
                        },
                        placeholder = Strings.t("mcp_url"),
                        state = state,
                        undoStack = tab.urlUndoStack,
                        modifier = Modifier.fillMaxWidth().testTag("mcp-url"),
                    )
                }
            }
            ConnectionActionButton(
                connection = connection,
                reconnectNeeded = reconnectNeeded,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
                onReconnect = onReconnect,
            )
            DocsHelpButton(
                url = DocsLinks.mcp,
                tooltip = "MCP documentation",
                tag = "mcp-docs-btn",
                size = 32.dp,
            )
        }
        if (showStdioConfirm) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Strings.t("mcp_stdio_confirm"), color = ClapiColors.OnSurface, modifier = Modifier.weight(1f))
                TextButton(onClick = onConfirmStdio) { Text(Strings.confirm) }
                TextButton(onClick = { onShowStdioConfirm(false) }) { Text(Strings.cancel) }
            }
        }
    }
}

@Composable
internal fun ConnectionActionButton(
    connection: McpConnectionState,
    reconnectNeeded: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
) {
    when {
        connection == McpConnectionState.CONNECTING -> {
            Button(onClick = {}, enabled = false, modifier = Modifier.testTag("mcp-connect")) {
                Text(Strings.t("mcp_connecting"))
            }
        }
        connection == McpConnectionState.CONNECTED && !reconnectNeeded -> {
            OutlinedButton(onClick = onDisconnect, modifier = Modifier.testTag("mcp-disconnect")) {
                Text(Strings.disconnect)
            }
        }
        connection == McpConnectionState.ERROR || reconnectNeeded -> {
            Button(onClick = onReconnect, modifier = Modifier.testTag("mcp-reconnect")) {
                Text(Strings.t("mcp_reconnect"))
            }
        }
        else -> {
            Button(onClick = onConnect, modifier = Modifier.testTag("mcp-connect")) {
                Text(Strings.connect)
            }
        }
    }
}


@Composable
internal fun SessionIdChip(sessionId: String) {
    val visibleId = visibleSessionId(sessionId)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ClapiColors.Surface)
            .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
            .padding(start = 8.dp, end = 2.dp)
            .testTag("mcp-session-id"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            Strings.t("mcp_session_id"),
            color = ClapiColors.OnSurfaceDim,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        Text(
            visibleId,
            color = ClapiColors.OnSurface,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontFamily = CodeFontFamily,
            maxLines = 1,
        )
        IconButton(
            onClick = { copyToClipboard(sessionId) },
            modifier = Modifier.size(28.dp).testTag("mcp-session-id-copy"),
        ) {
            Icon(painter = painterResource(Res.drawable.ic_content_copy),
                contentDescription = Strings.copy,
                tint = ClapiColors.OnSurfaceDim,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

internal const val MCP_SESSION_ID_MAX_VISIBLE = 36

internal fun visibleSessionId(sessionId: String): String =
    if (sessionId.length <= MCP_SESSION_ID_MAX_VISIBLE) sessionId
    else sessionId.take(MCP_SESSION_ID_MAX_VISIBLE) + "…"

@Composable
internal fun ConnectionMetaChip(
    text: String,
    modifier: Modifier = Modifier,
    fillText: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ClapiColors.Surface)
            .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text,
            color = ClapiColors.OnSurfaceDim,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (fillText) Modifier.fillMaxWidth() else Modifier,
        )
    }
}

@Composable
internal fun StatusDot(state: McpConnectionState) {
    val color = when (state) {
        McpConnectionState.CONNECTED -> ClapiColors.StatusSuccess
        McpConnectionState.CONNECTING -> ClapiColors.StatusWarning
        McpConnectionState.ERROR -> ClapiColors.Error
        McpConnectionState.DISCONNECTED -> ClapiColors.OnSurfaceDim
    }
    Box(Modifier.size(10.dp).clip(CircleShape).background(color).testTag("mcp-status-${state.name}"))
}

@Composable
internal fun TransportChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) ClapiColors.Primary else ClapiColors.OnSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (selected) ClapiColors.Primary else ClapiColors.OnSurfaceDim, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        fontSize = 12.sp,
    )
}
