package io.github.makeforge.clapi.ui.desktop.logging

import io.github.makeforge.clapi.ui.shared.state.ConsoleEntry
import io.github.makeforge.clapi.ui.shared.state.DiagnosticsLogState
import io.github.makeforge.clapi.ui.shared.state.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.tinylog.Level
import org.tinylog.core.LogEntry
import java.util.concurrent.ConcurrentLinkedQueue

object UiLogDispatcher {
    @Volatile
    private var diagnosticsState: DiagnosticsLogState? = null
    private val pendingQueue = ConcurrentLinkedQueue<ConsoleEntry>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile
    private var isFlushScheduled = false

    fun bind(state: DiagnosticsLogState) {
        diagnosticsState = state
        drainQueue()
    }

    fun clear() {
        pendingQueue.clear()
    }

    fun unbind() {
        diagnosticsState = null
        pendingQueue.clear()
    }

    fun dispatch(logEntry: LogEntry) {
        val level = when (logEntry.level) {
            Level.ERROR -> LogLevel.ERROR
            Level.WARN -> LogLevel.WARNING
            Level.INFO -> LogLevel.INFO
            Level.DEBUG, Level.TRACE -> LogLevel.INFO
            else -> LogLevel.INFO
        }
        val prefix = logEntry.className?.substringAfterLast('.') ?: "App"
        val baseMsg = logEntry.message ?: ""
        val exMsg = logEntry.exception?.let { " - ${it.message ?: it::class.simpleName}" } ?: ""
        val message = "[$prefix] $baseMsg$exMsg"
        val timestamp = runCatching { logEntry.timestamp?.toDate()?.time }.getOrNull() ?: System.currentTimeMillis()

        val entry = ConsoleEntry(
            message = message,
            level = level,
            timestamp = timestamp,
        )

        pendingQueue.add(entry)
        while (pendingQueue.size > DiagnosticsLogState.MAX_CONSOLE_LOGS) {
            pendingQueue.poll()
        }

        if (diagnosticsState != null) {
            scheduleBatchDrain()
        }
    }

    private fun scheduleBatchDrain() {
        if (isFlushScheduled) return
        isFlushScheduled = true
        scope.launch {
            delay(50)
            isFlushScheduled = false
            drainQueue()
        }
    }

    fun drainQueue() {
        val state = diagnosticsState ?: return
        val batch = mutableListOf<ConsoleEntry>()
        while (true) {
            val entry = pendingQueue.poll() ?: break
            batch.add(entry)
        }
        if (batch.isNotEmpty()) {
            state.appendBatch(batch)
        }
    }

    fun flush() {
        drainQueue()
    }

    fun close() {
        drainQueue()
    }
}
