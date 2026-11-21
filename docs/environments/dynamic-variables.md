---
title: Dynamic Variables
description: Built-in system generators and user-defined JavaScript dynamic variables in Clapi.
---

# Dynamic Variables

<p class="badges">
  <span class="badge badge-protocol">Automation</span>
  <span class="badge badge-security">V8 Sandbox</span>
</p>

## Overview

Dynamic variables generate fresh values on **every request execution** instead of resolving to a static string. Clapi provides two categories of dynamic variables:

1. **Built-in Dynamic System Variables** (prefixed with `$`, such as <code v-pre>{{$timestamp}}</code> or <code v-pre>{{$guid}}</code>) that are always available out of the box without configuration.
2. **User-Defined Dynamic Variables** powered by Clapi's sandboxed V8 JavaScript engine, allowing you to define custom per-request generators with optional primitive parameters (for example, <code v-pre>{{secondsFromNow(10)}}</code>) and read-only access to the outgoing request context (`context.request`).

> [!TIP]
> Press **`Ctrl+Space`** inside any variable-aware input field (URL bar, headers, query parameters, auth fields, or body editor) to open the variable autocomplete dropdown and insert built-in or user-defined dynamic variables with their parameter signatures. See [Keyboard Shortcuts](/reference/shortcuts) for all editor shortcuts.

---

## Built-in Dynamic System Variables

Built-in dynamic variables start with a `$` prefix and require no setup. Every occurrence of a built-in token is evaluated fresh when the request is sent.

| Dynamic Variable | Syntax | Description | Example Output |
|---|---|---|---|
| **GUID / UUID v4** | <code v-pre>{{$guid}}</code> or <code v-pre>{{$uuid}}</code> | Generates a random RFC 4122 v4 UUID | `c9b2f4a1-7e82-4f9a-b892-1bfa3c678231` |
| **UNIX Timestamp (s)** | <code v-pre>{{$timestamp}}</code> | Current UTC UNIX epoch timestamp in seconds | `1731628100` |
| **UNIX Timestamp (ms)** | <code v-pre>{{$timestampMs}}</code> | Current UTC UNIX epoch timestamp in milliseconds | `1731628100123` |
| **ISO 8601 Timestamp** | <code v-pre>{{$isoTimestamp}}</code> | Current UTC timestamp in ISO 8601 format | `2026-11-15T22:45:00.000Z` |
| **Random Integer** | <code v-pre>{{$randomInt}}</code> | Random integer between `0` and `1000` (inclusive) | `482` |
| **Random Integer (Range)** | <code v-pre>{{$randomInt(min, max)}}</code> | Random integer between `min` and `max` (inclusive) | `42` (for <code v-pre>{{$randomInt(10, 99)}}</code>) |
| **Random Alphanumeric** | <code v-pre>{{$randomAlphaNumeric}}</code> | Random 8-character alphanumeric string | `k9J2bX7w` |

### Built-in Usage Example

```http
POST https://{{baseUrl}}/v1/orders HTTP/1.1
X-Request-Id: {{$guid}}
X-Client-Timestamp: {{$isoTimestamp}}
Content-Type: application/json

{
  "orderId": "ord-{{$randomInt(10000, 99999)}}",
  "createdAt": {{$timestamp}}
}
```

---

## User-Defined Dynamic Variables

When built-in generators are not enough—such as computing a future expiration timestamp, formatting a composite correlation header, or deriving a signature from the request method and URL—you can define **User-Defined Dynamic Variables** in any **Environment**, **Collection**, or **Folder** variable table.

### 1. Creating a Dynamic Variable

1. Open the **Environment Editor** (or the **Variables** tab of a Collection or Folder).
2. Add a variable row and enter its name (for example, `secondsFromNow`).
3. Click the **Type** badge in the row to cycle from `Normal` → `Secret` → **`Dynamic`**.
4. When `Dynamic` is selected, the static value field is replaced by an **Edit Script** button.
5. Click **Edit Script** to open the two-pane **Dynamic Variable Script Editor**.

### 2. Script Editor & Interactive Test Runner

The **Dynamic Variable Script Editor** is split into two panes:

- **Left Pane — Parameters & JavaScript Editor**:
  - **Parameters**: Add optional named parameters with default values (for example, parameter `seconds` with default value `10`). The header live-updates the invocation preview (e.g., <code v-pre>{{secondsFromNow(seconds)}}</code>).
  - **JavaScript Code Editor**: Write standard JavaScript (`return ...;` or a bare expression) executed in an isolated V8 sandbox.
- **Right Pane — Interactive Test Execution**:
  - Enter test values for each declared parameter.
  - Expand **Mock Request Context (`context.request`)** to customize the simulated HTTP method, URL, and headers.
  - Click **Run Test** to immediately execute the script in V8 and inspect:
    - **Status & Execution Time** (e.g., `Success · 4 ms`)
    - **Return Value** (with one-click copy)
    - **Console Output** (`console.log`, `console.warn`, `console.error`)
    - **Script Error Details** if the script throws a syntax/runtime error or exceeds the 2,000 ms timeout.

---

## Defining & Calling Parameterized Dynamic Variables

You can declare zero or more primitive parameters on any user-defined dynamic variable. Inside your JavaScript code, parameter values are available both:
- By name on the `args` object: `args.<paramName>` (and directly as a local identifier `<paramName>`)
- By position: `args[0]`, `args[1]`, etc.

### Example: `secondsFromNow(seconds)`

Declare a parameter named `seconds` with default value `0`, and use the following script:

```javascript
const offsetSeconds = Number(args.seconds ?? 0);
return new Date(Date.now() + offsetSeconds * 1000).toISOString();
```

You can then invoke the variable in any request template:

| Template Expression | Behavior |
|---|---|
| <code v-pre>{{secondsFromNow(10)}}</code> | Passes `10` as `args.seconds`, returning an ISO timestamp 10 seconds in the future |
| <code v-pre>{{secondsFromNow(-60)}}</code> | Passes `-60` as `args.seconds`, returning an ISO timestamp 1 minute in the past |
| <code v-pre>{{secondsFromNow}}</code> or <code v-pre>{{secondsFromNow()}}</code> | Uses the declared default value (`0`) |

### Supported Argument Literals

Template calls support comma-separated primitive literals:
- **Numbers**: <code v-pre>{{myVar(10, -3.5)}}</code>
- **Double- or Single-Quoted Strings**: <code v-pre>{{formatTag("ORD", 'prod')}}</code> (supports `\"`, `\'`, and `\\` escapes)
- **Booleans & `null`**: <code v-pre>{{myVar(true, false, null)}}</code>

---

## Accessing Outgoing Request Context (`context.request`)

Every user-defined dynamic variable script receives a **read-only** `context.request` object containing metadata about the request currently being executed:

| Property | Type | Description |
|---|---|---|
| `context.request.url` | `string` | The outgoing request URL (with static variables resolved) |
| `context.request.method` | `string` | The HTTP/protocol method (e.g., `"GET"`, `"POST"`, `"GRPC"`, `"WS"`) |
| `context.request.headers` | `Record<string, string>` | Read-only map of enabled request headers |
| `context.request.name` | `string` | Display name of the request |

> [!NOTE]
> `context.request` is deeply frozen via a read-only `Proxy` to guarantee side-effect-free variable evaluation. Attempting to mutate `context.request` throws a `TypeError`.

### Example: Request-Aware Signature Header

```javascript
// Compute a trace tag combining the HTTP method and target path
const method = context.request.method;
const url = context.request.url;
console.log("Signing request:", method, url);
return btoa(method + ":" + url + ":" + Date.now());
```

---

## Scope Precedence & Import / Export

### Resolution Precedence

User-defined dynamic variables participate in the standard Clapi variable hierarchy:

```
[Active Environment] ──> [Folder (Leaf → Root)] ──> [Collection] ──> [Global]
```

If an active environment defines a dynamic variable with the same name as a collection static variable, the environment's dynamic variable takes precedence (and vice versa).

### OpenCollection JSON & YAML Format

Dynamic variables are persisted natively in OpenCollection JSON and YAML files with `"type": "dynamic"`, making them first-class citizens in [Git Sync Collaboration](/collaboration/git-sync-overview) and workspace exports:

```yaml
environments:
  - name: Production
    variables:
      - name: secondsFromNow
        type: dynamic
        description: ISO timestamp offset in seconds from current time
        script: |
          const offsetSeconds = Number(args.seconds ?? 0);
          return new Date(Date.now() + offsetSeconds * 1000).toISOString();
        parameters:
          - name: seconds
            defaultValue: "10"
            description: Offset in seconds
```

---

## Error Handling & Execution Guardrails

- **Per-Execution Timeout**: Each dynamic variable script is guarded by a **2,000 ms** V8 execution timeout. Infinite loops (`while (true) {}`) are terminated automatically.
- **Fail-Fast Request Halting**: If a dynamic variable script throws an unhandled exception, times out, or is called with malformed template syntax (such as unclosed parentheses), Clapi halts the request before network transmission and reports the variable name and error message in the request error banner and console log.
