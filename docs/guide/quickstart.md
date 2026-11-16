---
title: Quickstart in 60 Seconds
description: Get up and running with Clapi, create a collection, and execute your first API request in 60 seconds.
---

# Quickstart in 60 Seconds

<p class="badges">
  <span class="badge badge-protocol">Tutorial</span>
  <span class="badge badge-version">Beginner</span>
</p>

## Overview

In this quickstart guide, you will create a new API collection, send an HTTP request to a live endpoint, inspect the returned headers and response payload, and observe performance metrics.

---

## Step 1: Launch Clapi & Create a Collection

1. Open Clapi on your desktop.
2. In the left **Sidebar**, locate the **Collections** section.
3. Click the `+` icon on the header to create a new collection.
4. Double-click the newly created collection to name it `My API Workspace`.

---

## Step 2: Create a Request

Clapi supports multiple first-class protocol types. You can create:

- [HTTP / REST](/request-types/http/): Compose standard REST requests with query parameters, headers, and body payloads.
- [GraphQL](/request-types/graphql/): Query, mutate, and introspect GraphQL endpoints with a dedicated query & variable editor.
- [WebSocket](/request-types/websocket/): Connect to bidirectional WebSocket streams, inspect frame timelines, and send live messages.
- [gRPC](/request-types/grpc/): Load Protocol Buffers or use Server Reflection to invoke unary and streaming RPC methods.
- [Model Context Protocol (MCP)](/request-types/mcp/): Connect to MCP servers, discover AI tools, read resources, and test prompts.

### Example: Creating an HTTP Request

1. Right-click your new collection and select **Add Request** (or click the collection's `...` menu).
2. Clapi opens a new editor tab with the default `GET` method.
3. Enter the target URL into the address bar:
   ```text
   https://httpbin.org/get?user=developer&source=clapi
   ```
4. Notice how the query parameters `user` and `source` automatically populate the **Params** key-value table below the URL bar.

---

## Step 3: Send the Request & Inspect Response

1. Click the purple **Send** button on the right of the URL bar (or press `⌘ + Enter` on macOS / `Ctrl + Enter` on Windows/Linux).
2. The response appears in the right pane:
   - **Status Pill**: Shows `200 OK` in green.
   - **Timing Pill**: Displays roundtrip latency (e.g. `128 ms`).
   - **Size Pill**: Displays payload size (e.g. `1.2 KB`).
3. Click through the response tabs:
   - **Pretty**: Formatted, syntax-highlighted JSON with foldable nodes.
   - **Headers**: Complete response headers (e.g. `Content-Type`, `Date`, `Server`).
   - **Metrics**: Detailed connection breakdown (DNS, TCP, TLS, First Byte / TTFB).

---

## Step 4: Add a Test Assertion

1. In the request editor, switch to the **Tests** tab.
2. Enter the following assertion using Clapi's built-in V8 engine:
   ```javascript
   clapi.test("Status code is 200", function () {
     clapi.expect(clapi.response.status).to.equal(200);
   });

   clapi.test("Query parameters match", function () {
     const json = JSON.parse(clapi.response.body);
     clapi.expect(json.args.user).to.equal("developer");
   });
   ```
3. Click **Send** again.
4. Click the **Tests** sub-tab in the response viewer to see your test assertions pass with green checkmarks (`2 / 2 Passed`).

---

## Migrating from Postman or Insomnia?

Already have API collections in **Postman** or **Insomnia**? You can migrate them into Clapi in seconds:

1. **Export from your existing tool**:
   - **Insomnia**: Open your Collection, Design Document, or Environment menu (**...** &rarr; **Export**) and save as **Insomnia v5 (YAML or JSON)**.
   - **Postman**: Right-click your Collection (**Export** &rarr; **Collection v2.1** or **v2.0 JSON**).
2. **Import into Clapi**:
   - Click the **Import** button in the **Collections** sidebar header.
   - Select your exported `.yaml`, `.yml`, or `.json` file (supported formats `OpenAPI 3.0+`, `OpenCollection`, `Postman`, and `Insomnia` are badged right below the file picker).
   - Clapi imports your folder hierarchy, requests, environments, authentication configs, and automatically translates `insomnia.*` and `pm.*` scripts to `clapi.*`.

See the full [Importing & Migrating Guide](./import-export.md) for detailed feature mappings and script translation reference.

🎉 **Congratulations!** You've completed your first API flow in Clapi.
