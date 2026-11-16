package io.github.crustack.clapi.ui.shared.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.crustack.clapi.ui.shared.components.dialogs.settings.SettingsDialog as DelegateSettingsDialog
import io.github.crustack.clapi.ui.shared.state.AppState

@Composable
fun SettingsDialog(
    state: AppState,
    modifier: Modifier = Modifier,
) {
    DelegateSettingsDialog(
        state = state,
        modifier = modifier,
    )
}
