package io.github.makeforge.clapi.ui.desktop.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.makeforge.clapi.ui.shared.components.EnvironmentEditDialog
import io.github.makeforge.clapi.ui.shared.state.AppStateExamples
import io.github.makeforge.clapi.ui.shared.state.AppTheme
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme

private const val width = 1200
private const val height = 800

@Preview(widthDp = width, heightDp = height)
@Composable
fun EnvironmentEditDialogPreviewDark() {
    val state = AppStateExamples.http().apply {
        settings.theme = AppTheme.DARK
        openEnvEdit(0)
    }
    ClapiTheme(appTheme = AppTheme.DARK) {
        EnvironmentEditDialog(state, useDialogWindow = false)
    }
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun EnvironmentEditDialogPreviewLight() {
    val state = AppStateExamples.http().apply {
        settings.theme = AppTheme.LIGHT
        openEnvEdit(0)
    }
    ClapiTheme(appTheme = AppTheme.LIGHT) {
        EnvironmentEditDialog(state, useDialogWindow = false)
    }
}
