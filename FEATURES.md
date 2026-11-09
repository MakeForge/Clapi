# Clapi Features

Clapi is a desktop-first API testing tool built with Kotlin and Compose Multiplatform. It is designed for fast request iteration, scriptable validations, and reproducible API test workflows.

## Overview

Clapi supports end-to-end API testing with:

- HTTP request authoring and execution
- Pre-request scripting
- Post-request scripting
- Multi-scope variables (environment, global, collection, request-local)
- Assertion-based response validation
- Reusable collections and environments
- Deterministic local sample server for testing

## Supported Features

### HTTP Request Execution

- HTTP methods: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`, `HEAD`
- URL editing with query parameter table synchronization
- Header editing and request body editing
- Request body support for JSON, GraphQL, form-style payloads, and raw text
- JSON bodies accept JSON5 by default (comments, trailing commas, unquoted keys). Send converts to strict JSON; turn off in Settings to restore strict JSON.
- Authentication modes: None, Basic, Bearer, API Key, JWT, and OAuth 2.0 (Client Credentials, Resource Owner Password Credentials, Authorization Code, Implicit)
- **OAuth 2.0 Support**:
  - OpenCollection specification compliant OAuth2 configuration for requests, folders, and collections.
  - Interactive "Fetch Tokens" action in the Auth Editor with one-click clipboard copying for Access Token, Refresh Token, and Identity Token.
  - Full support for dynamic variables (`{{var}}`) and external secrets (`@secret{...}`) across all credential and URL fields.
  - Client credentials placement options: Basic Auth header (`Authorization: Basic ...`) or request body (`client_id` / `client_secret`).
  - Automatic pre-flight token expiration check (including clock-skew safety buffer and JWT `exp` parsing) and seamless in-flight auto-refresh prior to request execution.
- Retry controls and timeout behavior
- HTTP streaming for SSE (`text/event-stream`) and NDJSON (OpenAI-style `"stream": true`). Items with `Accept: text/event-stream` show an **SSE** badge in the HTTP method color; folder ⋮ → **New SSE Request**.
- **MCP client** for Streamable HTTP (2025-06-18), legacy HTTP+SSE, and desktop stdio. Tools (Form/JSON arguments), resources (read + subscribe), prompts, Activity JSON-RPC inspector, sampling/roots/elicitation, and auth (None, Basic, Bearer, API Key, JWT) match the REST workspace. See [docs/mcp.md](docs/mcp.md).
- Copy request as `curl`

### External Secret Managers (KeePassXC & HashiCorp Vault)

- Support for referencing external secrets dynamically across request URLs, headers, bodies, and auth configurations using `{{_secret.<name>}}` and `secret: true` environment variables.
- **KeePassXC Integration**:
  - Connects to locally running KeePassXC via native messaging proxy (`org.purejava:keepassxc-proxy-access:1.3.2`).
  - Configure Entry URL, Target Field (`password`, `login`, `totp`, or custom attribute), and optional Custom Field Filters supporting variable interpolation.
  - Live, non-leaking match count indicator in the Environment Editor displaying match counts or lock status without exposing secret values.
  - Clean lifecycle management: zero idle proxy socket or background thread footprint when KeePassXC is not used by the active environment.
- **HashiCorp Vault Integration**:
  - Vault Server (KV v2) integration with path grouping and encrypted token custody.

### Response Validation and Inspection

- Response status code and status text
- Response headers
- Response body (`text` and JSON parsing in scripts)
- Live assembled stream text; RAW view of individual SSE/NDJSON events
- Response timing (`responseTime`, TTFB, time to first token, time to last token)
- Response size (`size`)
- Structured pass/fail test result reporting

### Code Editor

Clapi features a full-featured code editor used across request body editing, script editing, and response viewing. All capabilities are built with pure Compose Multiplatform and work cross-platform (macOS, Linux, Windows, Web).

**Syntax Highlighting** — Token-level colorization for:
- JSON (keys, strings, numbers, booleans, null, braces)
- XML / HTML (tags, attributes, values, comments, doctypes)
- GraphQL (keywords, types, fields, directives, comments)
- JavaScript (keywords, builtins, strings, comments, numbers, operators)
- Plain text (default)

**Code Folding** — Collapse and expand regions in body, script, and response editors:
- Brace-based folding for JSON, JavaScript, and GraphQL (`{ }`, `[ ]`)
- Tag-based folding for XML and HTML (`<tag>...</tag>`)
- Multi-line comment folding (`/* ... */`, `<!-- ... -->`)
- Fold All / Unfold All toolbar controls
- Fold marker badges showing hidden line count

**Find / Replace** — In-editor search:
- Case-insensitive find with match count and active-match highlighting
- Previous / Next (`F3` / `Shift+F3`); `Esc` closes the bar
- Replace and Replace All in editable editors (one undo step)

**Formatting** — Auto-format source code:
- JSON pretty-print (indented with 2-space indent). With JSON5 on, Format pretty-prints JSON5 and keeps comments, unquoted keys, single quotes, and trailing commas; Send still converts to strict JSON. With JSON5 off, Format is a no-op on invalid JSON.
- XML / HTML indentation
- JavaScript formatting (including script editor)
- Toggle on/off from toolbar

**Editor Features**:
- Line numbers gutter (all editor modes)
- Indent guides every 2 spaces; matching `()[]{}` highlight at the caret
- Go to line (`Cmd/Ctrl+G`); status strip `Ln X, Col Y`
- Duplicate / move / comment / select current line (see [docs/shortcuts.md](docs/shortcuts.md))
- Diagnostic tick in the gutter with hover tooltip and status-bar message
- Truncation banner when a line exceeds 50,000 characters
- Word wrap toggle
- Copy to clipboard (toolbar button)
- Download to file (response viewer)
- Monospace font family
- Virtualized rendering (LazyColumn) for large responses (>200 lines)
- Full text selection support in read-only mode

### Script Runtime

Clapi provides a JavaScript runtime for request automation.

- **Pre-request scripts**: run before dispatch to mutate outgoing request
- **Post-request scripts**: run after response to assert behavior
- Runtime namespace default: `clapi` (global aliases also supported)
- Console logging via `clapi.console.log(...)`

### Variable System

Clapi supports four variable scopes:

1. **Environment variables**
   - `clapi.environment.get/set/unset`
   - Persisted with selected environment/workspace
2. **Global variables**
   - `clapi.globals.get/set/unset`
   - Persisted workspace-wide
3. **Collection variables**
   - `clapi.collectionVariables.get/set/unset`
   - Session-scoped runtime map across requests
4. **Request-local variables**
   - `clapi.variables.get/set/unset`
   - Request-scoped lifecycle (pre-request to post-request for the same request)

Variable interpolation uses `{{name}}` in URL, headers, body, and auth fields.

### Assertions and Test APIs

- Named tests: `clapi.test(name, fn)`
- Core assertions:
  - equality: `equal`, `eql`
  - type/value checks: `true`, `false`, `null`, `undefined`, `ok`, `exist`
  - numeric checks: `above`, `below`, `at.least`, `at.most`
  - collections/strings: `include`, `match`, `oneOf`, `lengthOf`, `property`
  - negation: `not.*`

### Request Mutation APIs

Pre-request scripts can mutate outgoing request values:

- `clapi.request.setHeader(name, value)`
- `clapi.request.setQueryParam(name, value)`
- `clapi.request.setMethod(method)`
- `clapi.request.setUrl(url)`
- `clapi.request.setBody(content)`

### Collections and Test Automation

- Global OpenCollection document persistence (`workspace.json`) storing Clapi Collections as top-level OpenCollection Folders and environments in `config.environments`
- Single-collection OpenCollection import/export (JSON/YAML) using `qa-tests/fixtures/clapi-test-collection.json` (includes a **JSON5** folder under Body Types; workspace bundle export/import removed)
- **Postman Collection v2 / v2.1 import** — auto-detected and converted to OpenCollection format
  - Folders, requests, headers, auth (bearer / basic / API key), body (raw JSON, form-data, urlencoded, GraphQL, binary), and scripts
  - Postman `pm.*` script namespace automatically rewritten to `clapi.*`
   - `pm.sendRequest` rewritten to `clapi.sendRequest` (supported)
   - `pm.execution.setNextRequest`, `pm.execution.skipRequest`, and `postman.setNextRequest` rewritten to `clapi.execution.*`
- **Postman Environment import** — name and enabled variables imported; disabled variables skipped
- Request-level pre-request and post-request scripts in collection items
- Automated collection validation via `qa-tests/collection-validator.mjs` (JSON5 requests are skipped, not counted as passed; Kotlin `SampleCollectionE2ETest` is the JSON5 send coverage)
- Deterministic sample-server endpoints for reproducible test runs

### Sample Server

The `sample-server` module provides local endpoints for deterministic API testing, including:

- status endpoints (`/status/200`, `/status/201`)
- JSON payload endpoints (`/json/user`, `/json/array`, `/json/object`)
- header and cookie test endpoints (`/headers`, `/cookies`)
- timing/body endpoints (`/response-time`, `/string-body`, `/echo-body`)
- additional API routes for QA and integration scenarios
- OpenAI-compatible LLM mocks (`/v1/models`, `/v1/chat/completions`, `/v1/chat/ndjson`, `/v1/embeddings`)
- `?demo=true` on chat completions for a visible multi-token SSE stream (`?chunkMs=` overrides delay)

## Scripting Capabilities

### Lifecycle

1. **Pre-request phase**
   - Resolve variables
   - Execute pre-request script
   - Apply request mutations and variable writes
2. **HTTP execution phase**
   - Send request with resolved/mutated values
3. **Post-request phase**
   - Build response context
   - Execute post-request script
   - Produce assertion results and pass/fail status

### Script API Areas

- `clapi.response` (code/status/time/size/body/headers/streamEvents)
- `clapi.response.llm` (assembledText, finishReason, usage, jsonContent)
- `clapi.request` (read context + mutation helpers)
- `clapi.environment`
- `clapi.globals`
- `clapi.collectionVariables`
- `clapi.variables`
- `clapi.expect`
- `clapi.test`
- `clapi.console`

### Script Example

```javascript
clapi.request.setHeader("X-Request-Id", "req-123")
clapi.variables.set("localToken", "abc")

clapi.test("status and schema basics", function () {
  const body = clapi.response.json()
  clapi.expect(clapi.response.code).to.equal(200)
  clapi.expect(body).to.have.property("id")
  clapi.expect(body.name).to.be.ok
})

clapi.test("streamed chat text", function () {
  clapi.expect(clapi.response.llm.assembledText).to.include("Hello")
  clapi.expect(clapi.response.llm.finishReason).to.equal("stop")
  clapi.expect(clapi.response.streamEvents.length).to.be.above(0)
})
```

## Test Automation

Clapi test automation includes:

- module-level automated tests for scripting, network, storage, model, and UI
- QA E2E tests in `qa-tests`
- collection-level validator for request scripts and assertions
- sample-server-assisted deterministic integration coverage

## Example Validation Workflow

```bash
# 1) Start deterministic sample API
./gradlew :sample-server:run

# 2) Run core automated suites
./gradlew :core-scripting:desktopTest :core-network:allTests :core-storage:allTests :core-model:allTests

# 3) Validate request collection against local server
node qa-tests/collection-validator.mjs
```

## Notes

- Script runtime is synchronous; asynchronous callbacks are not awaited before request dispatch.
- Scripts currently apply to HTTP requests only; WebSocket requests do not run pre-request/post-request scripts.
- Collection variables are session-scoped (not persisted as environment/global workspace values).
- For full script API details, see `docs/scripts/api-reference.md`.
