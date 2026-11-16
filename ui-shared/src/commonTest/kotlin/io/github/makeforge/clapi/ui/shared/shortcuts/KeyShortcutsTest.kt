package io.github.makeforge.clapi.ui.shared.shortcuts

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class)
class KeyShortcutsTest {

    @Test
    fun testSaveHotkeyMatchesCtrlS() {
        val event = KeyEvent(
            key = Key.S,
            type = KeyEventType.KeyDown,
            isCtrlPressed = true,
        )
        assertTrue(AppHotkeys.Save.matches(event))
    }

    @Test
    fun testSaveHotkeyMatchesMetaS() {
        val event = KeyEvent(
            key = Key.S,
            type = KeyEventType.KeyDown,
            isMetaPressed = true,
        )
        assertTrue(AppHotkeys.Save.matches(event))
    }

    @Test
    fun testSaveHotkeyFailsWithoutModifier() {
        val event = KeyEvent(
            key = Key.S,
            type = KeyEventType.KeyDown,
        )
        assertFalse(AppHotkeys.Save.matches(event))
    }

    @Test
    fun testSaveHotkeyFailsWithShiftOrAlt() {
        val shiftS = KeyEvent(
            key = Key.S,
            type = KeyEventType.KeyDown,
            isCtrlPressed = true,
            isShiftPressed = true,
        )
        assertFalse(AppHotkeys.Save.matches(shiftS))

        val altS = KeyEvent(
            key = Key.S,
            type = KeyEventType.KeyDown,
            isCtrlPressed = true,
            isAltPressed = true,
        )
        assertFalse(AppHotkeys.Save.matches(altS))
    }

    @Test
    fun testSaveHotkeyIgnoresKeyUp() {
        val event = KeyEvent(
            key = Key.S,
            type = KeyEventType.KeyUp,
            isCtrlPressed = true,
        )
        assertFalse(AppHotkeys.Save.matches(event))
    }

    @Test
    fun testHotkeyScopeExecution() {
        val scope = HotkeyScope()
        var saved = false
        var found = false

        scope.on(AppHotkeys.Save) { saved = true }
        scope.on(AppHotkeys.Find) { found = true }

        val saveEvent = KeyEvent(key = Key.S, type = KeyEventType.KeyDown, isCtrlPressed = true)
        val handled = scope.handle(saveEvent)

        assertTrue(handled)
        assertTrue(saved)
        assertFalse(found)

        val findEvent = KeyEvent(key = Key.F, type = KeyEventType.KeyDown, isCtrlPressed = true)
        val handledFind = scope.handle(findEvent)

        assertTrue(handledFind)
        assertTrue(found)
    }

    @Test
    fun testConditionalHotkeyHandling() {
        val scope = HotkeyScope()
        var callCount = 0

        scope.onConditional(AppHotkeys.Save) {
            callCount++
            callCount > 1 // Only handles on 2nd call
        }

        val event = KeyEvent(key = Key.S, type = KeyEventType.KeyDown, isCtrlPressed = true)
        assertFalse(scope.handle(event))
        assertEquals(1, callCount)

        assertTrue(scope.handle(event))
        assertEquals(2, callCount)
    }

    @Test
    fun testCustomKeyShortcut() {
        val custom = KeyShortcut(Key.D, ctrlOrMeta = true, shift = true)
        val matchingEvent = KeyEvent(
            key = Key.D,
            type = KeyEventType.KeyDown,
            isCtrlPressed = true,
            isShiftPressed = true,
        )
        assertTrue(custom.matches(matchingEvent))

        val nonMatching = KeyEvent(
            key = Key.D,
            type = KeyEventType.KeyDown,
            isCtrlPressed = true,
        )
        assertFalse(custom.matches(nonMatching))
    }
}
