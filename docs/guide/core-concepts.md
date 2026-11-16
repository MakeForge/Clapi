---
title: Core Concepts & UI Anatomy
description: Understand Clapi's workspace structure, request editor anatomy, and key-value editors.
---

# Core Concepts & UI Anatomy

<p class="badges">
  <span class="badge badge-protocol">Architecture</span>
</p>

## Overview

Clapi's interface is designed specifically for desktop ergonomics, minimal visual noise, and high productivity. This guide breaks down the four primary zones of the application window.

```
+------------------------------------------------------------------------------------+
|  Top Toolbar: Environment Selector | Active Collection | Theme Toggle | Settings   |
+-------------------+----------------------------------------------------------------+
|                   |  Editor Tab Bar: [Tab 1] [Tab 2] [+]                           |
|  Sidebar:         +----------------------------------------------------------------+
|  - Collections    |  Request Bar: [Method] [URL Bar] [Send] [Save] [cURL]          |
|    - Folders      +-------------------------------+--------------------------------+
|    - Requests     |  Request Configuration:       |  Response Viewer:              |
|  - Environments   |  [Params] [Headers] [Body]    |  - Pretty / Raw / Preview      |
|  - History        |  [Auth] [Pre-req] [Tests]     |  - Headers / Cookies / Metrics |
|                   +-------------------------------+--------------------------------+
|                   |  Bottom Status Console: Network Log / Activity                 |
+-------------------+----------------------------------------------------------------+
```

---

## 1. Top Toolbar

- **Environment Picker**: Quickly toggle between your active variable sets (e.g. `Development`, `Staging`, `Production`, or `No Environment`).
- **Git Sync Indicator**: Shows current repository branch and dirty status (`clean`, `uncommitted`, `ahead`, `behind`).
- **Theme Toggle**: Switch between Material 3 Light and Dark palettes.
- **Settings**: Global preferences for proxies, font sizing, SSL verification, and certificate authorities.

---

## 2. The Sidebar

The sidebar organizes your API assets into three collapsible sections:
- **Collections**: Your API tree containing root collections, nested folders, and request items.
- **Environments**: Global, environment, and secret variable stores.
- **History**: Chronological log of recent dispatched requests with one-click re-opening.

---

## 3. The Request Editor

The center-left region allows composing and executing requests across five core protocols:
- **Protocol & Method Dropdown**: Choose from HTTP verbs (`GET`, `POST`, `PUT`, `DELETE`, etc.), `GRAPHQL`, `WEBSOCKET`, `GRPC`, or `MCP`.
- **URL Bar**: Enter the destination URL or stdio command. Supports dynamic interpolation: `https://{{host}}:{{port}}/api/users/{{userId}}`.
- **Sub-Tabs**:
  - **Params**: Query string parameters with automatic bidirectional URL sync.
  - **Headers**: Custom and standard HTTP/transport headers with folder inheritance tags.
  - **Body**: Format selector (JSON5, Multipart, URL-encoded, Raw, Binary, GraphQL).
  - **Auth**: Inheritable authentication (Bearer, Basic, API Key, OAuth 2.0 PKCE, AWS SigV4).
  - **Pre-request**: JavaScript script run before dispatch.
  - **Tests**: Assertions run against the response payload.

---

## 4. The Response Viewer

The right pane displays returned data and timing diagnostics:
- **Status, Latency & Size**: Instant high-level summary at the top of the viewer.
- **Format Toggle**: Switch between **Pretty** (syntax-colored JSON/XML), **Raw** (unformatted stream), and **Preview** (rendered HTML/images).
- **Headers & Cookies**: Complete response headers with one-click copy.
- **Metrics Breakdown**: Timing breakdown of DNS lookup, TCP connect, TLS handshake, TTFB (time to first byte), and download duration.
