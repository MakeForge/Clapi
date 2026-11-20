---
title: Request Body Formats
description: Authoring request payloads in JSON5, Multipart Form-Data, URL-Encoded, Raw Text, and Binary formats.
---

# Request Body Formats

<p class="badges">
  <span class="badge badge-protocol">HTTP</span>
</p>

## Overview

Clapi supports all standard HTTP request payload formats. You can select your desired body type from the dropdown selector on the **Body** tab.

## Supported Body Types

### 1. JSON & JSON5
- **Features**: Syntax highlighting, code folding, automatic format on paste, and real-time syntax error markers.
- **Comments & Trailing Commas**: Supports relaxed JSON5 syntax during composition (comments and trailing commas).
- **Auto-Header**: Automatically sets `Content-Type: application/json; charset=utf-8`.

```json5
{
  // User creation payload
  "username": "{{username}}",
  "email": "user@example.com",
  "roles": ["developer", "tester"],
}
```

### 2. Multipart Form-Data (`multipart/form-data`)
Ideal for file uploads or combining files with structured text metadata.

- **Text Rows**: Enter key and string value.
- **File Rows**: Toggle row type from `Text` to `File`. Click **Choose File** to browse your filesystem.
- **MIME Type Override**: Specify custom content types per part (e.g. `image/png`, `application/pdf`).
- **Auto-Header**: Sets `Content-Type: multipart/form-data; boundary=...`.

### 3. URL-Encoded (`application/x-www-form-urlencoded`)
Standard HTML form submission encoding. Key-value pairs are automatically URL-escaped (`key1=val1&key2=val2`).

### 4. Raw Text
Send unformatted text payloads with custom MIME types:
- **Plain Text** (`text/plain`)
- **XML / SOAP** (`application/xml`, `text/xml`)
- **HTML** (`text/html`)
- **YAML** (`application/yaml`, `text/yaml`)
- **CSV** (`text/csv`)

### 5. Binary
Stream arbitrary binary files directly from disk (e.g. firmware images, compiled binaries, video clips, ZIP archives). Clapi streams the file efficiently without buffering the entire payload into RAM.
