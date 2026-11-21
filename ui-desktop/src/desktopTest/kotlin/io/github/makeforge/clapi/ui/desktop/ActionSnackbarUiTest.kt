package io.github.makeforge.clapi.ui.desktop

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.state.AppState
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActionSnackbarUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun undoable_snackbar_displays_and_clicking_undo_restores_and_dismisses() {
        val state = AppState(openDefaultTab = false)
        var restored = false

        composeRule.setContent { MainScreen(state = state) }

        composeRule.runOnUiThread {
            state.snackbar.showUndoable("Deleted \"Get Users\"") {
                restored = true
                true
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("action-snackbar", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("action-snackbar-message", useUnmergedTree = true)
            .assertTextEquals("Deleted \"Get Users\"")
        composeRule.onNodeWithTag("action-snackbar-undo", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        assertTrue(restored)
        composeRule.onNodeWithTag("action-snackbar", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun showing_second_snackbar_immediately_replaces_first_so_never_two_visible() {
        val state = AppState(openDefaultTab = false)
        var firstUndone = false
        var secondUndone = false

        composeRule.setContent { MainScreen(state = state) }

        composeRule.runOnUiThread {
            state.snackbar.showUndoable("Renamed \"A\" to \"B\"") {
                firstUndone = true
                true
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("action-snackbar", useUnmergedTree = true).assertCountEquals(1)

        composeRule.runOnUiThread {
            state.snackbar.showUndoable("Moved \"Users\"") {
                secondUndone = true
                true
            }
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("action-snackbar", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNodeWithTag("action-snackbar-message", useUnmergedTree = true)
            .assertTextEquals("Moved \"Users\"")

        composeRule.onNodeWithTag("action-snackbar-undo", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertFalse(firstUndone)
        assertTrue(secondUndone)
        composeRule.onNodeWithTag("action-snackbar", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun manual_dismiss_closes_snackbar_without_invoking_undo() {
        val state = AppState(openDefaultTab = false)
        var undone = false

        composeRule.setContent { MainScreen(state = state) }

        composeRule.runOnUiThread {
            state.snackbar.showUndoable("Deleted \"Folder\"") {
                undone = true
                true
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("action-snackbar-dismiss", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertFalse(undone)
        composeRule.onNodeWithTag("action-snackbar", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun snackbar_auto_dismisses_after_configured_duration() {
        val state = AppState(openDefaultTab = false)
        state.settings.snackbarAutoDismissSec = 1

        composeRule.setContent { MainScreen(state = state) }

        composeRule.runOnUiThread {
            state.snackbar.showUndoable("Deleted \"Temp\"") { true }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("action-snackbar", useUnmergedTree = true).assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(1_200L)
        composeRule.waitForIdle()

        assertNull(state.snackbar.current)
        composeRule.onNodeWithTag("action-snackbar", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun informational_snackbar_without_undo_hides_undo_button() {
        val state = AppState(openDefaultTab = false)

        composeRule.setContent { MainScreen(state = state) }

        composeRule.runOnUiThread {
            state.snackbar.show("Copied link", durationSecOverride = 3)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("action-snackbar", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("action-snackbar-message", useUnmergedTree = true)
            .assertTextEquals("Copied link")
        composeRule.onNodeWithTag("action-snackbar-undo", useUnmergedTree = true).assertDoesNotExist()
    }
}
