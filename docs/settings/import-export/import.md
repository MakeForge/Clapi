---
title: Importing Specifications
description: Importing OpenAPI v3+, OpenCollection, Postman, and Insomnia files into Clapi.
---

# Importing Specifications

<p class="badges">
  <span class="badge badge-protocol">Import / Export</span>
</p>

## Overview

Clapi provides a streamlined import dialog to bring existing API specifications and collections from other tools into your workspace without manual data re-entry.

## Supported Import Formats

| Format | Extensions | Capabilities & Notes |
|---|---|---|
| **OpenAPI v3.0 / v3.1** | `.json`, `.yaml`, `.yml` | Full endpoint extraction, parameter mapping, JSON schemas, server variables, and tag-based folder grouping. *(Note: Swagger 2.0 specs are rejected with an upgrade notification).* |
| **OpenCollection** | `.yaml`, `.yml`, `.json` | Native lossless format preserving nested folders, all protocols, scripts, and variables. |
| **Postman Collections** | `.json` (v2.1) | Imports requests, nested folders, pre-request scripts, test assertions, and environment variables. |
| **Insomnia Workspaces** | `.json` (v4/v5) | Imports workspace trees, environments, and HTTP/GraphQL requests. |

## Import Workflow

1. Click the **Import** button in the sidebar (or press `⌘ + O` / `Ctrl + O`).
2. Select your input method:
   - **File Import**: Drag and drop `.json` or `.yaml` files into the dropzone or click **Choose Files**.
   - **Clipboard Import**: Click **Scan Clipboard** to detect copied raw specification text or cURL commands.
3. Review the parsed endpoints in the summary preview.
4. Click **Import into Workspace** to generate the collection and folders.
