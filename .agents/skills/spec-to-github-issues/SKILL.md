---
name: spec-to-github-issues
description: Create or update exactly one concise GitHub issue per Spec Kit feature spec directory using GitHub MCP. Summarize feature scope with a short overview, high-level checklist, special testing notes, and direct reference links to spec documents.
metadata:
   author: Clapi Contributors
   last-updated: '2026-11-15'
   keywords:
      - Spec Kit
      - GitHub
      - GitHub MCP
      - Issue Management
      - Feature Specifications
      - Requirements
      - Implementation Checklist
      - Project Tracking
   scope: Spec Kit feature directories and their specification, plan, task, research, data model, and contract files.
   primary-goal: Create or maintain exactly one concise GitHub issue per Spec Kit feature directory, providing a focused overview, general scope, high-level implementation checklist, and spec reference links rather than verbose duplicates of spec documentation.
---

# Spec to GitHub Issues

## Purpose

Convert Spec Kit feature specifications into concise, readable GitHub issues.

**Core rule: Create exactly one concise GitHub issue per spec directory.**

GitHub issues should serve as high-level tracking items, not exhaustive duplicates of spec files. Keep them concise: provide a short overview, general scope, high-level implementation checklist, special testing considerations, and reference links to the underlying spec markdown files for all detailed technical context and acceptance criteria.

Use GitHub MCP for all GitHub issue operations. Do not use GitHub CLI (`gh`), GitHub REST calls through shell commands, or another GitHub integration.

## 1. Identify the repository

1. Inspect the current working directory.
2. Identify the Git repository and its root.
3. Determine the GitHub owner and repository from the configured Git remote.
4. Confirm that the GitHub MCP connection is available.
5. Discover the available GitHub MCP tools and their input schemas.
6. Use the MCP tools that provide issue search, issue creation, issue updates, and issue retrieval.

Do not assume particular MCP tool names. Discover the tools available in the current environment.

If the repository cannot be identified, or GitHub MCP is unavailable, stop and explain what is missing.

Do not use a different GitHub integration as a fallback.

## 2. Discover spec directories

Inspect the repository's `specs/` directory.

Each feature directory should be considered a separate spec when it contains a `spec.md` file.

Typical structure:

    specs/
    ├── 001-user-authentication/
    │   ├── spec.md
    │   ├── plan.md
    │   ├── tasks.md
    │   ├── research.md
    │   └── data-model.md
    ├── 002-payment-processing/
    │   ├── spec.md
    │   ├── plan.md
    │   └── tasks.md
    └── ...

For each feature directory, read the available files:

- `spec.md` — requirements, user stories, acceptance scenarios, and constraints.
- `plan.md` — architecture, technologies, implementation approach, and project structure.
- `tasks.md` — detailed implementation tasks and dependencies.
- `research.md` — relevant technical decisions and research.
- `data-model.md` — entities and relationships.
- `contracts/` — API contracts and interface definitions.
- Other relevant documentation in the feature directory.

Do not assume every file exists.

Ignore unrelated folders and files that are not feature specifications.

## 3. Understand the spec before creating an issue

For each spec directory, review the files to synthesize:

- Feature name, core problem solved, and primary objective.
- General scope and key functional capabilities.
- High-level implementation outcomes and status.
- Any special testing or validation requirements (e.g., E2E fixtures, benchmark constraints, security isolation).

Do not copy entire sections (such as detailed Given/When/Then acceptance tables, raw data models, or comprehensive architectural analyses) into the issue body. The issue should remain concise and delegate details to the spec files via reference links.

## 4. High-level implementation checklist

The issue must contain a concise, high-level checklist, not a copy of granular tasks from `tasks.md`.

Guidelines:

- Aim for 4–7 top-level checklist items (never exceed 8).
- Group related tasks into major implementation milestones or functional outcomes.
- Keep checklist items clear and outcome-oriented.
- Avoid granular subtasks (e.g., do not list individual helper methods or unit test files).
- The detailed task breakdown remains in `tasks.md`.

## 5. Find an existing issue before creating one

Every issue created by this skill must contain a stable marker in its body:

`<!-- speckit-spec: specs/FEATURE-DIRECTORY -->`

For each spec directory:

1. Search the repository's GitHub issues for the exact marker.
2. Search both open and closed issues if the MCP tools support it.
3. If an issue with the marker exists, update that issue.
4. If no matching issue exists, create one.
5. Never create a second issue for the same spec directory.
6. Do not match issues solely by title.
7. Preserve existing issue discussion, comments, labels, and history.
8. Do not reopen a closed issue automatically. Report it and leave its state unchanged unless explicitly asked to reopen it.

If search results are ambiguous, inspect the candidate issue bodies before deciding.

## 6. Issue title

Use this format:

`[Spec] FEATURE NAME`

Examples:

- `[Spec] User Authentication`
- `[Spec] Payment Processing`
- `[Spec] Notification Management`

Use a clear, concise feature name.

## 7. Concise issue body template

Create or update the issue using this concise structure:

### Overview

A short, focused description (1–2 paragraphs) explaining what the feature is, the problem it solves, and the intended outcome.

### Scope & Functionality

Brief bullet points summarizing the general user-facing capabilities and functional scope. Keep it high-level.

### Implementation Checklist

High-level checklist (4–7 items) representing primary outcomes.

Example:

- [ ] Core data models and serialization.
- [ ] Persistence drivers and repository implementation.
- [ ] Service integration and execution adaptation.
- [ ] Automated validation and round-trip tests.

### Special Testing Considerations

*(Include only if special testing is required; omit or keep to 1–2 lines if standard testing applies)*
Brief notes on any special, critical, or non-standard tests requested (e.g. cross-format round-trip verification, specific test harness/containers, performance limits).

### Status

A concise, evidence-based status line:
- **Status**: Not implemented | Partially implemented | Implemented — verification pending | Implemented and verified

### Spec References

Provide direct links to the spec artifacts for full technical context, detailed acceptance criteria, data models, and task breakdowns:

- [Specification](specs/FEATURE-DIRECTORY/spec.md)
- [Implementation Plan](specs/FEATURE-DIRECTORY/plan.md)
- [Task Breakdown](specs/FEATURE-DIRECTORY/tasks.md)
*(Include links to `data-model.md`, `research.md`, or `contracts/` if present in the spec directory)*

Always include the stable marker at the end of the body:

`<!-- speckit-spec: specs/FEATURE-DIRECTORY -->`

## 8. Labels

Use existing repository labels where appropriate.

Suggested labels:

- `spec`
- `enhancement` or `bug`
- `documentation`
- `technical-debt`
- `refactoring`
- `http`/`grpc`/`graphql`/`websocket`/`mcp` (if issue is scoped to one or multiple request types)
- `data-model` (if general changes to the underlying data model are issues focus)

Do not create a large number of new labels. Do not fail the workflow merely because a suggested label does not exist.

If the repository uses a different label convention, follow it.

## 9. Updating existing issues

When updating an existing issue:

- Preserve the issue number and URL.
- Preserve relevant existing discussion and history.
- Refresh the description to reflect the current concise format and status.
- Update checklist checkboxes when implementation progresses.
- Keep the stable marker unchanged.
- Do not overwrite unrelated manually maintained information without a clear reason.
- Do not close or reopen the issue as part of this workflow.

## 10. GitHub MCP requirements

All GitHub issue operations must use GitHub MCP.

Before acting:

1. Discover the available MCP tools.
2. Identify the tools for searching, retrieving, creating, and updating issues.
3. Inspect the tool schemas.
4. Confirm the target owner and repository.
5. Use the discovered tools with the correct arguments.

Never invent MCP tool names or argument fields.

Do not use:

- `gh issue create`
- `gh issue edit`
- `gh api`
- `curl` against GitHub APIs
- Direct REST or GraphQL requests through shell commands

Local file reading and Git inspection are allowed.

## 11. Quality checks

Before creating or updating an issue, verify:

- Exactly one issue is targeted for the spec directory.
- The title clearly names the feature with `[Spec] <Feature Name>`.
- The overview is concise and short (1–2 paragraphs).
- Scope & functionality is high-level and brief.
- The checklist is high-level with 4–7 items (never verbose task-level lists).
- Special testing notes are included only if there are specific non-standard testing requirements.
- Full technical details, detailed acceptance criteria, and architecture are referenced via links to the spec markdown files rather than duplicated in the issue.
- Implementation status is accurate and evidence-based.
- The stable marker is present.

## 12. Final report

After processing all spec directories, report:

- Number of spec directories discovered.
- Number of issues created.
- Number of existing issues updated.
- Number of closed issues left unchanged.
- Any specs that could not be processed.
- Any ambiguous issue matches.
- Links to all created or updated issues.

Use a concise table:

| Spec directory | Action | GitHub issue |
|---|---|---|
| `001-user-authentication` | Created | #42 |
| `002-payment-processing` | Updated | #43 |

Do not claim an issue was created or updated unless GitHub MCP confirms the operation succeeded.
