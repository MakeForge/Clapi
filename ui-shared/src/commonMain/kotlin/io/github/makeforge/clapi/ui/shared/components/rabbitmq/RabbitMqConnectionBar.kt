package io.github.makeforge.clapi.ui.shared.components.rabbitmq

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.core.model.RabbitMqConnectionStatus
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqConnectionConfig
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqOperationMode
import io.github.makeforge.clapi.ui.shared.components.DenseDropdownMenuItem
import io.github.makeforge.clapi.ui.shared.components.DocsHelpButton
import io.github.makeforge.clapi.ui.shared.components.RabbitMqBadge
import io.github.makeforge.clapi.ui.shared.components.SaveButton
import io.github.makeforge.clapi.ui.shared.components.VariableAwareTextField
import io.github.makeforge.clapi.ui.shared.components.handPointerHover
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_play_arrow
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_send
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_stop
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.platform.DocsLinks
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.RabbitMqEditorTab
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily
import org.jetbrains.compose.resources.painterResource

internal fun formatAmqpUrl(conn: RabbitMqConnectionConfig): String {
    val rawHost = conn.host.trim()
    if (rawHost.startsWith("amqp://", ignoreCase = true) || rawHost.startsWith("amqps://", ignoreCase = true)) {
        return rawHost
    }
    val scheme = if (conn.useTls) "amqps" else "amqp"
    val portPart = conn.port?.let { ":$it" } ?: ""
    val vhostPart = when {
        conn.vhost.isBlank() || conn.vhost == "/" -> "/"
        conn.vhost.startsWith("/") -> conn.vhost
        else -> "/${conn.vhost}"
    }
    return "$scheme://${rawHost.ifBlank { "localhost" }}$portPart$vhostPart"
}

internal fun parseAmqpUrlIntoConfig(raw: String, current: RabbitMqConnectionConfig): RabbitMqConnectionConfig {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return current.copy(host = "")
    val useTls = when {
        trimmed.startsWith("amqps://", ignoreCase = true) -> true
        trimmed.startsWith("amqp://", ignoreCase = true) -> false
        else -> current.useTls
    }
    val withoutScheme = trimmed
        .removePrefix("amqps://").removePrefix("AMQPS://")
        .removePrefix("amqp://").removePrefix("AMQP://")
    val slashIdx = withoutScheme.indexOf('/')
    val authority = if (slashIdx >= 0) withoutScheme.substring(0, slashIdx) else withoutScheme
    val vhost = if (slashIdx >= 0) {
        val path = withoutScheme.substring(slashIdx)
        path.ifBlank { "/" }
    } else {
        current.vhost.ifBlank { "/" }
    }
    val colonIdx = authority.lastIndexOf(':')
    val (host, port) = if (colonIdx > 0 && authority.substring(colonIdx + 1).toIntOrNull() != null) {
        authority.substring(0, colonIdx) to authority.substring(colonIdx + 1).toInt()
    } else {
        authority to current.port
    }
    return current.copy(host = host, port = port, vhost = vhost, useTls = useTls)
}

/**
 * Top address bar for RabbitMQ requests, styled identically to [WebSocketAddressBar]
 * and [GraphQLAddressBar] using [VariableAwareTextField], [SaveButton], and [DocsHelpButton].
 */
@Composable
fun RabbitMqConnectionBar(
    tab: RequestTabState,
    state: AppState,
    onPrimaryAction: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var modeMenuOpen by remember { mutableStateOf(false) }
    val displayUrl = remember(tab.rabbitMqConnection) { formatAmqpUrl(tab.rabbitMqConnection) }
    var urlText by remember(tab.id) { mutableStateOf(displayUrl) }
    // Keep URL bar in sync if user edits Host/Port/VHost/TLS inside the AMQP tab
    if (parseAmqpUrlIntoConfig(urlText, tab.rabbitMqConnection) != tab.rabbitMqConnection) {
        urlText = displayUrl
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ClapiColors.Surface)
            .border(width = 1.dp, color = ClapiColors.Border)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Mode dropdown (PUB / SUB / RPC)
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .handPointerHover()
                    .clickable { modeMenuOpen = true }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .testTag("rabbitmq-mode-selector"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RabbitMqBadge(modeBadge = tab.rabbitMqMode.badge, compact = false)
                Text("▾", color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
            }
            DropdownMenu(expanded = modeMenuOpen, onDismissRequest = { modeMenuOpen = false }) {
                RabbitMqOperationMode.entries.forEach { mode ->
                    DenseDropdownMenuItem(
                        text = { Text("${mode.badge} — ${mode.value.replaceFirstChar { it.uppercase() }}") },
                        modifier = Modifier.testTag("rabbitmq-mode-option-${mode.value}"),
                        onClick = {
                            tab.rabbitMqMode = mode
                            if (mode == RabbitMqOperationMode.SUBSCRIBE && tab.selectedRabbitMqTab == RabbitMqEditorTab.MESSAGE) {
                                tab.selectedRabbitMqTab = RabbitMqEditorTab.AMQP
                            }
                            tab.markDirty()
                            modeMenuOpen = false
                        },
                    )
                }
            }
        }

        // Broker URL input styled identically to WebSocket / HTTP / GraphQL URL bar
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(ClapiColors.SurfaceContainer)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            VariableAwareTextField(
                value = urlText,
                onValueChange = { newVal ->
                    urlText = newVal
                    tab.url = newVal
                    tab.rabbitMqConnection = parseAmqpUrlIntoConfig(newVal, tab.rabbitMqConnection)
                    tab.markDirty()
                },
                placeholder = "amqp://localhost:5672/ or amqps://…",
                textStyle = TextStyle(
                    color = ClapiColors.OnSurface,
                    fontSize = 14.sp,
                    fontFamily = CodeFontFamily,
                ),
                state = state,
                tab = tab,
                undoStack = tab.urlUndoStack,
                modifier = Modifier.fillMaxWidth().testTag("rabbitmq-host-input"),
            )
        }

        // Connection Status Pill for Subscribe mode
        if (tab.rabbitMqMode == RabbitMqOperationMode.SUBSCRIBE) {
            val (statusLabel, statusColor) = when (tab.rabbitMqConnectionStatus) {
                RabbitMqConnectionStatus.CONNECTED -> Strings.connected to ClapiColors.StatusSuccess
                RabbitMqConnectionStatus.CONNECTING -> Strings.connecting to ClapiColors.StatusWarning
                RabbitMqConnectionStatus.DISCONNECTING -> "Disconnecting" to ClapiColors.StatusWarning
                RabbitMqConnectionStatus.ERROR -> "Error" to ClapiColors.Error
                RabbitMqConnectionStatus.DISCONNECTED -> "Disconnected" to ClapiColors.OnSurfaceDim
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(statusColor.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(statusColor))
                Text(statusLabel, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }

        // Primary action button matching WebSocketAddressBar styling
        val isSubscribed = tab.rabbitMqMode == RabbitMqOperationMode.SUBSCRIBE &&
            (tab.rabbitMqConnectionStatus == RabbitMqConnectionStatus.CONNECTED ||
                tab.rabbitMqConnectionStatus == RabbitMqConnectionStatus.CONNECTING)
        val actionLabel = when (tab.rabbitMqMode) {
            RabbitMqOperationMode.PUBLISH -> if (tab.rabbitMqIsExecuting) "Publishing…" else Strings.t("rabbitmq_publish")
            RabbitMqOperationMode.SUBSCRIBE -> if (isSubscribed) Strings.t("rabbitmq_unsubscribe") else Strings.t("rabbitmq_subscribe")
            RabbitMqOperationMode.RPC -> if (tab.rabbitMqIsExecuting) "Waiting…" else Strings.t("rabbitmq_send_rpc")
        }
        val accentColor = if (isSubscribed) ClapiColors.Error else ClapiColors.MethodRabbitMq
        val actionIcon = when {
            isSubscribed -> Res.drawable.ic_stop
            tab.rabbitMqMode == RabbitMqOperationMode.SUBSCRIBE -> Res.drawable.ic_play_arrow
            else -> Res.drawable.ic_send
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(accentColor.copy(alpha = 0.15f))
                .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .handPointerHover()
                .clickable(enabled = !tab.rabbitMqIsExecuting, onClick = onPrimaryAction)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .testTag("rabbitmq-primary-action"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(actionIcon),
                contentDescription = actionLabel,
                tint = accentColor,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = actionLabel,
                color = accentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        SaveButton(isLoading = tab.rabbitMqIsExecuting, onClick = onSave)
        DocsHelpButton(
            url = DocsLinks.rabbitmq,
            tooltip = "RabbitMQ documentation",
            tag = "rabbitmq-docs-button",
            size = 32.dp,
        )
    }
}

@Composable
internal fun CompactField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ClapiColors.SurfaceContainer)
            .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = ClapiColors.OnSurfaceDim, fontSize = 12.sp, fontFamily = CodeFontFamily)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 12.sp, fontFamily = CodeFontFamily),
            cursorBrush = SolidColor(ClapiColors.Primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
