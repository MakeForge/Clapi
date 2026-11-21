---
title: WebSocket Lifecycle & Heartbeats
description: Understanding connection lifecycle, Ping/Pong heartbeats, and reconnects in Clapi.
---

# WebSocket Lifecycle & Heartbeats

<p class="badges">
  <span class="badge badge-protocol">WebSocket</span>
</p>

## Overview

Persistent socket connections require active connection health monitoring. Clapi manages standard WebSocket control frames and connection state transitions transparently.

## Connection States

| State | Status Chip | Description |
|---|---|---|
| **Disconnected** | Gray | Socket is closed; ready to connect. |
| **Connecting** | Yellow | HTTP upgrade handshake in flight. |
| **Connected** | Green | Handshake accepted; full-duplex communication active. |
| **Error** | Red | Handshake failed, TLS error, or connection reset by peer. |

## Ping / Pong Frames (Heartbeats)

- Clapi automatically answers incoming WebSocket `Ping` frames (opcode `0x09`) with an immediate RFC-compliant `Pong` frame (opcode `0x0A`).
- Heartbeat frames are handled at the protocol layer to keep the connection alive without cluttering your message log.

## Reconnection Behavior

- If the server closes the connection cleanly (Close frame `0x08`), Clapi transitions to `Disconnected` and displays the close status code (e.g. `1000 Normal Closure`, `1001 Going Away`, `1008 Policy Violation`).
- Click **Connect** to re-open the session with the same parameters.
