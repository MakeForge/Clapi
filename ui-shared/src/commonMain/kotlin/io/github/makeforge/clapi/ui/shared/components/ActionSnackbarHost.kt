package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_close
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.state.ActionSnackbarController
import io.github.makeforge.clapi.ui.shared.state.MAX_SNACKBAR_AUTO_DISMISS_SEC
import io.github.makeforge.clapi.ui.shared.state.MIN_SNACKBAR_AUTO_DISMISS_SEC
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

/**
 * Renders the single active [io.github.makeforge.clapi.ui.shared.state.ActionSnackbar] anchored
 * in the bottom-right corner of the workspace.
 *
 * Strictly renders at most one snackbar at a time; when [ActionSnackbarController.current] changes
 * to a new ID, the previous snackbar is immediately replaced and the auto-dismiss timer resets.
 */
@Composable
fun ActionSnackbarHost(
    controller: ActionSnackbarController,
    autoDismissSec: Int,
    modifier: Modifier = Modifier,
) {
    val active = controller.current ?: return
    val effectiveDurationSec = (active.durationSecOverride ?: autoDismissSec)
        .coerceIn(MIN_SNACKBAR_AUTO_DISMISS_SEC, MAX_SNACKBAR_AUTO_DISMISS_SEC)
    val effectiveDurationMs = effectiveDurationSec * 1_000L

    LaunchedEffect(active.id, effectiveDurationMs) {
        delay(effectiveDurationMs)
        controller.dismiss(active.id)
    }

    val shape = RoundedCornerShape(8.dp)
    val borderColor = if (active.isError) ClapiColors.Error else ClapiColors.Border

    Surface(
        modifier = modifier
            .widthIn(min = 240.dp, max = 420.dp)
            .testTag("action-snackbar"),
        shape = shape,
        color = ClapiColors.SurfaceHigh,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, borderColor, shape)
                .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = active.message,
                color = if (active.isError) ClapiColors.Error else ClapiColors.OnSurface,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .testTag("action-snackbar-message"),
            )

            val actionLabel = active.actionLabel
            if (actionLabel != null && active.onAction != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ClapiColors.Primary.copy(alpha = 0.14f))
                        .handPointerHover()
                        .clickable { controller.triggerAction(active.id) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("action-snackbar-undo"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = actionLabel,
                        color = ClapiColors.Primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .handPointerHover()
                    .clickable { controller.dismiss(active.id) }
                    .testTag("action-snackbar-dismiss"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_close),
                    contentDescription = Strings.close,
                    tint = ClapiColors.OnSurfaceDim,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
