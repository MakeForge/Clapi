---
title: WebSocket Live Message Log & Stream Filtering
description: Inspecting, searching, and filtering bidirectional WebSocket frames in Clapi.
---

# WebSocket Live Message Log & Stream Filtering

<p class="badges">
  <span class="badge badge-protocol">WebSocket</span>
</p>

## Overview

The WebSocket Response Viewer maintains a chronological, real-time message stream of every frame exchanged during the active session.

---

## Message Log Features

- **Direction Badges**:
  - `↑ SENT` (Blue): Frames dispatched from Clapi to the server.
  - `↓ RECEIVED` (Green): Frames sent from the server to Clapi.
- **Timestamps**: Precise millisecond timestamp for each frame.
- **Byte Size Indicator**: Payload length displayed beside each message.
- **Payload Inspection**: Click any row to expand its payload in the Pretty JSON viewer with one-click copy.

---

## Filtering & Search

- **Direction Filter**: Toggle between **All**, **Sent**, or **Received** messages.
- **Search Query**: Real-time text filter highlighting matches in incoming frame streams.
- **Clear Log**: Click the trash icon to clear the session log history without disconnecting.
