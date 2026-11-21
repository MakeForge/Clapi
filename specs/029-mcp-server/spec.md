# Feature Specification: Clapi Model Context Protocol (MCP) Server

**Feature Branch**: `029-mcp-server`

**Created**: 2026-11-18

**Status**: Draft

**Input**: User description: "adding an MCP server. A first analysis of another LLM can be found at ./scratch/mcp-server-analysis.md. Look at it with care, since this was just a basic first proposal on what features currently exist, which tools, resources etc. could be implemented."

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Workspace Discovery, Context Reading & Multi-Protocol Request Authoring (Priority: P1)

As an API developer using an AI assistant connected to Clapi via MCP, I want the assistant to browse my workspace hierarchy, inspect collection and request definitions, and create, update, or delete collections, folders, and multi-protocol requests (HTTP, gRPC, GraphQL, and WebSocket) using either unique IDs or human-readable slash paths so that I can build and maintain API collections conversationally.

**Why this priority**: Core foundational capability of the MCP server. Without structured workspace reading and multi-protocol authoring, an AI agent cannot understand existing APIs or help author new requests.

**Independent Test**: Connect an MCP client to Clapi, list the workspace tree, create a new collection and nested folder, author an HTTP request, a gRPC request, a GraphQL request, and a WebSocket request inside that folder, retrieve each request by both UUID and slash path (e.g., `MyCollection/Folder/GetUsers`), update the request configuration, and verify the changes persist in the workspace.

**Acceptance Scenarios**:

1. **Given** a workspace containing collections, nested folders, and requests across multiple protocols, **When** the AI agent reads the `clapi://tree` resource or invokes `clapi_list_tree`, **Then** the server returns a concise hierarchical summary including item IDs, names, item types/methods, and slash paths.
2. **Given** a target collection or folder, **When** the AI agent invokes `clapi_upsert_request` for an HTTP, gRPC, GraphQL, or WebSocket request, **Then** the server creates or updates the request with its protocol-specific settings (headers, query parameters, body variants/payloads, authentication, pre-request/test scripts, or `.proto` configurations) and persists the workspace.
3. **Given** an existing request in the workspace, **When** the AI agent reads `clapi://requests/{requestId}` or invokes `clapi_get_request` using either the request's UUID or its slash path, **Then** the server returns the complete sanitized request definition.
4. **Given** a collection or folder with shared defaults, **When** the AI agent invokes `clapi_manage_collection` or `clapi_manage_folder`, **Then** the agent can configure collection/folder metadata, default authentication, shared headers, scoped variables, scripts, and collection-level Protocol Buffer files and import paths.
5. **Given** an existing collection, folder, or request, **When** the AI agent attempts to delete it without explicit confirmation (`confirm: true`), **Then** the server rejects the destructive operation with an actionable validation error.

---

### User Story 2 - Request Execution, Test Suite Runner & Context-Safe Response Inspection (Priority: P1)

As an API developer debugging or verifying endpoints with an AI assistant, I want the assistant to execute saved requests (HTTP, gRPC, and GraphQL), run folder or collection test suites, and inspect large responses incrementally via JSONPath or byte-range pagination so that the assistant can verify API behavior and diagnose failures without overflowing its context window.

**Why this priority**: Executing requests and evaluating test assertions is the primary interactive loop that differentiates an active API client MCP server from static file editing.

**Independent Test**: Execute an HTTP request with post-request test assertions and a large JSON response (>50 KB) via `clapi_execute_request`, verify that the returned preview is capped at the configured byte budget with truncation metadata, use `clapi_inspect_response` with a JSONPath expression and byte offset to extract specific nested fields, and run `clapi_run_test_suite` on the parent folder to verify the aggregated pass/fail matrix.

**Acceptance Scenarios**:

1. **Given** a saved HTTP, gRPC, or GraphQL request, **When** the AI agent invokes `clapi_execute_request` with an optional environment name and optional transient variable overrides, **Then** the server resolves all variables (including dynamic and external secret variables), runs pre-request scripts, executes the network call, runs post-request test scripts, caches the latest response snapshot, and returns status code, timing metrics, sanitized headers, body preview, and test assertion results.
2. **Given** an execution response whose body exceeds the preview budget (default 4,096 bytes), **When** `clapi_execute_request` returns or `clapi://responses/{requestId}/latest` is read, **Then** the body is truncated to the budget and accompanied by `_truncated: true`, `_totalBytes`, and guidance on using `clapi_inspect_response`.
3. **Given** a cached response from a previous request execution, **When** the AI agent invokes `clapi_inspect_response` with a JSONPath query (e.g., `$.data.items[0].id`) or a `byteOffset` and `length`, **Then** the server returns only the matching sanitized JSON subset or byte slice without re-executing the network request.
4. **Given** a collection or folder containing multiple executable requests with post-request assertions, **When** the AI agent invokes `clapi_run_test_suite`, **Then** the server executes the requests sequentially, evaluates all assertions, and returns an aggregated summary of passed/failed requests and individual assertion results.
5. **Given** script console output generated during pre-request or post-request script execution, **When** the AI agent reads `clapi://logs/console`, **Then** the server returns recent sanitized console entries and script diagnostic messages.

---

### User Story 3 - Zero-Exfiltration Secret & Credential Protection (Priority: P1)

As a security-conscious developer, I want strict guarantees that the MCP server never exposes plaintext secret variable values, authentication credentials, or external secret manager tokens to the AI agent in any resource read, tool output, execution response, or error message, while still allowing the agent to reference secrets by name and set new secret values in a write-only manner.

**Why this priority**: Non-negotiable security boundary. Sending raw API keys, bearer tokens, vault credentials, or resolved secrets into an LLM context creates a severe credential exfiltration risk.

**Independent Test**: Create a secret variable (`kind = secret`) with value `"super-secret-token-999"`, configure Bearer auth and an external secret manager credential, read the environment and request via MCP tools/resources, execute the request against an endpoint that echoes request headers and body back in the response, and verify that `"super-secret-token-999"` and the vault credentials never appear in any MCP payload (replaced by `"********"` or `"[REDACTED_SECRET]"`).

**Acceptance Scenarios**:

1. **Given** a secret variable (`kind = secret`) in Global, Environment, Collection, Folder, or Request scope, **When** any MCP resource or tool returns variable metadata, **Then** the variable's value is masked as `"********"` and marked with `secret: true` and `isSet: true/false`.
2. **Given** an AI agent configuring a secret variable via `clapi_set_variable` with `kind = secret`, **When** the value is saved, **Then** the server stores the secret value for internal request execution, returns a masked confirmation to the agent, and never allows the plaintext value to be read back.
3. **Given** a request, folder, or collection configured with authentication (Basic password, Bearer token, API Key value, JWT secret, or OAuth 2.0 client secret/tokens), **When** inspected via MCP resources or tools, **Then** all credential fields are masked as `"********"` while non-sensitive auth configuration (type, username, header key, OAuth 2.0 flow and endpoint URLs) remains visible.
4. **Given** configured external secret managers (HashiCorp Vault, AWS Secrets Manager, Azure Key Vault, GCP Secret Manager, Keeper Security, or KeePassXC), **When** accessed through MCP, **Then** provider authentication credentials are completely inaccessible, and environment external secret declarations expose only reference metadata (variable name, provider type, secret path/ID, target field).
5. **Given** a request execution that interpolates secret variables, auth credentials, or external secrets, **When** the execution result, latest response resource, console log, or failure diagnostic is returned to the AI agent, **Then** sensitive headers (`Authorization`, `Proxy-Authorization`, `X-Api-Key`, `Api-Key`, `Token`, `Cookie`, `Set-Cookie`, `X-Auth-Token`) are masked and any occurrence of a known resolved secret value in the response body, headers, console output, or error message is scrubbed with `"[REDACTED_SECRET]"`.

---

### User Story 4 - Environment & Scoped Typed Variable Management (Priority: P2)

As an API developer managing multi-environment configurations, I want the AI assistant to list, create, update, and switch environments (including inheritance chains via `extends` and external secret mappings) and manage typed variables (`regular`, `secret`, and scripted `dynamic` variables with `string`, `number`, `boolean`, or `object` data types) across Global, Environment, Collection, and Folder scopes.

**Why this priority**: API workflows rely heavily on switching between environments (e.g., Local, Staging, Production) and configuring static, secret, or dynamic variables with accurate JSON data types.

**Independent Test**: Use MCP tools to create a base environment `"Base"` and a child environment `"Staging"` extending `"Base"`, add a `regular` `number` variable, a `secret` `string` variable, and a `dynamic` variable with a script and parameters, set `"Staging"` as active, read `clapi://environments/Staging`, and verify effective inherited and local variable projections with secrets masked.

**Acceptance Scenarios**:

1. **Given** multiple environments with inheritance (`extends`), **When** the AI agent reads `clapi://environments` or `clapi://environments/{envName}` or calls `clapi_list_environments`, **Then** the server returns the environment list, active environment selection, inheritance chain, and effective resolved non-secret variables (indicating inherited vs. overridden status and masking secrets).
2. **Given** an environment management request via `clapi_upsert_environment`, **When** creating or updating an environment with `name`, `color`, `extends`, or external secret declarations, **Then** the server validates that `extends` does not introduce a circular inheritance chain before saving.
3. **Given** a variable mutation via `clapi_set_variable`, **When** specifying the target scope (`global`, `environment`, `collection`, or `folder`), variable `kind` (`regular`, `secret`, or `dynamic`), `variableType` (`string`, `number`, `boolean`, or `object`), value or dynamic script/parameters, **Then** the variable is persisted in the specified scope with full type fidelity.
4. **Given** a valid environment name, **When** the AI agent calls `clapi_set_active_environment`, **Then** the active environment selection is updated and subsequent executions default to that environment.

---

### User Story 5 - Specification Import, Export & Git Sync Operations (Priority: P2)

As an API developer integrating external specifications and version-controlled collections, I want the AI assistant to import collections from OpenAPI 3.0+, OpenCollection (JSON/YAML), or Insomnia (v4/v5) specifications, export collections to OpenCollection (JSON/YAML) or OpenAPI 3.0.3 formats, and run Git synchronization operations (`status`, `commit`, `pull`, `push`) on Git-backed collections.

**Why this priority**: Enables rapid onboarding of existing API specs into Clapi, generating OpenAPI documentation from Clapi collections, and keeping team collections synchronized in Git.

**Independent Test**: Invoke `clapi_import_specification` with an OpenAPI 3.0 YAML specification to create a new collection, invoke `clapi_export_collection` to export it as both OpenCollection YAML and OpenAPI 3.0.3 YAML, and verify `clapi_git_sync` (`status` and `commit`) on a Git-backed collection.

**Acceptance Scenarios**:

1. **Given** raw specification content or a local file path containing an OpenAPI 3.0/3.1, OpenCollection v1.0.0 (JSON/YAML), or Insomnia v4/v5 export, **When** the AI agent calls `clapi_import_specification`, **Then** the server validates and imports the specification as a new collection with a unique name and returns the imported collection summary.
2. **Given** an unsupported Swagger 2.0 specification or malformed file passed to `clapi_import_specification`, **When** scanned, **Then** the server returns a clear diagnostic error message explaining why the import failed.
3. **Given** an existing collection, **When** the AI agent calls `clapi_export_collection` specifying format `opencollection-json`, `opencollection-yaml`, `openapi-json`, or `openapi-yaml`, **Then** the server returns the exported specification with all secret values omitted or masked.
4. **Given** a Git-backed collection, **When** the AI agent invokes `clapi_git_sync` with action `status`, `commit`, `pull`, or `push`, **Then** the server executes the corresponding Git workflow, reloads collection state on `pull`, and returns the resulting repository status.

---

### User Story 6 - Dual Operating Modes (Headless CLI & Desktop Embedded) and Guided Workflow Prompts (Priority: P3)

As a Clapi user working across both terminal-based AI coding agents and the Clapi Desktop GUI, I want to run the Clapi MCP server either headlessly via CLI (`--mcp` over standard input/output) or as an embedded local server inside the Clapi Desktop app with live UI synchronization, and access built-in MCP prompt templates for common API debugging and authoring tasks.

**Why this priority**: Provides seamless integration for both standalone CLI agent setups and interactive desktop workflows where the user watches and collaborates with the AI agent in real time.

**Independent Test**: Launch Clapi in headless `--mcp` stdio mode, perform an MCP handshake, list prompts (`debug_api_failure`, `generate_api_tests`, `curl_to_clapi_request`), and invoke a tool; then enable the embedded MCP server in Desktop settings, invoke a request creation tool over HTTP, and verify the new request immediately appears in the Desktop sidebar.

**Acceptance Scenarios**:

1. **Given** the user launches Clapi with the `--mcp` command-line flag, **When** an MCP client connects over standard input/output (`stdio`), **Then** Clapi starts headlessly without opening a desktop window, loads the workspace from the configured storage directory (`--dir`, `CLAPI_DIR`, or `~/.clapi`), and serves all MCP resources, tools, and prompts without emitting non-protocol banner noise to standard output.
2. **Given** the Clapi Desktop application is running with the embedded local MCP server enabled in Settings, **When** an AI agent mutates collections, folders, requests, or environments via MCP tools, **Then** the changes are immediately reflected in the live Desktop UI state and persisted to disk.
3. **Given** an MCP client querying available prompts, **When** listing or retrieving prompts, **Then** the server provides structured workflow prompts for `debug_api_failure` (diagnosing a failing request using its config, sanitized response, and script logs), `generate_api_tests` (writing post-request assertions based on response schema), and `curl_to_clapi_request` (converting a raw cURL command into a saved Clapi request).

---

### Edge Cases

- **Ambiguous Slash Paths or Duplicate Names**: If two requests or folders in the same parent share a name or if a slash path matches multiple items, path resolution fails with an explicit disambiguation error listing the matching UUIDs so the agent can retry with the exact UUID.
- **Large or Binary Responses**: When an HTTP response returns a multi-megabyte payload or binary content (e.g., images, PDFs, octet-stream), `clapi_execute_request` never dumps raw binary or unbounded text into the MCP response; binary bodies are summarized by content type and byte size, and text bodies are truncated to `maxBodyLength` with pagination instructions.
- **Secret Value Echoed by Target Server**: If a request sends a secret variable or token to an echo endpoint (or receives an error message repeating the token) and the secret is at least 4 characters long, the sanitizer replaces every occurrence of that resolved secret in the response body, headers, and logs with `"[REDACTED_SECRET]"`.
- **Short or Common Secret Values**: To avoid corrupting normal response structure (such as a secret accidentally set to `"1"` or `"a"`), full-text body redaction only scrubs resolved secret strings of length ≥ 4 characters (while header and variable value fields are always masked regardless of length).
- **Concurrent Desktop UI and MCP Mutations**: When running in embedded Desktop mode, workspace state mutations triggered by MCP tool calls are serialized on the application state synchronization boundary so concurrent UI edits and agent tool calls do not corrupt `workspace.json` or `private-state.json`.
- **Unsupported or Long-Lived Streaming in Synchronous Execution**: For finite SSE or NDJSON streams (such as LLM chat completions), `clapi_execute_request` collects stream events up to the configured timeout and returns assembled text and token timing metrics; for WebSocket requests, authoring and inspection are supported via request tools while execution tools clearly report if an operation requires an interactive persistent socket session.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide an MCP-compliant server exposing Clapi's workspace, collections, folders, requests, environments, variables, execution engine, import/export capabilities, and Git sync operations through MCP Resources, Tools, and Prompts.
- **FR-002**: The system MUST support two operating modes:
  1. **Headless Stdio Mode**: Invoked via a `--mcp` CLI flag (with optional `--dir <path>` workspace directory override), communicating strictly over standard input/output with zero non-JSON-RPC output on `stdout`.
  2. **Embedded Desktop HTTP Mode**: Hosted locally within the Clapi Desktop application (bound to `127.0.0.1` on a configurable port, toggleable in application settings) and synchronized live with the open desktop workspace state.
- **FR-003**: All tools and resources that accept a collection, folder, or request identifier MUST support both exact UUIDs and human-readable slash-separated paths (e.g., `"CollectionName/FolderName/RequestName"`), returning a disambiguation error with candidate UUIDs if a slash path is ambiguous.
- **FR-004**: The server MUST expose the following read-only MCP Resources:
  - `clapi://tree`: Hierarchical summary of collections, folders, and requests (IDs, names, types, HTTP methods, slash paths).
  - `clapi://collections/{collectionId}`: Sanitized collection structure and collection-level settings.
  - `clapi://requests/{requestId}`: Sanitized request definition across HTTP, gRPC, GraphQL, or WebSocket protocols.
  - `clapi://environments`: List of environments, active environment name, inheritance links (`extends`), and external secret provider types.
  - `clapi://environments/{envName}`: Effective variable projection for an environment (including inherited variables and override metadata) with all secret values masked.
  - `clapi://responses/{requestId}/latest`: Latest cached execution response snapshot for a request (sanitized headers, truncated body preview, metrics, and test assertion results).
  - `clapi://logs/console`: Recent sanitized pre-request and post-request script console logs and diagnostics.
- **FR-005**: The server MUST provide Workspace, Collection, and Folder management tools:
  - `clapi_list_tree`: Returns the workspace hierarchy with optional filtering by collection, protocol kind, or search query.
  - `clapi_manage_collection`: Creates, updates, or deletes a collection (including description, default auth, headers, variables, scripts, `.proto` files/import paths, and Git storage settings; `delete` requires `confirm: true`).
  - `clapi_manage_folder`: Creates, updates, moves, or deletes a folder within a collection (`delete` requires `confirm: true`).
- **FR-006**: The server MUST provide Multi-Protocol Request authoring tools:
  - `clapi_get_request`: Retrieves full sanitized details of a request by UUID or slash path.
  - `clapi_upsert_request`: Creates or updates an HTTP, gRPC, GraphQL, or WebSocket request, supporting all protocol-specific fields (HTTP method, URL, query params, headers, body type and content/form entries, auth config, pre-request and test scripts, gRPC service/method/metadata/proto config, GraphQL query/variables/variants, WebSocket message variants).
  - `clapi_delete_request`: Deletes a request by UUID or slash path (requires `confirm: true`).
- **FR-007**: The server MUST provide Environment and Typed Variable management tools:
  - `clapi_list_environments`: Lists all environments, active selection, inheritance chains, and sanitized variable summaries across Global and Environment scopes.
  - `clapi_set_active_environment`: Sets or clears the active environment.
  - `clapi_upsert_environment`: Creates or updates an environment (`name`, `color`, `extends`, description, external secret declarations), rejecting circular `extends` chains.
  - `clapi_delete_environment`: Deletes an environment (requires `confirm: true`).
  - `clapi_set_variable`: Creates, updates, or deletes a variable in `global`, `environment`, `collection`, or `folder` scope, supporting variable `kind` (`regular`, `secret`, `dynamic`), `variableType` (`string`, `number`, `boolean`, `object`), value, dynamic script, and dynamic parameters.
- **FR-008**: The server MUST provide Execution and Response Inspection tools:
  - `clapi_execute_request`: Executes an HTTP, gRPC, or GraphQL request with full variable interpolation (scoped variables, inherited environment variables, dynamic variables, and external secrets), pre-request script execution, and post-request test evaluation; supports optional `environmentName`, `transientVariables` (non-persisted key-value overrides), and `maxBodyLength` (default 4,096 bytes).
  - `clapi_inspect_response`: Inspects the cached response of a previously executed request using a `jsonPath` query and/or `byteOffset` and `length` slice without re-running the network call.
  - `clapi_run_test_suite`: Executes all executable requests within a specified collection or folder sequentially and returns an aggregated test assertion pass/fail report.
- **FR-009**: The server MUST provide Import, Export, and Git Synchronization tools:
  - `clapi_import_specification`: Imports OpenAPI v3.0/v3.1, OpenCollection v1.0.0 (JSON/YAML), or Insomnia v4/v5 specifications from raw text or a file path.
  - `clapi_export_collection`: Exports a collection to sanitized OpenCollection (`JSON` or `YAML`) or OpenAPI 3.0.3 (`JSON` or `YAML`).
  - `clapi_git_sync`: Performs `status`, `commit`, `pull`, or `push` operations on a Git-backed collection.
- **FR-010**: The server MUST enforce a strict **Zero-Exfiltration Secret Sanitization Pipeline** across all resources, tools, prompts, logs, and error responses:
  - All secret variables (`kind = secret` / `secret = true`) MUST have their values replaced with `"********"` when read; setting a secret variable is strictly write-only.
  - All authentication credentials (passwords, bearer tokens, API key values, JWT secrets, OAuth 2.0 client secrets and tokens) in requests, folders, and collections MUST be masked as `"********"` when read.
  - External secret manager provider credentials MUST never be readable or modifiable via MCP, and external secret references MUST only expose declaration metadata (name, provider, secret path/ID, target field).
  - Sensitive HTTP/gRPC headers (`Authorization`, `Proxy-Authorization`, `X-Api-Key`, `Api-Key`, `Token`, `Cookie`, `Set-Cookie`, `X-Auth-Token`) and any secret-flagged headers MUST be masked in execution results and response snapshots.
  - Any resolved secret value (of length ≥ 4 characters) used during request execution MUST be scrubbed (`"[REDACTED_SECRET]"`) from returned response bodies, headers, console logs, and exception/error messages.
- **FR-011**: The server MUST expose built-in MCP Prompts (`debug_api_failure`, `generate_api_tests`, and `curl_to_clapi_request`) that assemble sanitized request/response context to guide AI agents through debugging failed calls, authoring `clapi.test` / `clapi.expect` assertions, and converting cURL commands into Clapi requests.

---

### Key Entities

- **McpServerConfig**: Configuration state governing the embedded MCP server (enabled/disabled toggle, local bind port, default response preview byte limit).
- **SanitizedRequestView / SanitizedEnvironmentView**: Redacted projections of requests, folders, collections, and environments guaranteed to contain masked placeholders (`"********"`) in place of secret values and credentials.
- **ExecutionSnapshot**: Cached record of the most recent execution of a request, retaining the full sanitized response body in memory for `clapi_inspect_response` pagination/JSONPath querying while returning only a budgeted preview in `clapi_execute_request`.
- **WorkspaceTreeEntry**: Lightweight node summary (`id`, `name`, `kind`, `method`, `slashPath`, `children`) used by `clapi://tree` and `clapi_list_tree` to minimize token consumption.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Zero secret leakage: Across 100% of MCP resource reads, tool responses, console log reads, exported specifications, and execution error messages, plaintext values of secret variables, auth tokens/passwords, and external vault credentials never appear in the MCP output.
- **SC-002**: Context efficiency: Reading the workspace tree (`clapi://tree` / `clapi_list_tree`) for a workspace of 100 requests consumes under 4 KB of payload, and `clapi_execute_request` caps response body previews to the requested budget (default 4 KB) 100% of the time while preserving full response inspectability via `clapi_inspect_response`.
- **SC-003**: Protocol and feature coverage: AI agents can author and read requests for all 4 core collection protocols (HTTP, gRPC, GraphQL, WebSocket), execute HTTP, gRPC, and GraphQL requests with pre/post scripts, manage environments and typed variables (`regular`, `secret`, `dynamic` across `string`, `number`, `boolean`, `object`), and import/export specifications with a 100% success rate in end-to-end integration tests.
- **SC-004**: Dual-mode reliability: Both headless CLI stdio mode (`--mcp`) and Desktop embedded HTTP mode pass full MCP protocol initialization, tool invocation, resource reading, and prompt retrieval tests without stdout framing corruption or state race conditions.

---

## Assumptions

- In headless CLI mode (`--mcp`), the server reads and writes the same workspace storage directory structure (`workspace.json` and `private-state.json` under `~/.clapi` or the directory specified via `--dir` / `CLAPI_DIR`) as the Clapi Desktop application.
- The embedded Desktop HTTP MCP server binds strictly to localhost (`127.0.0.1`) and is disabled by default until enabled by the user in Settings (or started via CLI flag).
- External secret manager provider credentials (such as Vault tokens, AWS secret keys, Azure client secrets, GCP service account keys, Keeper configs, and KeePassXC keys) continue to be configured exclusively by the human user through the Clapi Desktop Settings UI; the MCP server can resolve existing external secrets during request execution and manage environment-level external secret declarations (path/name mappings), but cannot read or alter provider master credentials.
- Supported specification import formats match Clapi's existing `SpecificationImporter` capabilities: OpenCollection v1.0.0 (YAML/JSON), OpenAPI v3.0+ (YAML/JSON), and Insomnia v4/v5 (JSON/YAML). Supported export formats match Clapi's existing exporters: OpenCollection v1.0.0 (JSON/YAML) and OpenAPI 3.0.3 (JSON/YAML).
