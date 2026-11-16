package io.github.makeforge.clapi.ui.shared.components.sidebar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.ui.shared.components.IconButtonWithTooltip
import io.github.makeforge.clapi.ui.shared.components.MethodBadge
import io.github.makeforge.clapi.ui.shared.components.handPointerHover
import io.github.makeforge.clapi.ui.shared.components.sharedSidebarTooltipState
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_more_vert
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.platform.formatTimestamp
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.HistoryItem
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import org.jetbrains.compose.resources.painterResource

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
internal fun HistoryRow(
    state: AppState,
    item: HistoryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var showMenu by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val tooltipIndentPx = with(density) { 28.dp.roundToPx() }

    LaunchedEffect(isHovered) {
        val tooltipId = "history-${item.requestId}-${item.timestamp}"
        if (isHovered) {
            sharedSidebarTooltipState.onHoverEnter(tooltipId, "${item.name}  ${item.url}")
        } else {
            sharedSidebarTooltipState.onHoverExit(tooltipId)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isHovered) ClapiColors.HoverOverlay else Color.Transparent
            )
            .onGloballyPositioned { coordinates ->
                if (sharedSidebarTooltipState.hoveredItemId == "history-${item.requestId}-${item.timestamp}") {
                    val position = coordinates.positionInRoot()
                    sharedSidebarTooltipState.updateHoverPosition(
                        rowRootY = position.y.toInt(),
                        rowHeightPx = coordinates.size.height,
                        indentXPx = tooltipIndentPx,
                    )
                }
            }
            .hoverable(interactionSource)
            .handPointerHover()
            .onPointerEvent(PointerEventType.Press) {
                if (it.buttons.isSecondaryPressed) {
                    showMenu = true
                }
            }
            .clickable(onClick = onClick)
            .testTag("history-row-${item.requestId}")
            .padding(start = 28.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MethodBadge(item.method, compact = true)
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            color = ClapiColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // M-6: Show the request timestamp so users can identify when each call was made.
        Text(
            text = formatTimestamp(item.timestamp),
            style = MaterialTheme.typography.bodySmall,
            color = ClapiColors.OnSurfaceDim,
            fontSize = 10.sp,
        )
        Box {
            IconButtonWithTooltip(
                onClick = { showMenu = true },
                tooltip = Strings.t("history_actions"),
                modifier = Modifier.size(20.dp).testTag("history-actions-${item.requestId}"),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_more_vert),
                    contentDescription = Strings.t("history_actions"),
                    tint = ClapiColors.OnSurfaceDim,
                    modifier = Modifier.size(14.dp),
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(Strings.t("open")) },
                    onClick = {
                        showMenu = false
                        onClick()
                    },
                )
                DropdownMenuItem(
                    text = { Text(Strings.t("open_in_sidebar")) },
                    onClick = {
                        showMenu = false
                        state.goToCollectionFromHistory(item)
                    },
                )
                DropdownMenuItem(
                    text = { Text(Strings.goToFolder) },
                    onClick = {
                        showMenu = false
                        state.goToCollectionFromHistory(item)
                    },
                )
                DropdownMenuItem(
                    text = { Text(Strings.t("remove_from_history")) },
                    onClick = {
                        showMenu = false
                        state.removeHistoryItem(item.requestId)
                    },
                )
            }
        }
    }
}
