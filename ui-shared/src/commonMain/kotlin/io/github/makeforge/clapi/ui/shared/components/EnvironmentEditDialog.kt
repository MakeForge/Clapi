// Modified by MakeForge (2026)
package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.makeforge.clapi.ui.shared.components.dialogs.env.EnvironmentEditDialog as DelegateEnvironmentEditDialog
import io.github.makeforge.clapi.ui.shared.components.dialogs.env.EnvironmentEditTab
import io.github.makeforge.clapi.ui.shared.components.dialogs.env.EnvironmentResizeHandle
import io.github.makeforge.clapi.ui.shared.components.dialogs.env.visibleEnvironmentVariables as delegateVisibleEnvironmentVariables
import io.github.makeforge.clapi.ui.shared.components.dialogs.env.environmentVariableResultCountLabel as delegateEnvironmentVariableResultCountLabel
import io.github.makeforge.clapi.ui.shared.components.dialogs.env.environmentVariableMatches as delegateEnvironmentVariableMatches
import io.github.makeforge.clapi.ui.shared.components.dialogs.env.resizeEnvironmentDialog as delegateResizeEnvironmentDialog
import io.github.makeforge.clapi.ui.shared.components.dialogs.env.clampEnvironmentDialog as delegateClampEnvironmentDialog
import io.github.makeforge.clapi.ui.shared.components.dialogs.env.EnvironmentRowTone
import io.github.makeforge.clapi.ui.shared.components.dialogs.env.environmentRowTone as delegateEnvironmentRowTone
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue

@Composable
fun EnvironmentEditDialog(
    state: AppState,
    modifier: Modifier = Modifier,
    useDialogWindow: Boolean = true,
) {
    DelegateEnvironmentEditDialog(
        state = state,
        modifier = modifier,
        useDialogWindow = useDialogWindow,
    )
}

// Backwards compatibility re-exports
internal fun visibleEnvironmentVariables(
    variables: List<MutableKeyValue>,
    query: String,
    pinnedUid: String? = null,
): List<MutableKeyValue> = delegateVisibleEnvironmentVariables(variables, query, pinnedUid)

internal fun environmentVariableResultCountLabel(shown: Int, total: Int, template: String): String =
    delegateEnvironmentVariableResultCountLabel(shown, total, template)

internal fun environmentVariableMatches(key: String, value: String, query: String): Boolean =
    delegateEnvironmentVariableMatches(key, value, query)

internal typealias EnvironmentDialogGeometry = io.github.makeforge.clapi.ui.shared.components.dialogs.env.EnvironmentDialogGeometry
internal typealias EnvironmentResizeHandle = io.github.makeforge.clapi.ui.shared.components.dialogs.env.EnvironmentResizeHandle
internal typealias EnvironmentEditTab = io.github.makeforge.clapi.ui.shared.components.dialogs.env.EnvironmentEditTab
typealias EnvironmentRowTone = io.github.makeforge.clapi.ui.shared.components.dialogs.env.EnvironmentRowTone

internal fun resizeEnvironmentDialog(
    geometry: EnvironmentDialogGeometry,
    handle: EnvironmentResizeHandle,
    deltaXDp: Float,
    deltaYDp: Float,
    minWidthDp: Float,
    minHeightDp: Float,
    maxWidthDp: Float,
    maxHeightDp: Float,
): EnvironmentDialogGeometry = delegateResizeEnvironmentDialog(geometry, handle, deltaXDp, deltaYDp, minWidthDp, minHeightDp, maxWidthDp, maxHeightDp)

internal fun clampEnvironmentDialog(
    geometry: EnvironmentDialogGeometry,
    viewportWidthDp: Float,
    viewportHeightDp: Float,
): EnvironmentDialogGeometry = delegateClampEnvironmentDialog(geometry, viewportWidthDp, viewportHeightDp)

fun environmentRowTone(index: Int, isHovered: Boolean): EnvironmentRowTone =
    delegateEnvironmentRowTone(index, isHovered)
