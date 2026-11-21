# Contract: MCP Resources, Prompts & Transports

**Feature**: `029-mcp-server` | **Spec**: [spec.md](../spec.md) | **Data Model**: [data-model.md](../data-model.md)

---

## 1. Transport & Protocol Handshake Contract

### 1.1 Supported Protocol Versions & Transports
- **Protocol Versions**: `2025-06-18` (default) and `2024-11-05` (legacy compatibility).
- **Server Info**:
  ```json
  {
    "name": "clapi-mcp-server",
    "version": "<appVersion>",
    "title": "Clapi API Client MCP Server"
  }
  ```
- **Advertised Capabilities**:
  ```json
  {
    "tools": { "listChanged": true },
    "resources": { "subscribe": true, "listChanged": true },
    "prompts": { "listChanged": false },
    "logging": {},
    "completions": {}
  }
  ```
- **Transports**:
  1. **Headless Stdio (`--mcp [--dir <path>]`)**: Newline-delimited JSON-RPC 2.0 on `stdin`/`stdout`. All diagnostic and runtime logs redirected to `stderr`.
  2. **Embedded Desktop HTTP (`http://127.0.0.1:<port>/mcp`)**:
     - `POST /mcp`, `GET /mcp`, `DELETE /mcp` (Streamable HTTP `2025-06-18` with `Mcp-Session-Id` header)
     - `GET /mcp/sse`, `POST /mcp/messages` (Legacy HTTP+SSE `2024-11-05`)

---

## 2. MCP Resources & Resource Templates

### 2.1 Static Resources (`resources/list`)

| URI | Name | MIME Type | Payload Description |
| :--- | :--- | :--- | :--- |
| `clapi://tree` | `Workspace Tree` | `application/json` | Hierarchical JSON array of `WorkspaceTreeNodeDto` objects representing all collections, folders, and requests |
| `clapi://environments` | `Environments Overview` | `application/json` | Active environment selection, global variables (secrets masked), and list of environments with `extends` and color metadata |
| `clapi://logs/console` | `Script & Execution Console Logs` | `application/json` | Last 100 sanitized console log entries (`timestamp`, `level`, `message`) |

### 2.2 Resource Templates (`resources/templates/list`)

| URI Template | Name | MIME Type | Parameter Resolution & Output |
| :--- | :--- | :--- | :--- |
| `clapi://collections/{collectionId}` | `Collection Details` | `application/json` | Accepts collection UUID or collection name; returns `SanitizedCollectionDto` (info, default auth, headers, variables, scripts, `.proto` config, Git storage info, and child tree) |
| `clapi://requests/{requestId}` | `Request Details` | `application/json` | Accepts request UUID or URL-encoded slash path; returns `SanitizedRequestDto` |
| `clapi://environments/{envName}` | `Environment Projection` | `application/json` | Accepts environment name; returns `SanitizedEnvironmentDto` including inherited variable projections (`isInherited`, `isOverridden`, `sourceEnvName`) and external secret declarations with all secret values masked as `"********"` |
| `clapi://responses/{requestId}/latest` | `Latest Execution Snapshot` | `application/json` | Accepts request UUID or URL-encoded slash path; returns `ExecutionResultDto` from `ResponseSnapshotCache` (or 404 resource error if the request has not been executed in the current session) |

---

## 3. MCP Prompts (`prompts/list` & `prompts/get`)

### 3.1 `debug_api_failure`
- **Description**: Assemble sanitized request definition, latest response status/headers/body preview, test assertion failures, and script logs to diagnose why an API call failed.
- **Arguments**:
  - `requestId` (`required: true`): Request UUID or slash path.
- **Generated Messages**:
  - `user` message containing a structured markdown & JSON block with the sanitized request config, latest execution snapshot (if available), failed assertions, and console logs, asking the assistant to identify the root cause and propose an exact `clapi_upsert_request` or `clapi_set_variable` fix.

### 3.2 `generate_api_tests`
- **Description**: Generate post-request JavaScript assertions (`clapi.test`, `clapi.expect`) for a request based on its declaration and latest execution response.
- **Arguments**:
  - `requestId` (`required: true`): Request UUID or slash path.
- **Generated Messages**:
  - `user` message containing the sanitized request definition, latest response status/headers/body preview, and Clapi's scripting API conventions (`clapi.test(name, fn)`, `clapi.expect(val).to.equal(...)`, `clapi.response.code`, `clapi.response.json()`).

### 3.3 `curl_to_clapi_request`
- **Description**: Parse a raw cURL command and author a structured Clapi request inside a target collection/folder.
- **Arguments**:
  - `curlCommand` (`required: true`): Raw `curl ...` string.
  - `targetCollection` (`required: true`): Target collection name or UUID.
  - `targetFolder` (`required: false`): Optional target folder path.
- **Generated Messages**:
  - `user` message instructing the assistant to map method, URL, query params, headers (converting sensitive tokens into secret variables via `clapi_set_variable`), and body into a `clapi_upsert_request` call.
