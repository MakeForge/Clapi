# Feature Specification: Asynchronous Persistent File & Diagnostics Logging

**Feature Branch**: `030-async-file-logging`

**Created**: 2026-11-18

**Status**: Draft

**Input**: User description: "implement logging with tinylog with slf4j. Logs should also be written to a persistent, rolling log file. In general logging should be async and not deteriorate Clapi's user perfromance at all. For general first proposal of another LLM (the LLM did not have entire context of the app, just some hints), see ./scratch/logging-proposal.md"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Non-Blocking High-Throughput Diagnostic Logging (Priority: P1)

As a developer or API tester executing high-concurrency requests, automated scripts, or large streaming payloads, I want the application to capture diagnostic and network event details asynchronously without causing UI stutter, frame drops, or slowing down request dispatching.

**Why this priority**: Fast, responsive UI interaction is a foundational quality of the client. Logging must never degrade the main logic or Compose UI performance, even during heavy streaming or rapid test execution.

**Independent Test**: Can be tested by running an automated burst of 5,000 log entries across multiple concurrent coroutines while scrolling the UI and measuring UI frame budget: UI maintains smooth 60 FPS without jank.

**Acceptance Scenarios**:

1. **Given** a user is streaming a response or running rapid requests, **When** log events are emitted across concurrent threads, **Then** the log calls return immediately without blocking the invoking thread.
2. **Given** an intensive logging loop emitting events continuously, **When** the user interacts with the UI (scrolling lists, switching tabs, typing in editors), **Then** frame rendering remains smooth without UI lockups or dropped frames.

---

### User Story 2 - Persistent Rolling File Logging & Retention (Priority: P1)

As a user or troubleshooter diagnosing an issue or crash, I want the application to automatically write operational and error logs to a persistent rolling file on disk, with automatic size- and date-based rotation and old file cleanup, so that diagnostic history is preserved across application launches without consuming unbounded disk space.

**Why this priority**: Currently, all logs are ephemeral and held only in memory. When the application closes or crashes, all troubleshooting context is lost forever.

**Independent Test**: Can be tested by generating logs, closing or terminating the application, and verifying that the log file exists on disk with complete timestamps, levels, and error stack traces preserved across restarts.

**Acceptance Scenarios**:

1. **Given** the application is running, **When** log events are emitted, **Then** they are asynchronously flushed to a designated log file in the application's persistent data directory.
2. **Given** the active log file reaches its size threshold (e.g. 10 MB) or a new day begins, **When** subsequent logs are written, **Then** the file is rotated, archived, and a new active file is created.
3. **Given** the number of archived log files exceeds the retention limit (e.g. 5 backups), **When** a new rotation occurs, **Then** the oldest archive is automatically purged so total disk usage stays bounded.
4. **Given** an unhandled fatal error or unexpected crash occurs, **When** the process exits, **Then** the fatal stack trace is captured in the persistent log file.

---

### User Story 3 - Live In-App Diagnostics Console & Easy Log Access (Priority: P2)

As a user, I want the in-app bottom diagnostics panel (Console and Network Logs) to display live diagnostic events with filtering and search, and provide a single-click shortcut to open the active log folder in my operating system's native file explorer.

**Why this priority**: Users need fast visibility into errors and request workflows directly inside the application, plus an effortless way to retrieve full log files when reporting bugs or sharing troubleshooting data.

**Independent Test**: Can be tested by triggering various log levels (Info, Warning, Error), observing them appear in the bottom panel, using the search/filter controls, and clicking "Open Log Folder" to confirm the OS file manager opens to the correct directory.

**Acceptance Scenarios**:

1. **Given** new log events occur, **When** the user views the bottom Console or Network Logs tab, **Then** entries appear formatted with timestamp, level, component name, and message.
2. **Given** the bottom diagnostics panel is visible, **When** the user clicks "Open Log Folder", **Then** the operating system's file manager opens directly to the persistent log directory.
3. **Given** the user enters a search term or changes the minimum level filter in the UI, **When** the filter is applied, **Then** only matching log entries are displayed without re-reading the entire disk file.

---

### User Story 4 - Headless & Stdio Protocol Isolation (Priority: P2)

As an automation user or client running Clapi in headless CLI or Stdio mode (e.g., `--mcp`), I want diagnostic logging to completely avoid writing to standard output (`stdout`), directing any console output strictly to standard error (`stderr`), so that protocol framing is never corrupted.

**Why this priority**: Clapi supports headless MCP server execution where `stdout` is strictly reserved for newline-delimited JSON-RPC 2.0 messages. Any unexpected log written to `stdout` breaks client-server protocol communication.

**Independent Test**: Can be tested by launching the application in `--mcp` mode, generating diagnostic logs, and verifying that `stdout` receives only valid JSON-RPC frames while diagnostic logs route to `stderr` and the log file.

**Acceptance Scenarios**:

1. **Given** the application is started in `--mcp` mode, **When** internal diagnostic logs are emitted, **Then** `stdout` receives strictly JSON-RPC protocol frames, with log output directed to `stderr` and disk.

---

### Edge Cases

- **Disk Full / Read-Only Storage**: What happens when the user's disk is full or the log directory has no write permissions? The logging system must fail gracefully without throwing unhandled exceptions or crashing the main application.
- **High-Frequency Bursts**: What happens when a script or network stream emits tens of thousands of messages in seconds? The asynchronous queue must buffer smoothly, apply batching, or drop excessive trace entries safely without exhausting JVM heap memory.
- **Concurrent Coroutine Logging**: How does the system handle hundreds of concurrent requests logging simultaneously? The log pipeline must be thread-safe with lock-free/low-contention queueing.
- **Crash / Abrupt Process Kill**: How are pending log events handled if the application is suddenly killed? Flushes must occur at critical error boundaries and on JVM shutdown hooks to minimize log truncation.
- **Custom Directory Overrides**: What happens when the user specifies a custom storage directory via `--dir <path>` or environment variables? The log files must automatically relocate into the `<storageDir>/logs/` folder.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST process all log dispatch calls asynchronously on a dedicated background worker, ensuring zero I/O blocking on the user interface thread or network dispatchers.
- **FR-002**: The system MUST write persistent log entries to a designated log file stored under the application's storage directory (e.g., `<storageDir>/logs/clapi.log`).
- **FR-003**: The system MUST support automatic rolling of log files based on file size (defaulting to 10 MB) and daily date boundaries.
- **FR-004**: The system MUST enforce a bounded retention policy (defaulting to retaining at most 5 archived log files) and automatically prune older archives.
- **FR-005**: The system MUST record uncaught JVM exceptions and fatal thread terminations to the persistent log file prior to application exit.
- **FR-006**: The system MUST forward diagnostic log entries to the in-app Compose UI diagnostics panel using batched or non-blocking updates to avoid UI frame hitching.
- **FR-007**: The system MUST provide a user action in the UI to open the persistent log directory in the operating system's native file explorer.
- **FR-008**: The system MUST support configurable logging levels (TRACE, DEBUG, INFO, WARN, ERROR), with production defaults that suppress noisy third-party internal network logs (such as Ktor/Netty) while preserving application events.
- **FR-009**: In headless Stdio mode (e.g., `--mcp`), the system MUST ensure diagnostic logs are never written to `stdout`, keeping `stdout` strictly reserved for protocol communication.
- **FR-010**: The system MUST respect custom storage directory overrides provided via CLI argument (`--dir`), system property, or environment variable (`CLAPI_DIR`).

### Key Entities

- **LogEntry**: An immutable record representing an emitted event, containing timestamp, severity level, logger/component name, message text, thread identifier, and optional throwable/stack trace.
- **LogRotationPolicy**: Configuration defining maximum active file size, rolling schedule, compression/archival format, and retention backup count.
- **LogStore**: The storage abstraction managing active log file streams, archive rotation, and pruning on disk.
- **UiLogBuffer**: A bounded, memory-safe in-memory buffer providing filtered log entries to the Compose UI diagnostics panel.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Emitting a burst of 1,000 log events within 1 second introduces 0 ms of blocking on the UI thread and maintains steady 60 FPS scrolling and interaction.
- **SC-002**: 100% of diagnostic events emitted prior to normal application exit or clean shutdown are persisted to disk.
- **SC-003**: In the event of an unhandled fatal crash, the fatal exception and its complete stack trace are captured in the active log file on disk.
- **SC-004**: Total disk space utilized by application logs never exceeds the configured threshold (e.g. 60 MB total under default 5 x 10 MB plus active log).
- **SC-005**: 100% of JSON-RPC protocol frames emitted over `stdout` during `--mcp` execution remain clean and uncontaminated by log output.
- **SC-006**: Users can access and reveal the persistent log directory within 2 clicks from the application UI.

## Assumptions

- Standard default log retention is 5 historical archive files of up to 10 MB each, compressed to minimize disk usage.
- Default log level in standard desktop runs is `INFO` for application logic, with verbose third-party network engine internals (e.g. Ktor CIO / Netty) quieted to `WARN` unless explicitly configured.
- The log directory defaults to `<storageDir>/logs/`, adhering to the active workspace storage resolution (defaulting to `~/.clapi/logs/` or OS-appropriate path when `--dir` is not supplied).
- The in-memory UI log buffer retains a maximum of 1,000 entries to prevent unbounded memory growth during long-running sessions.
