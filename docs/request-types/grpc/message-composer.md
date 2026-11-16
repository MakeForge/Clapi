---
title: JSON-to-Protobuf Message Composer
description: Composing gRPC payloads using JSON and Proto3 mapping in Clapi.
---

# JSON-to-Protobuf Message Composer

<p class="badges">
  <span class="badge badge-protocol">gRPC</span>
</p>

## Overview

Protobuf is a binary serialization format. To provide a smooth editing experience, Clapi allows authoring payloads in standard JSON and automatically translates between JSON and binary Protobuf using official proto3 JSON mapping rules (`com.google.protobuf.util.JsonFormat`).

---

## Example Mapping

### Proto Definition
```protobuf
syntax = "proto3";

package acme.orders;

message OrderRequest {
  string order_id = 1;
  int32 quantity = 2;
  double price = 3;
  bool expedited = 4;
  repeated string tags = 5;
}
```

### JSON in Clapi Composer
```json
{
  "orderId": "{{$guid}}",
  "quantity": 3,
  "price": 49.99,
  "expedited": true,
  "tags": ["hardware", "priority"]
}
```

::: tip Case Handling
Both `camelCase` (standard proto3 JSON format) and `snake_case` (original protobuf field names) are automatically supported by Clapi's JSON mapper.
:::
