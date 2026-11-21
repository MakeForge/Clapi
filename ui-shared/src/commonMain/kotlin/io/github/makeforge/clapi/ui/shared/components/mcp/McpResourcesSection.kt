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
internal fun ResourcesSection(
    connected: Boolean,
    resources: List<McpResource>,
    selected: String?,
    subscribed: Set<String>,
    supportsSubscribe: Boolean,
    busy: Boolean,
    listSplit: Float,
    onListSplitChanged: (Float) -> Unit,
    onSelect: (McpResource) -> Unit,
    onRead: (String) -> Unit,
    onSubscribe: (String) -> Unit,
    onUnsubscribe: (String) -> Unit,
    onStop: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!connected) {
            EmptyState(Strings.t("mcp_connect_hint"))
            return@Column
        }
        if (resources.isEmpty()) {
            EmptyState(Strings.t("mcp_no_resources"))
            return@Column
        }
        var query by remember { mutableStateOf("") }
        val filtered = remember(resources, query) {
            val q = query.trim()
            if (q.isEmpty()) resources
            else resources.filter { it.name.contains(q, ignoreCase = true) || it.uri.contains(q, ignoreCase = true) }
        }
        SplitCard(Modifier.weight(1f).fillMaxWidth()) {
        HorizontalSplitPane(
            modifier = Modifier.fillMaxSize(),
            splitFraction = listSplit,
            onSplitChanged = onListSplitChanged,
            minFraction = 0.18f,
            maxFraction = 0.55f,
            dividerTag = "mcp-resources-split",
            hairline = true,
            first = {
                SplitColumn {
                    SearchField(query, { query = it }, Strings.t("mcp_search_resources"), "mcp-search-resources")
                    ScrollableLazyColumn(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        listTestTag = "mcp-resource-list",
                        scrollbarTag = "mcp-resource-list-vscrollbar",
                    ) {
                        items(filtered, key = { it.uri }) { res ->
                            ListRow(
                                title = res.name,
                                subtitle = res.uri,
                                active = res.uri == selected,
                                trailing = if (res.uri in subscribed) Strings.t("mcp_subscribed") else null,
                                onClick = { onSelect(res) },
                            )
                        }
                    }
                }
            },
            second = {
                val res = resources.firstOrNull { it.uri == selected }
                if (res == null) {
                    SplitColumn {
                        EmptyState(Strings.t("mcp_resource_empty"))
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().padding(10.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(res.name, color = ClapiColors.OnSurface, fontWeight = FontWeight.SemiBold)
                        Text(res.uri, color = ClapiColors.OnSurfaceDim, fontSize = 11.sp, fontFamily = CodeFontFamily, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            RunButton(
                                busy = busy,
                                enabled = true,
                                label = Strings.t("mcp_read_resource"),
                                testTag = "mcp-read",
                                onClick = { onRead(res.uri) },
                                onStop = onStop,
                            )
                            if (supportsSubscribe) {
                                if (res.uri in subscribed) {
                                    OutlinedButton(onClick = { onUnsubscribe(res.uri) }, enabled = !busy, modifier = Modifier.testTag("mcp-unsubscribe")) {
                                        Text(Strings.t("mcp_unsubscribe"))
                                    }
                                } else {
                                    OutlinedButton(onClick = { onSubscribe(res.uri) }, enabled = !busy, modifier = Modifier.testTag("mcp-subscribe")) {
                                        Text(Strings.t("mcp_subscribe"))
                                    }
                                }
                            }
                        }
                    }
                }
            },
        )
        }
    }
}
