---
title: Git Sync Overview
description: Collaborative API development using standard Git repositories with zero SaaS cloud lock-in.
---

# Git Sync Overview

<p class="badges">
  <span class="badge badge-protocol">Collaboration</span>
</p>

## Overview

Traditional API clients charge expensive monthly per-seat subscriptions to synchronize API collections through proprietary cloud databases. This locks engineering teams into vendor ecosystems and exposes proprietary internal endpoint documentation to third-party cloud breaches.

**Clapi provides a superior, Git-native alternative**:
- Collections can be mapped directly to local Git repositories.
- Use standard Git workflows—branching, commits, pull requests, code reviews, and tags.
- Sync across your team using your existing Git forge (GitHub, GitLab, Bitbucket, Gitea, or self-hosted SSH servers).
- Zero cloud subscription costs. Zero SaaS lock-in.

---

## The OpenCollection Advantage in Git

Git repositories require file formats that produce clean, human-readable diffs. Clapi's underlying **OpenCollection** format is specifically designed for version control:
- Deterministic key ordering prevents arbitrary diff churn when modifying requests.
- Discrete YAML or JSON files for requests, folders, and environments.
- Clean 3-way merge resolution using standard Git merge tools.
- Secret values are not synced and private authentication tokens/credentials are excluded automatically and kept in the local encrypted storage.

---

## gRPC Proto Schema Synchronization

A common problem in API collaboration is sharing Protocol Buffer (`.proto`) schema files without breaking machine-specific paths across teammates' workstations (e.g. `/Users/alice/...` vs `C:\Users\bob\...`).

Clapi solves this with built-in **`ProtoPathResolver`** synchronization:

- **Dual-Path Resolution**: Paths starting with `./protobuf/...` resolve dynamically against the collection's local Git repository root subfolder (`<collectionDir>/protobuf/collection/` or `<collectionDir>/protobuf/requests/<requestId>/`), while preserving external absolute paths when desired.
- **Automatic Repository Ingestion**: When attaching `.proto` schema files or import search paths, Clapi can synchronize them directly into your collection's Git working copy:
  - Collection-level schemas: `./protobuf/collection/<fileName>.proto`
  - Collection-level imports: `./protobuf/collection/imports/<importDir>`
  - Request-level schemas: `./protobuf/requests/<requestId>/<fileName>.proto`
- **Zero Broken Paths for Teams**: When other team members pull or clone the collection repository, all gRPC methods, types, and nested imports resolve relatively without manual configuration.
- **End-to-End Verified**: Fully covered in automated integration and end-to-end tests (`GitSyncE2ETest`), ensuring commits, merges, and pushes retain complete schema fidelity.
