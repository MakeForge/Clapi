package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_help_outline
import io.github.makeforge.clapi.ui.shared.platform.openUrlInBrowser
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import org.jetbrains.compose.resources.painterResource

/**
 * Standardized documentation / help icon button used consistently across all app views, dialogs, and panels.
 * Renders a question mark outline icon ([ic_help_outline]) with a tooltip.
 *
 * If [onClick] is provided, it is invoked on click; otherwise, if [url] is provided,
 * it is opened in the default web browser.
 */
@Composable
fun DocsHelpButton(
    url: String? = null,
    onClick: (() -> Unit)? = null,
    tooltip: String = "Documentation",
    tooltipTag: String? = null,
    modifier: Modifier = Modifier,
    tag: String = "docs-help-button",
    size: Dp = 24.dp,
    iconSize: Dp = 16.dp,
    tint: Color = ClapiColors.OnSurfaceDim,
) {
    val clickAction: () -> Unit = onClick ?: {
        url?.let { openUrlInBrowser(it) }
    }

    IconButtonWithTooltip(
        onClick = clickAction,
        tooltip = tooltip,
        tooltipTag = tooltipTag,
        modifier = modifier.size(size).testTag(tag),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_help_outline),
            contentDescription = tooltip,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}
