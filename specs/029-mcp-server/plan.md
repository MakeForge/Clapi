# Implementation Plan: Clapi Model Context Protocol (MCP) Server

**Branch**: `029-mcp-server` | **Date**: 2026-11-18 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/029-mcp-server/spec.md`

---

## Summary

This feature adds a comprehensive **Model Context Protocol (MCP) Server** for Clapi in a dedicated `:mcp-server` module, supporting both **Headless CLI Stdio Mode** (`--mcp [--dir <path>]`) and **Embedded Desktop HTTP Mode** (`http://127.0.0.1:<port>/mcp` with live `AppState` UI synchronization). It exposes Clapi's multi-protocol request authoring (HTTP, gRPC, GraphQL, WebSocket), collection/folder hierarchy, scoped typed variables (`regular`, `secret`, `dynamic` across `string`, `number`, `boolean`, `object`), request and test suite execution, JSONPath/byte-range response inspection, specification import/export (OpenCollection, OpenAPI 3.0+, Insomnia), and Git synchronization through MCP Resources, Tools, and Prompts—guarded by an immutable **Zero-Exfiltration `SecretSanitizer`** pipeline.

---

## Technical Context

**Language/Version**: Kotlin 2.1.21 (KMP `jvm("desktop")`)
**Primary Dependencies**: `:core-model` (`McpModels.kt`), `:core-network`, `:core-storage`, `:core-scripting`, Ktor Server `3.6.1` (`ktor-server-core`, `ktor-server-netty`), `kotlinx.serialization` `1.8.1`, `kotlinx.coroutines` `1.10.2`
**Storage**: OpenCollection v1.0.0 canonical document (`workspace.json`) via `CanonicalCollectionRepository`, private workspace state (`private-state.json`), and `SettingsRepository` (`PlatformStorage`)
**Testing**: `kotlin.test`, `kotlinx.coroutines.test`, Ktor `ktor-server-test-host`, Compose Desktop UI tests, `:qa-tests` E2E integration tests
**Target Platform**: Desktop JVM (macOS, Linux, Windows) — Headless CLI & Compose Desktop GUI
**Project Type**: Multi-module Kotlin Multiplatform desktop application
**Performance Goals**: `< 4 KB` payload for 100-request `clapi://tree`; bounded `4 KB` default body preview on execution; `< 10 ms` overhead for `SecretSanitizer` and `NodeLookupResolver`
**Constraints**: Zero plaintext exfiltration of secret variables, auth credentials, or external vault secrets across all resources, tools, logs, exports, and error messages; zero non-JSON-RPC bytes emitted to `stdout` in `--mcp` stdio mode

---

## Constitution Check

*GATE: Passed before Phase 0 research. Re-evaluated and passed after Phase 1 design.*

- [x] **I. Library-First & Multi-Module Isolation**: Protocol dispatcher, `SecretSanitizer`, `NodeLookupResolver`, `ResponseInspector`, `HeadlessWorkspaceBackend`, and transports live in a self-contained `:mcp-server` module depending only on `:core-*` modules. Live UI synchronization (`AppStateWorkspaceBackend`) and settings UI live in `:ui-shared`, and CLI `--mcp` entry routing lives in `:ui-desktop`.
- [x] **II. CLI & Text I/O Interface**: Headless `--mcp` mode communicates strictly over standard input (`stdin`) and standard output (`stdout`) with newline-delimited JSON-RPC 2.0 envelopes and all operational diagnostics routed to `stderr`.
- [x] **III. Test-First & Verifiability**: Every tool, resource template, prompt, identifier resolver, JSONPath inspector, and secret sanitization rule is independently unit-tested in `:mcp-server` and integration-tested in `:qa-tests`.
- [x] **IV. Simplicity & Zero-Bloat Dependencies**: Reuses `:core-model/McpModels.kt` and Ktor `3.6.1` already present in `gradle/libs.versions.toml` without adding external MCP SDK bloat.

---

## Project Structure

### Documentation (this feature)

```text
specs/029-mcp-server/
├── plan.md                                      # This file
├── research.md                                  # Phase 0 architectural decisions & research
├── data-model.md                                # Phase 1 DTOs, class diagram, and invariants
├── quickstart.md                                # Runnable verification commands & scenarios
├── checklists/
│   └── requirements.md                          # Spec quality checklist
└── contracts/
    ├── mcp-resources-prompts-contract.md        # Resources, templates, prompts, and transports
    ├── mcp-tools-contract.md                    # 15 MCP tools input/output schemas
    └── security-sanitization-contract.md        # Zero-exfiltration SecretSanitizer contract
```

### Source Code Impact

```text
settings.gradle.kts                              # Include ":mcp-server"

mcp-server/                                      # NEW dedicated MCP server module
├── build.gradle.kts                             # KMP jvm("desktop"), depends on :core-model, :core-network, :core-storage, :core-scripting, ktor-server-netty
└── src/
    ├── commonMain/kotlin/io/github/makeforge/clapi/mcp/server/
    │   ├── ClapiMcpServer.kt                    # JSON-RPC 2.0 MCP protocol dispatcher (initialize, tools/*, resources/*, prompts/*, ping, completion/*, logging/*)
    │   ├── backend/
    │   │   ├── McpWorkspaceBackend.kt           # Backend interface & DTO models
    │   │   ├── HeadlessWorkspaceBackend.kt      # Direct file/repository-backed implementation for CLI & tests
    │   │   └── NodeLookupResolver.kt            # Dual UUID & slash-path resolution with disambiguation errors
    │   ├── security/
    │   │   └── SecretSanitizer.kt               # Zero-exfiltration variable, auth, header, and full-text echo scrubber
    │   ├── inspection/
    │   │   ├── ResponseSnapshotCache.kt         # In-memory LRU cache (max 50) of sanitized ExecutionSnapshots
    │   │   └── JsonPathInspector.kt             # Lightweight JsonElement JSONPath & UTF-8 byte-slice evaluator
    │   ├── tools/
    │   │   ├── WorkspaceTools.kt                # clapi_list_tree, clapi_manage_collection, clapi_manage_folder
    │   │   ├── RequestTools.kt                  # clapi_get_request, clapi_upsert_request, clapi_delete_request
    │   │   ├── EnvironmentTools.kt              # clapi_list_environments, clapi_set_active_environment, clapi_upsert_environment, clapi_delete_environment, clapi_set_variable
    │   │   ├── ExecutionTools.kt                # clapi_execute_request, clapi_inspect_response, clapi_run_test_suite
    │   │   └── SyncImportExportTools.kt         # clapi_import_specification, clapi_export_collection, clapi_git_sync
    │   ├── resources/
    │   │   └── McpResourceRegistry.kt           # clapi://tree, clapi://collections/{id}, clapi://requests/{id}, clapi://environments, clapi://environments/{name}, clapi://responses/{id}/latest, clapi://logs/console
    │   ├── prompts/
    │   │   └── McpPromptRegistry.kt             # debug_api_failure, generate_api_tests, curl_to_clapi_request
    │   └── transport/
    │       ├── StdioMcpServerRunner.kt          # Headless stdin/stdout runner with System.out -> System.err guard
    │       └── KtorMcpServerHost.kt             # Embedded 127.0.0.1 Ktor Netty host (/mcp Streamable HTTP & /mcp/sse Legacy SSE)
    └── commonTest/kotlin/io/github/makeforge/clapi/mcp/server/
        ├── SecretSanitizerTest.kt               # Exhaustive unit tests for secret masking & echo scrubbing
        ├── NodeLookupResolverTest.kt            # UUID, slash path, escaped slash, and ambiguity error tests
        ├── JsonPathInspectorTest.kt             # JSONPath query & byte-range pagination tests
        ├── ClapiMcpServerProtocolTest.kt        # Handshake, resources, prompts, and tool dispatch tests
        └── HeadlessWorkspaceBackendTest.kt      # CRUD, variable types/kinds, import/export, and execution tests

ui-shared/
├── build.gradle.kts                             # Add api(project(":mcp-server"))
└── src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/
    ├── mcp/server/
    │   ├── AppStateWorkspaceBackend.kt          # Live AppState-synchronized McpWorkspaceBackend implementation
    │   └── EmbeddedMcpServerController.kt       # Lifecycle manager starting/stopping KtorMcpServerHost from AppState
    ├── state/
    │   ├── AppSettings.kt                       # Add mcpServerEnabled (false) and mcpServerPort (3333)
    │   └── AppState.kt                          # Integrate EmbeddedMcpServerController lifecycle & disposal
    ├── persistence/
    │   └── SettingsRepository.kt                # Persist settings.mcpServerEnabled & settings.mcpServerPort
    └── components/dialogs/settings/
        ├── SettingsDialog.kt                    # Add MCP_SERVER section entry
        └── sections/McpServerSettingsSection.kt # Enable toggle, port field, server status badge, and client config snippet

ui-desktop/
└── src/desktopMain/kotlin/io/github/makeforge/clapi/ui/desktop/
    └── Main.kt                                  # Intercept `--mcp` and `--dir <path>` before AWT/Compose init and invoke StdioMcpServerRunner

qa-tests/
└── src/test/kotlin/io/github/makeforge/clapi/qa/
    └── McpServerE2ETest.kt                      # End-to-end test over HTTP & Stdio verifying multi-protocol authoring, execution, test suites, and zero-exfiltration

docs/
└── features/
    └── mcp-server.md                            # User documentation for Clapi MCP Server (CLI & Desktop setup, resources, tools, security model)
```

---

## Phase 0: Outline & Research

- [x] **Research Topic 1**: First-party MCP protocol engine on `:core-model/McpModels.kt` vs. external SDK ([research.md](research.md#research-topic-1-mcp-protocol-engine-vs-external-sdk-dependency)).
- [x] **Research Topic 2**: Dedicated `:mcp-server` module and dual-mode `McpWorkspaceBackend` architecture ([research.md](research.md#research-topic-2-module-architecture--dual-mode-workspace-synchronization-mcpworkspacebackend)).
- [x] **Research Topic 3**: Zero-exfiltration `SecretSanitizer` pipeline and pre-cache echo scrubbing ([research.md](research.md#research-topic-3-zero-exfiltration-secret-sanitization-pipeline-secretsanitizer)).
- [x] **Research Topic 4**: Dual UUID/slash-path identifier resolution and response context budgeting ([research.md](research.md#research-topic-4-dual-identifier-resolution-uuid--slash-path-and-response-context-budgeting)).
- [x] **Research Topic 5**: Headless stdio `System.out` framing guard and Desktop settings integration ([research.md](research.md#research-topic-5-headless-stdio-framing--desktop-settings-integration)).

---

## Phase 1: Design & Contracts

- [x] **Data Model**: [data-model.md](data-model.md) created with architecture diagram, sanitized DTO definitions, `ExecutionSnapshot` cache schema, settings fields, and validation invariants.
- [x] **Contracts**:
  - [mcp-resources-prompts-contract.md](contracts/mcp-resources-prompts-contract.md)
  - [mcp-tools-contract.md](contracts/mcp-tools-contract.md)
  - [security-sanitization-contract.md](contracts/security-sanitization-contract.md)
- [x] **Quickstart Validation Guide**: [quickstart.md](quickstart.md) created with automated test commands and manual verification scenarios.
