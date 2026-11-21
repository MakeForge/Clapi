# Scripted Assertions (Scripting Reference)

> **Note:** This file documents post-request script assertions. For the full scripting guide including pre-request scripts and variable access, see [docs/scripts.md](../scripts.md).

Clapi post-request scripts are written using the `clapi` namespace (configurable in **Settings → Scripts**).

## Pattern

1. Wrap checks in `clapi.test("name", function() { ... })`.
2. Use `clapi.expect(...)` for assertions.
3. Access response data via `clapi.response.*`.
4. Persist extracted values with `clapi.environment.set(...)` when chaining requests.

## Example

```javascript
clapi.test("Status is 200", function() {
  clapi.expect(clapi.response.code).to.equal(200)
})

clapi.test("Response time is acceptable", function() {
  clapi.expect(clapi.response.responseTime).to.be.below(2000)
})

clapi.test("Body contains users", function() {
  clapi.expect(clapi.response.text()).to.include("users")
})

clapi.test("Token extracted", function() {
  clapi.expect(clapi.response.json().token).to.exist
  clapi.environment.set("token", clapi.response.json().token)
})
```

## Assertions

- `.to.equal(v)`, `.to.not.equal(v)` — strict equality
- `.to.eql(v)` — deep equality
- `.to.include(text)` — substring or array containment
- `.to.match(/regex/)` — regex match
- `.to.be.above(n)`, `.to.be.below(n)` — numeric comparison
- `.to.be.at.least(n)`, `.to.be.at.most(n)` — inclusive bounds
- `.to.be.oneOf([a, b, c])` — value in set
- `.to.exist` — not null/undefined
- `.to.be.ok` — truthy
- `.to.be.empty` — empty string/array
- `.to.be.null` — strictly null

## Notes

- Assertion results are displayed in the **Test Results** panel after request execution.
- The global (prefix-free) API (`test`, `expect`, `env.set`, …) is still supported for backward compatibility.
- Change the namespace prefix in **Settings → Scripts** — any identifier works (e.g. `api`, `rl`).

