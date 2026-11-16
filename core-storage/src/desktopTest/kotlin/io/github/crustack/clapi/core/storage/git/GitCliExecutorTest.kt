package io.github.crustack.clapi.core.storage.git

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitCliExecutorTest {

    private val executor = DesktopGitCliExecutor()

    @Test
    fun git_version_check_succeeds() = runBlocking {
        val result = executor.execute(
            workingDir = null,
            args = listOf("--version"),
        )
        assertTrue(result.isSuccess, "git --version should exit with code 0")
        assertTrue(result.stdout.contains("git version"), "Output should contain 'git version'")
    }

    @Test
    fun git_repository_lifecycle_in_temp_dir() = runBlocking {
        val tempDir = File.createTempFile("clapi_test_git_", "").apply {
            delete()
            mkdirs()
        }
        try {
            val manager = GitRepositoryManager(
                executor = executor,
                baseStorageDir = tempDir.absolutePath,
            )

            val repoDir = File(tempDir, "test_repo")
            repoDir.mkdirs()

            // Initialize repo locally
            val initResult = executor.execute(repoDir.absolutePath, listOf("init", "-b", "main"))
            assertTrue(initResult.isSuccess, "git init failed: ${initResult.stderr}")

            // Set test user config locally for commits
            executor.execute(repoDir.absolutePath, listOf("config", "user.name", "Test User"))
            executor.execute(repoDir.absolutePath, listOf("config", "user.email", "test@example.com"))
            executor.execute(repoDir.absolutePath, listOf("config", "commit.gpgsign", "false"))

            // Initial status should be clean
            val initialStatus = manager.checkStatus(repoDir.absolutePath)
            assertTrue(initialStatus.isClean, "Initial empty repo should be clean")
            assertEquals("main", initialStatus.branch)

            // Create a collection directory and file
            val colDir = File(repoDir, "My_Collection")
            colDir.mkdirs()
            val collectionYaml = File(colDir, "collection.opencollection.yaml")
            collectionYaml.writeText("opencollection: 1.0.0\ninfo:\n  name: My Collection\n")

            // Status should now be dirty with 1 untracked file
            val dirtyStatus = manager.checkStatus(repoDir.absolutePath)
            assertFalse(dirtyStatus.isClean, "Status should be dirty after adding file")
            assertEquals(1, dirtyStatus.changes.size)
            assertEquals("My_Collection", dirtyStatus.changes[0].collectionName)
            assertEquals(GitChangeKind.ADDED, dirtyStatus.changes[0].changeKind)

            // Commit changes
            val commitResult = manager.commit(repoDir.absolutePath, "Initial collection commit")
            assertTrue(commitResult.isSuccess, "Commit should succeed: ${commitResult.stderr}")

            // Status should be clean again
            val cleanStatus = manager.checkStatus(repoDir.absolutePath)
            assertTrue(cleanStatus.isClean, "Status should be clean after commit")

            // Modify file
            collectionYaml.appendText("extensions:\n  storage:\n    type: git\n")
            val modifiedStatus = manager.checkStatus(repoDir.absolutePath)
            assertFalse(modifiedStatus.isClean)
            assertEquals(1, modifiedStatus.changes.size)
            assertEquals(GitChangeKind.MODIFIED, modifiedStatus.changes[0].changeKind)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun repo_id_and_sanitization() {
        val manager = GitRepositoryManager(executor = executor, baseStorageDir = "/tmp/storage")
        val repoId1 = manager.deriveRepoId("git@github.com:myorg/api-repo.git")
        val repoId2 = manager.deriveRepoId("https://github.com/myorg/api-repo.git")
        assertTrue(repoId1.isNotBlank())
        assertTrue(repoId2.isNotBlank())

        val sanitized = manager.sanitizeCollectionName("My Cool Collection / v1.0")
        assertEquals("My_Cool_Collection___v1.0", sanitized)
        assertEquals("/tmp/storage/$repoId1", manager.getRepositoryDirectory("git@github.com:myorg/api-repo.git"))
    }

    @Test
    fun missing_git_binary_throws_git_not_installed_exception() {
        val badExecutor = DesktopGitCliExecutor(gitBinary = "non_existent_git_binary_xyz_123")
        var thrown = false
        try {
            runBlocking {
                badExecutor.execute(workingDir = null, args = listOf("--version"))
            }
        } catch (_: GitNotInstalledException) {
            thrown = true
        }
        assertTrue(thrown, "Expected GitNotInstalledException when binary is missing")
    }

    @Test
    fun blank_commit_message_is_rejected() = runBlocking {
        val manager = GitRepositoryManager(executor = executor, baseStorageDir = "/tmp/storage")
        var thrown = false
        try {
            manager.commit("/tmp/storage", "")
        } catch (_: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown, "Blank commit message should be rejected")
    }
}
