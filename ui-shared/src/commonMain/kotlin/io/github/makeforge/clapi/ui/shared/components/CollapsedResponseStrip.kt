package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_open_in_full
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import org.jetbrains.compose.resources.painterResource

/**
 * Layout modifier that rotates a composable counter-clockwise by -90 degrees,
 * correctly swapping measurement constraints and bounds so it fits seamlessly into parent layouts.
 */
fun Modifier.rotateMinus90(): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints(
            minWidth = constraints.minHeight,
            maxWidth = constraints.maxHeight,
            minHeight = constraints.minWidth,
            maxHeight = constraints.maxWidth,
        ),
    )
    layout(placeable.height, placeable.width) {
        placeable.placeRelativeWithLayer(0, 0) {
            rotationZ = -90f
            transformOrigin = TransformOrigin(0f, 0f)
            translationX = 0f
            translationY = placeable.width.toFloat()
        }
    }
}

/**
 * Vertical bar shown when the response view is collapsed.
 * Runs vertically alongside the workspace rather than floating over it.
 * At the top of the bar, an expand icon and the text "Response" rotated -90 degrees are displayed.
 */
@Composable
fun CollapsedResponseStrip(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isHovered by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .testTag("collapsed-response-strip")
            .width(32.dp)
            .fillMaxHeight()
            .background(ClapiColors.Surface),
    ) {
        // 1dp left separator border
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(ClapiColors.Border),
        )

        // Vertical bar content
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top clickable expand action area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpand)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .background(if (isHovered) ClapiColors.SurfaceHigh else ClapiColors.Surface)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconButtonWithTooltip(
                    tooltip = Strings.expand,
                    tooltipTag = "collapsed-response-expand-tooltip",
                    offset = IntOffset(-36, 0),
                    onClick = onExpand,
                    modifier = Modifier
                        .size(22.dp)
                        .testTag("expand-response-button"),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_open_in_full),
                        contentDescription = Strings.expand,
                        tint = if (isHovered) ClapiColors.Primary else ClapiColors.OnSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = Strings.response,
                    modifier = Modifier
                        .rotateMinus90()
                        .testTag("collapsed-response-text"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isHovered) ClapiColors.Primary else ClapiColors.OnSurfaceVariant,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.weight(1f))
        }
    }
}
