# Quickstart Validation Guide: Asynchronous Persistent File Logging

**Feature**: `030-async-file-logging`  
**Date**: 2026-11-18  
**Status**: Draft  

This guide provides end-to-end scenarios to validate asynchronous logging, persistent rolling file storage, in-app diagnostics, and protocol safety.

---

## Prerequisites & Setup

Ensure the project builds cleanly:
```bash
./gradlew :ui-desktop:assemble
```

---

## Validation Scenarios

### Scenario 1: Verifying Non-Blocking Performance Under Burst Logging

**Objective**: Verify that high-throughput logging executes off the main thread with zero UI freezing or frame drops.

1. **Execute Performance Benchmark Test**:
   ```bash
   ./gradlew :ui-desktop:desktopTest --tests "io.github.makeforge.clapi.ui.desktop.logging.LoggingPerformanceBenchmarkTest"
   ```
2. **Expected Outcome**:
   - 10,000 log events dispatched across 10 concurrent threads complete in < 200ms total wall-clock time from the caller's perspective.
   - Caller thread wait time per log invocation is < 0.05 ms.

---

### Scenario 2: Verifying Persistent File Output & Rolling Rotation

**Objective**: Verify that logs are written to `<storageDir>/logs/clapi.log` and rotate when size exceeds 10 MB or day boundary passes.

1. **Launch Clapi with a temporary test directory**:
   ```bash
   ./gradlew :ui-desktop:run --args="--dir=/tmp/clapi-log-test"
   ```
2. **Perform API requests in the UI** or send a sample request.
3. **Inspect the log directory**:
   ```bash
   ls -la /tmp/clapi-log-test/logs/
   ```
4. **Expected Outcome**:
   - `clapi.log` exists in `/tmp/clapi-log-test/logs/`.
   - File contains properly formatted entries matching `{date} [{thread}] {level}: {class-name} - {message}`.
   - When rotated, compressed archives match `clapi-YYYY-MM-DD.1.log.gz`.
   - Old archives beyond 5 backups are automatically purged.

---

### Scenario 3: Verifying In-App Diagnostics Console & "Open Log Folder"

**Objective**: Verify that logs appear in the Compose UI bottom panel and the log directory opens in the OS file explorer.

1. **Open Clapi Desktop**:
   ```bash
   ./gradlew :ui-desktop:run
   ```
2. **Open Bottom Panel**: Navigate to the **Console** or **Network Logs** tab.
3. **Verify entries**: Confirm requests, script logs, and diagnostics appear with timestamp and level tags.
4. **Click "Open Log Folder"**:
   - Click the folder icon button in the diagnostics toolbar.
   - The OS native file manager (Finder / Nautilus / Explorer) opens displaying the log folder.

---

### Scenario 4: Verifying Headless Stdio / MCP Safety

**Objective**: Verify that diagnostic logs never contaminate `stdout` when running `--mcp`.

1. **Run MCP Server and send an initialize JSON-RPC request**:
   ```bash
   echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}' | \
     ./gradlew :ui-desktop:run --args="--mcp" 2>/dev/null
   ```
2. **Expected Outcome**:
   - Standard output contains **only** the single JSON-RPC 2.0 response line.
   - Diagnostic logs, startup messages, and Ktor logs are directed exclusively to `stderr` or `clapi.log`.

---

### Scenario 5: Verifying Fatal Crash Capture

**Objective**: Verify that uncaught exceptions are flushed to disk before process death.

1. **Run Uncaught Exception Integration Test**:
   ```bash
   ./gradlew :ui-desktop:desktopTest --tests "io.github.makeforge.clapi.ui.desktop.logging.CrashHandlerIntegrationTest"
   ```
2. **Expected Outcome**:
   - The test verifies that a synthetic unhandled runtime exception on a worker thread is written to `clapi.log` with the complete stack trace before shutdown completes.
