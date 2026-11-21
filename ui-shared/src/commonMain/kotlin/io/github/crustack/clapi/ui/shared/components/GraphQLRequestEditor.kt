package io.github.crustack.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.crustack.clapi.core.model.opencollection.GraphQLBody
import io.github.crustack.clapi.core.model.opencollection.GraphQLBodyVariant
import io.github.crustack.clapi.ui.shared.generated.resources.Res
import io.github.crustack.clapi.ui.shared.generated.resources.ic_add
import io.github.crustack.clapi.ui.shared.generated.resources.ic_arrow_drop_down
import io.github.crustack.clapi.ui.shared.generated.resources.ic_delete
import io.github.crustack.clapi.ui.shared.generated.resources.ic_edit
import io.github.crustack.clapi.ui.shared.generated.resources.ic_format_align_left
import io.github.crustack.clapi.ui.shared.generated.resources.ic_send
import io.github.crustack.clapi.ui.shared.generated.resources.ic_stop
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.shortcuts.onSaveHotkey
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.GraphQLEditorTab
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import io.github.crustack.clapi.ui.shared.theme.ClapiColors
import io.github.crustack.clapi.ui.shared.theme.CodeFontFamily
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.compose.resources.painterResource

/**
 * Top-level editor for GraphQL requests (`RequestKind.GRAPHQL`).
 */
@OptIn(ExperimentalSerializationApi::class)
@Composable
fun GraphQLRequestEditor(
    tab: RequestTabState,
    state: AppState,
    onSend: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val markDirty = { tab.markDirty() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClapiColors.Background)
            .onSaveHotkey { onSave() }
            .testTag("graphql-request-editor"),
    ) {
        // ── Top Address Bar ──────────────────────────────────────────
        GraphQLAddressBar(
            tab = tab,
            state = state,
            onSend = onSend,
            onCancel = onCancel,
            onSave = onSave,
            markDirty = markDirty,
        )

        // ── Sub-Tabs Bar ─────────────────────────────────────────────
        ScrollableTabRow(
            selectedTabIndex = tab.selectedGraphQLTab.ordinal,
            containerColor = ClapiColors.Surface,
            contentColor = ClapiColors.Primary,
            edgePadding = 8.dp,
            modifier = Modifier.fillMaxWidth().height(36.dp),
        ) {
            GraphQLEditorTab.entries.forEach { editorTab ->
                val isSelected = tab.selectedGraphQLTab == editorTab
                val label = when (editorTab) {
                    GraphQLEditorTab.HEADERS -> "${editorTab.label} (${tab.headers.size})"
                    GraphQLEditorTab.PARAMS -> "${editorTab.label} (${tab.params.size})"
                    else -> editorTab.label
                }
                Tab(
                    selected = isSelected,
                    onClick = { tab.selectedGraphQLTab = editorTab },
                    text = {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) ClapiColors.Primary else ClapiColors.OnSurfaceVariant,
                            maxLines = 1,
                        )
                    },
                    modifier = Modifier.testTag("graphql-tab-${editorTab.name.lowercase()}"),
                )
            }
        }

        // ── Sub-Tab Content ──────────────────────────────────────────
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(4.dp)) {
            when (tab.selectedGraphQLTab) {
                GraphQLEditorTab.BODY -> GraphQLBodyPane(
                    tab = tab,
                    state = state,
                    markDirty = markDirty,
                )
                GraphQLEditorTab.HEADERS -> KeyValueEditor(
                    entries = tab.headers,
                    tag = "headers",
                    state = state,
                    tab = tab,
                    onDirty = markDirty,
                )
                GraphQLEditorTab.PARAMS -> KeyValueEditor(
                    entries = tab.params,
                    tag = "params",
                    state = state,
                    tab = tab,
                    onDirty = markDirty,
                )
                GraphQLEditorTab.AUTH -> AuthEditor(
                    tab = tab,
                    state = state,
                    onDirty = markDirty,
                )
                GraphQLEditorTab.SCRIPTS -> GraphQLScriptsPane(
                    tab = tab,
                    state = state,
                    markDirty = markDirty,
                )
                GraphQLEditorTab.SETTINGS -> GraphQLSettingsPane(
                    tab = tab,
                    markDirty = markDirty,
                )
                GraphQLEditorTab.DOCS -> GraphQLDocsPane(
                    tab = tab,
                    markDirty = markDirty,
                )
            }
        }
    }
}

// ── Top Address Bar ─────────────────────────────────────────────────

@Composable
private fun GraphQLAddressBar(
    tab: RequestTabState,
    state: AppState,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    markDirty: () -> Unit,
) {
    var methodMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClapiColors.Surface)
            .border(width = 1.dp, color = ClapiColors.Border)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GraphQLBadge(compact = false)

        // Method selector dropdown (POST / GET)
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ClapiColors.SurfaceContainer)
                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                    .clickable { methodMenuExpanded = true }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .testTag("graphql-method-selector"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = tab.graphQlMethod,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = CodeFontFamily,
                    color = ClapiColors.MethodGraphQl,
                )
                Icon(
                    painter = painterResource(Res.drawable.ic_arrow_drop_down),
                    contentDescription = null,
                    tint = ClapiColors.OnSurfaceDim,
                    modifier = Modifier.size(14.dp),
                )
            }

            DropdownMenu(
                expanded = methodMenuExpanded,
                onDismissRequest = { methodMenuExpanded = false },
            ) {
                listOf("POST", "GET").forEach { m ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = m,
                                fontWeight = if (tab.graphQlMethod == m) FontWeight.Bold else FontWeight.Normal,
                                color = if (tab.graphQlMethod == m) ClapiColors.MethodGraphQl else ClapiColors.OnSurface,
                                fontFamily = CodeFontFamily,
                            )
                        },
                        onClick = {
                            tab.graphQlMethod = m
                            methodMenuExpanded = false
                            markDirty()
                        },
                        modifier = Modifier.testTag("graphql-method-option-$m"),
                    )
                }
            }
        }

        // URL input with variable token resolution
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(ClapiColors.SurfaceContainer)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            VariableAwareTextField(
                value = tab.url,
                onValueChange = { newUrl ->
                    tab.url = newUrl
                    markDirty()
                },
                placeholder = "https://example.com/graphql",
                textStyle = TextStyle(
                    color = ClapiColors.OnSurface,
                    fontSize = 14.sp,
                    fontFamily = CodeFontFamily,
                ),
                state = state,
                tab = tab,
                undoStack = tab.urlUndoStack,
                modifier = Modifier.fillMaxWidth().testTag("graphql-url-input"),
            )
        }

        // Send / Stop Button
        val sendInteraction = remember { MutableInteractionSource() }
        val sendHovered by sendInteraction.collectIsHoveredAsState()

        if (tab.isLoading) {
            Box(
                modifier = Modifier
                    .widthIn(min = 80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (sendHovered) Color(0xFFD32F2F) else Color(0xFFC62828))
                    .hoverable(sendInteraction)
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("graphql-stop-button"),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_stop),
                        contentDescription = Strings.t("stop_request"),
                        tint = ClapiColors.OnPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = Strings.t("stop"),
                        color = ClapiColors.OnPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .widthIn(min = 80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (sendHovered) ClapiColors.MethodGraphQl.copy(alpha = 0.85f) else ClapiColors.MethodGraphQl)
                    .hoverable(sendInteraction)
                    .clickable(onClick = onSend)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("graphql-send-button"),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = Strings.send,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Icon(
                        painter = painterResource(Res.drawable.ic_send),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // Save Button
        SaveButton(isLoading = false, onClick = onSave)
    }
}

// ── Body Pane (Variants + Query + Variables) ─────────────────────────

@OptIn(ExperimentalSerializationApi::class)
@Composable
private fun GraphQLBodyPane(
    tab: RequestTabState,
    state: AppState,
    markDirty: () -> Unit,
) {
    var splitFraction by remember { mutableStateOf(0.65f) }
    var renamingVariantIndex by remember { mutableStateOf<Int?>(null) }
    var renameTitleValue by remember { mutableStateOf("") }

    // Rename Dialog
    renamingVariantIndex?.let { idx ->
        Dialog(onDismissRequest = { renamingVariantIndex = null }) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(ClapiColors.Surface)
                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(10.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Rename Variant",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ClapiColors.OnSurface,
                )
                BasicTextField(
                    value = renameTitleValue,
                    onValueChange = { renameTitleValue = it },
                    singleLine = true,
                    textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 13.sp),
                    cursorBrush = SolidColor(ClapiColors.Primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(ClapiColors.SurfaceContainer)
                        .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("graphql-rename-variant-input"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clickable { renamingVariantIndex = null }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("Cancel", color = ClapiColors.OnSurfaceDim, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ClapiColors.Primary)
                            .clickable {
                                if (renameTitleValue.isNotBlank() && idx in tab.graphQlVariants.indices) {
                                    val current = tab.graphQlVariants[idx]
                                    tab.graphQlVariants[idx] = current.copy(title = renameTitleValue.trim())
                                    markDirty()
                                }
                                renamingVariantIndex = null
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("graphql-rename-variant-confirm"),
                    ) {
                        Text("Rename", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Variant Management Strip ─────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ClapiColors.Surface)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "VARIANTS:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ClapiColors.OnSurfaceDim,
            )

            if (tab.graphQlVariants.isEmpty()) {
                // Show single default variant representation
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ClapiColors.Primary.copy(alpha = 0.15f))
                        .border(1.dp, ClapiColors.Primary.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Default",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ClapiColors.Primary,
                    )
                }
            } else {
                tab.graphQlVariants.forEachIndexed { index, variant ->
                    val isActive = index == tab.activeGraphQLVariantIndex
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isActive) ClapiColors.Primary.copy(alpha = 0.15f) else ClapiColors.SurfaceContainer)
                            .border(
                                1.dp,
                                if (isActive) ClapiColors.Primary.copy(alpha = 0.5f) else ClapiColors.Border,
                                RoundedCornerShape(6.dp),
                            )
                            .clickable {
                                tab.activeGraphQLVariantIndex = index
                                tab.graphQlVariants.indices.forEach { i ->
                                    val v = tab.graphQlVariants[i]
                                    tab.graphQlVariants[i] = v.copy(selected = (i == index))
                                }
                                markDirty()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("graphql-variant-chip-$index"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = variant.title,
                            fontSize = 11.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isActive) ClapiColors.Primary else ClapiColors.OnSurface,
                        )

                        // Rename icon
                        Icon(
                            painter = painterResource(Res.drawable.ic_edit),
                            contentDescription = "Rename",
                            tint = ClapiColors.OnSurfaceDim,
                            modifier = Modifier
                                .size(12.dp)
                                .clickable {
                                    renameTitleValue = variant.title
                                    renamingVariantIndex = index
                                },
                        )

                        // Delete icon (if more than 1 variant)
                        if (tab.graphQlVariants.size > 1) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_delete),
                                contentDescription = "Delete",
                                tint = ClapiColors.OnSurfaceDim,
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable {
                                        tab.graphQlVariants.removeAt(index)
                                        if (tab.activeGraphQLVariantIndex >= tab.graphQlVariants.size) {
                                            tab.activeGraphQLVariantIndex = tab.graphQlVariants.size - 1
                                        }
                                        if (tab.graphQlVariants.isNotEmpty() && tab.graphQlVariants.none { it.selected == true }) {
                                            val current = tab.graphQlVariants[tab.activeGraphQLVariantIndex]
                                            tab.graphQlVariants[tab.activeGraphQLVariantIndex] = current.copy(selected = true)
                                        }
                                        markDirty()
                                    },
                            )
                        }
                    }
                }
            }

            // Plus icon to add another variant
            Icon(
                painter = painterResource(Res.drawable.ic_add),
                contentDescription = "Add Variant",
                tint = ClapiColors.Primary,
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        if (tab.graphQlVariants.isEmpty()) {
                            tab.graphQlVariants.add(
                                GraphQLBodyVariant(
                                    title = "Default",
                                    selected = false,
                                    body = GraphQLBody(query = tab.graphQlQuery, variables = tab.graphQlVariables),
                                )
                            )
                        }
                        val nextNumber = tab.graphQlVariants.size + 1
                        tab.graphQlVariants.add(
                            GraphQLBodyVariant(
                                title = "Variant $nextNumber",
                                selected = true,
                                body = GraphQLBody(query = "query {\n  \n}", variables = "{\n  \n}"),
                            )
                        )
                        tab.activeGraphQLVariantIndex = tab.graphQlVariants.size - 1
                        tab.graphQlVariants.indices.forEach { i ->
                            val v = tab.graphQlVariants[i]
                            tab.graphQlVariants[i] = v.copy(selected = (i == tab.activeGraphQLVariantIndex))
                        }
                        markDirty()
                    }
                    .testTag("graphql-add-variant"),
            )
        }

        Spacer(Modifier.height(4.dp))

        // ── Split Pane: Query Editor (Top) & Variables Editor (Bottom) ─
        VerticalSplitPane(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            splitFraction = splitFraction,
            onSplitChanged = { splitFraction = it },
            first = {
                // GraphQL Query Editor
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ClapiColors.Surface)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "GRAPHQL QUERY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ClapiColors.MethodGraphQl,
                            fontFamily = CodeFontFamily,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ClapiColors.MethodGraphQl.copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    tab.effectiveGraphQlQuery = formatGraphQL(tab.effectiveGraphQlQuery)
                                    markDirty()
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("graphql-prettify-button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_format_align_left),
                                contentDescription = Strings.t("graphql_prettify"),
                                tint = ClapiColors.OnSurfaceDim,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = Strings.t("graphql_prettify"),
                                fontSize = 11.sp,
                                color = ClapiColors.OnSurfaceDim,
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        CodeEditor(
                            text = tab.effectiveGraphQlQuery,
                            onTextChange = {
                                tab.effectiveGraphQlQuery = it
                                markDirty()
                            },
                            language = SyntaxLanguage.GRAPHQL,
                            placeholder = "query {\n  \n}",
                            modifier = Modifier.fillMaxSize(),
                            showToolbar = true,
                            enableFolding = true,
                            enableSearch = true,
                            enableFormat = true,
                            enableWordWrap = true,
                            enableCopy = true,
                            showCursorPosition = state.settings.showEditorPositionIndicator,
                            testTagPrefix = "graphql-query-editor",
                        )
                    }
                }
            },
            second = {
                // GraphQL Variables Editor
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ClapiColors.Surface)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "VARIABLES (JSON)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFA500),
                            fontFamily = CodeFontFamily,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFFA500).copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    tab.effectiveGraphQlVariables = formatJsonSafe(tab.effectiveGraphQlVariables)
                                    markDirty()
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("graphql-format-variables-button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_format_align_left),
                                contentDescription = Strings.format,
                                tint = ClapiColors.OnSurfaceDim,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = Strings.format,
                                fontSize = 11.sp,
                                color = ClapiColors.OnSurfaceDim,
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        CodeEditor(
                            text = tab.effectiveGraphQlVariables,
                            onTextChange = {
                                tab.effectiveGraphQlVariables = it
                                markDirty()
                            },
                            language = SyntaxLanguage.JSON,
                            placeholder = "{\n  \n}",
                            modifier = Modifier.fillMaxSize(),
                            showToolbar = true,
                            enableFolding = true,
                            enableSearch = true,
                            enableFormat = true,
                            enableWordWrap = true,
                            enableCopy = true,
                            showCursorPosition = state.settings.showEditorPositionIndicator,
                            testTagPrefix = "graphql-variables-editor",
                        )
                    }
                }
            },
        )
    }
}

// ── Scripts Pane ────────────────────────────────────────────────────

@OptIn(ExperimentalSerializationApi::class)
@Composable
private fun GraphQLScriptsPane(
    tab: RequestTabState,
    state: AppState,
    markDirty: () -> Unit,
) {
    var selectedScriptPhase by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ClapiColors.Surface)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("Pre-request Script", "Post-request Script").forEachIndexed { idx, label ->
                val active = selectedScriptPhase == idx
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (active) ClapiColors.Primary.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { selectedScriptPhase = idx }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("graphql-script-tab-$idx"),
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) ClapiColors.Primary else ClapiColors.OnSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (selectedScriptPhase == 0) {
                ScriptEditor(
                    script = tab.preRequestScript,
                    onScriptChanged = {
                        tab.preRequestScript = it
                        markDirty()
                    },
                    title = "Pre-request Script",
                    showCursorPosition = state.settings.showEditorPositionIndicator,
                    availableVariables = getAvailableVariables(state, tab),
                )
            } else {
                ScriptEditor(
                    script = tab.testScript,
                    onScriptChanged = {
                        tab.testScript = it
                        markDirty()
                    },
                    title = "Post-request Script",
                    showCursorPosition = state.settings.showEditorPositionIndicator,
                    availableVariables = getAvailableVariables(state, tab),
                )
            }
        }
    }
}

// ── Settings Pane ───────────────────────────────────────────────────

@Composable
private fun GraphQLSettingsPane(
    tab: RequestTabState,
    markDirty: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "GraphQL Execution Settings",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = ClapiColors.OnSurface,
        )

        // Timeout
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Timeout (ms or 'inherit')",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = ClapiColors.OnSurfaceVariant,
            )
            BasicTextField(
                value = tab.graphQlTimeout ?: "inherit",
                onValueChange = {
                    tab.graphQlTimeout = it
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
                    .testTag("graphql-settings-timeout"),
            )
        }

        // Encode URL
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Encode URL ('inherit', 'true', 'false')",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = ClapiColors.OnSurfaceVariant,
            )
            BasicTextField(
                value = tab.graphQlEncodeUrl ?: "inherit",
                onValueChange = {
                    tab.graphQlEncodeUrl = it
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
                    .testTag("graphql-settings-encode-url"),
            )
        }

        // Follow Redirects
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Follow Redirects ('inherit', 'true', 'false')",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = ClapiColors.OnSurfaceVariant,
            )
            BasicTextField(
                value = tab.graphQlFollowRedirects ?: "inherit",
                onValueChange = {
                    tab.graphQlFollowRedirects = it
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
                    .testTag("graphql-settings-follow-redirects"),
            )
        }

        // Forward Authorization Header
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Forward Authorization Header on Redirect ('inherit', 'true', 'false')",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = ClapiColors.OnSurfaceVariant,
            )
            BasicTextField(
                value = tab.graphQlForwardAuthHeader ?: "inherit",
                onValueChange = {
                    tab.graphQlForwardAuthHeader = it
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
                    .testTag("graphql-settings-forward-auth"),
            )
        }

        // Max Redirects
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Max Redirects (integer or 'inherit')",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = ClapiColors.OnSurfaceVariant,
            )
            BasicTextField(
                value = tab.graphQlMaxRedirects ?: "inherit",
                onValueChange = {
                    tab.graphQlMaxRedirects = it
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
                    .testTag("graphql-settings-max-redirects"),
            )
        }
    }
}

// ── Docs Pane ───────────────────────────────────────────────────────

@OptIn(ExperimentalSerializationApi::class)
@Composable
private fun GraphQLDocsPane(
    tab: RequestTabState,
    markDirty: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text(
            text = "Request Documentation (Markdown)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = ClapiColors.OnSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp)),
        ) {
            CodeEditor(
                text = tab.graphQlDocs,
                onTextChange = {
                    tab.graphQlDocs = it
                    markDirty()
                },
                language = SyntaxLanguage.PLAIN,
                modifier = Modifier.fillMaxSize(),
                placeholder = "Add documentation for this GraphQL request…",
                testTagPrefix = "graphql-docs-editor",
            )
        }
    }
}
