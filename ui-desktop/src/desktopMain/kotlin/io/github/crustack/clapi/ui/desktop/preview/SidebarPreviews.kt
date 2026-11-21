package io.github.crustack.clapi.ui.desktop.preview


import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import io.github.crustack.clapi.ui.shared.components.RequestEditor
import io.github.crustack.clapi.ui.shared.components.Sidebar
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import io.github.crustack.clapi.ui.shared.theme.ClapiTheme

@Preview
@Composable
fun SidebarPreview(){
    ClapiTheme(){
        Sidebar( state = AppState( withDemoData = true))
    }
}