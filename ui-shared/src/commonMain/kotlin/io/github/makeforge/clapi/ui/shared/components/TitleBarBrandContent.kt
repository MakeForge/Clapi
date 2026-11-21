package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_menu
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import org.jetbrains.compose.resources.painterResource

/**
 * Top-left title bar branding and drawer toggle placed inside the native window decoration bar.
 */
@Composable
fun TitleBarBrandContent(
    state: AppState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(start = 8.dp)
            .testTag("title-bar-brand"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Siderawer toggle button
        IconButtonWithTooltip(
            tooltip = Strings.t("toggle_sidebar"),
            tooltipTag = "toolbar-tooltip-toggle-sidebar",
            offset = IntOffset(0, 32),
            onClick = { state.sidebarExpanded = !state.sidebarExpanded },
            modifier = Modifier
                .size(32.dp)
                .testTag("toolbar-toggle-sidebar-button"),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_menu),
                contentDescription = Strings.t("toggle_sidebar"),
                tint = ClapiColors.OnSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.width(4.dp))
        BrandIcon(modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = Strings.appName,
            color = ClapiColors.OnSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
    }
}
