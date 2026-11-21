# Contract: OpenCollection Storage Extension Specification

**Feature Branch**: `009-git-sync`
**Date**: 2026-11-09
**Status**: Draft

## Purpose

Defines the serialization schema and structure for persisting Git and local storage metadata in `OpenCollectionDocument.extensions.storage` conforming to OpenCollection v1.0.0.

---

## Schema Definition

### 1. Git Storage Configuration (YAML)

When a collection is configured for Git synchronization, the OpenCollection YAML document contains:

```yaml
opencollection: "1.0.0"
info:
  name: "Payment API"
  version: "1.0.0"
extensions:
  storage:
    type: "git"
    git:
      remoteUrl: "git@github.com:myorg/payment-api.git"
      branch: "main"
      credentials:
        type: "system"
config:
  protobuf:
    protoFiles:
      - path: "./protobuf/collection/payment_service.proto"
      - path: "/opt/shared-protos/common/types.proto"
    importPaths:
      - path: "./protobuf/collection/imports"
        disabled: false
items:
  - name: "Authorize"
    # ...
```

### 2. Local Storage Configuration (YAML / JSON)

When a collection is local (default):

```yaml
opencollection: "1.0.0"
info:
  name: "Local Testing Collection"
extensions:
  storage:
    type: "local"
items: []
```

---

## Serialization & Validation Behavior

1. **Format**: Standalone collection files in Git repositories MUST be serialized using YAML format (`collection.opencollection.yaml`).
2. **Extensions Mapping**:
   - `extensions["storage"]` is mapped to `StorageExtension` during import/load.
   - If `extensions["storage"]` is absent or null, it defaults to `{ "type": "local" }`.
3. **Protobuf Paths**:
   - Any path beginning with `./` (or relative path syntax) is recognized as a Git-synced asset relative to the collection directory.
   - Any path beginning with `/` (POSIX) or `[A-Za-z]:\` (Windows) is recognized as an absolute local filesystem path.
