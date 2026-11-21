# Clapi Features

Clapi is a desktop-first API testing and development tool built with Kotlin and Compose Multiplatform. It is designed for fast request iteration, multi-protocol execution, scriptable validations, and reproducible API workflows across macOS, Linux, and Windows.

---

## Overview

Clapi provides an end-to-end API workspace supporting:

- **Multi-Protocol Execution**: HTTP/1.1, HTTP/2, Server-Sent Events (SSE), NDJSON, GraphQL, WebSocket streaming, and gRPC unary calls.
- **Model Context Protocol (MCP)**: Native MCP client for Streamable HTTP, legacy HTTP+SSE, and desktop stdio with tool execution, resource reading/subscriptions, prompts, and JSON-RPC traffic inspection.
- **Git-Backed Collection Synchronization**: Direct repository synchronization (commit, push, pull, branch tracking) using system Git credentials.
- **Multi-Cloud Secret Managers**: External secret resolution with AWS Secrets Manager, Azure Key Vault, Google Cloud Secret Manager (GCP), Keeper Security Cloud, KeePassXC, and HashiCorp Vault.
- **Environment Inheritance & Styling**: Base/sub-environment hierarchy with variable overrides, quick-add inline rows, and custom visual color-coding.
- **Typed & Dynamic Variables**: Built-in dynamic variables, user-defined scriptable dynamic variables with arguments, and template-aware JSON validation for unquoted expressions.
- **Comprehensive Import / Export**: First-class support for OpenCollection (JSON/YAML), OpenAPI (2.0 / 3.0 / 3.1), Postman Collections (v2 / v2.1) & Environments, and Insomnia (v5) workspaces.
- **Modern Script Runtime**: Fast JavaScript engine powered by Javet V8 for pre-request mutations, post-response assertions, gRPC lifecycle hooks, and dynamic variable computation.
- **High-Performance Code Editor**: Virtualized Compose editor with syntax highlighting (JSON, GraphQL, XML, HTML, JavaScript), brace/tag folding, find/replace, formatting, and diagnostics.
- **Embedded Sample Server**: Local deterministic Ktor server for offline testing across all protocols, auth flows, streaming, and LLM completions.

---

## Supported Features

### 1. HTTP Request Execution

- **HTTP Methods**: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`, `HEAD`.
- **URL & Query Parameter Editor**: Two-way synchronization between URL query strings and the structured key-value parameter table.
- **Header & Body Editor**: Header management with variable interpolation; payload support for JSON, GraphQL, URL-encoded forms, multipart form-data, and raw text.
- **JSON5 Support**: JSON bodies accept JSON5 by default (comments, trailing commas, unquoted keys, single quotes). Transmitted as strict JSON on Send; toggleable in Settings.
- **Authentication Modes**:
  - None, Basic Auth, Bearer Token, API Key, JWT.
  - **OAuth 2.0**: Full OpenCollection specification compliance (Client Credentials, Resource Owner Password Credentials, Authorization Code, Implicit). Interactive "Fetch Tokens" action with one-click clipboard copying, dynamic variable (`{{var}}`) and external secret (`@secret{...}`) support, Basic header or request body placement, automatic token expiration checks with clock-skew buffer, and seamless in-flight auto-refresh.
  - Collection and Folder authentication inheritance.
- **Streaming & LLM Inspection**:
  - Server-Sent Events (SSE via `text/event-stream`) and NDJSON (OpenAI-style `"stream": true`).
  - Items with `Accept: text/event-stream` show an **SSE** badge.
  - Live assembled text preview with time-to-first-token, time-to-last-token, and individual event log inspection.
- **Retry Controls & Timeouts**: Configurable request timeout, redirect policy, and connection settings.
- **cURL Integration**: One-click "Copy as cURL" generating ready-to-run shell commands.

### 2. Model Context Protocol (MCP) Client

- **Transports**: Streamable HTTP (2025-06-18 spec), legacy HTTP+SSE, and desktop stdio command execution.
- **Tool Execution**: Interactive form and raw JSON argument authoring, invocation, and response formatting.
- **Resources**: Browse available server resources, read contents, and subscribe to real-time resource updates.
- **Prompts**: Inspect prompt templates and test prompt generation with parameter inputs.
- **Activity Log**: Full bi-directional JSON-RPC frame inspector with request/response payloads, timing, and error details.
- **Capabilities & Auth**: Support for sampling, roots, elicitation, and authentication (None, Basic, Bearer, API Key, JWT).
- **Product Guide**: See [docs/request-types/mcp/index.md](docs/request-types/mcp/index.md).

### 3. gRPC Request Authoring and Execution

- **First-Class Request Type**: Dedicated `RequestKind.GRPC` with distinct `gRPC` badge (`#00897B`) across the sidebar, tab bar, and creation dialogs.
- **Dynamic Client (No Code-Gen Required)**: Powered by Netty and ProtocJarParser, compiling Protocol Buffer (`.proto`) definitions on the fly without generating static stubs.
- **Proto Schema Management**:
  - Request-level `.proto` file selection.
  - Collection-level and folder-level `.proto` inheritance.
  - Custom protobuf import path resolution (`importPaths`).
  - Automatic method discovery parsing available services and unary RPC methods.
- **Message Authoring**: High-performance JSON message payload editor with syntax highlighting, schema validation, line numbers, and formatting.
- **Metadata & Headers**: Key-value metadata table with dynamic variable interpolation (`{{var}}`).
- **Authentication**: Bearer Token, Basic Auth, API Key, and inherited collection/folder credentials automatically converted to gRPC call metadata.
- **Lifecycle Scripts**: Pre- and post-call hooks including `grpc:before-call-start`, `grpc:after-call-end`, `grpc:before-message-send`, and `grpc:after-message-receive`.
- **Response Inspection**: Structured status code (e.g. `OK`, `NOT_FOUND`), status message, response headers, trailing metadata, JSON response payload, and round-trip latency.

### 4. GraphQL Request Authoring and Execution

- **First-Class Request Type**: `RequestKind.GRAPHQL` with dedicated magenta `GQL` badge (`#E10098`).
- **Protocols**: POST with JSON envelope (`query`, `variables`, `operationName`) or GET via URL query parameters.
- **Multi-Tab Workspace**:
  - **Params & Headers**: Query parameter table and custom header editors with variable interpolation.
  - **Auth**: None, Basic, Bearer, API Key, OAuth 2.0, and Inherit modes.
  - **Body**: Dual syntax-highlighted editor with line numbers for GraphQL query (`highlightGraphql`) and JSON variables (`highlightJson`), brace folding, and query formatting ("Prettify").
  - **Body Variants**: Reusable named query/variables variants with variant creation, deletion, renaming, selection, and persistent active state.
  - **Scripts & Settings**: Pre-request and post-response assertion execution, timeout controls, and Markdown documentation.
- **Response Inspection**: Integrated `ResponseViewer` displaying status code, latency, headers, and formatted JSON output.

### 5. WebSocket Request Authoring and Execution

- **First-Class Request Type**: `RequestKind.WEBSOCKET` with dedicated blue `WS` badge (`#0288D1`).
- **Connection Lifecycle**: Interactive Connect, Disconnect, Clear timeline, and live status indicator (Disconnected, Connecting, Connected, Closing, Error).
- **Message Formats**: Plain text, JSON, XML, and Binary (hex / Base64).
- **Message Variants**: Named reusable message variants with selector, quick switching, and persistent state.
- **Live Timeline**: Real-time bi-directional message timeline displaying timestamp, direction (incoming/outgoing), format, payload, and byte length, capped at 1,000 frames FIFO with auto-scroll.
- **Configuration**: Custom connection headers, query parameters, auth (Bearer, Basic, API Key), connection timeout, and keep-alive ping intervals.

### 6. Git-Backed Collection Synchronization

- **Direct Git Sync**: Synchronize collections directly with remote Git repositories (GitHub, GitLab, Bitbucket, self-hosted via HTTPS or SSH) using the system's local Git credentials.
- **Native YAML Storage**: OpenCollection documents are stored as human-readable YAML files inside dedicated collection folders in the repository.
- **Sidebar Status Indicator**: Status widget in the bottom-left corner of the sidebar displaying the current active branch name and an uncommitted changes indicator dot.
- **Git Sync Dialog**:
  - Lists modified collections and changed files.
  - Commit message composer.
  - One-click Commit, Push, and Pull actions.
- **Selective Protobuf Sync**: Toggle between syncing `.proto` schema files into the repository (`./protobuf/...`) or referencing local workstation paths.

### 7. External Secret Managers (Multi-Cloud & Vaults)

Reference external secrets dynamically across request URLs, headers, bodies, and auth configurations using `{{_secret.<name>}}` and `secret: true` environment variables:

- **AWS Secrets Manager**:
  - Authenticates via AWS SigV4 signer using Access Key ID, Secret Access Key, optional Session Token, Region, and endpoint override.
  - Fetches secret strings and key-value JSON entries without persisting raw credentials.
- **Azure Key Vault**:
  - Authenticates via Azure Active Directory (Client ID, Client Secret, Tenant ID) or Bearer token.
  - Fetches secrets by Vault URL, secret name, and optional version tag.
- **Google Cloud Secret Manager (GCP)**:
  - Authenticates using Service Account JSON credentials or OAuth tokens.
  - Accesses secret payloads across GCP projects and version tags.
- **Keeper Security Cloud (`keeper-security-cloud`)**:
  - One-Time Token initialization in Settings -> Secrets Manager, storing bound KSM client configuration encrypted on-disk via KSafe.
  - Supports standard record types (`login`, `serverCredentials` with dotted properties), custom record types, and Keeper Notation (`field/...`, `custom_field/...`).
- **KeePassXC**:
  - Connects to locally running KeePassXC via native messaging proxy (`org.purejava:keepassxc-proxy-access`).
  - Supports Entry URL, Target Field (`password`, `login`, `totp`, custom attribute), custom field filters, and non-leaking live match count indicator.
- **HashiCorp Vault**:
  - Vault Server (KV v2) integration with path grouping and encrypted token custody.

### 8. Environment Management & Inheritance

- **Multi-Tier Environment Inheritance**: Environments can extend a parent environment (`extends`), inheriting variables and external secrets while allowing explicit local overrides with clear visual indicators.
- **Visual Customization**: Assign distinct colors (predefined palette or custom hex code) to environments, rendered as colored dots/badges in the sidebar and environment picker.
- **Inline Quick-Add Row**: Persistent top row in variable and secret tables for instant entry without scrolling to the bottom.
- **Instant Creation Workflow**: Adding a new environment automatically expands the Environments sidebar section and opens the editor dialog.

### 9. Variable System & Typed Dynamic Variables

- **Scopes**: Environment, Global, Collection, Folder, and Request-local (`{{name}}` interpolation).
- **Variable Kinds**: Regular, Secret (masked values), and Dynamic.
- **Explicit Variable Data Types**: String, Number, Boolean, and Object.
- **Built-in Dynamic Variables**: Auto-generated values including `{{$guid}}`, `{{$timestamp}}`, `{{$randomInt}}`, and more.
- **User-Defined Scriptable Dynamic Variables**: Define custom JavaScript scripts that execute per request to return freshly computed values (e.g., dynamic timestamps, hashes, signatures).
- **Parameterized Dynamic Variables**: Pass arguments inside template expressions (e.g. `{{secondsFromNow(10)}}`).
- **Dynamic Variable Editor Dialog**: Side-by-side script editor, parameter configuration table, and interactive test runner sandbox with sample input values and console output.
- **Request-Aware Dynamic Scripts**: Read-only access to `context.request` (method, URL, headers) to derive signatures and idempotency keys.
- **Template-Aware JSON Validation**: Code editor recognizes unquoted numeric, boolean, and object variable tokens (e.g., `{"count": {{numberVar}}, "active": {{boolVar}}}`) as valid templates without false syntax errors.

### 10. Specification & Collection Import / Export

- **OpenCollection v1.0.0**: Native bidirectional JSON and YAML import and export.
- **OpenAPI 2.0 / 3.0 / 3.1 Import**: Parses OpenAPI specs (JSON/YAML) into collections, preserving operations, paths, query/path parameters, auth schemes, request bodies, and examples.
- **Postman Collection v2 / v2.1 Import**:
  - Converts folders, requests, headers, auth (Bearer, Basic, API Key), bodies (raw JSON, form-data, urlencoded, GraphQL, binary), and scripts.
  - Automatically translates `pm.*` script namespaces to `clapi.*`, including `pm.sendRequest` and `pm.execution.*`.
- **Postman Environment Import**: Imports active variable key-value pairs.
- **Insomnia v5 Export Import (YAML & JSON)**:
  - Supports Insomnia v5 workspaces (`collection.insomnia.rest/5.0`, `spec.insomnia.rest/5.0`, `environment.insomnia.rest/5.0`).
  - Converts nested folder trees, HTTP requests, gRPC requests, WebSocket requests, authentication schemes (Basic, Bearer, API Key, AWS SigV4, OAuth2), base environments, and sub-environments.
  - Automatically converts Insomnia script namespaces (`insomnia.*` to `clapi.*`).
- **Import Dialog UI**: Prominent format discovery badges (OpenAPI, OpenCollection, Postman, Insomnia) and drag-and-drop YAML/JSON file support.

### 11. Code Editor

Clapi includes a built-in code editor built with Compose Multiplatform:

- **Syntax Highlighting**: Token-level colorization for JSON, XML, HTML, GraphQL, JavaScript, and Plain Text.
- **Code Folding**: Brace-based folding (`{ }`, `[ ]`), tag-based folding (`<tag>...</tag>`), multi-line comment folding, and Fold All / Unfold All controls.
- **Find / Replace**: Case-insensitive search, match counter, active match highlighting, next/previous navigation (`F3` / `Shift+F3`), and Replace / Replace All.
- **Formatting**: Auto-format source code (JSON / JSON5 pretty-print, XML/HTML indentation, JavaScript formatting).
- **Editor Ergonomics**: Line numbers gutter, indent guides (2 spaces), matching bracket highlights, Go to Line (`Cmd/Ctrl+G`), line manipulation shortcuts, diagnostic tick tooltips, truncation protection for lines >50,000 characters, word wrap toggle, and clipboard copy.
- **Performance**: Virtualized rendering (`LazyColumn`) handling large responses and payloads smoothly.

### 12. Script Engine & Test Assertions

- **Modern JavaScript Engine**: Powered by **Javet V8**, providing fast and secure execution for request automation.
- **Execution Lifecycle**:
  1. *Pre-request*: Resolve variables, execute pre-request script, mutate outgoing headers/body/params.
  2. *Execution*: Dispatch request over selected protocol.
  3. *Post-request*: Populate response context, execute test scripts, evaluate assertion assertions.
- **Script API Namespaces**:
  - `clapi.request`: Read request details and mutate outgoing request (`setHeader`, `setQueryParam`, `setMethod`, `setUrl`, `setBody`).
  - `clapi.response`: Access response code, status, time, size, headers, body (`clapi.response.json()`), and stream events.
  - `clapi.response.llm`: Access assembled text, finish reason, and usage metrics for streamed LLM calls.
  - `clapi.environment`, `clapi.globals`, `clapi.collectionVariables`, `clapi.variables`: Get, set, and unset variables.
  - `clapi.test(name, fn)`: Define named test assertions.
  - `clapi.expect(...)`: Chai-style BDD assertion chaining (`equal`, `above`, `include`, `have.property`, etc.).
  - `clapi.console`: Debug logging via `clapi.console.log(...)`.
  - `clapi.sendRequest`: Programmatically dispatch secondary requests from within scripts.
  - `clapi.execution`: Control collection execution flow (`setNextRequest`, `skipRequest`).

### 13. Deterministic Local Sample Server

The `sample-server` module provides local endpoints for automated testing and manual exploration without external network dependencies:

- **HTTP Verification**: CRUD endpoints, header echoing, payload echoing (JSON, XML, raw, form-data, upload), query parameter validation.
- **Auth Simulation**: Basic Auth, Bearer Token, API Key, and protected endpoints.
- **Error & Latency Simulation**: Deterministic HTTP error codes (`/api/error/{code}`), artificial delays (`?mode=slow`, `?ms=`), and large payloads.
- **LLM Completion Mocks**: OpenAI-compatible `/v1/models`, `/v1/chat/completions` (JSON or SSE stream), `/v1/chat/ndjson`, and `/v1/embeddings` (`?demo=true` enables visible typewriter token arrival).
- **Streaming & Realtime**: Finite SSE (`/sse`) and WebSocket echo (`/ws`).
- **MCP Mock Transports**: Streamable HTTP (`/mcp`), legacy SSE (`/mcp/sse`), and MCP stdio launcher with mock tools (`echo`, `add`, `fail`, `slow`, `trigger_sampling`, `trigger_roots`, `trigger_elicitation`, `trigger_ping`).
- **OAuth 2.1 Server**: Auto-approving authorization endpoint, token endpoint, dynamic client registration, and metadata.

---

## Documentation & References

- **VitePress Documentation**: Full documentation site located in `docs/` (run with `npm run docs:dev`).
- **Installation Guide**: [docs/guide/installation.md](docs/guide/installation.md)
- **Architecture Reference**: [docs/settings/architecture.md](docs/settings/architecture.md)
- **MCP Client Guide**: [docs/request-types/mcp/index.md](docs/request-types/mcp/index.md)
- **Scripting Reference**: [docs/scripting/index.md](docs/automation/index.md)
- **Keyboard Shortcuts**: [docs/settings/shortcuts.md](docs/settings/shortcuts.md)
