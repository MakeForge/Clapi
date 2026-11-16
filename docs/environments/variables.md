---
title: Variable Scopes, Syntax & Dynamic Variables
description: Using variables, scope resolution hierarchy, and dynamic system generators in Clapi.
---

# Variable Scopes, Syntax & Dynamic Variables

<p class="badges">
  <span class="badge badge-protocol">Automation</span>
</p>

## Overview

Variables allow parameterizing URLs, headers, authentication fields, request bodies, and scripts. Clapi supports a multi-tier resolution hierarchy and built-in dynamic generators.

---

## Variable Syntax

Reference any variable using double curly braces:
```text
https://{{baseUrl}}/v1/users/{{userId}}
```
Variables can be placed in:
- URL address bars and path segments
- Query parameter keys and values
- Request headers
- Request bodies (JSON, GraphQL, XML, URL-encoded)
- Basic, Bearer, and API Key authentication fields

---

## Variable Resolution Hierarchy

When a variable <code v-pre><code v-pre>{{myVar}}</code></code> is evaluated, Clapi resolves it in order from narrowest to broadest scope:

```
[Request Local] ──> [Folder Scope] ──> [Collection Scope] ──> [Active Environment] ──> [Global Scope]
```

1. **Request Local**: Set dynamically during the active request execution via `clapi.variables.set(...)`.
2. **Folder Scope**: Defined in the parent folder's settings.
3. **Collection Scope**: Defined on the collection root settings.
4. **Active Environment**: Variables belonging to the currently selected environment (e.g. `Staging`).
5. **Global Scope**: Universal variables available across all collections and environments.

---

## Dynamic System Variables

Clapi includes built-in dynamic variables that generate fresh values on every request execution:

| Dynamic Variable | Description | Example Output |
|---|---|---|
| <code v-pre>{{$guid}}</code> | Generates a random v4 UUID | `c9b2f4a1-7e82-4f9a-b892-1bfa3c678231` |
| <code v-pre>{{$timestamp}}</code> | Current UNIX timestamp in seconds | `1731628100` |
| <code v-pre>{{$timestampMs}}</code> | Current UNIX timestamp in milliseconds | `1731628100123` |
| <code v-pre>{{$isoTimestamp}}</code> | Current ISO 8601 UTC timestamp | `2026-11-14T23:45:00.000Z` |
| <code v-pre>{{$randomInt}}</code> | Random integer between 0 and 1000 | `482` |
| <code v-pre>{{$randomAlphaNumeric}}</code>| Random 8-character alphanumeric string | `k9J2bX7w` |
