package io.github.makeforge.clapi.ui.desktop.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.makeforge.clapi.ui.shared.components.ImportSpecificationDialog
import io.github.makeforge.clapi.ui.shared.state.AppStateExamples
import io.github.makeforge.clapi.ui.shared.state.AppTheme
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme

private const val width = 640
private const val height = 560

@Preview(widthDp = width, heightDp = height)
@Composable
fun ImportSpecificationDialogPreviewLight() {
    val state = AppStateExamples.http().apply {
        settings.theme = AppTheme.LIGHT
    }
    ClapiTheme(appTheme = AppTheme.LIGHT) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ClapiColors.Background)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            ImportSpecificationDialog(
                state = state,
                onDismiss = {},
                useDialogWindow = false,
            )
        }
    }
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun ImportSpecificationDialogPreviewDark() {
    val state = AppStateExamples.http().apply {
        settings.theme = AppTheme.DARK
    }
    ClapiTheme(appTheme = AppTheme.DARK) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ClapiColors.Background)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            ImportSpecificationDialog(
                state = state,
                onDismiss = {},
                useDialogWindow = false,
            )
        }
    }
}
