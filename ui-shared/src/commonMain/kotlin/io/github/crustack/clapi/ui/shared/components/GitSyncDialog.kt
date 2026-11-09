package io.github.crustack.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.crustack.clapi.core.storage.git.GitChangeKind
import io.github.crustack.clapi.core.storage.git.GitConflictException
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.persistence.WorkspaceRepository
import io.github.crustack.clapi.ui.shared.platform.ioDispatcher
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.LogLevel
import io.github.crustack.clapi.ui.shared.theme.ClapiColors
import io.github.crustack.clapi.ui.shared.theme.CodeFontFamily
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GitSyncDialog(
    state: AppState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val activeCol = state.activeGitCollectionNode
    if (activeCol == null) {
        onDismiss()
        return
    }

    val remoteUrl = activeCol.gitRemoteUrl
    val branch = activeCol.gitBranch
    val repoDir = state.gitRepositoryManager.getRepositoryDirectory(remoteUrl)
    val gitStatus = state.activeGitStatus
    val changes = gitStatus?.changes.orEmpty()
    val groupedChanges = changes.groupBy { it.collectionName }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    var commitMessage by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var conflictWarning by remember { mutableStateOf<String?>(null) }
    var statusSuccessMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activeCol.id, remoteUrl) {
        val refreshed = state.refreshGitStatusForCollection(activeCol)
        if (refreshed?.hasConflicts == true) {
            conflictWarning = "Merge conflict detected. Please resolve conflicts using an external Git client."
        }
    }

    val repoShortName = remember(remoteUrl, branch) {
        val repoName = remoteUrl.removeSuffix(".git").substringAfterLast('/').substringAfterLast(':')
            .ifBlank { "Repository" }
        "$repoName / ${gitStatus?.branch ?: branch}"
    }

    fun handleCommit() {
        if (commitMessage.isBlank() || changes.isEmpty()) return
        isExecuting = true
        errorMessage = null
        statusSuccessMessage = null
        scope.launch {
            try {
                val result = withContext(ioDispatcher) {
                    state.gitRepositoryManager.commit(repoDir, commitMessage.trim())
                }
                val refreshed = state.refreshGitStatusForCollection(activeCol)
                if (result.isSuccess) {
                    commitMessage = ""
                    statusSuccessMessage = "Changes committed locally."
                    state.log("Git commit succeeded on branch ${refreshed?.branch ?: branch}", LogLevel.SUCCESS)
                } else {
                    errorMessage = result.stderr.ifBlank { result.stdout }.ifBlank { "Failed to commit changes." }
                    state.log("Git commit failed: $errorMessage", LogLevel.ERROR)
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to commit changes."
                state.log("Git commit failed: $errorMessage", LogLevel.ERROR)
            } finally {
                isExecuting = false
            }
        }
    }

    fun handlePush() {
        isExecuting = true
        errorMessage = null
        statusSuccessMessage = null
        scope.launch {
            try {
                val result = withContext(ioDispatcher) {
                    state.gitRepositoryManager.push(repoDir, gitStatus?.branch ?: branch)
                }
                state.refreshGitStatusForCollection(activeCol)
                if (result.isSuccess) {
                    statusSuccessMessage = "Pushed to origin/${gitStatus?.branch ?: branch}."
                    state.log("Git push succeeded to origin/${gitStatus?.branch ?: branch}", LogLevel.SUCCESS)
                } else {
                    errorMessage = result.stderr.ifBlank { result.stdout }.ifBlank { "Failed to push to remote." }
                    state.log("Git push failed: $errorMessage", LogLevel.ERROR)
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to push to remote."
                state.log("Git push failed: $errorMessage", LogLevel.ERROR)
            } finally {
                isExecuting = false
            }
        }
    }

    fun handlePull() {
        isExecuting = true
        errorMessage = null
        conflictWarning = null
        statusSuccessMessage = null
        scope.launch {
            try {
                val result = withContext(ioDispatcher) {
                    state.gitRepositoryManager.pull(repoDir, gitStatus?.branch ?: branch)
                }
                val refreshed = state.refreshGitStatusForCollection(activeCol)
                if (refreshed?.hasConflicts == true) {
                    conflictWarning = "Merge conflict detected. Please resolve conflicts using an external Git client."
                    state.log("Merge conflict detected during pull", LogLevel.WARNING)
                } else if (result.isSuccess) {
                    WorkspaceRepository.reloadCollectionsFromGitRepository(state, remoteUrl)
                    statusSuccessMessage = "Pulled latest changes from origin/${gitStatus?.branch ?: branch}."
                    state.log("Git pull succeeded from origin/${gitStatus?.branch ?: branch}", LogLevel.SUCCESS)
                } else {
                    errorMessage = result.stderr.ifBlank { result.stdout }.ifBlank { "Failed to pull from remote." }
                    state.log("Git pull failed: $errorMessage", LogLevel.ERROR)
                }
            } catch (e: GitConflictException) {
                state.refreshGitStatusForCollection(activeCol)
                conflictWarning = "Merge conflict detected. Please resolve conflicts using an external Git client."
                state.log("Merge conflict detected during pull: ${e.message}", LogLevel.WARNING)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to pull from remote."
                state.log("Git pull failed: $errorMessage", LogLevel.ERROR)
            } finally {
                isExecuting = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .widthIn(min = 500.dp, max = 620.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ClapiColors.Surface)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                .padding(20.dp)
                .testTag("git-sync-dialog"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.ForkRight,
                        contentDescription = null,
                        tint = ClapiColors.Primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = "Git Sync - $repoShortName",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = ClapiColors.OnSurface,
                            modifier = Modifier.testTag("git-sync-dialog-title"),
                        )
                        Text(
                            text = "Synchronize collections with remote repository",
                            style = MaterialTheme.typography.bodySmall,
                            color = ClapiColors.OnSurfaceDim,
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp).testTag("git-sync-close-icon"),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = Strings.close,
                        tint = ClapiColors.OnSurfaceDim,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            HorizontalDivider(color = ClapiColors.Border)

            // Conflict / Error / Success banners
            if (conflictWarning != null || gitStatus?.hasConflicts == true) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(ClapiColors.Error.copy(alpha = 0.15f))
                        .padding(12.dp)
                        .testTag("git-sync-conflict-banner"),
                ) {
                    Text(
                        text = conflictWarning ?: "Merge conflict detected. Please resolve conflicts using an external Git client.",
                        color = ClapiColors.Error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(ClapiColors.Error.copy(alpha = 0.15f))
                        .padding(12.dp)
                        .testTag("git-sync-error-banner"),
                ) {
                    Text(
                        text = errorMessage!!,
                        color = ClapiColors.Error,
                        fontSize = 12.sp,
                    )
                }
            }

            if (statusSuccessMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(ClapiColors.Primary.copy(alpha = 0.15f))
                        .padding(12.dp)
                        .testTag("git-sync-success-banner"),
                ) {
                    Text(
                        text = statusSuccessMessage!!,
                        color = ClapiColors.Primary,
                        fontSize = 12.sp,
                    )
                }
            }

            // Uncommitted Changes Section
            Text(
                text = Strings.gitChanges,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = ClapiColors.OnSurface,
            )

            if (changes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(ClapiColors.SurfaceContainer)
                        .padding(16.dp)
                        .testTag("git-sync-clean-state"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = Strings.gitUpToDate,
                        color = ClapiColors.OnSurfaceDim,
                        fontSize = 12.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(ClapiColors.SurfaceContainer)
                        .padding(8.dp)
                        .testTag("git-sync-changes-list"),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    groupedChanges.forEach { (collectionFolder, colChanges) ->
                        val isExpanded = expandedGroups[collectionFolder] ?: true
                        val displayCollectionName = state.collections.firstOrNull {
                            state.gitRepositoryManager.sanitizeCollectionName(it.name) == collectionFolder
                        }?.name ?: collectionFolder

                        item(key = "header_$collectionFolder") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { expandedGroups[collectionFolder] = !isExpanded }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .testTag("git-sync-group-$collectionFolder"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = ClapiColors.OnSurfaceDim,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "$displayCollectionName (${colChanges.size} change${if (colChanges.size == 1) "" else "s"})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ClapiColors.OnSurface,
                                )
                            }
                        }

                        if (isExpanded) {
                            items(colChanges, key = { it.relativePath }) { change ->
                                val fileWithinCollection = change.relativePath
                                    .removePrefix("$collectionFolder/")
                                    .removePrefix("$collectionFolder\\")
                                val kindLabel = when (change.changeKind) {
                                    GitChangeKind.MODIFIED -> "Modified"
                                    GitChangeKind.ADDED, GitChangeKind.UNTRACKED -> "Added"
                                    GitChangeKind.DELETED -> "Deleted"
                                    GitChangeKind.CONFLICTED -> "Conflicted"
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 24.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)
                                        .testTag("git-sync-file-${change.relativePath}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "• $fileWithinCollection",
                                        fontSize = 12.sp,
                                        fontFamily = CodeFontFamily,
                                        color = ClapiColors.OnSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "($kindLabel)",
                                        fontSize = 11.sp,
                                        color = if (change.changeKind == GitChangeKind.CONFLICTED) ClapiColors.Error else ClapiColors.OnSurfaceDim,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Commit Section
            OutlinedTextField(
                value = commitMessage,
                onValueChange = { commitMessage = it },
                label = { Text(Strings.gitCommitMessage) },
                placeholder = { Text("Commit message (required to commit)...") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("git-sync-commit-message"),
            )

            // Actions Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { handlePull() },
                        enabled = !isExecuting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ClapiColors.SurfaceContainer,
                            contentColor = ClapiColors.OnSurface,
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(34.dp).testTag("git-sync-pull-button"),
                    ) {
                        Text(Strings.gitPull, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { handleCommit() },
                        enabled = !isExecuting && commitMessage.isNotBlank() && changes.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ClapiColors.Primary,
                            contentColor = ClapiColors.OnPrimary,
                            disabledContainerColor = ClapiColors.SurfaceContainer,
                            disabledContentColor = ClapiColors.OnSurfaceDim,
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(34.dp).testTag("git-sync-commit-button"),
                    ) {
                        Text(Strings.gitCommit, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { handlePush() },
                        enabled = !isExecuting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ClapiColors.SurfaceContainer,
                            contentColor = ClapiColors.OnSurface,
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(34.dp).testTag("git-sync-push-button"),
                    ) {
                        Text(Strings.gitPush, fontSize = 12.sp)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isExecuting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = ClapiColors.Primary,
                        )
                    }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ClapiColors.SurfaceContainer,
                            contentColor = ClapiColors.OnSurface,
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(34.dp).testTag("git-sync-close-button"),
                    ) {
                        Text(Strings.close, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
