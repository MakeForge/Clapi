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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.crustack.clapi.core.model.BodyType
import io.github.crustack.clapi.core.model.KeyValueEntry
import io.github.crustack.clapi.core.model.json.Json5
import io.github.crustack.clapi.core.network.VariableResolver
import io.github.crustack.clapi.core.network.grpc.GrpcCallRequest
import io.github.crustack.clapi.core.network.grpc.ProtoMethodInfo
import io.github.crustack.clapi.core.network.grpc.ProtoParseException
import io.github.crustack.clapi.core.network.grpc.parseGrpcEndpoint
import io.github.crustack.clapi.editor.core.LanguageMode
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.network.NetworkClientFactory
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.GrpcEditorTab
import io.github.crustack.clapi.ui.shared.state.LogLevel
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import io.github.crustack.clapi.ui.shared.theme.ClapiColors
import io.github.crustack.clapi.ui.shared.theme.CodeFontFamily
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * Request editor tailored for gRPC unary requests (`RequestKind.GRPC`).
 *
 * Includes:
 * - Top request bar with [GrpcBadge], variable-aware target URL input,
 *   Proto file selector dialog trigger, dynamic RPC method dropdown, Send/Stop, and Save buttons.
 * - Sub-tabs for **Message** (JSON payload editor), **Headers** (gRPC metadata key-values),
 *   and **Scripts** (Pre-request and Post-request scripts).
 */
@OptIn(ExperimentalSerializationApi::class)
@Composable
fun GrpcRequestEditor(
    tab: RequestTabState,
    state: AppState,
    onSend: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val markDirty = { tab.markDirty() }
    var showProtoDialog by remember { mutableStateOf(false) }

    val importPathsKey = state.collectionImportPaths.joinToString(";") { "${it.path}:${it.enabled}" }
    // Automatically parse the configured .proto file when opening a saved/imported tab or when collection imports change
    LaunchedEffect(tab.protoFilePath, importPathsKey) {
        val path = tab.protoFilePath?.trim().orEmpty()
        if (path.isNotEmpty() && !tab.isProtoLoading) {
            tab.isProtoLoading = true
            try {
                val includePaths = state.collectionImportPaths.filter { it.enabled }.map { it.path }
                val schema = NetworkClientFactory.createProtoParser().parseProtoFile(path, includePaths)
                val methods = schema.services.flatMap { it.methods }
                tab.availableMethods = methods
                tab.protoLoadError = null
                if (tab.grpcMethod.isNullOrBlank() && methods.isNotEmpty()) {
                    tab.grpcMethod = methods.first().fullMethodName
                    tab.grpcMethodType = methodTypeString(methods.first())
                }
            } catch (e: Exception) {
                tab.protoLoadError = e.message ?: "Failed to parse proto file"
                tab.availableMethods = emptyList()
            } finally {
                tab.isProtoLoading = false
            }
        }
    }

    if (showProtoDialog) {
        SelectProtoFileDialog(
            currentProtoFilePath = tab.protoFilePath,
            errorMessage = tab.protoLoadError,
            isLoading = tab.isProtoLoading,
            collectionProtoFiles = state.collectionProtoFiles.map { it.path },
            onSave = { selectedPath ->
                scope.launch {
                    tab.isProtoLoading = true
                    tab.protoLoadError = null
                    try {
                        val includePaths = state.collectionImportPaths.filter { it.enabled }.map { it.path }
                        val schema = NetworkClientFactory.createProtoParser().parseProtoFile(selectedPath, includePaths)
                        val methods = schema.services.flatMap { it.methods }
                        tab.protoFilePath = selectedPath
                        tab.availableMethods = methods
                        if (methods.isNotEmpty()) {
                            val current = tab.grpcMethod
                            val matched = methods.firstOrNull { it.fullMethodName == current } ?: methods.first()
                            tab.grpcMethod = matched.fullMethodName
                            tab.grpcMethodType = methodTypeString(matched)
                        } else {
                            tab.grpcMethod = null
                        }
                        markDirty()
                        showProtoDialog = false
                    } catch (e: ProtoParseException) {
                        tab.protoLoadError = e.message ?: "Failed to parse .proto file"
                        state.log("✗ Proto parse error: ${tab.protoLoadError}", LogLevel.ERROR)
                    } catch (e: Exception) {
                        tab.protoLoadError = e.message ?: "Failed to parse .proto file"
                        state.log("✗ Proto parse error: ${tab.protoLoadError}", LogLevel.ERROR)
                    } finally {
                        tab.isProtoLoading = false
                    }
                }
            },
            onDismiss = {
                tab.protoLoadError = null
                showProtoDialog = false
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ClapiColors.Background)
            .testTag("grpc-request-editor"),
    ) {
        // ── Top Request Bar ──────────────────────────────────────────
        GrpcRequestBar(
            tab = tab,
            state = state,
            onUrlChanged = { newUrl ->
                tab.url = newUrl
                markDirty()
            },
            onOpenProtoDialog = {
                tab.protoLoadError = null
                showProtoDialog = true
            },
            onMethodSelected = { methodInfo ->
                tab.grpcMethod = methodInfo.fullMethodName
                tab.grpcMethodType = methodTypeString(methodInfo)
                markDirty()
            },
            onSend = onSend,
            onCancel = onCancel,
            onSave = onSave,
        )

        // ── Sub-Tab Bar (Message / Headers / Scripts) ────────────────
        GrpcEditorTabBar(
            selectedTab = tab.selectedGrpcTab,
            onTabSelected = { tab.selectedGrpcTab = it },
            metadataCount = tab.grpcMetadata.count { it.key.isNotBlank() },
        )

        // ── Sub-Tab Content ──────────────────────────────────────────
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(1.dp)) {
            when (tab.selectedGrpcTab) {
                GrpcEditorTab.MESSAGE -> GrpcMessageEditor(
                    tab = tab,
                    state = state,
                    onDirty = markDirty,
                )
                GrpcEditorTab.HEADERS -> KeyValueEditor(
                    entries = tab.grpcMetadata,
                    tag = "metadata",
                    state = state,
                    onDirty = markDirty,
                )
                GrpcEditorTab.SCRIPTS -> GrpcScriptsTabContent(
                    tab = tab,
                    state = state,
                    onDirty = markDirty,
                )
            }
        }
    }
}

private fun methodTypeString(method: ProtoMethodInfo): String = when {
    method.isClientStreaming && method.isServerStreaming -> "bidi-streaming"
    method.isClientStreaming -> "client-streaming"
    method.isServerStreaming -> "server-streaming"
    else -> "unary"
}

/**
 * Resolves `{{variable}}` placeholders across the gRPC URL, metadata entries, and JSON message payload
 * using the active environment/folder/collection/global variable layers.
 */
fun resolveGrpcCallRequest(
    tab: RequestTabState,
    state: AppState,
    requestScopedVars: Map<String, String> = emptyMap(),
    urlOverride: String? = null,
    metadataOverride: List<KeyValueEntry>? = null,
    bodyOverride: String? = null,
): GrpcCallRequest {
    val baseLayers = state.activeVariableLayers(tab)
    val allLayers = if (requestScopedVars.isNotEmpty()) {
        listOf(requestScopedVars) + baseLayers
    } else {
        baseLayers
    }

    val rawUrl = (urlOverride ?: tab.url).trim()
    val resolvedUrl = VariableResolver.resolve(rawUrl, allLayers, removeUnresolved = false)
    val endpoint = parseGrpcEndpoint(resolvedUrl)

    val rawProtoPath = tab.protoFilePath?.trim().orEmpty()
    val resolvedProtoPath = VariableResolver.resolve(rawProtoPath, allLayers, removeUnresolved = false)

    val rawMethod = tab.grpcMethod?.trim().orEmpty()
    val resolvedMethod = VariableResolver.resolve(rawMethod, allLayers, removeUnresolved = false)

    val rawBody = bodyOverride ?: tab.grpcMessageBody
    val resolvedBody = VariableResolver.resolve(rawBody, allLayers, removeUnresolved = false)
    val wireJsonPayload = if (state.settings.allowJson5InJsonBodies && resolvedBody.isNotBlank()) {
        Json5.toWireJson(resolvedBody).getOrDefault(resolvedBody)
    } else {
        resolvedBody
    }

    val rawMetadata: List<KeyValueEntry> = metadataOverride ?: tab.grpcMetadata
        .filter { it.enabled && it.key.isNotBlank() }
        .map { KeyValueEntry(it.key, it.value, it.enabled, it.secret) }

    val resolvedMetadata = rawMetadata
        .filter { it.enabled && it.key.isNotBlank() }
        .map { entry ->
            VariableResolver.resolve(entry.key, allLayers, removeUnresolved = false) to
                VariableResolver.resolve(entry.value, allLayers, removeUnresolved = false)
        }

    val enabledImportPaths = state.collectionImportPaths
        .filter { it.enabled }
        .map { it.path }

    return GrpcCallRequest(
        requestId = tab.id,
        host = endpoint.host,
        port = endpoint.port,
        useTls = endpoint.useTls,
        protoFilePath = resolvedProtoPath,
        fullMethodName = resolvedMethod,
        jsonPayload = wireJsonPayload,
        metadata = resolvedMetadata,
        includePaths = enabledImportPaths,
    )
}

@Composable
private fun GrpcRequestBar(
    tab: RequestTabState,
    state: AppState,
    onUrlChanged: (String) -> Unit,
    onOpenProtoDialog: () -> Unit,
    onMethodSelected: (ProtoMethodInfo) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    var methodMenuExpanded by remember { mutableStateOf(false) }
    val protoLabel = remember(tab.protoFilePath) {
        val path = tab.protoFilePath?.trim().orEmpty()
        if (path.isEmpty()) "Proto"
        else path.substringAfterLast('/').substringAfterLast('\\').ifBlank { "Proto" }
    }
    val methodLabel = tab.grpcMethod?.takeIf { it.isNotBlank() } ?: "Select Method"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClapiColors.Surface)
            .padding(8.dp)
            .testTag("grpc-request-bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // gRPC badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(ClapiColors.MethodPatch.copy(alpha = 0.14f))
                .border(1.dp, ClapiColors.MethodPatch.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            GrpcBadge()
        }

        // URL input (variable-aware: highlights {{token}} in orange)
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
                onValueChange = onUrlChanged,
                placeholder = "Enter gRPC server URL (e.g. localhost:50051)…",
                textStyle = TextStyle(
                    color = ClapiColors.OnSurface,
                    fontSize = 14.sp,
                    fontFamily = CodeFontFamily,
                ),
                state = state,
                tab = tab,
                undoStack = tab.urlUndoStack,
                modifier = Modifier.fillMaxWidth().testTag("url-input"),
            )
        }

        // Proto file button
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(ClapiColors.SurfaceContainer)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenProtoDialog)
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("grpc-proto-button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = "Select Proto File",
                tint = ClapiColors.MethodPatch,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = protoLabel,
                color = ClapiColors.OnSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Method selector dropdown
        Box {
            Row(
                modifier = Modifier
                    .widthIn(min = 140.dp, max = 260.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ClapiColors.SurfaceContainer)
                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                    .clickable { methodMenuExpanded = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("grpc-method-dropdown"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = methodLabel,
                    color = if (tab.grpcMethod.isNullOrBlank()) ClapiColors.OnSurfaceDim else ClapiColors.OnSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = ClapiColors.OnSurfaceDim,
                    modifier = Modifier.size(18.dp),
                )
            }

            DropdownMenu(
                expanded = methodMenuExpanded,
                onDismissRequest = { methodMenuExpanded = false },
            ) {
                if (tab.availableMethods.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Select a .proto file first",
                                color = ClapiColors.OnSurfaceDim,
                                fontSize = 12.sp,
                            )
                        },
                        onClick = {
                            methodMenuExpanded = false
                            onOpenProtoDialog()
                        },
                        modifier = Modifier.testTag("grpc-method-empty-item"),
                    )
                } else {
                    tab.availableMethods.forEach { methodInfo ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = methodInfo.fullMethodName,
                                    color = ClapiColors.OnSurface,
                                    fontSize = 12.sp,
                                    fontFamily = CodeFontFamily,
                                )
                            },
                            onClick = {
                                onMethodSelected(methodInfo)
                                methodMenuExpanded = false
                            },
                            modifier = Modifier.testTag("grpc-method-item-${methodInfo.fullMethodName}"),
                        )
                    }
                }
            }
        }

        // Send / Stop button
        GrpcSendButton(
            isLoading = tab.isLoading,
            onClick = onSend,
            onCancel = onCancel,
        )

        // Save button
        GrpcSaveButton(
            isLoading = tab.isLoading,
            onClick = onSave,
        )
    }
}

@Composable
private fun GrpcSendButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    onCancel: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    if (isLoading) {
        Box(
            modifier = Modifier
                .widthIn(min = 80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isHovered) Color(0xFFD32F2F)
                    else Color(0xFFC62828)
                )
                .hoverable(interactionSource)
                .clickable(onClick = onCancel)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("stop-button"),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    Icons.Default.Stop,
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
                .background(if (isHovered) ClapiColors.Primary.copy(alpha = 0.9f) else ClapiColors.Primary)
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("send-button"),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = Strings.send,
                    color = ClapiColors.OnPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = ClapiColors.OnPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun GrpcSaveButton(
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .widthIn(min = 72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isHovered) ClapiColors.SurfaceHigh else ClapiColors.SurfaceContainer)
            .hoverable(interactionSource)
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("save-button"),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = Strings.save,
                color = ClapiColors.OnSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Icon(
                Icons.Default.Save,
                contentDescription = null,
                tint = ClapiColors.OnSurface,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun GrpcEditorTabBar(
    selectedTab: GrpcEditorTab,
    onTabSelected: (GrpcEditorTab) -> Unit,
    metadataCount: Int,
) {
    val tabs = GrpcEditorTab.entries
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClapiColors.Surface)
            .testTag("grpc-editor-tabs"),
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
                val countBadge = if (tab == GrpcEditorTab.HEADERS && metadataCount > 0) " ($metadataCount)" else ""
                Tab(
                    selected = selected,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("grpc-tab-${tab.name.lowercase()}"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 12.dp),
                    ) {
                        when (tab) {
                            GrpcEditorTab.MESSAGE -> Icon(
                                Icons.Default.Code,
                                contentDescription = null,
                                tint = if (selected) ClapiColors.Primary else ClapiColors.OnSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            GrpcEditorTab.SCRIPTS -> Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (selected) ClapiColors.Primary else ClapiColors.OnSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            else -> Unit
                        }
                        Text(
                            text = "${tab.label}$countBadge",
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) ClapiColors.Primary else ClapiColors.OnSurfaceVariant,
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(ClapiColors.Border),
        )
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Composable
private fun GrpcMessageEditor(
    tab: RequestTabState,
    state: AppState,
    onDirty: () -> Unit,
) {
    val definedVarNames: Set<String> = state.activeVariableLayers(tab).flatMap { it.keys }.toSet()
    var popupVariable by remember { mutableStateOf<String?>(null) }
    val allowJson5 = state.settings.allowJson5InJsonBodies
    val bodyViewModel = remember(tab, allowJson5) {
        tab.getOrCreateBodyViewModel(
            bodyType = BodyType.JSON,
            initialText = tab.grpcMessageBody,
            languageMode = LanguageMode.JSON,
            allowJson5 = allowJson5,
        )
    }

    Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        CodeEditor(
            text = tab.grpcMessageBody,
            onTextChange = {
                tab.grpcMessageBody = it
                onDirty()
            },
            language = SyntaxLanguage.JSON,
            modifier = Modifier.fillMaxSize(),
            showToolbar = true,
            enableFolding = true,
            enableSearch = true,
            enableFormat = true,
            enableWordWrap = true,
            enableCopy = true,
            enableDownload = false,
            showCursorPosition = state.settings.showEditorPositionIndicator,
            placeholder = "{\n  \"key\": \"value\"\n}",
            testTagPrefix = "grpc-message-editor",
            onCursorTap = { offset ->
                popupVariable = variableNameAtOffset(tab.grpcMessageBody, offset)
            },
            lineVariableSpans = { lineText, _ ->
                variableRangesForLine(lineText, definedVarNames)
            },
            allowJson5 = allowJson5,
            availableVariables = getAvailableVariables(state, tab),
            externalViewModel = bodyViewModel,
        )

        if (popupVariable != null) {
            VariableEditorPopup(
                variableName = popupVariable!!,
                state = state,
                tab = tab,
                onDismiss = { popupVariable = null },
            )
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Composable
private fun GrpcScriptsTabContent(
    tab: RequestTabState,
    state: AppState,
    onDirty: () -> Unit,
) {
    var selectedScriptPhase by remember { mutableStateOf(0) } // 0 = Pre-request, 1 = Post-request

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ClapiColors.SurfaceContainer)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("Pre-request" to "grpc-script-tab-pre", "Post-request" to "grpc-script-tab-post")
                .forEachIndexed { idx, (label, tag) ->
                    val active = selectedScriptPhase == idx
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) ClapiColors.Primary.copy(alpha = 0.16f) else Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = if (active) ClapiColors.Primary else ClapiColors.Border,
                                shape = RoundedCornerShape(6.dp),
                            )
                            .clickable { selectedScriptPhase = idx }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .testTag(tag),
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

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (selectedScriptPhase == 0) {
                ScriptEditor(
                    script = tab.preRequestScript,
                    onScriptChanged = {
                        tab.preRequestScript = it
                        onDirty()
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
                        onDirty()
                    },
                    title = "Post-request Script",
                    showCursorPosition = state.settings.showEditorPositionIndicator,
                    availableVariables = getAvailableVariables(state, tab),
                )
            }
        }
    }
}
