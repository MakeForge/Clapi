---
title: HTTP / REST Requests
description: Complete guide to sending and inspecting HTTP / REST API requests in Clapi.
---

# HTTP / REST Requests

<p class="badges">
  <span class="badge badge-protocol">Protocol: HTTP/1.1 & HTTP/2</span>
</p>

## Overview

<ThemedScreenshot
  light="/generated/screenshots/MainScreenPreviewsKt.MainScreenHttpPreviewLight.W1600dp_H800dp.png"
  dark="/generated/screenshots/MainScreenPreviewsKt.MainScreenHttpPreviewDark.W1600dp_H800dp.png"
  alt="Clapi HTTP Request Editor"
/>

Clapi provides a comprehensive HTTP / REST client engine powered by Ktor. You can compose and dispatch requests using all standard HTTP methods, inject dynamic variables, stream large payloads, inspect headers, and analyze detailed connection latency.

## Quick Start

1. Open a new request tab (`⌘ + N` or `Ctrl + N`).
2. Select your HTTP method from the method dropdown (`GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `OPTIONS`).
3. Enter your request URL:
   ```text
   https://api.example.com/v1/users
   ```
4. If sending a `POST` or `PUT`, switch to the **Body** tab and choose **JSON**.
5. Click **Send** (`⌘ + Enter` / `Ctrl + Enter`).
6. View the parsed response payload, status code, and latency in the right pane.

## Detailed Feature Subpages

Explore the specialized subpages for deep dives into each HTTP capability:

- [URL Parameters & Query Strings](./parameters.md): Managing query params, matrix params, and URL-encoding rules.
- [Headers & Inheritance](./headers.md): Setting custom headers, overriding folder-inherited headers, and standard header presets.
- [Request Body Formats](./body.md): Working with JSON5, Multipart form-data with file uploads, `x-www-form-urlencoded`, raw text, and binary blobs.
- [Authentication Methods](./authentication.md): Configuring Basic Auth, Bearer tokens, API Keys, OAuth 2.0 with PKCE, OAuth 1.0a, and AWS Signature v4.
- [Response Viewer & Metrics](./response-viewer.md): Pretty formatters, raw views, cookies, response headers, and connection timing waterfall.
- [cURL Integration](./curl.md): Importing raw cURL commands and exporting requests as copyable cURL strings.
