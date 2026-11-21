# Phase 0 Research: Clapi Model Context Protocol (MCP) Server

**Feature**: `029-mcp-server` | **Date**: 2026-11-18 | **Spec**: [spec.md](spec.md)

---

## Research Topic 1: MCP Protocol Engine vs. External SDK Dependency

### Decision
Build the Clapi MCP server protocol handler and transports in a dedicated `:mcp-server` Kotlin Multiplatform (`jvm("desktop")`) module directly on top of Clapi's existing `:core-model` MCP JSON-RPC 2.0 domain types (`McpModels.kt`) and Ktor Server (`ktor-server-core`, `ktor-server-netty`), rather than introducing an external `io.modelcontextprotocol:kotlin-sdk` dependency.

### Rationale
1. **First-Party Protocol Models Already Exist**: `:core-model` (`io.github.makeforge.clapi.core.model.McpModels.kt`) already defines the full MCP `2025-06-18` and legacy `2024-11-05` JSON-RPC 2.0 data types (`JsonRpcEnvelope`, `JsonRpcError`, `McpInitializeParams`, `McpInitializeResult`, `McpServerCapabilities`, `McpTool`, `McpListToolsResult`, `McpToolResult`, `McpResource`, `McpResourceTemplate`, `McpReadResourceResult`, `McpPrompt`, `McpGetPromptResult`, `McpContent`, `McpCompleteRequest`, `McpCompleteResult`).
2. **Zero Version Conflict with Ktor 3.6.1**: Clapi uses Ktor `3.6.1` and Kotlin `2.1.21` (in `gradle/libs.versions.toml`). Avoiding a third-party SDK wrapper prevents transitive Ktor version conflicts and avoids duplicating JSON-RPC envelope serializers.
3. **Proven In-Repo Transport Patterns**: `sample-server` (`McpRoutes.kt` and `McpMock.kt`) and `:core-network` (`McpClient.kt`) already implement and test MCP Streamable HTTP (`POST /mcp`, `GET /mcp`, `DELETE /mcp` with `Mcp-Session-Id`), legacy SSE (`GET /mcp/sse`, `POST /mcp/messages`), and newline-delimited Stdio JSON-RPC framing.
4. **Release Artifact Size Preservation**: Clapi actively optimizes native package and fat-JAR sizes (spec `022-release-artifact-size-optimization`); reusing existing `ktor-server-netty` and `kotlinx-serialization-json` dependencies keeps the distribution lean.

### Alternatives Considered
- **Adopting `io.modelcontextprotocol:kotlin-sdk`**: Rejected because it introduces parallel model classes duplicating `:core-model/McpModels.kt`, risks Ktor version mismatches with Ktor `3.6.1`, and adds unnecessary transitive artifact weight.
- **Embedding MCP Server inside `:ui-shared`**: Rejected because headless CLI invocation (`clapi --mcp`) and unit testing of MCP tools/resources should not require Compose UI initialization or AWT dependencies.

---

## Research Topic 2: Module Architecture & Dual-Mode Workspace Synchronization (`McpWorkspaceBackend`)

### Decision
Create a dedicated `:mcp-server` module that depends only on `:core-model`, `:core-network`, `:core-storage`, and `:core-scripting`. Define a clean `McpWorkspaceBackend` interface in `:mcp-server` with two concrete implementations:
1. **`HeadlessWorkspaceBackend` (in `:mcp-server`)**: Operates directly on `CanonicalCollectionRepository` (`workspace.json`), headless private state (`private-state.json` for global variables and history), `RequestExecutionService`, `ClapiScriptEngine`, `SpecificationImporter`, `OpenApiExporter`, and `GitRepositoryManager`. Used in `--mcp` CLI stdio mode and headless integration tests.
2. **`AppStateWorkspaceBackend` (in `:ui-shared`)**: Bridges MCP operations to the live `AppState` instance when the embedded Desktop HTTP server is active, serializing mutations safely so the Compose Desktop UI (`Sidebar`, open tabs, environment picker, console log) updates live in real time and persists via `WorkspaceRepository.saveAsync`.

### Rationale
- **Strict Adherence to Architecture Boundaries**: Per `DEVELOPMENT.md`, `:ui-desktop` depends only on `:ui-shared`, `:ui-shared` depends on feature and core modules, and feature modules (`:mcp-server`) depend only on core modules.
- **No File-Locking or Stale State in Desktop Mode**: If the embedded Desktop server wrote directly to `workspace.json` behind `AppState`'s back, the running GUI would overwrite or miss agent changes. Routing Desktop-mode MCP mutations through `AppStateWorkspaceBackend` ensures immediate UI reactivity and single-writer persistence.

### Alternatives Considered
- **File-Watcher Polling on `workspace.json` in Desktop Mode**: Rejected due to race conditions when a user has unsaved tab state or edits collections concurrently in the GUI.

---

## Research Topic 3: Zero-Exfiltration Secret Sanitization Pipeline (`SecretSanitizer`)

### Decision
Enforce an immutable `SecretSanitizer` layer inside `:mcp-server` that intercepts every outgoing MCP resource read, tool result, prompt message, console log entry, and error diagnostic before JSON-RPC serialization:
1. **Variable & Auth Field Masking**:
   - Any variable with `VariableKind.SECRET` (`secret == true` or `SecretVariable`) has its value replaced with `"********"` and metadata `"secret": true, "isSet": <boolean>`.
   - Request, Folder, and Collection `AuthConfig` / `OpenCollectionAuth` credentials (`password`, `token`, `apiValue`, `oauth2ClientSecret`, `oauth2Password`, JWT secrets) are replaced with `"********"`.
   - External secret manager provider credentials are completely omitted from the MCP surface; environment external secret declarations expose only `name`, `providerType`, `path`/`secretId`/`recordTitle`, `url`/`vaultName`/`projectId`, `targetField`, and `enabled`.
2. **Header Redaction**:
   - All request and response headers matching `authorization`, `proxy-authorization`, `x-api-key`, `api-key`, `token`, `cookie`, `set-cookie`, `x-auth-token` (case-insensitive) or flagged `secret = true` are masked (`"********"`, or `"<scheme> ********"` for `Bearer`/`Basic` schemes).
3. **Full-Text Value Scrubbing (Echo & Error Protection)**:
   - During `clapi_execute_request` and `clapi_run_test_suite`, all secret values resolved for that execution (secret variables across all scopes, auth secrets, and resolved external secret values) with length ≥ 4 characters are collected into an ephemeral `knownSecrets: Set<String>`.
   - Before caching `ExecutionSnapshot` or returning tool/error output, every occurrence of any string in `knownSecrets` (sorted longest-first to avoid partial prefix remnants) in response bodies, response headers, script console logs, and exception messages is replaced with `"[REDACTED_SECRET]"`.

### Rationale
- Target endpoints (such as debug/echo servers or verbose OAuth error payloads) frequently echo sent headers or tokens in the response body. Scrubbing `knownSecrets` before caching `ExecutionSnapshot` guarantees that neither `clapi_execute_request` nor subsequent `clapi_inspect_response` (JSONPath / byte-range) calls can ever exfiltrate a resolved secret.
- Restricting full-text replacement to secrets of length ≥ 4 prevents trivial accidental secrets (like `"1"` or `"a"`) from corrupting JSON structural syntax, while structural field masking still masks `"********"` regardless of length.

### Alternatives Considered
- **Sanitizing only at `clapi_execute_request` output time while storing raw body in `ExecutionSnapshot`**: Rejected because `clapi_inspect_response` or `clapi://responses/{requestId}/latest` could then leak an echoed secret via JSONPath or byte-slice queries. Sanitizing prior to snapshot storage closes this vector completely.

---

## Research Topic 4: Dual Identifier Resolution (UUID & Slash Path) and Response Context Budgeting

### Decision
1. **Dual Identifier Resolver (`NodeLookupResolver`)**:
   - Accepts either an exact item UUID (e.g., `"550e8400-e29b-41d4-a716-446655440000"`) or a slash-delimited hierarchical path (`"CollectionName/FolderName/RequestName"`, with leading/trailing slashes normalized and escaped `\/` supported for names containing literal slashes).
   - If a slash path matches multiple items (e.g., two requests named `"Create"` in the same folder), resolution fails with a structured `AmbiguousIdentifierException` listing all matching items (`id`, `name`, `kind`, `method`, `slashPath`) so the AI agent can immediately re-invoke with the exact UUID.
2. **Response Context Budgeting & Inspection (`ResponseInspector`)**:
   - `clapi_execute_request` defaults to `maxBodyLength = 4096` bytes (configurable per call up to 64 KB).
   - Binary content types (`image/*`, `audio/*`, `video/*`, `application/octet-stream`, `application/pdf`, `application/zip`) omit raw body text and return `"_binary": true, "contentType": "...", "_totalBytes": N`.
   - Text/JSON responses exceeding `maxBodyLength` return the UTF-8 prefix along with `"_truncated": true, "_totalBytes": N, "_hint": "Use clapi_inspect_response with requestId, jsonPath, or byteOffset/length to inspect further."`.
   - `clapi_inspect_response` operates on the cached sanitized `ExecutionSnapshot`, supporting dot/bracket JSONPath queries (`$.a.b[0].c`, `$.items[*]`) via a lightweight in-module JSONElement path evaluator as well as `byteOffset` + `length` windowing.

### Rationale
- AI agents often learn request names from `clapi://tree` or user conversation (`"run Auth/Login"`), making slash paths far more ergonomic than UUIDs, while UUID fallback guarantees precision when names collide.
- Large API payloads (e.g., 500 KB list endpoints) can exhaust LLM token budgets in a single tool call; capping preview size by default while keeping the sanitized response in an in-memory LRU cache (`max 50 entries`) enables surgical JSONPath or paginated inspection without repeating network side effects.

### Alternatives Considered
- **Adding external `com.jayway.jsonpath:json-path`**: Rejected because a clean ~120-line recursive `JsonElement` JSONPath evaluator (`$`, `.field`, `['field']`, `[index]`, `[*]`, `[start:end]`, `..field`) built on `kotlinx.serialization.json` covers 100% of agent inspection queries with zero additional library dependencies.

---

## Research Topic 5: Headless Stdio Framing & Desktop Settings Integration

### Decision
1. **Headless Stdio Launch (`--mcp`)**:
   - When `MainKt` in `:ui-desktop` is invoked with `--mcp` (and optional `--dir <path>`), before any logger or component initializes, capture the real `System.out` `PrintStream` exclusively for the MCP Stdio writer and redirect `System.setOut(System.err)`.
   - This guarantees that any library or JVM diagnostic print statements (`println(...)`) go to `stderr` and never corrupt the newline-delimited JSON-RPC stream on `stdout`.
2. **Desktop Embedded HTTP Server**:
   - Add an **MCP Server** section in `AppSettings` and `SettingsDialog`:
     - `mcpServerEnabled: Boolean` (default `false`)
     - `mcpServerPort: Int` (default `3333`)
   - Bound strictly to `127.0.0.1` using Ktor Netty (`POST /mcp`, `GET /mcp`, `DELETE /mcp` for Streamable HTTP `2025-06-18`, plus `GET /mcp/sse` and `POST /mcp/messages` for Legacy SSE `2024-11-05`).

### Rationale
- Redirecting `System.setOut(System.err)` while holding a private reference to the original `FileDescriptor.out` stream is the gold-standard pattern for stdio MCP servers on the JVM, preventing third-party libraries (e.g., Swagger Parser, Protoc, Javet, Ktor) from ever writing stray text onto the JSON-RPC stdout pipe.
