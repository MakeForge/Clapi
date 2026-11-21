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
internal fun ToolsSection(
    state: AppState,
    connected: Boolean,
    tools: List<McpTool>,
    selected: String?,
    args: String,
    busy: Boolean,
    listSplit: Float,
    onListSplitChanged: (Float) -> Unit,
    onSelect: (McpTool) -> Unit,
    onArgsChange: (String) -> Unit,
    onRun: (String) -> Unit,
    onStop: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!connected) {
            EmptyState(Strings.t("mcp_connect_hint"))
            return@Column
        }
        if (tools.isEmpty()) {
            EmptyState(Strings.t("mcp_no_tools"))
            return@Column
        }
        var query by remember { mutableStateOf("") }
        val filtered = remember(tools, query) {
            val q = query.trim()
            if (q.isEmpty()) tools
            else tools.filter { it.name.contains(q, ignoreCase = true) || it.description.orEmpty().contains(q, ignoreCase = true) }
        }
        SplitCard(Modifier.weight(1f).fillMaxWidth()) {
        HorizontalSplitPane(
            modifier = Modifier.fillMaxSize(),
            splitFraction = listSplit,
            onSplitChanged = onListSplitChanged,
            minFraction = 0.18f,
            maxFraction = 0.55f,
            dividerTag = "mcp-tools-split",
            hairline = true,
            first = {
                SplitColumn {
                    SearchField(query, { query = it }, Strings.t("mcp_search_tools"), "mcp-search-tools")
                    ScrollableLazyColumn(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        listTestTag = "mcp-tool-list",
                        scrollbarTag = "mcp-tool-list-vscrollbar",
                    ) {
                        items(filtered, key = { it.name }) { tool ->
                            ListRow(
                                title = tool.name,
                                subtitle = tool.description,
                                active = tool.name == selected,
                                onClick = { onSelect(tool) },
                            )
                        }
                    }
                }
            },
            second = {
                SplitColumn {
                    val tool = tools.firstOrNull { it.name == selected }
                    if (tool == null) {
                        EmptyState(Strings.t("mcp_select_tool"))
                        return@SplitColumn
                    }
                    val missing = mcpMissingRequiredArgs(tool.inputSchema, args)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(tool.name, color = ClapiColors.OnSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        mcpToolHintChips(tool.annotations).forEach { chip ->
                            val label = if (chip == "readOnly") Strings.t("mcp_readonly") else Strings.t("mcp_destructive")
                            AnnotationChip(label, destructive = chip == "destructive")
                        }
                        RunButton(
                            busy = busy,
                            enabled = missing.isEmpty(),
                            label = Strings.t("mcp_run"),
                            testTag = "mcp-run",
                            onClick = { onRun(tool.name) },
                            onStop = onStop,
                        )
                    }
                    SchemaArgsEditor(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        state = state,
                        schema = tool.inputSchema,
                        args = args,
                        onArgsChange = onArgsChange,
                        testTagPrefix = "mcp-tool-args",
                    )
                }
            },
        )
        }
    }
}
