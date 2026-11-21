---
title: gRPC Requests
description: Complete guide to executing gRPC unary and streaming calls with Protobuf in Clapi.
---

# gRPC Requests

<p class="badges">
  <span class="badge badge-protocol">Protocol: gRPC / HTTP/2</span>
</p>

## Overview

<ThemedScreenshot
  light="/generated/screenshots/MainScreenPreviewsKt.MainScreenGrpcPreviewLight.W1920dp_H1080dp.png"
  dark="/generated/screenshots/MainScreenPreviewsKt.MainScreenGrpcPreviewDark.W1920dp_H1080dp.png"
  alt="Clapi gRPC Request Editor"
/>

Clapi includes native support for **gRPC** (`RequestKind.GRPC`). It enables loading Protocol Buffer (`.proto`) files, discovering services via Server Reflection, selecting RPC methods from dropdowns, authoring request payloads as user-friendly JSON, and inspecting binary protobuf responses.

---

## Quick Start

1. Create a new request and set the protocol to **gRPC** (`RequestKind.GRPC`).
2. Enter the server host and port (e.g. `localhost:50051` or `grpc.example.com:443`).
3. Load your schema:
   - Click **Select Proto File** to browse your local `.proto` definitions, OR
   - Click **Use Server Reflection** to fetch services dynamically from the running server.
4. Select the target **Service** and **Method** from the dropdowns (e.g. `UserService / GetUserProfile`).
5. Notice how Clapi generates a sample JSON payload matching the protobuf message schema.
6. Click **Send** (`⌘ + Enter` / `Ctrl + Enter`).
7. Inspect the returned protobuf response formatted as JSON in the right pane.

---

## Detailed Feature Subpages

- [Protobuf Management](./proto-management.md): Loading `.proto` files, directory roots, import paths, and collection-level protobuf settings.
- [Server Reflection](./reflection.md): Connecting to gRPC reflection services for automatic schema discovery.
- [Method Selection & RPC Invocation](./method-invocation.md): Unary RPCs, client streaming, server streaming, and bidirectional streaming.
- [JSON-to-Protobuf Composer](./message-composer.md): Composing payloads in JSON, proto3 mapping rules, and schema generation.
- [Metadata & Custom Headers](./metadata.md): Setting gRPC metadata key-value pairs and binary headers (`-bin`).
- [TLS & Client Certificates](./tls.md): Plaintext (h2c) vs. TLS encryption, custom CA certificates, and mutual TLS (mTLS).
