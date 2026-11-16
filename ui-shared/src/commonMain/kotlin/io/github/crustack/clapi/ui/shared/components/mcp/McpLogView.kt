package io.github.crustack.clapi.ui.shared.components.mcp

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
import io.github.crustack.clapi.core.model.*
import io.github.crustack.clapi.core.network.mcp.*
import io.github.crustack.clapi.ui.shared.components.*
import io.github.crustack.clapi.ui.shared.components.executor.buildAuthConfig
import io.github.crustack.clapi.ui.shared.generated.resources.*
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.mcp.*
import io.github.crustack.clapi.ui.shared.platform.*
import io.github.crustack.clapi.ui.shared.state.*
import io.github.crustack.clapi.ui.shared.theme.ClapiColors
import io.github.crustack.clapi.ui.shared.theme.CodeFontFamily
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun ActivitySection(logs: List<McpLogEntry>, onClear: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (logs.isEmpty()) {
            EmptyState(Strings.t("mcp_activity_empty"))
            return@Column
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                Strings.t("clear"),
                color = ClapiColors.OnSurfaceDim,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                modifier = Modifier
                    .testTag("mcp-activity-clear")
                    .clickable(onClick = onClear)
                    .padding(horizontal = 2.dp),
            )
        }
        ScrollableLazyColumn(
            modifier = Modifier.weight(1f),
            listTestTag = "mcp-log",
            scrollbarTag = "mcp-activity-vscrollbar",
        ) {
            items(
                count = logs.size,
                key = { i ->
                    val entry = logs[logs.size - 1 - i]
                    "mcp_log_${entry.timestampEpochMillis}_${entry.kind}_$i"
                },
            ) { i ->
                ActivityRow(logs[logs.size - 1 - i])
                HorizontalDivider(color = ClapiColors.BorderLight)
            }
        }
    }
}

@Composable
internal fun ActivityRow(entry: McpLogEntry) {
    var expanded by remember(entry) { mutableStateOf(false) }
    val hasPayload = !entry.payload.isNullOrBlank()
    val pretty = if (hasPayload) prettyPayload(entry.payload.orEmpty()) else ""
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = hasPayload) { expanded = !expanded },
        ) {
            Text(formatTimestamp(entry.timestampEpochMillis), color = ClapiColors.OnSurfaceDim, fontSize = 11.sp, fontFamily = CodeFontFamily)
            Text(
                entry.kind.name,
                color = activityColor(entry.kind),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, activityColor(entry.kind), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
            Text(entry.summary, color = ClapiColors.OnSurface, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            entry.id?.let { Text("#$it", color = ClapiColors.OnSurfaceDim, fontSize = 10.sp, fontFamily = CodeFontFamily) }
            if (hasPayload) {
                IconButton(
                    onClick = { copyToClipboard(pretty) },
                    modifier = Modifier.size(28.dp).testTag("mcp-activity-copy"),
                ) {
                    Icon(painter = painterResource(Res.drawable.ic_content_copy),
                        contentDescription = Strings.copy,
                        tint = ClapiColors.OnSurfaceDim,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        if (expanded && hasPayload) {
            SelectionContainer {
                Text(
                    pretty,
                    color = ClapiColors.OnSurfaceDim,
                    fontSize = 11.sp,
                    fontFamily = CodeFontFamily,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(ClapiColors.SurfaceContainer)
                        .padding(8.dp)
                        .testTag("mcp-activity-payload"),
                )
            }
        } else if (hasPayload) {
            Text(Strings.t("mcp_activity_expand"), color = ClapiColors.OnSurfaceDim, fontSize = 10.sp)
        }
    }
}


@Composable
internal fun activityColor(kind: McpLogEntryKind): Color = when (kind) {
    McpLogEntryKind.SENT -> ClapiColors.Primary
    McpLogEntryKind.RECEIVED -> ClapiColors.StatusSuccess
    McpLogEntryKind.NOTIFICATION -> ClapiColors.Tertiary
    McpLogEntryKind.ERROR -> ClapiColors.Error
    McpLogEntryKind.STATE, McpLogEntryKind.OAUTH -> ClapiColors.OnSurfaceDim
}

internal fun prettyPayload(payload: String): String = mcpPrettyWireJson(payload)
