---
title: Request Headers & Cascaded Inheritance
description: Managing HTTP request headers, auto-completion, and folder inheritance in Clapi.
---

# Request Headers & Cascaded Inheritance

<p class="badges">
  <span class="badge badge-protocol">HTTP</span>
</p>

## Overview

HTTP headers allow sending metadata alongside requests (e.g. `Content-Type`, `Accept`, `X-Tenant-ID`). Clapi provides a powerful header editor with automatic header completion, case-insensitive collision handling, and **cascaded inheritance** from parent folders and collections.

---

## Inherited Headers

To avoid duplicating common headers across dozens of requests, Clapi supports inheriting default headers from ancestor folders and collections:

```
[Collection Defaults]
      │
      ▼
[Root Folder Defaults]
      │
      ▼
[Subfolder Defaults]
      │
      ▼
[Leaf Request Headers]
```

### Visual Identification
- **`[Inherited]` Badge**: Rows inherited from an ancestor folder display an `Inherited` chip in the `Type` column.
- **Read-Only Lock**: Inherited rows are locked to prevent accidental modification at the leaf level. A tooltip displays the source folder where the header was defined.
- **Overriding Inherited Headers**: To override an inherited header, simply add a row with the exact same key name in your request. The request's explicit header will take precedence over the ancestor default.

---

## Standard Headers Auto-Complete

Clapi provides built-in auto-completion for common HTTP request headers and MIME types:
- `Accept`: `application/json`, `application/xml`, `text/html`
- `Content-Type`: `application/json`, `multipart/form-data`, `application/x-www-form-urlencoded`
- `Authorization`: `Bearer {{token}}`, `Basic {{credentials}}`
- `User-Agent`: Customizable per collection or globally

::: tip Inserting Variables & Secrets
Press **`Ctrl + Space`** inside any header key or value input to trigger autocomplete for environment variables and external secrets. See the [Keyboard Shortcuts](/reference/shortcuts) page for details.
:::
