---
title: In-App Git Operations
description: Using the Git Sync dialog, reviewing dirty status, committing, pushing, and pulling in Clapi.
---

# In-App Git Operations

<p class="badges">
  <span class="badge badge-protocol">Collaboration</span>
</p>

## Overview

Clapi includes a simple integrated Git UI, allowing you to perform essential version control operations without switching back and forth between terminal windows.

---

## Sidebar Git Status Indicator

When an active collection is linked to a Git repository:
- The sidebar displays a branch pill (e.g. `main` or `feature/user-api`).
- **Status Badges**:
  - `Clean`: All local modifications are committed.
  - `Modified`: Changes are detected in requests, folders, or proto files.
  - `Ahead / Behind`: Indicates unpushed local commits or incoming remote commits.
- Click the status indicator to open the **Git Sync Dialog**.

---

## In-App Git Sync Dialog

1. **Changed Files Review**: Inspect modified requests, newly added endpoints, and changed `.proto` definitions.
2. **Commit Message**: Enter a descriptive commit message (e.g. `feat(users): add user profile endpoint`).
3. **Commit**: Commits changes locally to the active Git branch.
4. **Push**: Pushes local commits to the upstream remote repository.
5. **Pull**: Fetches and merges incoming changes from teammates.
6. **Branch Switching**: Switch branches directly from the branch dropdown.
