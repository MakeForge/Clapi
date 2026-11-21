package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.core.model.ResponseDefinition
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_close
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_close_fullscreen
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_error_outline
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_more_vert
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_open_in_full
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_open_in_new
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.platform.PlatformColumnVerticalScrollbar
import io.github.makeforge.clapi.ui.shared.platform.PlatformLazyVerticalScrollbar
import io.github.makeforge.clapi.ui.shared.platform.saveFileForExport
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.github.makeforge.clapi.ui.shared.state.ResponseTab
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily
import io.github.makeforge.clapi.ui.shared.theme.statusCodeColor
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalSerializationApi::class)
@Composable
fun ResponseViewer(
    tab: RequestTabState,
    modifier: Modifier = Modifier,
    showCursorPosition: Boolean = true,
    state: AppState? = null,
    onCollapse: (() -> Unit)? = null,
    onDetach: (() -> Unit)? = null,
    onReattach: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    isDetached: Boolean = false,
) {
    val actualOnCollapse = onCollapse ?: state?.let { { it.collapseResponse() } }
    val actualOnDetach = onDetach ?: state?.let { { it.detachResponse() } }
    val actualOnReattach = onReattach ?: state?.let { { it.reattachResponse() } }
    val actualOnClose = onClose ?: state?.let { { it.closeDetachedResponse() } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClapiColors.Background)
            .testTag("response-viewer"),
    ) {
        val response = tab.response

        if (response == null) {
            EmptyResponseHeader(
                isDetached = isDetached,
                onCollapse = actualOnCollapse,
                onDetach = actualOnDetach,
                onReattach = actualOnReattach,
                onClose = actualOnClose,
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                EmptyResponseState(tab)
            }
        } else {
            // ── Status bar ──────────────────────────────────────
            ResponseStatusBar(
                response = response,
                isDetached = isDetached,
                onCollapse = actualOnCollapse,
                onDetach = actualOnDetach,
                onReattach = actualOnReattach,
                onClose = actualOnClose,
            )

            if (!tab.lastError.isNullOrBlank()) {
                SelectionContainer {
                    Text(
                        text = tab.lastError!!,
                        color = ClapiColors.Error,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ClapiColors.Error.copy(alpha = 0.08f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("response-error-message"),
                    )
                }
            }

            // ── Tabs ────────────────────────────────────────────
            val visibleTabs = responseTabsFor(tab)
            val selectedTab = if (tab.responseTab in visibleTabs) tab.responseTab else ResponseTab.BODY
            ResponseTabBar(visibleTabs, selectedTab, onTabSelected = { tab.responseTab = it })

            // ── Content ─────────────────────────────────────────
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    ResponseTab.BODY    -> ResponseBodyView(response, showCursorPosition)
                    ResponseTab.HEADERS -> ResponseHeadersView(response)
                    ResponseTab.COOKIES -> ResponseCookiesView(response)
                    ResponseTab.TIMING  -> ResponseTimingView(response)
                    ResponseTab.RAW     -> ResponseRawView(response)
                }
            }
        }
    }
}

// ── Empty State ─────────────────────────────────────────────────

@Composable
private fun EmptyResponseState(tab: RequestTabState) {
    val error = tab.lastError
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                tab.isLoading -> {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = ClapiColors.Primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(Strings.sendingRequest, color = ClapiColors.OnSurfaceDim, fontSize = 13.sp)
                    if (tab.liveStreamText.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        SelectionContainer {
                            Text(
                                tab.liveStreamText,
                                color = ClapiColors.OnSurface,
                                fontSize = 13.sp,
                                fontFamily = CodeFontFamily,
                                modifier = Modifier
                                    .padding(horizontal = 24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ClapiColors.SurfaceContainer)
                                    .padding(12.dp)
                                    .testTag("response-live-stream"),
                            )
                        }
                    }
                }
                error != null -> {
                    Icon(painter = painterResource(Res.drawable.ic_error_outline),
                        contentDescription = null,
                        tint = ClapiColors.Error,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(Strings.requestFailed, color = ClapiColors.Error, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    SelectionContainer {
                        Text(
                            error,
                            color = ClapiColors.OnSurfaceDim,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .padding(horizontal = 32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(ClapiColors.Error.copy(alpha = 0.08f))
                                .padding(12.dp)
                                .testTag("response-error-message"),
                        )
                    }
                }
                else -> {
                    Text(Strings.response, color = ClapiColors.OnSurfaceDim, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        Strings.sendToSeeResponse,
                        color = ClapiColors.OnSurfaceDim,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

// ── Status Bar & Empty Header ───────────────────────────────────

@Composable
private fun EmptyResponseHeader(
    isDetached: Boolean,
    onCollapse: (() -> Unit)?,
    onDetach: (() -> Unit)?,
    onReattach: (() -> Unit)?,
    onClose: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClapiColors.Surface)
            .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
            .testTag("response-empty-header"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))

        ResponseMoreActionsButton(
            isDetached = isDetached,
            onCollapse = onCollapse,
            onDetach = onDetach,
            onReattach = onReattach,
            onClose = onClose,
        )
    }

    Box(Modifier.fillMaxWidth().height(1.dp).background(ClapiColors.Border))
}

@Composable
private fun ResponseStatusBar(
    response: ResponseDefinition,
    isDetached: Boolean,
    onCollapse: (() -> Unit)?,
    onDetach: (() -> Unit)?,
    onReattach: (() -> Unit)?,
    onClose: (() -> Unit)?,
) {
    SelectionContainer {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ClapiColors.Surface)
                .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                .testTag("response-status-bar"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Status code badge
            val statusColor = statusCodeColor(response.statusCode)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${response.statusCode}",
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
                Text(
                    text = response.statusText,
                    color = statusColor,
                    fontSize = 13.sp,
                )
            }

            // Response time
            MetricChip("${response.metrics.responseTimeMs} ms", ClapiColors.Secondary)

            // Response size
            MetricChip(formatBytes(response.metrics.responseSizeBytes), ClapiColors.Tertiary)

            Spacer(Modifier.weight(1f))

            DisableSelection {
                ResponseMoreActionsButton(
                    isDetached = isDetached,
                    onCollapse = onCollapse,
                    onDetach = onDetach,
                    onReattach = onReattach,
                    onClose = onClose,
                )
            }
        }
    }

    Box(Modifier.fillMaxWidth().height(1.dp).background(ClapiColors.Border))
}

@Composable
private fun ResponseMoreActionsButton(
    isDetached: Boolean,
    onCollapse: (() -> Unit)?,
    onDetach: (() -> Unit)?,
    onReattach: (() -> Unit)?,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButtonWithTooltip(
            tooltip = "Response options",
            tooltipTag = "response-more-options-tooltip",
            onClick = { expanded = !expanded },
            modifier = Modifier
                .size(28.dp)
                .testTag("response-more-options-button"),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_more_vert),
                contentDescription = "Response options",
                tint = ClapiColors.OnSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(ClapiColors.SurfaceContainer)
                .testTag("response-more-options-menu"),
        ) {
            if (!isDetached) {
                DropdownMenuItem(
                    text = { Text(Strings.collapse, fontSize = 13.sp, color = ClapiColors.OnSurface) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_close_fullscreen),
                            contentDescription = Strings.collapse,
                            modifier = Modifier.size(16.dp),
                            tint = ClapiColors.OnSurfaceVariant,
                        )
                    },
                    onClick = {
                        expanded = false
                        onCollapse?.invoke()
                    },
                    modifier = Modifier.testTag("response-menu-collapse"),
                )
                DropdownMenuItem(
                    text = { Text(Strings.detach, fontSize = 13.sp, color = ClapiColors.OnSurface) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_open_in_new),
                            contentDescription = Strings.detach,
                            modifier = Modifier.size(16.dp),
                            tint = ClapiColors.OnSurfaceVariant,
                        )
                    },
                    onClick = {
                        expanded = false
                        onDetach?.invoke()
                    },
                    modifier = Modifier.testTag("response-menu-detach"),
                )
            } else {
                DropdownMenuItem(
                    text = { Text(Strings.reattach, fontSize = 13.sp, color = ClapiColors.OnSurface) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_open_in_full),
                            contentDescription = Strings.reattach,
                            modifier = Modifier.size(16.dp),
                            tint = ClapiColors.OnSurfaceVariant,
                        )
                    },
                    onClick = {
                        expanded = false
                        onReattach?.invoke()
                    },
                    modifier = Modifier.testTag("response-menu-reattach"),
                )
                DropdownMenuItem(
                    text = { Text(Strings.close, fontSize = 13.sp, color = ClapiColors.OnSurface) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_close),
                            contentDescription = Strings.close,
                            modifier = Modifier.size(16.dp),
                            tint = ClapiColors.OnSurfaceVariant,
                        )
                    },
                    onClick = {
                        expanded = false
                        onClose?.invoke()
                    },
                    modifier = Modifier.testTag("response-menu-close"),
                )
            }
        }
    }
}

@Composable
private fun MetricChip(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// ── Tab Bar ─────────────────────────────────────────────────────

private fun responseTabsFor(tab: RequestTabState): List<ResponseTab> =
    if (tab.kind == RequestKind.MCP || tab.kind == RequestKind.GRPC) {
        ResponseTab.entries.filter { it != ResponseTab.COOKIES }
    } else {
        ResponseTab.entries
    }

@Composable
private fun ResponseTabBar(
    tabs: List<ResponseTab>,
    selectedTab: ResponseTab,
    onTabSelected: (ResponseTab) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClapiColors.Surface)
    ) {
        ScrollableTabRow(
            selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0),
            containerColor = Color.Transparent,
            contentColor = ClapiColors.OnSurface,
            edgePadding = 0.dp,
            divider = {},
            indicator = {},
        ) {
            tabs.forEach { tab ->
                val selected = tab == selectedTab
                Tab(
                    selected = selected,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier
                        .height(34.dp)
                        .handPointerHover()
                        .testTag("response-tab-${tab.name.lowercase()}"),
                ) {
                    Text(
                        tab.label,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) ClapiColors.Primary else ClapiColors.OnSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(ClapiColors.Border)
        )
    }
}

// ── Body View ───────────────────────────────────────────────────

/**
 * Enhanced response body viewer powered by the shared [CodeEditor].
 * Provides syntax highlighting, code folding, search, formatting,
 * word wrap, copy, and download.
 */
@kotlinx.serialization.ExperimentalSerializationApi
@Composable
private fun ResponseBodyView(response: ResponseDefinition, showCursorPosition: Boolean) {
    val isStream = response.streamEvents.isNotEmpty()
    val bodyText = if (isStream) {
        response.assembledText?.takeIf { it.isNotBlank() } ?: response.bodyText
    } else {
        response.bodyText
    }
    val language = if (isStream) detectLanguage("text/plain") else detectLanguage(response.contentType)

    CodeEditor(
        text = bodyText,
        onTextChange = null,   // read-only
        language = language,
        modifier = Modifier.fillMaxSize(),
        showToolbar = true,
        enableFolding = true,
        enableSearch = true,
        enableFormat = true,
        enableWordWrap = true,
        enableCopy = true,
        enableDownload = true,
        onDownload = { saveResponseToFile(response) },
        showCursorPosition = showCursorPosition,
        testTagPrefix = "response",
    )
}

// ── Headers View ────────────────────────────────────────────────

@Composable
private fun ResponseHeadersView(response: ResponseDefinition) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(end = 12.dp, start = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                ) {
                    Text(Strings.t("key_upper"), style = MaterialTheme.typography.labelSmall, color = ClapiColors.OnSurfaceDim, modifier = Modifier.weight(0.4f))
                    Text(Strings.t("value_upper"), style = MaterialTheme.typography.labelSmall, color = ClapiColors.OnSurfaceDim, modifier = Modifier.weight(0.6f))
                }
            }
            itemsIndexed(response.headers, key = { index, header -> "${header.key}_${header.value}_$index" }) { _, header ->
                SelectionContainer {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ClapiColors.SurfaceContainer)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(header.key, color = ClapiColors.Primary, fontSize = 12.sp, fontFamily = CodeFontFamily, modifier = Modifier.weight(0.4f))
                        Text(header.value, color = ClapiColors.OnSurface, fontSize = 12.sp, fontFamily = CodeFontFamily, modifier = Modifier.weight(0.6f))
                    }
                }
            }
        }
        PlatformLazyVerticalScrollbar(
            listState = listState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            testTag = "response-headers-scrollbar",
        )
    }
}

// ── Cookies View ────────────────────────────────────────────────

@Composable
private fun ResponseCookiesView(response: ResponseDefinition) {
    if (response.cookies.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(Strings.noCookies, color = ClapiColors.OnSurfaceDim, fontSize = 13.sp)
        }
    } else {
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = 12.dp, start = 8.dp, top = 8.dp, bottom = 8.dp),
            ) {
                itemsIndexed(response.cookies, key = { index, cookie -> "${cookie.key}_${cookie.value}_$index" }) { _, cookie ->
                    SelectionContainer {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(ClapiColors.SurfaceContainer)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text(cookie.key, color = ClapiColors.Tertiary, fontSize = 12.sp, fontFamily = CodeFontFamily, modifier = Modifier.weight(0.4f))
                            Text(cookie.value, color = ClapiColors.OnSurface, fontSize = 12.sp, fontFamily = CodeFontFamily, modifier = Modifier.weight(0.6f))
                        }
                    }
                }
            }
            PlatformLazyVerticalScrollbar(
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                testTag = "response-cookies-scrollbar",
            )
        }
    }
}

// ── Raw View ────────────────────────────────────────────────────

@Composable
private fun ResponseRawView(response: ResponseDefinition) {
    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()
    val rawLines = remember(response.bodyText, response.streamEvents) {
        val fullText = if (response.streamEvents.isNotEmpty()) {
            response.streamEvents.joinToString("\n")
        } else {
            response.bodyText
        }
        fullText.split('\n')
    }
    Box(modifier = Modifier.fillMaxSize()) {
        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalScrollState)
                    .padding(end = 12.dp, start = 12.dp, top = 12.dp, bottom = 12.dp),
            ) {
                itemsIndexed(rawLines, key = { index, _ -> index }) { _, line ->
                    Text(
                        text = line,
                        color = ClapiColors.OnSurface,
                        fontSize = 13.sp,
                        fontFamily = CodeFontFamily,
                        softWrap = false,
                    )
                }
            }
        }
        PlatformLazyVerticalScrollbar(
            listState = listState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            testTag = "response-raw-scrollbar",
        )
    }
}

// ── Timing View ─────────────────────────────────────────────────

@Composable
private fun ResponseTimingView(response: ResponseDefinition) {
    val m = response.metrics
    data class Phase(val label: String, val ms: Long, val color: Color)

    val phases = buildList {
        if (m.dnsMs >= 0) add(Phase("DNS Lookup", m.dnsMs, ClapiColors.StatusInfo))
        if (m.connectMs >= 0) add(Phase("TCP Connect", m.connectMs, ClapiColors.StatusWarning))
        if (m.tlsMs >= 0) add(Phase("TLS Handshake", m.tlsMs, ClapiColors.Tertiary))
        if (m.serverMs >= 0) add(Phase("Server Processing", m.serverMs, ClapiColors.StatusSuccess))
        if (m.downloadMs >= 0) add(Phase("Content Download", m.downloadMs, ClapiColors.Error))
        if (m.ttfbMs >= 0) add(Phase("TTFB", m.ttfbMs, ClapiColors.Secondary))
        if (m.timeToFirstTokenMs >= 0) add(Phase("Time to First Token", m.timeToFirstTokenMs, ClapiColors.Primary))
        if (m.timeToLastTokenMs >= 0) add(Phase("Time to Last Token", m.timeToLastTokenMs, ClapiColors.OnSurfaceDim))
    }

    val totalMs = m.responseTimeMs.coerceAtLeast(1)
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(end = 16.dp, start = 16.dp, top = 16.dp, bottom = 16.dp)
                    .testTag("response-timing-view"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
        Text(
            "Request Timing Breakdown",
            color = ClapiColors.OnSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )

        // Summary chip
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricChip("Total: ${m.responseTimeMs} ms", ClapiColors.Secondary)
            MetricChip("Size: ${formatBytes(m.responseSizeBytes)}", ClapiColors.Tertiary)
        }

        Spacer(Modifier.height(4.dp))

        if (phases.isEmpty()) {
            Text(
                "Detailed timing phases are not available for this response.",
                color = ClapiColors.OnSurfaceDim,
                fontSize = 13.sp,
            )
        } else {
            // Waterfall bar chart
            phases.forEach { phase ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(phase.label, color = ClapiColors.OnSurface, fontSize = 13.sp)
                        Text(
                            "${phase.ms} ms",
                            color = ClapiColors.OnSurfaceVariant,
                            fontSize = 13.sp,
                            fontFamily = CodeFontFamily,
                        )
                    }
                    // Bar
                    val fraction = (phase.ms.toFloat() / totalMs).coerceIn(0.005f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ClapiColors.SurfaceContainer),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(10.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(phase.color)
                                .testTag("timing-bar-${phase.label}"),
                        )
                    }
                }
            }
        }
            }
        }
        PlatformColumnVerticalScrollbar(
            scrollState = scrollState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            testTag = "response-timing-scrollbar",
        )
    }
}

// ── Utilities ───────────────────────────────────────────────────

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024        -> "$bytes B"
    bytes < 1024 * 1024 -> "${((bytes / 1024.0 * 10).toLong() / 10.0)} KB"
    else                -> "${((bytes / (1024.0 * 1024.0) * 10).toLong() / 10.0)} MB"
}

private fun saveResponseToFile(response: ResponseDefinition) {
    val ext = fileExtensionForContentType(response.contentType)
    val defaultName = "response.$ext"
    saveFileForExport(response.bodyText, defaultName)
}
