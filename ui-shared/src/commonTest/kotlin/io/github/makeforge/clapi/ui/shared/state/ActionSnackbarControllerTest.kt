package io.github.makeforge.clapi.ui.shared.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActionSnackbarControllerTest {

    @Test
    fun show_sets_current_snackbar_and_assigns_monotonic_ids() {
        val controller = ActionSnackbarController()

        val firstId = controller.show("Deleted \"Users\"")
        assertNotNull(firstId)
        assertEquals("Deleted \"Users\"", controller.current?.message)
        assertNull(controller.current?.actionLabel)
        assertNull(controller.current?.onAction)

        val secondId = controller.show("Deleted \"Users\"")
        assertNotNull(secondId)
        assertNotEquals(firstId, secondId)
        assertEquals(secondId, controller.current?.id)
    }

    @Test
    fun show_immediately_replaces_existing_snackbar() {
        val controller = ActionSnackbarController()
        var firstUndone = false
        var secondUndone = false

        val id1 = controller.showUndoable("Renamed \"A\" to \"B\"") {
            firstUndone = true
            true
        }!!
        val id2 = controller.showUndoable("Deleted \"C\"") {
            secondUndone = true
            true
        }!!

        assertEquals(id2, controller.current?.id)
        assertEquals("Deleted \"C\"", controller.current?.message)

        // Triggering the replaced snackbar ID is a no-op
        controller.triggerAction(id1)
        assertFalse(firstUndone)
        assertEquals(id2, controller.current?.id)

        // Triggering the active snackbar ID succeeds and dismisses
        controller.triggerAction(id2)
        assertTrue(secondUndone)
        assertNull(controller.current)
    }

    @Test
    fun triggerAction_executes_at_most_once_and_dismisses_immediately() {
        val controller = ActionSnackbarController()
        var undoCount = 0
        val id = controller.showUndoable("Deleted \"Item\"") {
            undoCount++
            true
        }!!

        controller.triggerAction(id)
        controller.triggerAction(id)

        assertEquals(1, undoCount)
        assertNull(controller.current)
    }

    @Test
    fun stale_dismiss_does_not_close_newer_snackbar() {
        val controller = ActionSnackbarController()
        val id1 = controller.show("First")!!
        val id2 = controller.show("Second")!!

        controller.dismiss(id1)
        assertEquals(id2, controller.current?.id)

        controller.dismiss(id2)
        assertNull(controller.current)
    }

    @Test
    fun undo_failure_or_exception_shows_error_snackbar_without_crashing() {
        val controller = ActionSnackbarController()
        val idFalse = controller.showUndoable(
            message = "Deleted \"Item\"",
            failureMessage = "Could not undo action",
        ) { false }!!

        controller.triggerAction(idFalse)
        assertNotNull(controller.current)
        assertTrue(controller.current!!.isError)
        assertEquals("Could not undo action", controller.current!!.message)
        assertNull(controller.current!!.onAction)

        val idThrow = controller.showUndoable(
            message = "Deleted \"Item 2\"",
            failureMessage = "Undo failed cleanly",
        ) { error("boom") }!!

        controller.triggerAction(idThrow)
        assertNotNull(controller.current)
        assertTrue(controller.current!!.isError)
        assertEquals("Undo failed cleanly", controller.current!!.message)
    }

    @Test
    fun blank_messages_are_ignored_and_duration_override_is_clamped() {
        val controller = ActionSnackbarController()
        assertNull(controller.show("   "))
        assertNull(controller.current)

        controller.show("Saved", durationSecOverride = 999)
        assertEquals(MAX_SNACKBAR_AUTO_DISMISS_SEC, controller.current?.durationSecOverride)

        controller.show("Saved", durationSecOverride = 0)
        assertEquals(MIN_SNACKBAR_AUTO_DISMISS_SEC, controller.current?.durationSecOverride)
    }
}
