---
title: MCP Activity Log & Protocol Diagnostics
description: Inspecting raw JSON-RPC traffic, notifications, and errors in Clapi.
---

# MCP Activity Log & Protocol Diagnostics

<p class="badges">
  <span class="badge badge-protocol">MCP</span>
</p>

## Overview

When testing or building an MCP server, diagnosing unexpected JSON-RPC serialization issues or protocol handshake failures requires deep protocol visibility. Clapi isolates all session traffic in the **Activity Log** tab.

## Log Categorization

Every JSON-RPC frame exchanged over the transport is recorded with color-coded categories:

| Kind | Badge | Description |
|---|---|---|
| **Sent** | `SENT` (Blue) | Outgoing client requests (e.g. `initialize`, `tools/call`). |
| **Received** | `RCVD` (Green) | Incoming server responses with `result` or data. |
| **Notification** | `NOTIF` (Purple) | Server-initiated notifications (e.g. `notifications/resources/updated`, `notifications/message`). |
| **State** | `STATE` (Gray) | Connection transitions (`CONNECTING`, `CONNECTED`, `DISCONNECTED`). |
| **Error** | `ERROR` (Red) | Protocol errors with code (e.g. `-32601 Method not found`) and stack traces. |
| **OAuth** | `OAUTH` (Yellow) | OAuth 2.1 token negotiation events. |

## Protocol Inspection

Click any entry in the Activity Log to inspect the raw JSON-RPC object:
```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "method": "tools/call",
  "params": {
    "name": "read_file",
    "arguments": {
      "path": "/etc/hosts"
    }
  }
}
```
You can search the activity log or copy any payload with a single click.
