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
internal fun PromptsSection(
    state: AppState,
    connected: Boolean,
    prompts: List<McpPrompt>,
    selected: String?,
    args: String,
    busy: Boolean,
    listSplit: Float,
    onListSplitChanged: (Float) -> Unit,
    onSelect: (McpPrompt) -> Unit,
    onArgsChange: (String) -> Unit,
    onGet: (String) -> Unit,
    onStop: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!connected) {
            EmptyState(Strings.t("mcp_connect_hint"))
            return@Column
        }
        if (prompts.isEmpty()) {
            EmptyState(Strings.t("mcp_no_prompts"))
            return@Column
        }
        var query by remember { mutableStateOf("") }
        val filtered = remember(prompts, query) {
            val q = query.trim()
            if (q.isEmpty()) prompts
            else prompts.filter { it.name.contains(q, ignoreCase = true) || it.description.orEmpty().contains(q, ignoreCase = true) }
        }
        SplitCard(Modifier.weight(1f).fillMaxWidth()) {
        HorizontalSplitPane(
            modifier = Modifier.fillMaxSize(),
            splitFraction = listSplit,
            onSplitChanged = onListSplitChanged,
            minFraction = 0.18f,
            maxFraction = 0.55f,
            dividerTag = "mcp-prompts-split",
            hairline = true,
            first = {
                SplitColumn {
                    SearchField(query, { query = it }, Strings.t("mcp_search_prompts"), "mcp-search-prompts")
                    ScrollableLazyColumn(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        listTestTag = "mcp-prompt-list",
                        scrollbarTag = "mcp-prompt-list-vscrollbar",
                    ) {
                        items(filtered, key = { it.name }) { prompt ->
                            ListRow(
                                title = prompt.name,
                                subtitle = prompt.description,
                                active = prompt.name == selected,
                                onClick = { onSelect(prompt) },
                            )
                        }
                    }
                }
            },
            second = {
                SplitColumn {
                    val prompt = prompts.firstOrNull { it.name == selected }
                    if (prompt == null) {
                        EmptyState(Strings.t("mcp_prompt_empty"))
                        return@SplitColumn
                    }
                    val schema = mcpPromptSchema(prompt)
                    val missing = mcpMissingRequiredArgs(schema, args)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(prompt.name, color = ClapiColors.OnSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        RunButton(
                            busy = busy,
                            enabled = missing.isEmpty(),
                            label = Strings.t("mcp_get_prompt"),
                            testTag = "mcp-get-prompt",
                            onClick = { onGet(prompt.name) },
                            onStop = onStop,
                        )
                    }
                    SchemaArgsEditor(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        state = state,
                        schema = schema,
                        args = args,
                        onArgsChange = onArgsChange,
                        testTagPrefix = "mcp-prompt-args",
                    )
                }
            },
        )
        }
    }
}

@Composable
internal fun SchemaArgsEditor(
    modifier: Modifier = Modifier,
    state: AppState,
    schema: JsonObject,
    args: String,
    onArgsChange: (String) -> Unit,
    testTagPrefix: String,
) {
    val formSupported = mcpSchemaFormSupported(schema)
    var preferForm by remember { mutableStateOf(true) }
    val showForm = formSupported && preferForm
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (formSupported) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TransportChip(Strings.t("mcp_form"), showForm) { preferForm = true }
                TransportChip(Strings.t("mcp_json"), !showForm) { preferForm = false }
            }
        }
        if (showForm) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                mcpSchemaFields(schema).forEach { field ->
                    val label = buildString {
                        append(field.title ?: field.name)
                        if (field.required) append(" *")
                    }
                    Text(label, color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
                    when {
                        field.enumValues.isNotEmpty() -> {
                            val current = (mcpArgsGet(args, field.name) as? JsonPrimitive)?.content.orEmpty()
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                field.enumValues.forEach { opt ->
                                    TransportChip(opt, current == opt) {
                                        onArgsChange(mcpArgsPut(args, field.name, JsonPrimitive(opt)))
                                    }
                                }
                            }
                        }
                        field.type == "boolean" -> {
                            val checked = (mcpArgsGet(args, field.name) as? JsonPrimitive)?.content == "true"
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { onArgsChange(mcpArgsPut(args, field.name, JsonPrimitive(it))) },
                                )
                                Text(field.name, color = ClapiColors.OnSurface, fontSize = 13.sp)
                            }
                        }
                        else -> {
                            val current = (mcpArgsGet(args, field.name) as? JsonPrimitive)?.content.orEmpty()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ClapiColors.Surface)
                                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                VariableAwareTextField(
                                    value = current,
                                    onValueChange = {
                                        onArgsChange(mcpArgsPut(args, field.name, mcpParseScalar(it, field.type)))
                                    },
                                    placeholder = field.name,
                                    state = state,
                                    modifier = Modifier.fillMaxWidth().testTag("$testTagPrefix-${field.name}"),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))) {
                CodeEditor(
                    text = args,
                    onTextChange = onArgsChange,
                    language = SyntaxLanguage.JSON,
                    modifier = Modifier.fillMaxSize(),
                    enableDownload = false,
                    showCursorPosition = state.settings.showEditorPositionIndicator,
                    testTagPrefix = testTagPrefix,
                )
            }
        }
    }
}


internal fun defaultPromptArgsJson(prompt: McpPrompt): String {
    if (prompt.arguments.isEmpty()) return "{}"
    return mcpDefaultArgsJson(mcpPromptSchema(prompt))
}
