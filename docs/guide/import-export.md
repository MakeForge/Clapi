---
title: Importing & Migrating from Postman & Insomnia
description: Step-by-step guide to migrating API collections, environments, authentication configs, and scripts from Postman, Insomnia v5, OpenAPI 3.0+, and OpenCollection into Clapi.
---

# Importing & Migrating Collections

<p class="badges">
  <span class="badge badge-protocol">Migration Guide</span>
  <span class="badge badge-version">OpenAPI · OpenCollection · Postman · Insomnia v5</span>
</p>

## Overview

Clapi makes it effortless to migrate your existing API workspaces without vendor lock-in. From the unified **Import** dialog, Clapi automatically detects and imports:

- **OpenAPI 3.0+ (`YAML` / `JSON`)** — API specifications with operation selection.
- **OpenCollection (`YAML` / `JSON`)** — Clapi's native Git-friendly open standard.
- **Postman (`v2.0` / `v2.1` `JSON`)** — Collections, nested folders, auth configs, environments, and `pm.*` scripts.
- **Insomnia (`v5` `YAML` / `JSON`)** — Request collections (`collection.insomnia.rest/5.0`), API design specs (`spec.insomnia.rest/5.0`), standalone environments (`environment.insomnia.rest/5.0`), and `insomnia.*` scripts.

---

## Migrating from Insomnia (v5)

### Step 1: Export from Insomnia

1. Open **Insomnia** and navigate to your Collection, Design Document, or Environment.
2. Click the document menu (**...**) in the top-left sidebar or workspace header and choose **Export**.
3. Select **Insomnia v5** format (**YAML** or **JSON**) and save the file to your disk:
   - Collections export with `type: collection.insomnia.rest/5.0`.
   - Design Documents export with `type: spec.insomnia.rest/5.0`.
   - Standalone Environments export with `type: environment.insomnia.rest/5.0`.

### Step 2: Import into Clapi

1. In Clapi, click the **Import** button in the top **Collections** sidebar header.
2. Confirm that **Insomnia** is listed among the supported format badges (`OpenAPI 3.0+`, `OpenCollection`, `Postman`, `Insomnia`) beneath the file dropzone.
3. Click **Select File...** and choose your exported `.yaml`, `.yml`, or `.json` file (or paste its contents directly).
4. Clapi scans the file, displays the **Insomnia v5** format badge in the preview header, and lists all discovered requests (HTTP, GraphQL, gRPC, WebSocket).
5. Select the endpoints you want to import (all are selected by default) and click **Import**.

### What Gets Imported from Insomnia v5?

| Insomnia v5 Feature | Clapi Mapping |
| :--- | :--- |
| **Nested Request Groups / Folders** | Preserved as hierarchical `FolderItem` trees |
| **HTTP & GraphQL Requests** | Method, URL, query parameters, path parameters (`:id` &rarr; `{{id}}`), headers, JSON/XML/raw/form-urlencoded/multipart bodies |
| **gRPC Requests** | Server URL, `protoMethodName`, metadata headers, JSON message payload, and Server Reflection toggle |
| **WebSocket Requests** | Endpoint URL, handshake headers, and query parameters |
| **Authentication** | Basic, Bearer, API Key (Header/Query), OAuth 2.0, AWS IAM Signature v4, Digest, and Single Token |
| **Environments & Sub-Environments** | Base `environments.data` and all `subEnvironments` are imported as Clapi Environments; `{{ _.varName }}` templates are normalized to `{{varName}}` |
| **Pre-Request & After-Response Scripts** | `scripts.preRequest` and `scripts.afterResponse` are automatically translated from `insomnia.*` to `clapi.*` |
| **Embedded OpenAPI Specs (`spec.contents`)** | Automatically delegated to Clapi's OpenAPI 3.0+ importer while preserving Insomnia environments |

---

## Migrating from Postman (v2.0 / v2.1)

### Step 1: Export from Postman

1. In **Postman**, right-click the collection you want to migrate and select **Export**.
2. Choose **Collection v2.1 (recommended)** or **Collection v2.0** and save the `.json` file.
3. *(Optional)* To export environments, switch to the **Environments** tab in Postman, click **...** next to an environment, and select **Export**.

### Step 2: Import into Clapi

1. In Clapi, click the **Import** button in the **Collections** sidebar header.
2. Select your exported Postman `.json` collection file.
3. Clapi imports your folder hierarchy, HTTP/GraphQL requests, authentication settings, collection variables, and automatically translates `pm.*` pre-request and test scripts into `clapi.*` scripts.
4. To import a standalone Postman environment `.json` file, open the **Environments** selector in the top toolbar and choose **Import Environment**.

---

## Automatic Script Translation (`insomnia.*` & `pm.*` &rarr; `clapi.*`)

Both Postman and Insomnia v5 scripts are automatically translated during import so your dynamic workflows and test assertions run out of the box in Clapi's isolated V8 JavaScript engine:

| Insomnia v5 API | Postman API | Translated Clapi API |
| :--- | :--- | :--- |
| `insomnia.environment.get/set/unset` | `pm.environment.get/set/unset` | `clapi.environment.get/set/unset` |
| `insomnia.baseEnvironment.get/set` | `pm.globals.get/set` | `clapi.collectionVariables.get/set` / `clapi.environment.get/set` |
| `insomnia.collectionVariables.get/set` | `pm.collectionVariables.get/set` | `clapi.collectionVariables.get/set` |
| `insomnia.variables.get/set` | `pm.variables.get/set` | `clapi.variables.get/set` |
| `insomnia.parentFolders.get/set` | — | `clapi.folder.get/set` |
| `insomnia.request.addHeader(...)` | `pm.request.headers.add(...)` | `clapi.request.addHeader(...)` |
| `insomnia.request.removeHeader(...)` | `pm.request.headers.remove(...)` | `clapi.request.removeHeader(...)` |
| `insomnia.request.url.addQueryParams(...)` | `pm.request.url.addQueryParams(...)` | `clapi.request.url.addQueryParams(...)` |
| `insomnia.test(name, fn)` | `pm.test(name, fn)` | `clapi.test(name, fn)` |
| `insomnia.expect(val)` | `pm.expect(val)` | `clapi.expect(val)` |
| `insomnia.response.code` / `.status` | `pm.response.code` / `.status` | `clapi.response.code` / `.status` |
| `insomnia.response.json()` / `.text()` | `pm.response.json()` / `.text()` | `clapi.response.json()` / `.text()` |
| `insomnia.sendRequest(...)` | `pm.sendRequest(...)` | `clapi.sendRequest(...)` |

---

## Unsupported Insomnia v5 Fields & Graceful Degradation

Clapi safely ignores vendor-specific or non-collection properties during import without failing your workspace migration:

- **Mock Server & MCP Client exports** (`mock.insomnia.rest/5.0`, `mcpClient.insomnia/5.0`): Rejected with a clear diagnostic message explaining that only collections, specs, and environments can be imported.
- **Collection Certificates & Cookie Jars** (`certificates`, `cookieJar`): Ignored during import; Clapi uses system TLS trust stores and fresh session state.
- **Kong Konnect Metadata** (`konnectRouteKey`, `konnectRouteId`, `konnectManagedHeaderNames`): Ignored.
- **Legacy Auth Schemes** (`hawk`, `asap`, `netrc`, `ntlm`): Imported with `AuthConfig.None` so the request remains editable.
