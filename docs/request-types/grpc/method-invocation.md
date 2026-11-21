---
title: Method Selection & RPC Invocation
description: Selecting services, invoking RPCs, and handling streaming gRPC calls in Clapi.
---

# Method Selection & RPC Invocation

<p class="badges">
  <span class="badge badge-protocol">gRPC</span>
</p>

## Overview

gRPC supports four distinct communication paradigms over HTTP/2. Clapi supports unary and streaming RPC methods.

## RPC Modes

### 1. Unary RPC
The client sends a single request message and receives a single response message.
- Example: `GetUserProfile(GetUserRequest) returns (UserProfile)`
- The request bar shows a standard **Send** button.

### 2. Server Streaming RPC
The client sends a single request message and receives a stream of response messages over time.
- Example: `StreamStockQuotes(StockSymbol) returns (stream StockPrice)`
- Clapi streams incoming messages into the response viewer as they arrive.

### 3. Client Streaming RPC
The client sends a stream of request messages and receives a single response summary.

### 4. Bidirectional Streaming RPC
Both client and server send a stream of messages independently over the same persistent HTTP/2 connection.
