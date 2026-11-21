package io.github.makeforge.clapi.ui.desktop.logging

import io.github.makeforge.clapi.ui.shared.state.ConsoleEntry
import io.github.makeforge.clapi.ui.shared.state.DiagnosticsLogState
import io.github.makeforge.clapi.ui.shared.state.LogLevel
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiDiagnosticsLogWriterTest {

    private lateinit var diagnostics: DiagnosticsLogState

    @Before
    fun setup() {
        UiLogDispatcher.unbind()
        diagnostics = DiagnosticsLogState()
    }

    @After
    fun tearDown() {
        UiLogDispatcher.unbind()
    }

    @Test
    fun testBatchAppendAndBoundedSize() {
        val entries = (1..50).map { i ->
            ConsoleEntry(message = "Message $i", level = LogLevel.INFO)
        }
        diagnostics.appendBatch(entries)

        assertEquals(50, diagnostics.consoleLogs.size)
        assertEquals("Message 50", diagnostics.consoleLogs.first().message)

        // Test bounding: add 1,200 entries to exceed MAX_CONSOLE_LOGS (1,000)
        val overflow = (51..1200).map { i ->
            ConsoleEntry(message = "Message $i", level = LogLevel.INFO)
        }
        diagnostics.appendBatch(overflow)

        assertTrue(
            diagnostics.consoleLogs.size <= DiagnosticsLogState.MAX_CONSOLE_LOGS,
            "Console logs should remain bounded to ${DiagnosticsLogState.MAX_CONSOLE_LOGS}, got ${diagnostics.consoleLogs.size}"
        )
        assertEquals("Message 1200", diagnostics.consoleLogs.first().message)
    }
}
