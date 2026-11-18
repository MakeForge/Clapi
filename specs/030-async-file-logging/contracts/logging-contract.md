# Interface Contracts: Asynchronous Persistent Logging

**Feature**: `030-async-file-logging`  
**Date**: 2026-11-18  
**Status**: Draft  

---

## 1. Public Facade Contracts

### 1.1 Core Multiplatform Logger Contract (`commonMain`)
To provide zero-allocation logging in common modules (`core-network`, `core-scripting`, `ui-shared`), the system exposes:

```kotlin
package io.github.makeforge.clapi.core.logging

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Factory for acquiring named KLogger instances across common and desktop modules.
 */
object AppLogging {
    fun logger(name: String): KLogger = KotlinLogging.logger(name)
    inline fun <reified T : Any> logger(): KLogger = KotlinLogging.logger(T::class.simpleName ?: "App")
}
```

### 1.2 Network Logger Bridge Contract
The existing `NetworkLogger` interface in `core-network` is implemented to bridge seamlessly to SLF4J / Tinylog:

```kotlin
package io.github.makeforge.clapi.core.network

import io.github.oshai.kotlinlogging.KotlinLogging

class Slf4jNetworkLogger(tag: String = "Network") : NetworkLogger {
    private val logger = KotlinLogging.logger(tag)

    override fun debug(message: String) {
        logger.debug { message }
    }

    override fun info(message: String) {
        logger.info { message }
    }

    override fun error(message: String, throwable: Throwable?) {
        if (throwable != null) {
            logger.error(throwable) { message }
        } else {
            logger.error { message }
        }
    }
}
```

---

## 2. Tinylog Writer SPI Contract

Tinylog uses Java's `java.util.ServiceLoader` SPI for custom writers. The bridge to Compose UI implements `org.tinylog.writers.Writer`:

```kotlin
package io.github.makeforge.clapi.ui.desktop.logging

import org.tinylog.core.LogEntry
import org.tinylog.core.LogEntryValue
import org.tinylog.writers.Writer

class UiDiagnosticsLogWriter(properties: Map<String, String>) : Writer {

    override fun getRequiredLogEntryValues(): Collection<LogEntryValue> = setOf(
        LogEntryValue.DATE,
        LogEntryValue.LEVEL,
        LogEntryValue.CLASS,
        LogEntryValue.MESSAGE,
        LogEntryValue.EXCEPTION,
    )

    override fun write(logEntry: LogEntry) {
        // Enqueue or forward entry to the thread-safe UI log store
        UiLogDispatcher.dispatch(logEntry)
    }

    override fun flush() {
        UiLogDispatcher.flush()
    }

    override fun close() {
        UiLogDispatcher.close()
    }
}
```

### SPI Registration
File: `META-INF/services/org.tinylog.writers.Writer`
```text
io.github.makeforge.clapi.ui.desktop.logging.UiDiagnosticsLogWriter
```

---

## 3. Configuration Contract (`tinylog.properties`)

```properties
# ── Global Severity ──────────────────────────────────────────
level = info

# Suppress noisy internal library heartbeats / chunk traces
level@io.ktor = warn
level@io.netty = warn
level@io.grpc = warn

# ── Asynchronous Execution ───────────────────────────────────
writingthread = true
writingthread.priority = 1

# ── Output 1: Stderr Console (MCP-safe) ──────────────────────
writer1 = console
writer1.stream = err
writer1.format = {date: HH:mm:ss.SSS} [{thread}] {level}: {class-name} - {message}

# ── Output 2: Persistent Rolling File ────────────────────────
writer2 = rolling file
writer2.file = ${clapi.log.dir}/clapi.{date:yyyy-MM-dd}.{count}.log
writer2.latest = ${clapi.log.dir}/clapi.log
writer2.charset = UTF-8
writer2.buffered = true
writer2.policies = daily, size: 10mb
writer2.backups = 5
writer2.convert = gzip
writer2.format = {date: yyyy-MM-dd HH:mm:ss.SSS} [{thread}] {level}: {class-name} - {message}

# ── Output 3: Live In-App Compose UI Buffer ───────────────────
writer3 = io.github.makeforge.clapi.ui.desktop.logging.UiDiagnosticsLogWriter
writer3.level = info
```
