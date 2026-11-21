// Modified by MakeForge (2026)
package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.ui.shared.platform.PlatformHorizontalScrollbar
import kotlinx.coroutines.launch
import io.github.makeforge.clapi.core.model.BodyType
import io.github.makeforge.clapi.core.model.FormEntryType
import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.ui.shared.components.executor.buildCurlCommand
import io.github.makeforge.clapi.ui.shared.components.executor.buildPowerShellCommand
import io.github.makeforge.clapi.ui.shared.components.executor.buildPythonCommand
import io.github.makeforge.clapi.ui.shared.components.executor.copyHeaderList
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_check_circle
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_code
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_lock
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_play_arrow
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.shortcuts.onSaveHotkey
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import io.github.makeforge.clapi.ui.shared.state.RequestEditorTab
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme
import io.ktor.http.*
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.compose.resources.painterResource
import io.github.makeforge.clapi.ui.shared.platform.copyToClipboard as platformCopyToClipboard

/**
 * Top-level composable for a single request tab's editor area.
 *
 * Delegates to focused sub-composables:
 *  - [RequestBar]      — method + URL + action buttons  (RequestBar.kt)
 *  - [EditorTabBar]    — Params / Headers / Body / Auth / Script tab selector
 *  - [KeyValueEditor]  — params and headers tables      (KeyValueEditor.kt)
 *  - [BodyEditor]      — body type + content            (BodyEditor.kt)
 *  - [AuthEditor]      — auth type + credentials        (AuthEditor.kt)
 *  - [ScriptEditor]    — pre-request / post-request scripts (ScriptEditor.kt)
 */
@OptIn(ExperimentalSerializationApi::class)
@Composable
fun RequestEditor(
    tab: RequestTabState,
    state: AppState,
    onSend: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    /** Cancels the in-flight request when the Stop button is pressed (H-2). */
    onCancel: () -> Unit = {},
) {
    if (tab.kind == RequestKind.GRPC) {
        GrpcRequestEditor(
            tab = tab,
            state = state,
            onSend = onSend,
            onSave = onSave,
            onCancel = onCancel,
            modifier = modifier,
        )
        return
    }

    if (tab.kind == RequestKind.WEBSOCKET) {
        WebSocketRequestEditor(
            tab = tab,
            state = state,
            onSave = onSave,
            modifier = modifier,
        )
        return
    }

    if (tab.kind == RequestKind.GRAPHQL) {
        GraphQLRequestEditor(
            tab = tab,
            state = state,
            onSend = onSend,
            onSave = onSave,
            onCancel = onCancel,
            modifier = modifier,
        )
        return
    }

    val markDirty = { tab.markDirty() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClapiColors.Background)
            .onSaveHotkey { onSave() }
            .testTag("request-editor"),
    ) {
        RequestBar(
            method = tab.method,
            onMethodChanged = { tab.method = it; markDirty() },
            url = tab.url,
            onUrlChanged = { newUrl ->
                tab.url = newUrl
                syncParamsFromUrl(tab, newUrl)
                markDirty()
            },
            isLoading = tab.isLoading,
            onSend = onSend,
            onCancel = onCancel,
            onSave = onSave,
            copyFormats = buildCopyFormats(tab, state),
            retryEnabled = tab.retryEnabled,
            retryCount = tab.retryCount,
            retryDelayMs = tab.retryDelayMs,
            onRetryEnabledChanged = { tab.retryEnabled = it; markDirty() },
            onRetryCountChanged = { tab.retryCount = it; markDirty() },
            onRetryDelayChanged = { tab.retryDelayMs = it; markDirty() },
            state = state,
            tab = tab,
            urlUndoStack = tab.urlUndoStack,
        )

        EditorTabBar(
            selectedTab = tab.selectedEditorTab,
            onTabSelected = { tab.selectedEditorTab = it },
            paramCount = tab.params.size,
            headerCount = tab.headers.size,
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(1.dp)) {
            when (tab.selectedEditorTab) {
                RequestEditorTab.PARAMS      -> KeyValueEditor(tab.params, "param", state = state, tab = tab) {
                    syncUrlFromParams(tab)
                    markDirty()
                }
                RequestEditorTab.HEADERS     -> KeyValueEditor(tab.headers, "header", state = state, tab = tab) { markDirty() }
                RequestEditorTab.BODY        -> BodyEditor(tab, state) { markDirty() }
                RequestEditorTab.AUTH        -> AuthEditor(tab, state) { markDirty() }
                RequestEditorTab.PRE_REQUEST -> ScriptEditor(
                    script          = tab.preRequestScript,
                    onScriptChanged = { tab.preRequestScript = it; markDirty() },
                    title           = "Pre-request Script",
                    showCursorPosition = state.settings.showEditorPositionIndicator,
                    availableVariables = getAvailableVariables(state, tab),
                )
                RequestEditorTab.TESTS       -> ScriptEditor(
                    script          = tab.testScript,
                    onScriptChanged = { tab.testScript = it; markDirty() },
                    title           = "Post-request Script",
                    showCursorPosition = state.settings.showEditorPositionIndicator,
                    availableVariables = getAvailableVariables(state, tab),
                )
                RequestEditorTab.VARIABLES   -> Unit
            }
        }
    }
}

// ── URL ↔ Params two-way sync ───────────────────────────────────

/**
 * Parses query parameters out of [url] and replaces the tab's params list.
 * Called whenever the user edits the URL field directly.
 * Rows hold literal values decoded once from the pasted URL.
 */
fun syncParamsFromUrl(tab: RequestTabState, url: String) {
    val parts = splitRequestUrl(url)
    tab.params.clear()
    tab.params.addAll(parseQueryPairs(parts.query).map { MutableKeyValue(key = it.first, value = it.second) })
}

/**
 * Rebuilds the URL's query string from the current params table.
 * Called whenever a param key, value, or enabled-state changes.
 */
fun syncUrlFromParams(tab: RequestTabState) {
    val parts = splitRequestUrl(tab.url)
    val enabled = tab.params.filter { it.enabled && it.key.isNotBlank() }
    val query = encodeOrderedQuery(enabled.map { it.key to it.value }, preserveTemplates = true)
    tab.url = joinRequestUrl(parts.base, query, parts.fragment)
}

internal data class RequestUrlParts(val base: String, val query: String, val fragment: String)

internal fun splitRequestUrl(url: String): RequestUrlParts {
    val hashIdx = url.indexOf('#')
    val withoutFrag = if (hashIdx >= 0) url.substring(0, hashIdx) else url
    val fragment = if (hashIdx >= 0) url.substring(hashIdx + 1) else ""
    val qIdx = withoutFrag.indexOf('?')
    val base = if (qIdx >= 0) withoutFrag.substring(0, qIdx) else withoutFrag
    val query = if (qIdx >= 0 && qIdx < withoutFrag.lastIndex) withoutFrag.substring(qIdx + 1) else ""
    return RequestUrlParts(base, query, fragment)
}

internal fun joinRequestUrl(base: String, query: String, fragment: String): String {
    val withQuery = if (query.isEmpty()) base else "$base?$query"
    return if (fragment.isEmpty()) withQuery else "$withQuery#$fragment"
}

internal fun parseQueryPairs(query: String): List<Pair<String, String>> {
    if (query.isEmpty()) return emptyList()
    return query.split('&').mapNotNull { pair ->
        if (pair.isBlank()) return@mapNotNull null
        val eqIdx = pair.indexOf('=')
        when {
            eqIdx < 0 -> decodeQueryComponent(pair) to ""
            eqIdx == 0 -> "" to decodeQueryComponent(pair.substring(1))
            else -> decodeQueryComponent(pair.substring(0, eqIdx)) to
                decodeQueryComponent(pair.substring(eqIdx + 1))
        }
    }
}

private fun decodeQueryComponent(value: String): String =
    runCatching { value.decodeURLQueryComponent() }.getOrDefault(value)

private val templateToken = Regex("\\{\\{[^{}]+}}")

internal fun encodeQueryComponent(value: String, preserveTemplates: Boolean = false): String {
    if (!preserveTemplates) return value.encodeURLParameter()
    val result = StringBuilder()
    var offset = 0
    templateToken.findAll(value).forEach { match ->
        result.append(value.substring(offset, match.range.first).encodeURLParameter())
        result.append(match.value)
        offset = match.range.last + 1
    }
    result.append(value.substring(offset).encodeURLParameter())
    return result.toString()
}

internal fun encodeOrderedQuery(entries: List<Pair<String, String>>, preserveTemplates: Boolean = false): String =
    entries.joinToString("&") { (key, value) ->
        "${encodeQueryComponent(key, preserveTemplates)}=${encodeQueryComponent(value, preserveTemplates)}"
    }

private fun copyToClipboard(text: String) {
    runCatching { platformCopyToClipboard(text) }
}

/**
 * Builds the list of (label, action) pairs shown in the copy-as dropdown.
 * Variables from the active environment are resolved for "resolved" variants.
 */
internal fun buildCopyFormats(tab: RequestTabState, state: AppState): List<CopyFormatOption> {
    val layers = state.activeVariableLayers(tab)
    val fileReason = when {
        tab.bodyType == BodyType.BINARY -> "Embedded binary has no reusable file path"
        tab.bodyType == BodyType.FORM_DATA && tab.formRows.any { it.enabled && it.type == FormEntryType.FILE } ->
            "Embedded multipart file has no reusable file path"
        else -> null
    }
    val headers = copyHeaderList(tab, layers, omitMultipartContentType = false)
    val duplicateHeaders = headers.map { it.first.lowercase() }.distinct().size != headers.size
    val mapReason = fileReason ?: if (duplicateHeaders) "Duplicate headers cannot be represented by this API" else null
    val multipartKeys = tab.formRows.filter { it.enabled }.map { it.key }
    val powerShellReason = mapReason ?: if (
        tab.bodyType == BodyType.FORM_DATA && multipartKeys.distinct().size != multipartKeys.size
    ) "Duplicate multipart field names cannot be represented by PowerShell -Form" else null
    return listOf(
        CopyFormatOption("cURL", fileReason == null, fileReason) {
            copyToClipboard(buildCurlCommand(tab, layers, state.settings.allowJson5InJsonBodies, state))
        },
        CopyFormatOption("Python", mapReason == null, mapReason) {
            copyToClipboard(buildPythonCommand(tab, layers, state.settings.allowJson5InJsonBodies, state))
        },
        CopyFormatOption("PowerShell", powerShellReason == null, powerShellReason) {
            copyToClipboard(buildPowerShellCommand(tab, layers, state.settings.allowJson5InJsonBodies, state))
        },
    )
}

// ── Editor Tab Bar ──────────────────────────────────────────────

private val defaultRequestEditorTabs = listOf(
    RequestEditorTab.PARAMS,
    RequestEditorTab.HEADERS,
    RequestEditorTab.BODY,
    RequestEditorTab.AUTH,
    RequestEditorTab.PRE_REQUEST,
    RequestEditorTab.TESTS,
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun EditorTabBar(
    selectedTab: RequestEditorTab,
    onTabSelected: (RequestEditorTab) -> Unit,
    modifier: Modifier = Modifier,
    paramCount: Int = 0,
    headerCount: Int = 0,
    variableCount: Int = 0,
    availableTabs: List<RequestEditorTab> = defaultRequestEditorTabs,
) {
    val icons = mapOf(
        RequestEditorTab.PARAMS      to null,
        RequestEditorTab.VARIABLES   to null,
        RequestEditorTab.HEADERS     to null,
        RequestEditorTab.BODY        to painterResource(Res.drawable.ic_code),
        RequestEditorTab.AUTH        to painterResource(Res.drawable.ic_lock),
        RequestEditorTab.PRE_REQUEST to painterResource(Res.drawable.ic_play_arrow),
        RequestEditorTab.TESTS       to painterResource(Res.drawable.ic_check_circle),
    )
    val counts = mapOf(
        RequestEditorTab.PARAMS    to paramCount,
        RequestEditorTab.VARIABLES to variableCount,
        RequestEditorTab.HEADERS   to headerCount,
    )

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val tabBounds = remember { mutableStateMapOf<RequestEditorTab, Pair<Int, Int>>() }

    LaunchedEffect(selectedTab) {
        val (tabLeft, tabWidth) = tabBounds[selectedTab] ?: return@LaunchedEffect
        val viewport = scrollState.viewportSize
        if (viewport > 0) {
            val tabRight = tabLeft + tabWidth
            when {
                tabLeft < scrollState.value -> scrollState.animateScrollTo(tabLeft)
                tabRight > scrollState.value + viewport -> scrollState.animateScrollTo(tabRight - viewport)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ClapiColors.Surface)
            .testTag("editor-tab-bar"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val delta = event.changes.firstOrNull()?.scrollDelta ?: return@onPointerEvent
                    val horizontal = if (delta.x != 0f) delta.x else delta.y
                    if (horizontal != 0f && scrollState.maxValue > 0) {
                        scope.launch { scrollState.scrollBy(horizontal * 48f) }
                    }
                }
                .testTag("editor-tab-row"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            availableTabs.forEach { tab ->
                val selected = tab == selectedTab
                val label = when (tab) {
                    RequestEditorTab.PARAMS -> Strings.params
                    RequestEditorTab.VARIABLES -> Strings.variables
                    RequestEditorTab.HEADERS -> Strings.headers
                    RequestEditorTab.BODY -> Strings.body
                    RequestEditorTab.AUTH -> Strings.auth
                    RequestEditorTab.PRE_REQUEST -> Strings.preRequest
                    RequestEditorTab.TESTS -> Strings.tests
                }
                val interactionSource = remember(tab) { MutableInteractionSource() }
                val isHovered by interactionSource.collectIsHoveredAsState()
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .onGloballyPositioned { coords ->
                            tabBounds[tab] = coords.positionInParent().x.toInt() to coords.size.width
                        }
                        .background(if (isHovered && !selected) ClapiColors.HoverOverlay else Color.Transparent)
                        .hoverable(interactionSource)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { onTabSelected(tab) }
                        .testTag("editor-tab-${tab.name.lowercase()}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 12.dp),
                    ) {
                        icons[tab]?.let { icon ->
                            Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (selected) ClapiColors.Primary else ClapiColors.OnSurfaceDim,
                            )
                        }
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) ClapiColors.Primary else ClapiColors.OnSurfaceVariant,
                            maxLines = 1,
                        )
                        counts[tab]?.takeIf { it > 0 }?.let { count ->
                            Text(
                                "($count)",
                                fontSize = 10.sp,
                                color = ClapiColors.OnSurfaceDim,
                                maxLines = 1,
                            )
                        }
                    }
                    if (selected) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(ClapiColors.Primary),
                        )
                    }
                }
            }
        }

        PlatformHorizontalScrollbar(
            scrollState = scrollState,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            testTag = "editor-tabs-scrollbar",
        )

        // bottom line
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(ClapiColors.Border)
        )
    }
}
