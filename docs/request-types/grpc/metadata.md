---
title: gRPC Metadata & Custom Headers
description: Setting ASCII and binary metadata headers on gRPC calls in Clapi.
---

# gRPC Metadata & Custom Headers

<p class="badges">
  <span class="badge badge-protocol">gRPC</span>
</p>

## Overview

gRPC metadata is analogous to HTTP headers. Metadata allows passing contextual information such as authentication tokens, tracing IDs, and tenancy keys alongside RPC calls.

---

## ASCII Metadata
Standard string key-value pairs entered in the **Metadata** tab:
- `authorization`: `Bearer {{myJwtToken}}`
- `x-request-id`: <code v-pre>{{$guid}}</code>
- `tenant-id`: `acme-corp`

---

## Binary Metadata (`-bin`)
In gRPC, keys ending with `-bin` represent binary metadata values:
- Clapi automatically Base64-encodes binary metadata values before transmitting them over the wire.
- Incoming binary response metadata is decoded and rendered in a human-readable format.
