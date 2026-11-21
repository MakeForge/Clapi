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
internal fun ClientSection(
    state: AppState,
    tab: RequestTabState,
    session: io.github.makeforge.clapi.ui.shared.mcp.McpSessionState,
    connected: Boolean,
) {
    val scroll = rememberScrollState()
    val http = tab.mcpConfig.transport == McpTransportType.STREAMABLE_HTTP
    fun persistRoots(roots: List<McpRoot>) {
        tab.mcpConfig = tab.mcpConfig.copy(roots = roots)
        tab.markDirty()
        if (connected) session.notifyRootsEdited(roots)
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ClientSettingsCard(title = Strings.t("mcp_client_connection")) {
                ClientSettingLabel(Strings.t("mcp_transport"))
                val transportSegments = buildList {
                    add(
                        McpSegment("HTTP", tab.mcpConfig.transport == McpTransportType.STREAMABLE_HTTP) {
                            tab.mcpConfig = tab.mcpConfig.copy(transport = McpTransportType.STREAMABLE_HTTP)
                            tab.markDirty()
                        },
                    )
                    if (session.stdioAvailable()) {
                        add(
                            McpSegment("stdio", tab.mcpConfig.transport == McpTransportType.STDIO) {
                                tab.mcpConfig = tab.mcpConfig.copy(transport = McpTransportType.STDIO)
                                tab.markDirty()
                            },
                        )
                    }
                }
                McpSegmentedControl(transportSegments)
                if (http) {
                    ClientSettingLabel(Strings.t("mcp_http_mode"))
                    McpSegmentedControl(
                        listOf(
                            McpSegment("Auto", tab.mcpConfig.httpMode == McpHttpMode.AUTO) {
                                tab.mcpConfig = tab.mcpConfig.copy(httpMode = McpHttpMode.AUTO)
                                tab.markDirty()
                            },
                            McpSegment("2025-06-18", tab.mcpConfig.httpMode == McpHttpMode.STREAMABLE_2025_06_18) {
                                tab.mcpConfig = tab.mcpConfig.copy(httpMode = McpHttpMode.STREAMABLE_2025_06_18)
                                tab.markDirty()
                            },
                            McpSegment("Legacy", tab.mcpConfig.httpMode == McpHttpMode.LEGACY_2024_11_05) {
                                tab.mcpConfig = tab.mcpConfig.copy(httpMode = McpHttpMode.LEGACY_2024_11_05)
                                tab.markDirty()
                            },
                        ),
                    )
                }
                if (connected) {
                    Text(Strings.t("mcp_client_reconnect"), color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
                }
            }

            ClientSettingsCard(title = Strings.t("mcp_client_callbacks")) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            Strings.t("mcp_auto_sampling"),
                            color = ClapiColors.OnSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        )
                        Text(Strings.t("mcp_auto_sampling_explain"), color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
                    }
                    Switch(
                        checked = tab.mcpConfig.samplingMode == McpSamplingMode.MOCK,
                        onCheckedChange = { on ->
                            tab.mcpConfig = tab.mcpConfig.copy(
                                samplingMode = if (on) McpSamplingMode.MOCK else McpSamplingMode.MANUAL,
                            )
                            tab.markDirty()
                        },
                        modifier = Modifier.testTag("mcp-auto-sampling"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ClapiColors.OnPrimary,
                            checkedTrackColor = ClapiColors.Primary,
                            uncheckedThumbColor = ClapiColors.OnSurfaceDim,
                            uncheckedTrackColor = ClapiColors.SurfaceHigh,
                        ),
                    )
                }
                if (tab.mcpConfig.samplingMode != McpSamplingMode.MOCK) {
                    ClientSettingLabel(Strings.t("mcp_llm_url"))
                    SearchField(
                        value = tab.mcpConfig.samplingForwardUrl.orEmpty(),
                        onValueChange = {
                            tab.mcpConfig = tab.mcpConfig.copy(samplingForwardUrl = it.ifBlank { null })
                            tab.markDirty()
                        },
                        placeholder = "http://localhost:8080/v1/chat/completions",
                        testTag = "mcp-llm-url",
                        state = state,
                    )
                    ClientSettingLabel(Strings.t("mcp_llm_token"))
                    SearchField(
                        value = tab.mcpConfig.samplingForwardToken.orEmpty(),
                        onValueChange = {
                            tab.mcpConfig = tab.mcpConfig.copy(samplingForwardToken = it.ifBlank { null })
                            tab.markDirty()
                        },
                        placeholder = Strings.t("mcp_llm_token"),
                        testTag = "mcp-llm-token",
                    )
                    ClientSettingLabel(Strings.t("mcp_llm_max_tokens"))
                    SearchField(
                        value = tab.mcpConfig.samplingMaxTokens?.toString().orEmpty(),
                        onValueChange = {
                            tab.mcpConfig = tab.mcpConfig.copy(samplingMaxTokens = it.toIntOrNull())
                            tab.markDirty()
                        },
                        placeholder = "256",
                        testTag = "mcp-llm-max-tokens",
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            Strings.t("mcp_auto_elicit"),
                            color = ClapiColors.OnSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        )
                        Text(Strings.t("mcp_elicit_explain"), color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
                    }
                    Switch(
                        checked = tab.mcpConfig.autoRespondElicitation,
                        onCheckedChange = {
                            tab.mcpConfig = tab.mcpConfig.copy(autoRespondElicitation = it)
                            tab.markDirty()
                        },
                        modifier = Modifier.testTag("mcp-auto-elicit"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ClapiColors.OnPrimary,
                            checkedTrackColor = ClapiColors.Primary,
                            uncheckedThumbColor = ClapiColors.OnSurfaceDim,
                            uncheckedTrackColor = ClapiColors.SurfaceHigh,
                        ),
                    )
                }
            }

            ClientSettingsCard(title = Strings.t("mcp_roots")) {
                if (tab.mcpConfig.roots.isEmpty()) {
                    Text(Strings.t("mcp_roots_empty"), color = ClapiColors.OnSurfaceDim, fontSize = 12.sp)
                } else {
                    RootsTableRow {
                        Text(
                            Strings.t("mcp_root_uri"),
                            color = ClapiColors.OnSurfaceDim,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            Strings.t("mcp_root_name"),
                            color = ClapiColors.OnSurfaceDim,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.size(28.dp))
                    }
                    tab.mcpConfig.roots.forEachIndexed { index, root ->
                        RootsTableRow {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ClapiColors.SurfaceContainer)
                                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            ) {
                                VariableAwareTextField(
                                    value = root.uri,
                                    onValueChange = { next ->
                                        val roots = tab.mcpConfig.roots.toMutableList()
                                        roots[index] = root.copy(uri = next)
                                        persistRoots(roots)
                                    },
                                    placeholder = Strings.t("mcp_root_uri"),
                                    modifier = Modifier.fillMaxWidth().testTag("mcp-root-uri-$index"),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ClapiColors.SurfaceContainer)
                                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            ) {
                                VariableAwareTextField(
                                    value = root.name.orEmpty(),
                                    onValueChange = { next ->
                                        val roots = tab.mcpConfig.roots.toMutableList()
                                        roots[index] = root.copy(name = next.ifBlank { null })
                                        persistRoots(roots)
                                    },
                                    placeholder = Strings.t("mcp_root_name"),
                                    modifier = Modifier.fillMaxWidth().testTag("mcp-root-name-$index"),
                                )
                            }
                            IconButton(
                                onClick = {
                                    persistRoots(tab.mcpConfig.roots.filterIndexed { i, _ -> i != index })
                                },
                                modifier = Modifier.size(28.dp).testTag("mcp-root-remove-$index"),
                            ) {
                                Icon(painter = painterResource(Res.drawable.ic_close),
                                    contentDescription = Strings.t("mcp_remove_root"),
                                    tint = ClapiColors.OnSurfaceDim,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
                TextButton(
                    onClick = {
                        persistRoots(tab.mcpConfig.roots + McpRoot(uri = "file://", name = ""))
                    },
                    modifier = Modifier.testTag("mcp-add-root"),
                ) {
                    Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(Strings.t("mcp_add_root"))
                }
            }
        }
        PlatformColumnVerticalScrollbar(
            scrollState = scroll,
            modifier = Modifier.align(Alignment.CenterEnd).insetScrollbar(),
            testTag = "mcp-client-vscrollbar",
        )
    }
}

@Composable
internal fun RootsTableRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
internal fun ClientSettingsCard(
    title: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SplitCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, color = ClapiColors.OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (description != null) {
                Text(description, color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
            }
            content()
        }
    }
}

@Composable
internal fun ClientSettingLabel(text: String) {
    Text(text, color = ClapiColors.OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
}


