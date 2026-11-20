package io.github.makeforge.clapi.ui.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.makeforge.clapi.ui.shared.components.SelectProtoFileDialog
import io.github.makeforge.clapi.ui.shared.platform.DiscoveredProtoFile
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GrpcProtoFolderDialogUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selecting_proto_folder_lists_discovered_protos_and_saves_folder_with_target_file() {
        var savedProto: String? = null
        var savedFolder: String? = null
        var savedSync = false
        var savedClose = false

        val folder = "/workspace/resources"
        val discovered = listOf(
            DiscoveredProtoFile("protobuf/common/metadata.proto", "/workspace/resources/protobuf/common/metadata.proto"),
            DiscoveredProtoFile("protobuf/greeter.proto", "/workspace/resources/protobuf/greeter.proto"),
        )

        composeRule.setContent {
            SelectProtoFileDialog(
                currentProtoFilePath = null,
                onDismiss = {},
                isGitSyncEnabled = true,
                initialSyncToGit = false,
                onPickFolder = { cb -> cb(folder) },
                onListProtoFiles = { dir -> if (dir == folder) discovered else emptyList() },
                onSaveSelection = { proto, protoFolder, sync, close ->
                    savedProto = proto
                    savedFolder = protoFolder
                    savedSync = sync
                    savedClose = close
                },
            )
        }
        composeRule.waitForIdle()

        // Pick folder
        composeRule.onNodeWithTag("proto-dialog-pick-folder-button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("proto-dialog-folder-display").assertIsDisplayed()
        composeRule.onNodeWithTag("proto-dialog-folder-file-list").assertIsDisplayed()
        composeRule.onNodeWithTag("proto-dialog-folder-item-protobuf/greeter.proto").assertIsDisplayed()
        composeRule.onNodeWithTag("proto-dialog-folder-sync-hint").assertIsDisplayed()

        // Save is disabled until a proto file from the folder is chosen
        composeRule.onNodeWithTag("proto-dialog-save").assertIsNotEnabled()

        // Pick target proto file and toggle Sync to Git
        composeRule.onNodeWithTag("proto-dialog-folder-item-protobuf/greeter.proto").performClick()
        composeRule.onNodeWithTag("proto-dialog-sync-to-git-toggle").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("proto-dialog-save").assertIsEnabled()
        composeRule.onNodeWithTag("proto-dialog-save").performClick()
        composeRule.waitForIdle()

        assertEquals("/workspace/resources/protobuf/greeter.proto", savedProto)
        assertEquals(folder, savedFolder)
        assertTrue(savedSync)
        assertTrue(savedClose)
    }

    @Test
    fun selecting_empty_folder_shows_empty_state_and_disables_save() {
        composeRule.setContent {
            SelectProtoFileDialog(
                currentProtoFilePath = null,
                onDismiss = {},
                onPickFolder = { cb -> cb("/workspace/empty_dir") },
                onListProtoFiles = { emptyList() },
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("proto-dialog-pick-folder-button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("proto-dialog-folder-empty").assertIsDisplayed()
        composeRule.onNodeWithTag("proto-dialog-save").assertIsNotEnabled()
    }

    @Test
    fun switching_from_folder_mode_to_single_file_or_collection_proto_clears_folder_and_hides_git_toggle_for_collection_proto() {
        var savedProto: String? = null
        var savedFolder: String? = "sentinel"
        var savedSync = true

        val collectionProto = "/shared/collection_greeter.proto"

        composeRule.setContent {
            SelectProtoFileDialog(
                currentProtoFilePath = "/workspace/protos/greeter.proto",
                currentProtoFolderPath = "/workspace/protos",
                collectionProtoFiles = listOf(collectionProto),
                isGitSyncEnabled = true,
                initialSyncToGit = true,
                onDismiss = {},
                onPickFile = { cb -> cb("/local/standalone.proto") },
                onListProtoFiles = {
                    listOf(DiscoveredProtoFile("greeter.proto", "/workspace/protos/greeter.proto"))
                },
                onSaveSelection = { proto, folder, sync, _ ->
                    savedProto = proto
                    savedFolder = folder
                    savedSync = sync
                },
            )
        }
        composeRule.waitForIdle()

        // Initially in folder mode with Git sync toggle visible
        composeRule.onNodeWithTag("proto-dialog-folder-display").assertIsDisplayed()
        composeRule.onNodeWithTag("proto-dialog-sync-to-git-toggle").assertIsDisplayed()

        // Switch to collection-level proto -> folder display clears and request-level Git toggle hides
        composeRule.onNodeWithTag("proto-dialog-collection-item-$collectionProto").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("proto-dialog-folder-display").assertDoesNotExist()
        composeRule.onNodeWithTag("proto-dialog-sync-to-git-toggle").assertDoesNotExist()

        composeRule.onNodeWithTag("proto-dialog-save").performClick()
        composeRule.waitForIdle()

        assertEquals(collectionProto, savedProto)
        assertNull(savedFolder)
        assertFalse(savedSync)
    }

    @Test
    fun changing_folder_and_canceling_prompts_unsaved_changes_confirmation() {
        var dismissed = false
        composeRule.setContent {
            SelectProtoFileDialog(
                currentProtoFilePath = null,
                onDismiss = { dismissed = true },
                onPickFolder = { cb -> cb("/workspace/protos") },
                onListProtoFiles = {
                    listOf(DiscoveredProtoFile("greeter.proto", "/workspace/protos/greeter.proto"))
                },
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("proto-dialog-pick-folder-button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("proto-dialog-cancel").performClick()
        composeRule.waitForIdle()

        // Unsaved changes confirmation dialog appears instead of immediate dismiss
        assertFalse(dismissed)
        composeRule.onNodeWithTag("dirty-close-discard").assertIsDisplayed()
        composeRule.onNodeWithTag("dirty-close-discard").performClick()
        composeRule.waitForIdle()

        assertTrue(dismissed)
    }
}
