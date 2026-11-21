package io.github.makeforge.clapi.qa

import io.github.makeforge.clapi.core.model.opencollection.CollectionInfo
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestDetails
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestInfo
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestItem
import io.github.makeforge.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.makeforge.clapi.core.model.storage.GitStorageConfig
import io.github.makeforge.clapi.core.model.storage.StorageExtension
import io.github.makeforge.clapi.core.model.storage.StorageType
import io.github.makeforge.clapi.core.model.storage.getStorageExtension
import io.github.makeforge.clapi.core.model.storage.withStorageExtension
import io.github.makeforge.clapi.core.storage.git.DesktopGitCliExecutor
import io.github.makeforge.clapi.core.storage.git.GitConflictException
import io.github.makeforge.clapi.core.storage.git.GitRepositoryManager
import io.github.makeforge.clapi.core.storage.git.ProtoPathResolver
import io.github.makeforge.clapi.core.storage.opencollection.OpenCollectionSerializer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end QA integration test validating all scenarios from quickstart.md:
 * - Scenario 1: Configure Collection Git Storage
 * - Scenario 2: Make Changes, Commit & Push
 * - Scenario 3: Selective Protobuf File Sync with Dual-Path Resolution
 * - Scenario 4: Pull and Conflict Handling
 */
class GitSyncE2ETest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val executor = DesktopGitCliExecutor()

    @Test
    fun execute_quickstart_scenarios_e2e() = runBlocking {
        val rootDir = tempDir.newFolder("quickstart_e2e")
        val remoteSrcDir = File(rootDir, "remote_src").apply { mkdirs() }
        val remoteGitDir = File(rootDir, "remote.git")
        val storageBaseDir = File(rootDir, "storage").apply { mkdirs() }

        // Setup bare remote repo with an initial commit on 'main'
        val initResult = executor.execute(remoteSrcDir.absolutePath, listOf("init", "-b", "main"))
        assertTrue(initResult.isSuccess, "Failed to init source repo: ${initResult.stderr}")
        executor.execute(remoteSrcDir.absolutePath, listOf("config", "user.name", "Test CI"))
        executor.execute(remoteSrcDir.absolutePath, listOf("config", "user.email", "test@example.com"))
        executor.execute(remoteSrcDir.absolutePath, listOf("config", "commit.gpgsign", "false"))

        File(remoteSrcDir, "README.md").writeText("# Quickstart Test Remote\n")
        executor.execute(remoteSrcDir.absolutePath, listOf("add", "README.md"))
        executor.execute(remoteSrcDir.absolutePath, listOf("commit", "-m", "Initial remote commit"))

        val bareCloneResult = executor.execute(
            rootDir.absolutePath,
            listOf("clone", "--bare", remoteSrcDir.absolutePath, remoteGitDir.absolutePath),
        )
        assertTrue(bareCloneResult.isSuccess, "Failed to create bare remote: ${bareCloneResult.stderr}")

        val remoteUrl = remoteGitDir.absolutePath
        val manager = GitRepositoryManager(executor = executor, baseStorageDir = storageBaseDir.absolutePath)

        // =========================================================================
        // SCENARIO 1: Configure Collection Git Storage
        // =========================================================================
        val collectionName = "Quickstart API"
        val clone = manager.ensureClonedOrCheckedOut(remoteUrl, "main", collectionName)
        val repoDir = clone.workingDirectory
        assertTrue(File(repoDir, ".git").exists(), "Cloned repository must have .git directory")

        // Disable gpg signing in cloned test repo
        executor.execute(repoDir, listOf("config", "user.name", "Clapi User"))
        executor.execute(repoDir, listOf("config", "user.email", "user@clapi.dev"))
        executor.execute(repoDir, listOf("config", "commit.gpgsign", "false"))

        val collectionDir = File(manager.getCollectionDirectory(remoteUrl, collectionName)).apply { mkdirs() }
        assertEquals("Quickstart_API", collectionDir.name)

        // Create initial OpenCollection document with git storage extension
        val storageExt = StorageExtension(
            type = StorageType.GIT,
            git = GitStorageConfig(remoteUrl = remoteUrl, branch = "main"),
        )
        val extensionsMap = emptyMap<String, JsonElement>().withStorageExtension(storageExt)

        val initialDoc = OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(name = collectionName, version = "1.0.0"),
            items = listOf(
                HttpRequestItem(
                    info = HttpRequestInfo(name = "Health Check"),
                    http = HttpRequestDetails(method = "GET", url = "https://api.example.com/health"),
                ),
            ),
            extensions = extensionsMap,
        )

        val yamlFile = File(collectionDir, "collection.opencollection.yaml")
        yamlFile.writeText(OpenCollectionSerializer.serializeYaml(initialDoc))
        assertTrue(yamlFile.exists(), "collection.opencollection.yaml must be exported")

        // Validate storage extension deserialization
        val deserializedDoc = OpenCollectionSerializer.deserializeYaml(yamlFile.readText())
        val decodedStorage = deserializedDoc.extensions.getStorageExtension()
        assertNotNull(decodedStorage)
        assertEquals(StorageType.GIT, decodedStorage.type)
        assertEquals(remoteUrl, decodedStorage.git?.remoteUrl)
        assertEquals("main", decodedStorage.git?.branch)

        // =========================================================================
        // SCENARIO 2: Make Changes, Commit & Push
        // =========================================================================
        // 1. Status shows dirty
        val dirtyStatus = manager.checkStatus(repoDir)
        assertFalse(dirtyStatus.isClean, "Status should be dirty after creating collection file")
        assertEquals("main", dirtyStatus.branch)

        // 2. Commit
        val commitResult = manager.commit(repoDir, "Add Quickstart API collection")
        assertTrue(commitResult.isSuccess, "Commit failed: ${commitResult.stderr}")

        val cleanStatus = manager.checkStatus(repoDir)
        assertTrue(cleanStatus.isClean, "Status should be clean after commit")

        // 3. Push
        val pushResult = manager.push(repoDir, "main")
        assertTrue(pushResult.isSuccess, "Push failed: ${pushResult.stderr}")

        // =========================================================================
        // SCENARIO 3: Selective Protobuf File Sync with Dual-Path Resolution
        // =========================================================================
        // Create local proto files outside repository
        val localProtosDir = tempDir.newFolder("local_protos")
        val syncedProtoFile = File(localProtosDir, "user_service.proto").apply {
            writeText("syntax = \"proto3\";\npackage user;\nservice UserService {}\n")
        }
        val unSyncedProtoFile = File(localProtosDir, "internal_service.proto").apply {
            writeText("syntax = \"proto3\";\npackage internal;\nservice InternalService {}\n")
        }

        // Sync first proto file to Git collection folder
        val relativeSyncedPath = ProtoPathResolver.syncCollectionProtoFile(
            sourcePath = syncedProtoFile.absolutePath,
            collectionDir = collectionDir.absolutePath,
        )
        assertEquals("./protobuf/collection/user_service.proto", relativeSyncedPath)
        assertTrue(File(collectionDir, "protobuf/collection/user_service.proto").exists(), "Synced proto must be copied")

        // Second proto file is NOT synced
        val unSyncedPath = unSyncedProtoFile.absolutePath

        // Resolve paths
        val resolvedSynced = ProtoPathResolver.resolve(relativeSyncedPath, collectionDir.absolutePath)
        val resolvedUnsynced = ProtoPathResolver.resolve(unSyncedPath, collectionDir.absolutePath)
        assertEquals(File(collectionDir, "protobuf/collection/user_service.proto").canonicalPath, File(resolvedSynced).canonicalPath)
        assertEquals(unSyncedProtoFile.canonicalPath, File(resolvedUnsynced).canonicalPath)

        // Update collection YAML with proto extensions
        val clapiExtension = buildJsonObject {
            put("protoFiles", buildJsonArray {
                add(buildJsonObject { put("path", relativeSyncedPath); put("syncToGit", true) })
                add(buildJsonObject { put("path", unSyncedPath); put("syncToGit", false) })
            })
        }
        val docWithProtos = initialDoc.copy(
            extensions = extensionsMap + ("clapi" to clapiExtension),
        )
        yamlFile.writeText(OpenCollectionSerializer.serializeYaml(docWithProtos))

        // Commit and push proto files
        val protoCommit = manager.commit(repoDir, "Add synced protobuf schemas")
        assertTrue(protoCommit.isSuccess)
        val protoPush = manager.push(repoDir, "main")
        assertTrue(protoPush.isSuccess)

        // =========================================================================
        // SCENARIO 4: Pull and Conflict Handling
        // =========================================================================
        // Simulate a remote edit by cloning in a separate directory and pushing a change
        val remoteClientDir = File(rootDir, "remote_client")
        val clientCloneResult = executor.execute(rootDir.absolutePath, listOf("clone", remoteUrl, remoteClientDir.absolutePath))
        assertTrue(clientCloneResult.isSuccess)
        executor.execute(remoteClientDir.absolutePath, listOf("config", "user.name", "Remote Colleague"))
        executor.execute(remoteClientDir.absolutePath, listOf("config", "user.email", "remote@clapi.dev"))
        executor.execute(remoteClientDir.absolutePath, listOf("config", "commit.gpgsign", "false"))

        val remoteYaml = File(remoteClientDir, "Quickstart_API/collection.opencollection.yaml")
        remoteYaml.writeText(remoteYaml.readText() + "\n# Remote comment from colleague\n")
        executor.execute(remoteClientDir.absolutePath, listOf("commit", "-am", "Remote colleague edit"))
        val remotePushResult = executor.execute(remoteClientDir.absolutePath, listOf("push", "origin", "main"))
        assertTrue(remotePushResult.isSuccess, "Remote colleague push failed: ${remotePushResult.stderr}")

        // Now introduce a conflicting local change and commit it in the local repository
        yamlFile.writeText(yamlFile.readText() + "\n# Conflicting local comment\n")
        val localCommitResult = manager.commit(repoDir, "Conflicting local commit")
        assertTrue(localCommitResult.isSuccess, "Local commit before pull should succeed: ${localCommitResult.stderr}")

        // Pull should trigger a conflict or detect conflicts
        var conflictTriggered = false
        try {
            val pullResult = manager.pull(repoDir, "main")
            if (!pullResult.isSuccess) {
                val statusAfterPull = manager.checkStatus(repoDir)
                if (statusAfterPull.hasConflicts) {
                    conflictTriggered = true
                }
            }
        } catch (e: GitConflictException) {
            conflictTriggered = true
        }

        if (!conflictTriggered) {
            val statusAfterPull = manager.checkStatus(repoDir)
            if (statusAfterPull.hasConflicts) {
                conflictTriggered = true
            }
        }
        assertTrue(conflictTriggered, "Merge conflict must be detected when local and remote edit the same file")
    }

    @Test
    fun syncs_request_proto_folder_recursively_excluding_non_proto_files_and_compiles_from_fresh_clone() = runBlocking {
        val rootDir = tempDir.newFolder("proto_folder_e2e")
        val remoteRepoDir = File(rootDir, "remote.git").apply { mkdirs() }
        val storageBaseDir = File(rootDir, "storage").apply { mkdirs() }
        val sourceProtoFolder = File(rootDir, "local_protos/protobuf").apply { mkdirs() }
        val sourceCommonDir = File(sourceProtoFolder, "common").apply { mkdirs() }

        File(sourceCommonDir, "metadata.proto").writeText(
            """
            syntax = "proto3";
            package clapi.common;
            message RequestMetadata { string trace_id = 1; }
            """.trimIndent()
        )
        val greeterFile = File(sourceProtoFolder, "greeter.proto").apply {
            writeText(
                """
                syntax = "proto3";
                package helloworld;
                import "protobuf/common/metadata.proto";
                service Greeter {
                  rpc SayHello (HelloRequest) returns (HelloReply);
                }
                message HelloRequest { string name = 1; }
                message HelloReply {
                  string message = 1;
                  clapi.common.RequestMetadata ack_metadata = 2;
                }
                """.trimIndent()
            )
        }
        File(sourceProtoFolder, "README.md").writeText("ignored non-proto file")

        val manager = GitRepositoryManager(executor, storageBaseDir.absolutePath)
        val remoteUrl = remoteRepoDir.absolutePath

        assertTrue(executor.execute(remoteRepoDir.absolutePath, listOf("init", "--bare", "-b", "main")).isSuccess)
        val repoDir = manager.ensureClonedOrCheckedOut(remoteUrl, "main").workingDirectory
        executor.execute(repoDir, listOf("config", "user.name", "Clapi E2E"))
        executor.execute(repoDir, listOf("config", "user.email", "e2e@clapi.dev"))
        executor.execute(repoDir, listOf("config", "commit.gpgsign", "false"))
        executor.execute(repoDir, listOf("commit", "--allow-empty", "-m", "Init"))
        executor.execute(repoDir, listOf("push", "-u", "origin", "main"))

        val collectionDir = File(manager.getCollectionDirectory(remoteUrl, "GrpcFolderCol")).apply { mkdirs() }
        val synced = ProtoPathResolver.syncRequestProtoFolder(
            sourceFolderPath = sourceProtoFolder.absolutePath,
            sourceProtoFilePath = greeterFile.absolutePath,
            requestId = "req-grpc-folder",
            collectionDir = collectionDir.absolutePath,
        )

        assertTrue(manager.commit(repoDir, "Sync request proto folder").isSuccess)
        assertTrue(manager.push(repoDir, "main").isSuccess)

        // Clone on a "teammate" machine without local_protos
        val teammateClone = File(rootDir, "teammate_clone")
        assertTrue(executor.execute(rootDir.absolutePath, listOf("clone", remoteUrl, teammateClone.absolutePath)).isSuccess)
        val teammateCollectionDir = File(teammateClone, "GrpcFolderCol")

        assertTrue(File(teammateCollectionDir, "protobuf/requests/req-grpc-folder/protobuf/greeter.proto").exists())
        assertTrue(File(teammateCollectionDir, "protobuf/requests/req-grpc-folder/protobuf/common/metadata.proto").exists())
        assertFalse(File(teammateCollectionDir, "protobuf/requests/req-grpc-folder/protobuf/README.md").exists())

        val resolvedProto = ProtoPathResolver.resolve(synced.protoFileRelPath, teammateCollectionDir.absolutePath)
        val resolvedFolder = ProtoPathResolver.resolve(synced.folderRelPath, teammateCollectionDir.absolutePath)
        val schema = io.github.makeforge.clapi.core.network.grpc.ProtocJarParser().parseProtoFile(
            protoFilePath = resolvedProto,
            includePaths = listOf(resolvedFolder),
        )
        assertEquals(1, schema.services.size)
        assertEquals("helloworld.Greeter/SayHello", schema.services.single().methods.single().fullMethodName)
    }
}
