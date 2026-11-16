---
name: spec-close-github-issue
description: Find an existing GitHub issue for a given specification ID (e.g., 001-grpc-support), locate the implementing commit(s) in git history by commit message, and close the issue as completed with clickable commit references.
---

# Close Spec GitHub Issue

Use this skill when the user asks to close an existing GitHub issue for a specific spec (for example: `"close the issue for 001-grpc-support"`).

## Core Rules

1. **Inspect commit messages only:** Match commits strictly by their commit messages (e.g., `spec: 001-grpc-support`, `feat: 001-grpc-support`). Do **not** inspect file diffs or code contents.
2. **Use GitHub MCP tools:** Prefer the GitHub MCP server tools (`list_issues` / `search_issues`, `list_commits`, `add_issue_comment`, `issue_write`) so all operations happen directly against the remote repository.
3. **Verify before closing:** Ensure the matched commit(s) exist on GitHub's remote history before referencing them so GitHub renders valid clickable commit links.

## Workflow

### Step 1: Identify the Spec Identifier
Extract the spec identifier from the user's request (e.g., `001-grpc-support`, `042-oauth-flow`).

### Step 2: Find the Existing Open GitHub Issue
1. Search open issues in the repository using `search_issues` or `list_issues` (with `state: "open"`) matching the spec identifier or feature name.
2. Confirm the exact `issue_number` and `title`.
   - If multiple open issues match, ask the user which `issue_number` to close before proceeding.
   - If no open issue is found, check closed issues (`state: "closed"`) and inform the user if it is already closed.

### Step 3: Find Implementing Commits by Message
1. Fetch recent commits on the target branch using `list_commits` (or `git log --oneline` locally if needed to check unpushed commits, though only pushed SHAs will link on GitHub).
2. Filter commits whose **commit message** contains the spec identifier (e.g., `001-grpc-support`).
   - Typical commit sequence:
     - Specification commit: `spec: <spec-id>`
     - Implementation commit(s): `feat: <spec-id>` (or `fix: <spec-id>`, `refactor: <spec-id>`)
3. Collect the commit SHAs in chronological order (oldest to newest), distinguishing between the spec commit (`spec:`) and the implementing commit(s) (`feat:`, `fix:`, etc.).
   - If no implementing commit (`feat:` / `fix:`) is found in remote commits, stop and notify the user (the commit may not be pushed yet).

### Step 4: Comment with Commit References
Call `add_issue_comment` on the target `issue_number` **before** closing the issue so the activity timeline reads chronologically:

```markdown
Implemented in <implementing_sha_1> (spec defined in <spec_sha>).
```

*(If multiple implementation commits exist, list all implementing SHAs separated by commas, e.g., `Implemented in a1b2c3d, e4f5g6h.` If no separate `spec:` commit exists, simply write `Implemented in <implementing_sha>.`)*

### Step 5: Close the Issue as Completed
Immediately call `issue_write` to close the issue:
- `method`: `"update"`
- `issue_number`: `<issue_number>`
- `state`: `"closed"`
- `state_reason`: `"completed"`

### Step 6: Report Summary
Provide a brief confirmation to the user including:
- Issue number, title, and URL
- Matched commit SHA(s) and commit message(s) referenced in the closing comment