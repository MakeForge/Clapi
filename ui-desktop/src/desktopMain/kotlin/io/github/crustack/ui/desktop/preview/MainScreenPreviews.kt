package io.github.crustack.ui.desktop.preview

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import io.github.crustack.ui.shared.MainScreen
import io.github.crustack.ui.shared.state.AppState
import io.github.crustack.ui.shared.state.AppTheme
import io.github.crustack.ui.shared.theme.ClapiTheme

@Preview
@Composable
fun MainScreenLightPreview() {
    ClapiTheme(appTheme = AppTheme.LIGHT) {
        MainScreen(AppState(withDemoData = true))
    }
}

@Preview
@Composable
fun MainScreenDarkPreview() {
    ClapiTheme(appTheme = AppTheme.DARK) {
        MainScreen(AppState(withDemoData = true))
    }
}