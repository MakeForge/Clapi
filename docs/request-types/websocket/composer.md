---
title: WebSocket Message Composer
description: Composing and sending real-time text, JSON, and binary messages in Clapi.
---

# WebSocket Message Composer

<p class="badges">
  <span class="badge badge-protocol">WebSocket</span>
</p>

## Overview

The WebSocket Message Composer allows authoring payloads while a connection is actively open.

---

## Formats

### 1. JSON
Full syntax highlighting and bracket matching. Perfect for JSON-RPC, GraphQL subscriptions, and STOMP messages.
```json
{
  "type": "subscribe",
  "topic": "trades.BTC-USD"
}
```

### 2. Plain Text
Raw string frames for simple chat or custom text protocols.

### 3. Binary (Hex / Base64)
Transmit raw binary frames (opcode `0x02`) by inputting Base64 or Hex encoded strings.

---

## Dynamic Variable Injection

Payloads support dynamic variable interpolation:
```json
{
  "messageId": "{{$guid}}",
  "clientTime": {{$timestamp}},
  "token": "{{authToken}}"
}
```
Variables are resolved dynamically on each press of the **Send** button.
