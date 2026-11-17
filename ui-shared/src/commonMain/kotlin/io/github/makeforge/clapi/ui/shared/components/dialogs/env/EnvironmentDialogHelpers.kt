package io.github.makeforge.clapi.ui.shared.components.dialogs.env

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.core.model.opencollection.ExternalSecretType
import io.github.makeforge.clapi.ui.shared.components.IconButtonWithTooltip
import io.github.makeforge.clapi.ui.shared.components.externalSecretIcon
import io.github.makeforge.clapi.ui.shared.generated.resources.*
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily
import org.jetbrains.compose.resources.painterResource
import kotlin.math.min
import kotlin.math.roundToInt

internal const val ENV_DIALOG_MIN_WIDTH_DP = 600f
internal const val ENV_DIALOG_MIN_HEIGHT_DP = 460f
internal const val ENV_DIALOG_MARGIN_DP = 16f

internal enum class EnvironmentResizeHandle(
    val affectsLeft: Boolean = false,
    val affectsRight: Boolean = false,
    val affectsTop: Boolean = false,
    val affectsBottom: Boolean = false,
) {
    LEFT(affectsLeft = true),
    RIGHT(affectsRight = true),
    TOP(affectsTop = true),
    BOTTOM(affectsBottom = true),
    TOP_LEFT(affectsLeft = true, affectsTop = true),
    TOP_RIGHT(affectsRight = true, affectsTop = true),
    BOTTOM_LEFT(affectsLeft = true, affectsBottom = true),
    BOTTOM_RIGHT(affectsRight = true, affectsBottom = true),
}

internal data class EnvironmentDialogGeometry(
    val widthDp: Float,
    val heightDp: Float,
    val offsetXDp: Float = 0f,
    val offsetYDp: Float = 0f,
)

internal fun resizeEnvironmentDialog(
    geometry: EnvironmentDialogGeometry,
    handle: EnvironmentResizeHandle,
    deltaXDp: Float,
    deltaYDp: Float,
    minWidthDp: Float,
    minHeightDp: Float,
    maxWidthDp: Float,
    maxHeightDp: Float,
): EnvironmentDialogGeometry {
    val widthDelta = when {
        handle.affectsLeft -> -deltaXDp
        handle.affectsRight -> deltaXDp
        else -> 0f
    }
    val heightDelta = when {
        handle.affectsTop -> -deltaYDp
        handle.affectsBottom -> deltaYDp
        else -> 0f
    }
    val width = (geometry.widthDp + widthDelta).coerceIn(minWidthDp, maxWidthDp)
    val height = (geometry.heightDp + heightDelta).coerceIn(minHeightDp, maxHeightDp)
    val appliedWidth = width - geometry.widthDp
    val appliedHeight = height - geometry.heightDp
    return geometry.copy(
        widthDp = width,
        heightDp = height,
        offsetXDp = geometry.offsetXDp + when {
            handle.affectsLeft -> -appliedWidth / 2f
            handle.affectsRight -> appliedWidth / 2f
            else -> 0f
        },
        offsetYDp = geometry.offsetYDp + when {
            handle.affectsTop -> -appliedHeight / 2f
            handle.affectsBottom -> appliedHeight / 2f
            else -> 0f
        },
    )
}

internal fun clampEnvironmentDialog(
    geometry: EnvironmentDialogGeometry,
    viewportWidthDp: Float,
    viewportHeightDp: Float,
): EnvironmentDialogGeometry {
    if (viewportWidthDp <= 0f || viewportHeightDp <= 0f) return geometry
    val maxWidth = (viewportWidthDp - ENV_DIALOG_MARGIN_DP * 2f).coerceAtLeast(1f)
    val maxHeight = (viewportHeightDp - ENV_DIALOG_MARGIN_DP * 2f).coerceAtLeast(1f)
    val width = geometry.widthDp.coerceIn(min(ENV_DIALOG_MIN_WIDTH_DP, maxWidth), maxWidth)
    val height = geometry.heightDp.coerceIn(min(ENV_DIALOG_MIN_HEIGHT_DP, maxHeight), maxHeight)
    val maxOffsetX = ((viewportWidthDp - width) / 2f - ENV_DIALOG_MARGIN_DP).coerceAtLeast(0f)
    val maxOffsetY = ((viewportHeightDp - height) / 2f - ENV_DIALOG_MARGIN_DP).coerceAtLeast(0f)
    return geometry.copy(
        widthDp = width,
        heightDp = height,
        offsetXDp = geometry.offsetXDp.coerceIn(-maxOffsetX, maxOffsetX),
        offsetYDp = geometry.offsetYDp.coerceIn(-maxOffsetY, maxOffsetY),
    )
}

internal fun environmentVariableMatches(key: String, value: String, query: String): Boolean {
    val normalized = query.trim()
    return normalized.isEmpty() || key.contains(normalized, ignoreCase = true) ||
        value.contains(normalized, ignoreCase = true)
}

internal fun visibleEnvironmentVariables(
    variables: List<MutableKeyValue>,
    query: String,
    pinnedUid: String? = null,
): List<MutableKeyValue> = variables.filter { kv ->
    environmentVariableMatches(kv.key, kv.value, query) || kv.uid == pinnedUid
}

internal fun environmentVariableResultCountLabel(shown: Int, total: Int, template: String): String =
    template.replace("{shown}", shown.toString()).replace("{total}", total.toString())

data class EnvironmentColorPreset(
    val name: String,
    val hex: String,
)

val ENVIRONMENT_COLOR_PRESETS = listOf(
    EnvironmentColorPreset("Red", "#EF4444"),
    EnvironmentColorPreset("Orange", "#F97316"),
    EnvironmentColorPreset("Amber", "#F59E0B"),
    EnvironmentColorPreset("Green", "#10B981"),
    EnvironmentColorPreset("Cyan", "#06B6D4"),
    EnvironmentColorPreset("Blue", "#3B82F6"),
    EnvironmentColorPreset("Purple", "#8B5CF6"),
    EnvironmentColorPreset("Gray", "#6B7280"),
)

fun parseHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6 && clean.length != 8) return null
    return runCatching {
        if (clean.length == 6) {
            Color(clean.toLong(16) or 0xFF000000L)
        } else {
            Color(clean.toLong(16))
        }
    }.getOrNull()
}

fun normalizeHexColor(hex: String?): String? {
    if (hex.isNullOrBlank()) return null
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6 && clean.length != 8) return null
    val isValid = clean.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    if (!isValid) return null
    return "#${clean.uppercase()}"
}

fun ExternalSecretType.displayName(): String = when (this) {
    ExternalSecretType.HASHICORP_VAULT_SERVER -> "HashiCorp Vault (Server)"
    ExternalSecretType.HASHICORP_VAULT_CLOUD -> "HashiCorp Vault (Cloud)"
    ExternalSecretType.KEEPASSXC -> "KeePassXC"
    ExternalSecretType.KEEPER_SECURITY_CLOUD -> "Keeper Security Cloud"
    ExternalSecretType.AWS_SECRETS_MANAGER -> "AWS Secrets Manager"
    ExternalSecretType.AZURE_KEY_VAULT -> "Azure Key Vault"
    ExternalSecretType.GCP_SECRETS_MANAGER -> "GCP Secret Manager"
    ExternalSecretType.ONE_PASSWORD -> "1Password"
}

fun externalSecretDisplayName(provider: String?): String {
    val type = ExternalSecretType.fromValue(provider)
    return type?.displayName() ?: (provider ?: "")
}

val SupportedExternalSecretProviders: List<ExternalSecretType> = listOf(
    ExternalSecretType.HASHICORP_VAULT_SERVER,
    ExternalSecretType.KEEPASSXC,
    ExternalSecretType.KEEPER_SECURITY_CLOUD,
    ExternalSecretType.AWS_SECRETS_MANAGER,
    ExternalSecretType.AZURE_KEY_VAULT,
    ExternalSecretType.GCP_SECRETS_MANAGER,
)

@Composable
internal fun EnvQuickAddTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    tag: String = "",
    onEnter: () -> Unit = {},
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 12.sp, fontFamily = CodeFontFamily),
        singleLine = true,
        cursorBrush = SolidColor(ClapiColors.Primary),
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(ClapiColors.Surface)
            .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) {
                    onEnter()
                    true
                } else false
            }
            .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier),
        decorationBox = { innerTextField ->
            if (value.isEmpty()) {
                Text(placeholder, color = ClapiColors.OnSurfaceDim, fontSize = 12.sp, fontFamily = CodeFontFamily)
            }
            innerTextField()
        },
    )
}

@Composable
internal fun EnvQuickAddButton(
    onClick: () -> Unit,
    tooltip: String,
    tag: String = "",
    modifier: Modifier = Modifier,
) {
    IconButtonWithTooltip(
        onClick = onClick,
        tooltip = tooltip,
        modifier = modifier.size(24.dp).then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_add),
            contentDescription = tooltip,
            tint = ClapiColors.Primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
internal fun EnvironmentTabButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "",
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) ClapiColors.SelectedItem else ClapiColors.SurfaceContainer)
            .border(
                1.dp,
                if (isSelected) ClapiColors.Primary else ClapiColors.Border,
                RoundedCornerShape(6.dp),
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier),
    ) {
        Text(
            title,
            color = if (isSelected) ClapiColors.OnSurface else ClapiColors.OnSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
internal fun ExternalSecretProviderDropdown(
    selectedProvider: String,
    onProviderSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedType = ExternalSecretType.fromValue(selectedProvider)
    val displayName = selectedType?.displayName() ?: selectedProvider
    val icon = externalSecretIcon(selectedType)

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .height(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(ClapiColors.SurfaceContainer)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("env-secrets-provider-selector"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(16.dp),
            )
            Text(
                displayName,
                color = ClapiColors.OnSurface,
                fontSize = 12.sp,
            )
            Icon(
                painter = painterResource(Res.drawable.ic_keyboard_arrow_down),
                contentDescription = null,
                tint = ClapiColors.OnSurfaceDim,
                modifier = Modifier.size(14.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SupportedExternalSecretProviders.forEach { providerType ->
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            painter = painterResource(externalSecretIcon(providerType)),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    text = { Text(providerType.displayName(), fontSize = 12.sp) },
                    onClick = {
                        onProviderSelected(providerType.value)
                        expanded = false
                    },
                    modifier = Modifier.testTag("env-provider-option-${providerType.value}"),
                )
            }
        }
    }
}



