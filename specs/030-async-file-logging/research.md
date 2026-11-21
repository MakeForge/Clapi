# Phase 0 Research: Asynchronous Persistent File & Diagnostics Logging

**Feature**: `030-async-file-logging`  
**Date**: 2026-11-18  
**Status**: Completed  

---

## 1. Logging Framework Selection

### Decision
Adopt **Tinylog 2 (`tinylog-impl`, `tinylog-api`, `slf4j-tinylog`)** paired with **`slf4j-api`** and **`io.github.oshai:kotlin-logging`**.

### Rationale
1. **Asynchronous Background Processing**: Tinylog contains a native lock-free writing thread (`writingthread = true`) with adjustable thread priority (`writingthread.priority = 1`). Log calls from the UI or network coroutines deposit lightweight entry objects into a lock-free queue and return immediately.
2. **Minimal Footprint**: The entire Tinylog + SLF4J stack is ~200 KB in total JAR size, compared to Logback Classic (~2.5 MB) or Log4j2 (~4 MB).
3. **Instant Startup**: Configuration is parsed via a simple key-value `tinylog.properties` file or programmatic API with zero XML parsing or classpath-wide scanning, avoiding launch latency for the Compose desktop window.
4. **SLF4J Compatibility**: `slf4j-tinylog` acts as an SLF4J binding provider, allowing existing and third-party libraries (Ktor, gRPC, Netty) to direct all logging to the same unified pipeline without duplicate log engines.
5. **KMP Friendly Facade**: `kotlin-logging` in `commonMain` provides zero-allocation lazy evaluation (`logger.debug { expensiveString() }`), mapping directly to SLF4J on desktop JVM.

### Alternatives Considered
- **Logback Classic**: Mature, but heavy (~2.5 MB), requires XML parsing (`logback.xml`), and requires an explicit `AsyncAppender` wrapper to prevent disk I/O from blocking caller threads.
- **Pure Custom Coroutine Logging Engine**: Avoids third-party libraries, but requires writing, testing, and maintaining rolling file mechanics, daily calendar boundaries, gzip compression, retention cleaners, and SLF4J provider bridges from scratch.

---

## 2. Persistent Storage Location & CLI Directory Overrides

### Decision
Store logs in `<storageDir>/logs/clapi.log`, where `<storageDir>` is dynamically resolved from CLI arguments (`--dir <path>`), JVM property (`-Dclapi.dir`), environment variable (`CLAPI_DIR`), or OS user home fallback (`~/.clapi`).

### Rationale
- Clapi already defines canonical directory resolution in `StdioMcpServerRunner.resolveStorageDirectory(args)`.
- Setting a system property (e.g. `System.setProperty("clapi.log.dir", File(storageDir, "logs").absolutePath)`) in `Main.kt` *before* loading logger classes allows `tinylog.properties` to dynamically reference `${clapi.log.dir}` for rolling file paths.
- On launch, the logging initialization ensures `<storageDir>/logs/` is created via `.mkdirs()`.

### Retention & Rolling Policies
- **Active Log File**: `${clapi.log.dir}/clapi.log` (alias/latest symlink or base file)
- **Archive Pattern**: `${clapi.log.dir}/clapi-{date:yyyy-MM-dd}.{count}.log.gz`
- **Trigger Policies**: Size threshold of `10mb` and `daily` calendar boundary.
- **Retention Count**: `backups = 5` (purges archives older than 5 generations, capping total disk footprint to ~50–60 MB).
- **Gzip Compression**: `convert = gzip` compresses rotated historical files, reducing disk footprint by up to 90%.

---

## 3. Safe Stdio Protocol Isolation for Headless MCP Mode

### Decision
Configure Tinylog's console writer to route exclusively to `stderr`:
```properties
writer1 = console
writer1.stream = err
```

### Rationale
- When Clapi runs with `--mcp`, standard input (`stdin`) and standard output (`stdout`) are reserved exclusively for newline-delimited JSON-RPC 2.0 messages.
- Routing console log output strictly to standard error (`System.err` / `stderr`) ensures that diagnostic and informational logs never inject arbitrary text into `stdout`, preventing JSON-RPC parsing failures in connected MCP clients.

---

## 4. Compose Desktop UI Diagnostic Console Integration

### Decision
Implement a custom `org.tinylog.writers.Writer` (`UiDiagnosticsLogWriter`) discovered via `ServiceLoader`. The writer forwards formatted log entries from Tinylog's background writing thread into a thread-safe, bounded memory buffer (`DiagnosticsLogState`).

### Rationale
- **Thread Decoupling**: Because Tinylog’s `writingthread` runs writer invocations asynchronously on its background thread, parsing and formatting log entries occurs completely off the UI thread.
- **Batching & Snapshot Safety**: To avoid mutating Compose `SnapshotStateList` continuously on high-frequency log streams, `UiDiagnosticsLogWriter` deposits entries into a thread-safe bounded buffer and flushes updates to `DiagnosticsLogState` using a batched or throttled schedule.
- **Existing `state.log(...)` Bridge**: Existing call sites (`state.log(...)`, `state.logNetworkEvent(...)`) will be updated to emit directly to `KotlinLogging.logger("App")` and `KotlinLogging.logger("Network")`. This immediately routes all application events through Tinylog's rolling file appender and back into the UI console, giving seamless backwards compatibility without changing UI view components.

---

## 5. Crash Reporting & Uncaught Exception Handling

### Decision
Install a global `Thread.setDefaultUncaughtExceptionHandler` in `Main.kt` that logs unhandled throwables via the SLF4J logger at `ERROR` level, followed by an explicit Tinylog writing thread flush before JVM exit.

### Rationale
- Currently, unhandled exceptions on coroutine dispatchers or the AWT Event Dispatch Thread print to stderr and may terminate silently.
- Logging uncaught throwables to Tinylog guarantees that fatal crash reports, stack traces, and active thread identifiers are persisted to `clapi.log` on disk.
- Calling `ProviderRegistry.getLoggingProvider().shutdown()` flushes the internal queue to disk before process death.

---

## 6. ProGuard & Native Packaging Compatibility

### Decision
Include keep rules in `ui-desktop/proguard-rules.pro` for Tinylog internal reflection and the custom `ServiceLoader` writer:
```proguard
-keep class org.tinylog.** { *; }
-keep class io.github.makeforge.clapi.ui.desktop.logging.UiDiagnosticsLogWriter { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
```

### Rationale
Compose Desktop native distribution tasks (`packageDmg`, `packageMsi`, `packageDeb`) strip unused classes when obfuscation/minification is enabled. Preserving Tinylog packages and the service loader registration ensures runtime reflection does not fail inside packaged binary distributions.
