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

::: tip Variable & Secret Shortcuts
Press **`Ctrl + Space`** inside any variable-aware input field (URL address bar, query params, headers, request body, or authentication inputs) to open the autocomplete popup and quickly insert environment variables, external secrets, and dynamic generators. See the [Keyboard Shortcuts](/settings/shortcuts) page for all shortcuts.
:::

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

## Dynamic Variables

Clapi also supports variables with dynamic values, see the dedicated [Dynamic Variables](./dynamic-variables) subpage.
