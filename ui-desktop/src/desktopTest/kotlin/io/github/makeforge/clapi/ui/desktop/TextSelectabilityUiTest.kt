package io.github.makeforge.clapi.ui.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.github.makeforge.clapi.ui.shared.components.ConfirmDeleteDialog
import io.github.makeforge.clapi.ui.shared.components.DirtyCloseDialog
import io.github.makeforge.clapi.ui.shared.components.DirtyMultiCloseDialog
import io.github.makeforge.clapi.ui.shared.components.ErrorMessageDialog
import io.github.makeforge.clapi.ui.shared.components.OperationProgressDialog
import io.github.makeforge.clapi.ui.shared.components.SelectProtoFileDialog
import org.junit.Rule
import org.junit.Test

class TextSelectabilityUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun errorMessageDialog_rendersTitleAndMessage() {
        composeRule.setContent {
            ErrorMessageDialog(
                title = "Network Failure",
                message = "Connection refused at localhost:8080",
                onDismiss = {},
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("error-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Network Failure").assertIsDisplayed()
        composeRule.onNodeWithText("Connection refused at localhost:8080").assertIsDisplayed()
        composeRule.onNodeWithTag("error-dialog-ok").assertIsDisplayed()
    }

    @Test
    fun confirmDeleteDialog_rendersTitleAndMessage() {
        composeRule.setContent {
            ConfirmDeleteDialog(
                title = "Delete Request?",
                message = "Are you sure you want to permanently delete this request?",
                onConfirm = {},
                onDismiss = {},
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Delete Request?").assertIsDisplayed()
        composeRule.onNodeWithText("Are you sure you want to permanently delete this request?").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm-cancel-button").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm-ok-button").assertIsDisplayed()
    }

    @Test
    fun dirtyCloseDialog_rendersTitleAndMessage() {
        composeRule.setContent {
            DirtyCloseDialog(
                title = "Unsaved Changes",
                message = "Save your changes before closing?",
                onSave = {},
                onDiscard = {},
                onCancel = {},
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dirty-close-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Unsaved Changes").assertIsDisplayed()
        composeRule.onNodeWithText("Save your changes before closing?").assertIsDisplayed()
        composeRule.onNodeWithTag("dirty-close-cancel").assertIsDisplayed()
        composeRule.onNodeWithTag("dirty-close-discard").assertIsDisplayed()
        composeRule.onNodeWithTag("dirty-close-save").assertIsDisplayed()
    }

    @Test
    fun dirtyMultiCloseDialog_rendersCountAndMessage() {
        composeRule.setContent {
            DirtyMultiCloseDialog(
                dirtyCount = 3,
                onSaveAll = {},
                onDiscardAll = {},
                onCancel = {},
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dirty-multi-close-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Unsaved Changes").assertIsDisplayed()
        composeRule.onNodeWithTag("dirty-multi-cancel").assertIsDisplayed()
        composeRule.onNodeWithTag("dirty-multi-discard").assertIsDisplayed()
        composeRule.onNodeWithTag("dirty-multi-save-all").assertIsDisplayed()
    }

    @Test
    fun operationProgressDialog_rendersTitleAndMessage() {
        composeRule.setContent {
            OperationProgressDialog(
                title = "Importing Collections",
                message = "Parsing OpenAPI schema...",
                onCancel = {},
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("operation-progress-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Importing Collections").assertIsDisplayed()
        composeRule.onNodeWithText("Parsing OpenAPI schema...").assertIsDisplayed()
        composeRule.onNodeWithTag("operation-progress-cancel").assertIsDisplayed()
    }

    @Test
    fun selectProtoFileDialog_rendersErrorBanner() {
        composeRule.setContent {
            SelectProtoFileDialog(
                currentProtoFilePath = "/invalid/path.proto",
                errorMessage = "Failed to parse syntax for proto file",
                onDismiss = {},
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("proto-dialog-error").assertIsDisplayed()
        composeRule.onNodeWithText("Failed to parse syntax for proto file").assertIsDisplayed()
    }
}
