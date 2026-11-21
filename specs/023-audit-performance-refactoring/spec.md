# Feature Specification: Audit Performance & Architecture Refactoring

**Feature Branch**: `023-audit-performance-refactoring`

**Created**: 2026-11-14

**Status**: Draft

**Input**: User description: "the recommended refactorings of @[scratch/audit_findings.md], ordered as suggested in the phases"

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Phase 1: Immediate Critical Resource, Persistence, Security & Cancellation Fixes (Priority: P1)

As a desktop API client user working with multiple tabs, authenticated endpoints, and external secret managers, I want the application to reuse background network resources without leaking threads, debounce workspace autosaves while typing, mask secrets in diagnostic logs, and cleanly cancel in-flight operations when I stop a request or close a tab, so that my system resources remain stable, my credentials stay protected, and typing feels instantaneous.

**Why this priority**: Phase 1 addresses the highest-severity resource leaks, data-race hazards, credential exposure risks, and broken cancellation paths identified in the audit. Resolving these first eliminates thread/connection leaks on every request, stops per-keystroke disk writes, prevents plain-text secret leakage in logs, and guarantees that closed tabs stop executing background work.

**Independent Test**: Execute 50 consecutive requests across HTTP, GraphQL, gRPC, WebSocket, and MCP tabs while monitoring active network engine threads; type rapidly in the URL bar and body editor while monitoring disk write frequency; execute a request with secret variables in query parameters and inspect the network/console logs; and close a tab or click Cancel while a request or secret lookup is in flight.

**Acceptance Scenarios**:

1. **Given** a user executes multiple HTTP, GraphQL, or gRPC requests (with or without OAuth2 or external secret managers), **When** requests complete and WebSocket or MCP sessions disconnect, **Then** auxiliary authentication, secret-provider, WebSocket, and MCP network clients are shared or properly closed so that zero orphaned network engines or selector threads accumulate across requests.
2. **Given** a user types rapidly in the URL bar, parameter/header tables, or request body editor, **When** keystrokes occur within a 500-millisecond window, **Then** autosave coalesces changes into a single debounced disk write after typing pauses, capturing a consistent state snapshot on the UI thread before writing on a background thread without concurrent modification errors.
3. **Given** a request URL or query parameter references a secret environment variable or an external secret manager value (Vault, AWS, GCP, Azure, KeePassXC, or Keeper), **When** the request is executed and recorded in the Network and Console logs, **Then** all secret values in the logged URL are masked (`••••••••`) rather than appearing in plain text.
4. **Given** a tab has an in-flight request, streaming response, secret resolution, script sub-request, Git operation, or MCP call, **When** the user closes the tab (via Close, Close Others, Close to Left/Right, or Close All) or cancels the operation, **Then** the in-flight job is immediately cancelled, cancellation propagates cleanly without spurious error toasts, and no further state mutations or chained requests occur for that closed tab.

---

### User Story 2 - Phase 2: Core Execution Performance, Off-Main-Thread Processing & Connection Pooling (Priority: P2)

As a user executing scripted requests, streaming real-time SSE/LLM responses, calling gRPC services, and formatting or searching large payloads, I want heavy computation and file I/O to run off the UI thread and network connections and compiled schemas to be reused across requests, so that the desktop interface never freezes and repeated requests execute with minimal latency.

**Why this priority**: Phase 2 eliminates UI freezes caused by synchronous script evaluation, $O(N^2)$ stream chunk re-parsing, synchronous payload formatting/folding/searching, and blocking file picker reads, while unlocking connection reuse (HTTP keep-alive and gRPC channel pooling) and `.proto` descriptor caching.

**Independent Test**: Run a pre-request or test script containing a multi-second computation while interacting with the UI; stream a 1,000-event SSE/LLM response and verify linear processing time; execute repeated HTTP and gRPC requests to the same host and verify connection and schema reuse; and verify per-request GraphQL timeout and redirect overrides.

**Acceptance Scenarios**:

1. **Given** a request with a pre-request or post-response script (up to the 5-second execution limit) or a user selecting a large binary file for upload, **When** the script runs or the file is loaded and encoded, **Then** execution happens on a background worker thread and the desktop UI remains continuously responsive at 60 FPS.
2. **Given** a streaming SSE or LLM response delivering hundreds of incremental chunks, **When** chunks arrive, **Then** the live stream text is assembled incrementally without re-parsing all prior chunks on every event, and UI updates remain smooth.
3. **Given** a large HTTP response (e.g., multi-megabyte payload), **When** the response is received, **Then** response byte size is determined from `Content-Length` or calculated without allocating a duplicate full-body byte array, and LLM token extraction is skipped for non-LLM or oversized payloads.
4. **Given** repeated HTTP, GraphQL, or gRPC requests to the same endpoint, **When** executed sequentially, **Then** underlying network connections/channels are pooled and reused, unchanged `.proto` files are parsed once and cached by modification timestamp rather than re-invoking the external protocol compiler on every call, and per-request GraphQL redirect and timeout settings are honored.
5. **Given** a user formats, folds, or searches within a request or response body in the code editor, **When** the operation runs on a large document, **Then** formatting, fold-region detection, and search matching execute asynchronously without blocking keystrokes or cursor movement.

---

### User Story 3 - Phase 3: UI Virtualization, Bounded Memory Hygiene & Tab State Isolation (Priority: P3)

As a power user keeping Clapi open across long desktop sessions with many tabs and large responses, I want the raw response viewer and code editor to render only visible lines with minimal recomposition overhead, logs and history to stay within bounded memory limits, and each tab to maintain its own isolated test results, so that memory usage remains stable over days of use and switching tabs never mixes up request results.

**Why this priority**: Phase 3 hardens long-session stability, large-response rendering, recomposition efficiency, and state modularity so the application scales gracefully to multi-megabyte responses and multi-day desktop workflows.

**Independent Test**: Open a 10 MB response and switch to the Raw view to verify virtualized scrolling; run different test assertions in Tab A and Tab B and switch between them to verify isolated test results; generate thousands of console/network log entries and history items to verify ring-buffer eviction; and verify editor scroll performance on a file with thousands of fold regions and template variables.

**Acceptance Scenarios**:

1. **Given** a multi-megabyte HTTP response, **When** the user switches to the Raw response tab or scrolls through the formatted Code Editor with fold regions, diagnostics, and `{{variable}}` highlights, **Then** only visible lines are laid out and rendered via virtualized lists, line fold/diagnostic lookups run in $O(1)$ indexed time per visible line, and scrolling remains smooth without UI freezes.
2. **Given** Tab A executes a request with post-response test assertions and Tab B executes a different request, **When** the user switches between Tab A and Tab B, **Then** each tab displays its own independent test assertion results without clearing or overwriting the other tab's results.
3. **Given** a long-running session generating thousands of console messages, network events, WebSocket frames, stream chunks, and request history entries, **When** any collection exceeds its configured capacity limit, **Then** the oldest entries are automatically evicted so in-memory collections and persisted history remain bounded.
4. **Given** multiple open tabs where background tabs hold cached editor view models or replaced editor instances, **When** editor models are recreated or tabs are inactive, **Then** orphaned scroll states and redundant payload buffers are cleaned up, and core application state responsibilities (layout, tabs, collections, environments, and diagnostics logs) are cleanly separated.

---

### Edge Cases

- **Rapid Tab Close During Multi-Step Execution**: If a user closes a tab while it is mid-way through external secret resolution, OAuth2 token refresh, pre-request script `sendRequest`, or a `setNextRequest` chain, cancellation must halt the pipeline before any downstream step or next-request tab transition occurs.
- **Secret Values Matching Common Short Substrings**: Secret redaction in network and console logs must only mask non-blank secret values of meaningful length (e.g., 3 or more characters) so single-character values do not corrupt log readability while all real tokens, passwords, and API keys are masked.
- **Modified `.proto` Files Between gRPC Calls**: When caching compiled `.proto` descriptors for gRPC requests, editing any imported or root `.proto` file on disk must invalidate the cache entry via file modification timestamps so the updated schema is immediately reflected on the next request.
- **Application Shutdown Flushing Pending Autosaves**: When a user edits a tab or collection and closes the desktop window within the 500 ms autosave debounce window, the final state must still be persisted cleanly or handled without corrupting the stored workspace files.
- **Oversized Responses in Raw and Formatted Views**: When an endpoint returns a multi-megabyte response without a `Content-Length` header, byte length calculation must accurately count UTF-8 bytes (including multi-byte and surrogate pair characters) without duplicating the entire string as a byte array in heap memory.

---

## Requirements *(mandatory)*

### Functional Requirements

#### Phase 1: Immediate Critical Fixes
- **FR-001**: The request execution pipeline (HTTP, GraphQL, gRPC, and variable resolution) MUST reuse shared authentication and external secret-provider client instances rather than instantiating new unclosed network clients via default function arguments on every request.
- **FR-002**: WebSocket and MCP session managers MUST properly close their underlying network clients and cancel child worker scopes when disconnecting or disposing sessions.
- **FR-003**: Workspace, open tabs, and settings autosave observers MUST debounce rapid state changes (by 500 milliseconds), capture a thread-safe snapshot of UI state on the main thread before performing background disk I/O, and dispatch any UI error notifications back onto the main thread.
- **FR-004**: Request executors MUST redact all resolved secret values (from secret environment variables and external secret providers) before writing request URLs or parameters to network event logs and console logs.
- **FR-005**: Closing a single tab or multiple tabs MUST immediately cancel any in-flight request job (`currentJob`) associated with the closing tab(s) and reset their loading state.
- **FR-006**: All suspending operations across variable/secret resolution, script execution, Git CLI execution, and MCP communication MUST rethrow cancellation exceptions rather than catching and swallowing them as generic errors.

#### Phase 2: Core Performance & Concurrency Hardening
- **FR-007**: Embedded script evaluation (pre-request and post-response test scripts) and file-picker binary reading/encoding MUST execute on background worker threads so the main UI thread is never blocked.
- **FR-008**: Streaming response assembly (SSE and LLM token streams) MUST append incoming chunks incrementally ($O(1)$ per chunk) rather than re-parsing the entire accumulated chunk history on every event.
- **FR-009**: Response post-processing MUST compute payload byte size without allocating a duplicate full-body byte array and MUST skip full-body JSON parsing for LLM text extraction when the payload exceeds a safe size threshold (512 KB) or lacks LLM envelope keys.
- **FR-010**: Primary HTTP, GraphQL, and gRPC clients MUST reuse pooled connections/channels across compatible requests and cache parsed `.proto` file descriptors keyed by file paths and last-modified timestamps.
- **FR-011**: The GraphQL request executor MUST apply the active tab's per-request timeout, follow-redirects, and max-redirects overrides when executing GraphQL queries and mutations.
- **FR-012**: Code editor formatting (including manual Format triggers), fold-region detection, search match scanning, and bracket matching MUST avoid blocking the main UI thread on large documents.

#### Phase 3: UI Virtualization, Memory Caps & State Modularization
- **FR-013**: The Raw response viewer MUST render response lines using a virtualized lazy list rather than laying out the entire response body in a single text element.
- **FR-014**: The code editor renderer MUST pre-index fold regions and inline diagnostics by line number once per document revision and memoize template-variable sets and highlight providers so scrolling does not perform linear scans or redundant regex passes per visible line.
- **FR-015**: Post-response test assertion results (`testResults`) MUST be stored and displayed per request tab (`RequestTabState`) rather than shared in a single global list on `AppState`.
- **FR-016**: In-memory logs and history collections (`consoleLogs`, `networkEventLogs`, `historyItems`, `webSocketLogMessages`, and `streamChunks`) MUST enforce maximum item capacity limits (e.g., 1,000 log entries, 500 history items), evicting the oldest entries when full.
- **FR-017**: Editor scroll-state caches and inactive tab editor buffers MUST release references when editor view models are replaced or tabs are inactive on large payloads, and `AppState` domain concerns MUST be organized into cohesive state holders while preserving existing external API contracts.

---

### Key Entities

- **Shared Network Client Registry**: Lifecycle-managed holder for shared HTTP, OAuth2, external secret provider, WebSocket, MCP, and gRPC channel/descriptor caches across the application session.
- **Request Tab State (`RequestTabState`)**: Per-tab state container owning the request definition, in-flight execution job, streaming buffers, response payload, and tab-scoped test assertion results.
- **Debounced Persistence Snapshot**: Immutable serialized snapshot captured atomically from UI state on the main thread and written asynchronously to local storage after a quiet period.
- **Bounded Diagnostic & History Buffers**: Capacity-capped collections for console logs, network event logs, WebSocket frame logs, and request history items with secret redaction applied prior to insertion.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Executing 100 consecutive API requests leaks **0** orphaned network client engines or background selector threads.
- **SC-002**: Typing continuously in the URL bar, headers table, or request body editor at 10 keystrokes per second triggers at most **1** disk persistence write after typing pauses (500 ms debounce), down from 10 writes per second.
- **SC-003**: **100%** of secret environment variable values and external secret manager values interpolated into request URLs or query parameters are masked in network and console logs.
- **SC-004**: Main UI thread frame times remain responsive (no UI freeze exceeding 100 ms) during a 3-second pre-request/test script execution, a 1,000-chunk SSE stream, or when viewing and scrolling a 5 MB response in either Formatted or Raw view.
- **SC-005**: Switching between two open tabs that each ran different test scripts preserves **100%** of each tab's individual test assertion results without cross-tab contamination.
- **SC-006**: All existing automated unit, integration, and UI tests across all project modules pass with zero regressions.

---

## Assumptions

- A 500-millisecond debounce interval for tab, workspace, and settings autosave provides the optimal balance between eliminating per-keystroke disk I/O and minimizing potential data loss on unexpected termination.
- Capping in-memory console logs and network event logs at 1,000 entries each, WebSocket message logs at 1,000 entries per tab, and request history at 500 entries per collection satisfies typical debugging needs while bounding long-session heap growth.
- Secret redaction applies to non-blank secret values of at least 3 characters in length to prevent accidental masking of common 1–2 character tokens in URLs while protecting all real credentials and API keys.
- Reorganizing `AppState` into focused domain state holders will maintain backward-compatible property/method delegation on `AppState` where required so existing UI components and tests continue to compile and pass without breaking changes.
