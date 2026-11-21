---
name: spec-implementation-to-pr
description: Verify completed Spec Kit implementations and create or update corresponding GitHub pull requests using GitHub MCP. Use after /speckit-implement or when preparing a completed feature specification for code review.
metadata:
   author: Clapi Contributors
   last-updated: '2026-11-13'
   keywords:
      - Spec Kit
      - GitHub
      - GitHub MCP
      - Pull Requests
      - Issue Linking
      - Implementation Verification
      - Acceptance Criteria
      - Automated Testing
      - Git Workflow
      - Code Review
      - Feature Delivery
   scope: Completed Spec Kit feature implementations, their associated specification files, Git branches, commits, and GitHub issues and pull requests.
   primary-goal: Verify implementation completeness against the feature specification, run relevant quality checks, and create or update a GitHub pull request linked to the corresponding spec issue using a closing keyword so the issue closes when the PR is merged into the default branch.
---

# Spec Implementation to Pull Request

## Purpose

After a Spec Kit implementation, prepare and create a GitHub pull request linked to the feature's existing GitHub issue.

**Core rule: Every pull request created by this skill must reference its corresponding spec issue using a GitHub closing keyword.**

Example:

`Closes #42`

The issue must not be manually closed. GitHub will close it automatically when the pull request is merged into the repository's default branch.

Use GitHub MCP for all GitHub pull request and issue operations.

Use local Git for branch inspection, commits, and pushing.

Do not use GitHub CLI (`gh`).

## 1. Determine the active spec

Identify the feature that was most recently implemented.

Inspect:

- The current working directory.
- The current Git branch.
- Recent Git commits and changes.
- The relevant `specs/` feature directories.
- The `spec.md`, `plan.md`, and `tasks.md` files.

Use the conversation context to identify the spec if it clearly identifies the implementation just completed.

If multiple specs could be the target, ask the user which one to prepare.

Do not create a PR for an unrelated feature.

## 2. Find the corresponding GitHub issue

Read the spec directory name.

Find the issue using the stable marker:

`<!-- speckit-spec: specs/FEATURE-DIRECTORY -->`

Use GitHub MCP to search and retrieve the issue.

Confirm:

- The issue belongs to the current repository.
- The marker matches the active spec directory.
- The issue describes the feature being implemented.
- The issue number is known.

Do not identify the issue by title alone.

If no matching issue exists:

1. Do not invent an issue number.
2. Do not create a PR with an unrelated closing reference.
3. Explain that the spec issue is missing.
4. Ask whether the user wants the issue created using the `spec-to-github-issues` skill.

If multiple matching issues are found, stop and ask the user to resolve the ambiguity.

## 3. Verify implementation completeness

Before creating a PR, compare the implementation against:

- `spec.md`
- `plan.md`
- `tasks.md`
- The Git diff
- Relevant source code
- Relevant tests

Check:

1. Functional requirements are implemented.
2. Acceptance criteria are addressed.
3. Important architecture and design decisions are respected.
4. Error handling and validation are implemented.
5. Relevant tests exist.
6. Documentation is updated where required.
7. `CHANGELOG.md` is updated with a concise entry (including a link to the corresponding GitHub issue) under the active unreleased `## [X.Y.Z] - TBD` section, and `appVersion` in `gradle.properties` matches (creating a new SemVer-bumped `TBD` section and updating `appVersion` if the previous version is already released/tagged `v*`, per `AGENTS.md`).
8. No unrelated changes have been included.

Inspect task completion claims against actual code.

Do not assume a task is complete just because it is marked complete in `tasks.md`.

### Run tests

Identify the project's test commands from its documentation and configuration.

Run relevant tests, linters, formatters, and build checks where feasible.

Do not invent test commands.

Report:

- Checks that passed.
- Checks that failed.
- Checks that could not be run.
- Any known implementation gaps.

### Stop conditions

Do not create the PR if:

- Important acceptance criteria are not implemented.
- Relevant tests fail.
- There are unresolved build errors.
- The changes contain unrelated or unexplained modifications.
- `CHANGELOG.md` is missing an entry for the feature/fix or `gradle.properties` `appVersion` is out of sync with the active unreleased section.
- The target issue cannot be identified confidently.

Explain the blockers and what needs to be fixed.

If a check cannot be run because of an unavailable dependency or environment limitation, do not claim it passed. Ask the user before proceeding if the missing check is important to confidence in the change.

## 4. Inspect Git status and diff

Inspect:

- `git status`
- Current branch
- `git diff`
- `git diff --cached`
- Recent commits

Do not stage or commit unrelated user changes.

Do not discard, reset, overwrite, or silently modify unrelated work.

If changes are on the default branch:

1. Determine the appropriate feature branch name.
2. Create a feature branch without losing existing work.
3. Keep the branch associated with the active spec.

Suggested branch naming:

`feat/FEATURE-DIRECTORY`

If the branch already exists, inspect it and reuse it when appropriate.

Do not force-push.

## 5. Prepare the commit

Review the complete diff before committing.

Stage only files relevant to the feature.

Use a descriptive commit message, for example:

`feat: implement user authentication`

Do not include secrets, credentials, generated temporary files, or unrelated modifications.

If the changes have already been committed, do not create a redundant commit.

## 6. Push the feature branch

Use local Git to push the feature branch to the configured remote.

Before pushing:

- Confirm the remote points to the expected GitHub repository.
- Confirm the current branch is the intended feature branch.
- Confirm the staged changes contain only intended files.

If Git authentication or push fails, stop and report the error.

Do not attempt to bypass repository protections.

## 7. Check for an existing pull request

Use GitHub MCP to discover the available pull request search and retrieval tools.

Search for an existing PR associated with:

- The current head branch.
- The target base branch.
- The corresponding issue number.

If a matching open PR already exists:

- Update it if necessary.
- Preserve its existing discussion and review history.
- Do not create a duplicate PR.

If an existing PR is closed or merged, do not automatically create another one. Report its state and ask what the user wants to do.

## 8. Create the pull request

Use GitHub MCP to create the PR.

Do not use `gh pr create` or direct GitHub API calls.

Discover the available MCP tool and its schema before using it.

### PR title

Use:

`[FEATURE] Implement FEATURE NAME`

Example:

`[User Authentication] Implement authentication flow`

Keep the title clear and concise.

### PR body

Use this structure:

## Summary

Explain what the implementation delivers.

## Related issue

`Closes #ISSUE_NUMBER`

This closing keyword is mandatory.

For an issue in another repository, use the appropriate fully qualified reference:

`Closes OWNER/REPOSITORY#ISSUE_NUMBER`

## Implementation details

Summarize the main changes and relevant architectural decisions.

## Acceptance criteria

List the important acceptance criteria and their implementation status.

## Testing

List the tests, builds, and checks performed, including their results.

Clearly identify anything not run.

## Spec references

Link to the relevant files:

- `specs/FEATURE-DIRECTORY/spec.md`
- `specs/FEATURE-DIRECTORY/plan.md`
- `specs/FEATURE-DIRECTORY/tasks.md`

## Notes for reviewers

Highlight important implementation decisions, limitations, migration considerations, or review areas.

Do not claim tests passed unless they actually passed.

## 9. GitHub MCP requirements

Use GitHub MCP for:

- Finding the corresponding issue.
- Retrieving issue details.
- Searching for existing pull requests.
- Creating a pull request.
- Updating an existing pull request when appropriate.
- Retrieving the created or updated pull request.

Discover the actual MCP tools and their schemas at runtime.

Never assume tool names or argument fields.

Do not use:

- `gh pr create`
- `gh pr edit`
- `gh api`
- `curl` against GitHub APIs
- Direct REST or GraphQL requests through shell commands

Local Git commands for commits and pushes are allowed.

## 10. Issue-closing behavior

The PR body must contain the appropriate GitHub closing keyword:

`Closes #ISSUE_NUMBER`

Important:

- Creating a PR does not close the issue.
- The issue remains open while the PR is under review.
- GitHub automatically closes the issue when the PR is merged into the repository's default branch.
- Do not manually close the issue.
- Do not merge the PR automatically.
- Do not enable auto-merge unless the user explicitly asks.

If the PR is merged into a non-default branch, do not assume the issue will close automatically.

## 11. Final report

After the PR operation succeeds, report:

- Spec directory.
- Corresponding issue number and link.
- Source branch.
- Target branch.
- PR number and link.
- Tests and checks performed.
- Any outstanding warnings or limitations.

Do not claim the PR was created unless GitHub MCP confirms success.

---

# File 3: `AGENTS.md` (repository root)

# Repository Agent Instructions

## Spec Kit and GitHub workflow

When working with Spec Kit specifications, follow these rules:

### Creating feature issues

When asked to synchronize specifications with GitHub or create issues from specs:

- Use the `spec-to-github-issues` skill.
- Create exactly one GitHub issue per feature spec directory.
- Consolidate granular tasks into no more than 10 top-level checklist items.
- Use GitHub MCP for all issue operations.
- Never use GitHub CLI (`gh`) for issue management.

### After `/speckit-implement`

After `/speckit-implement` finishes successfully:

1. Identify the feature spec that was implemented.
2. Invoke the `spec-implementation-to-pr` skill.
3. Verify implementation completeness and run relevant checks.
4. Find the corresponding GitHub issue using its stable spec marker.
5. Create or update the pull request using GitHub MCP.
6. Include `Closes #ISSUE_NUMBER` in the PR description.
7. Report the PR URL and any verification results.

Do not create a PR if important acceptance criteria remain incomplete or relevant tests fail.

Do not close the issue manually. GitHub should close it when the PR is merged into the default branch.

Do not merge PRs or enable auto-merge unless explicitly requested.

### GitHub integration

GitHub issue and pull request operations must use the configured GitHub MCP server.

Discover available MCP tools and inspect their schemas before using them. Do not assume tool names or arguments.

Local Git commands may be used for branch management, commits, and pushing.

Never use GitHub CLI (`gh`) as a fallback.

### Safety

- Never overwrite unrelated user changes.
- Never force-push.
- Never commit secrets or credentials.
- Never create duplicate issues or pull requests.
- Never claim an operation succeeded without confirmation from the relevant tool.