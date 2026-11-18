---
title: Collection-Level Protobuf Settings
description: Configuring shared Protocol Buffer schemas and import roots across collections in Clapi.
---

# Collection-Level Protobuf Settings

<p class="badges">
  <span class="badge badge-protocol">Workspaces & gRPC</span>
</p>

## Overview

When developing microservice architectures with gRPC, multiple requests typically share identical Protobuf schemas or import standard Google API definitions (`google/protobuf/timestamp.proto`, `google/api/annotations.proto`).

---

## Configuring Collection Protobuf Roots

In the collection editor, navigate to the **Protobuf** tab:
1. **Proto Directory Roots**: Add one or more local directories where `.proto` schema files are stored.
2. **Import Path Resolution**: Specify additional base directories for resolving `import "..."` statements in proto files.
3. **Auto-Inheritance**: Any gRPC request created inside this collection immediately gains access to these schemas without manual file selection.

---

## Git Synchronization for Proto Files

When a collection is backed by Git:
- Proto schema files can be stored directly within the collection repository (e.g. `./proto/`).
- Clapi automatically tracks changes to `.proto` definitions and includes them in the **Git Sync** staging view.
