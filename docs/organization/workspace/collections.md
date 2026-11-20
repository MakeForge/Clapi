---
title: Multiple Collections & OpenCollection
description: Maintaining and organizing multiple API collections side by side using the OpenCollection standard in Clapi.
---

# Multiple Collections & OpenCollection

<p class="badges">
  <span class="badge badge-protocol">Workspaces</span>
</p>

## Overview

Clapi supports maintaining multiple independent API collections side by side in a unified workspace. Under the hood, Clapi uses the open-standard [OpenCollection](https://opencollection.com) format, ensuring your API definitions are never trapped inside a proprietary database or cloud service.

## Managing Collections in the Sidebar

In the left sidebar, the **Collections** section allows managing multiple distinct collections:
- **Add Collection**: Click the `+` button on the Collections header to create a new collection document.
- **Expand / Collapse**: Click the chevron beside any collection to expand or collapse its internal folders and requests.
- **Inline Renaming**: Double-click any collection's name in the sidebar to rename it inline. Press Enter to commit or Escape to cancel.
- **Context Menu**: Right-click any collection to:
  - **Add Folder**: Create an organizational sub-folder.
  - **Add Request**: Create an HTTP, GraphQL, WebSocket, gRPC, RabbitMQ, or MCP request.
  - **Duplicate Collection**: Create a clone of the collection.
  - **Export Collection**: Save the collection to disk as an OpenCollection file.
  - **Delete Collection**: Remove the collection from the workspace.

## Collection Settings Editor

Clicking a collection row in the sidebar opens its dedicated **Collection Settings** editor:

1. **Info**:
   - **Name**: Collection title.
   - **Summary**: Markdown description of the collection's purpose and usage.
   - **Version**: Semantic version string (e.g. `1.0.0`).
2. **Variables**: Collection-scoped key-value variables cascaded to all child requests. These variables (along with environment variables and secrets) can be autocompleted in requests via `Ctrl + Space` (see [Keyboard Shortcuts](/reference/shortcuts)).
3. **Headers**: Default headers applied automatically to all descendant requests.
4. **Auth**: Inheritable authentication credentials (Bearer, Basic, API Key, OAuth2).
5. **Pre-request**: Pre-request JavaScript executed before any request in the collection runs.
6. **Tests**: Post-response assertion script executed after any request in the collection finishes.
