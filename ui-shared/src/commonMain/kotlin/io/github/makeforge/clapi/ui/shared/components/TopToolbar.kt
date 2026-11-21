// Modified by MakeForge (2026)
package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.makeforge.clapi.ui.shared.components.dialogs.env.parseHexColor
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_help_outline
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_menu
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_more_vert
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_public
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_settings
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_view_sidebar
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_view_week
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.ResponseLayout
import io.github.makeforge.clapi.ui.shared.state.WorkspaceMode
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

@Composable
fun TopToolbar(
    state: AppState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state.workspaceMode) {
        if (state.workspaceMode != WorkspaceMode.HTTP) {
            state.workspaceMode = WorkspaceMode.HTTP
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(ClapiColors.Surface)
            .padding(horizontal = 8.dp)
            .testTag("top-toolbar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Sidebar toggle
        IconButtonWithTooltip(
            tooltip = Strings.t("toggle_sidebar"),
            tooltipTag = "toolbar-tooltip-toggle-sidebar",
            offset = IntOffset(0, 40),
            onClick = { state.sidebarExpanded = !state.sidebarExpanded },
            modifier = Modifier.testTag("toolbar-toggle-sidebar-button"),
        ) {
            Icon(painter = painterResource(Res.drawable.ic_menu),
                contentDescription = Strings.t("toggle_sidebar"),
                tint = ClapiColors.OnSurfaceVariant,
            )
        }

        // Logo
        Spacer(Modifier.width(4.dp))
        BrandIcon()
        Spacer(Modifier.width(8.dp))
        Text(
            text = Strings.appName,
            color = ClapiColors.OnSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )

        Spacer(Modifier.width(16.dp))

        // Workspace mode selector (HTTP / Realtime / GraphQL)
        // Row(
        //     modifier = Modifier
        //         .clip(RoundedCornerShape(6.dp))
        //         .background(ClapiColors.SurfaceContainer),
        //     horizontalArrangement = Arrangement.spacedBy(0.dp),
        // ) {
        //     WorkspaceMode.entries.forEach { mode ->
        //         val selected = state.workspaceMode == mode
        //         Text(
        //             text = when (mode) {
        //                 WorkspaceMode.HTTP -> "HTTP"
        //                 WorkspaceMode.REALTIME -> Strings.protocols
        //                 WorkspaceMode.GRAPHQL -> "GraphQL"
        //             },
        //             fontSize = 12.sp,
        //             fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        //             color = if (selected) ClapiColors.Primary else ClapiColors.OnSurfaceVariant,
        //             modifier = Modifier
        //                 .clip(RoundedCornerShape(6.dp))
        //                 .background(if (selected) ClapiColors.Primary.copy(alpha = 0.12f) else Color.Transparent)
        //                 .clickable { state.workspaceMode = mode }
        //                 .padding(horizontal = 12.dp, vertical = 6.dp)
        //                 .testTag("workspace-mode-${mode.name.lowercase()}"),
        //         )
        //     }
        // }

        Spacer(Modifier.weight(1f))

        // Environment selector
        EnvironmentChip(state)

        Spacer(Modifier.width(8.dp))

        IconButtonWithTooltip(
            tooltip = Strings.t("toggle_response_layout"),
            tooltipTag = "toolbar-tooltip-response-layout",
            offset = IntOffset(0, 40),
            onClick = {
                state.settings.responseLayout =
                    if (state.settings.responseLayout == ResponseLayout.RIGHT) ResponseLayout.BOTTOM
                    else ResponseLayout.RIGHT
            },
            modifier = Modifier.testTag("response-layout-toggle"),
        ) {
            Icon(
                painter = if (state.settings.responseLayout == ResponseLayout.RIGHT) {
                    painterResource(Res.drawable.ic_view_sidebar)
                } else {
                    painterResource(Res.drawable.ic_view_week)
                },
                contentDescription = Strings.t("toggle_response_layout"),
                tint = ClapiColors.OnSurfaceVariant,
            )
        }

        Spacer(Modifier.width(4.dp))

        // Global variables
        IconButtonWithTooltip(
            tooltip = Strings.globalVariables,
            tooltipTag = "toolbar-tooltip-global-variables",
            offset = IntOffset(0, 40),
            onClick = { state.showGlobalVariablesDialog = true },
            modifier = Modifier.testTag("global-variables-button"),
        ) {
            Icon(painter = painterResource(Res.drawable.ic_public),
                contentDescription = Strings.globalVariables,
                tint = ClapiColors.OnSurfaceVariant,
            )
        }

        IconButtonWithTooltip(
            tooltip = "Help and About",
            tooltipTag = "toolbar-tooltip-help-about",
            offset = IntOffset(0, 40),
            onClick = { state.showHelpDialog = true },
        ) {
            Icon(painter = painterResource(Res.drawable.ic_help_outline),
                contentDescription = "Help and About",
                tint = ClapiColors.OnSurfaceVariant,
                modifier = Modifier.testTag("help-about-button"),
            )
        }

        // Settings
        IconButtonWithTooltip(
            tooltip = Strings.settings,
            tooltipTag = "toolbar-tooltip-settings",
            offset = IntOffset(0, 40),
            onClick = { state.showSettingsDialog = true },
        ) {
            Icon(painter = painterResource(Res.drawable.ic_settings),
                contentDescription = Strings.settings,
                tint = ClapiColors.OnSurfaceVariant,
                modifier = Modifier.testTag("settings-button"),
            )
        }
    }

    // bottom border
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ClapiColors.Border)
    )
}

@Composable
expect fun BrandIcon(modifier: Modifier = Modifier)

@Composable
private fun EnvironmentChip(state: AppState) {
    var menuExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val newEnvironmentLabel = Strings.t("new_environment")
    val editEnvironmentLabel = Strings.t("edit_environment")

    val selectedEnvironment = state.selectedEnvironment

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isHovered) ClapiColors.HoverOverlay else ClapiColors.SurfaceContainer)
                .hoverable(interactionSource)
                .handPointerHover()
                .clickable { menuExpanded = true }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("env-chip"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val activeColor = selectedEnvironment?.color?.let { parseHexColor(it) } ?: ClapiColors.Secondary
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(activeColor)
                    .testTag("top-toolbar-env-color-dot"),
            )
            Text(
                text = selectedEnvironment?.name ?: Strings.noEnvironmentsConfigured,
                style = MaterialTheme.typography.labelMedium,
                color = ClapiColors.OnSurface,
            )
            Icon(painter = painterResource(Res.drawable.ic_more_vert),
                contentDescription = null,
                tint = ClapiColors.OnSurfaceDim,
                modifier = Modifier.size(14.dp),
            )
        }

        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            if (state.environments.isEmpty()) {
                DenseDropdownMenuItem(
                    text = { Text(Strings.noEnvironmentsConfigured) },
                    onClick = { menuExpanded = false },
                )
                DenseDropdownMenuItem(
                    text = { Text(Strings.createEnvironment) },
                    onClick = {
                        val created = EnvState(newEnvironmentLabel)
                        state.environments.add(created)
                        state.selectedEnvIndex = state.environments.lastIndex
                        state.environmentsExpanded = true
                        state.settings.environmentsExpanded = true
                        state.openEnvEdit(state.selectedEnvIndex)
                        menuExpanded = false
                    },
                )
            } else {
                state.environments.forEachIndexed { index, env ->
                    val envColor = env.color?.let { parseHexColor(it) } ?: (if (index == state.selectedEnvIndex) ClapiColors.Secondary else ClapiColors.OnSurfaceDim)
                    DenseDropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(envColor)
                                )
                                Text(env.name)
                            }
                        },
                        onClick = {
                            state.selectedEnvIndex = index
                            menuExpanded = false
                        },
                    )
                }
            }
            HorizontalDivider()
            DenseDropdownMenuItem(
                text = { Text("$editEnvironmentLabel…") },
                onClick = {
                    if (state.selectedEnvIndex in state.environments.indices) {
                        state.openEnvEdit(state.selectedEnvIndex)
                    }
                    menuExpanded = false
                },
            )
            DenseDropdownMenuItem(
                text = { Text("${Strings.globalVariables}…") },
                onClick = {
                    state.showGlobalVariablesDialog = true
                    menuExpanded = false
                },
            )
        }
    }
}
