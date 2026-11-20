---
title: Team Workflows & Pull Requests
description: Best practices for collaborating on API collections with your engineering team using Git PRs.
---

# Team Workflows & Pull Requests

<p class="badges">
  <span class="badge badge-protocol">Collaboration</span>
</p>

## Overview

By managing API collections in standard Git repositories, your team can easily maintain Clapi workspaces and keep them in sync without much effort.

## Typical Workflow

1. **Pull Changes**: Ensure you are working with the latest changes via Git Sync dialog via pull action.
2. **Make Changes**: Edit or create new Requests/Collections/Folders or similar in Clapi.
3. **Commit & Push**: Open the Git Sync dialog in Clapi, write a commit message, and push the branch to your Git remote.

## Conflict Resolution

Because OpenCollection organizes collections and folders into deterministic files:
- Merge conflicts in Git are rare.
- If two developers modify the same request simultaneously, Git highlights the exact lines (headers, URL, or body).
- Resolve conflicts using standard Git tools or your IDE's 3-way merge editor.
