package io.github.crustack.clapi.ui.shared.components

import org.jetbrains.compose.resources.painterResource
import io.github.crustack.clapi.ui.shared.generated.resources.Res

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.crustack.clapi.ui.shared.generated.resources.ic_check_circle
import io.github.crustack.clapi.ui.shared.generated.resources.ic_close
import io.github.crustack.clapi.ui.shared.generated.resources.ic_content_copy
import io.github.crustack.clapi.ui.shared.generated.resources.ic_keyboard_arrow_down
import io.github.crustack.clapi.ui.shared.generated.resources.ic_keyboard_arrow_up
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.BottomTab
import io.github.crustack.clapi.ui.shared.state.ConsoleEntry
import io.github.crustack.clapi.ui.shared.state.LogLevel
import io.github.crustack.clapi.ui.shared.state.TestResultEntry
import io.github.crustack.clapi.ui.shared.theme.CodeFontFamily
import io.github.crustack.clapi.ui.shared.theme.ClapiColors
import io.github.crustack.clapi.ui.shared.platform.copyToClipboard
import io.github.crustack.clapi.ui.shared.platform.formatTimestamp
import io.github.crustack.clapi.ui.shared.platform.verticalResizeCursor

private const val MIN_BOTTOM_PANEL_HEIGHT = 120f
private const val MAX_BOTTOM_PANEL_HEIGHT = 560f

@Composable
fun BottomPanel(state: AppState) {
    val noConsoleOutput = Strings.t("no_console_output")
    val noTestResults = Strings.t("no_test_results")
    val copyOutputLabel = Strings.t("copy_output")
    val consoleLabel = Strings.console
    val testResultsLabel = Strings.testResults
    val logsLabel = Strings.logs

    Column(modifier = Modifier.fillMaxWidth().testTag("bottom-panel")) {
        // ── Toggle bar ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(ClapiColors.Surface)
                .clickable { state.bottomPanelExpanded = !state.bottomPanelExpanded }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (state.bottomPanelExpanded) painterResource(Res.drawable.ic_keyboard_arrow_down) else painterResource(Res.drawable.ic_keyboard_arrow_up),
                contentDescription = Strings.t("toggle_bottom_panel"),
                tint = ClapiColors.OnSurfaceDim,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))

            // Tab bar (always visible as header)
            ScrollableTabRow(
                selectedTabIndex = state.selectedBottomTab.ordinal,
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                contentColor = ClapiColors.OnSurface,
                edgePadding = 0.dp,
                divider = {},
                indicator = {},
                modifier = Modifier.weight(1f),
            ) {
                BottomTab.entries.forEach { tab ->
                    val selected = tab == state.selectedBottomTab
                    Tab(
                        selected = selected,
                        onClick = {
                            state.selectedBottomTab = tab
                            if (!state.bottomPanelExpanded) state.bottomPanelExpanded = true
                        },
                        modifier = Modifier.height(30.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                when (tab) {
                                    BottomTab.CONSOLE -> consoleLabel
                                    BottomTab.TEST_RESULTS -> testResultsLabel
                                    BottomTab.LOGS -> logsLabel
                                },
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) ClapiColors.Primary else ClapiColors.OnSurfaceDim,
                            )
                            // Show count badge on CONSOLE and LOGS tabs
                            if (tab == BottomTab.CONSOLE && state.consoleLogs.isNotEmpty()) {
                                Text(
                                    "${state.consoleLogs.size}",
                                    fontSize = 9.sp,
                                    color = ClapiColors.OnSurfaceDim,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(ClapiColors.SurfaceHigh)
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                            if (tab == BottomTab.LOGS && state.networkEventLogs.isNotEmpty()) {
                                Text(
                                    "${state.networkEventLogs.size}",
                                    fontSize = 9.sp,
                                    color = ClapiColors.OnSurfaceDim,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(ClapiColors.SurfaceHigh)
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Clear button
            if (state.bottomPanelExpanded) {
                IconButton(
                    onClick = {
                        copyToClipboard(
                            when (state.selectedBottomTab) {
                                BottomTab.CONSOLE -> state.consoleLogs.asConsoleTextDump(noConsoleOutput)
                                // M-3: Logs tab copies network event logs, not console logs
                                BottomTab.LOGS -> state.networkEventLogs.asConsoleTextDump(noConsoleOutput)
                                BottomTab.TEST_RESULTS -> state.testResults.asTestResultsTextDump(noTestResults)
                            }
                        )
                        val tabLabel = when (state.selectedBottomTab) {
                            BottomTab.CONSOLE -> consoleLabel
                            BottomTab.TEST_RESULTS -> testResultsLabel
                            BottomTab.LOGS -> logsLabel
                        }
                        state.log("Copied ${tabLabel.lowercase()} output", LogLevel.INFO)
                    },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(painter = painterResource(Res.drawable.ic_content_copy),
                        contentDescription = copyOutputLabel,
                        tint = ClapiColors.OnSurfaceDim,
                        modifier = Modifier.size(14.dp),
                    )
                }
                IconButton(onClick = {
                    when (state.selectedBottomTab) {
                        BottomTab.CONSOLE      -> state.consoleLogs.clear()
                        BottomTab.TEST_RESULTS -> state.testResults.clear()
                        // M-3: Clear only network event logs for the Logs tab
                        BottomTab.LOGS         -> state.networkEventLogs.clear()
                    }
                }, modifier = Modifier.size(24.dp)) {
                    Icon(painter = painterResource(Res.drawable.ic_close), contentDescription = Strings.t("clear"), tint = ClapiColors.OnSurfaceDim, modifier = Modifier.size(14.dp))
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(ClapiColors.Border))

        // ── Content ─────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.bottomPanelExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Modifier.draggable reports delta in pixels; state is in dp.
                // Divide by density so 1px cursor movement = 1/density dp = 1px on screen.
                val density = LocalDensity.current.density
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .pointerHoverIcon(verticalResizeCursor)
                        .draggable(
                            orientation = Orientation.Vertical,
                            startDragImmediately = true,
                            state = rememberDraggableState { delta ->
                                state.bottomPanelHeight = (state.bottomPanelHeight - delta / density)
                                    .coerceIn(MIN_BOTTOM_PANEL_HEIGHT, MAX_BOTTOM_PANEL_HEIGHT)
                            },
                        )
                        .background(ClapiColors.Border)
                        .testTag("bottom-panel-resize-handle"),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(state.bottomPanelHeight.dp)
                        .background(ClapiColors.SurfaceVariant),
                ) {
                when (state.selectedBottomTab) {
                    BottomTab.CONSOLE      -> ConsoleView(state.consoleLogs)
                    BottomTab.TEST_RESULTS -> TestResultsView(state.testResults)
                    // M-3: LOGS shows only network-level events (HTTP start/retry/done),
                    // keeping it distinct from the general-purpose Console tab.
                    BottomTab.LOGS         -> ConsoleView(state.networkEventLogs)
                }
            }
            }
        }
    }
}

private fun List<ConsoleEntry>.asConsoleTextDump(emptyLabel: String): String =
    if (isEmpty()) {
        emptyLabel
    } else {
        joinToString("\n") { entry ->
            "${formatTimestamp(entry.timestamp)} ${entry.level.name}: ${entry.message}"
        }
    }

private fun List<TestResultEntry>.asTestResultsTextDump(emptyLabel: String): String =
    if (isEmpty()) {
        emptyLabel
    } else {
        joinToString("\n") { entry ->
            "${if (entry.passed) "PASS" else "FAIL"} ${entry.name}${if (entry.message.isNotBlank()) " - ${entry.message}" else ""}"
        }
    }

// ── Console View ────────────────────────────────────────────────

@Composable
private fun ConsoleView(logs: List<ConsoleEntry>) {
    if (logs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(Strings.t("no_console_output"), color = ClapiColors.OnSurfaceDim, fontSize = 12.sp)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .testTag("console-output"),
        ) {
            items(logs) { entry ->
                ConsoleRow(entry)
            }
        }
    }
}

@Composable
private fun ConsoleRow(entry: ConsoleEntry) {
    val color = when (entry.level) {
        LogLevel.INFO    -> ClapiColors.OnSurfaceVariant
        LogLevel.SUCCESS -> ClapiColors.Secondary
        LogLevel.WARNING -> ClapiColors.Tertiary
        LogLevel.ERROR   -> ClapiColors.Error
    }
    val prefix = when (entry.level) {
        LogLevel.INFO    -> "INFO"
        LogLevel.SUCCESS -> " OK "
        LogLevel.WARNING -> "WARN"
        LogLevel.ERROR   -> " ERR"
    }
    val timeStr = formatTimestamp(entry.timestamp)

    SelectionContainer {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(timeStr, color = ClapiColors.OnSurfaceDim, fontSize = 11.sp, fontFamily = CodeFontFamily)
            Text(
                prefix,
                color = color,
                fontSize = 11.sp,
                fontFamily = CodeFontFamily,
                fontWeight = FontWeight.Bold,
            )
            Text(entry.message, color = color, fontSize = 11.sp, fontFamily = CodeFontFamily)
        }
    }
}

// ── Test Results View ───────────────────────────────────────────

@Composable
private fun TestResultsView(results: List<TestResultEntry>) {
    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(Strings.t("no_test_results"), color = ClapiColors.OnSurfaceDim, fontSize = 12.sp)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .testTag("test-results"),
        ) {
            items(results) { result ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (result.passed) ClapiColors.Secondary.copy(alpha = 0.08f)
                            else ClapiColors.Error.copy(alpha = 0.08f)
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(painter = painterResource(Res.drawable.ic_check_circle),
                        contentDescription = null,
                        tint = if (result.passed) ClapiColors.Secondary else ClapiColors.Error,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        result.name,
                        color = ClapiColors.OnSurface,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (result.message.isNotEmpty()) {
                        Text(result.message, color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
