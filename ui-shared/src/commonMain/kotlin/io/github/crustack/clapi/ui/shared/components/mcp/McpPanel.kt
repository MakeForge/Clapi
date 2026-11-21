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

private const val SECTION_TOOLS = 0
private const val SECTION_RESOURCES = 1
private const val SECTION_PROMPTS = 2
private const val SECTION_AUTH = 3
private const val SECTION_HEADERS = 4
private const val SECTION_PARAMS = 5
private const val SECTION_ACTIVITY = 6
private const val SECTION_CLIENT = 7
/** Canonical UUID string length; longer session ids are truncated in the chip. */


@Composable
fun McpPanel(state: AppState, tab: RequestTabState) {
    val session = remember(tab.id) { state.getOrCreateMcpSession(tab.id) }
    val connection by session.connectionState.collectAsState()
    val tools by session.tools.collectAsState()
    val resources by session.resources.collectAsState()
    val prompts by session.prompts.collectAsState()
    val logs by session.logs.collectAsState()
    val subscribed by session.subscribedUris.collectAsState()
    val lastOperation by session.lastOperation.collectAsState()
    val busy by session.busy.collectAsState()
    val error by session.error.collectAsState()

    var section by remember { mutableIntStateOf(0) }
    var toolArgs by remember { mutableStateOf("{}") }
    var selectedTool by remember { mutableStateOf<String?>(null) }
    var selectedResource by remember { mutableStateOf<String?>(null) }
    var selectedPrompt by remember { mutableStateOf<String?>(null) }
    var promptArgs by remember { mutableStateOf("{}") }
    var showStdioConfirm by remember { mutableStateOf(false) }
    val lastToolArgs = remember(tab.id) { mutableStateMapOf<String, String>() }
    val lastPromptArgs = remember(tab.id) { mutableStateMapOf<String, String>() }
    var listSplit by remember { mutableFloatStateOf(0.32f) }

    val headerRows = remember(tab.id) {
        mutableStateListOf<MutableKeyValue>().apply {
            addAll(tab.mcpConfig.headers.map { MutableKeyValue(it.key, it.value, it.enabled, it.secret) })
        }
    }
    fun persistHeaders() {
        tab.mcpConfig = tab.mcpConfig.copy(
            headers = headerRows.map { KeyValueEntry(it.key, it.value, it.enabled, it.secret) },
        )
        tab.markDirty()
    }

    fun liveConfig() = tab.mcpConfig.copy(
        url = tab.url.ifBlank { tab.mcpConfig.url },
        auth = buildAuthConfig(tab),
        headers = headerRows.map { KeyValueEntry(it.key, it.value, it.enabled, it.secret) },
    )

    val errorLabel = Strings.t("mcp_tool_error")
    val okLabel = Strings.t("mcp_tool_ok")
    LaunchedEffect(lastOperation) {
        val op = lastOperation ?: return@LaunchedEffect
        tab.response = op.toResponseDefinition(tab.id, okStatusText = okLabel, errorStatusText = errorLabel)
        tab.responseTab = ResponseTab.BODY
        tab.lastError = if (op.isError) error else null
    }
    LaunchedEffect(busy) { tab.isLoading = busy }
    LaunchedEffect(error) {
        if (lastOperation?.isError != true) tab.lastError = error
    }
    LaunchedEffect(connection) {
        if (connection == McpConnectionState.DISCONNECTED) {
            tab.response = null
            tab.lastError = null
            tab.isLoading = false
        }
    }

    LaunchedEffect(connection, tools) {
        if (connection != McpConnectionState.CONNECTED || tools.isEmpty()) return@LaunchedEffect
        val preferred = selectedTool?.takeIf { name -> tools.any { it.name == name } } ?: tools.first().name
        if (selectedTool != preferred) {
            selectedTool = preferred
            toolArgs = lastToolArgs[preferred] ?: mcpDefaultArgsJson(tools.first { it.name == preferred }.inputSchema)
        }
    }
    LaunchedEffect(connection, prompts) {
        if (connection != McpConnectionState.CONNECTED || prompts.isEmpty()) return@LaunchedEffect
        val preferred = selectedPrompt?.takeIf { name -> prompts.any { it.name == name } } ?: prompts.first().name
        if (selectedPrompt != preferred) {
            selectedPrompt = preferred
            promptArgs = lastPromptArgs[preferred] ?: defaultPromptArgsJson(prompts.first { it.name == preferred })
        }
    }

    fun connectNow() {
        syncUrlFromParams(tab)
        val url = tab.url.ifBlank { tab.mcpConfig.url }
        tab.url = url
        persistHeaders()
        tab.mcpConfig = tab.mcpConfig.copy(url = url, auth = buildAuthConfig(tab))
        val cfg = tab.mcpConfig
        state.appScope.launch { runCatching { session.connect(cfg, state.activeVariableLayers()) } }
    }

    fun requestConnect() {
        if (tab.mcpConfig.transport == McpTransportType.STDIO && !session.confirmStdio) {
            showStdioConfirm = true
        } else {
            connectNow()
        }
    }

    val selectedToolSchema = tools.firstOrNull { it.name == selectedTool }?.inputSchema
    val selectedPromptModel = prompts.firstOrNull { it.name == selectedPrompt }
    LaunchedEffect(section, selectedTool, selectedPrompt, selectedResource, toolArgs, promptArgs, busy, connection) {
        session.pendingShortcut = {
            when {
                busy -> session.cancelCall()
                connection != McpConnectionState.CONNECTED -> Unit
                section == SECTION_TOOLS && selectedTool != null &&
                    mcpMissingRequiredArgs(selectedToolSchema ?: JsonObject(emptyMap()), toolArgs).isEmpty() -> {
                    val args = runCatching { mcpPrettyJson.parseToJsonElement(toolArgs) }.getOrNull() as? JsonObject
                    session.launchCall { session.callSelectedTool(selectedTool!!, args) }
                }
                section == SECTION_RESOURCES && selectedResource != null -> {
                    session.launchCall { session.readSelectedResource(selectedResource!!) }
                }
                section == SECTION_PROMPTS && selectedPrompt != null && selectedPromptModel != null &&
                    mcpMissingRequiredArgs(mcpPromptSchema(selectedPromptModel), promptArgs).isEmpty() -> {
                    val parsed = runCatching { mcpPrettyJson.parseToJsonElement(promptArgs) }.getOrNull() as? JsonObject
                    val map = parsed?.mapNotNull { (k, v) ->
                        (v as? JsonPrimitive)?.contentOrNull?.let { k to it }
                    }?.toMap().orEmpty()
                    session.launchCall { session.getSelectedPrompt(selectedPrompt!!, map) }
                }
            }
        }
    }

    val reconnectNeeded = session.isReconnectNeeded(liveConfig())
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ClapiColors.Background)
            .testTag("mcp-panel"),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ConnectionBar(
                tab = tab,
                state = state,
                session = session,
                connection = connection,
                reconnectNeeded = reconnectNeeded,
                showStdioConfirm = showStdioConfirm,
                onShowStdioConfirm = { showStdioConfirm = it },
                onConnect = { requestConnect() },
                onDisconnect = { state.appScope.launch { session.disconnect() } },
                onReconnect = {
                    state.appScope.launch {
                        session.disconnect()
                        requestConnect()
                    }
                },
                onConfirmStdio = {
                    session.confirmStdio = true
                    showStdioConfirm = false
                    connectNow()
                },
            )
            error?.let { Text(it, color = ClapiColors.Error, fontSize = 12.sp, modifier = Modifier.testTag("mcp-error")) }
        }

        val tabLabels = listOf(
            "${Strings.t("mcp_tools")}${countSuffix(tools.size)}",
            "${Strings.t("mcp_resources")}${countSuffix(resources.size)}",
            "${Strings.t("mcp_prompts")}${countSuffix(prompts.size)}",
            Strings.t("auth"),
            "${Strings.t("headers")}${countSuffix(headerRows.size)}",
            "${Strings.t("params")}${countSuffix(tab.params.size)}",
            Strings.t("mcp_activity"),
            Strings.t("mcp_client"),
        )
        McpSectionTabBar(
            labels = tabLabels,
            selectedIndex = section,
            onSelect = { section = it },
        )

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            val connected = connection == McpConnectionState.CONNECTED
            when (section) {
                SECTION_TOOLS -> ToolsSection(
                    state = state,
                    connected = connected,
                    tools = tools,
                    selected = selectedTool,
                    args = toolArgs,
                    busy = busy,
                    listSplit = listSplit,
                    onListSplitChanged = { listSplit = it },
                    onSelect = { tool ->
                        selectedTool?.let { lastToolArgs[it] = toolArgs }
                        selectedTool = tool.name
                        toolArgs = lastToolArgs[tool.name] ?: mcpDefaultArgsJson(tool.inputSchema)
                    },
                    onArgsChange = { next ->
                        toolArgs = next
                        selectedTool?.let { lastToolArgs[it] = next }
                    },
                    onRun = { name ->
                        val args = runCatching { mcpPrettyJson.parseToJsonElement(toolArgs) }.getOrNull() as? JsonObject
                        session.launchCall { session.callSelectedTool(name, args) }
                    },
                    onStop = { session.cancelCall() },
                )
                SECTION_RESOURCES -> ResourcesSection(
                    connected = connected,
                    resources = resources,
                    selected = selectedResource,
                    subscribed = subscribed,
                    supportsSubscribe = session.supportsSubscribe(),
                    busy = busy,
                    listSplit = listSplit,
                    onListSplitChanged = { listSplit = it },
                    onSelect = { selectedResource = it.uri },
                    onRead = { uri -> session.launchCall { session.readSelectedResource(uri) } },
                    onSubscribe = { uri -> session.launchCall { session.subscribeResource(uri) } },
                    onUnsubscribe = { uri -> session.launchCall { session.unsubscribeResource(uri) } },
                    onStop = { session.cancelCall() },
                )
                SECTION_PROMPTS -> PromptsSection(
                    state = state,
                    connected = connected,
                    prompts = prompts,
                    selected = selectedPrompt,
                    args = promptArgs,
                    busy = busy,
                    listSplit = listSplit,
                    onListSplitChanged = { listSplit = it },
                    onSelect = { prompt ->
                        selectedPrompt?.let { lastPromptArgs[it] = promptArgs }
                        selectedPrompt = prompt.name
                        promptArgs = lastPromptArgs[prompt.name] ?: defaultPromptArgsJson(prompt)
                    },
                    onArgsChange = { next ->
                        promptArgs = next
                        selectedPrompt?.let { lastPromptArgs[it] = next }
                    },
                    onGet = { name ->
                        val parsed = runCatching { mcpPrettyJson.parseToJsonElement(promptArgs) }.getOrNull() as? JsonObject
                        val map = parsed?.mapNotNull { (k, v) ->
                            (v as? JsonPrimitive)?.contentOrNull?.let { k to it }
                        }?.toMap().orEmpty()
                        session.launchCall { session.getSelectedPrompt(name, map) }
                    },
                    onStop = { session.cancelCall() },
                )
                SECTION_AUTH -> AuthEditor(tab, state) {
                    tab.mcpConfig = tab.mcpConfig.copy(auth = buildAuthConfig(tab))
                    tab.markDirty()
                }
                SECTION_HEADERS -> KeyValueEditor(headerRows, "header", state) { persistHeaders() }
                SECTION_PARAMS -> KeyValueEditor(tab.params, "param", state) {
                    syncUrlFromParams(tab)
                    tab.mcpConfig = tab.mcpConfig.copy(url = tab.url)
                    tab.markDirty()
                }
                SECTION_ACTIVITY -> ActivitySection(logs, onClear = { session.clearLogs() })
                else -> ClientSection(state, tab, session, connected = connected)
            }
        }
    }
}
