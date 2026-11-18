# Clapi Model Context Protocol (MCP) Server

Clapi includes a built-in **Model Context Protocol (MCP) Server** (`2025-06-18` Streamable HTTP & Stdio, plus `2024-11-05` Legacy SSE) that allows AI coding assistants (such as Claude Code, Claude Desktop, Cursor, Windsurf, and custom MCP agents) to browse collections, author multi-protocol requests (HTTP, gRPC, GraphQL, WebSocket), manage environments and typed variables, execute API calls and test suites, inspect large responses via JSONPath, and import/export specifications—all protected by a **Zero-Exfiltration Secret Sanitization Pipeline**.

---

## 1. Operating Modes

### A. Headless CLI Mode (`--mcp` over Stdio)

Use headless mode when connecting terminal-based or editor-spawned MCP clients directly to your Clapi workspace storage directory (`~/.clapi` by default, or any custom directory via `--dir`).

```bash
# Using the installed native binary:
clapi --mcp

# With a custom workspace directory:
clapi --mcp --dir /path/to/workspace

# Using the universal fat JAR:
java -jar Clapi.jar --mcp --dir /path/to/workspace
```

Example MCP client configuration (`claude_desktop_config.json` / `.mcp.json`):

```json
{
  "mcpServers": {
    "clapi": {
      "command": "clapi",
      "args": ["--mcp"]
    }
  }
}
```

> **Stdio Framing Guarantee**: In `--mcp` mode, Clapi captures `stdout` exclusively for newline-delimited JSON-RPC 2.0 protocol frames and redirects all diagnostic logs to `stderr`.

### B. Embedded Desktop HTTP Mode (`127.0.0.1`)

Use embedded mode when you want to collaborate interactively with an AI agent while watching collections, requests, environments, and console logs update live in the Clapi Desktop GUI:

1. Open **Settings → MCP Server** in Clapi Desktop.
2. Enable **Enable Embedded MCP Server** (default port: `3333`, bound strictly to `127.0.0.1`).
3. Copy the client configuration snippet:
   ```json
   {
     "mcpServers": {
       "clapi": {
         "url": "http://127.0.0.1:3333/mcp"
       }
     }
   }
   ```

---

## 2. Dual Identifier Resolution (UUID & Slash Paths)

Every tool and resource template that targets a collection, folder, or request accepts **either**:
- **Exact UUID**: `"550e8400-e29b-41d4-a716-446655440000"`
- **Human-Readable Slash Path**: `"Payments API/Invoices/Create Invoice"` (literal slashes in item names can be escaped as `\/`)

If a slash path matches multiple items with the same name, Clapi returns a disambiguation error listing each candidate's UUID, method, and path.

---

## 3. Available MCP Resources

| Resource URI / Template | Description |
| :--- | :--- |
| `clapi://tree` | Compact hierarchical overview of all collections, folders, and requests (`id`, `name`, `kind`, `method`, `url`, `slashPath`). |
| `clapi://collections/{collectionId}` | Sanitized collection details, default auth, headers, variables, scripts, `.proto` files/import paths, and Git config. |
| `clapi://requests/{requestId}` | Complete sanitized request configuration across HTTP, gRPC, GraphQL, or WebSocket protocols. |
| `clapi://environments` | Active environment selection, sanitized global variables, and environment list with `extends` inheritance links. |
| `clapi://environments/{envName}` | Effective variable projection for an environment (including inherited/overridden indicators and external secret declarations) with all secrets masked. |
| `clapi://responses/{requestId}/latest` | Latest cached execution response snapshot (sanitized headers, budgeted body preview, timing metrics, and test assertion results). |
| `clapi://logs/console` | Recent sanitized pre-request and post-request script console output and execution diagnostics. |

---

## 4. Available MCP Tools

### Workspace, Collections & Folders
- **`clapi_list_tree`**: Browse the workspace hierarchy with optional `collection`, `kind` (`http`, `grpc`, `graphql`, `websocket`), and `query` filters.
- **`clapi_manage_collection`**: `create`, `update`, or `delete` (`confirm: true` required) a collection, including collection-level auth, headers, scripts, and `.proto` files/import paths.
- **`clapi_manage_folder`**: `create`, `update` (including moving to a new `parentId`), or `delete` (`confirm: true` required) a folder and its folder-level defaults.

### Multi-Protocol Request Authoring
- **`clapi_get_request`**: Retrieve a sanitized request definition by UUID or slash path.
- **`clapi_upsert_request`**: Create or update an `http`, `grpc`, `graphql`, or `websocket` request (headers, query params, auth, body types, form-data entries, pre-request & test scripts, gRPC service/method/metadata/proto path, GraphQL variants, WebSocket variants).
- **`clapi_delete_request`**: Delete a request (`confirm: true` required).

### Environments & Scoped Typed Variables
- **`clapi_list_environments`**: List all environments, active selection, inheritance chains, and sanitized variable projections.
- **`clapi_set_active_environment`**: Switch or clear the active environment.
- **`clapi_upsert_environment`**: Create or update an environment (`name`, `newName`, `color`, `extends`, `description`, `externalSecrets`), with automatic cycle detection on `extends`.
- **`clapi_delete_environment`**: Delete an environment (`confirm: true` required).
- **`clapi_set_variable`**: Create, update, or delete a variable across `global`, `environment`, `collection`, or `folder` scopes with explicit `kind` (`regular`, `secret`, `dynamic`) and `variableType` (`string`, `number`, `boolean`, `object`), including JavaScript generator scripts and parameters for dynamic variables.

### Execution, Test Suites & Response Inspection
- **`clapi_execute_request`**: Execute an HTTP, gRPC, or GraphQL request with optional `environmentName`, `transientVariables`, and `maxBodyLength` (default `4096` bytes).
- **`clapi_inspect_response`**: Query the cached sanitized response of a previously executed request using `jsonPath` (e.g., `$.data.items[0].id`, `$.users[*]`) and/or UTF-8 `byteOffset` and `length` slicing without re-executing the network request.
- **`clapi_run_test_suite`**: Sequentially execute all requests in a collection or folder and return the aggregated test assertion pass/fail report.

### Specification Import, Export & Git Sync
- **`clapi_import_specification`**: Import OpenAPI 3.0+, OpenCollection 1.0.0 (YAML/JSON), or Insomnia v4/v5 specifications from `rawContent` or `filePath`.
- **`clapi_export_collection`**: Export a collection to sanitized `opencollection-yaml`, `opencollection-json`, `openapi-yaml`, or `openapi-json`.
- **`clapi_git_sync`**: Execute `status`, `commit`, `pull`, or `push` on a Git-backed collection.

---

## 5. Built-In MCP Prompts

- **`debug_api_failure(requestId)`**: Assembles sanitized request config, latest execution snapshot, failed assertions, and console logs to diagnose API failures.
- **`generate_api_tests(requestId)`**: Inspects request and latest response structure to author `clapi.test` and `clapi.expect` post-request assertions.
- **`curl_to_clapi_request(curlCommand, targetCollection, targetFolder?)`**: Guides conversion of a raw cURL command into a structured Clapi request while extracting hardcoded credentials into write-only secret variables.

---

## 6. Zero-Exfiltration Security Model

Clapi's MCP server enforces an immutable **`SecretSanitizer`** pipeline across all outputs:

1. **Write-Only Secret Variables**: Setting a variable with `kind = "secret"` stores the value internally for request execution, but reading any resource or tool output always returns `"value": "********", "secret": true, "isSet": true`.
2. **Masked Authentication Credentials**: Passwords, Bearer/JWT tokens, API key values, and OAuth 2.0 client secrets are always masked as `"********"`. Passing `"********"` back during a `clapi_upsert_request` update preserves the stored credential without overwriting it.
3. **Protected External Secret Managers**: Master credentials for HashiCorp Vault, AWS Secrets Manager, Azure Key Vault, GCP Secret Manager, Keeper Security, and KeePassXC are never accessible via MCP; only declaration metadata (`name`, `providerType`, `path`, `targetField`) is exposed.
4. **Header & Full-Text Echo Redaction**: Sensitive headers (`Authorization`, `Proxy-Authorization`, `X-Api-Key`, `Api-Key`, `Token`, `Cookie`, `Set-Cookie`, `X-Auth-Token`) are masked, and every resolved secret value (length ≥ 4 characters, including URL-encoded and Basic Auth Base64 variants) is scrubbed (`"[REDACTED_SECRET]"`) from response bodies, headers, script logs, and error messages **before** caching the execution snapshot.
