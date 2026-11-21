package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_close
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/**
 * State holder for draggable dialogs. Clamps dragging so the dialog card
 * always stays visible within the viewport.
 */
class DraggableDialogState {
    var offsetX by mutableStateOf(0f)
    var offsetY by mutableStateOf(0f)
    var cardSize by mutableStateOf(IntSize.Zero)
    var viewportSize by mutableStateOf(IntSize.Zero)

    fun onDrag(dx: Float, dy: Float) {
        val (cx, cy) = clampDialogOffsetFromCenter(
            offsetX = offsetX + dx,
            offsetY = offsetY + dy,
            cardSize = cardSize,
            viewportSize = viewportSize,
        )
        offsetX = cx
        offsetY = cy
    }

    fun reset() {
        offsetX = 0f
        offsetY = 0f
    }
}

@Composable
fun rememberDraggableDialogState(): DraggableDialogState {
    return remember { DraggableDialogState() }
}

/**
 * Modifier for full-screen backdrop Boxes enclosing a dialog.
 * Handles viewport measurement and backdrop tap dismiss.
 */
@Composable
fun Modifier.draggableDialogBackdrop(
    dragState: DraggableDialogState,
    onDismiss: () -> Unit,
): Modifier = this
    .fillMaxSize()
    .dialogEscape(onDismiss = onDismiss)
    .onSizeChanged { dragState.viewportSize = it }
    .pointerInput(Unit) { detectTapGestures { onDismiss() } }

/**
 * Modifier for the dialog card. Applies the center-relative drag offset,
 * measures the card size, clips, and prevents background clicks from hitting the backdrop.
 *
 * NOTE: [draggableNoSlop] is NOT placed here. Dragging is strictly restricted
 * to the [DialogTitleBar] at the top of the dialog.
 */
@Composable
fun Modifier.draggableDialogCard(
    dragState: DraggableDialogState,
    cornerRadius: Dp = 12.dp,
): Modifier = this
    .offset { IntOffset(dragState.offsetX.roundToInt(), dragState.offsetY.roundToInt()) }
    .onSizeChanged { dragState.cardSize = it }
    .clip(RoundedCornerShape(cornerRadius))
    .background(ClapiColors.Surface)
    .border(1.dp, ClapiColors.Border, RoundedCornerShape(cornerRadius))
    .pointerInput(Unit) { detectTapGestures { } }

/**
 * Standard top bar for all dialogs.
 *
 * Clear by design:
 * - Distinct elevated background ([ClapiColors.SurfaceContainer])
 * - Bottom border divider ([ClapiColors.Border])
 * - Title and optional icon on the left
 * - Close / Done / X action on the right
 * - Only this top bar is draggable via [onDrag] ([draggableNoSlop]).
 */
@Composable
fun DialogTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    titleTag: String? = null,
    subtitle: String? = null,
    icon: Painter? = null,
    iconTint: Color = ClapiColors.Primary,
    closeButtonText: String? = null,
    closeButtonTag: String? = null,
    onClose: (() -> Unit)? = null,
    onDrag: ((dx: Float, dy: Float) -> Unit)? = null,
    cornerRadius: Dp = 12.dp,
    testTag: String = "dialog-title-bar",
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius))
                .background(ClapiColors.SurfaceContainer)
                .then(
                    if (onDrag != null) {
                        Modifier.draggableNoSlop { dx, dy -> onDrag(dx, dy) }
                    } else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag(testTag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (icon != null) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column {
                    Text(
                        text = title,
                        modifier = if (titleTag != null) Modifier.testTag(titleTag) else Modifier,
                        color = ClapiColors.OnSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            color = ClapiColors.OnSurfaceDim,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                actions?.invoke(this)

                if (onClose != null) {
                    if (closeButtonText != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ClapiColors.SurfaceVariant)
                                .clickable(onClick = onClose)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .testTag(closeButtonTag ?: "dialog-close-button"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = closeButtonText,
                                color = ClapiColors.OnSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag(closeButtonTag ?: "dialog-close-button"),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_close),
                                contentDescription = Strings.close,
                                tint = ClapiColors.OnSurfaceDim,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = ClapiColors.Border, thickness = 1.dp)
    }
}
