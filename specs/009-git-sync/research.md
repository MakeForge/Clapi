# Research: Git Collection Sync

**Feature Branch**: `009-git-sync`
**Date**: 2026-11-09
**Status**: Completed

## Overview

This research evaluates the technical choices and architecture for implementing Insomnia-style Git synchronization for OpenCollection documents in Clapi. It resolves all unknowns regarding process execution, repository storage topology, extension schema modeling, Protocol Buffer file structure and dual-path resolution, and user interaction workflows.

---

## Technical Decisions

### Decision 1: Direct Host Git Invocation (`ProcessBuilder`)

- **Chosen**: Direct invocation of the host system's `git` binary via Java `ProcessBuilder` (wrapped in a coroutine-friendly `GitCliClient` on Desktop JVM).
- **Rationale**:
  - The feature specification requires leveraging the system's Git setup directly without third-party Git libraries.
  - Using the system `git` CLI natively inherits the user's SSH keys, `ssh-agent`, Git credential helpers (macOS Keychain, Windows Credential Manager, Freedesktop Secret Service), GPG signing, and HTTP proxy settings without Clapi needing to capture or store user credentials.
  - Process stdout and stderr are captured with timeouts and descriptive error translation (e.g., distinguishing between missing `git` command, network failure, authentication rejection, and dirty working tree conflicts).
- **Alternatives Considered**:
  - *JGit*: A pure Java Git implementation. Rejected because it does not seamlessly support native OpenSSH agent workflows or modern OS credential helpers without extensive custom callbacks, adds several megabytes of dependency overhead, and explicitly conflicts with the requirement.

---

### Decision 2: Cloned Repository Layout & Storage Location

- **Chosen**:
  - Repositories are cloned under `~/.clapi/storage/<repo-id>/`.
  - The `<repo-id>` is a deterministic alphanumeric slug based on a hash of `remoteUrl` (e.g., `repo_<short-sha256>`), allowing multiple collections to share the same repository clone safely.
  - Within each repository, each collection occupies an isolated root directory named after the collection's slug/sanitized name:
    ```text
    ~/.clapi/storage/
    └── repo_a1b2c3d4/
        ├── .git/
        ├── Billing_Service/
        │   ├── collection.opencollection.yaml
        │   └── protobuf/
        │       ├── collection/
        │       └── requests/
        └── User_Service/
            ├── collection.opencollection.yaml
            └── protobuf/
    ```
- **Rationale**:
  - Conforms to the specification: "Git repo should be cloned by clapi into Clapi's default storage folder in 'storage' subfolder" and "In the git repo there should be a root folder per collection".
  - Multi-collection support works seamlessly: multiple collections pointing to the same remote repo share a single clone, avoiding duplicate disk usage and branch divergence.
- **Alternatives Considered**:
  - *Flat repo root without collection subfolders*: Would prevent storing multiple collections in a single Git repository.
  - *Separate clones per collection even with same remote*: Redundant network traffic and potential file lock conflicts.

---

### Decision 3: OpenCollection YAML Storage & Extension Schema

- **Chosen**:
  - Stored inside each collection root directory as `collection.opencollection.yaml`.
  - Conforms to OpenCollection v1.0.0 specification using the free-form `extensions` property:
    ```json
    {
      "extensions": {
        "storage": {
          "type": "git",
          "git": {
            "remoteUrl": "git@github.com:user/repo.git",
            "branch": "main",
            "credentials": {
              "type": "system"
            }
          }
        }
      }
    }
    ```
  - For local-only collections:
    ```json
    {
      "extensions": {
        "storage": {
          "type": "local"
        }
      }
    }
    ```
- **Rationale**:
  - Storing the configuration directly in `OpenCollectionDocument.extensions` allows the document itself to carry its sync metadata when shared across environments or teammates.
  - `core-storage` already supports robust YAML serialization via `libs.kaml` in `OpenCollectionSerializer`.
- **Alternatives Considered**:
  - *Private state storage only*: Would not persist sync metadata when exporting or transferring collections between workstations.

---

### Decision 4: Protobuf Directory Structure and Dual-Path Resolution

- **Chosen**:
  - When Git sync is enabled, `.proto` files with "Sync to Git" toggled ON are copied into the repository's `protobuf/` subfolder:
    - Collection-level proto files: `<repo-root>/<collection-dir>/protobuf/collection/<proto-name>.proto`
    - Collection-level import paths: `<repo-root>/<collection-dir>/protobuf/collection/imports/<relative-or-flat>/`
    - Request-level proto files: `<repo-root>/<collection-dir>/protobuf/requests/<requestId>/<proto-name>.proto`
  - In `OpenCollectionDocument`, synced files are stored with relative paths starting with `./` (e.g. `./protobuf/collection/service.proto`).
  - Proto files with "Sync to Git" toggled OFF retain their local absolute paths (e.g. `/usr/local/include/service.proto` or `C:\protos\service.proto`).
  - Path Resolution Engine (`ProtoPathResolver`):
    - If `path.startsWith("./")` or is relative: resolved against `<repo-root>/<collection-dir>/`.
    - If `path` is absolute: resolved directly from the local filesystem.
    - Transparently provided to `ProtocJarParser` and gRPC execution pipelines.
- **Rationale**:
  - Solves portability across team members: repository-synced schemas work on any machine regardless of username or filesystem root.
  - Retains local developer flexibility: engineers can reference local workstation SDKs without polluting the Git repository.
- **Alternatives Considered**:
  - *Base64 embedding inside YAML*: Violates requirement for clean git diffs and breaks external proto tooling.
  - *Symlinks*: Not portable across Windows and Linux/macOS.

---

### Decision 5: Git Status Polling, Sidebar Indicator & Sync Dialog

- **Chosen**:
  - **Git Status Polling**: Non-blocking background checks using `git status --porcelain=v1` and `git rev-parse --abbrev-ref HEAD`. Triggered on workspace save, tab switch, or manual refresh in the dialog.
  - **Sidebar Indicator**: Placed in the bottom-left corner of the sidebar layout.
    - Displays current branch name (e.g. `main` or `feature-x`).
    - Displays an uncommitted changes badge/dot when modified or untracked files exist.
    - Hidden or shows local state when the active collection has storage type `"local"`.
  - **Git Sync Dialog**:
    - Opens when clicking the sidebar Git indicator.
    - Lists changed files grouped by collection (e.g., `collection.opencollection.yaml`, `protobuf/collection/service.proto`).
    - Multi-line commit message input field.
    - Actions: "Commit", "Push", "Pull", and "Close".
    - On Pull conflict: Detects conflict exit code or conflict status markers (`UU`, `AA`), displays an alert banner explaining that conflicts occurred and must be resolved externally, and keeps the local changes safe.
- **Rationale**:
  - Clear, familiar UX matching developer expectations from Insomnia.
  - Keeps scope manageable and reliable by leaving conflict resolution to dedicated diff/merge tools.
