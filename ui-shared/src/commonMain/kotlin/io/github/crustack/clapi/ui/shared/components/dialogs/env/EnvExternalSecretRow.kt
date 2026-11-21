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
internal fun EnvExternalSecretRow(
    secret: MutableExternalSecret,
    index: Int,
    workingVars: List<MutableKeyValue> = emptyList(),
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(baseRowColor)
            .hoverable(interactionSource)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("env-secret-row-$index"),
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
            placeholder = "client_id",
            tag = "env-secret-row-$index-name",
            modifier = Modifier.weight(1.2f),
            state = state,
            extraVariables = extraVars,
            excludeVariables = excludeSecretVars,
        )

        EnvTextField(
            value = secret.path,
            onValueChange = { secret.path = it },
            placeholder = "payment/foo",
            tag = "env-secret-row-$index-path",
            modifier = Modifier.weight(1.5f),
            state = state,
            extraVariables = extraVars,
            excludeVariables = excludeSecretVars,
        )

        EnvTextField(
            value = secret.description,
            onValueChange = { secret.description = it },
            placeholder = "Description (optional)",
            tag = "env-secret-row-$index-desc",
            modifier = Modifier.weight(1.5f),
            state = state,
            extraVariables = extraVars,
            excludeVariables = excludeSecretVars,
        )

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(24.dp).testTag("env-secret-row-$index-delete"),
        ) {
            Icon(painter = painterResource(Res.drawable.ic_delete),
                contentDescription = Strings.delete,
                tint = ClapiColors.OnSurfaceDim,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
