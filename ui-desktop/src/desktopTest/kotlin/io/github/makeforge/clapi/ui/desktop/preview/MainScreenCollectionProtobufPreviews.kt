package io.github.makeforge.clapi.ui.desktop.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.makeforge.clapi.core.model.storage.StorageType
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.AppStateExamples
import io.github.makeforge.clapi.ui.shared.state.AppTheme
import io.github.makeforge.clapi.ui.shared.state.CollectionEditorTab
import io.github.makeforge.clapi.ui.shared.state.ProtoFileItemState
import io.github.makeforge.clapi.ui.shared.state.ProtoImportPathState
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme

private const val width = 1600
private const val height = 800

private fun createCollectionProtobufPreviewState(theme: AppTheme): AppState = AppStateExamples.grpc().apply {
    settings.theme = theme
    openCollection("c1")
    val tab = activeTab
    if (tab != null) {
        tab.selectedCollectionEditorTab = CollectionEditorTab.PROTOBUF
        tab.storageType = StorageType.GIT
        tab.collectionProtoFiles.clear()
        tab.collectionProtoFiles.addAll(
            listOf(
                ProtoFileItemState(
                    path = "./protobuf/collection/greeter.proto",
                    syncToGit = true,
                ),
                ProtoFileItemState(
                    path = "./protobuf/collection/user_service.proto",
                    syncToGit = true,
                ),
            ),
        )
        tab.collectionImportPaths.clear()
        tab.collectionImportPaths.addAll(
            listOf(
                ProtoImportPathState(
                    path = "./protobuf/collection/imports/google/api",
                    enabled = true,
                    syncToGit = true,
                ),
            ),
        )
    }
    collectionProtoFiles.clear()
    collectionProtoFiles.addAll(tab?.collectionProtoFiles.orEmpty())
    collectionImportPaths.clear()
    collectionImportPaths.addAll(tab?.collectionImportPaths.orEmpty())
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenCollectionProtobufPreviewLight() {
    val state = createCollectionProtobufPreviewState(AppTheme.LIGHT)
    ClapiTheme(appTheme = AppTheme.LIGHT) {
        MainScreen(state)
    }
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenCollectionProtobufPreviewDark() {
    val state = createCollectionProtobufPreviewState(AppTheme.DARK)
    ClapiTheme(appTheme = AppTheme.DARK) {
        MainScreen(state)
    }
}
