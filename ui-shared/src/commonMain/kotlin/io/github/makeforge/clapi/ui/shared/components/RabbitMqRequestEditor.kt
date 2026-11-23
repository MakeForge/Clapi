package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.core.model.RabbitMqConnectionStatus
import io.github.makeforge.clapi.core.model.opencollection.AmqpAckMode
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqOperationMode
import io.github.makeforge.clapi.ui.shared.components.executor.ackRabbitMqMessage
import io.github.makeforge.clapi.ui.shared.components.executor.executeRabbitMqPublishTab
import io.github.makeforge.clapi.ui.shared.components.executor.executeRabbitMqRpcTab
import io.github.makeforge.clapi.ui.shared.components.executor.nackRabbitMqMessage
import io.github.makeforge.clapi.ui.shared.components.executor.startRabbitMqSubscriptionTab
import io.github.makeforge.clapi.ui.shared.components.executor.stopRabbitMqSubscriptionTab
import io.github.makeforge.clapi.ui.shared.components.rabbitmq.LabeledCheckbox
import io.github.makeforge.clapi.ui.shared.components.rabbitmq.LabeledField
import io.github.makeforge.clapi.ui.shared.components.rabbitmq.RabbitMqAmqpTab
import io.github.makeforge.clapi.ui.shared.components.rabbitmq.RabbitMqConnectionBar
import io.github.makeforge.clapi.ui.shared.components.rabbitmq.RabbitMqHeadersPropsTab
import io.github.makeforge.clapi.ui.shared.components.rabbitmq.RabbitMqRpcReplyPane
import io.github.makeforge.clapi.ui.shared.components.rabbitmq.RabbitMqTimelinePane
import io.github.makeforge.clapi.ui.shared.components.websocket.WebSocketMessagePane
import io.github.makeforge.clapi.ui.shared.shortcuts.onSaveHotkey
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.RabbitMqEditorTab
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.github.makeforge.clapi.ui.shared.state.ResponseLayout
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily
import kotlinx.serialization.ExperimentalSerializationApi

@OptIn(ExperimentalSerializationApi::class)
@Composable
fun RabbitMqRequestEditor(
    tab: RequestTabState,
    state: AppState,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = state.appScope
    val markDirty = { tab.markDirty() }
    var splitFraction by remember { mutableStateOf(0.55f) }

    val onPrimaryAction: () -> Unit = {
        when (tab.rabbitMqMode) {
            RabbitMqOperationMode.PUBLISH -> executeRabbitMqPublishTab(scope, state, tab)
            RabbitMqOperationMode.RPC -> executeRabbitMqRpcTab(scope, state, tab)
            RabbitMqOperationMode.SUBSCRIBE -> {
                val active = tab.rabbitMqConnectionStatus == RabbitMqConnectionStatus.CONNECTED ||
                    tab.rabbitMqConnectionStatus == RabbitMqConnectionStatus.CONNECTING
                if (active) {
                    stopRabbitMqSubscriptionTab(scope, tab)
                } else {
                    startRabbitMqSubscriptionTab(scope, state, tab)
                }
            }
        }
    }

    // Ensure Subscribe mode never lands on hidden Message or Headers & Props tabs
    LaunchedEffect(tab.rabbitMqMode) {
        if (tab.rabbitMqMode == RabbitMqOperationMode.SUBSCRIBE &&
            (tab.selectedRabbitMqTab == RabbitMqEditorTab.MESSAGE || tab.selectedRabbitMqTab == RabbitMqEditorTab.HEADERS)
        ) {
            tab.selectedRabbitMqTab = RabbitMqEditorTab.AMQP
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClapiColors.Background)
            .onSaveHotkey { onSave() }
            .testTag("rabbitmq-request-editor"),
    ) {
        RabbitMqConnectionBar(
            tab = tab,
            state = state,
            onPrimaryAction = onPrimaryAction,
            onSave = onSave,
        )

        val leftPane: @Composable () -> Unit = {
            RabbitMqLeftPane(
                tab = tab,
                state = state,
                markDirty = markDirty,
                onSend = onPrimaryAction,
            )
        }
        val rightPane: @Composable () -> Unit = {
            if (tab.rabbitMqMode == RabbitMqOperationMode.SUBSCRIBE) {
                RabbitMqTimelinePane(
                    tab = tab,
                    onAck = { tag -> ackRabbitMqMessage(scope, tab, tag) },
                    onNack = { tag, requeue -> nackRabbitMqMessage(scope, tab, tag, requeue) },
                )
            } else {
                RabbitMqRpcReplyPane(tab = tab)
            }
        }

        if (state.settings.responseLayout == ResponseLayout.BOTTOM) {
            VerticalSplitPane(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                splitFraction = splitFraction,
                onSplitChanged = { splitFraction = it },
                first = leftPane,
                second = rightPane,
            )
        } else {
            HorizontalSplitPane(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                splitFraction = splitFraction,
                onSplitChanged = { splitFraction = it },
                hairline = true,
                first = leftPane,
                second = rightPane,
            )
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Composable
private fun RabbitMqLeftPane(
    tab: RequestTabState,
    state: AppState,
    markDirty: () -> Unit,
    onSend: () -> Unit,
) {
    val visibleTabs = remember(tab.rabbitMqMode) {
        if (tab.rabbitMqMode == RabbitMqOperationMode.SUBSCRIBE) {
            RabbitMqEditorTab.entries.filter {
                it != RabbitMqEditorTab.MESSAGE && it != RabbitMqEditorTab.HEADERS
            }
        } else {
            RabbitMqEditorTab.entries.toList()
        }
    }
    val effectiveSelectedTab = if (tab.selectedRabbitMqTab in visibleTabs) {
        tab.selectedRabbitMqTab
    } else {
        visibleTabs.first()
    }
    val selectedIndex = visibleTabs.indexOf(effectiveSelectedTab).coerceAtLeast(0)

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedIndex,
            containerColor = ClapiColors.Surface,
            contentColor = ClapiColors.OnSurface,
            edgePadding = 8.dp,
            divider = {},
            indicator = {},
            modifier = Modifier.fillMaxWidth().height(40.dp),
        ) {
            visibleTabs.forEach { editorTab ->
                val selected = effectiveSelectedTab == editorTab
                val badgeCount = when (editorTab) {
                    RabbitMqEditorTab.HEADERS -> tab.headers.count { it.key.isNotBlank() }
                    RabbitMqEditorTab.MESSAGE -> if (tab.webSocketMessageVariants.isNotEmpty()) tab.webSocketMessageVariants.size else null
                    else -> null
                }
                Tab(
                    selected = selected,
                    onClick = { tab.selectedRabbitMqTab = editorTab },
                    modifier = Modifier.testTag("rabbitmq-tab-${editorTab.name.lowercase()}"),
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
            when (effectiveSelectedTab) {
                RabbitMqEditorTab.MESSAGE -> WebSocketMessagePane(
                    tab = tab,
                    markDirty = markDirty,
                    onSend = onSend,
                )
                RabbitMqEditorTab.AMQP -> RabbitMqAmqpTab(tab = tab)
                RabbitMqEditorTab.HEADERS -> RabbitMqHeadersPropsTab(tab = tab, state = state)
                RabbitMqEditorTab.AUTH -> AuthEditor(
                    tab = tab,
                    state = state,
                    onDirty = markDirty,
                )
                RabbitMqEditorTab.SCRIPTS -> if (tab.rabbitMqMode == RabbitMqOperationMode.SUBSCRIBE) {
                    ScriptEditor(
                        script = tab.testScript,
                        onScriptChanged = {
                            tab.testScript = it
                            markDirty()
                        },
                        title = "On-Message Script (runs on every received message)",
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            ScriptEditor(
                                script = tab.preRequestScript,
                                onScriptChanged = {
                                    tab.preRequestScript = it
                                    markDirty()
                                },
                                title = "Pre-request Script",
                            )
                        }
                        HorizontalDivider(color = ClapiColors.Border)
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            ScriptEditor(
                                script = tab.testScript,
                                onScriptChanged = {
                                    tab.testScript = it
                                    markDirty()
                                },
                                title = "Post-response / Test Script",
                            )
                        }
                    }
                }
                RabbitMqEditorTab.SETTINGS -> RabbitMqSettingsTab(tab = tab)
                RabbitMqEditorTab.DOCS -> RabbitMqDocsTab(tab = tab)
            }
        }
    }
}

@Composable
private fun RabbitMqSettingsTab(tab: RequestTabState) {
    val s = tab.rabbitMqSettings
    var ackMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Connection & Reliability", color = ClapiColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            LabeledField(
                label = "Connection Timeout (ms)",
                value = s.connectionTimeoutMs.toString(),
                placeholder = "10000",
                onValueChange = { raw ->
                    raw.toLongOrNull()?.let {
                        tab.rabbitMqSettings = s.copy(connectionTimeoutMs = it)
                        tab.markDirty()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            LabeledField(
                label = "Heartbeat (seconds)",
                value = s.heartbeatSeconds.toString(),
                placeholder = "30",
                onValueChange = { raw ->
                    raw.toIntOrNull()?.let {
                        tab.rabbitMqSettings = s.copy(heartbeatSeconds = it)
                        tab.markDirty()
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }

        if (tab.rabbitMqMode == RabbitMqOperationMode.PUBLISH) {
            LabeledCheckbox(
                label = "Enable Publisher Confirms",
                checked = s.publisherConfirms,
                onCheckedChange = { tab.rabbitMqSettings = s.copy(publisherConfirms = it); tab.markDirty() },
            )
            LabeledCheckbox(
                label = "Mandatory Delivery (capture unroutable returns)",
                checked = s.mandatory,
                onCheckedChange = { tab.rabbitMqSettings = s.copy(mandatory = it); tab.markDirty() },
            )
        }
        LabeledCheckbox(
            label = "Auto-Declare Exchange / Queue / Binding",
            checked = s.declareTopology,
            onCheckedChange = { tab.rabbitMqSettings = s.copy(declareTopology = it); tab.markDirty() },
        )
        LabeledCheckbox(
            label = "Use TLS Client Certificate (EXTERNAL SASL)",
            checked = s.useTlsClientCert,
            onCheckedChange = { tab.rabbitMqSettings = s.copy(useTlsClientCert = it); tab.markDirty() },
        )

        if (tab.rabbitMqMode == RabbitMqOperationMode.SUBSCRIBE) {
            HorizontalDivider(color = ClapiColors.Border)
            Text("Subscribe Settings", color = ClapiColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Ack Mode", color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ClapiColors.SurfaceContainer)
                                .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                                .handPointerHover()
                                .clickable { ackMenuOpen = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(s.ackMode.value, color = ClapiColors.OnSurface, fontSize = 12.sp)
                            Text("▾", color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
                        }
                        DropdownMenu(expanded = ackMenuOpen, onDismissRequest = { ackMenuOpen = false }) {
                            AmqpAckMode.entries.forEach { mode ->
                                DenseDropdownMenuItem(
                                    text = { Text(mode.value) },
                                    onClick = {
                                        tab.rabbitMqSettings = s.copy(ackMode = mode)
                                        tab.markDirty()
                                        ackMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
                LabeledField(
                    label = "Prefetch Count",
                    value = s.prefetchCount.toString(),
                    placeholder = "50",
                    onValueChange = { raw ->
                        raw.toIntOrNull()?.let {
                            tab.rabbitMqSettings = s.copy(prefetchCount = it)
                            tab.markDirty()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (tab.rabbitMqMode == RabbitMqOperationMode.RPC) {
            HorizontalDivider(color = ClapiColors.Border)
            Text("RPC Settings", color = ClapiColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            LabeledField(
                label = "RPC Timeout (ms)",
                value = s.rpcTimeoutMs.toString(),
                placeholder = "15000",
                onValueChange = { raw ->
                    raw.toLongOrNull()?.let {
                        tab.rabbitMqSettings = s.copy(rpcTimeoutMs = it)
                        tab.markDirty()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RabbitMqDocsTab(tab: RequestTabState) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (tab.rabbitMqDocs.isEmpty()) {
            Text("Markdown documentation for this RabbitMQ request...", color = ClapiColors.OnSurfaceDim, fontSize = 12.sp)
        }
        BasicTextField(
            value = tab.rabbitMqDocs,
            onValueChange = {
                tab.rabbitMqDocs = it
                tab.markDirty()
            },
            textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 12.sp, fontFamily = CodeFontFamily),
            cursorBrush = SolidColor(ClapiColors.Primary),
            modifier = Modifier.fillMaxSize(),
        )
    }
}
