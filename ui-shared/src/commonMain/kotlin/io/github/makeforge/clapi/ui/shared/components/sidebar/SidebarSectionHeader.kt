package io.github.makeforge.clapi.ui.shared.components.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.ui.shared.components.HoverTooltip
import io.github.makeforge.clapi.ui.shared.generated.resources.*
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun SearchBar(query: String, onQueryChanged: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ClapiColors.SurfaceContainer)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(Res.drawable.ic_search), contentDescription = null, tint = ClapiColors.OnSurfaceDim, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChanged,
            singleLine = true,
            textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 13.sp),
            cursorBrush = SolidColor(ClapiColors.Primary),
            modifier = Modifier.weight(1f).testTag("sidebar-search-input"),
            decorationBox = { inner ->
                if (query.isEmpty()) Text("${Strings.search}…", color = ClapiColors.OnSurfaceDim, fontSize = 13.sp)
                inner()
            },
        )
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            HoverTooltip(
                tooltip = Strings.t("clear_search"),
            ) {
                Icon(painter = painterResource(Res.drawable.ic_close),
                    contentDescription = Strings.t("clear_search"),
                    tint = ClapiColors.OnSurfaceDim,
                    modifier = Modifier
                        .size(14.dp)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(onClick = { onQueryChanged("") })
                        .testTag("sidebar-search-clear"),
                )
            }
        }
    }
}

@Composable
internal fun SectionHeader(
    icon: Painter,
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isHovered) ClapiColors.HoverOverlay else Color.Transparent)
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = { onClick?.invoke() ?: onToggle() })
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (expanded) painterResource(Res.drawable.ic_keyboard_arrow_down) else painterResource(Res.drawable.ic_keyboard_arrow_right),
            contentDescription = null,
            tint = ClapiColors.OnSurfaceDim,
            modifier = Modifier
                .size(16.dp)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                ),
        )
        Spacer(Modifier.width(4.dp))
        Icon(icon, contentDescription = null, tint = ClapiColors.OnSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = ClapiColors.OnSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
internal fun SectionSpacer(modifier: Modifier = Modifier) {
    Spacer(modifier.height(8.dp))
}
