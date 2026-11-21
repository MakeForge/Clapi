package io.github.makeforge.clapi.ui.desktop.logging

import org.tinylog.core.LogEntry
import org.tinylog.core.LogEntryValue
import org.tinylog.writers.Writer

class UiDiagnosticsLogWriter(properties: Map<String, String> = emptyMap()) : Writer {

    override fun getRequiredLogEntryValues(): Collection<LogEntryValue> = setOf(
        LogEntryValue.DATE,
        LogEntryValue.LEVEL,
        LogEntryValue.CLASS,
        LogEntryValue.MESSAGE,
        LogEntryValue.EXCEPTION,
    )

    override fun write(logEntry: LogEntry) {
        UiLogDispatcher.dispatch(logEntry)
    }

    override fun flush() {
        UiLogDispatcher.flush()
    }

    override fun close() {
        UiLogDispatcher.close()
    }
}
