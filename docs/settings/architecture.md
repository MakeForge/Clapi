---
title: Application Architecture Deep Dive
description: Architectural overview, module boundaries, state management, and code editor engine of Clapi.
---

# Application Architecture Deep Dive

<p class="badges">
  <span class="badge badge-protocol">Internal Architecture</span>
  <span class="badge badge-version">Kotlin Multiplatform</span>
</p>

## Overview

Clapi is engineered as a modular **Kotlin Multiplatform (KMP)** desktop application targeting the JVM (`jvm("desktop")`). It leverages Compose Multiplatform for declarative UI rendering.

## Module Hierarchy & Dependency Boundaries

```
                 ui-desktop (Thin Launcher)
                     │
                     ▼
                 ui-shared (Compose Multiplatform Desktop UI)
              ┌──────┴──────┐
              ▼             ▼
         editor-ui     core-network (Ktor, OAuth2, MCP, gRPC)
              │             │
              ▼             ▼
         editor-core   core-storage (Persistence & JSON Adapters)
              │             │
              └──────┬──────┘
                     ▼
                 core-model (Domain Models & OpenCollection)
```

### Architectural Principles
1. **Unidirectional Dependency Flow**: The UI launcher (`ui-desktop`) depends only on `ui-shared`. `ui-shared` depends on editor and core modules. Core modules never depend on UI modules.
2. **Platform Abstraction**: Common business logic and UI composables live strictly in `commonMain`. Desktop JVM platform integrations (windowing, system keychain, file dialogs) use `expect`/`actual` declarations.
3. **Pure-Kotlin Code Editor**: The code editor engine (`editor-core`) is completely decoupled from UI frameworks, maintaining document models, syntax lexers, and line fold maps independently.

## State Management

Clapi utilizes observable state holders without Android lifecycles:
- **`AppState`**: Root observable state managing active tabs, loaded collections, active environment, and dialog states.
- **`RequestTabState`**: Encapsulates the complete mutable state of an individual tab (method, URL, headers, body, auth, and response snapshot).
- **`WorkspaceRepository`**: Persists tab state, open collections, and window dimensions to disk upon workspace modification.

## Asynchronous Logging Subsystem

Clapi uses an asynchronous, zero-UI-blocking logging architecture powered by **Tinylog 2** and **SLF4J / `kotlin-logging`**:

1. **Non-Blocking Execution**: Log calls from the UI, Ktor client, and coroutine dispatchers deposit lightweight records into a lock-free queue and return immediately (<0.05 ms). A dedicated background writing thread (`writingthread = true`, `priority = 1`) handles all disk formatting, file writes, and UI batching.
2. **Persistent Rolling File & Retention**: Logs are stored in `<storageDir>/logs/clapi.log` (rotating daily or at 10 MB, retaining 5 compressed `.gz` historical archives).
3. **Crash Capture**: An uncaught exception handler intercepts unhandled JVM errors and flushes complete stack traces to disk before termination.
4. **Headless Stdio Protocol Safety**: When running with `--mcp`, console logs route strictly to `stderr` (`writer1.stream = err`), ensuring `stdout` remains clean for newline-delimited JSON-RPC 2.0 frames.
5. **In-App UI Diagnostics Console**: A custom `org.tinylog.writers.Writer` (`UiDiagnosticsLogWriter`) forwards entries from the background writing thread to `DiagnosticsLogState` on `Dispatchers.Main` with batching, accompanied by a one-click "Open Log Folder" button in the diagnostics panel toolbar.
