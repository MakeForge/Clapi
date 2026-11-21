# Quickstart & Validation Guide: Clapi Model Context Protocol (MCP) Server

**Feature**: `029-mcp-server` | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

---

## Prerequisites

- JDK 17+ (Zulu 21 recommended)
- Repository checked out on branch `029-mcp-server`

---

## 1. Automated Verification Commands

Run the `:mcp-server` unit test suite (protocol handshake, resources, tools, prompts, JSONPath response inspector, and zero-exfiltration secret sanitization):

```bash
rtk gradlew :mcp-server:allTests
```

Run shared UI and Desktop integration tests (verifying `AppStateWorkspaceBackend`, live UI synchronization, and MCP Server Settings UI):

```bash
rtk gradlew :ui-shared:desktopTest :ui-desktop:desktopTest
```

Run end-to-end QA tests against the embedded sample server (verifying multi-protocol request execution, test suite runner, specification import/export, and echo-endpoint secret scrubbing):

```bash
rtk gradlew :qa-tests:jvmTest
```

---

## 2. Manual & Interactive Validation Scenarios

### Scenario A: Headless CLI Stdio Mode (`--mcp`)

1. Start Clapi in headless MCP stdio mode pointing to an isolated workspace directory:
   ```bash
   rtk gradlew :ui-desktop:run --args="--mcp --dir /tmp/clapi-mcp-test"
   ```
2. Or connect from an MCP client (such as Claude Code, Cursor, or Clapi's own MCP Client tab) with command:
   - Executable: `java`
   - Arguments: `["-jar", "ui-desktop/build/distribute/Clapi-1.19.0.jar", "--mcp", "--dir", "/tmp/clapi-mcp-test"]`
3. Verify:
   - `initialize` returns protocol version `2025-06-18` with `tools`, `resources`, and `prompts` capabilities and zero non-JSON-RPC noise on `stdout`.
   - `resources/list` exposes `clapi://tree`, `clapi://environments`, and `clapi://logs/console`.
   - `tools/list` exposes all 15 `clapi_*` tools defined in [mcp-tools-contract.md](contracts/mcp-tools-contract.md).
   - `prompts/list` exposes `debug_api_failure`, `generate_api_tests`, and `curl_to_clapi_request`.

### Scenario B: Zero-Exfiltration Secret Verification

1. Call `clapi_upsert_environment` with `name = "Prod"`.
2. Call `clapi_set_variable` with `scope = "environment"`, `target = "Prod"`, `key = "apiToken"`, `kind = "secret"`, `value = "secret-token-value-12345"`.
3. Read `clapi://environments/Prod` and verify `apiToken` shows `"value": "********", "secret": true, "isSet": true`.
4. Author a request pointing to `http://localhost:8080/api/echo-headers` with header `Authorization: Bearer {{apiToken}}` via `clapi_upsert_request`.
5. Execute the request via `clapi_execute_request` with `environmentName = "Prod"`.
6. Verify that in both `clapi_execute_request` and `clapi_inspect_response`:
   - Outgoing `Authorization` header is masked as `"Bearer ********"`.
   - The echoed token inside the JSON response body from `/api/echo-headers` is scrubbed to `"[REDACTED_SECRET]"`.

### Scenario C: Desktop Embedded HTTP Mode & Live UI Sync

1. Launch Clapi Desktop (`rtk gradlew :ui-desktop:runDev`).
2. Open **Settings → MCP Server**, enable the embedded MCP server on port `3333` (`http://127.0.0.1:3333/mcp`).
3. Open an MCP Request tab inside Clapi (or connect an external MCP client) pointing to `http://127.0.0.1:3333/mcp`.
4. Invoke `clapi_manage_collection(action="create", name="Agent Created API")` and `clapi_upsert_request(parentId="Agent Created API", name="Health Check", method="GET", url="http://localhost:8080/")`.
5. Confirm that the `"Agent Created API"` collection and `"Health Check"` request appear immediately in the Clapi Desktop sidebar without restarting or reloading.
