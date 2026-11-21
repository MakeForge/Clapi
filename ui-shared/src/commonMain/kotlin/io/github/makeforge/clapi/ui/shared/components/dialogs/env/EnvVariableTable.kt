package io.github.makeforge.clapi.ui.shared.components.dialogs.env

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
import androidx.compose.ui.input.pointer.PointerIcon
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
import io.github.makeforge.clapi.core.model.opencollection.VariableKind
import io.github.makeforge.clapi.core.model.opencollection.VariableType
import io.github.makeforge.clapi.ui.shared.components.DenseDropdownMenuItem
import io.github.makeforge.clapi.core.network.keepassxc.createKeePassXcClient
import io.github.makeforge.clapi.core.network.keeper.createKeeperClient
import io.github.makeforge.clapi.ui.shared.components.VariableAwareTextField
import io.github.makeforge.clapi.ui.shared.components.HoverTooltip
import io.github.makeforge.clapi.ui.shared.components.IconButtonWithTooltip
import io.github.makeforge.clapi.ui.shared.components.dialogs.dynamicvar.DynamicVariableEditorDialog
import io.github.makeforge.clapi.ui.shared.components.handPointerHover
import io.github.makeforge.clapi.ui.shared.components.rememberAutoSelectTextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import io.github.makeforge.clapi.ui.shared.generated.resources.*
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.platform.*
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.MutableExternalSecret
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily
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
internal fun EnvSecretStatusCell(
    status: String,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
    statusTag: String = "",
    refreshTag: String = "",
    onRefresh: (() -> Unit)? = null,
) {
    val (statusBg, statusBorder, statusFg) = when {
        status.startsWith("✔") -> Triple(
            ClapiColors.Primary.copy(alpha = 0.15f),
            ClapiColors.Primary.copy(alpha = 0.45f),
            ClapiColors.Primary,
        )
        status.startsWith("✖") || status == "Error" -> Triple(
            ClapiColors.Error.copy(alpha = 0.15f),
            ClapiColors.Error.copy(alpha = 0.45f),
            ClapiColors.Error,
        )
        status == "Not Connected" || status == "Closed" || status == "Locked" || status == "Unassociated" -> Triple(
            Color(0xFFF59E0B).copy(alpha = 0.15f),
            Color(0xFFF59E0B).copy(alpha = 0.45f),
            Color(0xFFF59E0B),
        )
        status == "Checking..." -> Triple(
            ClapiColors.Primary.copy(alpha = 0.08f),
            ClapiColors.Border,
            ClapiColors.Primary,
        )
        else -> Triple(
            ClapiColors.SurfaceContainer,
            ClapiColors.Border,
            ClapiColors.OnSurfaceVariant,
        )
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val statusTextComposable: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusBg)
                    .border(1.dp, statusBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = status,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    color = statusFg,
                    modifier = if (statusTag.isNotEmpty()) Modifier.testTag(statusTag) else Modifier,
                )
            }
        }

        if (status == "Error" && !errorMessage.isNullOrBlank()) {
            HoverTooltip(
                tooltip = errorMessage,
                tooltipTag = if (statusTag.isNotEmpty()) "$statusTag-tooltip" else null,
                maxLines = 10,
            ) {
                statusTextComposable()
            }
        } else {
            statusTextComposable()
        }

        if (onRefresh != null) {
            HoverTooltip(tooltip = "Refresh status") {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(ClapiColors.SurfaceContainer)
                        .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                        .clickable(onClick = onRefresh)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .then(if (refreshTag.isNotEmpty()) Modifier.testTag(refreshTag) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_refresh),
                        contentDescription = "Refresh status",
                        tint = ClapiColors.OnSurface,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
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
    isOverridden: Boolean = false,
) {
    var showValue by remember { mutableStateOf(!kv.secret) }
    var showDynamicEditor by remember { mutableStateOf(false) }
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
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
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
            if (isOverridden) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ClapiColors.StatusWarning.copy(alpha = 0.15f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .testTag("env-var-override-badge-${kv.key}"),
                ) {
                    Text(
                        "Overridden",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ClapiColors.StatusWarning,
                    )
                }
            }
        }

        // Value field (masked if secret, Edit Script button if dynamic)
        if (kv.isDynamic) {
            val paramSummary = if (kv.dynamicParameters.isEmpty()) {
                ""
            } else {
                " (${kv.dynamicParameters.joinToString(", ") { it.name }})"
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ClapiColors.Primary.copy(alpha = 0.1f))
                    .border(1.dp, ClapiColors.Primary.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .clickable { showDynamicEditor = true }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("env-var-edit-script-$index"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_code),
                    contentDescription = Strings.editScript,
                    tint = ClapiColors.Primary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = Strings.editScript + paramSummary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ClapiColors.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            EnvTextField(
                value = kv.value,
                onValueChange = { if (!kv.secret) kv.value = it },
                placeholder = if (kv.secret) "-" else Strings.value,
                masked = kv.secret && !showValue && kv.value.isNotEmpty(),
                tag = "env-var-value-$index",
                modifier = Modifier.weight(1f),
                state = if (kv.secret) null else state,
                extraVariables = extraVars,
                excludeVariables = excludeVars,
            )
        }

        // Kind selector (Regular / Secret / Dynamic)
        VariableKindSelector(
            selectedKind = kv.variableKind,
            onKindSelected = { newKind ->
                kv.variableKind = newKind
                when (newKind) {
                    VariableKind.SECRET -> {
                        showValue = false
                        kv.value = ""
                    }
                    VariableKind.DYNAMIC -> {
                        if (kv.dynamicScript.isBlank()) {
                            kv.dynamicScript = "return new Date().toISOString();"
                        }
                    }
                    VariableKind.REGULAR -> Unit
                }
            },
            tag = "env-var-type-toggle-$index",
            modifier = Modifier.width(88.dp),
        )

        // Data Type selector (String / Number / Boolean / Object)
        VariableTypeSelector(
            selectedType = kv.variableType,
            onTypeSelected = { kv.variableType = it },
            tag = "env-var-datatype-$index",
            modifier = Modifier.width(88.dp),
        )

        // Reveal / hide value for secrets
        if (kv.secret && !kv.isDynamic) {
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

    if (showDynamicEditor) {
        DynamicVariableEditorDialog(
            kv = kv,
            onDismiss = { showDynamicEditor = false },
        )
    }
}

val SelectableVariableTypes = listOf(
    VariableType.STRING,
    VariableType.NUMBER,
    VariableType.BOOLEAN,
    VariableType.OBJECT,
)

@Composable
fun variableKindDisplayLabel(kind: VariableKind): String = when (kind) {
    VariableKind.REGULAR -> Strings.t("normal")
    VariableKind.SECRET -> Strings.t("secret")
    VariableKind.DYNAMIC -> Strings.dynamic
}

@Composable
fun variableTypeDisplayLabel(type: VariableType): String = when (type) {
    VariableType.STRING -> Strings.typeString
    VariableType.NUMBER -> Strings.typeNumber
    VariableType.BOOLEAN -> Strings.typeBoolean
    VariableType.OBJECT -> Strings.typeObject
    VariableType.NULL -> "Null"
}

@Composable
fun VariableKindSelector(
    selectedKind: VariableKind,
    onKindSelected: (VariableKind) -> Unit,
    tag: String = "",
    allowedKinds: List<VariableKind> = VariableKind.entries,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = variableKindDisplayLabel(selectedKind)
    val textColor = when (selectedKind) {
        VariableKind.DYNAMIC -> ClapiColors.Primary
        VariableKind.SECRET -> ClapiColors.Tertiary
        VariableKind.REGULAR -> ClapiColors.OnSurface
    }
    val bgColor = when (selectedKind) {
        VariableKind.DYNAMIC -> ClapiColors.Primary.copy(alpha = 0.12f)
        VariableKind.SECRET -> ClapiColors.Tertiary.copy(alpha = 0.1f)
        VariableKind.REGULAR -> ClapiColors.SurfaceContainer
    }
    val borderColor = when (selectedKind) {
        VariableKind.DYNAMIC -> ClapiColors.Primary.copy(alpha = 0.35f)
        VariableKind.SECRET -> ClapiColors.Tertiary.copy(alpha = 0.35f)
        VariableKind.REGULAR -> ClapiColors.Border
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(4.dp))
                .clickable { expanded = true }
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = 6.dp)
                .let { if (tag.isNotBlank()) it.testTag(tag) else it },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painter = painterResource(Res.drawable.ic_keyboard_arrow_down),
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(13.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(ClapiColors.SurfaceHigh).border(1.dp, ClapiColors.Border),
        ) {
            allowedKinds.forEach { kindOption ->
                val optionLabel = variableKindDisplayLabel(kindOption)
                DenseDropdownMenuItem(
                    text = {
                        Text(
                            text = optionLabel,
                            fontSize = 12.sp,
                            color = if (kindOption == selectedKind) ClapiColors.Primary else ClapiColors.OnSurface,
                            fontWeight = if (kindOption == selectedKind) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        onKindSelected(kindOption)
                    },
                    modifier = if (tag.isNotBlank()) Modifier.testTag("$tag-option-${kindOption.value}") else Modifier,
                )
            }
        }
    }
}

@Composable
fun VariableTypeSelector(
    selectedType: VariableType,
    onTypeSelected: (VariableType) -> Unit,
    tag: String = "",
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = variableTypeDisplayLabel(selectedType)

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ClapiColors.SurfaceContainer)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                .clickable { expanded = true }
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = 6.dp)
                .let { if (tag.isNotBlank()) it.testTag(tag) else it },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontFamily = CodeFontFamily,
                color = ClapiColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painter = painterResource(Res.drawable.ic_keyboard_arrow_down),
                contentDescription = null,
                tint = ClapiColors.OnSurfaceDim,
                modifier = Modifier.size(13.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(ClapiColors.SurfaceHigh).border(1.dp, ClapiColors.Border),
        ) {
            SelectableVariableTypes.forEach { typeOption ->
                val optionLabel = variableTypeDisplayLabel(typeOption)
                DenseDropdownMenuItem(
                    text = {
                        Text(
                            text = optionLabel,
                            fontSize = 12.sp,
                            fontFamily = CodeFontFamily,
                            color = if (typeOption == selectedType) ClapiColors.Primary else ClapiColors.OnSurface,
                            fontWeight = if (typeOption == selectedType) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        onTypeSelected(typeOption)
                    },
                    modifier = if (tag.isNotBlank()) Modifier.testTag("$tag-option-${typeOption.value}") else Modifier,
                )
            }
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
    selectAllOnStart: Boolean = false,
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
                selectAllOnStart = selectAllOnStart,
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
        val (tfv, onTfvChange) = rememberAutoSelectTextFieldValue(
            value = if (masked) displayValue else value,
            onValueChange = if (masked) ({}) else onValueChange,
            selectAllOnInitial = selectAllOnStart,
        )
        BasicTextField(
            value = tfv,
            onValueChange = onTfvChange,
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
                    if (value.isEmpty()) {
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
                    .pointerHoverIcon(PointerIcon.Hand)
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
