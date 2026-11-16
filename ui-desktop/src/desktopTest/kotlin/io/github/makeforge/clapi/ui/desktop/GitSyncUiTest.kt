package io.github.makeforge.clapi.ui.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.makeforge.clapi.core.model.storage.GitStorageConfig
import io.github.makeforge.clapi.core.model.storage.StorageExtension
import io.github.makeforge.clapi.core.model.storage.StorageType
import io.github.makeforge.clapi.core.storage.git.DesktopGitCliExecutor
import io.github.makeforge.clapi.core.storage.git.GitRepositoryManager
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.persistence.WorkspaceRepository
import io.github.makeforge.clapi.ui.shared.state.AppState
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitSyncUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sidebar_git_indicator_shows_branch_and_dirty_dot_and_opens_sync_dialog() {
        val tempDir = File.createTempFile("clapi_git_sync_ui_", "").apply {
            delete()
            mkdirs()
        }
        try {
            val remoteRepoDir = File(tempDir, "remote.git").apply { mkdirs() }
            val storageBaseDir = File(tempDir, "storage").apply { mkdirs() }
            val executor = DesktopGitCliExecutor()
            val manager = GitRepositoryManager(executor, storageBaseDir.absolutePath)

            val remoteUrl = remoteRepoDir.absolutePath
            runBlocking {
                executor.execute(remoteRepoDir.absolutePath, listOf("init", "--bare", "-b", "main"))
                val clone = manager.ensureClonedOrCheckedOut(remoteUrl, "main")
                executor.execute(clone.workingDirectory, listOf("config", "user.name", "Test User"))
                executor.execute(clone.workingDirectory, listOf("config", "user.email", "test@example.com"))
                executor.execute(clone.workingDirectory, listOf("config", "commit.gpgsign", "false"))
            }

            val state = AppState(withDemoData = true)
            state.gitRepositoryManager = manager

            // Configure collection c1 as Git-synced
            val idx = state.collections.indexOfFirst { it.id == "c1" }
            val c1 = state.collections[idx].copy(
                storageExtension = StorageExtension(
                    type = StorageType.GIT,
                    git = GitStorageConfig(remoteUrl = remoteUrl, branch = "main"),
                ),
            )
            state.collections[idx] = c1
            state.selectedCollectionId = "c1"

            // Export collection YAML to make repository dirty
            WorkspaceRepository.exportCollectionToGitRepository(state, c1)
            runBlocking {
                state.refreshGitStatusForCollection(c1)
            }

            composeRule.setContent { MainScreen(state) }
            composeRule.waitForIdle()

            // Verify sidebar Git indicator and dirty dot
            composeRule.onNodeWithTag("sidebar-git-indicator").assertIsDisplayed()
            composeRule.onNodeWithTag("sidebar-git-branch-label", useUnmergedTree = true).assertTextEquals("main")
            composeRule.onNodeWithTag("sidebar-git-dirty-dot", useUnmergedTree = true).assertIsDisplayed()

            // Click indicator to open Git Sync Dialog
            composeRule.onNodeWithTag("sidebar-git-indicator").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("git-sync-dialog").assertIsDisplayed()
            composeRule.onNodeWithTag("git-sync-changes-list").assertIsDisplayed()

            // Commit button is disabled when commit message is blank
            composeRule.onNodeWithTag("git-sync-commit-button").assertIsNotEnabled()

            // Type commit message -> Commit button becomes enabled
            composeRule.onNodeWithTag("git-sync-commit-message").performTextInput("Initial collection sync")
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("git-sync-commit-button").assertIsEnabled()

            // Click Commit
            composeRule.onNodeWithTag("git-sync-commit-button").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                state.activeGitStatus?.isClean == true
            }
            composeRule.waitForIdle()

            // After commit, working copy is clean and dirty dot disappears
            composeRule.onNodeWithTag("git-sync-clean-state").assertIsDisplayed()
            composeRule.onNodeWithTag("sidebar-git-dirty-dot", useUnmergedTree = true).assertDoesNotExist()

            // Click Push
            composeRule.onNodeWithTag("git-sync-push-button").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                runBlocking {
                    val logRes = executor.execute(remoteRepoDir.absolutePath, listOf("log", "--oneline", "-n", "1"))
                    logRes.isSuccess && logRes.stdout.contains("Initial collection sync")
                }
            }
            composeRule.waitForIdle()

            // Close dialog
            composeRule.onNodeWithTag("git-sync-close-button").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("git-sync-dialog").assertDoesNotExist()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun git_sync_dialog_detects_and_displays_pull_conflict_warning() {
        val tempDir = File.createTempFile("clapi_git_conflict_ui_", "").apply {
            delete()
            mkdirs()
        }
        try {
            val remoteRepoDir = File(tempDir, "remote.git").apply { mkdirs() }
            val otherPeerDir = File(tempDir, "peer").apply { mkdirs() }
            val storageBaseDir = File(tempDir, "storage").apply { mkdirs() }
            val executor = DesktopGitCliExecutor()
            val manager = GitRepositoryManager(executor, storageBaseDir.absolutePath)
            val remoteUrl = remoteRepoDir.absolutePath

            runBlocking {
                executor.execute(remoteRepoDir.absolutePath, listOf("init", "--bare", "-b", "main"))
                val clone = manager.ensureClonedOrCheckedOut(remoteUrl, "main")
                executor.execute(clone.workingDirectory, listOf("config", "user.name", "Clapi User"))
                executor.execute(clone.workingDirectory, listOf("config", "user.email", "clapi@example.com"))
                executor.execute(clone.workingDirectory, listOf("config", "commit.gpgsign", "false"))
                executor.execute(clone.workingDirectory, listOf("config", "pull.rebase", "false"))

                // Initial commit & push
                val colDir = File(clone.workingDirectory, "Users_API").apply { mkdirs() }
                File(colDir, "collection.opencollection.yaml").writeText("opencollection: \"1.0.0\"\ninfo:\n  name: Users API\n  version: \"1.0.0\"\n")
                manager.commit(clone.workingDirectory, "Base commit")
                manager.push(clone.workingDirectory, "main")

                // Clone from another peer and push conflicting change
                executor.execute(tempDir.absolutePath, listOf("clone", "--branch", "main", remoteUrl, otherPeerDir.absolutePath))
                executor.execute(otherPeerDir.absolutePath, listOf("config", "user.name", "Peer User"))
                executor.execute(otherPeerDir.absolutePath, listOf("config", "user.email", "peer@example.com"))
                executor.execute(otherPeerDir.absolutePath, listOf("config", "commit.gpgsign", "false"))
                File(otherPeerDir, "Users_API/collection.opencollection.yaml").writeText("opencollection: \"1.0.0\"\ninfo:\n  name: Remote Conflicting Name\n  version: \"2.0.0\"\n")
                executor.execute(otherPeerDir.absolutePath, listOf("add", "-A"))
                executor.execute(otherPeerDir.absolutePath, listOf("commit", "-m", "Peer conflicting edit"))
                executor.execute(otherPeerDir.absolutePath, listOf("push", "origin", "main"))

                // Make local conflicting commit in Clapi clone
                File(colDir, "collection.opencollection.yaml").writeText("opencollection: \"1.0.0\"\ninfo:\n  name: Local Conflicting Name\n  version: \"3.0.0\"\n")
                manager.commit(clone.workingDirectory, "Local conflicting edit")
            }

            val state = AppState(withDemoData = true)
            state.gitRepositoryManager = manager

            val idx = state.collections.indexOfFirst { it.id == "c1" }
            val c1 = state.collections[idx].copy(
                name = "Users API",
                storageExtension = StorageExtension(
                    type = StorageType.GIT,
                    git = GitStorageConfig(remoteUrl = remoteUrl, branch = "main"),
                ),
            )
            state.collections[idx] = c1
            state.selectedCollectionId = "c1"

            runBlocking { state.refreshGitStatusForCollection(c1) }

            composeRule.setContent { MainScreen(state) }
            composeRule.waitForIdle()

            // Open Git Sync Dialog
            composeRule.onNodeWithTag("sidebar-git-indicator").performClick()
            composeRule.waitForIdle()

            // Click Pull -> should trigger merge conflict and show conflict warning banner
            composeRule.onNodeWithTag("git-sync-pull-button").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                state.activeGitStatus?.hasConflicts == true
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("git-sync-conflict-banner").assertIsDisplayed()
            assertTrue(state.activeGitStatus!!.hasConflicts)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
