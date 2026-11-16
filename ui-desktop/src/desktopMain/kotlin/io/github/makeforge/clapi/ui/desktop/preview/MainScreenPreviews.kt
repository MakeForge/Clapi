package io.github.makeforge.clapi.ui.desktop.preview

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.AppTheme
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme

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