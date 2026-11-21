---
title: WebSocket Connection & Handshake
description: Configuring WebSocket handshake headers, subprotocols, and SSL/TLS in Clapi.
---

# WebSocket Connection & Handshake

<p class="badges">
  <span class="badge badge-protocol">WebSocket</span>
</p>

## Overview

WebSocket connections begin with an HTTP/1.1 Upgrade handshake (`Upgrade: websocket`, `Connection: Upgrade`). Clapi gives you granular control over this initial handshake exchange.

---

## Handshake Parameters & Headers

### Query Parameters
Query parameters entered in the address bar (e.g. `wss://api.example.com/ws?token={{sessionToken}}`) are encoded into the handshake request URL.

### Handshake Headers
In the **Headers** tab, you can add custom headers evaluated during the upgrade request:
- `Authorization: Bearer {{token}}`
- `Sec-WebSocket-Protocol: graphql-ws, soap`
- `Origin: https://app.example.com`

---

## TLS & Secure WebSockets (`wss://`)

When connecting to `wss://` endpoints:
- Clapi validates remote certificates using the OS trust store by default.
- If testing against local self-signed development servers, you can disable SSL verification in **Settings → Network** or supply custom CA certificates.
