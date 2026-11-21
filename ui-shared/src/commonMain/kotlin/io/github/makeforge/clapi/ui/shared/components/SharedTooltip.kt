package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import io.github.makeforge.clapi.ui.shared.platform.currentTimeMillis
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import kotlinx.coroutines.delay

/**
 * Shared state for the single sidebar tooltip overlay.
 *
 * We store the hovered item ID, the tooltip text, and the pixel coordinates of
 * the hovered row inside the sidebar container (sidebar-local space). The
 * overlay is rendered once at the Sidebar root — no per-node Popup windows —
 * which eliminates the OS-window boundary hover-leave flicker.
 */
class TooltipState {
    var hoveredItemId    by mutableStateOf<String?>(null)
    var tooltipVisible   by mutableStateOf(false)
    var tooltipText      by mutableStateOf("")
    /** Absolute Y of the sidebar Box top-edge in root (window) pixel coordinates. */
    var containerRootY   by mutableStateOf(0)
    /** Absolute Y of the hovered row top in root pixel coordinates. */
    var tooltipRowRootY  by mutableStateOf(0)
    /** Height of the hovered row in pixels. */
    var tooltipRowHeight by mutableStateOf(0)
    /** Horizontal indent in pixels so the tooltip aligns under the row text. */
    var tooltipIndentXPx by mutableStateOf(0)

    private var hoverStartTime = 0L

    /** Milliseconds elapsed since the current hover started. */
    fun elapsedSinceHoverStart(): Long = currentTimeMillis() - hoverStartTime

    /** Called when a sidebar row gains hover. */
    fun onHoverEnter(itemId: String, text: String) {
        if (hoveredItemId != itemId) {
            hoveredItemId  = itemId
            tooltipText    = text
            hoverStartTime = currentTimeMillis()
            tooltipVisible = false
        }
    }

    /** Called when a sidebar row loses hover. */
    fun onHoverExit(itemId: String) {
        if (hoveredItemId == itemId) {
            hoveredItemId  = null
            tooltipVisible = false
        }
    }

    /**
     * Called from the hovered row's onGloballyPositioned so the overlay knows
     * where to draw without a separate Popup anchor.
     */
    fun updateHoverPosition(rowRootY: Int, rowHeightPx: Int, indentXPx: Int) {
        tooltipRowRootY  = rowRootY
        tooltipRowHeight = rowHeightPx
        tooltipIndentXPx = indentXPx
    }

    /** Sidebar-local Y of the tooltip row, relative to the sidebar container top. */
    val localRowY: Int get() = tooltipRowRootY - containerRootY
}

/** Singleton used by all sidebar items and by the Sidebar composable. */
val sharedSidebarTooltipState = TooltipState()

/**
 * Drives the 300 ms delay before the tooltip becomes visible.
 * Must be composed exactly once, at the Sidebar root.
 */
@Composable
fun SharedTooltipCoordinator(state: TooltipState = sharedSidebarTooltipState) {
    LaunchedEffect(state.hoveredItemId) {
        val itemId = state.hoveredItemId ?: return@LaunchedEffect
        val elapsed = state.elapsedSinceHoverStart()
        val wait    = 300L - elapsed
        if (wait > 0L) delay(wait)
        if (state.hoveredItemId == itemId) {
            state.tooltipVisible = true
        }
    }
}

/**
 * Single, non-Popup tooltip overlay rendered as a child of the Sidebar Box.
 *
 * Because this is an in-tree Box rather than a floating OS window, moving the
 * mouse over the tooltip text does NOT cause the sidebar row to fire a
 * hover-exit — eliminating the flicker loop that occurred with Popup.
 *
 * Call this ONCE inside the Sidebar root Box, after the content Column.
 */
@Composable
fun SidebarTooltipOverlay(state: TooltipState = sharedSidebarTooltipState) {
    if (!state.tooltipVisible || state.hoveredItemId == null) return

    // Bottom edge of the hovered row in sidebar-local pixels
    val yPx = (state.localRowY + state.tooltipRowHeight).coerceAtLeast(0)
    val xPx = state.tooltipIndentXPx

    // wrapContentSize(unbounded=true) lets the tooltip extend past the sidebar
    // width for long names — the sidebar Box does not clip its children.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .zIndex(100f),
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(xPx, yPx) }
                .clip(RoundedCornerShape(4.dp))
                .background(ClapiColors.SurfaceHigh)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("sidebar-tooltip"),
        ) {
            Text(
                text = state.tooltipText,
                color = ClapiColors.OnSurface,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

/**
 * A standard IconButton that displays a hover hint (tooltip) with its content description/action.
 */
@Composable
fun IconButtonWithTooltip(
    onClick: () -> Unit,
    tooltip: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tooltipTag: String? = null,
    delayMillis: Long = 200L,
    alignment: Alignment = Alignment.BottomCenter,
    offset: IntOffset = IntOffset(0, 26),
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val isHovered by interactionSource.collectIsHoveredAsState()
    var tooltipVisible by remember { mutableStateOf(false) }
    var dismissedByClick by remember { mutableStateOf(false) }

    LaunchedEffect(isHovered, enabled, tooltip, dismissedByClick) {
        if (!isHovered) {
            dismissedByClick = false
            tooltipVisible = false
        } else if (!dismissedByClick && enabled && tooltip.isNotBlank()) {
            delay(delayMillis)
            tooltipVisible = true
        } else {
            tooltipVisible = false
        }
    }

    Box {
        IconButton(
            onClick = {
                dismissedByClick = true
                tooltipVisible = false
                onClick()
            },
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource,
            modifier = modifier,
        ) {
            content()
        }

        if (tooltipVisible && tooltip.isNotBlank()) {
            Popup(
                alignment = alignment,
                offset = offset,
                properties = PopupProperties(focusable = false),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(ClapiColors.SurfaceHigh)
                        .border(1.dp, ClapiColors.Border, RoundedCornerShape(5.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .then(if (tooltipTag != null) Modifier.testTag(tooltipTag) else Modifier),
                ) {
                    Text(
                        text = tooltip,
                        color = ClapiColors.OnSurface,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Positions a tooltip strictly below its anchor (or strictly above if there is
 * not enough room below), preventing tall or multi-line popups from overlapping
 * the hovered anchor and triggering a hover-exit/re-enter flicker loop.
 */
internal class TooltipPopupPositionProvider(
    private val gapPx: Int,
    private val marginPx: Int = 8,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val preferredX = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val x = preferredX.coerceIn(marginPx, maxX)

        val belowY = anchorBounds.bottom + gapPx
        val aboveY = anchorBounds.top - popupContentSize.height - gapPx
        val y = when {
            belowY + popupContentSize.height <= windowSize.height - marginPx -> belowY
            aboveY >= marginPx -> aboveY
            else -> {
                val spaceBelow = windowSize.height - anchorBounds.bottom
                val spaceAbove = anchorBounds.top
                if (spaceBelow >= spaceAbove) {
                    belowY.coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0))
                } else {
                    aboveY.coerceAtLeast(0)
                }
            }
        }
        return IntOffset(x, y)
    }
}

/**
 * Displays a hover hint (tooltip) over any composable (e.g. icon-only clickable items).
 */
@Composable
fun HoverTooltip(
    tooltip: String,
    modifier: Modifier = Modifier,
    tooltipTag: String? = null,
    delayMillis: Long = 200L,
    alignment: Alignment = Alignment.BottomCenter,
    offset: IntOffset = IntOffset(0, 24),
    maxLines: Int = 1,
    content: @Composable () -> Unit,
) {
    if (tooltip.isBlank()) {
        Box(modifier) { content() }
        return
    }

    val density = LocalDensity.current
    val gapPx = with(density) { 6.dp.roundToPx() }
    val marginPx = with(density) { 8.dp.roundToPx() }
    val positionProvider = remember(gapPx, marginPx) {
        TooltipPopupPositionProvider(gapPx = gapPx, marginPx = marginPx)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val popupInteractionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPopupHovered by popupInteractionSource.collectIsHoveredAsState()
    val activelyHovered = isHovered || isPopupHovered
    var tooltipVisible by remember { mutableStateOf(false) }

    LaunchedEffect(activelyHovered, tooltip) {
        if (tooltip.isBlank()) {
            tooltipVisible = false
        } else if (activelyHovered) {
            if (!tooltipVisible) {
                delay(delayMillis)
                tooltipVisible = true
            }
        } else if (tooltipVisible) {
            // Brief grace window avoids flicker if the cursor crosses the gap
            // onto the tooltip or hits a transient OS hover-exit event.
            delay(80L)
            tooltipVisible = false
        }
    }

    Box(modifier = modifier.hoverable(interactionSource)) {
        content()

        if (tooltipVisible && tooltip.isNotBlank()) {
            Popup(
                popupPositionProvider = positionProvider,
                properties = PopupProperties(focusable = false),
            ) {
                Box(
                    modifier = Modifier
                        .hoverable(popupInteractionSource)
                        .then(if (maxLines > 1) Modifier.widthIn(max = 380.dp) else Modifier)
                        .clip(RoundedCornerShape(5.dp))
                        .background(ClapiColors.SurfaceHigh)
                        .border(1.dp, ClapiColors.Border, RoundedCornerShape(5.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .then(if (tooltipTag != null) Modifier.testTag(tooltipTag) else Modifier),
                ) {
                    Text(
                        text = tooltip,
                        color = ClapiColors.OnSurface,
                        fontSize = 11.sp,
                        maxLines = maxLines,
                    )
                }
            }
        }
    }
}
