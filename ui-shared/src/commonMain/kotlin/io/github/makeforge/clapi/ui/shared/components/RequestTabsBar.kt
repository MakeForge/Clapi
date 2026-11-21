package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_article
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_close
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_folder
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_help_outline
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_public
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_settings
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_view_sidebar
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_view_week
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.platform.PlatformHorizontalScrollbar
import io.github.makeforge.clapi.ui.shared.platform.horizontalResizeCursor
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.github.makeforge.clapi.ui.shared.state.ResponseLayout
import io.github.makeforge.clapi.ui.shared.state.hasSseAccept
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily
import org.jetbrains.compose.resources.painterResource

/**
 * The horizontal tab bar that shows all open request tabs with a "+" button.
 * Supports horizontal scrolling, right-click context menus (per-tab), and
 * the active-tab underline indicator.
 */
@Composable
fun RequestTabsBar(
    state: AppState,
    onRequestClose: (Int) -> Unit,
    onRenameTab: (requestId: String, newName: String) -> Unit,
    onShowInSidebar: (requestId: String) -> Unit,
    onCloseOthers: (Int) -> Unit,
    onCloseToLeft: (Int) -> Unit,
    onCloseToRight: (Int) -> Unit,
    onCloseAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabScrollState = rememberScrollState()
    // Natural (layout-coordinate) x-position of each tab chip inside the
    // scrollable Row. Populated by onGloballyPositioned on each chip wrapper.
    val tabPositions = remember { mutableStateMapOf<Int, Int>() }

    // Auto-scroll so the active tab is visible whenever it changes
    // (e.g. when the user clicks a request in the sidebar).
    LaunchedEffect(state.activeTabIndex) {
        val x = tabPositions[state.activeTabIndex] ?: return@LaunchedEffect
        tabScrollState.animateScrollTo(x)
    }

    Column(modifier = modifier.fillMaxWidth().testTag("request-tabs-bar")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(ClapiColors.Surface),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Horizontally scrollable chips row
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(tabScrollState)
                        .padding(start = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    state.openTabs.forEachIndexed { index, tab ->
                        // Box captures the chip's natural x-offset in the Row's
                        // layout coordinate space for auto-scroll targeting.
                        Box(
                            modifier = Modifier.onGloballyPositioned { coords ->
                                tabPositions[index] = coords.positionInParent().x.toInt()
                            },
                        ) {
                            RequestTabChip(
                                tab = tab,
                                isActive = index == state.activeTabIndex,
                                onClick = {
                                    state.activeTabIndex = index
                                },
                                onClose = { onRequestClose(index) },
                                onRename = { newName -> onRenameTab(tab.id, newName) },
                                onShowInSidebar = { onShowInSidebar(tab.id) },
                                onCloseOthers = { onCloseOthers(index) },
                                onCloseToLeft = { onCloseToLeft(index) },
                                onCloseToRight = { onCloseToRight(index) },
                                onCloseAll = onCloseAll,
                                showClose = true,
                            )
                        }
                    }
                }
                PlatformHorizontalScrollbar(
                    scrollState = tabScrollState,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                )
            }

            // Workspace actions: Environment, response layout, global variables, help, settings
            Row(
                modifier = Modifier.padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                EnvironmentChip(state)

                IconButtonWithTooltip(
                    tooltip = Strings.t("toggle_response_layout"),
                    tooltipTag = "toolbar-tooltip-response-layout",
                    offset = IntOffset(0, 30),
                    onClick = {
                        state.settings.responseLayout =
                            if (state.settings.responseLayout == ResponseLayout.RIGHT) ResponseLayout.BOTTOM
                            else ResponseLayout.RIGHT
                    },
                    modifier = Modifier.size(32.dp).testTag("response-layout-toggle"),
                ) {
                    Icon(
                        painter = if (state.settings.responseLayout == ResponseLayout.RIGHT) {
                            painterResource(Res.drawable.ic_view_sidebar)
                        } else {
                            painterResource(Res.drawable.ic_view_week)
                        },
                        contentDescription = Strings.t("toggle_response_layout"),
                        tint = ClapiColors.OnSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }

                IconButtonWithTooltip(
                    tooltip = Strings.globalVariables,
                    tooltipTag = "toolbar-tooltip-global-variables",
                    offset = IntOffset(0, 30),
                    onClick = { state.showGlobalVariablesDialog = true },
                    modifier = Modifier.size(32.dp).testTag("global-variables-button"),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_public),
                        contentDescription = Strings.globalVariables,
                        tint = ClapiColors.OnSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }

                IconButtonWithTooltip(
                    tooltip = "Help and About",
                    tooltipTag = "toolbar-tooltip-help-about",
                    offset = IntOffset(0, 30),
                    onClick = { state.showHelpDialog = true },
                    modifier = Modifier.size(32.dp).testTag("help-about-button"),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_help_outline),
                        contentDescription = "Help and About",
                        tint = ClapiColors.OnSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }

                IconButtonWithTooltip(
                    tooltip = Strings.settings,
                    tooltipTag = "toolbar-tooltip-settings",
                    offset = IntOffset(0, 30),
                    onClick = { state.showSettingsDialog = true },
                    modifier = Modifier.size(32.dp).testTag("settings-button"),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_settings),
                        contentDescription = Strings.settings,
                        tint = ClapiColors.OnSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // Bottom border
        Box(Modifier.fillMaxWidth().height(1.dp).background(ClapiColors.Border))
    }
}

// ── Single tab chip ─────────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RequestTabChip(
    tab: RequestTabState,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onRename: (String) -> Unit,
    onShowInSidebar: () -> Unit,
    onCloseOthers: () -> Unit,
    onCloseToLeft: () -> Unit,
    onCloseToRight: () -> Unit,
    onCloseAll: () -> Unit,
    showClose: Boolean,
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    var showContextMenu by remember { mutableStateOf(false) }
    var renameMode by remember(tab.id) { mutableStateOf(false) }
    var renameText by remember(tab.id) { mutableStateOf(TextFieldValue(tab.name)) }

    // Outer Box fills the 36dp row height so the indicator can anchor at the bottom
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
            .background(
                when {
                    isActive  -> ClapiColors.Background
                    isHovered -> ClapiColors.HoverOverlay
                    else      -> Color.Transparent
                }
            )
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .onPointerEvent(PointerEventType.Press) {
                if (it.buttons.isSecondaryPressed) showContextMenu = true
            }
            .testTag("tab-chip-${tab.id}"),
    ) {
        // Tab label row
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (tab.kind == RequestKind.MCP) {
                McpMethodBadge(compact = true, modifier = Modifier.testTag("tab-mcp-badge-${tab.id}"))
            } else if (tab.kind == RequestKind.GRPC) {
                GrpcBadge(compact = true, modifier = Modifier.testTag("tab-grpc-badge-${tab.id}"))
            } else if (tab.kind == RequestKind.WEBSOCKET) {
                WebSocketBadge(compact = true, modifier = Modifier.testTag("tab-websocket-badge-${tab.id}"))
            } else if (tab.kind == RequestKind.GRAPHQL) {
                GraphQLBadge(compact = true, modifier = Modifier.testTag("tab-graphql-badge-${tab.id}"))
            } else if (tab.kind == RequestKind.FOLDER) {
                Icon(painter = painterResource(Res.drawable.ic_folder),
                    contentDescription = null,
                    tint = ClapiColors.Tertiary,
                    modifier = Modifier.size(14.dp).testTag("tab-folder-icon-${tab.id}"),
                )
            } else if (tab.kind == RequestKind.COLLECTION) {
                Icon(painter = painterResource(Res.drawable.ic_article),
                    contentDescription = null,
                    tint = ClapiColors.Tertiary,
                    modifier = Modifier.size(14.dp).testTag("tab-collection-icon-${tab.id}"),
                )
            } else {
                val sse = tab.hasSseAccept()
                MethodBadge(
                    tab.method,
                    compact = true,
                    sse = sse,
                    modifier = if (sse) Modifier.testTag("tab-sse-badge-${tab.id}") else Modifier,
                )
            }
            if (renameMode) {
                BasicTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = ClapiColors.OnSurface,
                        fontSize = 12.sp,
                        fontFamily = CodeFontFamily,
                    ),
                    cursorBrush = SolidColor(ClapiColors.Primary),
                    modifier = Modifier
                        .background(ClapiColors.SurfaceContainer, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        // M-5: Confirm rename with Enter, cancel with Escape
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.Enter -> {
                                        val newName = renameText.text.trim()
                                        if (newName.isNotEmpty()) onRename(newName)
                                        renameMode = false
                                        true
                                    }
                                    Key.Escape -> {
                                        renameMode = false
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .testTag("tab-rename-input-${tab.id}"),
                )
                Text(
                    text = Strings.save,
                    color = ClapiColors.Primary,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clickable {
                            val newName = renameText.text.trim()
                            if (newName.isNotEmpty()) onRename(newName)
                            renameMode = false
                        }
                        .testTag("tab-rename-save-${tab.id}"),
                )
            } else {
                Text(
                    text = tab.name + if (tab.isDirty) " *" else "",
                    color = if (isActive) ClapiColors.OnSurface else ClapiColors.OnSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showClose && (isActive || isHovered)) {
                HoverTooltip(
                    tooltip = Strings.t("close_tab"),
                ) {
                    Icon(painter = painterResource(Res.drawable.ic_close),
                        contentDescription = Strings.t("close_tab"),
                        tint = ClapiColors.OnSurfaceDim,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable(onClick = onClose)
                            .testTag("tab-close-${tab.id}"),
                    )
                }
            }
        }

        // Active-tab underline indicator (2dp, Primary colour)
        if (isActive) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(ClapiColors.Primary)
                    .testTag("tab-active-indicator-${tab.id}"),
            )
        }

        // Right-click context menu
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
        ) {
            if (showClose) DropdownMenuItem(
                text = { Text(Strings.close) },
                onClick = { showContextMenu = false; onClose() },
                modifier = Modifier.testTag("tab-ctx-close"),
            )
            DropdownMenuItem(
                text = { Text(Strings.t("rename")) },
                onClick = {
                    showContextMenu = false
                    renameText = TextFieldValue(tab.name)
                    renameMode = true
                },
                modifier = Modifier.testTag("tab-ctx-rename"),
            )
            DropdownMenuItem(
                text = { Text(Strings.t("show_in_sidebar")) },
                onClick = { showContextMenu = false; onShowInSidebar() },
                modifier = Modifier.testTag("tab-ctx-show-sidebar"),
            )
            DropdownMenuItem(
                text = { Text(Strings.t("close_others")) },
                onClick = { showContextMenu = false; onCloseOthers() },
                modifier = Modifier.testTag("tab-ctx-close-others"),
            )
            DropdownMenuItem(
                text = { Text(Strings.t("close_tabs_to_left")) },
                onClick = { showContextMenu = false; onCloseToLeft() },
                modifier = Modifier.testTag("tab-ctx-close-left"),
            )
            DropdownMenuItem(
                text = { Text(Strings.t("close_tabs_to_right")) },
                onClick = { showContextMenu = false; onCloseToRight() },
                modifier = Modifier.testTag("tab-ctx-close-right"),
            )
            DropdownMenuItem(
                text = { Text(Strings.t("close_all")) },
                onClick = { showContextMenu = false; onCloseAll() },
                modifier = Modifier.testTag("tab-ctx-close-all"),
            )
        }
    }
}

// ── Sidebar resize divider ───────────────────────────────────────

/**
 * Thin draggable vertical bar that lets the user resize the sidebar.
 * Clamps sidebar width between 200 dp and 500 dp.
 */
@Composable
fun SidebarResizeDivider(state: AppState) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    // Modifier.draggable reports delta in pixels; sidebarWidth is in dp.
    // Divide by density so 1px cursor movement = 1/density dp = 1px on screen.
    val density = LocalDensity.current.density

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(if (isHovered) 6.dp else 4.dp)
            .background(if (isHovered) ClapiColors.Primary.copy(alpha = 0.6f) else ClapiColors.Border)
            .hoverable(interaction)
            .pointerHoverIcon(horizontalResizeCursor)
            // M-9 / smooth drag: Modifier.draggable with startDragImmediately=true
            // fires on the first pixel and uses a stable coordinate frame,
            // so the divider never runs ahead of the cursor.
            .draggable(
                orientation = Orientation.Horizontal,
                startDragImmediately = true,
                state = rememberDraggableState { delta ->
                    state.sidebarWidth = (state.sidebarWidth + delta / density)
                        .coerceIn(150f, 500f)
                },
            )
            .testTag("sidebar-resize-divider"),
    )
}
