# Data Model: Git Collection Sync

**Feature Branch**: `009-git-sync`
**Date**: 2026-11-09
**Status**: Draft

## Overview

This document specifies the core entities, data structures, validation rules, and state transitions for Git synchronization and storage configuration in Clapi.

---

## Entities & Data Structures

### 1. StorageExtension & StorageType

Represents the storage configuration persisted in `OpenCollectionDocument.extensions["storage"]`.

```kotlin
package io.github.makeforge.clapi.core.model.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

enum class StorageType {
    @SerialName("local")
    LOCAL,
    @SerialName("git")
    GIT,
}

@Serializable
data class StorageExtension(
    val type: StorageType = StorageType.LOCAL,
    val git: GitStorageConfig? = null,
)

@Serializable
data class GitStorageConfig(
    val remoteUrl: String,
    val branch: String = "main",
    val credentials: GitCredentialsConfig = GitCredentialsConfig(),
)

@Serializable
data class GitCredentialsConfig(
    val type: String = "system",
)
```

**Validation Rules**:
- When `type == StorageType.GIT`:
  - `git` MUST NOT be null.
  - `git.remoteUrl` MUST be a non-blank valid Git URL (HTTPS or SSH format).
  - `git.branch` MUST be a non-blank valid Git branch name (defaulting to `"main"` if not specified).
  - `git.credentials.type` MUST be `"system"` in the initial version.
- When `type == StorageType.LOCAL`:
  - `git` SHOULD be null or ignored.

---

### 2. GitRepositoryClone

Represents a local Git working copy cloned on disk.

```kotlin
data class GitRepositoryClone(
    val repoId: String,               // Unique deterministic hash/slug of remoteUrl
    val remoteUrl: String,            // Remote repository URL
    val workingDirectory: String,     // Absolute path: ~/.clapi/storage/<repoId>/
    val activeBranch: String,         // Current active branch
)
```

**Attributes & Invariants**:
- `workingDirectory` always resides inside Clapi's root storage directory under `storage/` (e.g. `~/.clapi/storage/<repoId>`).
- Contains a valid `.git` directory initialized or cloned by the system Git binary.
- Can host multiple collection root folders simultaneously.

---

### 3. CollectionGitStatus & GitFileChange

Represents the live synchronization state of a Git-synced repository or collection.

```kotlin
enum class GitChangeKind {
    MODIFIED,
    ADDED,
    DELETED,
    UNTRACKED,
    CONFLICTED,
}

data class GitFileChange(
    val relativePath: String,         // Path relative to repository root
    val collectionName: String,       // Inferred collection name from root folder
    val changeKind: GitChangeKind,    // Kind of working tree change
)

data class CollectionGitStatus(
    val branch: String,
    val isClean: Boolean,
    val hasConflicts: Boolean,
    val changes: List<GitFileChange> = emptyList(),
    val aheadCount: Int = 0,
    val behindCount: Int = 0,
    val lastError: String? = null,
)
```

---

### 4. GitSyncProtoFile

Represents a Protocol Buffer file or import path with sync capability.

```kotlin
data class ProtoFileItemState(
    val path: String,                 // File path (relative ./protobuf/... or absolute)
    val syncToGit: Boolean = false,   // True if marked to be synchronized to Git
)

data class ProtoImportPathState(
    val path: String,                 // Directory path
    val enabled: Boolean = true,
    val syncToGit: Boolean = false,
)
```

**Path Convention Rules**:
- If `syncToGit == true`:
  - The physical file is copied into `<repo-root>/<collection-dir>/protobuf/collection/` or `<repo-root>/<collection-dir>/protobuf/requests/<requestId>/`.
  - The path recorded in the OpenCollection document is serialized as `./protobuf/...`.
- If `syncToGit == false`:
  - The original absolute path from the local workstation is stored as-is in the OpenCollection document.

---

## State Transitions

### 1. Storage Configuration Lifecycle

```text
[Local Storage]
      │
      │ User enters remoteUrl + branch and clicks "Save" in Storage Tab
      ▼
[Cloning / Initializing Repository]
      │
      ├─── Git error (invalid URL / no auth / no git binary) ──► [Local Storage (error displayed)]
      │
      └─── Success
             │
             ▼
      [Export Collection YAML + Synced Protos to Repo]
             │
             ▼
      [Git Storage Active]
             │
             │ User switches storage type back to "Local" and clicks "Save"
             ▼
      [Local Storage] (repo clone preserved on disk, document marked local)
```

### 2. Git Synchronization State Machine

```text
[Clean Working Copy]
      │
      │ User edits collection (requests, folders, variables, proto files)
      ▼
[Dirty / Uncommitted Changes] ──► Sidebar Indicator shows dot/badge
      │
      │ User clicks indicator ──► Opens Git Sync Dialog
      │
      ├─── Commit action
      │      │
      │      ▼
      │    [Committed Locally (Ahead of Remote)]
      │      │
      │      ▼ Push action
      │    [Pushed to Remote] ──► [Clean Working Copy]
      │
      └─── Pull action (Fetch & Merge)
             │
             ├─── Clean merge / Fast-forward ──► [Reload Collection from YAML] ──► [Clean Working Copy]
             │
             └─── Merge Conflict ──► [Conflicted State]
                                           │
                                           ├── Alert user: "Conflict detected. Resolve externally."
                                           └── Pauses auto-commit until external resolution
```
