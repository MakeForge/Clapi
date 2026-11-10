package io.github.crustack.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.crustack.clapi.core.model.MessageDirection
import io.github.crustack.clapi.core.model.WebSocketLogMessage
import io.github.crustack.clapi.core.model.opencollection.WebSocketMessage
import io.github.crustack.clapi.core.model.opencollection.WebSocketMessageType
import io.github.crustack.clapi.core.model.opencollection.WebSocketMessageVariant
import io.github.crustack.clapi.core.network.ConnectionState
import io.github.crustack.clapi.core.network.websocket.WebSocketClientManager
import io.github.crustack.clapi.ui.shared.generated.resources.Res
import io.github.crustack.clapi.ui.shared.generated.resources.ic_add
import io.github.crustack.clapi.ui.shared.generated.resources.ic_arrow_drop_down
import io.github.crustack.clapi.ui.shared.generated.resources.ic_clear
import io.github.crustack.clapi.ui.shared.generated.resources.ic_content_copy
import io.github.crustack.clapi.ui.shared.generated.resources.ic_delete
import io.github.crustack.clapi.ui.shared.generated.resources.ic_edit
import io.github.crustack.clapi.ui.shared.generated.resources.ic_play_arrow
import io.github.crustack.clapi.ui.shared.generated.resources.ic_save
import io.github.crustack.clapi.ui.shared.generated.resources.ic_send
import io.github.crustack.clapi.ui.shared.generated.resources.ic_stop
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.platform.copyToClipboard
import io.github.crustack.clapi.ui.shared.platform.formatTimestamp
import io.github.crustack.clapi.ui.shared.shortcuts.AppHotkeys
import io.github.crustack.clapi.ui.shared.shortcuts.hotkeys
import io.github.crustack.clapi.ui.shared.shortcuts.onSaveHotkey
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import io.github.crustack.clapi.ui.shared.state.WebSocketEditorTab
import io.github.crustack.clapi.ui.shared.theme.ClapiColors
import io.github.crustack.clapi.ui.shared.theme.CodeFontFamily
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.compose.resources.painterResource

fun WebSocketMessageType.toSyntaxLanguage(): SyntaxLanguage = when (this) {
    WebSocketMessageType.JSON -> SyntaxLanguage.JSON
    WebSocketMessageType.XML -> SyntaxLanguage.XML
    WebSocketMessageType.TEXT, WebSocketMessageType.BINARY -> SyntaxLanguage.PLAIN
}

/**
 * Top-level editor for WebSocket requests (`RequestKind.WEBSOCKET`).
 *
 * Provides:
 * - Address bar with [WebSocketBadge], URL variable input, status indicator, Connect/Disconnect, and Save buttons.
 * - Split pane: Left pane for Configuration (Message/Variants, Headers, Auth, Settings, Scripts, Variables, Docs),
 *   Right pane for real-time bidirectional Message Timeline.
 */
@OptIn(ExperimentalSerializationApi::class)
@Composable
fun WebSocketRequestEditor(
    tab: RequestTabState,
    state: AppState,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val markDirty = { tab.markDirty() }
    var splitFraction by remember { mutableStateOf(0.55f) }

    // Collect connection state and log messages from WebSocketClientManager
    LaunchedEffect(tab.id) {
        launch {
            WebSocketClientManager.default.connectionState(tab.id).collect { connState ->
                tab.webSocketConnectionState = connState
            }
        }
        launch {
            WebSocketClientManager.default.messages(tab.id).collect { msg ->
                if (tab.webSocketLogMessages.size >= 1000) {
                    tab.webSocketLogMessages.removeAt(0)
                }
                tab.webSocketLogMessages.add(msg)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClapiColors.Background)
            .onSaveHotkey { onSave() }
            .testTag("websocket-request-editor"),
    ) {
        // ── Top Address Bar ──────────────────────────────────────────
        WebSocketAddressBar(
            tab = tab,
            state = state,
            onUrlChanged = { newUrl ->
                tab.url = newUrl
                markDirty()
            },
            onConnect = { connectWebSocketTab(scope, state, tab) },
            onDisconnect = { disconnectWebSocketTab(tab) },
            onSave = onSave,
        )

        // ── Split Pane (Left = Configuration, Right = Timeline) ─────
        HorizontalSplitPane(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            splitFraction = splitFraction,
            onSplitChanged = { splitFraction = it },
            hairline = true,
            first = {
                WebSocketLeftPane(
                    tab = tab,
                    state = state,
                    markDirty = markDirty,
                    onSend = { sendWebSocketMessage(scope, state, tab) },
                )
            },
            second = {
                WebSocketTimelinePane(
                    messages = tab.webSocketLogMessages,
                    onClear = { tab.webSocketLogMessages.clear() },
                )
            },
        )
    }
}

// ── Top Address Bar ─────────────────────────────────────────────────

@Composable
private fun WebSocketAddressBar(
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
private fun WebSocketStatusPill(state: ConnectionState) {
    val (statusLabel, statusColor) = when (state) {
        ConnectionState.CONNECTED -> "Connected" to Color(0xFF4CAF50)
        ConnectionState.CONNECTING -> "Connecting…" to Color(0xFFFFA726)
        ConnectionState.DISCONNECTED -> "Disconnected" to ClapiColors.OnSurfaceDim
        ConnectionState.ERROR -> "Error" to ClapiColors.Error
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

// ── Left Configuration Pane ─────────────────────────────────────────

@OptIn(ExperimentalSerializationApi::class)
@Composable
private fun WebSocketLeftPane(
    tab: RequestTabState,
    state: AppState,
    markDirty: () -> Unit,
    onSend: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = tab.selectedWebSocketTab.ordinal,
            containerColor = ClapiColors.Surface,
            contentColor = ClapiColors.OnSurface,
            edgePadding = 8.dp,
            divider = {},
            modifier = Modifier.fillMaxWidth().height(40.dp),
        ) {
            WebSocketEditorTab.entries.forEach { editorTab ->
                val selected = tab.selectedWebSocketTab == editorTab
                val badgeCount = when (editorTab) {
                    WebSocketEditorTab.HEADERS -> tab.headers.count { it.key.isNotBlank() }
                    WebSocketEditorTab.VARIABLES -> tab.variables.count { it.key.isNotBlank() }
                    WebSocketEditorTab.MESSAGE -> if (tab.webSocketMessageVariants.isNotEmpty()) tab.webSocketMessageVariants.size else null
                    else -> null
                }
                Tab(
                    selected = selected,
                    onClick = { tab.selectedWebSocketTab = editorTab },
                    modifier = Modifier.testTag("websocket-tab-${editorTab.name.lowercase()}"),
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = editorTab.label,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) ClapiColors.Primary else ClapiColors.OnSurfaceVariant,
                            )
                            if (badgeCount != null && badgeCount > 0) {
                                Text(
                                    text = "$badgeCount",
                                    fontSize = 10.sp,
                                    color = if (selected) ClapiColors.Primary else ClapiColors.OnSurfaceDim,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ClapiColors.SurfaceContainer)
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    },
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
            when (tab.selectedWebSocketTab) {
                WebSocketEditorTab.MESSAGE -> WebSocketMessagePane(
                    tab = tab,
                    markDirty = markDirty,
                    onSend = onSend,
                )
                WebSocketEditorTab.HEADERS -> KeyValueEditor(
                    entries = tab.headers,
                    tag = "headers",
                    state = state,
                    onDirty = markDirty,
                )
                WebSocketEditorTab.AUTH -> AuthEditor(
                    tab = tab,
                    state = state,
                    onDirty = markDirty,
                )
                WebSocketEditorTab.SETTINGS -> WebSocketSettingsPane(
                    tab = tab,
                    markDirty = markDirty,
                )
                WebSocketEditorTab.SCRIPTS -> ScriptEditor(
                    script = tab.preRequestScript,
                    onScriptChanged = {
                        tab.preRequestScript = it
                        markDirty()
                    },
                    title = "Pre-request Handshake Script",
                )
                WebSocketEditorTab.VARIABLES -> KeyValueEditor(
                    entries = tab.variables,
                    tag = "variables",
                    state = state,
                    onDirty = markDirty,
                )
                WebSocketEditorTab.DOCS -> WebSocketDocsPane(
                    tab = tab,
                    markDirty = markDirty,
                )
            }
        }
    }
}

// ── Outgoing Message & Variants Pane ────────────────────────────────

@OptIn(ExperimentalSerializationApi::class)
@Composable
private fun WebSocketMessagePane(
    tab: RequestTabState,
    markDirty: () -> Unit,
    onSend: () -> Unit,
) {
    var renamingVariantIndex by remember { mutableStateOf<Int?>(null) }

    if (renamingVariantIndex != null) {
        val idx = renamingVariantIndex!!
        val currentTitle = tab.webSocketMessageVariants.getOrNull(idx)?.title.orEmpty()
        var newTitle by remember(idx) { mutableStateOf(currentTitle) }

        RenameVariantDialog(
            title = "Rename Message Variant",
            value = newTitle,
            onValueChange = { newTitle = it },
            onDismiss = { renamingVariantIndex = null },
            onConfirm = {
                if (idx in tab.webSocketMessageVariants.indices && newTitle.isNotBlank()) {
                    val variant = tab.webSocketMessageVariants[idx]
                    tab.webSocketMessageVariants[idx] = variant.copy(title = newTitle)
                    markDirty()
                }
                renamingVariantIndex = null
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Variants Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tab.webSocketMessageVariants.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ClapiColors.SurfaceContainer)
                            .clickable {
                                tab.webSocketMessageVariants.add(
                                    WebSocketMessageVariant(
                                        title = "Default",
                                        selected = true,
                                        message = WebSocketMessage(
                                            type = tab.webSocketMessageFormat,
                                            data = tab.webSocketOutgoingPayload,
                                        ),
                                    )
                                )
                                tab.activeWebSocketVariantIndex = 0
                                markDirty()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("websocket-add-variant"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_add),
                            contentDescription = "Add Variant",
                            tint = ClapiColors.Primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "Add Variant",
                            fontSize = 11.sp,
                            color = ClapiColors.Primary,
                        )
                    }
                } else {
                    tab.webSocketMessageVariants.forEachIndexed { index, variant ->
                        val isActive = index == tab.activeWebSocketVariantIndex
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isActive) ClapiColors.Primary.copy(alpha = 0.15f) else ClapiColors.SurfaceContainer)
                                .border(
                                    1.dp,
                                    if (isActive) ClapiColors.Primary.copy(alpha = 0.5f) else ClapiColors.Border,
                                    RoundedCornerShape(6.dp),
                                )
                                .clickable {
                                    tab.activeWebSocketVariantIndex = index
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = variant.title,
                                fontSize = 11.sp,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isActive) ClapiColors.Primary else ClapiColors.OnSurface,
                            )

                            // Edit icon
                            Icon(
                                painter = painterResource(Res.drawable.ic_edit),
                                contentDescription = "Rename",
                                tint = ClapiColors.OnSurfaceDim,
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable { renamingVariantIndex = index },
                            )

                            // Delete icon (if more than 1 variant)
                            if (tab.webSocketMessageVariants.size > 1) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_delete),
                                    contentDescription = "Delete",
                                    tint = ClapiColors.OnSurfaceDim,
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clickable {
                                            tab.webSocketMessageVariants.removeAt(index)
                                            if (tab.activeWebSocketVariantIndex >= tab.webSocketMessageVariants.size) {
                                                tab.activeWebSocketVariantIndex = tab.webSocketMessageVariants.size - 1
                                            }
                                            markDirty()
                                        },
                                )
                            }
                        }
                    }

                    // Plus button to add another variant
                    Icon(
                        painter = painterResource(Res.drawable.ic_add),
                        contentDescription = "Add Variant",
                        tint = ClapiColors.Primary,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                val nextNumber = tab.webSocketMessageVariants.size + 1
                                tab.webSocketMessageVariants.add(
                                    WebSocketMessageVariant(
                                        title = "Variant $nextNumber",
                                        selected = false,
                                        message = WebSocketMessage(
                                            type = tab.effectiveWebSocketFormat,
                                            data = "",
                                        ),
                                    )
                                )
                                tab.activeWebSocketVariantIndex = tab.webSocketMessageVariants.size - 1
                                markDirty()
                            }
                            .testTag("websocket-add-variant"),
                    )
                }
            }

            // Message Format Selector and Send Button
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FormatDropdown(
                    currentFormat = tab.effectiveWebSocketFormat,
                    onFormatSelected = { format ->
                        tab.effectiveWebSocketFormat = format
                        markDirty()
                    },
                )

                // Send Button
                val isConnected = tab.webSocketConnectionState == ConnectionState.CONNECTED
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isConnected) ClapiColors.Primary else ClapiColors.Primary.copy(alpha = 0.35f))
                        .clickable(enabled = isConnected) { onSend() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("websocket-send-button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_send),
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = Strings.t("websocket_send"),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Code Editor for Active Payload
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp)),
        ) {
            CodeEditor(
                text = tab.webSocketOutgoingMessage,
                onTextChange = {
                    tab.webSocketOutgoingMessage = it
                    markDirty()
                },
                language = tab.effectiveWebSocketFormat.toSyntaxLanguage(),
                modifier = Modifier.fillMaxSize(),
                placeholder = "Compose outgoing message payload…",
                testTagPrefix = "websocket-message-editor",
            )
        }
    }
}

@Composable
private fun FormatDropdown(
    currentFormat: WebSocketMessageType,
    onFormatSelected: (WebSocketMessageType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(ClapiColors.SurfaceContainer)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("websocket-format-selector"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = currentFormat.name,
                color = ClapiColors.OnSurface,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_drop_down),
                contentDescription = null,
                tint = ClapiColors.OnSurfaceDim,
                modifier = Modifier.size(14.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            WebSocketMessageType.entries.forEach { format ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = format.name,
                            fontSize = 12.sp,
                            fontWeight = if (format == currentFormat) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onFormatSelected(format)
                        expanded = false
                    },
                )
            }
        }
    }
}

// ── Settings Pane ───────────────────────────────────────────────────

@Composable
private fun WebSocketSettingsPane(
    tab: RequestTabState,
    markDirty: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "WebSocket Connection Settings",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = ClapiColors.OnSurface,
        )

        // Timeout
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Connection Timeout (ms or 'inherit')",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = ClapiColors.OnSurfaceVariant,
            )
            BasicTextField(
                value = tab.webSocketTimeout ?: "inherit",
                onValueChange = {
                    tab.webSocketTimeout = it
                    markDirty()
                },
                singleLine = true,
                textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
                cursorBrush = SolidColor(ClapiColors.Primary),
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ClapiColors.SurfaceContainer)
                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("websocket-settings-timeout"),
            )
        }

        // Keep-Alive Ping Interval
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Keep-Alive Ping Interval (ms or 'inherit')",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = ClapiColors.OnSurfaceVariant,
            )
            BasicTextField(
                value = tab.webSocketKeepAliveInterval ?: "inherit",
                onValueChange = {
                    tab.webSocketKeepAliveInterval = it
                    markDirty()
                },
                singleLine = true,
                textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
                cursorBrush = SolidColor(ClapiColors.Primary),
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ClapiColors.SurfaceContainer)
                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("websocket-settings-keepalive"),
            )
        }
    }
}

// ── Docs Pane ───────────────────────────────────────────────────────

@OptIn(ExperimentalSerializationApi::class)
@Composable
private fun WebSocketDocsPane(
    tab: RequestTabState,
    markDirty: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text(
            text = "Request Documentation (Markdown)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = ClapiColors.OnSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp)),
        ) {
            CodeEditor(
                text = tab.webSocketDocs,
                onTextChange = {
                    tab.webSocketDocs = it
                    markDirty()
                },
                language = SyntaxLanguage.PLAIN,
                modifier = Modifier.fillMaxSize(),
                placeholder = "Add documentation for this WebSocket request…",
                testTagPrefix = "websocket-docs-editor",
            )
        }
    }
}

// ── Right Timeline Pane ─────────────────────────────────────────────

@Composable
private fun WebSocketTimelinePane(
    messages: List<WebSocketLogMessage>,
    onClear: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredMessages = remember(messages.toList(), searchQuery) {
        if (searchQuery.isBlank()) {
            messages.toList()
        } else {
            messages.filter { it.payload.contains(searchQuery, ignoreCase = true) }
        }
    }

    val listState = rememberLazyListState()

    // Auto-scroll to latest item on new incoming/outgoing message
    LaunchedEffect(filteredMessages.size) {
        if (filteredMessages.isNotEmpty()) {
            listState.animateScrollToItem(filteredMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ClapiColors.Surface)
            .testTag("websocket-timeline"),
    ) {
        // Timeline Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ClapiColors.SurfaceContainer)
                .border(1.dp, ClapiColors.Border)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Timeline",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ClapiColors.OnSurface,
                )
                Text(
                    text = "${messages.size} msgs",
                    fontSize = 10.sp,
                    color = ClapiColors.OnSurfaceDim,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ClapiColors.Surface)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Search filter
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 11.sp, fontFamily = CodeFontFamily),
                    cursorBrush = SolidColor(ClapiColors.Primary),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(ClapiColors.Surface)
                                .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text("Filter…", color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
                            }
                            innerTextField()
                        }
                    },
                )

                // Clear button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onClear() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                        .testTag("websocket-clear-log"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_clear),
                        contentDescription = "Clear Log",
                        tint = ClapiColors.OnSurfaceDim,
                        modifier = Modifier.size(13.dp),
                    )
                    Text("Clear", color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
                }
            }
        }

        // Messages List or Empty State
        if (filteredMessages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (messages.isEmpty()) {
                        "No messages yet. Connect and send messages to view the timeline."
                    } else {
                        "No messages matching filter."
                    },
                    color = ClapiColors.OnSurfaceDim,
                    fontSize = 12.sp,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filteredMessages, key = { it.id }) { msg ->
                    TimelineMessageItem(msg)
                }
            }
        }
    }
}

@Composable
private fun TimelineMessageItem(msg: WebSocketLogMessage) {
    val isSent = msg.direction == MessageDirection.SENT
    val directionColor = if (isSent) Color(0xFF4EC9B0) else Color(0xFF7B8DEF)
    val directionLabel = if (isSent) "↑ SENT" else "↓ RECEIVED"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(ClapiColors.SurfaceContainer)
            .border(0.5.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
            .padding(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Direction Badge
                Text(
                    text = directionLabel,
                    color = directionColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(directionColor.copy(alpha = 0.12f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )

                // Format Badge
                Text(
                    text = msg.format.uppercase(),
                    color = ClapiColors.OnSurfaceDim,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(ClapiColors.Surface)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )

                // Byte size
                Text(
                    text = "${msg.byteLength} B",
                    color = ClapiColors.OnSurfaceDim,
                    fontSize = 9.sp,
                    fontFamily = CodeFontFamily,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Timestamp
                Text(
                    text = formatTimestamp(msg.timestamp),
                    color = ClapiColors.OnSurfaceDim,
                    fontSize = 10.sp,
                    fontFamily = CodeFontFamily,
                )

                // Copy button
                Icon(
                    painter = painterResource(Res.drawable.ic_content_copy),
                    contentDescription = "Copy message payload",
                    tint = ClapiColors.OnSurfaceDim,
                    modifier = Modifier
                        .size(13.dp)
                        .clickable { copyToClipboard(msg.payload) },
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Payload content preview
        Text(
            text = msg.payload,
            color = ClapiColors.OnSurface,
            fontSize = 11.sp,
            fontFamily = CodeFontFamily,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        )
    }
}

// ── Rename Variant Dialog ───────────────────────────────────────────

@Composable
private fun RenameVariantDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 480.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ClapiColors.Surface)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(12.dp))
                .hotkeys {
                    on(AppHotkeys.Save) { onConfirm() }
                    on(AppHotkeys.Escape) { onDismiss() }
                }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                color = ClapiColors.OnSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
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
                    .testTag("rename-variant-dialog-input"),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                Text(
                    text = Strings.cancel,
                    color = ClapiColors.OnSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Text(
                    text = Strings.save,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ClapiColors.Primary)
                        .clickable { onConfirm() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
