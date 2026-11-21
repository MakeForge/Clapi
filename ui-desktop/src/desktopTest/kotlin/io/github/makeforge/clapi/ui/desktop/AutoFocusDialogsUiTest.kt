package io.github.makeforge.clapi.ui.desktop

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.core.model.storage.GitStorageConfig
import io.github.makeforge.clapi.core.model.storage.StorageExtension
import io.github.makeforge.clapi.core.model.storage.StorageType
import io.github.makeforge.clapi.core.storage.git.CollectionGitStatus
import io.github.makeforge.clapi.core.storage.git.GitChangeKind
import io.github.makeforge.clapi.core.storage.git.GitFileChange
import io.github.makeforge.clapi.ui.shared.components.GitSyncDialog
import io.github.makeforge.clapi.ui.shared.components.SelectProtoFileDialog
import io.github.makeforge.clapi.ui.shared.components.dialogs.env.EnvironmentEditDialog
import io.github.makeforge.clapi.ui.shared.components.sidebar.RenameItemDialog
import io.github.makeforge.clapi.ui.shared.components.variable.VariableEditorPopup
import io.github.makeforge.clapi.ui.shared.components.websocket.RenameVariantDialog
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class AutoFocusDialogsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun renameItemDialog_immediatelyFocusedAndDefaultValueSelected() {
        var value = "New Folder"
        composeRule.setContent {
            RenameItemDialog(
                title = "Add folder",
                value = value,
                onValueChange = { value = it },
                onDismiss = {},
                onConfirm = {},
            )
        }
        composeRule.waitForIdle()
        val node = composeRule.onNodeWithTag("rename-dialog-input")
        node.assertIsDisplayed().assertIsFocused()

        val selection = node.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        assertEquals(TextRange(0, "New Folder".length), selection)

        node.performTextInput("My Folder")
        composeRule.waitForIdle()
        assertEquals("My Folder", value)
    }

    @Test
    fun renameVariantDialog_immediatelyFocusedAndDefaultValueSelected() {
        var value = "Default"
        composeRule.setContent {
            RenameVariantDialog(
                title = "Rename variant",
                value = value,
                onValueChange = { value = it },
                onDismiss = {},
                onConfirm = {},
            )
        }
        composeRule.waitForIdle()
        val node = composeRule.onNodeWithTag("rename-variant-dialog-input")
        node.assertIsDisplayed().assertIsFocused()

        val selection = node.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        assertEquals(TextRange(0, "Default".length), selection)

        node.performTextInput("Variant B")
        composeRule.waitForIdle()
        assertEquals("Variant B", value)
    }

    @Test
    fun selectProtoFileDialog_immediatelyFocusedAndDefaultValueSelected() {
        var savedPath = ""
        composeRule.setContent {
            SelectProtoFileDialog(
                currentProtoFilePath = "/path/to/service.proto",
                onSave = { savedPath = it },
                onDismiss = {},
            )
        }
        composeRule.waitForIdle()
        val node = composeRule.onNodeWithTag("proto-dialog-path-input")
        node.assertIsDisplayed().assertIsFocused()

        val selection = node.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        assertEquals(TextRange(0, "/path/to/service.proto".length), selection)
    }

    @Test
    fun variableEditorPopup_immediatelyFocusedAndDefaultValueSelected() {
        val env = EnvState("Dev").apply {
            variables.add(MutableKeyValue("MY_VAR", "value123"))
        }
        val state = AppState().apply {
            environments.add(env)
            selectedEnvIndex = 0
        }
        composeRule.setContent {
            VariableEditorPopup(
                variableName = "MY_VAR",
                state = state,
                onDismiss = {},
            )
        }
        composeRule.waitForIdle()
        val node = composeRule.onNodeWithTag("variable-popup-value-input")
        node.assertIsDisplayed().assertIsFocused()

        val selection = node.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        assertEquals(TextRange(0, "value123".length), selection)

        node.performTextInput("newVal")
        composeRule.waitForIdle()
        node.assertTextEquals("newVal")
    }

    @Test
    fun environmentEditDialog_immediatelyFocusedAndDefaultValueSelected() {
        val env = EnvState("Dev")
        val state = AppState().apply {
            environments.add(env)
            openEnvEdit(0)
        }
        composeRule.setContent {
            EnvironmentEditDialog(state)
        }
        composeRule.waitForIdle()
        val node = composeRule.onNodeWithTag("env-name-field")
        node.assertIsDisplayed().assertIsFocused()

        val selection = node.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        assertEquals(TextRange(0, "Dev".length), selection)
    }

    @Test
    fun gitSyncDialog_withChanges_immediatelyFocused() {
        val remoteUrl = "https://github.com/example/test.git"
        val collectionNode = CollectionNode(
            id = "col-1",
            name = "Test Col",
            kind = RequestKind.COLLECTION,
            storageExtension = StorageExtension(
                type = StorageType.GIT,
                git = GitStorageConfig(
                    remoteUrl = remoteUrl,
                    branch = "main",
                ),
            ),
        )
        val state = AppState().apply {
            collections.add(collectionNode)
            selectedCollectionId = "col-1"
            val repoDir = gitRepositoryManager.getRepositoryDirectory(remoteUrl)
            gitStatusesByRepoDir[repoDir] = CollectionGitStatus(
                branch = "main",
                isClean = false,
                hasConflicts = false,
                changes = listOf(
                    GitFileChange(
                        relativePath = "col-1/req.json",
                        collectionName = "Test Col",
                        changeKind = GitChangeKind.MODIFIED,
                    )
                ),
            )
            showGitSyncDialog = true
        }
        composeRule.setContent {
            GitSyncDialog(
                state = state,
                onDismiss = {},
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("git-sync-commit-message").assertIsDisplayed().assertIsFocused()
    }
}

