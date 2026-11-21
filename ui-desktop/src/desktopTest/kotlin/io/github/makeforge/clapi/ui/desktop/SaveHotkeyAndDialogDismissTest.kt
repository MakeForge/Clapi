package io.github.makeforge.clapi.ui.desktop

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.performTextReplacement
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class)
class SaveHotkeyAndDialogDismissTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val ctrlS = KeyEvent(
        key = Key.S,
        type = KeyEventType.KeyDown,
        isCtrlPressed = true,
    )

    // ── EnvironmentEditDialog ──

    @Test
    fun envDialog_cleanDismiss_closesImmediately() {
        val state = AppState().apply {
            environments.add(EnvState("Dev"))
            openEnvEdit(0)
        }

        composeRule.setContent { MainScreen(state) }
        composeRule.onNodeWithTag("env-edit-dialog").assertIsDisplayed()

        // Click cancel when no changes were made
        composeRule.onNodeWithTag("env-cancel-button").performClick()
        composeRule.waitForIdle()

        assertFalse(state.showEnvEditDialog)
        composeRule.onNodeWithTag("dirty-close-dialog").assertDoesNotExist()
    }

    @Test
    fun envDialog_ctrlS_savesWithoutClosing() {
        val env = EnvState("Dev").apply {
            variables.add(MutableKeyValue("host", "http://old.com"))
        }
        val state = AppState().apply {
            environments.add(env)
            openEnvEdit(0)
        }

        composeRule.setContent { MainScreen(state) }
        composeRule.onNodeWithTag("env-edit-dialog").assertIsDisplayed()

        // Modify variable value
        composeRule.onNodeWithTag("env-var-value-0").performClick()
        composeRule.onNodeWithTag("env-var-value-0").performTextReplacement("http://new.com")
        composeRule.waitForIdle()

        // Press Ctrl+S on the dialog
        composeRule.onNodeWithTag("env-edit-dialog").performKeyPress(ctrlS)
        composeRule.waitForIdle()

        // Dialog must remain open!
        assertTrue(state.showEnvEditDialog)
        composeRule.onNodeWithTag("env-edit-dialog").assertIsDisplayed()
        // But changes are saved into env
        assertEquals("http://new.com", env.variables.first().value)

        // And since it was saved, dismissing now closes immediately without confirmation
        composeRule.onNodeWithTag("env-cancel-button").performClick()
        composeRule.waitForIdle()
        assertFalse(state.showEnvEditDialog)
        composeRule.onNodeWithTag("dirty-close-dialog").assertDoesNotExist()
    }

    @Test
    fun envDialog_dirtyDismiss_promptsSaveDiscardCancel() {
        val env = EnvState("Dev").apply {
            variables.add(MutableKeyValue("host", "http://old.com"))
        }
        val state = AppState().apply {
            environments.add(env)
            openEnvEdit(0)
        }

        composeRule.setContent { MainScreen(state) }
        composeRule.onNodeWithTag("env-edit-dialog").assertIsDisplayed()

        // Modify variable to make it dirty
        composeRule.onNodeWithTag("env-var-value-0").performClick()
        composeRule.onNodeWithTag("env-var-value-0").performTextReplacement("http://staging.com")
        composeRule.waitForIdle()

        // Click cancel (or outside)
        composeRule.onNodeWithTag("env-cancel-button").performClick()
        composeRule.waitForIdle()

        // Unsaved changes dialog must appear with Save, Discard, Cancel
        composeRule.onNodeWithTag("dirty-close-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("dirty-close-save").assertIsDisplayed()
        composeRule.onNodeWithTag("dirty-close-discard").assertIsDisplayed()
        composeRule.onNodeWithTag("dirty-close-cancel").assertIsDisplayed()

        // Clicking Cancel keeps the dialog open
        composeRule.onNodeWithTag("dirty-close-cancel").performClick()
        composeRule.waitForIdle()
        assertTrue(state.showEnvEditDialog)
        composeRule.onNodeWithTag("dirty-close-dialog").assertDoesNotExist()

        // Click cancel again -> click Discard
        composeRule.onNodeWithTag("env-cancel-button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dirty-close-discard").performClick()
        composeRule.waitForIdle()

        // Environment dialog closed and changes discarded
        assertFalse(state.showEnvEditDialog)
        assertEquals("http://old.com", env.variables.first().value)
    }

    // ── GlobalVariablesDialog ──

    @Test
    fun globalVarsDialog_ctrlS_savesWithoutClosing() {
        val state = AppState().apply {
            globalVariables.add(MutableKeyValue("API_HOST", "http://old.com"))
            showGlobalVariablesDialog = true
        }

        composeRule.setContent { MainScreen(state) }
        composeRule.onNodeWithTag("global-variables-dialog").assertIsDisplayed()

        // Modify value
        composeRule.onNodeWithTag("global-var-value-0").performTextReplacement("http://new.com")
        composeRule.waitForIdle()

        // Press Ctrl+S
        composeRule.onNodeWithTag("global-variables-dialog").performKeyPress(ctrlS)
        composeRule.waitForIdle()

        // Dialog stays open!
        assertTrue(state.showGlobalVariablesDialog)
        composeRule.onNodeWithTag("global-variables-dialog").assertIsDisplayed()
        assertEquals("http://new.com", state.globalVariables.first().value)

        // Clean close now
        composeRule.onNodeWithTag("global-vars-cancel").performClick()
        composeRule.waitForIdle()
        assertFalse(state.showGlobalVariablesDialog)
        composeRule.onNodeWithTag("dirty-close-dialog").assertDoesNotExist()
    }

    @Test
    fun globalVarsDialog_dirtyDismiss_promptsConfirmation() {
        val state = AppState().apply {
            globalVariables.add(MutableKeyValue("KEY", "val"))
            showGlobalVariablesDialog = true
        }

        composeRule.setContent { MainScreen(state) }
        composeRule.onNodeWithTag("global-variables-dialog").assertIsDisplayed()

        // Modify key
        composeRule.onNodeWithTag("global-var-key-0").performTextReplacement("NEW_KEY")
        composeRule.waitForIdle()

        // Cancel
        composeRule.onNodeWithTag("global-vars-cancel").performClick()
        composeRule.waitForIdle()

        // Confirmation shown
        composeRule.onNodeWithTag("dirty-close-dialog").assertIsDisplayed()

        // Click Save in confirmation
        composeRule.onNodeWithTag("dirty-close-save").performClick()
        composeRule.waitForIdle()

        assertFalse(state.showGlobalVariablesDialog)
        assertEquals("NEW_KEY", state.globalVariables.first().key)
    }

    // ── SelectProtoFileDialog ──

    @Test
    fun protoFileDialog_ctrlS_savesWithoutClosing() {
        var savedPath = ""
        var dismissed = false

        composeRule.setContent {
            io.github.makeforge.clapi.ui.shared.components.SelectProtoFileDialog(
                currentProtoFilePath = "/proto/old.proto",
                onSaveWithClose = { path, _, close ->
                    savedPath = path
                    if (close) dismissed = true
                },
                onDismiss = { dismissed = true },
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("select-proto-file-dialog").assertIsDisplayed()

        // Modify path
        composeRule.onNodeWithTag("proto-dialog-path-input").performTextReplacement("/proto/new.proto")
        composeRule.waitForIdle()

        // Press Ctrl+S
        composeRule.onNodeWithTag("select-proto-file-dialog").performKeyPress(ctrlS)
        composeRule.waitForIdle()

        assertEquals("/proto/new.proto", savedPath)
        assertFalse(dismissed, "Ctrl+S in SelectProtoFileDialog must not close the dialog")
        composeRule.onNodeWithTag("select-proto-file-dialog").assertIsDisplayed()

        // Clean cancel now closes
        composeRule.onNodeWithTag("proto-dialog-cancel").performClick()
        composeRule.waitForIdle()
        assertTrue(dismissed)
    }

    @Test
    fun protoFileDialog_dirtyDismiss_promptsConfirmation() {
        var dismissed = false

        composeRule.setContent {
            io.github.makeforge.clapi.ui.shared.components.SelectProtoFileDialog(
                currentProtoFilePath = "/proto/old.proto",
                onSave = {},
                onDismiss = { dismissed = true },
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("proto-dialog-path-input").performTextReplacement("/proto/changed.proto")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("proto-dialog-cancel").performClick()
        composeRule.waitForIdle()

        // Confirmation shown
        composeRule.onNodeWithTag("dirty-close-dialog").assertIsDisplayed()
        assertFalse(dismissed)

        // Cancel confirmation keeps dialog open
        composeRule.onNodeWithTag("dirty-close-cancel").performClick()
        composeRule.waitForIdle()
        assertFalse(dismissed)
        composeRule.onNodeWithTag("select-proto-file-dialog").assertIsDisplayed()
    }

    // ── VariableEditorPopup ──

    @Test
    fun variableEditorPopup_ctrlS_savesWithoutClosing() {
        val env = EnvState("Dev").apply {
            variables.add(MutableKeyValue("MY_VAR", "initial"))
        }
        val state = AppState().apply {
            environments.add(env)
            selectedEnvIndex = 0
        }
        var dismissed = false

        composeRule.setContent {
            io.github.makeforge.clapi.ui.shared.components.VariableEditorPopup(
                variableName = "MY_VAR",
                state = state,
                onDismiss = { dismissed = true },
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("variable-editor-popup").assertIsDisplayed()

        // Change value
        composeRule.onNodeWithTag("variable-popup-value-input").performTextReplacement("updated-value")
        composeRule.waitForIdle()

        // Press Ctrl+S
        composeRule.onNodeWithTag("variable-editor-popup").performKeyPress(ctrlS)
        composeRule.waitForIdle()

        assertEquals("updated-value", env.variables.first().value)
        assertFalse(dismissed, "Ctrl+S in VariableEditorPopup must not close the popup")
        composeRule.onNodeWithTag("variable-editor-popup").assertIsDisplayed()

        // Dismiss now
        composeRule.onNodeWithTag("variable-popup-close").performClick()
        composeRule.waitForIdle()
        assertTrue(dismissed)
    }

    // ── Active Request Tab Save Hotkey ──

    @Test
    fun requestEditor_ctrlS_triggersSave() {
        val state = AppState()
        state.openRequest(
            requestId = "req-test-1",
            name = "Test Request",
            method = HttpMethodType.GET,
            url = "https://api.example.com/items",
        )
        val tab = state.activeTab!!
        tab.url = "https://api.example.com/modified"
        tab.markDirty()
        assertTrue(tab.isDirty)

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("url-input").performClick()
        composeRule.onNodeWithTag("url-input").performKeyPress(ctrlS)
        composeRule.waitUntil(timeoutMillis = 8_000) { !tab.isDirty }

        assertFalse(tab.isDirty)
    }
}
