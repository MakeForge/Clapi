package io.github.makeforge.clapi.ui.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.core.model.storage.StorageType
import io.github.makeforge.clapi.core.storage.git.DesktopGitCliExecutor
import io.github.makeforge.clapi.core.storage.git.GitRepositoryManager
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.state.AppState
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollectionStorageTabUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun storage_tab_displays_local_and_git_options_and_fields() {
        val state = AppState(withDemoData = true)
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Open collection editor for c1
        composeRule.onNodeWithTag("collection-node-c1").performClick()
        composeRule.waitForIdle()

        assertEquals(RequestKind.COLLECTION, state.activeTab?.kind)

        // Click Storage tab
        composeRule.onNodeWithTag("collection-tab-storage").assertIsDisplayed()
        composeRule.onNodeWithTag("collection-tab-storage").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("collection-storage-panel").assertIsDisplayed()
        composeRule.onNodeWithTag("storage-radio-local").assertIsDisplayed()
        composeRule.onNodeWithTag("storage-radio-git").assertIsDisplayed()
        composeRule.onNodeWithTag("storage-local-description").assertIsDisplayed()
        composeRule.onNodeWithTag("storage-git-remote-url").assertDoesNotExist()

        // Select Git radio option
        composeRule.onNodeWithTag("storage-radio-git").performClick()
        composeRule.waitForIdle()

        assertEquals(StorageType.GIT, state.activeTab?.storageType)
        composeRule.onNodeWithTag("storage-local-description").assertDoesNotExist()
        composeRule.onNodeWithTag("storage-git-remote-url").assertIsDisplayed()
        composeRule.onNodeWithTag("storage-git-branch").assertIsDisplayed()
        composeRule.onNodeWithTag("storage-git-credentials-note").assertIsDisplayed()

        // Save button is disabled when remoteUrl is blank
        composeRule.onNodeWithTag("storage-save-button").assertIsNotEnabled()

        // Enter remoteUrl -> Save button becomes enabled
        composeRule.onNodeWithTag("storage-git-remote-url").performTextInput("https://github.com/org/repo.git")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("storage-save-button").assertIsEnabled()

        // Clear branch -> Save button becomes disabled
        composeRule.onNodeWithTag("storage-git-branch").performTextClearance()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("storage-save-button").assertIsNotEnabled()
    }

    @Test
    fun storage_tab_clones_git_repository_and_exports_collection_yaml() {
        val tempDir = File.createTempFile("clapi_storage_ui_test_", "").apply {
            delete()
            mkdirs()
        }
        try {
            val remoteRepoDir = File(tempDir, "remote.git").apply { mkdirs() }
            val storageBaseDir = File(tempDir, "storage").apply { mkdirs() }
            val executor = DesktopGitCliExecutor()

            runBlocking {
                executor.execute(remoteRepoDir.absolutePath, listOf("init", "--bare", "-b", "main"))
            }

            val state = AppState(withDemoData = true)
            state.gitRepositoryManager = GitRepositoryManager(
                executor = executor,
                baseStorageDir = storageBaseDir.absolutePath,
            )

            composeRule.setContent { MainScreen(state) }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("collection-node-c1").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("collection-tab-storage").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("storage-radio-git").performClick()
            composeRule.waitForIdle()

            val remoteUrl = remoteRepoDir.absolutePath
            composeRule.onNodeWithTag("storage-git-remote-url").performTextInput(remoteUrl)
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("storage-save-button").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                val col = state.collections.first { it.id == "c1" }
                col.storageType == StorageType.GIT && !state.activeTab!!.isSavingStorage
            }
            composeRule.waitForIdle()

            val colNode = state.collections.first { it.id == "c1" }
            assertEquals(StorageType.GIT, colNode.storageType)
            assertEquals(remoteUrl, colNode.gitRemoteUrl)
            assertEquals("main", colNode.gitBranch)

            val colDir = state.gitRepositoryManager.getCollectionDirectory(remoteUrl, colNode.name)
            val yamlFile = File(colDir, "collection.opencollection.yaml")
            assertTrue(yamlFile.exists(), "Expected collection.opencollection.yaml to exist at ${yamlFile.absolutePath}")
            val yamlContent = yamlFile.readText()
            assertTrue(yamlContent.contains("type: git") || yamlContent.contains("type: \"git\""))
            assertTrue(yamlContent.contains(remoteUrl))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun storage_tab_displays_error_banner_when_clone_fails() {
        val tempDir = File.createTempFile("clapi_storage_err_test_", "").apply {
            delete()
            mkdirs()
        }
        try {
            val state = AppState(withDemoData = true)
            state.gitRepositoryManager = GitRepositoryManager(
                executor = DesktopGitCliExecutor(),
                baseStorageDir = tempDir.absolutePath,
            )

            composeRule.setContent { MainScreen(state) }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("collection-node-c1").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("collection-tab-storage").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("storage-radio-git").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("storage-git-remote-url").performTextInput("/nonexistent/invalid/repo/path/12345.git")
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("storage-save-button").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                state.activeTab?.storageError != null
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("storage-error-banner").assertIsDisplayed()
            assertFalse(state.activeTab!!.storageError.isNullOrBlank())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
