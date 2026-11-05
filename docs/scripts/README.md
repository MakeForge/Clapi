# Clapi Scripts

Clapi scripts use a namespaced API under the `clapi` prefix (configurable in **Settings → Scripts**).

## Where scripts run

- **Pre-request script** — runs before the HTTP request is built and sent.
- **Post-request script** — runs after the response is received.
- Scripts apply to HTTP requests. WebSocket requests currently do not execute pre-request or post-request scripts.

## Namespace prefix

The default namespace is `clapi`. You can change it in **Settings → Scripts → Script namespace prefix**. Any identifier (letters, digits, `_`) is valid, e.g. `api`, `rl`, or `myns`.

The global (prefix-free) API is also supported for backward compatibility.

## Core API

| Namespaced form | Description |
|---|---|
| `clapi.test(name, fn)` | Register a named test block |
| `clapi.expect(value)` | Begin a Chai-style assertion chain |
| `clapi.response.code` | HTTP status code (number) |
| `clapi.response.status` | HTTP status text |
| `clapi.response.responseTime` | Round-trip time in ms |
| `clapi.response.size` | Response body size in bytes |
| `clapi.response.text()` | Response body as plain text |
| `clapi.response.json()` | Response body parsed as JSON |
| `clapi.response.headers.get(name)` | Response header value |
| `clapi.response.streamEvents` | Raw SSE/NDJSON event payloads |
| `clapi.response.llm.assembledText` | Assembled assistant / stream text |
| `clapi.response.llm.finishReason` | Chat `finish_reason` |
| `clapi.response.llm.usage` | Token usage object |
| `clapi.response.llm.jsonContent()` | Parse assembled text as JSON |
| `clapi.request.url` | Request URL |
| `clapi.request.method` | Request method |
| `clapi.request.body` | Request body |
| `clapi.request.headers.add(name, value)` | Add a request header |
| `clapi.request.headers.upsert(name, value)` | Set/replace a request header |
| `clapi.environment.get/set/unset(key)` | Environment variables |
| `clapi.globals.get/set/unset(key)` | Global variables |
| `clapi.collectionVariables.get/set/unset(key)` | Collection variables |
| `clapi.variables.get/set/unset(key)` | Merged variable lookup + request-local overrides |
| `clapi.console.log(...)` | Log to the script output panel |
| `clapi.execution.setNextRequest(nameOrIdOrRef)` | Chain to another saved request |
| `clapi.execution.skipRequest()` | Skip current request from pre-request script |

## Runtime behavior summary

- Scripts execute in a sandboxed JavaScript runtime with the configured namespace prefix.
- Pre-request scripts can mutate request URL, method, body, headers, and query params before send.
- Post-request scripts can read response code/status/time/size/body/headers and register multiple named tests.
- `environment`, `globals`, `collectionVariables`, and `variables` scopes are available inside scripts.
- Script tabs support in-editor JavaScript formatting from the editor toolbar.
- The engine executes JavaScript synchronously for request flow; asynchronous callbacks are not awaited before request dispatch.

## Example — pre-request script

```javascript
var runId = "run-" + Date.now()
clapi.environment.set("runId", runId)
clapi.request.headers.upsert("X-Run-Id", runId)
clapi.request.setQueryParam("source", "script")
clapi.request.setBody('{"from":"pre-request"}')
clapi.console.log("Prepared request", clapi.request.method, clapi.request.url)
```

## Example — post-request script

```javascript
clapi.test("Status is 200", function() {
  clapi.expect(clapi.response.code).to.equal(200)
})

clapi.test("Response time is acceptable", function() {
  clapi.expect(clapi.response.responseTime).to.be.below(2000)
})

clapi.test("Payload shape is valid", function() {
  var body = clapi.response.json()
  clapi.expect(body).to.have.property("id")
  clapi.expect(typeof body.id).to.equal("string")
  clapi.expect(body.type).to.be.oneOf(["Subscriber", "Admin"])
})

clapi.test("streamed assistant text", function() {
  clapi.expect(clapi.response.llm.assembledText).to.include("Hello")
  clapi.expect(clapi.response.streamEvents.length).to.be.above(0)
})

clapi.environment.set("lastStatus", String(clapi.response.code))
clapi.console.log("Captured lastStatus", clapi.environment.get("lastStatus"))
```

## Alternative prefix

```javascript
// Settings → Scripts → "api"
api.test("Status is 200", function() {
  api.expect(api.response.code).to.equal(200)
})
```

## Undefined / empty-safe usage

```javascript
var g = clapi.globals.get("suiteGlobal")
var c = clapi.collectionVariables.get("suiteCollection")
var l = clapi.variables.get("requestOnly")

clapi.environment.set("safeGlobal", g == null ? "" : String(g))
clapi.environment.set("safeCollection", c == null ? "" : String(c))
clapi.environment.set("safeLocal", l == null ? "" : String(l))

clapi.test("undefined getters are safe", function() {
  clapi.expect(clapi.globals.get("missingGlobal")).to.be.undefined
  clapi.expect(clapi.collectionVariables.get("missingCollection")).to.be.undefined
  clapi.expect(clapi.variables.get("missingVar")).to.be.undefined
})
```
