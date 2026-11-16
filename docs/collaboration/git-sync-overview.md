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
