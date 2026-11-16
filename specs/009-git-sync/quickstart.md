# Quickstart & Validation Guide: Git Collection Sync

**Feature Branch**: `009-git-sync`
**Date**: 2026-11-09
**Status**: Draft

## Prerequisites

1. System Git installed (`git --version` returns 2.x+).
2. SSH keys or Git credential helper configured for remote testing (or a local bare repository for test runs).

---

## Scenario 1: Configure Collection Git Storage

### Objective
Verify that a user can switch a collection from local storage to Git storage and that the collection is cloned and exported into `~/.clapi/storage/`.

### Steps
1. Launch Clapi (`./gradlew :ui-desktop:run`).
2. In the sidebar, select a collection (or create a new collection).
3. In the Collection Editor, navigate to the **Storage** tab.
4. Select **Git** storage.
5. Provide a valid Git repository URL (e.g. `file:///tmp/test-git-remote.git` or SSH/HTTPS URL) and branch name `main`.
6. Click **Save**.

### Expected Results
- Clapi clones or checks out the repository into `~/.clapi/storage/repo_<hash>/`.
- The collection directory `~/.clapi/storage/repo_<hash>/<collection-name>/` is created containing `collection.opencollection.yaml`.
- The collection editor displays a success notification and the sidebar bottom-left indicator shows `main`.

---

## Scenario 2: Make Changes, Commit & Push

### Objective
Verify that modifying requests marks the repository dirty and allows committing and pushing from the Git Sync Dialog.

### Steps
1. In the Git-synced collection, add a new HTTP request or edit an existing request.
2. Save changes (or allow autosave).
3. Observe the bottom-left corner of the sidebar: the Git branch indicator displays an uncommitted change dot.
4. Click the Git branch indicator to open the **Git Sync Dialog**.
5. Observe the uncommitted changes list showing `collection.opencollection.yaml`.
6. Enter commit message: `Add new endpoint`.
7. Click **Commit**.
8. Click **Push**.

### Expected Results
- `git status` in the repository shows working tree clean.
- Remote repository contains the new commit with the updated YAML document.
- Sidebar indicator returns to clean state without the dirty dot.

---

## Scenario 3: Selective Protobuf File Sync

### Objective
Verify that toggling "Sync to Git" copies `.proto` files into `<collection-dir>/protobuf/` and uses relative `./protobuf/` paths, while un-synced files retain absolute paths.

### Steps
1. In the Git-synced collection, open the **Protobuf** tab.
2. Add a `.proto` file from the local filesystem.
3. Toggle ON the **Sync to Git** switch for that proto file.
4. Add a second `.proto` file and leave **Sync to Git** OFF.
5. Save the collection.
6. Inspect `~/.clapi/storage/repo_<hash>/<collection-name>/protobuf/collection/`.
7. Inspect `collection.opencollection.yaml`.
8. Execute a gRPC request referencing the proto schema.

### Expected Results
- First proto file is physically copied into the repository's `protobuf/collection/` folder.
- In `collection.opencollection.yaml`, the first proto is saved with relative path `./protobuf/collection/...`, while the second proto has its absolute local path.
- The gRPC request executes successfully, proving `ProtoPathResolver` correctly resolves both relative and absolute paths.

---

## Scenario 4: Pull and Conflict Handling

### Objective
Verify that remote changes can be pulled, and that merge conflicts trigger an alert prompting external resolution.

### Steps
1. Push a remote commit externally modifying a conflicting field in `collection.opencollection.yaml`.
2. In Clapi, make a conflicting local edit to the collection.
3. Open the **Git Sync Dialog** and click **Pull**.

### Expected Results
- Clapi detects the merge conflict from Git.
- An alert is displayed: *"Merge conflict detected. Please resolve conflicts using an external Git client."*
- Clapi does not crash or overwrite either version destructively.
