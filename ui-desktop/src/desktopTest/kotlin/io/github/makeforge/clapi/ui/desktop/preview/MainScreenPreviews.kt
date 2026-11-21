package io.github.makeforge.clapi.ui.desktop.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.makeforge.clapi.core.model.storage.GitStorageConfig
import io.github.makeforge.clapi.core.model.storage.StorageExtension
import io.github.makeforge.clapi.core.model.storage.StorageType
import io.github.makeforge.clapi.core.storage.git.CollectionGitStatus
import io.github.makeforge.clapi.core.storage.git.GitChangeKind
import io.github.makeforge.clapi.core.storage.git.GitFileChange
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.AppStateExamples
import io.github.makeforge.clapi.ui.shared.state.AppTheme
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme

// ── HTTP Request Previews ──────────────────────────────────────────────────
private const val width = 1600
private const val height = 800
private const val PREVIEW_GIT_REMOTE_URL = "https://github.com/acme/clapi-collections.git"
private const val PREVIEW_GIT_BRANCH = "main"

private fun AppState.withPreviewGitSyncStatus(theme: AppTheme): AppState = apply {
    settings.theme = theme
    autoRefreshGitStatus = false

    val targetId = activeCollectionNode?.id ?: collections.firstOrNull()?.id
    val idx = collections.indexOfFirst { it.id == targetId }
    if (idx >= 0) {
        val current = collections[idx]
        collections[idx] = current.copy(
            storageExtension = StorageExtension(
                type = StorageType.GIT,
                git = GitStorageConfig(
                    remoteUrl = PREVIEW_GIT_REMOTE_URL,
                    branch = PREVIEW_GIT_BRANCH,
                ),
            ),
        )
        val repoDir = gitRepositoryManager.getRepositoryDirectory(PREVIEW_GIT_REMOTE_URL)
        gitStatusesByRepoDir[repoDir] = CollectionGitStatus(
            branch = PREVIEW_GIT_BRANCH,
            isClean = false,
            hasConflicts = false,
            changes = listOf(
                GitFileChange(
                    relativePath = "${current.name}/Users/Create_User.opencollection.yaml",
                    collectionName = current.name,
                    changeKind = GitChangeKind.MODIFIED,
                ),
            ),
        )
    }
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenHttpPreviewLight() {
    val state = AppStateExamples.http().withPreviewGitSyncStatus(AppTheme.LIGHT)
    ClapiTheme(appTheme = AppTheme.LIGHT) {
        MainScreen(state)
    }
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenHttpPreviewDark() {
    val state = AppStateExamples.http().withPreviewGitSyncStatus(AppTheme.DARK)
    ClapiTheme(appTheme = AppTheme.DARK) {
        MainScreen(state)
    }
}

// ── gRPC Request Previews ──────────────────────────────────────────────────

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenGrpcPreviewLight() {
    val state = AppStateExamples.grpc().withPreviewGitSyncStatus(AppTheme.LIGHT)
    ClapiTheme(appTheme = AppTheme.LIGHT) {
        MainScreen(state)
    }
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenGrpcPreviewDark() {
    val state = AppStateExamples.grpc().withPreviewGitSyncStatus(AppTheme.DARK)
    ClapiTheme(appTheme = AppTheme.DARK) {
        MainScreen(state)
    }
}

// ── WebSocket Request Previews ─────────────────────────────────────────────

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenWebSocketPreviewLight() {
    val state = AppStateExamples.webSocket().withPreviewGitSyncStatus(AppTheme.LIGHT)
    ClapiTheme(appTheme = AppTheme.LIGHT) {
        MainScreen(state)
    }
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenWebSocketPreviewDark() {
    val state = AppStateExamples.webSocket().withPreviewGitSyncStatus(AppTheme.DARK)
    ClapiTheme(appTheme = AppTheme.DARK) {
        MainScreen(state)
    }
}

// ── GraphQL Request Previews ───────────────────────────────────────────────

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenGraphQlPreviewLight() {
    val state = AppStateExamples.graphQl().withPreviewGitSyncStatus(AppTheme.LIGHT)
    ClapiTheme(appTheme = AppTheme.LIGHT) {
        MainScreen(state)
    }
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenGraphQlPreviewDark() {
    val state = AppStateExamples.graphQl().withPreviewGitSyncStatus(AppTheme.DARK)
    ClapiTheme(appTheme = AppTheme.DARK) {
        MainScreen(state)
    }
}

// ── MCP Request Previews ───────────────────────────────────────────────────

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenMcpPreviewLight() {
    val state = AppStateExamples.mcp().withPreviewGitSyncStatus(AppTheme.LIGHT)
    ClapiTheme(appTheme = AppTheme.LIGHT) {
        MainScreen(state)
    }
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenMcpPreviewDark() {
    val state = AppStateExamples.mcp().withPreviewGitSyncStatus(AppTheme.DARK)
    ClapiTheme(appTheme = AppTheme.DARK) {
        MainScreen(state)
    }
}

// ── RabbitMQ Request Previews ──────────────────────────────────────────────

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenRabbitMqPublishPreviewLight() {
    val state = AppStateExamples.rabbitMqPublish().withPreviewGitSyncStatus(AppTheme.LIGHT)
    ClapiTheme(appTheme = AppTheme.LIGHT) {
        MainScreen(state)
    }
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenRabbitMqPublishPreviewDark() {
    val state = AppStateExamples.rabbitMqPublish().withPreviewGitSyncStatus(AppTheme.DARK)
    ClapiTheme(appTheme = AppTheme.DARK) {
        MainScreen(state)
    }
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenRabbitMqSubscribePreviewLight() {
    val state = AppStateExamples.rabbitMqSubscribe().withPreviewGitSyncStatus(AppTheme.LIGHT)
    ClapiTheme(appTheme = AppTheme.LIGHT) {
        MainScreen(state)
    }
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenRabbitMqSubscribePreviewDark() {
    val state = AppStateExamples.rabbitMqSubscribe().withPreviewGitSyncStatus(AppTheme.DARK)
    ClapiTheme(appTheme = AppTheme.DARK) {
        MainScreen(state)
    }
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenRabbitMqRpcPreviewLight() {
    val state = AppStateExamples.rabbitMqRpc().withPreviewGitSyncStatus(AppTheme.LIGHT)
    ClapiTheme(appTheme = AppTheme.LIGHT) {
        MainScreen(state)
    }
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenRabbitMqRpcPreviewDark() {
    val state = AppStateExamples.rabbitMqRpc().withPreviewGitSyncStatus(AppTheme.DARK)
    ClapiTheme(appTheme = AppTheme.DARK) {
        MainScreen(state)
    }
}

// ── Default Previews (Aliased to HTTP for backward compatibility) ─────────

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenPreviewLight() {
    MainScreenHttpPreviewLight()
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenPreviewDark() {
    MainScreenHttpPreviewDark()
}
