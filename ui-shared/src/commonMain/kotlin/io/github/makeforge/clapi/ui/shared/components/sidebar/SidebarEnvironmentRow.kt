package io.github.makeforge.clapi.ui.shared.components.sidebar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.alpha
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
import io.github.makeforge.clapi.ui.shared.components.IconButtonWithTooltip
import io.github.makeforge.clapi.ui.shared.components.sharedSidebarTooltipState
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_content_copy
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_delete
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_edit
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_more_vert
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun EnvironmentRow(
    envName: String,
    active: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var showMenu by remember { mutableStateOf(false) }

    var envRowRootY  by remember { mutableStateOf(0) }
    var envRowHeight by remember { mutableStateOf(0) }
    val envDensity   = LocalDensity.current
    val envIndentPx  = with(envDensity) { 28.dp.roundToPx() }

    LaunchedEffect(isHovered) {
        if (isHovered) {
            sharedSidebarTooltipState.onHoverEnter("env-$envName", envName)
            sharedSidebarTooltipState.updateHoverPosition(
                rowRootY    = envRowRootY,
                rowHeightPx = envRowHeight,
                indentXPx   = envIndentPx,
            )
        } else {
            sharedSidebarTooltipState.onHoverExit("env-$envName")
        }
    }

    val rowBackground by animateColorAsState(
        targetValue = when {
            active -> ClapiColors.SelectedItem
            isHovered -> ClapiColors.HoverOverlay
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 120),
        label = "envRowBg",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(rowBackground)
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .onPointerEvent(PointerEventType.Press) { if (it.buttons.isSecondaryPressed) showMenu = true }
            .combinedClickable(onClick = onClick, onDoubleClick = onDoubleClick)
            .padding(start = 28.dp, end = 4.dp, top = 5.dp, bottom = 5.dp)
            .testTag("env-row-$envName")
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                envRowRootY  = pos.y.toInt()
                envRowHeight = coords.size.height
                if (sharedSidebarTooltipState.hoveredItemId == "env-$envName") {
                    sharedSidebarTooltipState.updateHoverPosition(
                        rowRootY    = pos.y.toInt(),
                        rowHeightPx = coords.size.height,
                        indentXPx   = envIndentPx,
                    )
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(if (active) ClapiColors.Secondary else ClapiColors.OnSurfaceDim),
        )
        Text(
            text = envName,
            style = MaterialTheme.typography.bodySmall,
            color = if (active) ClapiColors.OnSurface else ClapiColors.OnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag("env-name-text-$envName"),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButtonWithTooltip(
                onClick = onEdit,
                tooltip = Strings.t("edit_environment"),
                modifier = Modifier
                    .size(24.dp)
                    .alpha(if (isHovered || active || showMenu) 1f else 0f)
                    .testTag("env-edit-$envName"),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_edit),
                    contentDescription = Strings.t("edit_environment"),
                    tint = ClapiColors.OnSurfaceDim,
                    modifier = Modifier.size(14.dp),
                )
            }
            Box(modifier = Modifier.width(24.dp)) {
                IconButtonWithTooltip(
                    onClick = { showMenu = true },
                    tooltip = Strings.t("environment_actions"),
                    modifier = Modifier
                        .size(24.dp)
                        .alpha(if (isHovered || active || showMenu) 1f else 0f)
                        .testTag("env-actions-$envName"),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_more_vert),
                        contentDescription = Strings.t("environment_actions"),
                        tint = ClapiColors.OnSurfaceDim,
                        modifier = Modifier.size(14.dp),
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(Strings.t("duplicate_environment")) },
                        leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_content_copy), contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showMenu = false
                            onDuplicate()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(Strings.t("rename_environment")) },
                        leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showMenu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(Strings.t("delete_environment")) },
                        leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
