package io.github.crustack.clapi.ui.shared.components.dialogs.env

import io.github.crustack.clapi.ui.shared.state.MutableKeyValue
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


