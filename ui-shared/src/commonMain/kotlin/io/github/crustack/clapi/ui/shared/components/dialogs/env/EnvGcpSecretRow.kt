package io.github.crustack.clapi.ui.shared.components.dialogs.env

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.crustack.clapi.core.network.keepassxc.createKeePassXcClient
import io.github.crustack.clapi.core.network.keeper.createKeeperClient
import io.github.crustack.clapi.ui.shared.components.VariableAwareTextField
import io.github.crustack.clapi.ui.shared.components.handPointerHover
import io.github.crustack.clapi.ui.shared.generated.resources.*
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.platform.*
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.MutableExternalSecret
import io.github.crustack.clapi.ui.shared.state.MutableKeyValue
import io.github.crustack.clapi.ui.shared.theme.ClapiColors
import io.github.crustack.clapi.ui.shared.theme.CodeFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun EnvGcpSecretRow(
    secret: MutableExternalSecret,
    index: Int,
    workingVars: List<MutableKeyValue>,
    state: AppState? = null,
    onDelete: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val baseRowColor = when (environmentRowTone(index, isHovered)) {
        EnvironmentRowTone.EVEN -> ClapiColors.SurfaceVariant
        EnvironmentRowTone.ODD -> ClapiColors.Surface
        EnvironmentRowTone.HOVERED -> ClapiColors.Primary.copy(alpha = 0.08f)
    }
    val extraVars = remember(workingVars.size, workingVars.map { it.key to it.value }) {
        workingVars.filter { it.key.isNotBlank() }.map { it.key to it.value }
    }
    val initialName = remember { secret.name }
    val excludeSecretVars = remember(secret.name, initialName) {
        val set = mutableSetOf<String>()
        if (secret.name.isNotBlank()) {
            set.add(secret.name.trim())
            set.add("_secret.${secret.name.trim()}")
        }
        if (initialName.isNotBlank()) {
            set.add(initialName.trim())
            set.add("_secret.${initialName.trim()}")
        }
        set
    }

    var filtersExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(baseRowColor)
            .hoverable(interactionSource)
            .testTag("env-secret-row-$index"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Checkbox(
                checked = secret.enabled,
                onCheckedChange = { secret.enabled = it },
                modifier = Modifier.size(24.dp).testTag("env-secret-row-$index-enabled"),
                colors = CheckboxDefaults.colors(
                    checkedColor = ClapiColors.Primary,
                    uncheckedColor = ClapiColors.OnSurfaceDim,
                ),
            )

            EnvTextField(
                value = secret.name,
                onValueChange = { secret.name = it },
                placeholder = "secret_name",
                tag = "env-secret-row-$index-name",
                modifier = Modifier.weight(1.0f),
                state = state,
                extraVariables = extraVars,
                excludeVariables = excludeSecretVars,
            )

            EnvTextField(
                value = secret.path,
                onValueChange = { secret.path = it },
                placeholder = "secret-id",
                tag = "env-secret-row-$index-path",
                modifier = Modifier.weight(1.2f),
                state = state,
                extraVariables = extraVars,
                excludeVariables = excludeSecretVars,
            )

            EnvTextField(
                value = secret.url,
                onValueChange = { secret.url = it },
                placeholder = "project override (optional)",
                tag = "env-secret-row-$index-project",
                modifier = Modifier.weight(1.1f),
                state = state,
                extraVariables = extraVars,
                excludeVariables = excludeSecretVars,
            )

            EnvTextField(
                value = secret.targetField,
                onValueChange = { secret.targetField = it },
                placeholder = "target key (optional)",
                tag = "env-secret-row-$index-target-field",
                modifier = Modifier.weight(0.9f),
                state = state,
                extraVariables = extraVars,
                excludeVariables = excludeSecretVars,
            )

            Box(
                modifier = Modifier.weight(0.8f),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (filtersExpanded) ClapiColors.Primary.copy(alpha = 0.15f) else ClapiColors.SurfaceContainer)
                        .border(
                            1.dp,
                            if (filtersExpanded) ClapiColors.Primary else ClapiColors.Border,
                            RoundedCornerShape(6.dp),
                        )
                        .clickable { filtersExpanded = !filtersExpanded }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("env-secret-row-$index-filter-toggle"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            if (secret.customFilterList.isEmpty()) "Filters" else "Filters (${secret.customFilterList.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (filtersExpanded) ClapiColors.Primary else ClapiColors.OnSurfaceDim,
                        )
                        Icon(
                            painter = if (filtersExpanded) painterResource(Res.drawable.ic_keyboard_arrow_up) else painterResource(Res.drawable.ic_keyboard_arrow_down),
                            contentDescription = if (filtersExpanded) "Collapse filters" else "Expand filters",
                            tint = if (filtersExpanded) ClapiColors.Primary else ClapiColors.OnSurfaceDim,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            EnvTextField(
                value = secret.description,
                onValueChange = { secret.description = it },
                placeholder = "Description (optional)",
                tag = "env-secret-row-$index-desc",
                modifier = Modifier.weight(1.0f),
                state = state,
                extraVariables = extraVars,
                excludeVariables = excludeSecretVars,
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp).testTag("env-secret-row-$index-delete"),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_delete),
                    contentDescription = Strings.delete,
                    tint = ClapiColors.OnSurfaceDim,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        if (filtersExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ClapiColors.SurfaceContainer.copy(alpha = 0.4f))
                    .border(1.dp, ClapiColors.Border)
                    .padding(start = 32.dp, end = 12.dp, top = 6.dp, bottom = 8.dp)
                    .testTag("env-secret-row-$index-filters-panel"),
            ) {
                Text(
                    "Custom Field Filters (Secret key-value attributes match):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ClapiColors.OnSurfaceDim,
                )
                Spacer(Modifier.height(4.dp))
                secret.customFilterList.forEachIndexed { fIdx, kv ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        EnvTextField(
                            value = kv.key,
                            onValueChange = {
                                kv.key = it
                                secret.syncCustomFiltersFromList()
                            },
                            placeholder = "attribute name",
                            tag = "env-secret-row-$index-filter-$fIdx-key",
                            modifier = Modifier.width(130.dp),
                            state = state,
                            extraVariables = extraVars,
                            excludeVariables = excludeSecretVars,
                        )
                        Text("=", fontSize = 11.sp, color = ClapiColors.OnSurfaceDim)
                        EnvTextField(
                            value = kv.value,
                            onValueChange = {
                                kv.value = it
                                secret.syncCustomFiltersFromList()
                            },
                            placeholder = "attribute value",
                            tag = "env-secret-row-$index-filter-$fIdx-val",
                            modifier = Modifier.weight(1f),
                            state = state,
                            extraVariables = extraVars,
                            excludeVariables = excludeSecretVars,
                        )
                        IconButton(
                            onClick = {
                                secret.customFilterList.removeAt(fIdx)
                                secret.syncCustomFiltersFromList()
                            },
                            modifier = Modifier.size(20.dp).testTag("env-secret-row-$index-filter-$fIdx-del"),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_close),
                                contentDescription = "Delete filter",
                                tint = ClapiColors.OnSurfaceDim,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                var newFilterKey by remember { mutableStateOf("") }
                var newFilterVal by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    EnvTextField(
                        value = newFilterKey,
                        onValueChange = { newFilterKey = it },
                        placeholder = "field (e.g. stage)",
                        tag = "env-secret-row-$index-new-filter-key",
                        modifier = Modifier.width(130.dp),
                        state = state,
                        extraVariables = extraVars,
                        excludeVariables = excludeSecretVars,
                    )
                    Text("=", fontSize = 11.sp, color = ClapiColors.OnSurfaceDim)
                    EnvTextField(
                        value = newFilterVal,
                        onValueChange = { newFilterVal = it },
                        placeholder = "expected value or {{var}}",
                        tag = "env-secret-row-$index-new-filter-val",
                        modifier = Modifier.weight(1f),
                        state = state,
                        extraVariables = extraVars,
                        excludeVariables = excludeSecretVars,
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ClapiColors.Primary)
                            .clickable {
                                if (newFilterKey.isNotBlank()) {
                                    secret.customFilterList.add(MutableKeyValue(key = newFilterKey.trim(), value = newFilterVal.trim()))
                                    secret.syncCustomFiltersFromList()
                                    newFilterKey = ""
                                    newFilterVal = ""
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("env-secret-row-$index-add-filter-btn"),
                    ) {
                        Text("Add Filter", color = ClapiColors.OnPrimary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
