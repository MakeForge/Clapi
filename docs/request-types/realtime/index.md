---
title: Realtime Streams (SSE, Socket.IO, MQTT)
description: Consuming Server-Sent Events, Socket.IO, and MQTT event streams in Clapi.
---

# Realtime Streams (SSE, Socket.IO, MQTT)

<p class="badges">
  <span class="badge badge-protocol">Realtime Protocols</span>
</p>

## Overview

In addition to WebSockets, modern APIs utilize streaming event architectures. Clapi natively supports **Server-Sent Events (SSE)**, **Socket.IO**, and **MQTT over WebSockets**.

## 1. Server-Sent Events (SSE)

- **Content-Type**: `text/event-stream`
- **Streaming Parser**: Events are parsed into discrete blocks showing `event`, `data`, `id`, and `retry`.
- **Typewriter Effect**: LLM token streams (e.g. OpenAI-compatible `/v1/chat/completions` with `"stream": true`) are visualized as tokens arrive in real time.

## 2. Socket.IO

- Connects to Socket.IO v4 servers with custom namespaces and query parameters.
- Emits named events with structured JSON arguments.
- Listens for specific incoming event channels with dedicated filtering.

## 3. MQTT over WebSockets

- Connects to MQTT brokers via WebSocket transport (`ws://` / `wss://`).
- Subscribe to topic filters with wildcards (`#`, `+`).
- Publish telemetry payloads with configurable Quality of Service (QoS 0, 1, 2).
