package io.github.crustack.clapi.ui.shared.components.sidebar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.crustack.clapi.ui.shared.state.AppState

@Composable
internal fun SidebarFooter(
    state: AppState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        SidebarGitStatusIndicator(state = state)
    }
}
