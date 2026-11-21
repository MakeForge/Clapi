package io.github.makeforge.clapi.ui.desktop.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.AppStateExamples
import io.github.makeforge.clapi.ui.shared.state.AppTheme
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme

private const val width = 1600
private const val height = 800

private fun createFolderPreviewState(theme: AppTheme): AppState = AppStateExamples.http().apply {
    settings.theme = theme
    openFolder("f-users")
    val folderTab = activeTab
    if (folderTab != null) {
        folderTab.headers.clear()
        folderTab.headers.add(MutableKeyValue("Accept", "application/json"))
        folderTab.headers.add(MutableKeyValue("X-Api-Version", "v1"))
        folderTab.headers.add(MutableKeyValue("X-Client-Platform", "desktop"))
        folderTab.authType = AuthType.BEARER
        folderTab.authToken = "{{authToken}}"
    }
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenFolderPreviewLight() {
    val state = createFolderPreviewState(AppTheme.LIGHT)
    ClapiTheme(appTheme = AppTheme.LIGHT) {
        MainScreen(state)
    }
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenFolderPreviewDark() {
    val state = createFolderPreviewState(AppTheme.DARK)
    ClapiTheme(appTheme = AppTheme.DARK) {
        MainScreen(state)
    }
}
