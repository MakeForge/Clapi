package io.github.crustack.clapi.ui.shared.components.dialogs.env

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import io.github.crustack.clapi.ui.shared.components.IconButtonWithTooltip
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
internal fun TableHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = ClapiColors.OnSurfaceDim,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        modifier = modifier,
    )
}

@Composable
internal fun EnvVariableRow(
    kv: MutableKeyValue,
    index: Int,
    focusKey: Boolean,
    onFocusConsumed: () -> Unit,
    onRowFocusChanged: (Boolean) -> Unit,
    onDelete: () -> Unit,
    state: AppState? = null,
    workingVars: List<MutableKeyValue> = emptyList(),
) {
    var showValue by remember { mutableStateOf(!kv.secret) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val keyFocusRequester = remember { FocusRequester() }
    val baseRowColor = when (environmentRowTone(index, isHovered)) {
        EnvironmentRowTone.EVEN -> ClapiColors.SurfaceVariant
        EnvironmentRowTone.ODD -> ClapiColors.Surface
        EnvironmentRowTone.HOVERED -> ClapiColors.Primary.copy(alpha = 0.08f)
    }
    val rowColor = baseRowColor

    val initialKey = remember { kv.key }
    val excludeVars = remember(kv.key, initialKey) {
        val set = mutableSetOf<String>()
        if (kv.key.isNotBlank()) set.add(kv.key.trim())
        if (initialKey.isNotBlank()) set.add(initialKey.trim())
        set
    }
    val extraVars = remember(workingVars.size, workingVars.map { it.key to it.value }) {
        workingVars.filter { it.key.isNotBlank() }.map { it.key to it.value }
    }

    LaunchedEffect(focusKey) {
        if (focusKey) {
            yield()
            keyFocusRequester.requestFocus()
            onFocusConsumed()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowColor)
            .hoverable(interactionSource)
            .focusGroup()
            .onFocusChanged { onRowFocusChanged(it.hasFocus) }
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("env-var-row-$index"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Enabled checkbox
        Checkbox(
            checked = kv.enabled,
            onCheckedChange = { kv.enabled = it },
            modifier = Modifier.size(24.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = ClapiColors.Primary,
                uncheckedColor = ClapiColors.OnSurfaceDim,
            ),
        )

        // Key field
        EnvTextField(
            value = kv.key,
            onValueChange = { kv.key = it },
            placeholder = Strings.variableName,
            tag = "env-var-key-$index",
            modifier = Modifier.weight(1f),
            focusRequester = keyFocusRequester,
            state = state,
            extraVariables = extraVars,
            excludeVariables = excludeVars,
        )

        // Value field (masked if secret)
        EnvTextField(
            value = kv.value,
            onValueChange = { kv.value = it },
            placeholder = if (kv.secret) "••••••••" else Strings.value,
            masked = kv.secret && !showValue,
            tag = "env-var-value-$index",
            modifier = Modifier.weight(1f),
            state = state,
            extraVariables = extraVars,
            excludeVariables = excludeVars,
        )

        // Type toggle (Normal / Secret)
        Row(
            modifier = Modifier.width(90.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (kv.secret) Strings.t("secret") else Strings.t("normal"),
                fontSize = 11.sp,
                color = if (kv.secret) ClapiColors.Tertiary else ClapiColors.OnSurfaceDim,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (kv.secret) ClapiColors.Tertiary.copy(alpha = 0.1f) else Color.Transparent)
                    .clickable { kv.secret = !kv.secret; if (kv.secret) showValue = false }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }

        // Reveal / hide value for secrets
        if (kv.secret) {
            val toggleTooltip = if (showValue) "Hide value" else "Show value"
            IconButtonWithTooltip(
                onClick = { showValue = !showValue },
                tooltip = toggleTooltip,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    if (showValue) painterResource(Res.drawable.ic_visibility_off) else painterResource(Res.drawable.ic_visibility),
                    contentDescription = toggleTooltip,
                    tint = ClapiColors.OnSurfaceDim,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            Spacer(Modifier.width(24.dp))
        }

        // Delete
        IconButtonWithTooltip(
            onClick = onDelete,
            tooltip = Strings.delete,
            modifier = Modifier.size(24.dp).testTag("env-var-delete-$index"),
        ) {
            Icon(painter = painterResource(Res.drawable.ic_delete), contentDescription = Strings.delete, tint = ClapiColors.OnSurfaceDim, modifier = Modifier.size(14.dp))
        }
    }
}

enum class EnvironmentRowTone {
    EVEN,
    ODD,
    HOVERED,
}

fun environmentRowTone(index: Int, isHovered: Boolean): EnvironmentRowTone {
    if (isHovered) return EnvironmentRowTone.HOVERED
    return if (index % 2 == 0) EnvironmentRowTone.EVEN else EnvironmentRowTone.ODD
}

@Composable
internal fun EnvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    masked: Boolean = false,
    tag: String = "",
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    state: AppState? = null,
    extraVariables: List<Pair<String, String>> = emptyList(),
    excludeVariables: Set<String> = emptySet(),
) {
    val displayValue = if (masked) "•".repeat(value.length.coerceAtMost(20)) else value
    var isFocused by remember { mutableStateOf(false) }

    if (state != null && !masked) {
        Box(
            modifier = modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isFocused) ClapiColors.Surface else ClapiColors.SurfaceContainer)
                .border(1.dp, if (isFocused) ClapiColors.Primary else ClapiColors.Border, RoundedCornerShape(4.dp))
                .onFocusChanged { focusState -> isFocused = focusState.isFocused }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            VariableAwareTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder,
                singleLine = true,
                textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
                state = state,
                extraVariables = extraVariables,
                excludeVariables = excludeVariables,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier),
            )
            if (isFocused && tag.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(1.dp)
                        .align(Alignment.TopStart)
                        .testTag("$tag-focused"),
                )
            }
        }
    } else {
        BasicTextField(
            value = if (masked) displayValue else value,
            onValueChange = if (masked) ({}) else onValueChange,
            singleLine = true,
            readOnly = masked,
            textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
            cursorBrush = SolidColor(ClapiColors.Primary),
            modifier = modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isFocused) ClapiColors.Surface else ClapiColors.SurfaceContainer)
                .border(1.dp, if (isFocused) ClapiColors.Primary else ClapiColors.Border, RoundedCornerShape(4.dp))
                .onFocusChanged { focusState -> isFocused = focusState.isFocused }
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier),
            decorationBox = { inner ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (value.isEmpty() && !masked) {
                        Text(placeholder, color = ClapiColors.OnSurfaceDim, fontSize = 13.sp)
                    }
                    inner()
                    if (isFocused && tag.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(1.dp)
                                .align(Alignment.TopStart)
                                .testTag("$tag-focused"),
                        )
                    }
                }
            },
        )
    }
}


@Composable
internal fun EnvironmentVariableSearch(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onClear: () -> Unit,
    shown: Int,
    total: Int,
    focusRequester: FocusRequester,
) {
    val placeholder = Strings.searchEnvironmentVariables
    val countLabel = environmentVariableResultCountLabel(
        shown,
        total,
        Strings.t("environment_variable_result_count"),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ClapiColors.SurfaceContainer)
            .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("env-variable-search"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(Res.drawable.ic_search),
            contentDescription = null,
            tint = ClapiColors.OnSurfaceDim,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 13.sp),
            cursorBrush = SolidColor(ClapiColors.Primary),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .testTag("env-variable-search-input")
                .semantics { contentDescription = placeholder },
            decorationBox = { inner ->
                Box {
                    if (value.text.isEmpty()) {
                        Text(placeholder, color = ClapiColors.OnSurfaceDim, fontSize = 13.sp)
                    }
                    inner()
                }
            },
        )
        if (value.text.isNotEmpty()) {
            Icon(painter = painterResource(Res.drawable.ic_close),
                contentDescription = Strings.clearSearch,
                tint = ClapiColors.OnSurfaceDim,
                modifier = Modifier
                    .size(14.dp)
                    .clickable(onClick = onClear)
                    .testTag("env-search-clear"),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            countLabel,
            color = ClapiColors.OnSurfaceDim,
            fontSize = 11.sp,
            modifier = Modifier
                .testTag("env-search-count")
                .semantics { contentDescription = countLabel },
        )
    }
}
