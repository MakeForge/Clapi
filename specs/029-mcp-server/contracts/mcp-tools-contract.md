# Contract: MCP Tools Schema & Behavior

**Feature**: `029-mcp-server` | **Spec**: [spec.md](../spec.md) | **Data Model**: [data-model.md](../data-model.md)

---

## 1. Workspace, Collection & Folder Tools

### 1.1 `clapi_list_tree`
Lists collections, nested folders, and requests in a compact tree.
- **Input Schema**:
  - `collection` (`string`, optional): Filter to a single collection by UUID or name.
  - `kind` (`string`, optional): Filter leaf requests by `"http"`, `"grpc"`, `"graphql"`, or `"websocket"`.
  - `query` (`string`, optional): Case-insensitive substring filter on request/folder name or URL.
- **Output**: JSON array of `WorkspaceTreeNodeDto`.

### 1.2 `clapi_manage_collection`
Creates, updates, or deletes a collection and its collection-level defaults.
- **Input Schema**:
  - `action` (`string`, required): `"create"` | `"update"` | `"delete"`
  - `collectionId` (`string`, optional): Required for `"update"` and `"delete"` (UUID or collection name).
  - `name` (`string`, optional): Required for `"create"`, optional rename for `"update"`.
  - `summary` (`string`, optional): Collection description/summary.
  - `version` (`string`, optional): Collection version string.
  - `auth` (`object`, optional): Collection-level default authentication config.
  - `headers` (`array`, optional): Collection-level default headers (`[{key, value, enabled, secret}]`).
  - `preRequestScript` (`string`, optional): Collection-level pre-request JS script.
  - `testScript` (`string`, optional): Collection-level post-request test JS script.
  - `protoFiles` (`array`, optional): List of `.proto` file paths (`[{path, syncToGit}]`).
  - `importPaths` (`array`, optional): List of protobuf import directory paths (`[{path, enabled, syncToGit}]`).
  - `confirm` (`boolean`, optional): Must be `true` when `action == "delete"`.
- **Output**: `SanitizedCollectionDto` (or `DeleteResultDto` for `"delete"`).

### 1.3 `clapi_manage_folder`
Creates, updates, moves, or deletes a folder within a collection.
- **Input Schema**:
  - `action` (`string`, required): `"create"` | `"update"` | `"delete"`
  - `folderId` (`string`, optional): Target folder UUID or slash path (required for `"update"` and `"delete"`).
  - `parentId` (`string`, optional): Parent collection or parent folder UUID/slash path (required for `"create"`, optional move target for `"update"`).
  - `name` (`string`, optional): Folder name (required for `"create"`).
  - `auth` (`object`, optional): Folder-level default authentication config.
  - `headers` (`array`, optional): Folder-level default headers.
  - `preRequestScript` (`string`, optional): Folder-level pre-request JS script.
  - `testScript` (`string`, optional): Folder-level post-request test JS script.
  - `confirm` (`boolean`, optional): Must be `true` when `action == "delete"`.
- **Output**: `SanitizedFolderDto` (or `DeleteResultDto` for `"delete"`).

---

## 2. Multi-Protocol Request Authoring Tools

### 2.1 `clapi_get_request`
Retrieves a single sanitized request definition.
- **Input Schema**:
  - `requestId` (`string`, required): Request UUID or slash path (e.g., `"MyAPI/Users/GetUser"`).
- **Output**: `SanitizedRequestDto`.

### 2.2 `clapi_upsert_request`
Creates a new request or updates an existing request across HTTP, gRPC, GraphQL, or WebSocket protocols.
- **Input Schema**:
  - `requestId` (`string`, optional): Existing request UUID or slash path to update. If omitted (or not found when `parentId` is provided), creates a new request.
  - `parentId` (`string`, optional): Target collection or folder UUID/slash path when creating or moving a request.
  - `name` (`string`, required for new requests): Request display name.
  - `kind` (`string`, optional, default `"http"`): `"http"` | `"grpc"` | `"graphql"` | `"websocket"`.
  - `summary` (`string`, optional): Request summary/documentation.
  - `method` (`string`, optional): HTTP method (`"GET"`, `"POST"`, `"PUT"`, `"PATCH"`, `"DELETE"`, `"OPTIONS"`, `"HEAD"`, `"TRACE"`, `"CONNECT"`).
  - `url` (`string`, optional): Target URL or host:port endpoint.
  - `queryParams` (`array`, optional): `[{key, value, enabled, description}]`.
  - `headers` (`array`, optional): `[{key, value, enabled, secret, description}]`.
  - `auth` (`object`, optional): Auth configuration (`type`, `username`, `password`, `token`, `apiKey`, `apiValue`, `apiPlacement`, `oauth2*`). Any field passed as `"********"` during an update preserves its existing stored secret value.
  - `bodyType` (`string`, optional): `"NONE"`, `"JSON"`, `"XML"`, `"HTML"`, `"JAVASCRIPT"`, `"FORM_DATA"`, `"X_WWW_FORM_URLENCODED"`, `"RAW_TEXT"`, `"BINARY"`, `"GRAPHQL"`.
  - `bodyContent` (`string`, optional): Raw body payload.
  - `formDataEntries` (`array`, optional): `[{key, type, value, description, enabled}]` for `FORM_DATA` or `X_WWW_FORM_URLENCODED`.
  - `preRequestScript` (`string`, optional): Pre-request JavaScript code.
  - `testScript` (`string`, optional): Post-request test JavaScript code.
  - `grpc` (`object`, optional): `{protoFilePath, grpcMethod, grpcMethodType, metadata: [{name, value, disabled}], message}`.
  - `graphql` (`object`, optional): `{query, operationName, variablesJson, variants: [{name, query, operationName, variables}], settings: {method, prettify}}`.
  - `websocket` (`object`, optional): `{messageFormat, timeout, keepAliveInterval, variants: [{title, type, data, selected}]}`.
- **Output**: `SanitizedRequestDto`.

### 2.3 `clapi_delete_request`
Deletes a request.
- **Input Schema**:
  - `requestId` (`string`, required): Request UUID or slash path.
  - `confirm` (`boolean`, required): Must be `true`.
- **Output**: `DeleteResultDto`.

---

## 3. Environment & Scoped Variable Tools

### 3.1 `clapi_list_environments`
Lists all environments, active selection, inheritance relationships, and sanitized variable summaries.
- **Input Schema**:
  - `includeVariables` (`boolean`, optional, default `true`): Include sanitized variable projections.
- **Output**: `EnvironmentsOverviewDto`.

### 3.2 `clapi_set_active_environment`
Switches or clears the active environment.
- **Input Schema**:
  - `name` (`string`, optional): Environment name to activate, or `null`/`""` to clear active selection.
- **Output**: `EnvironmentsOverviewDto`.

### 3.3 `clapi_upsert_environment`
Creates or updates an environment.
- **Input Schema**:
  - `name` (`string`, required): Environment name.
  - `newName` (`string`, optional): Optional new name when renaming an existing environment.
  - `color` (`string`, optional): Hex color string (e.g., `"#4CAF50"`).
  - `extends` (`string`, optional): Parent environment name to inherit from (validated against circular inheritance).
  - `description` (`string`, optional): Environment description.
  - `externalSecrets` (`array`, optional): External secret declarations (`[{name, providerType, path, url, targetField, customFilters, enabled}]`).
- **Output**: `SanitizedEnvironmentDto`.

### 3.4 `clapi_delete_environment`
Deletes an environment.
- **Input Schema**:
  - `name` (`string`, required): Environment name.
  - `confirm` (`boolean`, required): Must be `true`.
- **Output**: `DeleteResultDto`.

### 3.5 `clapi_set_variable`
Creates, updates, or deletes a variable in Global, Environment, Collection, or Folder scope with full support for `VariableKind` and `VariableType`.
- **Input Schema**:
  - `scope` (`string`, required): `"global"` | `"environment"` | `"collection"` | `"folder"`.
  - `target` (`string`, optional): Environment name (when `scope == "environment"`), or collection/folder UUID/slash path (when `scope` is `"collection"` or `"folder"`).
  - `key` (`string`, required): Variable name.
  - `action` (`string`, optional, default `"upsert"`): `"upsert"` | `"delete"`.
  - `kind` (`string`, optional, default `"regular"`): `"regular"` | `"secret"` | `"dynamic"`.
  - `variableType` (`string`, optional, default `"string"`): `"string"` | `"number"` | `"boolean"` | `"object"`.
  - `value` (`string`, optional): Variable value (for `"regular"` and `"secret"`). When `kind == "secret"`, stored write-only and never echoed back.
  - `enabled` (`boolean`, optional, default `true`): Whether the variable is active.
  - `dynamicScript` (`string`, optional): JavaScript generator body when `kind == "dynamic"`.
  - `dynamicParameters` (`array`, optional): `[{name, type, required, defaultValue, description}]` when `kind == "dynamic"`.
- **Output**: `SanitizedVariableDto` (with `value = "********"` if `kind == "secret"`).

---

## 4. Execution & Inspection Tools

### 4.1 `clapi_execute_request`
Executes an HTTP, gRPC, or GraphQL request and caches its sanitized snapshot.
- **Input Schema**:
  - `requestId` (`string`, required): Request UUID or slash path.
  - `environmentName` (`string`, optional): Override environment for this execution (defaults to active environment).
  - `transientVariables` (`object`, optional): Key-value map of highest-precedence non-persisted variables for this single execution.
  - `maxBodyLength` (`integer`, optional, default `4096`): Maximum UTF-8 bytes returned in `bodyPreview` (clamped to `256..65536`).
- **Output**: `ExecutionResultDto`.

### 4.2 `clapi_inspect_response`
Inspects the cached sanitized response of a previously executed request without re-running the network request.
- **Input Schema**:
  - `requestId` (`string`, required): Request UUID or slash path.
  - `jsonPath` (`string`, optional): JSONPath query (e.g., `"$.data.users[0]"` or `"$.items[*].id"`).
  - `byteOffset` (`integer`, optional, default `0`): UTF-8 byte offset into the body (or into the JSONPath result).
  - `length` (`integer`, optional, default `4096`): Maximum bytes to return (clamped to `256..65536`).
- **Output**: `InspectResponseResultDto` (`requestId`, `jsonPath`, `byteOffset`, `returnedBytes`, `totalBytes`, `hasMore`, `content`).

### 4.3 `clapi_run_test_suite`
Executes all HTTP, gRPC, and GraphQL requests in a collection or folder sequentially and returns the test assertion matrix.
- **Input Schema**:
  - `targetId` (`string`, required): Collection or folder UUID or slash path.
  - `environmentName` (`string`, optional): Override environment name.
  - `stopOnFirstFailure` (`boolean`, optional, default `false`): Halt suite execution on first failed request or assertion.
- **Output**: `TestSuiteResultDto` (`targetName`, `totalRequests`, `passedRequests`, `failedRequests`, `totalAssertions`, `passedAssertions`, `failedAssertions`, `durationMs`, `results: [{requestId, name, slashPath, statusCode, passed, assertions, error}]`).

---

## 5. Import, Export & Git Sync Tools

### 5.1 `clapi_import_specification`
Imports an OpenAPI 3.0+, OpenCollection 1.0.0 (YAML/JSON), or Insomnia v4/v5 specification as a new collection.
- **Input Schema**:
  - `rawContent` (`string`, optional): Specification text content.
  - `filePath` (`string`, optional): Local filesystem path to read specification from (at least one of `rawContent` or `filePath` is required).
  - `collectionNameOverride` (`string`, optional): Optional custom name for the imported collection.
- **Output**: `SanitizedCollectionDto`.

### 5.2 `clapi_export_collection`
Exports a collection in sanitized OpenCollection or OpenAPI 3.0.3 format.
- **Input Schema**:
  - `collectionId` (`string`, required): Collection UUID or name.
  - `format` (`string`, optional, default `"opencollection-yaml"`): `"opencollection-yaml"` | `"opencollection-json"` | `"openapi-yaml"` | `"openapi-json"`.
- **Output**: `ExportResultDto` (`collectionName`, `format`, `content`).

### 5.3 `clapi_git_sync`
Performs Git operations on a Git-backed collection.
- **Input Schema**:
  - `collectionId` (`string`, required): Collection UUID or name.
  - `action` (`string`, required): `"status"` | `"commit"` | `"pull"` | `"push"`.
  - `commitMessage` (`string`, optional): Required when `action == "commit"`.
- **Output**: `GitSyncResultDto` (`collectionName`, `action`, `branch`, `remoteUrl`, `status`, `modifiedFiles`, `message`).
