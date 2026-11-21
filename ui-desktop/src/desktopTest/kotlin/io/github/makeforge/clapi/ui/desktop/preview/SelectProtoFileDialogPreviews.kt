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
import io.github.makeforge.clapi.ui.shared.components.SelectProtoFileDialog
import io.github.makeforge.clapi.ui.shared.platform.DiscoveredProtoFile
import io.github.makeforge.clapi.ui.shared.state.AppTheme
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme

private const val width = 580
private const val height = 540

private val previewDiscoveredFiles = listOf(
    DiscoveredProtoFile(
        relativePath = "protobuf/greeter.proto",
        absolutePath = "/workspace/protobuf/greeter.proto",
    ),
    DiscoveredProtoFile(
        relativePath = "protobuf/user_service.proto",
        absolutePath = "/workspace/protobuf/user_service.proto",
    ),
    DiscoveredProtoFile(
        relativePath = "protobuf/common/annotations.proto",
        absolutePath = "/workspace/protobuf/common/annotations.proto",
    ),
)

@Preview(widthDp = width, heightDp = height)
@Composable
fun SelectProtoFileDialogPreviewLight() {
    ClapiTheme(appTheme = AppTheme.LIGHT) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ClapiColors.Background)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            SelectProtoFileDialog(
                currentProtoFilePath = "protobuf/greeter.proto",
                currentProtoFolderPath = "protobuf/",
                isGitSyncEnabled = true,
                initialSyncToGit = true,
                onListProtoFiles = { previewDiscoveredFiles },
                onDismiss = {},
                onSave = {},
                useDialogWindow = false,
            )
        }
    }
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun SelectProtoFileDialogPreviewDark() {
    ClapiTheme(appTheme = AppTheme.DARK) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ClapiColors.Background)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            SelectProtoFileDialog(
                currentProtoFilePath = "protobuf/greeter.proto",
                currentProtoFolderPath = "protobuf/",
                isGitSyncEnabled = true,
                initialSyncToGit = true,
                onListProtoFiles = { previewDiscoveredFiles },
                onDismiss = {},
                onSave = {},
                useDialogWindow = false,
            )
        }
    }
}
