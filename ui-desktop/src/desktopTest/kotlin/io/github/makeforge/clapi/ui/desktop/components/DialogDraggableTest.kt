package io.github.makeforge.clapi.ui.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import io.github.makeforge.clapi.ui.shared.components.draggableNoSlop
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DialogDraggableTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `plain button with moveBy`() {
        var clicked = false
        composeRule.setContent {
            Button(
                onClick = { clicked = true },
                modifier = Modifier.testTag("plain-btn"),
            ) {
                Text("Plain")
            }
        }

        composeRule.onNodeWithTag("plain-btn").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(1f, 1f))
            release()
        }
        composeRule.waitForIdle()

        assertTrue(clicked, "Plain button should fire with 1px move")
    }

    @Test
    fun `button click with micro-jitter should not be cancelled by dialog dragging`() {
        var clicked = false
        var draggedDx = 0f
        var draggedDy = 0f

        composeRule.setContent {
            Box(
                modifier = Modifier
                    .size(400.dp)
                    .draggableNoSlop { dx, dy ->
                        draggedDx += dx
                        draggedDy += dy
                    }
            ) {
                Button(
                    onClick = { clicked = true },
                    modifier = Modifier.testTag("test-btn"),
                ) {
                    Text("Click me")
                }
            }
        }

        // Simulate a physical mouse click where the user's hand jitters slightly (1px)
        composeRule.onNodeWithTag("test-btn").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(1f, 1f))
            release()
        }
        composeRule.waitForIdle()

        assertTrue(clicked, "Button onClick should have fired despite 1px micro-jitter")
        assertEquals(0f, draggedDx, "Dialog should not have dragged on micro-jitter during button click")
    }

    @Test
    fun `intentional drag past slop moves dialog by full delta`() {
        var draggedDx = 0f
        var draggedDy = 0f

        composeRule.setContent {
            Box(
                modifier = Modifier
                    .size(400.dp)
                    .testTag("dialog-box")
                    .draggableNoSlop { dx, dy ->
                        draggedDx += dx
                        draggedDy += dy
                    }
            )
        }

        composeRule.onNodeWithTag("dialog-box").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(10f, 15f))
            moveBy(Offset(5f, 5f))
            release()
        }
        composeRule.waitForIdle()

        assertEquals(15f, draggedDx, 0.01f, "Total drag dx should equal total movement past slop")
        assertEquals(20f, draggedDy, 0.01f, "Total drag dy should equal total movement past slop")
    }

    @Test
    fun `dragging past slop starting on button cancels click and drags dialog`() {
        var clicked = false
        var draggedDx = 0f
        var draggedDy = 0f

        composeRule.setContent {
            Box(
                modifier = Modifier
                    .size(400.dp)
                    .draggableNoSlop { dx, dy ->
                        draggedDx += dx
                        draggedDy += dy
                    }
            ) {
                Button(
                    onClick = { clicked = true },
                    modifier = Modifier.testTag("test-btn"),
                ) {
                    Text("Click me")
                }
            }
        }

        // Pointer down on button, moves 20px past slop
        composeRule.onNodeWithTag("test-btn").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(20f, 20f))
            release()
        }
        composeRule.waitForIdle()

        assertEquals(20f, draggedDx, 0.01f, "Dialog should drag when intentionally moved past slop")
        assertFalse(clicked, "Button should cancel click when drag takes over")
    }

    @Test
    fun `text selection in text field should not drag the dialog`() {
        var draggedDx = 0f
        var draggedDy = 0f
        var textValue by mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("Hello World 12345"))

        composeRule.setContent {
            Box(
                modifier = Modifier
                    .size(400.dp)
                    .draggableNoSlop { dx, dy ->
                        draggedDx += dx
                        draggedDy += dy
                    }
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    modifier = Modifier.testTag("test-input").size(200.dp, 40.dp),
                )
            }
        }

        // Drag across the text field to select text
        composeRule.onNodeWithTag("test-input").performMouseInput {
            moveTo(Offset(10f, 20f))
            press()
            moveTo(Offset(80f, 20f))
            release()
        }
        composeRule.waitForIdle()

        assertEquals(0f, draggedDx, "Text field selection should not drag the dialog")
    }

    @Test
    fun `dialog with detectTapGestures and draggableNoSlop should still drag from empty space`() {
        var draggedDx = 0f
        var draggedDy = 0f

        composeRule.setContent {
            Box(
                modifier = Modifier
                    .size(400.dp)
                    .draggableNoSlop { dx, dy ->
                        draggedDx += dx
                        draggedDy += dy
                    }
                    .pointerInput(Unit) { detectTapGestures { _ -> } }
                    .testTag("dialog-with-tap-guard")
            )
        }

        composeRule.onNodeWithTag("dialog-with-tap-guard").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(20f, 20f))
            release()
        }
        composeRule.waitForIdle()

        assertEquals(20f, draggedDx, 0.01f, "Dialog should drag from empty space even with detectTapGestures")
    }

    @Test
    fun `dialog top bar drags dialog but dragging dialog body does not move dialog`() {
        var draggedDx = 0f
        var draggedDy = 0f
        var closeClicked = false

        composeRule.setContent {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .size(400.dp)
                    .testTag("dialog-card")
            ) {
                io.github.makeforge.clapi.ui.shared.components.DialogTitleBar(
                    title = "Test Dialog",
                    onClose = { closeClicked = true },
                    onDrag = { dx, dy ->
                        draggedDx += dx
                        draggedDy += dy
                    },
                    testTag = "test-title-bar",
                    closeButtonTag = "test-close-btn",
                )
                Box(
                    modifier = Modifier
                        .size(400.dp, 300.dp)
                        .testTag("dialog-body")
                ) {
                    Text("Dialog Body Content")
                }
            }
        }

        // 1. Drag on dialog body: should NOT drag the dialog!
        composeRule.onNodeWithTag("dialog-body").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(30f, 30f))
            release()
        }
        composeRule.waitForIdle()

        assertEquals(0f, draggedDx, "Dragging on dialog body must NOT move the dialog")
        assertEquals(0f, draggedDy, "Dragging on dialog body must NOT move the dialog")

        // 2. Drag on dialog title bar: SHOULD drag the dialog!
        composeRule.onNodeWithTag("test-title-bar").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(25f, 15f))
            release()
        }
        composeRule.waitForIdle()

        assertEquals(25f, draggedDx, 0.01f, "Dragging on title bar must move dialog horizontally")
        assertEquals(15f, draggedDy, 0.01f, "Dragging on title bar must move dialog vertically")

        // 3. Click close button on title bar
        composeRule.onNodeWithTag("test-close-btn").performClick()
        composeRule.waitForIdle()

        assertTrue(closeClicked, "Close button on title bar should invoke onClose callback")
    }
}
