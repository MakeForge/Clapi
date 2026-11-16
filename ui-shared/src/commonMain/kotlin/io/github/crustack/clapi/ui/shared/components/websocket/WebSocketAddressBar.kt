package io.github.crustack.clapi.ui.shared.components.websocket

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.crustack.clapi.core.network.ConnectionState
import io.github.crustack.clapi.ui.shared.components.SaveButton
import io.github.crustack.clapi.ui.shared.components.VariableAwareTextField
import io.github.crustack.clapi.ui.shared.components.WebSocketBadge
import io.github.crustack.clapi.ui.shared.generated.resources.Res
import io.github.crustack.clapi.ui.shared.generated.resources.ic_play_arrow
import io.github.crustack.clapi.ui.shared.generated.resources.ic_stop
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import io.github.crustack.clapi.ui.shared.theme.ClapiColors
import io.github.crustack.clapi.ui.shared.theme.CodeFontFamily
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun WebSocketAddressBar(
    tab: RequestTabState,
    state: AppState,
    onUrlChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClapiColors.Surface)
            .border(width = 1.dp, color = ClapiColors.Border)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WebSocketBadge()

        // URL input with variable token resolution
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(ClapiColors.SurfaceContainer)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            VariableAwareTextField(
                value = tab.url,
                onValueChange = onUrlChanged,
                placeholder = "ws:// or wss:// URL…",
                textStyle = TextStyle(
                    color = ClapiColors.OnSurface,
                    fontSize = 14.sp,
                    fontFamily = CodeFontFamily,
                ),
                state = state,
                tab = tab,
                undoStack = tab.urlUndoStack,
                modifier = Modifier.fillMaxWidth().testTag("websocket-url-input"),
            )
        }

        // Connection State Pill
        WebSocketStatusPill(state = tab.webSocketConnectionState)

        // Connect / Disconnect Action Button
        val isConnectedOrConnecting = tab.webSocketConnectionState == ConnectionState.CONNECTED ||
                tab.webSocketConnectionState == ConnectionState.CONNECTING

        if (isConnectedOrConnecting) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ClapiColors.Error.copy(alpha = 0.15f))
                    .border(1.dp, ClapiColors.Error.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clickable { onDisconnect() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("websocket-disconnect-button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_stop),
                    contentDescription = "Disconnect",
                    tint = ClapiColors.Error,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = Strings.t("websocket_disconnect"),
                    color = ClapiColors.Error,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ClapiColors.MethodWebSocket.copy(alpha = 0.15f))
                    .border(1.dp, ClapiColors.MethodWebSocket.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clickable { onConnect() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("websocket-connect-button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_play_arrow),
                    contentDescription = "Connect",
                    tint = ClapiColors.MethodWebSocket,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = Strings.t("websocket_connect"),
                    color = ClapiColors.MethodWebSocket,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Save Button
        SaveButton(isLoading = false, onClick = onSave)
    }
}

@Composable
internal fun WebSocketStatusPill(state: ConnectionState) {
    val (statusLabel, statusColor) = when (state) {
        ConnectionState.CONNECTED -> Strings.connected to ClapiColors.StatusSuccess
        ConnectionState.CONNECTING -> Strings.connecting to ClapiColors.StatusWarning
        ConnectionState.DISCONNECTED -> Strings.disconnected to ClapiColors.OnSurfaceDim
        ConnectionState.ERROR -> Strings.t("error") to ClapiColors.Error
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(statusColor.copy(alpha = 0.10f))
            .border(1.dp, statusColor.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Text(
            text = statusLabel,
            color = statusColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
