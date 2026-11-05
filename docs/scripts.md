# Clapi Scripting Guide (Complete Reference)

This document is the full scripting reference for Clapi’s built-in JavaScript runtime.

Clapi supports two script execution points:

- **Pre-request script**: runs before request dispatch.
- **Post-request script**: runs after response is received.

Both use the same API surface.

---

## 1) Runtime model

### Namespace prefix

- Default namespace: `clapi`
- Configurable in **Settings → Scripts → Script namespace prefix**
- Example custom prefix: `api` (then use `api.test(...)`, `api.expect(...)`, etc.)

### Backward-compatible global aliases

The runtime also exposes unprefixed globals for compatibility:

- `test`, `expect`, `response`, `request`
- `environment`, `globals`, `collectionVariables`, `variables`
- short aliases: `env`, `global`, `collection`, `vars`
- `console.log(...)`

### Execution behavior

- Scripts run in a sandboxed JS runtime.
- Execution is **synchronous** in request flow.
- Async callbacks (for example delayed callbacks) are not guaranteed to affect the current request before send completes.
- Script runtime returns:
  - test results
  - logs
  - variable mutations
  - request mutations
  - error (if any)

### Scope limitation

- Scripts currently apply to **HTTP requests**.
- WebSocket requests do not execute pre-request/post-request scripts.

---

## 2) API surface (everything supported)

## 2.1 Test registration

| API | Description |
|---|---|
| `clapi.test(name, fn)` | Registers and executes one named test block |

Example:

```javascript
clapi.test("Status is 200", function () {
  clapi.expect(clapi.response.code).to.equal(200)
})
```

---

## 2.2 Assertions (`clapi.expect`)

Start an assertion chain:

```javascript
clapi.expect(actual)
```

### Chain words (syntax sugar)

Supported chain words:

`to`, `be`, `been`, `is`, `that`, `which`, `and`, `has`, `have`, `with`, `at`, `of`, `same`, `but`, `does`, `still`, `also`, `a`, `an`

### Positive / negative toggle

| API | Description |
|---|---|
| `.not` | Negates next assertion |

### Equality / inclusion / match

| API | Notes |
|---|---|
| `.equal(expected)` | strict equality, with deep fallback for objects/arrays |
| `.eql(expected)` | alias of `equal` |
| `.equals(expected)` | alias of `equal` |
| `.include(value)` | works for strings, arrays, and object property presence |
| `.contain(value)` | alias of `include` |
| `.includes(value)` | alias of `include` |
| `.contains(value)` | alias of `include` |
| `.match(regexOrPattern)` | accepts regex or string pattern |

### Numeric assertions

| API | Notes |
|---|---|
| `.above(n)` | greater than |
| `.greaterThan(n)` | alias of `above` |
| `.below(n)` | less than |
| `.lessThan(n)` | alias of `below` |
| `.at.least(n)` | greater than or equal |
| `.at.most(n)` | less than or equal |

### Set/object/length assertions

| API | Notes |
|---|---|
| `.oneOf([a, b, c])` | value exists in list |
| `.property(name)` / `.have.property(name)` | object contains key |
| `.lengthOf(n)` | array/string length equals `n` |
| `.length(n)` | alias of `lengthOf` |

### Getter assertions

| API | Notes |
|---|---|
| `.exist` | value is not `null`/`undefined` |
| `.ok` | truthy |
| `.empty` | `""`, `null`, `undefined`, empty array, or empty object |
| `.null` | strictly `null` |
| `.true` | strictly `true` |
| `.false` | strictly `false` |
| `.undefined` | strictly `undefined` |

---

## 2.3 Response API (`clapi.response`)

| API | Type | Description |
|---|---|---|
| `response.code` | number \| null | HTTP status code |
| `response.statusCode` | number \| null | alias of `code` |
| `response.status` | string | status text (for known codes) |
| `response.responseTime` | number \| null | request duration in ms |
| `response.time` | number \| null | alias of `responseTime` |
| `response.size` | number \| null | payload size in bytes |
| `response.text()` | string | response body string (empty string if null body) |
| `response.json()` | object \| array \| null | parses JSON body |
| `response.headers.get(name)` | string \| undefined | case-insensitive header lookup |
| `response.statusIs(code)` | void | throws if status code differs |
| `response.statusOk()` | void | throws if status is not 2xx |
| `response.hasHeader(name, value?)` | void | throws if header absent or value mismatches |
| `response.to.have.status(code)` | void | Postman-compatible chain — calls `statusIs(code)` |
| `response.to.have.header(name, value?)` | void | Postman-compatible chain — calls `hasHeader(name, value?)` |
| `response.to.be.ok` | void | Postman-compatible chain — calls `statusOk()` |
| `response.streamEvents` | array | Raw SSE/NDJSON event payloads (empty for non-stream responses) |
| `response.llm.assembledText` | string | Concatenated assistant text from stream deltas or `choices[0].message.content` |
| `response.llm.finishReason` | string \| null | `stop`, `tool_calls`, etc. |
| `response.llm.usage` | object \| null | Token usage object when the provider sent one (`prompt_tokens`, `completion_tokens`, `total_tokens`) |
| `response.llm.jsonContent()` | object \| null | Parses `assembledText` as JSON (JSON-mode chat); `null` if it is not JSON |

Notes:

- `response.json()` throws if body is invalid JSON.
- `response.status` is populated for mapped HTTP status codes.
- For SSE, `response.text()` / Body view use assembled assistant text, not the raw `data:` frames. Use `response.streamEvents` or the RAW tab for individual chunks.

```javascript
clapi.test("streamed chat", function () {
  clapi.expect(clapi.response.llm.assembledText).to.include("Hello")
  clapi.expect(clapi.response.llm.finishReason).to.equal("stop")
  clapi.expect(clapi.response.streamEvents.length).to.be.above(0)
})
```

---

## 2.4 Request API (`clapi.request`)

### Read access

| API | Description |
|---|---|
| `request.url` | current URL (after pre-request mutations if any) |
| `request.method` | current method (after mutations if any) |
| `request.body` | current body (after mutations if any) |
| `request.headers.get(name)` | reads original header or mutated value |
| `request.query.get(name)` / `request.params.get(name)` | reads query param |

### Request mutation APIs

| API | Description |
|---|---|
| `request.setHeader(name, value)` | set/replace outgoing header |
| `request.headers.add(name, value)` | add header (set semantics) |
| `request.headers.upsert(name, value)` | upsert header |
| `request.setQueryParam(name, value)` | set/replace query parameter |
| `request.setMethod(method)` | set HTTP method (uppercased) |
| `request.setUrl(url)` | replace request URL |
| `request.setBody(content)` | replace body content |

Best practice: perform request mutations in pre-request scripts.

---

## 2.5 Variables API

Clapi exposes four variable scopes.

### Environment scope

| API |
|---|
| `clapi.environment.get(key)` |
| `clapi.environment.set(key, value)` |
| `clapi.environment.unset(key)` |
| `clapi.environment.clear()` |
| `clapi.environment.has(key)` |
| `clapi.environment.toObject()` |
| `clapi.environment.replaceIn(template)` |

### Global scope

| API |
|---|
| `clapi.globals.get(key)` |
| `clapi.globals.set(key, value)` |
| `clapi.globals.unset(key)` |
| `clapi.globals.clear()` |
| `clapi.globals.has(key)` |
| `clapi.globals.toObject()` |
| `clapi.globals.replaceIn(template)` |

### Collection scope

| API |
|---|
| `clapi.collectionVariables.get(key)` |
| `clapi.collectionVariables.set(key, value)` |
| `clapi.collectionVariables.unset(key)` |
| `clapi.collectionVariables.clear()` |
| `clapi.collectionVariables.has(key)` |
| `clapi.collectionVariables.toObject()` |
| `clapi.collectionVariables.replaceIn(template)` |

### Request-local merged scope (`variables`)

| API |
|---|
| `clapi.variables.get(key)` |
| `clapi.variables.set(key, value)` |
| `clapi.variables.unset(key)` |
| `clapi.variables.has(key)` |

`clapi.variables.get(key)` resolution order:

1. request-local values set with `clapi.variables.set`
2. environment scope
3. collection scope
4. global scope

`clapi.variables.has(key)` checks all four scopes in the same order.

---

## 2.6 Logging API

| API | Description |
|---|---|
| `clapi.console.log(...args)` | writes to script/console output |
| `console.log(...args)` | global alias |

Notes:

- `null` and `undefined` are logged safely.
- Objects are JSON-stringified; circular objects are logged as `[Object]` fallback.

---

## 2.7 Info API (`clapi.info`)

Read-only metadata about the current script execution context.

| API | Type | Description |
|---|---|---|
| `clapi.info.requestName` | string | Name of the current request (empty string) |
| `clapi.info.requestId` | string | Internal request ID (empty string) |
| `clapi.info.iteration` | number | Current iteration number (1-based) |
| `clapi.info.iterationCount` | number | Total number of iterations (1) |
| `clapi.info.eventName` | string | Script event name (empty string) |

Note: These are stub values. Clapi does not have a collection runner, so iteration-related fields always return their default.

---

## 2.8 Stub APIs (`clapi.iterationData`, `clapi.cookies`)

These APIs are present to prevent runtime errors when importing Postman collections that use them. They do not provide real data.

### `clapi.iterationData`

| API | Returns |
|---|---|
| `.get(key)` | always `undefined` |
| `.has(key)` | always `false` |
| `.toObject()` | always `{}` |

### `clapi.cookies`

| API | Returns |
|---|---|
| `.get(name)` | always `undefined` |
| `.has(name)` | always `false` |
| `.jar()` | returns a no-op jar object with `.get()`, `.set()`, `.unset()`, `.clear()` |

---

## 2.9 `clapi.sendRequest()` — HTTP sub-requests from scripts

`clapi.sendRequest()` lets you make a real HTTP request from inside a pre-request or test script. The callback runs after the HTTP response arrives, and any `clapi.test()` assertions or `clapi.environment.set()` calls inside the callback are merged into the overall script result.

### Signature

```javascript
clapi.sendRequest(urlOrOptions, callback)
```

### String URL form (simple GET)

```javascript
clapi.sendRequest("https://api.example.com/token", function(err, resp) {
    clapi.environment.set("token", resp.json().token)
})
```

### Options object form (POST with body and headers)

```javascript
clapi.sendRequest({
    url: clapi.variables.get("baseUrl") + "/auth",
    method: "POST",
    header: [{ key: "Content-Type", value: "application/json" }],
    body: {
        mode: "raw",
        raw: JSON.stringify({ user: "alice", password: "s3cr3t" })
    }
}, function(err, resp) {
    clapi.environment.set("authToken", resp.json().access_token)
})
```

### Options object fields

| Field | Type | Description |
|---|---|---|
| `url` | string | Request URL (required) |
| `method` | string | HTTP method, default `"GET"` |
| `header` | array or object | Headers — array of `{key, value}` objects or plain key/value map |
| `headers` | object | Alternative header map (same as `header` when it's an object) |
| `body` | string or `{raw, mode}` | Request body. Pass a string directly or a Postman-style `{mode:"raw", raw:"..."}` object |

### Callback response object (`resp`)

| Property/Method | Description |
|---|---|
| `resp.code` | HTTP status code (integer) |
| `resp.status` | Status text, e.g. `"OK"` |
| `resp.responseTime` | Elapsed milliseconds |
| `resp.json()` | Parses response body as JSON |
| `resp.text()` | Returns raw response body as string |
| `resp.headers.get(name)` | Returns a response header by name (case-insensitive) |

### Using assertions in the callback

Assertions added inside the callback contribute to the overall test result:

```javascript
clapi.sendRequest("{{baseUrl}}/api/token", function(err, resp) {
    clapi.test("token endpoint returns 200", function() {
        clapi.expect(resp.code).to.equal(200)
    })
    clapi.test("token is present", function() {
        clapi.expect(resp.json().token).to.exist
    })
})
```

### Typical pattern: fetch auth token before the main request

```javascript
// Pre-request script
clapi.sendRequest({
    url: clapi.variables.get("baseUrl") + "/api/token",
    method: "POST",
    header: [{ key: "Content-Type", value: "application/json" }],
    body: { mode: "raw", raw: JSON.stringify({ user: "alice", role: "admin" }) }
}, function(err, resp) {
    var tok = resp.json().token
    clapi.environment.set("authToken", tok)
    clapi.request.headers.upsert("Authorization", "Bearer " + tok)
})
```

### Error handling

If the sub-request fails (network error, invalid URL), the error is appended to the script logs and the callback is not invoked. The main script continues normally.

---

## 3) Pre-request script patterns

## 3.1 Header/query/body mutation

```javascript
var runId = "run-" + Date.now()
clapi.environment.set("runId", runId)

clapi.request.headers.upsert("X-Run-Id", runId)
clapi.request.setQueryParam("source", "pre-script")
clapi.request.setBody('{"from":"pre-request"}')
```

## 3.2 Method/URL override

```javascript
clapi.request.setMethod("POST")
clapi.request.setUrl("https://api.example.com/v2/items")
```

## 3.3 Defensive variable handling

```javascript
var token = clapi.globals.get("token")
clapi.request.headers.upsert("Authorization", token ? "Bearer " + token : "")
```

---

## 4) Post-request script patterns

## 4.1 Status/body assertions

```javascript
clapi.test("status is 200", function () {
  clapi.expect(clapi.response.code).to.equal(200)
})

clapi.test("response has id", function () {
  var body = clapi.response.json()
  clapi.expect(body).to.have.property("id")
})
```

## 4.2 Timing/size assertions

```javascript
clapi.test("time under 2s", function () {
  clapi.expect(clapi.response.responseTime).to.be.below(2000)
})

clapi.test("payload not empty", function () {
  clapi.expect(clapi.response.size).to.be.above(0)
})
```

## 4.3 Extract value for next request

```javascript
clapi.test("token exists", function () {
  var token = clapi.response.json().token
  clapi.expect(token).to.exist
  clapi.environment.set("token", String(token))
})
```

---

## 5) Error handling and result semantics

- A thrown top-level script error sets script `error` and marks execution failed.
- `clapi.test(...)` captures failures per test block; mixed pass/fail is supported.
- Overall post-request script success is false if any assertion fails.

Recommended pattern:

```javascript
clapi.test("json parse safe", function () {
  var threw = false
  try { clapi.response.json() } catch (e) { threw = true }
  clapi.expect(threw).to.be.false
})
```

---

## 6) Professional usage guidelines

- Keep pre-request logic deterministic and idempotent.
- Prefer explicit test names for readable test reports.
- Use `toObject()` and `has()` for scope introspection when debugging.
- Guard optional values (`null`/`undefined`) before strict assertions.
- Avoid async timing-dependent logic in request-critical paths.

---

## 7) Related docs

- Supplemental overview: [docs/scripts/README.md](docs/scripts/README.md)
- API-focused reference: [docs/scripts/api-reference.md](docs/scripts/api-reference.md)

---

## 8) Postman migration guide

When a Postman collection is imported, Clapi automatically rewrites all `pm.*` calls and the even older `postman.*` sandbox calls to their `clapi.*` equivalents. The table below shows every mapping applied.

### 8.1 Modern `pm.*` API (Postman v6+)

| Postman | Clapi | Notes |
|---|---|---|
| `pm.test(name, fn)` | `clapi.test(name, fn)` | |
| `pm.expect(v)` | `clapi.expect(v)` | |
| `pm.response.to.have.status(code)` | `clapi.response.statusIs(code)` | Converted before generic `pm.response` replacement |
| `pm.response.to.have.header(name)` | `clapi.response.hasHeader(name)` | |
| `pm.response.to.be.ok` | `clapi.response.statusOk()` | |
| `pm.response` | `clapi.response` | Catch-all for remaining `pm.response.*` access |
| `pm.request` | `clapi.request` | Full object reference |
| `pm.environment.get(k)` | `clapi.environment.get(k)` | |
| `pm.environment.set(k, v)` | `clapi.environment.set(k, v)` | |
| `pm.environment.unset(k)` | `clapi.environment.unset(k)` | |
| `pm.environment.clear()` | `clapi.environment.clear()` | |
| `pm.environment.has(k)` | `clapi.environment.has(k)` | |
| `pm.globals.get(k)` | `clapi.globals.get(k)` | Preserved in globals scope |
| `pm.globals.set(k, v)` | `clapi.globals.set(k, v)` | |
| `pm.globals.unset(k)` | `clapi.globals.unset(k)` | |
| `pm.globals.clear()` | `clapi.globals.clear()` | |
| `pm.globals.has(k)` | `clapi.globals.has(k)` | |
| `pm.collectionVariables.get(k)` | `clapi.collectionVariables.get(k)` | Preserved in collection scope |
| `pm.collectionVariables.set(k, v)` | `clapi.collectionVariables.set(k, v)` | |
| `pm.collectionVariables.unset(k)` | `clapi.collectionVariables.unset(k)` | |
| `pm.collectionVariables.clear()` | `clapi.collectionVariables.clear()` | |
| `pm.collectionVariables.has(k)` | `clapi.collectionVariables.has(k)` | |
| `pm.variables.get(k)` | `clapi.variables.get(k)` | Merged scope with priority resolution |
| `pm.variables.set(k, v)` | `clapi.variables.set(k, v)` | |
| `pm.variables.unset(k)` | `clapi.variables.unset(k)` | |
| `pm.variables.has(k)` | `clapi.variables.has(k)` | |
| `pm.info` | `clapi.info` | Stub — see Section 2.7 |
| `pm.iterationData` | `clapi.iterationData` | Stub — see Section 2.8 |
| `pm.cookies` | `clapi.cookies` | Stub — see Section 2.8 |
| `pm.execution.setNextRequest(req)` | `clapi.execution.setNextRequest(req)` | Supported via execution API mapping |
| `pm.execution.skipRequest()` | `clapi.execution.skipRequest()` | Supported via execution API mapping |
| `pm.sendRequest(url, cb)` | `clapi.sendRequest(url, cb)` | Fully supported — see Section 2.9 |

### 8.2 Legacy `postman.*` API (Postman pre-v6)

Collections exported from older versions of Postman use a `postman.*` namespace and global sandbox variables. All of these are converted on import.

| Postman (old) | Clapi | Notes |
|---|---|---|
| `postman.setEnvironmentVariable(k, v)` | `clapi.environment.set(k, v)` | |
| `postman.getEnvironmentVariable(k)` | `clapi.environment.get(k)` | |
| `postman.clearEnvironmentVariable(k)` | `clapi.environment.unset(k)` | |
| `postman.clearEnvironmentVariables()` | `clapi.environment.clear()` | |
| `postman.setGlobalVariable(k, v)` | `clapi.globals.set(k, v)` | Preserved in globals scope |
| `postman.getGlobalVariable(k)` | `clapi.globals.get(k)` | |
| `postman.clearGlobalVariable(k)` | `clapi.globals.unset(k)` | |
| `postman.clearGlobalVariables()` | `clapi.globals.clear()` | |
| `postman.setNextRequest(req)` | `clapi.execution.setNextRequest(req)` | Supported via execution API mapping |
| `responseBody` (global string) | `response.text()` | Old sandbox global |
| `responseCode.code` (global object) | `response.code` | Old sandbox global |
| `responseCode.name` | `response.status` | Old sandbox global |

### 8.3 Example — full legacy login script

Original Postman script (old API):

```javascript
var jsonData = JSON.parse(responseBody);
if (responseCode.code === 200) {
    console.log("Login succeeded for user " + postman.getEnvironmentVariable('USERNAME'));
    postman.setEnvironmentVariable('SESSION_ID', jsonData.sessionId);
    postman.setEnvironmentVariable('ORG_ID', jsonData.orgId);
} else {
    postman.setNextRequest(null);
    console.log("Login failed for user " + postman.getEnvironmentVariable('USERNAME'));
}
```

After Clapi import (converted automatically):

```javascript
var jsonData = JSON.parse(response.text());
if (response.code === 200) {
    console.log("Login succeeded for user " + clapi.environment.get('USERNAME'));
    clapi.environment.set('SESSION_ID', jsonData.sessionId);
    clapi.environment.set('ORG_ID', jsonData.orgId);
} else {
  clapi.execution.setNextRequest(null);
  console.log("Login failed for user " + clapi.environment.get('USERNAME'));
}
```

### 8.4 What is NOT automatically migrated

| Feature | Why |
|---|---|
| Unrecognized custom Postman globals/helpers | Clapi rewrites known APIs only; non-standard helpers require manual edits |

> **Note:** `pm.sendRequest()` is fully supported and translated to `clapi.sendRequest()` on import. See [Section 2.9](#29-clapisendrequest--http-sub-requests-from-scripts) for the complete API reference.