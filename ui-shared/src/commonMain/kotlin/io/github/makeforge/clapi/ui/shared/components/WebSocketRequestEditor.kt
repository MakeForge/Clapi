package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import io.github.makeforge.clapi.core.model.MessageDirection
import io.github.makeforge.clapi.core.model.WebSocketLogMessage
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessage
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessageType
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessageVariant
import io.github.makeforge.clapi.core.network.ConnectionState
import io.github.makeforge.clapi.core.network.websocket.WebSocketClientManager
import io.github.makeforge.clapi.ui.shared.components.executor.connectWebSocketTab
import io.github.makeforge.clapi.ui.shared.components.executor.disconnectWebSocketTab
import io.github.makeforge.clapi.ui.shared.components.executor.sendWebSocketMessage
import io.github.makeforge.clapi.ui.shared.components.websocket.WebSocketAddressBar
import io.github.makeforge.clapi.ui.shared.components.websocket.WebSocketMessagePane
import io.github.makeforge.clapi.ui.shared.components.websocket.WebSocketTimelinePane
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_add
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_arrow_drop_down
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_clear
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_content_copy
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_delete
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_edit
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_play_arrow
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_send
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_stop
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.platform.PlatformColumnVerticalScrollbar
import io.github.makeforge.clapi.ui.shared.platform.PlatformLazyVerticalScrollbar
import io.github.makeforge.clapi.ui.shared.platform.copyToClipboard
import io.github.makeforge.clapi.ui.shared.platform.formatTimestamp
import io.github.makeforge.clapi.ui.shared.shortcuts.AppHotkeys
import io.github.makeforge.clapi.ui.shared.shortcuts.hotkeys
import io.github.makeforge.clapi.ui.shared.shortcuts.onSaveHotkey
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.github.makeforge.clapi.ui.shared.state.WebSocketEditorTab
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily
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
                tab.appendWebSocketLogMessage(msg)
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

// ── Settings Pane ───────────────────────────────────────────────────

@Composable
private fun WebSocketSettingsPane(
    tab: RequestTabState,
    markDirty: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
                .padding(end = 12.dp),
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
        PlatformColumnVerticalScrollbar(
            scrollState = scrollState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
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


