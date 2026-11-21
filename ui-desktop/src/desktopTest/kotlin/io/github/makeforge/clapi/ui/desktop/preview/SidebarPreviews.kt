package io.github.makeforge.clapi.ui.desktop.preview

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import io.github.makeforge.clapi.ui.shared.components.sidebar.Sidebar
import io.github.makeforge.clapi.ui.shared.state.AppStateExamples
import io.github.makeforge.clapi.ui.shared.state.AppTheme
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme

@Preview
@Composable
fun SidebarPreview() {
    ClapiTheme(appTheme = AppTheme.DARK) {
        Sidebar(state = AppStateExamples.default())
    }
}
