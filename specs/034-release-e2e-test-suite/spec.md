# Feature Specification: Release-Ready E2E & UI Verification Suite

**Feature Branch**: `034-release-e2e-test-suite`

**Created**: 2026-11-22

**Status**: Draft

**Input**: User description: "implement all recommended missing tests/test-cases from @scracth/test-evaluation.md"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Complete Automated Quality Gates & Packaged Release Smoke Verification (Priority: P1)

Release maintainers and contributors rely on automated verification pipelines to guarantee that no regression slips into the main branch or into published desktop installers. Every pull request, main-branch update, and release candidate must execute the complete verification suite across every product subsystem—including desktop user interface tests—without silently skipping required external-service integration checks. Before any release artifacts are published, both the universal runnable package and every platform-specific native standalone distributable must pass a realistic startup, persistence, scripting, embedded agent server, and legal-file integrity smoke check.

**Why this priority**: Currently, pull-request checks omit the entire desktop UI test suite and multiple core subsystems, release checks omit several core subsystems, external-service integration tests can silently pass as skipped when test services are unavailable, and packaged runtime checks only verify a trivial empty shutdown on the universal package rather than the trimmed native installers. Closing these gaps first ensures every subsequent test immediately protects the release.

**Independent Test**: Run the pull-request verification workflow, the release verification workflow, and the packaged-runtime smoke checks against built universal and native distributables, confirming all 10 product subsystems execute, disabled protocol tests are active, unavailable test containers fail automated checks rather than silently skipping, and packaged binaries pass end-to-end startup/shutdown/persistence/scripting/server/license checks.

**Acceptance Scenarios**:

1. **Given** a pull request or update targeting the main branch, **When** automated quality checks run, **Then** automated tests execute across all 10 product subsystems—including domain models, networking, scripting, storage, code editor engine and UI, embedded agent server, shared workspace logic, desktop UI, and end-to-end integration tests.
2. **Given** a release candidate build is triggered, **When** the release quality gate runs, **Then** all 10 product subsystems must pass before platform installers are built or published.
3. **Given** an automated CI run where a required local test service (such as the OAuth2 identity provider or message broker) is unreachable, **When** integration verification executes, **Then** the check fails explicitly instead of reporting a silent pass via skipped tests.
4. **Given** a previously disabled protocol handshake test, **When** the networking verification suite executes, **Then** the test is enabled and passes deterministically without being ignored.
5. **Given** built release artifacts (universal runnable package and platform-specific native standalone distributables), **When** packaged runtime smoke verification executes, **Then** it confirms clean startup and shutdown, workspace/settings/tabs load and save in an isolated directory, script engine evaluation, embedded agent server startup and protocol handshake (both HTTP and standard input/output modes), startup-optimization training pass execution without error, and presence of required license and attribution notice files.

---

### User Story 2 - End-to-End Interactive UI Verification of Core HTTP, Auth, Scripting & Cancellation Flows (Priority: P1)

End users interact with Clapi primarily by authoring HTTP requests in the desktop workspace, clicking Send (or pressing the Send shortcut), inspecting responses, running pre/post-request scripts, managing variables, and canceling or retrying requests. Automated interactive UI tests must exercise these complete user journeys through the live desktop interface against a local deterministic test server so that real wiring between editors, execution, network transport, response display, history, and bottom panels is continuously verified.

**Why this priority**: Currently zero UI tests click the Send action or complete a live request round-trip through the desktop workspace, leaving regressions in UI-to-network wiring, response rendering, script result presentation, history recording, and request cancellation undetected until manual testing.

**Independent Test**: Launch the main desktop workspace connected to the local deterministic test server, execute full HTTP request journeys (methods, bodies, auth, variables, scripts, cancellation, errors, retries) via real UI interactions, and verify visible updates in the response viewer, history list, environment state, and bottom-panel tabs.

**Acceptance Scenarios**:

1. **Given** the main workspace is open with an active environment containing a base URL variable, **When** the user authors requests across HTTP methods (`GET`, `POST`, `PUT`, `DELETE`), adds query parameters, custom headers, and payloads (JSON, relaxed JSON5, multipart form data, URL-encoded form, XML, and raw text), and clicks Send, **Then** the request transitions through an active loading state and renders the live status code, latency, payload size, formatted response body, response headers, cookies, timing metrics, and raw body in the response viewer.
2. **Given** a user executes an HTTP request from the workspace, **When** the request completes, **Then** a new entry appears in the sidebar History list, clicking that history entry restores the request into the workspace, and the bottom-panel Console/Logs record the request.
3. **Given** a request configured with Basic, Bearer, API Key, OAuth2 token acquisition, or collection/folder-inherited authentication and headers, **When** the user clicks Fetch Tokens (for OAuth2) and clicks Send against protected local test endpoints, **Then** valid credentials yield authorized responses and invalid credentials surface unauthorized responses accurately in the UI.
4. **Given** a request with a pre-request script that mutates an outgoing header and environment variable and a post-request script containing both a passing test, a failing test, and console log output, **When** the user clicks Send, **Then** the mutated header reaches the server, the Test Results tab shows exact pass/fail counts and assertion details, the Console tab displays the script logs, and the active environment reflects the updated variable.
5. **Given** a slow in-flight HTTP request is running, **When** the user clicks Stop, **Then** the loading indicator clears, the Send button returns, no late/stale response overwrites the viewer, and a subsequent Send succeeds cleanly.
6. **Given** a request targeting server error endpoints (404, 500), an unreachable address, or an invalid URL—including with per-request retry enabled—**When** the user clicks Send, **Then** the workspace surfaces clear error status/feedback without freezing, and configured retries execute before settling on the final outcome.

---

### User Story 3 - End-to-End Interactive UI Verification of Multi-Protocol Workspaces (Priority: P1)

Clapi is a multi-protocol API client supporting Server-Sent Events (SSE), LLM token streaming, GraphQL, WebSocket, gRPC, Model Context Protocol (MCP), and RabbitMQ. Each protocol workspace must have interactive end-to-end UI tests that verify authoring, live execution or connection lifecycle, real-time output inspection, error handling, and clean resource teardown from the desktop interface.

**Why this priority**: Several flagship 1.0.0 protocol workspaces (MCP client, RabbitMQ, and SSE/LLM streaming) currently have zero dedicated UI tests, while GraphQL, WebSocket, and gRPC UI tests only render isolated controls without executing real requests or live sessions.

**Independent Test**: For each protocol (SSE/LLM, GraphQL, WebSocket, gRPC, MCP, RabbitMQ), open or create a request of that protocol inside the main desktop workspace, exercise its authoring controls and live execution/connection lifecycle against local test servers, and verify visible protocol-specific output and clean teardown.

**Acceptance Scenarios**:

1. **Given** an SSE or streaming LLM request in the workspace, **When** the user sends a GET/POST SSE request or a streaming chat completion request, **Then** the streaming badge is shown, events and tokens arrive incrementally in the live streaming viewer with assembled text preview and latency/token metrics, the user can stop an active stream cleanly, and early stream termination preserves partial output with clear status feedback.
2. **Given** a GraphQL request created via the sidebar menu, **When** the user authors a query, JSON variables, and multiple body variants and clicks Send in both POST and GET modes, **Then** the active variant's query and variables are transmitted to the local server and the formatted response renders in the response viewer.
3. **Given** a WebSocket request in the workspace, **When** the user connects to a local echo endpoint, sends text and JSON messages, disconnects, or closes the tab while still connected, **Then** connection status transitions accurately (`Connecting` -> `Connected` -> `Disconnected`), sent and received frames render in the live timeline, and closing an active tab terminates the session cleanly without errors.
4. **Given** a gRPC request with a direct or collection-inherited schema definition, **When** the user selects a discovered service and unary method, enters JSON payload and metadata, and clicks Send against a local gRPC test server, **Then** the structured gRPC status, response payload, response headers, and trailing metadata display accurately in the UI.
5. **Given** an MCP client tab in the workspace, **When** the user connects over Streamable HTTP or Legacy HTTP+SSE, invokes a tool with form or raw JSON arguments, inspects resources (including reading/subscribing), prompts, and bi-directional JSON-RPC activity logs, responds to interactive server callbacks (sampling and elicitation), and disconnects or closes the tab, **Then** all panels reflect live server state and the session terminates cleanly.
6. **Given** a RabbitMQ request in the workspace, **When** the user switches between Publish, Subscribe, and RPC modes, edits AMQP/header/payload settings, triggers an action against an unreachable broker, and (when a local test broker is available) runs Publish, Subscribe, and RPC with message acknowledge/reject controls, **Then** Subscribe mode hides unsupported message/header tabs and falls back to the AMQP tab, dirty/save state tracks edits accurately, unreachable-broker errors render cleanly, and live messaging operations display replies and timeline deliveries accurately.

---

### User Story 4 - Interactive UI Verification of Dialogs, Settings, Shortcuts & Workspace Workflows (Priority: P2)

Everyday desktop productivity depends on dialogs, settings, keyboard shortcuts, environment switching, undoable actions, multi-tab management, bottom-panel diagnostics, and detachable response windows working reliably together. All currently untested dialogs, settings sections, shortcuts, and end-to-end workspace flows must have dedicated interactive UI verification.

**Why this priority**: Multiple user-facing dialogs and core desktop productivity features (collection export, embedded MCP server settings, theme switching, global shortcuts, multi-tab dirty-close confirmation, and real sidebar-triggered undo snackbars) currently have zero or incomplete interactive UI coverage.

**Independent Test**: Open the main desktop workspace and interactively verify each dialog, settings pane, keyboard shortcut, toolbar switcher, multi-tab close workflow, sidebar undo flow, bottom-panel interaction, and detached response window lifecycle independently.

**Acceptance Scenarios**:

1. **Given** a workspace with multiple collections, **When** the user opens the Export Collections dialog from the sidebar, toggles Select All and individual selections, switches between JSON and YAML formats, and confirms export, **Then** the selected collections are written in the chosen format and the dialog closes cleanly.
2. **Given** the Settings dialog is open on the MCP Server section, **When** the user toggles the embedded MCP server on and off, changes the configured port while running, and clicks Copy Configuration Snippet, **Then** the live server status indicator reflects running/stopped state accurately, port changes restart the server on the new port, and a valid configuration snippet is copied.
3. **Given** the workspace is displayed, **When** the user switches between Light, Dark, and System themes (via Settings or the top toolbar) and switches the active environment via the top toolbar environment picker (including parent/child inherited environments), **Then** the selected theme updates immediately and persists across reload, and variable resolution immediately reflects the newly active environment.
4. **Given** the main workspace has focus, **When** the user invokes global keyboard shortcuts for Send, Save, New Tab, Close Tab (both clean and unsaved), Open Settings, and Move Tab Left/Right, **Then** each shortcut executes its corresponding workspace action reliably.
5. **Given** two or more open tabs have unsaved changes, **When** the user triggers "Close Others" or "Close All" from the tab context menu, **Then** the multi-tab unsaved changes confirmation dialog appears and properly executes Save All, Discard All, and Cancel paths.
6. **Given** items exist in the sidebar (requests, folders with nested contents, collections, environments, and history items), **When** the user deletes, renames, or moves them via real sidebar UI interactions and clicks Undo on the resulting bottom-right snackbar, **Then** the items—including nested children, open tabs, and persisted workspace state—are fully restored.
7. **Given** the bottom panel and detached response viewer, **When** the user switches between Console, Test Results, and Logs tabs, clears logs, collapses/resizes the bottom panel, or detaches the response viewer while sending requests, switching tabs, and closing the last tab, **Then** all panels stay synchronized and display the appropriate content or empty state.

---

### User Story 5 - Resilience, Security Redaction, Layout & Visual Regression Verification (Priority: P2)

Before a 1.0.0 release, the application must prove resilience against corrupted files, malformed user inputs, secret-leakage risks, extreme payloads, tight desktop window dimensions, and accidental visual regressions. Automated tests must cover these boundary conditions and establish repeatable visual baseline checks for primary screens and dialogs.

**Why this priority**: Edge cases such as leaked secrets in logs/exports, crashes on malformed local files, infinite variable expansion loops, broken layouts at the minimum window size, or unintended visual regressions undermine user trust in a 1.0.0 release.

**Independent Test**: Run targeted automated resilience, security, layout, and visual baseline suites verifying recovery from corrupted storage, secret redaction across all outputs, variable/payload boundaries, minimum-viewport rendering, high tab counts, multi-protocol import/export round-trips, and visual snapshot comparisons.

**Acceptance Scenarios**:

1. **Given** corrupted or truncated workspace collection files, saved tab state, or settings files on disk—or a restored tab pointing to a request deleted externally—**When** the application loads the workspace, **Then** it recovers gracefully without crashing, surfaces clear diagnostic feedback, and preserves all remaining valid collections and settings.
2. **Given** open modified tabs and active live sessions (WebSocket, MCP, or streaming), **When** the application performs its normal window-close shutdown sequence, **Then** workspace and tab state persist cleanly and all active connections terminate without hanging or losing data.
3. **Given** environment or external-secret variables marked as secret are used in a request, **When** the request executes, is logged to Console/Network/Diagnostics logs, is recorded in Request History, is copied as a command snippet, or is captured during a crash report, **Then** plaintext secret values never leak into logs, persisted history, crash/bug-report payloads, or unredacted outputs.
4. **Given** circular or self-referencing variable definitions (`a` referencing `b` and `b` referencing `a`), unresolved/malformed templates, unquoted typed dynamic variables in JSON bodies (with relaxed JSON5 syntax both enabled and disabled), or non-text binary responses, **When** requests are validated and executed through the UI, **Then** circular references terminate deterministically without freezing, strict JSON is transmitted for typed variables, invalid JSON5 is rejected when disabled, and binary responses display safely and support saving/downloading without corruption.
5. **Given** the application window is rendered at its minimum supported size (800×600), has 15–20+ open tabs (including closing a tab while its request is in-flight), or contains 5-level nested folders, **When** the user navigates, searches, scrolls, reorders, and closes tabs or folders, **Then** critical controls remain visible and usable with no layout crashes or broken references.
6. **Given** malformed OpenAPI, Postman, Insomnia, or OpenCollection import payloads, **When** imported through the Import dialog, **Then** a clear error banner is displayed and existing workspace data remains untouched; conversely, exporting a multi-protocol collection (HTTP, GraphQL, gRPC, WebSocket, MCP, RabbitMQ, and environments), deleting it, and re-importing it through the UI preserves all protocol-specific configurations intact.
7. **Given** primary workspace views and key dialogs, **When** visual baseline verification runs during automated quality checks, **Then** rendered screens are compared against committed visual baselines and flag unintended visual regressions.

---

### Edge Cases

- **Concurrent edit and autosave races**: Rapid edits immediately followed by tab switch, manual save, or application exit must not lose edits or leave inconsistent dirty indicators.
- **Closing a tab with an in-flight request or active streaming/WebSocket/MCP session**: Closing the tab must cancel pending network activity and release session resources cleanly without emitting late updates to other tabs.
- **Script runtime exceptions in UI**: Syntax errors, thrown errors, or timeouts in pre-request, post-request, or dynamic-variable scripts must display actionable error feedback in the UI without locking the Send button in a perpetual loading state.
- **Detached response window edge states**: Closing the last open tab while the response viewer is detached must transition the detached window to an empty state cleanly; switching to a non-HTTP tab or sending a new request must keep the detached viewer synchronized.
- **Platform installer runtime completeness**: A feature that works when executed against a full system runtime may fail in a trimmed native installer runtime if required runtime modules or native libraries are omitted; smoke tests must catch this on every supported desktop OS.
- **External service unavailability in local vs. automated pipeline runs**: Local developer test runs without optional local container services may handle missing services gracefully, whereas automated continuous-integration runs must treat unreachable required test services as hard failures.
- **Empty and deeply nested collections**: Empty collections, empty folders, deleting a parent folder whose child requests are currently open in tabs, and moving requests across distinct collections must keep sidebar, tab bar, and persistence consistent.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Automated pull-request and main-branch quality gates MUST execute the complete test suite across all 10 product subsystems, including all desktop UI tests.
- **FR-002**: Automated release quality gates MUST execute the complete test suite across all 10 product subsystems before building or publishing release artifacts.
- **FR-003**: Automated CI runs MUST fail—rather than silently skip—when required integration test containers (OAuth2 identity provider, message broker, or secret vault emulators) are unreachable, and all previously disabled protocol handshake tests MUST be resolved and active.
- **FR-004**: Release packaging verification MUST validate both the universal runnable package and platform-specific native standalone distributables across clean startup/shutdown, isolated workspace/settings/tabs persistence, script engine execution, embedded agent server HTTP and standard-I/O protocol handshakes, startup-optimization training pass completion, and presence of required license and attribution notice files.
- **FR-005**: Automated interactive UI tests MUST execute end-to-end HTTP journeys through the main workspace against a local deterministic test server covering all primary HTTP methods, query parameters, headers, all body formats (including JSON5), authentication types (Basic, Bearer, API Key, OAuth2 token fetch & usage, and collection/folder inheritance), response viewer tabs, history recording/reopening, bottom-panel logging, in-flight cancellation, error statuses, and retry behavior.
- **FR-006**: Automated interactive UI tests MUST verify pre-request and post-request script execution end-to-end through the main workspace, confirming outgoing request mutations, live environment variable updates, Test Results pass/fail reporting, Console log rendering, and graceful UI handling of script errors.
- **FR-007**: Automated interactive UI tests MUST verify live SSE and streaming LLM execution through the main workspace, including streaming indicators, incremental event/token rendering, assembled text preview, latency metrics, manual stop/disconnect, and early stream close handling.
- **FR-008**: Automated interactive UI tests MUST verify live GraphQL execution (POST and GET modes, variables, and active body variant selection), live WebSocket lifecycle (connect, send, receive echo in timeline, disconnect, and clean teardown on tab close), and live gRPC unary execution (schema/method discovery, JSON payload, metadata, and structured status/headers/trailers response display).
- **FR-009**: Automated interactive UI tests MUST cover the full MCP client workspace end-to-end against a local server (Streamable HTTP and Legacy HTTP+SSE connection, tool invocation via form and raw JSON, resource listing/reading/subscription, prompt inspection, bi-directional JSON-RPC log inspection, interactive sampling/elicitation callback handling, disconnect, and tab-close cleanup).
- **FR-010**: Automated interactive UI tests MUST cover the RabbitMQ workspace, including mode switching (Publish, Subscribe, RPC), conditional tab visibility and fallback in Subscribe mode, editing and saving AMQP/header/payload fields, clear UI error display when a broker is unreachable, and live Publish/Subscribe/RPC with acknowledge/reject flows when a broker is available.
- **FR-011**: Automated interactive UI tests MUST cover all previously untested dialogs and settings workflows: Export Collections dialog (selection, Select All, JSON/YAML format, export output), Embedded MCP Server settings (enable/disable, live status, port change restart, config snippet copy), Theme switching (Light/Dark/System in Settings and toolbar with persistence), and Top Toolbar environment picker (including inherited environment variable resolution).
- **FR-012**: Automated interactive UI tests MUST verify all main-workspace global keyboard shortcuts (Send, Save, New Tab, Close Tab clean/dirty, Open Settings, Move Tab Left/Right), multi-tab dirty-close confirmation (Save All, Discard All, Cancel), sidebar-initiated delete/move/rename actions paired with Undo snackbar restoration, bottom-panel tab/clear/collapse interactions, and detached response viewer synchronization.
- **FR-013**: Automated resilience tests MUST verify crash-free recovery from corrupted or truncated workspace/tab/settings files, graceful handling of orphaned restored tabs, and clean persistence and session teardown during window-close shutdown.
- **FR-014**: Automated security tests MUST verify that secret environment variables and external secrets never leak in plaintext into Console logs, Network logs, Diagnostics logs, saved Request History, crash/bug-report payloads, or unredacted command exports.
- **FR-015**: Automated boundary and layout tests MUST verify deterministic handling of circular/unresolved variables, strict JSON transmission of typed dynamic variables under JSON5 enabled/disabled settings, safe binary response handling/download, usable rendering at the minimum 800×600 window size, 15–20+ tab overflow and mid-flight tab close, 5-level nested folder operations, malformed import rejection without state corruption, and multi-protocol export -> delete -> re-import UI round-trips.
- **FR-016**: Visual regression baseline snapshots MUST be committed for primary workspace screens and key dialogs and verified automatically to detect unintended layout or styling changes.

### Key Entities

- **Quality Gate Suite**: The complete set of automated unit, integration, interactive UI, visual baseline, and packaged-runtime verification checks required to pass on pull requests and release candidates.
- **Interactive UI End-to-End Scenario**: An automated user journey driven exclusively through real visible desktop interface interactions (typing, clicking, keyboard shortcuts, menu selections) connected to a deterministic local test server and verifying observable UI outcomes.
- **Packaged Deliverable Smoke Verification**: An automated post-packaging check executed against both the universal runnable archive and platform-specific native standalone distributables to validate runtime completeness, persistence, scripting, server modes, and bundled legal files.
- **Visual Regression Baseline**: Committed reference renderings of primary workspace layouts and key dialogs against which subsequent automated builds are compared.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of the 10 product subsystems execute and pass on every pull request, main-branch push, and release candidate, with zero silently skipped required integration tests in automated pipelines and zero disabled or ignored tests remaining.
- **SC-002**: 100% of supported protocols (HTTP, SSE/LLM streaming, GraphQL, WebSocket, gRPC, MCP Client, and RabbitMQ) have at least one passing interactive UI end-to-end test verifying live execution or full workflow behavior through the main desktop workspace.
- **SC-003**: 100% of user-facing dialogs, settings panes, and main-workspace global keyboard shortcuts have automated interactive UI verification with zero untested major UI surfaces remaining.
- **SC-004**: 100% of release packaging targets (universal package plus macOS, Linux, and Windows native distributables) pass expanded packaged-runtime smoke verification (startup/shutdown, isolated persistence, script evaluation, embedded agent server HTTP and standard-I/O handshakes, startup-optimization pass, and bundled legal files) before release publication.
- **SC-005**: Zero plaintext secret values appear in any tested log output, saved history record, crash report payload, or redacted command export across all secret-handling test scenarios.
- **SC-006**: 100% of targeted resilience and boundary scenarios (corrupt storage recovery, orphaned tab restoration, circular variables, binary responses, 800×600 minimum window layout, 15–20+ tab overflow, malformed import rejection, and multi-protocol export/re-import round-trip) pass deterministically with zero crashes or data corruption.
- **SC-007**: Committed visual regression baselines cover the primary workspace views and core dialogs and pass automated verification with zero unexpected visual diffs on clean builds.

## Assumptions

- All interactive UI end-to-end tests will run against local, self-contained deterministic test servers (and local container services where required for RabbitMQ/OAuth2/vaults) so tests never depend on external internet connectivity or third-party cloud availability.
- Live RabbitMQ message-exchange UI assertions execute whenever the local test broker container is running (and fail hard if unavailable in automated CI); RabbitMQ editor interaction, mode-switching, persistence, and unreachable-broker error tests run unconditionally without requiring a broker.
- Secret redaction in command exports (such as Copy as cURL/Python/PowerShell) will follow the application's documented redaction or variable-placeholder policy so raw secret values are never exposed unintentionally.
- Visual regression baselines will be generated and verified under a consistent reference rendering environment to avoid cross-OS font anti-aliasing false positives while still catching layout, clipping, and theming regressions.
- Implementing missing tests may uncover genuine bugs in existing production code; fixing any defects revealed by the new tests is in scope so the entire suite passes cleanly for the 1.0.0 release.
