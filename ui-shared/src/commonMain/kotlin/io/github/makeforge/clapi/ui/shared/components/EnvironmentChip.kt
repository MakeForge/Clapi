package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.makeforge.clapi.ui.shared.components.dialogs.env.parseHexColor
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_more_vert
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import org.jetbrains.compose.resources.painterResource

@Composable
fun EnvironmentChip(
    state: AppState,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val newEnvironmentLabel = Strings.t("new_environment")
    val editEnvironmentLabel = Strings.t("edit_environment")

    val selectedEnvironment = state.selectedEnvironment

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isHovered) ClapiColors.HoverOverlay else ClapiColors.SurfaceContainer)
                .hoverable(interactionSource)
                .handPointerHover()
                .clickable { menuExpanded = true }
                .padding(horizontal = 8.dp, vertical = 4.dp)
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
            Icon(
                painter = painterResource(Res.drawable.ic_more_vert),
                contentDescription = null,
                tint = ClapiColors.OnSurfaceDim,
                modifier = Modifier.size(14.dp),
            )
        }

        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            if (state.environments.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(Strings.noEnvironmentsConfigured) },
                    onClick = { menuExpanded = false },
                )
                DropdownMenuItem(
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
                    DropdownMenuItem(
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
            DropdownMenuItem(
                text = { Text("$editEnvironmentLabel…") },
                onClick = {
                    if (state.selectedEnvIndex in state.environments.indices) {
                        state.openEnvEdit(state.selectedEnvIndex)
                    }
                    menuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("${Strings.globalVariables}…") },
                onClick = {
                    state.showGlobalVariablesDialog = true
                    menuExpanded = false
                },
            )
        }
    }
}
