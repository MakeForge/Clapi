# Contract: UI Components & Interactions for Git Sync

**Feature Branch**: `009-git-sync`
**Date**: 2026-11-09
**Status**: Draft

## Purpose

Defines the contract for the UI surfaces introduced by Git synchronization: the "Storage" tab in `CollectionEditor`, the Git status indicator in the bottom-left of the sidebar, and the Git Sync Dialog.

---

## 1. Collection Editor: "Storage" Tab

### Location & Tab Enum
- Located in `CollectionEditorTab`:
  ```kotlin
  enum class CollectionEditorTab(val label: String) {
      INFO("Info"),
      STORAGE("Storage"),  // New tab
      VARIABLES("Variables"),
      HEADERS("Headers"),
      AUTH("Auth"),
      PRE_REQUEST("Pre-request"),
      TESTS("Tests"),
      PROTOBUF("Protobuf"),
  }
  ```

### Elements & Behavior
- **Storage Type Selector**: Radio buttons or segmented toggle for `Local` vs `Git`.
  - When `Local`:
    - Helper text: "Collection is stored locally on this machine."
    - No additional fields required.
  - When `Git`:
    - Input: `Repository URL` (e.g. `git@github.com:org/repo.git` or `https://github.com/org/repo.git`).
    - Input: `Branch` (defaults to `main`).
    - Note: "Authentication uses your system's Git credentials (SSH agent / credential helper)."
- **Action Button**: "Save Storage Settings".
  - On click: Clones/checks out repository in background, writes OpenCollection YAML to `<repo-root>/<collection-name>/collection.opencollection.yaml`, and updates collection state.
  - Displays progress spinner during cloning, and error banner if Git fails.

---

## 2. Sidebar: Bottom-Left Git Status Indicator

### Location & Visibility
- Positioned in the bottom-left area of the sidebar footer.
- Visible whenever the currently selected or active collection has Git sync configured.

### Visual State
- **Icon**: Git branch icon (or Material `Source` / `ForkRight` icon).
- **Branch Label**: Displays current branch name (e.g. `main`).
- **Dirty Indicator**: A subtle accent dot or badge indicating uncommitted modifications exist.
- **Click Behavior**: Left-click opens the Git Sync Dialog.

---

## 3. Git Sync Dialog

### Dialog Properties & Header
- Title: "Git Sync - [Repository Name / Branch]"
- Subtitle: "Synchronize collections with remote repository"

### Content Areas
1. **Uncommitted Changes Section**:
   - Lists modified/added/deleted files grouped by collection name.
   - Example:
     ```text
     ▼ Payment API (2 changes)
       • collection.opencollection.yaml (Modified)
       • protobuf/collection/payment.proto (Added)
     ```
2. **Commit Section**:
   - Multi-line text field: "Commit message (required to commit)..."
3. **Actions Footer**:
   - **Pull Button**: Fetches remote changes and merges into the local clone. If conflicts occur, shows an alert: "Merge conflict detected. Please resolve conflicts using an external Git client."
   - **Commit Button**: Disabled if commit message is blank or no changes exist. Stages all files and commits locally.
   - **Push Button**: Pushes local commits to the remote branch.
   - **Close Button**: Dismisses the dialog.
