---
title: Scripting API Reference (`clapi.*`)
description: Complete reference table for clapi.test, clapi.expect, variables, request, and response objects.
---

# Scripting API Reference (`clapi.*`)

<p class="badges">
  <span class="badge badge-protocol">API Reference</span>
</p>

## Overview

All scripting capabilities in Clapi are accessible under the global `clapi` namespace (with legacy global aliases preserved).

---

## Test & Assertion API

### `clapi.test(name, fn)`
Registers a named test block. If the callback function throws an exception or assertion error, the test fails; otherwise, it passes.

```javascript
clapi.test("Verify status code", function () {
  clapi.expect(clapi.response.status).to.equal(200);
});
```

---

### Assertion Matchers (`clapi.expect`)

| Assertion | Description | Example |
|---|---|---|
| `.to.equal(value)` | Strict equality (`===`) | `clapi.expect(status).to.equal(200)` |
| `.to.eql(value)` | Deep object or array equality | `clapi.expect(body).to.eql({ success: true })` |
| `.to.include(item)` | Substring or array element containment | `clapi.expect(header).to.include("gzip")` |
| `.to.match(regex)` | Regular expression match | `clapi.expect(email).to.match(/@/)` |
| `.to.be.oneOf(array)` | Value is contained within array | `clapi.expect(code).to.be.oneOf([200, 201])` |
| `.to.be.above(n)` | Greater than (`>`) | `clapi.expect(count).to.be.above(0)` |
| `.to.be.below(n)` | Less than (`<`) | `clapi.expect(latency).to.be.below(500)` |
| `.to.be.at.least(n)` | Greater than or equal (`>=`) | `clapi.expect(page).to.be.at.least(1)` |
| `.to.be.at.most(n)` | Less than or equal (`<=`) | `clapi.expect(size).to.be.at.most(1000)` |
| `.to.exist` | Not `null` and not `undefined` | `clapi.expect(token).to.exist` |
| `.to.be.ok` | Truthy evaluation | `clapi.expect(isValid).to.be.ok` |
| `.to.be.empty` | Empty string, array, or object | `clapi.expect(errors).to.be.empty` |
| `.to.be.true` | Strictly `true` | `clapi.expect(flag).to.be.true` |
| `.to.be.false` | Strictly `false` | `clapi.expect(flag).to.be.false` |
| `.to.be.null` | Strictly `null` | `clapi.expect(error).to.be.null` |
| `.to.be.undefined` | Strictly `undefined` | `clapi.expect(field).to.be.undefined` |
| `.to.have.property(key)` | Object possesses key | `clapi.expect(user).to.have.property("id")` |
| `.to.have.lengthOf(n)` | Array or string length equals n | `clapi.expect(items).to.have.lengthOf(5)` |
| `.to.not...` | Inverts any assertion matcher | `clapi.expect(status).to.not.equal(500)` |

#### Supported Chaining Words
`.to`, `.be`, `.been`, `.is`, `.that`, `.which`, `.and`, `.has`, `.have`, `.with`, `.at`, `.of`, `.same`, `.but`, `.does`, `.still`, `.also`, `.a`, `.an`

---

## Variable Management APIs

### Environment Variables
- `clapi.environment.get(key)`: Returns string value of variable in active environment.
- `clapi.environment.set(key, value)`: Updates or creates variable in active environment.
- `clapi.environment.has(key)`: Checks existence.
- `clapi.environment.unset(key)`: Deletes variable from environment.

### Request-Scoped Variables
- `clapi.variables.get(key)`: Gets request-local variable.
- `clapi.variables.set(key, value)`: Sets temporary request-local variable.

### Global Variables
- `clapi.globals.get(key)`: Reads from global scope.
- `clapi.globals.set(key, value)`: Persists to global scope across all environments.

---

## Request & Response Objects

### `clapi.request`
- `clapi.request.url`: Target URL string.
- `clapi.request.method`: HTTP method verb (e.g. `"POST"`).
- `clapi.request.headers.get(name)`: Retrieves request header value.
- `clapi.request.headers.set(name, value)`: Sets request header value.
- `clapi.request.body`: String representation of request body.

### `clapi.response`
- `clapi.response.status`: Integer HTTP status code (e.g. `200`, `404`).
- `clapi.response.statusText`: Status reason phrase (e.g. `"OK"`, `"Not Found"`).
- `clapi.response.responseTime`: Milliseconds elapsed during network roundtrip.
- `clapi.response.headers.get(name)`: Retrieves response header value.
- `clapi.response.body`: Raw text of response payload.
- `clapi.response.json()`: Parses response body as JSON (convenience helper).
