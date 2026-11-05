# Script API Reference

All APIs are available under the configurable namespace prefix (default: `clapi`). The global (unprefixed) form is also supported for backward compatibility.

## Test and assertion

| Form | Description |
|---|---|
| `clapi.test(name, fn)` | Register a named test block |
| `clapi.expect(actual).to.equal(expected)` | Strict equality |
| `clapi.expect(actual).to.eql(expected)` | Deep equality |
| `clapi.expect(actual).to.include(text)` | Substring or array containment |
| `clapi.expect(actual).to.match(/regex/)` | Regex match |
| `clapi.expect(actual).to.be.oneOf([a, b])` | Value in array |
| `clapi.expect(actual).to.be.above(n)` | Greater than |
| `clapi.expect(actual).to.be.below(n)` | Less than |
| `clapi.expect(actual).to.be.at.least(n)` | Greater than or equal |
| `clapi.expect(actual).to.be.at.most(n)` | Less than or equal |
| `clapi.expect(actual).to.exist` | Not null/undefined |
| `clapi.expect(actual).to.be.ok` | Truthy |
| `clapi.expect(actual).to.be.empty` | Empty string / array |
| `clapi.expect(actual).to.be.null` | Strictly null |
| `clapi.expect(actual).to.be.true` | Strictly true |
| `clapi.expect(actual).to.be.false` | Strictly false |
| `clapi.expect(actual).to.be.undefined` | Strictly undefined |
| `clapi.expect(obj).to.have.property(key)` | Object has property |
| `clapi.expect(arrOrStr).to.have.lengthOf(n)` | Length equals n |
| `clapi.expect(actual).to.not.equal(expected)` | Not equal |

Chain words supported in assertion syntax:
- `.to`, `.be`, `.been`, `.is`, `.that`, `.which`, `.and`, `.has`, `.have`, `.with`, `.at`, `.of`, `.same`, `.but`, `.does`, `.still`, `.also`, `.a`, `.an`

Examples:

```javascript
clapi.test("status + payload", function () {
	const body = clapi.response.json()
	clapi.expect(clapi.response.code).to.equal(200)
	clapi.expect(body.type).to.be.oneOf(["Subscriber", "Admin"])
	clapi.expect(body).to.have.property("name")
	clapi.expect(body.name).to.match(/Jane/)
})
```

## `clapi.response`

| Property / Method | Description |
|---|---|
| `clapi.response.code` | HTTP status code (number, e.g. `200`) |
| `clapi.response.status` | HTTP status text (e.g. `"OK"`) |
| `clapi.response.responseTime` | Round-trip time in milliseconds |
| `clapi.response.size` | Response body size in bytes |
| `clapi.response.text()` | Response body as a plain string |
| `clapi.response.json()` | Response body parsed as JSON; supports path access (`clapi.response.json().user.id`) |
| `clapi.response.headers.get(name)` | Response header value by name (case-insensitive) |
| `clapi.response.streamEvents` | Raw SSE/NDJSON event payload strings |
| `clapi.response.llm.assembledText` | Assembled assistant text from stream deltas (or non-stream `message.content`) |
| `clapi.response.llm.finishReason` | Provider `finish_reason` (`stop`, `tool_calls`, …) |
| `clapi.response.llm.usage` | Token usage object, or `null` |
| `clapi.response.llm.jsonContent()` | Parse assembled text as JSON (JSON-mode bodies) |

Notes:
- `clapi.response.json()` throws if body is not valid JSON.
- `clapi.response.headers.get(name)` is case-insensitive.
- `clapi.response.size` and `clapi.response.responseTime` are numeric.
- For streams, `clapi.response.text()` is the assembled assistant text. Use `streamEvents` for the raw chunks.

```javascript
clapi.test("llm stream", function () {
	clapi.expect(clapi.response.llm.assembledText).to.include("Hello")
	clapi.expect(clapi.response.llm.finishReason).to.equal("stop")
	clapi.expect(clapi.response.streamEvents.length).to.be.above(0)
})
```

## `clapi.request`

| Property / Method | Description |
|---|---|
| `clapi.request.url` | Request URL (read) |
| `clapi.request.method` | Request HTTP method (read) |
| `clapi.request.body` | Request body (read) |
| `clapi.request.headers.add(name, value)` | Add a header (pre-request only) |
| `clapi.request.headers.upsert(name, value)` | Set or replace a header (pre-request only) |
| `clapi.request.query.get(name)` | Read a query parameter |

Supported mutators:
- `clapi.request.setHeader(name, value)`
- `clapi.request.setQueryParam(name, value)`
- `clapi.request.setMethod(method)`
- `clapi.request.setUrl(url)`
- `clapi.request.setBody(content)`

Mutation semantics:
- Pre-request scripts can mutate the outgoing request.
- Post-request scripts can read `clapi.request.*` values from the executed request context.

## `clapi.execution`

| Property / Method | Description |
|---|---|
| `clapi.execution.setNextRequest(nameOrIdOrRef)` | Queue another saved request to run after the current request completes. Accepts request name, request id, or requestRef. |
| `clapi.execution.setNextRequest(null)` | Stop script-driven chaining. |
| `clapi.execution.skipRequest()` | Skip sending the current request when called from pre-request script. |

Postman compatibility notes:
- `pm.execution.setNextRequest(...)` and `postman.setNextRequest(...)` are supported via import conversion and runtime aliases.
- If multiple requests share the same name, resolution prefers the current collection; otherwise the target is treated as ambiguous and ignored.

## Variable scopes

| Scope | Get | Set | Unset | Lifecycle |
|---|---|---|---|---|
| Environment | `clapi.environment.get(key)` | `clapi.environment.set(key, val)` | `clapi.environment.unset(key)` | Environment-scoped; persisted with workspace/environment state |
| Globals | `clapi.globals.get(key)` | `clapi.globals.set(key, val)` | `clapi.globals.unset(key)` | Workspace-wide; persisted with workspace state |
| Collection | `clapi.collectionVariables.get(key)` | `clapi.collectionVariables.set(key, val)` | `clapi.collectionVariables.unset(key)` | Session-scoped runtime map; available across requests during current app session |
| Request-local merged view | `clapi.variables.get(key)` | `clapi.variables.set(key, val)` | `clapi.variables.unset(key)` | Request-scoped overrides (pre-request → test of same request), not persisted |

Backward-compat aliases: `env`, `global`, `collection`, `vars`.

Resolution order for `clapi.variables.get(key)`:
1. request-local values set via `clapi.variables.set`
2. environment values
3. collection variables
4. globals

Undefined-safe patterns:

```javascript
const val = clapi.globals.get("optionalKey")
clapi.environment.set("safeOptional", val == null ? "" : String(val))

clapi.test("missing keys are safe", function () {
	clapi.expect(clapi.globals.get("missingGlobal")).to.be.undefined
	clapi.expect(clapi.collectionVariables.get("missingCollection")).to.be.undefined
	clapi.expect(clapi.variables.get("missingRequestVar")).to.be.undefined
})
```

## Logging

- `clapi.console.log(arg1, arg2, ...)`
- `console.log(arg1, arg2, ...)` (global alias)

## Async behavior

- Scripts run in a synchronous request pipeline.
- Promise APIs may exist depending on runtime, but asynchronous callbacks are not awaited before request dispatch.
- Use pre-request scripts for synchronous preparation only.

## Production recommendations

- Keep pre-request logic deterministic and idempotent.
- Avoid relying on async timing (`setTimeout`, delayed Promise callbacks).
- Guard optional values with null/undefined checks before assertions.
- Prefer explicit named tests (`clapi.test("name", fn)`) for clear UI reporting.

