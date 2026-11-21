// Modified by MakeForge (2026)
package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.makeforge.clapi.ui.shared.components.dialogs.settings.SettingsDialog as DelegateSettingsDialog
import io.github.makeforge.clapi.ui.shared.state.AppState

@Composable
fun SettingsDialog(
    state: AppState,
    modifier: Modifier = Modifier,
    useDialogWindow: Boolean = true,
) {
    DelegateSettingsDialog(
        state = state,
        modifier = modifier,
        useDialogWindow = useDialogWindow,
    )
}
