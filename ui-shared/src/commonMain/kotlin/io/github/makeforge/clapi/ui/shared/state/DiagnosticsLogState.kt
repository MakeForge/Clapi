package io.github.makeforge.clapi.ui.shared.state

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Encapsulates bounded diagnostic log buffers (`consoleLogs` and `networkEventLogs`)
 * to prevent unbounded heap growth during long-running sessions.
 */
class DiagnosticsLogState {
    val consoleLogs: SnapshotStateList<ConsoleEntry> = mutableStateListOf()
    val networkEventLogs: SnapshotStateList<ConsoleEntry> = mutableStateListOf()

    fun log(message: String, level: LogLevel = LogLevel.INFO) {
        prependBounded(consoleLogs, ConsoleEntry(message, level), MAX_CONSOLE_LOGS)
    }

    fun appendBatch(entries: List<ConsoleEntry>) {
        if (entries.isEmpty()) return
        val toAdd = entries.takeLast(MAX_CONSOLE_LOGS).reversed()
        val totalAfter = consoleLogs.size + toAdd.size
        if (totalAfter > MAX_CONSOLE_LOGS) {
            val overflow = totalAfter - MAX_CONSOLE_LOGS
            val removeCount = minOf(overflow, consoleLogs.size)
            if (removeCount > 0) {
                consoleLogs.removeRange(consoleLogs.size - removeCount, consoleLogs.size)
            }
        }
        consoleLogs.addAll(0, toAdd)
    }

    fun logNetworkEvent(
        message: String,
        level: LogLevel = LogLevel.INFO,
        echoToConsole: Boolean = true,
    ) {
        val entry = ConsoleEntry(message, level)
        prependBounded(networkEventLogs, entry, MAX_NETWORK_EVENT_LOGS)
        if (echoToConsole) {
            prependBounded(consoleLogs, entry, MAX_CONSOLE_LOGS)
        }
    }

    private fun <T> prependBounded(
        list: SnapshotStateList<T>,
        item: T,
        maxEntries: Int,
    ) {
        if (list.size >= maxEntries) {
            list.removeRange(maxEntries - 1, list.size)
        }
        list.add(0, item)
    }

    companion object {
        const val MAX_CONSOLE_LOGS = 1_000
        const val MAX_NETWORK_EVENT_LOGS = 1_000
        const val MAX_HISTORY_ITEMS = 500
    }
}
