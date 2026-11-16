package io.github.crustack.clapi.qa

import io.github.crustack.clapi.core.storage.git.CollectionGitStatus
import io.github.crustack.clapi.core.storage.git.DesktopGitCliExecutor
import io.github.crustack.clapi.core.storage.git.GitChangeKind
import io.github.crustack.clapi.core.storage.git.GitRepositoryManager
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MultiCollectionGitSyncE2ETest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val executor = DesktopGitCliExecutor()

    @Test
    fun multi_collection_shared_repository_lifecycle() = runBlocking {
        val rootDir = tempDir.newFolder("git_test_root")
        val remoteSrcDir = File(rootDir, "remote_src").apply { mkdirs() }
        val remoteGitDir = File(rootDir, "remote.git")
        val storageBaseDir = File(rootDir, "storage").apply { mkdirs() }

        // 1. Initialize bare remote repository with an initial commit on 'main'
        val initSrcResult = executor.execute(remoteSrcDir.absolutePath, listOf("init", "-b", "main"))
        assertTrue(initSrcResult.isSuccess, "Failed to init source repo: ${initSrcResult.stderr}")
        executor.execute(remoteSrcDir.absolutePath, listOf("config", "user.name", "Test CI"))
        executor.execute(remoteSrcDir.absolutePath, listOf("config", "user.email", "test@example.com"))
        executor.execute(remoteSrcDir.absolutePath, listOf("config", "commit.gpgsign", "false"))

        File(remoteSrcDir, "README.md").writeText("# API Collections Repository\n")
        executor.execute(remoteSrcDir.absolutePath, listOf("add", "README.md"))
        executor.execute(remoteSrcDir.absolutePath, listOf("commit", "-m", "Initial remote commit"))

        val bareCloneResult = executor.execute(
            rootDir.absolutePath,
            listOf("clone", "--bare", remoteSrcDir.absolutePath, remoteGitDir.absolutePath)
        )
        assertTrue(bareCloneResult.isSuccess, "Failed to create bare remote repo: ${bareCloneResult.stderr}")

        val remoteUrl = remoteGitDir.absolutePath

        // 2. Setup GitRepositoryManager pointing to local storage folder
        val manager = GitRepositoryManager(
            executor = executor,
            baseStorageDir = storageBaseDir.absolutePath,
        )

        // 3. Connect Collection 1 ("Users API")
        val clone1 = manager.ensureClonedOrCheckedOut(remoteUrl, "main", "Users API")
        val repoDir = clone1.workingDirectory
        assertTrue(File(repoDir, ".git").exists(), "Cloned repository should contain .git directory")

        // Configure local git in the cloned repo to prevent gpg signing failures in test
        executor.execute(repoDir, listOf("config", "user.name", "Test User"))
        executor.execute(repoDir, listOf("config", "user.email", "test@example.com"))
        executor.execute(repoDir, listOf("config", "commit.gpgsign", "false"))

        // 4. Connect Collection 2 ("Payments API") to the same remote repo
        val clone2 = manager.ensureClonedOrCheckedOut(remoteUrl, "main", "Payments API")

        // Verify shared clone directory
        assertEquals(clone1.workingDirectory, clone2.workingDirectory, "Both collections must share the same repo clone")
        assertEquals(clone1.repoId, clone2.repoId, "Repo IDs must match")

        // Verify registered collections
        val registered = manager.getRegisteredCollections(remoteUrl)
        assertTrue(registered.contains("Users API"), "Registered collections must include Users API")
        assertTrue(registered.contains("Payments API"), "Registered collections must include Payments API")

        // Verify per-collection directory isolation
        val usersDir = File(manager.getCollectionDirectory(remoteUrl, "Users API"))
        val paymentsDir = File(manager.getCollectionDirectory(remoteUrl, "Payments API"))
        assertEquals("Users_API", usersDir.name)
        assertEquals("Payments_API", paymentsDir.name)
        assertEquals(repoDir, usersDir.parentFile.absolutePath)
        assertEquals(repoDir, paymentsDir.parentFile.absolutePath)

        usersDir.mkdirs()
        paymentsDir.mkdirs()

        // 5. Populate files in both collection directories
        val usersYaml = File(usersDir, "collection.opencollection.yaml")
        usersYaml.writeText("opencollection: 1.0.0\ninfo:\n  name: Users API\n")
        val usersProtoDir = File(usersDir, "protobuf").apply { mkdirs() }
        File(usersProtoDir, "user.proto").writeText("syntax = \"proto3\";\nmessage User {}\n")

        val paymentsYaml = File(paymentsDir, "collection.opencollection.yaml")
        paymentsYaml.writeText("opencollection: 1.0.0\ninfo:\n  name: Payments API\n")
        val paymentsProtoDir = File(paymentsDir, "protobuf").apply { mkdirs() }
        File(paymentsProtoDir, "payment.proto").writeText("syntax = \"proto3\";\nmessage Payment {}\n")

        // 6. Check Git Status - changes must be grouped by collection folder
        val status = manager.checkStatus(repoDir)
        assertFalse(status.isClean, "Status should be dirty after adding files to both collections")
        assertEquals("main", status.branch)
        assertFalse(status.hasConflicts)

        val groupedChanges = manager.groupChangesByCollection(status)
        assertEquals(2, groupedChanges.size, "Changes should be grouped into 2 collections")
        assertTrue(groupedChanges.containsKey("Users_API"), "Must contain Users_API changes")
        assertTrue(groupedChanges.containsKey("Payments_API"), "Must contain Payments_API changes")

        val usersChanges = groupedChanges["Users_API"]
        assertNotNull(usersChanges)
        assertEquals(2, usersChanges.size)
        assertTrue(usersChanges.all { it.collectionName == "Users_API" })
        assertTrue(usersChanges.all { it.changeKind == GitChangeKind.ADDED })

        val paymentsChanges = groupedChanges["Payments_API"]
        assertNotNull(paymentsChanges)
        assertEquals(2, paymentsChanges.size)
        assertTrue(paymentsChanges.all { it.collectionName == "Payments_API" })
        assertTrue(paymentsChanges.all { it.changeKind == GitChangeKind.ADDED })

        // Check display name resolution
        assertEquals("Users API", manager.resolveDisplayCollectionName("Users_API"))
        assertEquals("Payments API", manager.resolveDisplayCollectionName("Payments_API"))

        // 7. Commit all changes across both collections in a single unified commit
        val commitResult = manager.commit(repoDir, "feat: add Users and Payments API collections")
        assertTrue(commitResult.isSuccess, "Unified commit failed: ${commitResult.stderr}")

        val cleanStatus = manager.checkStatus(repoDir)
        assertTrue(cleanStatus.isClean, "Status must be clean after commit")
        assertTrue(cleanStatus.changes.isEmpty())

        // 8. Push to remote
        val pushResult = manager.push(repoDir, "main")
        assertTrue(pushResult.isSuccess, "Push failed: ${pushResult.stderr}")

        // 9. Verify remote repository received all changes by cloning to a fresh location
        val verifyCloneDir = File(rootDir, "verify_clone")
        val verifyCloneResult = executor.execute(
            rootDir.absolutePath,
            listOf("clone", remoteUrl, verifyCloneDir.absolutePath)
        )
        assertTrue(verifyCloneResult.isSuccess, "Failed to clone remote repository: ${verifyCloneResult.stderr}")

        assertTrue(File(verifyCloneDir, "Users_API/collection.opencollection.yaml").exists())
        assertTrue(File(verifyCloneDir, "Users_API/protobuf/user.proto").exists())
        assertTrue(File(verifyCloneDir, "Payments_API/collection.opencollection.yaml").exists())
        assertTrue(File(verifyCloneDir, "Payments_API/protobuf/payment.proto").exists())

        // 10. Verify incremental changes isolated to one collection
        usersYaml.appendText("  version: 2.0.0\n")
        val incrementalStatus = manager.checkStatus(repoDir)
        assertFalse(incrementalStatus.isClean)
        val incrementalGroups = manager.groupChangesByCollection(incrementalStatus)
        assertEquals(1, incrementalGroups.size, "Only Users_API should have pending changes")
        assertTrue(incrementalGroups.containsKey("Users_API"))
        assertEquals(GitChangeKind.MODIFIED, incrementalGroups["Users_API"]!![0].changeKind)

        val commit2Result = manager.commit(repoDir, "chore: update Users API to v2")
        assertTrue(commit2Result.isSuccess)
        val push2Result = manager.push(repoDir, "main")
        assertTrue(push2Result.isSuccess)
    }
}
