---
title: GraphQL Authentication & Headers
description: Configuring authorization headers, cookies, and tokens for GraphQL endpoints in Clapi.
---

# GraphQL Authentication & Headers

<p class="badges">
  <span class="badge badge-protocol">GraphQL & Security</span>
</p>

## Overview

GraphQL requests are typically transmitted over HTTP POST with JSON payloads. Clapi lets you configure custom headers and authentication mechanisms that apply to both manual queries and automatic schema introspection requests.

---

## Setting Headers

Under the **Headers** tab:
- Add custom headers such as `X-Tenant-ID`, `Apollo-Require-Preflight: true`, or `X-Hasura-Role: admin`.
- Inherited headers from parent folders and collections are automatically included.

---

## Authentication Schemes

In the **Auth** tab:
- **Bearer Token**: Injects `Authorization: Bearer <token>`.
- **API Key**: Injects `X-API-Key` or custom key header.
- **Inherit from Parent**: Inherits folder or collection auth credentials.

::: tip Introspection Auth
When you click **Introspect Schema**, Clapi reuses the exact authentication headers configured for the request, ensuring authenticated GraphQL endpoints can be introspected smoothly.
:::
