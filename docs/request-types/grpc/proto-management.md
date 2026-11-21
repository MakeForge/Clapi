---
title: Protobuf Schema Management
description: Managing .proto files, import directories, and collection-level Protobuf settings in Clapi.
---

# Protobuf Schema Management

<p class="badges">
  <span class="badge badge-protocol">gRPC</span>
</p>

## Overview

gRPC relies on Protocol Buffer definitions (`.proto`) to define service contracts, RPC methods, and binary message formats. Clapi provides flexible schema management at both the request and collection levels.

---

## Loading Proto Files

### Request-Level Selection
1. In a gRPC request tab, click the **Proto File** button.
2. Browse your local filesystem and select your primary `.proto` file (e.g. `user_service.proto`).
3. If your proto imports other schemas (e.g. `import "google/protobuf/timestamp.proto";`), configure **Import Paths** to include the parent directories.

---

## Collection-Level Protobuf Settings

When working on a collection with multiple gRPC endpoints, you can configure shared Protobuf settings on the collection root:
- Open the collection editor and switch to the **Protobuf** tab.
- Add shared proto directories and base import roots.
- All gRPC requests within the collection automatically inherit these schemas and import paths, eliminating repetitive file selection.
- **Git Sync Integration**: Collection proto files can be tracked and synchronized through Git alongside your requests.
