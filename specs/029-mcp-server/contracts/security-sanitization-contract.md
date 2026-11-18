# Contract: Zero-Exfiltration Security & Sanitization Pipeline

**Feature**: `029-mcp-server` | **Spec**: [spec.md](../spec.md) | **Data Model**: [data-model.md](../data-model.md)

---

## 1. Security Invariants

1. **No Plaintext Secret Variables**:
   - Any variable with `kind == VariableKind.SECRET` (`secret == true` or `SecretVariable`) in Global, Environment, Collection, Folder, or Request scope MUST return `value = "********"`, `secret = true`, `isSet = value.isNotBlank()`.
   - When `clapi_set_variable` is called with `kind = "secret"`, the value is persisted to the backing store, and the returned confirmation DTO has `value = "********"`.

2. **No Plaintext Authentication Credentials**:
   - In `SanitizedAuthDto`, `password`, `token`, `apiValue`, `oauth2ClientSecret`, and `oauth2Password` MUST always be serialized as `"********"` (or `null` when empty), accompanied by `isConfigured: Boolean`.
   - When `clapi_upsert_request`, `clapi_manage_collection`, or `clapi_manage_folder` receives `"********"` for any masked auth credential field during an update, the server MUST preserve the existing stored credential value rather than overwriting it with literal asterisks.

3. **No External Secret Manager Provider Credentials**:
   - Provider master credentials stored in `AppSettings` / `PlatformStorage` (`vaultToken`, `awsSecretAccessKey`, `awsSessionToken`, `azureClientSecret`, `gcpServiceAccountJson`, `gcpAccessToken`, `keeperConfig`, `keepassxcKey`) MUST NOT appear in any MCP resource, tool schema, or output.

4. **Header Masking**:
   - Outgoing request headers and incoming response headers whose lowercased key matches any entry in:
     `{"authorization", "proxy-authorization", "x-api-key", "api-key", "token", "cookie", "set-cookie", "x-auth-token"}`
     or whose entry has `secret == true` MUST have their value masked:
     - If the value begins with a recognized auth scheme (`Bearer `, `Basic `, `Digest `, `Token `), preserve only the scheme prefix: e.g., `"Bearer ********"`.
     - Otherwise replace the value with `"********"`.

5. **Full-Text Secret Value Scrubbing (Echo & Error Redaction)**:
   - Before executing a request or test suite, collect all resolved secret strings (from secret variables across all scopes, request/folder/collection auth credentials, and resolved external secret values) where `secret.trim().length >= 4`.
   - Sort collected secret strings by length descending.
   - Replace every occurrence of each secret string (and its URL-encoded and Base64-encoded forms when applicable, e.g., Basic Auth `username:password` Base64 token) with `"[REDACTED_SECRET]"` across:
     - `ResponseDefinition.bodyText` / `assembledText` / `streamEvents`
     - `ResponseDefinition.headers` (non-sensitive header values that might echo a token)
     - `ScriptResult.logs` and `ScriptResult.error`
     - `AssertionResult.message`
     - Exception / error diagnostic strings returned in `McpToolResult` or `JsonRpcError`.
   - Store the resulting `ExecutionSnapshot` in `ResponseSnapshotCache` **only after** scrubbing has completed.

6. **Export Sanitization**:
   - `clapi_export_collection` MUST strip or mask (`"********"` / `""`) all `SecretVariable` values and auth credential secrets before returning exported OpenCollection JSON/YAML or OpenAPI 3.0.3 JSON/YAML content.
