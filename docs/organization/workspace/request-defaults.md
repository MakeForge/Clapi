---
title: Folders & Request Defaults
description: Organizing requests into nested folders and cascading default headers, auth, and scripts down the tree.
---

# Folders & Request Defaults

<p class="badges">
  <span class="badge badge-protocol">Workspaces</span>
</p>

## Overview

As API collections grow, organizing requests into nested folders is essential. In Clapi, folders are not merely visual containers—they act as **request defaults inheritance providers**.

## Folder Settings Editor

Clicking any folder in the sidebar opens a dedicated **Folder Settings** tab:
1. **Params**: Query parameters automatically cascaded to child requests.
2. **Headers**: Default HTTP headers cascaded to child requests.
3. **Auth**: Inheritable authentication credentials (`Basic`, `Bearer`, `API Key`, `OAuth2`).
4. **Pre-request**: Script executed prior to dispatching any descendant request.
5. **Tests**: Assertions executed after any descendant request receives a response.

## Inheritance Cascade Rules

In multi-level hierarchies:
```
Collection Defaults ──> Root Folder ──> Subfolder ──> Leaf Request
```

1. **Root-to-Leaf Precedence**: Distant ancestor defaults load first, closer folder defaults override identical keys, and the leaf request's own explicit configuration takes highest precedence.
2. **Visual `[Inherited]` Badges**: Inherited headers and query parameters display an `Inherited` chip in the `Type` column.
3. **Read-Only Locks**: Inherited entries are locked in request views to prevent accidental tampering. Hovering displays a tooltip indicating the originating ancestor folder.
4. **Overriding Inherited Values**: To override an inherited key, add an entry with the identical key name in the request. The request's explicit value will be sent over the wire.
