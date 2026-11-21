---
title: gRPC Server Reflection
description: Auto-discovering services and methods from gRPC servers using Server Reflection.
---

# gRPC Server Reflection

<p class="badges">
  <span class="badge badge-protocol">gRPC</span>
</p>

## Overview

When the target gRPC server has the **gRPC Server Reflection Protocol** (`grpc.reflection.v1alpha.ServerReflection` or `grpc.reflection.v1.ServerReflection`) enabled, you don't need manual `.proto` files on your local machine.

## Using Reflection in Clapi

1. In the gRPC request bar, enter the server address:
   ```text
   localhost:50051
   ```
2. Enable the **Reflection** toggle or click **Use Server Reflection**.
3. Clapi sends an introspection query over HTTP/2 to discover:
   - All registered service definitions.
   - All RPC methods exposed by each service.
   - Complete input and output message type schemas.
4. The **Service** and **Method** dropdowns populate immediately with discovered endpoints.
