---
title: URL Parameters & Query Strings
description: How to configure and manage query parameters and path variables in Clapi.
---

# URL Parameters & Query Strings

<p class="badges">
  <span class="badge badge-protocol">HTTP</span>
</p>

## Overview

Clapi features a bidirectional query parameter editor. Any changes made to query parameters in the address bar automatically reflect in the **Params** key-value table, and any changes in the table immediately re-encode into the URL string.

---

## Query Parameters Table

The **Params** tab allows toggling, editing, and adding query parameters:

| Column | Description |
|---|---|
| **Enabled Checkbox** | Temporarily disable a query parameter without deleting its key or value. Disabled parameters are excluded from the wire request. |
| **Key** | The parameter name (e.g. `limit`, `sort`, `filter`). |
| **Value** | The parameter value. Supports dynamic interpolation like <code v-pre>{{pageSize}}</code> or <code v-pre>{{$timestamp}}</code>. |
| **Description** | Optional human-readable documentation note for the parameter. |

::: tip Inserting Variables & Secrets
Press **`Ctrl + Space`** inside any parameter key or value cell to open the autocomplete popup and quickly insert variables and secrets. See the [Keyboard Shortcuts](/reference/shortcuts) page for details.
:::

---

## Bidirectional Synchronization

When you type:
```text
https://api.example.com/items?page=1&status=active
```
The table instantly shows:
- Row 1: `page` = `1`
- Row 2: `status` = `active`

Similarly, adding a row `sort` = `desc` updates the URL to:
```text
https://api.example.com/items?page=1&status=active&sort=desc
```

---

## Path Variables (`:param`)

Clapi supports URL path variables using the colon syntax:
```text
https://api.example.com/organizations/:orgId/users/:userId
```

When path variables are detected in the URL, Clapi automatically creates corresponding rows in the **Params** table under a dedicated **Path Variables** section, allowing you to supply values or variables (<code v-pre>{{currentOrg}}</code>).
