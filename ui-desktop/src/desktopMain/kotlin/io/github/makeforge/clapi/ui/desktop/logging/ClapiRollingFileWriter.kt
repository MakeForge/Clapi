package io.github.makeforge.clapi.ui.desktop.logging

import org.tinylog.core.LogEntry
import org.tinylog.core.LogEntryValue
import org.tinylog.writers.RollingFileWriter
import org.tinylog.writers.Writer
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Custom Tinylog [Writer] that dynamically delegates to a [RollingFileWriter]
 * pointing to the currently active Clapi log directory.
 *
 * This allows [LogDirectoryConfigurator] to redirect file logging at runtime
 * (even if Tinylog was previously initialized and frozen during test suite runs).
 */
class ClapiRollingFileWriter(
    properties: Map<String, String> = emptyMap(),
) : Writer {

    companion object {
        @Volatile
        private var activeInstance: ClapiRollingFileWriter? = null
        private val lock = ReentrantLock()

        /**
         * Reconfigures rolling file logging to output into [logDir].
         */
        fun reconfigure(logDir: File) {
            lock.withLock {
                activeInstance?.setupDelegate(logDir)
            }
        }

        /**
         * Flushes the underlying rolling file writer immediately.
         */
        fun flushCurrent() {
            lock.withLock {
                activeInstance?.flush()
            }
        }
    }

    private val baseProperties: MutableMap<String, String> = HashMap(properties)
    @Volatile
    private var delegate: RollingFileWriter? = null

    init {
        lock.withLock {
            activeInstance = this
            val currentDir = System.getProperty("clapi.log.dir")?.let { File(it) }
            if (currentDir != null) {
                setupDelegate(currentDir)
            } else if (baseProperties.containsKey("file")) {
                runCatching {
                    delegate = RollingFileWriter(baseProperties)
                }
            }
        }
    }

    private fun setupDelegate(logDir: File) {
        logDir.mkdirs()
        val old = delegate
        val props = HashMap(baseProperties)
        props["file"] = "${logDir.absolutePath}/clapi.{date:yyyy-MM-dd}.{count}.log"
        props["latest"] = "${logDir.absolutePath}/clapi.log"
        if (!props.containsKey("charset")) props["charset"] = "UTF-8"
        if (!props.containsKey("policies")) props["policies"] = "daily, size: 10mb"
        if (!props.containsKey("backups")) props["backups"] = "5"
        if (!props.containsKey("convert")) props["convert"] = "gzip"
        if (!props.containsKey("format")) {
            props["format"] = "{date: yyyy-MM-dd HH:mm:ss.SSS} [{thread}] {level}: {class-name} - {message}"
        }
        runCatching {
            delegate = RollingFileWriter(props)
            old?.close()
        }
    }

    override fun getRequiredLogEntryValues(): Collection<LogEntryValue> = setOf(
        LogEntryValue.DATE,
        LogEntryValue.LEVEL,
        LogEntryValue.CLASS,
        LogEntryValue.MESSAGE,
        LogEntryValue.EXCEPTION,
    )

    override fun write(logEntry: LogEntry) {
        lock.withLock {
            runCatching { delegate?.write(logEntry) }
        }
    }

    override fun flush() {
        lock.withLock {
            runCatching { delegate?.flush() }
        }
    }

    override fun close() {
        lock.withLock {
            runCatching { delegate?.close() }
            delegate = null
        }
    }
}
