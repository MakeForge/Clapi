package io.github.makeforge.clapi.ui.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.makeforge.clapi.core.model.storage.GitStorageConfig
import io.github.makeforge.clapi.core.model.storage.StorageExtension
import io.github.makeforge.clapi.core.model.storage.StorageType
import io.github.makeforge.clapi.core.network.grpc.ProtocJarParser
import io.github.makeforge.clapi.core.storage.git.DesktopGitCliExecutor
import io.github.makeforge.clapi.core.storage.git.GitRepositoryManager
import io.github.makeforge.clapi.core.storage.git.ProtoPathResolver
import io.github.makeforge.clapi.core.storage.opencollection.OpenCollectionSerializer
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.ProtoFileItemState
import io.github.makeforge.clapi.ui.shared.state.ProtoImportPathState
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import io.github.makeforge.clapi.ui.shared.state.AppStateExamples

class ProtoGitSyncUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun proto_sync_toggle_is_hidden_when_local_and_visible_when_git_storage_is_enabled() {
        val state = AppStateExamples.demo()
        val protoPath = "/tmp/example_service.proto"
        val idx = state.collections.indexOfFirst { it.id == "c1" }
        state.collections[idx] = state.collections[idx].copy(
            protoFiles = listOf(ProtoFileItemState(protoPath, syncToGit = false)),
            storageExtension = StorageExtension(type = StorageType.LOCAL),
        )

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Open collection editor -> Protobuf tab
        composeRule.onNodeWithTag("collection-node-c1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("collection-tab-protobuf").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("proto-file-row-$protoPath").assertIsDisplayed()
        composeRule.onNodeWithTag("proto-sync-toggle-$protoPath").assertDoesNotExist()

        // Switch to Storage tab and select Git
        composeRule.onNodeWithTag("collection-tab-storage").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("storage-radio-git").performClick()
        composeRule.waitForIdle()

        // Return to Protobuf tab -> Sync to Git toggle is now visible
        composeRule.onNodeWithTag("collection-tab-protobuf").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("proto-sync-toggle-$protoPath").assertIsDisplayed()
    }

    @Test
    fun selective_proto_sync_copies_synced_proto_and_writes_relative_and_absolute_paths_to_yaml() {
        val tempDir = File.createTempFile("clapi_proto_git_ui_", "").apply {
            delete()
            mkdirs()
        }
        try {
            val remoteRepoDir = File(tempDir, "remote.git").apply { mkdirs() }
            val storageBaseDir = File(tempDir, "storage").apply { mkdirs() }
            val sourceProtosDir = File(tempDir, "protos").apply { mkdirs() }

            val syncedProtoFile = File(sourceProtosDir, "greeter.proto").apply {
                writeText(
                    """
                    syntax = "proto3";
                    package greeting;
                    service Greeter {
                      rpc SayHello (HelloRequest) returns (HelloReply);
                    }
                    message HelloRequest { string name = 1; }
                    message HelloReply { string message = 1; }
                    """.trimIndent()
                )
            }
            val unsyncedProtoFile = File(sourceProtosDir, "local_only.proto").apply {
                writeText(
                    """
                    syntax = "proto3";
                    package localonly;
                    message Ping { string id = 1; }
                    """.trimIndent()
                )
            }
            val syncedImportDir = File(sourceProtosDir, "shared_imports").apply { mkdirs() }
            File(syncedImportDir, "common.proto").writeText("syntax = \"proto3\";\npackage shared;\n")

            val executor = DesktopGitCliExecutor()
            val manager = GitRepositoryManager(executor, storageBaseDir.absolutePath)
            val remoteUrl = remoteRepoDir.absolutePath

            runBlocking {
                executor.execute(remoteRepoDir.absolutePath, listOf("init", "--bare", "-b", "main"))
                manager.ensureClonedOrCheckedOut(remoteUrl, "main")
            }

            val state = AppStateExamples.demo()
            state.gitRepositoryManager = manager

            val idx = state.collections.indexOfFirst { it.id == "c1" }
            val c1 = state.collections[idx].copy(
                protoFiles = listOf(
                    ProtoFileItemState(syncedProtoFile.absolutePath, syncToGit = false),
                    ProtoFileItemState(unsyncedProtoFile.absolutePath, syncToGit = false),
                ),
                importPaths = listOf(
                    ProtoImportPathState(syncedImportDir.absolutePath, enabled = true, syncToGit = false),
                ),
                storageExtension = StorageExtension(
                    type = StorageType.GIT,
                    git = GitStorageConfig(remoteUrl = remoteUrl, branch = "main"),
                ),
            )
            state.collections[idx] = c1

            composeRule.setContent { MainScreen(state) }
            composeRule.waitForIdle()

            // Open collection c1 and go to Protobuf tab
            composeRule.onNodeWithTag("collection-node-c1").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("collection-tab-protobuf").performClick()
            composeRule.waitForIdle()

            // Toggle Sync to Git ON for greeter.proto and shared_imports, leave OFF for local_only.proto
            composeRule.onNodeWithTag("proto-sync-toggle-${syncedProtoFile.absolutePath}").performClick()
            composeRule.onNodeWithTag("import-sync-toggle-${syncedImportDir.absolutePath}").performClick()
            composeRule.waitForIdle()

            assertTrue(state.collectionProtoFiles.first { it.path == syncedProtoFile.absolutePath }.syncToGit)
            assertFalse(state.collectionProtoFiles.first { it.path == unsyncedProtoFile.absolutePath }.syncToGit)
            assertTrue(state.collectionImportPaths.first { it.path == syncedImportDir.absolutePath }.syncToGit)

            // Save collection via top Save button
            composeRule.onNodeWithTag("collection-save-button").performClick()
            val colGitDir = File(manager.getCollectionDirectory(remoteUrl, c1.name))
            val yamlFile = File(colGitDir, "collection.opencollection.yaml")

            composeRule.waitUntil(timeoutMillis = 10_000) {
                yamlFile.exists()
            }
            composeRule.waitForIdle()

            // Verify physical copy in <collection-dir>/protobuf/collection/greeter.proto
            val copiedProto = File(colGitDir, "protobuf/collection/greeter.proto")
            assertTrue(copiedProto.exists(), "Expected greeter.proto to be copied into protobuf/collection/")
            val copiedImport = File(colGitDir, "protobuf/collection/imports/shared_imports/common.proto")
            assertTrue(copiedImport.exists(), "Expected shared_imports to be copied into protobuf/collection/imports/")

            // Verify YAML contains relative path for synced proto and absolute path for unsynced proto
            val doc = OpenCollectionSerializer.deserializeYaml(yamlFile.readText())
            val yamlProtoPaths = doc.config?.protobuf?.protoFiles.orEmpty().map { it.path }
            assertEquals(
                listOf("./protobuf/collection/greeter.proto", unsyncedProtoFile.absolutePath),
                yamlProtoPaths,
            )
            val yamlImportPaths = doc.config?.protobuf?.importPaths.orEmpty().map { it.path }
            assertEquals(
                listOf("./protobuf/collection/imports/shared_imports"),
                yamlImportPaths,
            )

            // Verify ProtocJarParser + ProtoPathResolver compiles the relative path seamlessly
            val resolvedGreeter = ProtoPathResolver.resolve("./protobuf/collection/greeter.proto", colGitDir.absolutePath)
            val resolvedImports = ProtoPathResolver.resolveAll(yamlImportPaths, colGitDir.absolutePath)
            val schema = runBlocking {
                ProtocJarParser().parseProtoFile(resolvedGreeter, resolvedImports)
            }
            assertEquals(1, schema.services.size)
            assertEquals("greeting.Greeter/SayHello", schema.services.first().methods.first().fullMethodName)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
