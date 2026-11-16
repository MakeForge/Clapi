package io.github.makeforge.clapi.ui.shared.components.collectioneditor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.core.model.storage.StorageType
import io.github.makeforge.clapi.core.storage.git.GitErrorTranslator
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.persistence.TabsRepository
import io.github.makeforge.clapi.ui.shared.persistence.WorkspaceRepository
import io.github.makeforge.clapi.ui.shared.platform.PlatformLazyVerticalScrollbar
import io.github.makeforge.clapi.ui.shared.platform.ioDispatcher
import io.github.makeforge.clapi.ui.shared.shortcuts.onSaveHotkey
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.LogLevel
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CollectionStorageSettings(
    tab: RequestTabState,
    state: AppState,
    onFieldChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var savedBannerMessage by remember(tab.id) { mutableStateOf<String?>(null) }

    val canSaveGit = tab.storageType == StorageType.LOCAL ||
        (tab.gitRemoteUrl.isNotBlank() && tab.gitBranch.isNotBlank())

    fun handleSaveStorage() {
        tab.storageError = null
        savedBannerMessage = null
        if (tab.storageType == StorageType.GIT) {
            if (tab.gitRemoteUrl.isBlank()) {
                tab.storageError = "Git remoteUrl cannot be blank"
                return
            }
            if (tab.gitBranch.isBlank()) {
                tab.storageError = "Git branch cannot be blank"
                return
            }
        }
        tab.isSavingStorage = true
        scope.launch {
            try {
                if (tab.storageType == StorageType.GIT) {
                    val remoteUrl = tab.gitRemoteUrl.trim()
                    val branch = tab.gitBranch.trim().ifBlank { "main" }
                    withContext(ioDispatcher) {
                        state.gitRepositoryManager.ensureClonedOrCheckedOut(remoteUrl, branch)
                    }
                }
                state.syncTabToCollectionNode(tab)
                val colNode = state.collections.firstOrNull {
                    it.id == tab.id || it.id == tab.collectionId || "collection_${it.id}" == tab.id
                }
                if (tab.storageType == StorageType.GIT && colNode != null) {
                    withContext(ioDispatcher) {
                        WorkspaceRepository.exportCollectionToGitRepository(state, colNode)
                    }
                    state.refreshGitStatusForCollection(colNode)
                }
                tab.markSaved()
                withContext(ioDispatcher) {
                    TabsRepository.save(state)
                    WorkspaceRepository.save(state)
                }
                savedBannerMessage = "Storage settings saved."
                state.log("Storage settings saved for '${tab.name}'", LogLevel.SUCCESS)
            } catch (e: Exception) {
                tab.storageError = GitErrorTranslator.translate(e)
                state.log("Failed to configure Git storage: ${tab.storageError}", LogLevel.ERROR)
            } finally {
                tab.isSavingStorage = false
            }
        }
    }

    val listState = rememberLazyListState()
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 12.dp, start = 24.dp, top = 24.dp, bottom = 24.dp)
                .onSaveHotkey { if (canSaveGit && !tab.isSavingStorage) handleSaveStorage() }
                .testTag("collection-storage-panel"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = Strings.storageType,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ClapiColors.OnSurface,
                )
                Text(
                    text = "Configure how this collection is stored and synchronized.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ClapiColors.OnSurfaceDim,
                )
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                tab.storageType = StorageType.LOCAL
                                tab.storageError = null
                                savedBannerMessage = null
                                onFieldChange()
                            }
                            .padding(end = 8.dp)
                            .testTag("storage-radio-local"),
                    ) {
                        RadioButton(
                            selected = tab.storageType == StorageType.LOCAL,
                            onClick = {
                                tab.storageType = StorageType.LOCAL
                                tab.storageError = null
                                savedBannerMessage = null
                                onFieldChange()
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = ClapiColors.Primary,
                                unselectedColor = ClapiColors.OnSurfaceDim,
                            ),
                        )
                        Text(
                            text = Strings.storageLocal,
                            color = ClapiColors.OnSurface,
                            fontSize = 14.sp,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                tab.storageType = StorageType.GIT
                                tab.storageError = null
                                savedBannerMessage = null
                                onFieldChange()
                            }
                            .padding(end = 8.dp)
                            .testTag("storage-radio-git"),
                    ) {
                        RadioButton(
                            selected = tab.storageType == StorageType.GIT,
                            onClick = {
                                tab.storageType = StorageType.GIT
                                tab.storageError = null
                                savedBannerMessage = null
                                onFieldChange()
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = ClapiColors.Primary,
                                unselectedColor = ClapiColors.OnSurfaceDim,
                            ),
                        )
                        Text(
                            text = Strings.storageGit,
                            color = ClapiColors.OnSurface,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            if (tab.storageType == StorageType.LOCAL) {
                item {
                    Text(
                        text = Strings.storageLocalDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = ClapiColors.OnSurfaceDim,
                        modifier = Modifier.testTag("storage-local-description"),
                    )
                }
            } else {
                item {
                    OutlinedTextField(
                        value = tab.gitRemoteUrl,
                        onValueChange = {
                            tab.gitRemoteUrl = it
                            tab.storageError = null
                            savedBannerMessage = null
                            onFieldChange()
                        },
                        label = { Text(Strings.storageGitRemoteUrl) },
                        placeholder = { Text("git@github.com:org/repo.git or https://github.com/org/repo.git") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("storage-git-remote-url"),
                    )
                }

                item {
                    OutlinedTextField(
                        value = tab.gitBranch,
                        onValueChange = {
                            tab.gitBranch = it
                            tab.storageError = null
                            savedBannerMessage = null
                            onFieldChange()
                        },
                        label = { Text(Strings.storageGitBranch) },
                        placeholder = { Text("main") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("storage-git-branch"),
                    )
                }

                item {
                    Text(
                        text = Strings.storageGitCredentialsNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = ClapiColors.OnSurfaceDim,
                        modifier = Modifier.testTag("storage-git-credentials-note"),
                    )
                }
            }

            if (tab.storageError != null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(ClapiColors.Error.copy(alpha = 0.15f))
                            .padding(12.dp)
                            .testTag("storage-error-banner"),
                    ) {
                        Text(
                            text = tab.storageError!!,
                            color = ClapiColors.Error,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            if (savedBannerMessage != null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(ClapiColors.Primary.copy(alpha = 0.15f))
                            .padding(12.dp)
                            .testTag("storage-saved-banner"),
                    ) {
                        Text(
                            text = savedBannerMessage!!,
                            color = ClapiColors.Primary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { handleSaveStorage() },
                        enabled = !tab.isSavingStorage && canSaveGit,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ClapiColors.Primary,
                            contentColor = ClapiColors.OnPrimary,
                            disabledContainerColor = ClapiColors.SurfaceContainer,
                            disabledContentColor = ClapiColors.OnSurfaceDim,
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.testTag("storage-save-button"),
                    ) {
                        if (tab.isSavingStorage) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = ClapiColors.OnPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(Strings.storageSaveSettings, fontSize = 13.sp)
                    }
                }
            }
        }
        PlatformLazyVerticalScrollbar(
            listState = listState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            testTag = "collection-storage-scrollbar",
        )
    }
}
