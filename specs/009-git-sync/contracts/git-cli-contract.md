# Contract: Host System Git CLI Integration

**Feature Branch**: `009-git-sync`
**Date**: 2026-11-09
**Status**: Draft

## Purpose

Defines the contract for interacting with the host system's `git` command-line binary. This provides Git operations (clone, status, commit, push, pull) without using third-party Git libraries, preserving system credentials (SSH keys, credential helpers) transparently.

---

## Command Invocation Model

All commands execute against the system `git` binary using JVM `ProcessBuilder` on background I/O dispatchers.

```kotlin
interface GitCliExecutor {
    suspend fun execute(
        workingDir: File?,
        command: List<String>,
        timeoutMillis: Long = 30_000,
    ): GitCommandResult
}

data class GitCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val isSuccess: Boolean get() = exitCode == 0
}
```

---

## Specific Command Contracts

### 1. Version / Availability Check

- **Invocation**: `git --version`
- **Working Dir**: Null (system default)
- **Success Criteria**: Exit code `0`, output starts with `git version`.
- **Failure Handling**: If command fails or binary is not found, throws `GitNotInstalledException("Git CLI is not installed or not found in system PATH.")`.

### 2. Clone Repository

- **Invocation**: `git clone --branch <branch> <remoteUrl> <targetDir>`
  *(If branch does not exist on remote, fallback: `git clone <remoteUrl> <targetDir>` followed by `git checkout -b <branch>`)*
- **Working Dir**: Parent directory of `<targetDir>`
- **Success Criteria**: Exit code `0`. Directory `<targetDir>/.git` exists.
- **Failure Handling**:
  - Exit code `128` with "Permission denied (publickey)": Throws `GitAuthException` with SSH key notice.
  - Exit code `128` with "Repository not found": Throws `GitRemoteNotFoundException`.

### 3. Check Status (Dirty State & Changes)

- **Invocation**: `git status --porcelain=v1 -uall`
- **Working Dir**: Repository clone root directory
- **Output Parsing**:
  - Each line represents a file change:
    - ` M <path>`: Modified
    - `A  <path>`: Added / staged
    - `?? <path>`: Untracked
    - ` D <path>`: Deleted
    - `UU <path>`, `AA <path>`, `DD <path>`: Unmerged / Conflicted
- **Success Criteria**: Exit code `0`.
- **Failure Handling**: If `.git` is locked or invalid, returns empty status with error note.

### 4. Commit Changes

- **Invocation**:
  1. `git add -A` (stages all modified, untracked, and deleted files)
  2. `git commit -m "<message>"`
- **Working Dir**: Repository clone root directory
- **Success Criteria**: Exit code `0`.
- **Special Case**: Exit code `1` with "nothing to commit, working tree clean": Treated as a no-op success.

### 5. Push Changes

- **Invocation**: `git push origin <branch>`
- **Working Dir**: Repository clone root directory
- **Success Criteria**: Exit code `0`.
- **Failure Handling**:
  - Non-fast-forward rejection ("Updates were rejected because the remote contains work..."): User is informed that they must pull first.
  - Network / authentication error: Descriptive error shown to user.

### 6. Pull Changes

- **Invocation**: `git pull origin <branch>`
- **Working Dir**: Repository clone root directory
- **Success Criteria**: Exit code `0`. Files on disk updated.
- **Failure Handling**:
  - Exit code `1` with "CONFLICT (content): Merge conflict in ...":
    - Clapi detects merge conflict.
    - Emits `CollectionGitStatus.hasConflicts = true`.
    - Surfaces dialog warning: "Merge conflicts detected in repository. Please resolve them using an external Git client or editor."
