package io.github.makeforge.clapi.ui.shared.components.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.makeforge.clapi.ui.shared.components.IconButtonWithTooltip
import io.github.makeforge.clapi.ui.shared.generated.resources.*
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.persistence.ImportExportNaming
import io.github.makeforge.clapi.ui.shared.persistence.ImportExportRepository
import io.github.makeforge.clapi.ui.shared.persistence.WorkspaceRepository
import io.github.makeforge.clapi.ui.shared.platform.pickFileForImport
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.DeletedEnvironmentSnapshot
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.LogLevel
import io.github.makeforge.clapi.ui.shared.state.UndoableWorkspaceActions
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

internal fun LazyListScope.sidebarEnvironmentsSection(
    state: AppState,
    scope: CoroutineScope,
    launchTracked: (String, String, suspend () -> Unit) -> Unit,
    onCreateEnvironment: () -> Unit,
    onRenameEnvironment: (Int, String) -> Unit,
) {
    item {
        SectionHeader(
            icon = painterResource(Res.drawable.ic_settings),
            title = Strings.environments,
            expanded = state.environmentsExpanded,
            onToggle = {
                state.environmentsExpanded = !state.environmentsExpanded
                state.settings.environmentsExpanded = state.environmentsExpanded
            },
            trailing = {
                Row {
                    IconButtonWithTooltip(
                        onClick = {
                            pickFileForImport { content ->
                                scope.launch {
                                    launchTracked("Importing environment", "Importing environment...") {
                                        val importedName = ImportExportRepository.importEnvironmentFromString(state, content)
                                        withContext(Dispatchers.Main) {
                                            WorkspaceRepository.save(state)
                                            state.log("Environment imported: $importedName", LogLevel.SUCCESS)
                                        }
                                    }
                                }
                            }
                        },
                        tooltip = Strings.t("import_environment"),
                        modifier = Modifier.size(24.dp).testTag("environment-import-button"),
                    ) {
                        Icon(painter = painterResource(Res.drawable.ic_download),
                            contentDescription = Strings.t("import_environment"),
                            tint = ClapiColors.OnSurfaceDim,
                            modifier = Modifier.size(14.dp).testTag("environment-import-icon"),
                        )
                    }
                    IconButtonWithTooltip(
                        onClick = onCreateEnvironment,
                        tooltip = Strings.t("new_environment"),
                        modifier = Modifier.size(24.dp).testTag("sidebar-new-environment-button"),
                    ) {
                        Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = Strings.t("new_environment"), tint = ClapiColors.OnSurfaceDim, modifier = Modifier.size(16.dp))
                    }
                }
            },
        )
    }
    if (state.environmentsExpanded) {
        if (state.environments.isEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 28.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = Strings.noEnvironmentsConfigured,
                        style = MaterialTheme.typography.bodySmall,
                        color = ClapiColors.OnSurfaceDim,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = Strings.createEnvironment,
                        style = MaterialTheme.typography.bodySmall,
                        color = ClapiColors.Primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ClapiColors.Primary.copy(alpha = 0.10f))
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable(onClick = onCreateEnvironment)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
        state.environments.forEachIndexed { index, env ->
            item(key = "env_$index") {
                EnvironmentRow(
                    envName = env.name,
                    active = index == state.selectedEnvIndex,
                    colorHex = env.color,
                    onClick = { state.selectedEnvIndex = index },
                    onDoubleClick = {
                        state.selectedEnvIndex = index
                        state.openEnvEdit(index)
                    },
                    onEdit = { state.openEnvEdit(index) },
                    onDuplicate = {
                        val names = state.environments.map { it.name }.toSet()
                        val uniqueName = ImportExportNaming.generateUniqueEnvironmentName(env.name, names)
                        val duplicated = EnvState(
                            name = uniqueName,
                            variables = env.variables.map { v ->
                                io.github.makeforge.clapi.ui.shared.state.MutableKeyValue(v.key, v.value, v.enabled, v.secret)
                            }
                        )
                        state.environments.add(duplicated)
                        state.log("Environment duplicated: $uniqueName", LogLevel.SUCCESS)
                    },
                    onRename = { onRenameEnvironment(index, env.name) },
                    onDelete = {
                        val action = {
                            if (index in state.environments.indices) {
                                val previousSelected = state.selectedEnvIndex
                                val removedEnv = state.environments.removeAt(index)
                                state.selectedEnvIndex = if (state.environments.isEmpty()) 0 else state.selectedEnvIndex.coerceIn(0, state.environments.lastIndex)
                                UndoableWorkspaceActions.showDeletedEnvironmentSnackbar(
                                    state,
                                    DeletedEnvironmentSnapshot(removedEnv, index, previousSelected),
                                )
                                state.log("Environment deleted: ${removedEnv.name}", LogLevel.INFO)
                            }
                        }
                        if (state.settings.confirmBeforeDelete) {
                            state.showConfirm(
                                title = "Delete environment?",
                                message = "Delete \"${env.name}\"? This cannot be undone.",
                                action = action,
                            )
                        } else {
                            action()
                        }
                    },
                )
            }
        }
    }
}
