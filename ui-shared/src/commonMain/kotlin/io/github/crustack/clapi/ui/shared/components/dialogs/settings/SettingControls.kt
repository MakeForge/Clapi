package io.github.crustack.clapi.ui.shared.components.dialogs.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.crustack.clapi.core.model.opencollection.ExternalSecretType
import io.github.crustack.clapi.core.network.keepassxc.DefaultKeePassXcClientManager
import io.github.crustack.clapi.core.network.keepassxc.KeePassXcStatus
import io.github.crustack.clapi.core.network.keepassxc.createKeePassXcClient
import io.github.crustack.clapi.core.network.keeper.createKeeperClient
import io.github.crustack.clapi.core.network.keeper.initializeAndBindKeeperStorage
import io.github.crustack.clapi.ui.shared.components.handPointerHover
import io.github.crustack.clapi.ui.shared.components.IconButtonWithTooltip
import io.github.crustack.clapi.ui.shared.generated.resources.*
import io.github.crustack.clapi.ui.shared.i18n.AppLanguage
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.persistence.SettingsRepository
import io.github.crustack.clapi.ui.shared.platform.*
import io.github.crustack.clapi.ui.shared.state.*
import io.github.crustack.clapi.ui.shared.theme.ClapiColors
import io.github.crustack.clapi.ui.shared.theme.CodeFontFamily
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun <T> SettingDropdown(
    label: String,
    description: String = "",
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    tagPrefix: String = "setting-dropdown",
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = options.firstOrNull { it.first == selected }?.second ?: selected.toString()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 13.sp, color = ClapiColors.OnSurface)
        if (description.isNotEmpty()) {
            Text(description, fontSize = 11.sp, color = ClapiColors.OnSurfaceDim)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ClapiColors.SurfaceContainer)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("$tagPrefix-selector"),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = selectedText,
                    color = ClapiColors.OnSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.testTag("$tagPrefix-selected-text"),
                )
                Icon(
                    painter = painterResource(Res.drawable.ic_keyboard_arrow_down),
                    contentDescription = null,
                    tint = ClapiColors.OnSurfaceDim,
                    modifier = Modifier.size(16.dp),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.testTag("$tagPrefix-menu"),
            ) {
                options.forEach { (value, text) ->
                    val isSelected = value == selected
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = text,
                                color = if (isSelected) ClapiColors.Primary else ClapiColors.OnSurface,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            onSelected(value)
                            expanded = false
                        },
                        modifier = Modifier
                            .testTag("$tagPrefix-option-${value.toString().lowercase().replace(" ", "-")}"),
                    )
                }
            }
        }
    }
}

@Composable
internal fun <T> SettingChoice(
    label: String,
    description: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    tagPrefix: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 13.sp, color = ClapiColors.OnSurface)
        if (description.isNotEmpty()) {
            Text(description, fontSize = 11.sp, color = ClapiColors.OnSurfaceDim)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, text) ->
                val active = value == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (active) ClapiColors.SelectedItem else ClapiColors.SurfaceContainer)
                        .border(
                            width = if (active) 2.dp else 1.dp,
                            color = if (active) ClapiColors.Primary else ClapiColors.Border,
                            shape = RoundedCornerShape(6.dp),
                        )
                        .clickable { onSelected(value) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("$tagPrefix-${text.lowercase().replace(" ", "-")}"),
                ) {
                    Text(
                        text = text,
                        color = if (active) ClapiColors.OnSurface else ClapiColors.OnSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

// ── Setting primitives ──────────────────────────────────────────

@Composable
internal fun SettingToggle(
    label: String,
    description: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String = "",
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(label, fontSize = 13.sp, color = ClapiColors.OnSurface)
            if (description.isNotEmpty()) {
                Text(description, fontSize = 11.sp, color = ClapiColors.OnSurfaceDim)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.handPointerHover().then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier),
            colors = SwitchDefaults.colors(
                checkedThumbColor  = ClapiColors.OnPrimary,
                checkedTrackColor  = ClapiColors.Primary,
                uncheckedThumbColor= ClapiColors.OnSurfaceDim,
                uncheckedTrackColor= ClapiColors.SurfaceHigh,
            ),
        )
    }
}

@Composable
internal fun SettingNumberField(label: String, value: Int, onValueChange: (Int) -> Unit) {
    // M-10: Maintain local text state so mid-edit values (e.g. empty string
    // while the user is deleting digits) are never silently discarded.
    // A red border signals that the current text cannot be parsed as a number.
    var localText by remember(value) { mutableStateOf(value.toString()) }
    val isValid = localText.toIntOrNull() != null

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = ClapiColors.OnSurface, modifier = Modifier.weight(1f))
        BasicTextField(
            value = localText,
            onValueChange = { newText ->
                // Allow any text while typing; only commit valid integers.
                localText = newText
                newText.toIntOrNull()?.let(onValueChange)
            },
            singleLine = true,
            textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
            cursorBrush = SolidColor(ClapiColors.Primary),
            modifier = Modifier
                .width(80.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ClapiColors.SurfaceContainer)
                // Red border for invalid input so the user gets immediate feedback.
                .border(
                    1.dp,
                    if (isValid) ClapiColors.Border else ClapiColors.Error,
                    RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
internal fun SettingTextField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String = "", tag: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 13.sp, color = ClapiColors.OnSurface)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
            cursorBrush = SolidColor(ClapiColors.Primary),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(ClapiColors.SurfaceContainer)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, color = ClapiColors.OnSurfaceDim, fontSize = 13.sp)
                inner()
            },
        )
    }
}

@Composable
internal fun SettingPasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    tag: String = "",
) {
    var showPassword by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 13.sp, color = ClapiColors.OnSurface)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(ClapiColors.SurfaceContainer)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
                cursorBrush = SolidColor(ClapiColors.Primary),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
                    .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier),
                decorationBox = { inner ->
                    if (value.isEmpty() && !showPassword) {
                        Text(placeholder.ifEmpty { "••••••••" }, color = ClapiColors.OnSurfaceDim, fontSize = 13.sp)
                    } else if (value.isEmpty()) {
                        Text(placeholder, color = ClapiColors.OnSurfaceDim, fontSize = 13.sp)
                    }
                    inner()
                },
            )
            IconButton(
                onClick = { showPassword = !showPassword },
                modifier = Modifier
                    .size(28.dp)
                    .then(if (tag.isNotEmpty()) Modifier.testTag("$tag-toggle") else Modifier),
            ) {
                Icon(
                    painter = if (showPassword) painterResource(Res.drawable.ic_visibility_off) else painterResource(Res.drawable.ic_visibility),
                    contentDescription = if (showPassword) "Hide secret" else "Show secret",
                    tint = ClapiColors.OnSurfaceDim,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
internal fun SettingAction(
    label: String,
    description: String = "",
    actionLabel: String = "Run",
    actionColor: Color = ClapiColors.Primary,
    tag: String = "",
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(label, fontSize = 13.sp, color = ClapiColors.OnSurface)
            if (description.isNotEmpty()) {
                Text(description, fontSize = 11.sp, color = ClapiColors.OnSurfaceDim)
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(actionColor.copy(alpha = 0.12f))
                .border(1.dp, actionColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                .handPointerHover()
                .clickable(onClick = onAction)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .then(if (tag.isNotEmpty()) Modifier.testTag("$tag-action") else Modifier),
        ) {
            Text(actionLabel, color = actionColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(color = ClapiColors.Border.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
}
