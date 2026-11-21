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
import io.github.makeforge.clapi.core.model.storage.GitStorageConfig
import io.github.makeforge.clapi.core.model.storage.StorageExtension
import io.github.makeforge.clapi.core.model.storage.StorageType
import io.github.makeforge.clapi.core.storage.git.CollectionGitStatus
import io.github.makeforge.clapi.core.storage.git.GitChangeKind
import io.github.makeforge.clapi.core.storage.git.GitFileChange
import io.github.makeforge.clapi.ui.shared.components.GitSyncDialog
import io.github.makeforge.clapi.ui.shared.components.sidebar.SidebarGitStatusIndicator
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.AppStateExamples
import io.github.makeforge.clapi.ui.shared.state.AppTheme
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme

private const val GIT_PREVIEW_REMOTE_URL = "https://github.com/acme/clapi-collections.git"
private const val GIT_PREVIEW_BRANCH = "main"

private fun createGitSyncPreviewState(theme: AppTheme): AppState = AppStateExamples.http().apply {
    settings.theme = theme
    autoRefreshGitStatus = false

    val idx = collections.indexOfFirst { it.id == "c1" }
    if (idx >= 0) {
        val current = collections[idx]
        collections[idx] = current.copy(
            storageExtension = StorageExtension(
                type = StorageType.GIT,
                git = GitStorageConfig(
                    remoteUrl = GIT_PREVIEW_REMOTE_URL,
                    branch = GIT_PREVIEW_BRANCH,
                ),
            ),
        )
        val repoDir = gitRepositoryManager.getRepositoryDirectory(GIT_PREVIEW_REMOTE_URL)
        gitStatusesByRepoDir[repoDir] = CollectionGitStatus(
            branch = GIT_PREVIEW_BRANCH,
            isClean = false,
            hasConflicts = false,
            changes = listOf(
                GitFileChange(
                    relativePath = "Users_API/Users/Create_User.opencollection.yaml",
                    collectionName = "Users_API",
                    changeKind = GitChangeKind.MODIFIED,
                ),
                GitFileChange(
                    relativePath = "Users_API/Users/Get_User_Profile.opencollection.yaml",
                    collectionName = "Users_API",
                    changeKind = GitChangeKind.ADDED,
                ),
                GitFileChange(
                    relativePath = "Users_API/protos/greeter.proto",
                    collectionName = "Users_API",
                    changeKind = GitChangeKind.MODIFIED,
                ),
            ),
        )
    }
}

// ── Git Sync Dialog Previews ───────────────────────────────────────────────

@Preview(widthDp = 620, heightDp = 520)
@Composable
fun GitSyncDialogPreviewLight() {
    val state = createGitSyncPreviewState(AppTheme.LIGHT)
    ClapiTheme(appTheme = AppTheme.LIGHT) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ClapiColors.Background)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            GitSyncDialog(
                state = state,
                onDismiss = {},
                useDialogWindow = false,
            )
        }
    }
}

@Preview(widthDp = 620, heightDp = 520)
@Composable
fun GitSyncDialogPreviewDark() {
    val state = createGitSyncPreviewState(AppTheme.DARK)
    ClapiTheme(appTheme = AppTheme.DARK) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ClapiColors.Background)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            GitSyncDialog(
                state = state,
                onDismiss = {},
                useDialogWindow = false,
            )
        }
    }
}

// ── Sidebar Git Status Indicator Previews (Modified State) ─────────────────

@Preview(widthDp = 260, heightDp = 36)
@Composable
fun SidebarGitStatusIndicatorPreviewLight() {
    val state = createGitSyncPreviewState(AppTheme.LIGHT)
    ClapiTheme(appTheme = AppTheme.LIGHT) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ClapiColors.Surface),
            contentAlignment = Alignment.CenterStart,
        ) {
            SidebarGitStatusIndicator(state = state)
        }
    }
}

@Preview(widthDp = 260, heightDp = 36)
@Composable
fun SidebarGitStatusIndicatorPreviewDark() {
    val state = createGitSyncPreviewState(AppTheme.DARK)
    ClapiTheme(appTheme = AppTheme.DARK) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ClapiColors.Surface),
            contentAlignment = Alignment.CenterStart,
        ) {
            SidebarGitStatusIndicator(state = state)
        }
    }
}
