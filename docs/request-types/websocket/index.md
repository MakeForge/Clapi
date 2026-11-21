---
title: WebSocket Requests
description: Complete guide to real-time, bidirectional WebSocket connections, message streaming, and logging in Clapi.
---

# WebSocket Requests

<p class="badges">
  <span class="badge badge-protocol">Protocol: WebSocket (RFC 6455)</span>
</p>

## Overview

<ThemedScreenshot
  light="/generated/screenshots/MainScreenPreviewsKt.MainScreenWebSocketPreviewLight.W1920dp_H1080dp.png"
  dark="/generated/screenshots/MainScreenPreviewsKt.MainScreenWebSocketPreviewDark.W1920dp_H1080dp.png"
  alt="Clapi WebSocket Client"
/>

Clapi includes a full-duplex, persistent **WebSocket client** (`RequestKind.WEBSOCKET`). You can establish connections to `ws://` and `wss://` endpoints, negotiate custom handshake headers, stream text or binary frames in real time, and monitor an activity log of incoming and outgoing frames.

---

## Quick Start

1. Open a new request tab and set the protocol to **WebSocket** (`RequestKind.WEBSOCKET`).
2. Enter the WebSocket endpoint URL:
   ```text
   wss://echo.websocket.org
   ```
3. Click the green **Connect** button in the URL bar.
4. The status pill transitions to **Connected** (in green).
5. In the message composer below, type:
   ```json
   { "event": "ping", "timestamp": 123456789 }
   ```
6. Click **Send**.
7. Observe the outgoing message (with an up arrow `↑`) and the echoed incoming response (with a down arrow `↓`) in the live message log.
8. Click **Disconnect** when finished.

---

## Detailed Feature Subpages

- [Connection & Handshake](./connection.md): Handshake query params, headers, subprotocols (`Sec-WebSocket-Protocol`), and SSL/TLS verification.
- [Message Composer](./composer.md): Composing text, JSON, and binary payloads with variable interpolation.
- [Live Stream Log & Filter](./message-log.md): Filtering messages by direction (Sent vs. Received), search by payload content, byte sizes, and timestamps.
- [Lifecycle & Heartbeats](./lifecycle.md): Automatic Ping/Pong handling, disconnect detection, and reconnection policies.
