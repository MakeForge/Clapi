# Implementation Plan: Git Collection Sync

**Branch**: `009-git-sync` | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/009-git-sync/spec.md`

## Summary

This feature introduces Insomnia-style Git synchronization for OpenCollection documents in Clapi. Each collection can be configured with either "Local" or "Git" storage in a new "Storage" tab in the Collection Editor. For Git storage, Clapi clones/connects the remote repository into `~/.clapi/storage/<repo-id>/` directly invoking the host system's `git` command via `ProcessBuilder`, seamlessly reusing system SSH keys and credential helpers. Each collection lives in an isolated root folder within the Git repository containing `collection.opencollection.yaml`. Proto files and import paths can be selectively toggled to sync into a `protobuf/` subfolder using relative `./protobuf/...` references, while un-synced files preserve absolute paths, with transparent dual-path resolution. In the UI, a bottom-left sidebar Git status indicator displays the branch name and uncommitted changes dot; clicking it opens the Git Sync Dialog to view modified files grouped by collection, write a commit message, commit, push, and pull (with external conflict resolution guidance).

## Technical Context

**Language/Version**: Kotlin 2.1 (JVM 21 / 17 target)

**Primary Dependencies**:
- Host System `git` CLI (invoked via `ProcessBuilder` without third-party Git libraries)
- Compose Multiplatform 1.8.1 (Desktop UI)
- `kotlinx.serialization` & `com.charleskorn.kaml:kaml` (OpenCollection YAML & JSON serialization)
- Kotlin Coroutines & Flows
- Embedded `protoc-jar` and `core-network` gRPC client

**Storage**:
- Git cloned repositories under default storage directory: `~/.clapi/storage/<repo-id>/`
- Per-collection OpenCollection document serialized as `collection.opencollection.yaml`
- Metadata stored in `OpenCollectionDocument.extensions.storage` conforming to OpenCollection v1.0.0
- Synced Protocol Buffer assets under `<collection-folder>/protobuf/`

**Testing**:
- Unit tests for `GitCliExecutor` command parsing and execution in `core-storage`
- Unit tests for `GitRepositoryManager` repository layout and multi-collection isolation in `core-storage`
- Unit tests for `ProtoPathResolver` dual-path resolution in `core-storage` and `core-network`
- Compose UI interaction tests for Storage tab, Git indicator, and Git Sync Dialog in `ui-desktop`
- End-to-end Git sync round-trip and conflict detection tests in `qa-tests`

**Target Platform**: Desktop (Linux, macOS, Windows)

**Project Type**: Multiplatform Desktop Application

**Performance Goals**:
- Git status checks complete asynchronously in <200ms without blocking UI frame rendering.
- Seamless commit and push execution flow in <10 seconds.
- Zero UI freezing during Git clone, fetch, pull, or checkout operations.

**Constraints**:
- Must leverage system Git directly without adding external Git libraries (JGit, etc.).
- Implicit system credential reuse (SSH keys / OS credential helpers).
- No built-in merge conflict resolution tool; conflicts must alert user to resolve externally.
- Strict compliance with OpenCollection v1.0.0 JSON schema.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Library-First)**: Passed. Core Git process management, repository path layout, YAML storage, and proto path resolution are encapsulated in `core-storage` and `core-model` independently from UI components.
- **Principle II (Test-First)**: Passed. Strict unit, integration, and UI test coverage planned across `core-storage`, `ui-desktop`, and `qa-tests`.
- **Principle III (Simplicity & YAGNI)**: Passed. Leverages system Git CLI directly rather than embedding a complex Git library or building custom credential vaults/merge diff tools.
- **Principle IV (OpenCollection Compliance)**: Passed. Stores Git metadata in standard OpenCollection `extensions.storage` and YAML files without proprietary forks.

## Project Structure

### Documentation (this feature)

```text
specs/009-git-sync/
├── plan.md              # Implementation plan
├── research.md          # Technical choices and research findings
├── data-model.md        # Entities, validation rules, state machines
├── quickstart.md        # Runnable validation scenarios
├── contracts/           # Integration contracts
│   ├── git-cli-contract.md
│   ├── opencollection-storage-contract.md
│   └── ui-git-sync-contract.md
├── checklists/
│   └── requirements.md  # Spec quality validation checklist
└── tasks.md             # Generated implementation tasks
```

### Source Code (repository root)

```text
core-model/
└── src/commonMain/kotlin/io/github/makeforge/clapi/core/model/
    └── storage/
        └── StorageExtension.kt          # StorageType, GitStorageConfig, GitCredentialsConfig

core-storage/
├── src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/
    ├── git/
    │   ├── GitCliExecutor.kt            # Interface and data classes for CLI Git calls
    │   ├── GitRepositoryManager.kt      # Clones, branch tracking, multi-collection storage
    │   ├── ProtoPathResolver.kt         # Resolves ./protobuf/... relative vs absolute paths
    │   └── GitStatusModels.kt           # GitFileChange, CollectionGitStatus
    └── opencollection/
        └── OpenCollectionSerializer.kt  # Serializes and deserializes storage extension
└── src/desktopMain/kotlin/io/github/makeforge/clapi/core/storage/
    └── git/
        └── DesktopGitCliExecutor.kt     # ProcessBuilder implementation using host git binary

core-network/
└── src/desktopMain/kotlin/io/github/makeforge/clapi/core/network/grpc/
    └── ProtocJarParser.kt               # Integrates ProtoPathResolver for import and proto files

ui-shared/
└── src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/
    ├── state/
    │   ├── AppState.kt                  # Active Git status, sync state, repository manager
    │   └── ProtoFileItemState.kt        # syncToGit toggle flag
    └── components/
        ├── CollectionEditor.kt          # "Storage" tab (Local vs Git) + Proto sync toggles
        ├── Sidebar.kt                   # Bottom-left Git branch indicator & change dot
        └── GitSyncDialog.kt             # Commit message, changed files, Commit, Push, Pull

ui-desktop/
└── src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/
    └── GitSyncUiTest.kt                 # Compose UI tests for Storage tab & Git Sync Dialog

qa-tests/
└── src/test/kotlin/io/github/makeforge/clapi/qa/
    └── GitSyncE2ETest.kt                # End-to-end Git clone, commit, push, pull test suite
```

## Complexity Tracking

No constitution violations detected. System Git CLI integration eliminates the need for 3rd-party Git libraries, keeping architectural complexity minimal.
