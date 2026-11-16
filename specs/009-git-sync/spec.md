# Feature Specification: Git Collection Sync

**Feature Branch**: `009-git-sync`

**Created**: 2026-11-09

**Status**: Draft

**Input**: User description: "Implement git sync similar to Insomnia
- OpenCollection document YAML should be synced to git
- Store git sync properties in OpenCollectionDocument.extensions in something like structure:  
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
  For now we only support using system's git credentials implicitly
- For collection not using git sync "extensions.storage.type" is simply set to "local", no other info needed
- For git its required that git is setup on the system, so Clapi can simply leverage git directly without 3rd party library and also not having to manage git credentials
- Git repo should be cloned by clapi into Clapi's default storage folder in "storage" subfolder
- In the git repo there should be a root folder per collection, in which we then store the OpenCollection document and anything else needed for the collection
- For collection-level and request proto files, if git sync is enabled, there should be a toggle to sync the individual proto files into the git sync folder (protobuf subfolder) or not. We have to come up with a folder structure for that, and figure out how to then store the proto file info (path) properly so resolving the proto file works correctly with files copied into the git sync folder and proto files from local computer (for request/collection-level proto files and collection-level `importPaths`)
- In the UI:
  - in the CollectionEditor we need new Tab "Storage", in which user can choose type local or git:
    - for local no other fields necessary
    - for git: input field for remoteUrl + branch
    - Save button
  - If git sync is activated:
    - in the bottom left corner of the sidebar there should should be git indicator with branch name and little indicator if there are changes that can be commited
    - if indicator is clicked, dialog opens, which shows for which collection there are changes for which files and input field for commit message + commit + push buttons (not git diff view needed yet)
    - in the dialog there should also be pull button to fetch changes (on conflicts, simply tell user he has to resolve them externally, clapi will not offer conflict resolution for now)"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Configure Collection Storage in Collection Editor (Priority: P1)

Users need to choose where and how a collection is stored and synchronized. In the Collection Editor, a dedicated "Storage" tab allows the user to switch between "Local" storage and "Git" synchronization. For "Local" storage, no extra configuration is required. For "Git" synchronization, the user enters a remote repository URL (supporting HTTPS or SSH) and a target branch name (e.g., `main`). Clicking "Save" initiates repository cloning or checkout into Clapi's local storage directory, saves the OpenCollection document in YAML format inside a dedicated collection folder in the repository, and updates the collection document's storage extension metadata.

**Why this priority**: Configuring storage and connecting a collection to a remote Git repository is the core capability that enables Git synchronization.

**Independent Test**: Open a Collection Editor, navigate to the "Storage" tab, select "Git", enter a repository URL and branch, and click "Save". Verify that the repository is cloned locally and the collection is exported as YAML into the repository.

**Acceptance Scenarios**:

1. **Given** an open Collection Editor, **When** the user views the editor tabs, **Then** a "Storage" tab is available.
2. **Given** the "Storage" tab, **When** selected, **Then** the user can choose between storage types "Local" and "Git".
3. **Given** "Local" storage is selected, **When** viewing the tab, **Then** no Git-specific input fields are displayed and `extensions.storage.type` is set to `"local"`.
4. **Given** "Git" storage is selected, **When** viewing the tab, **Then** input fields for `remoteUrl` and `branch`, and a "Save" button are displayed.
5. **Given** valid Git repository information, **When** the user clicks "Save", **Then** Clapi clones or checks out the repository into `~/.clapi/storage/`, creates a root folder for the collection, writes the OpenCollection YAML document inside it, and records the configuration under `extensions.storage`.
6. **Given** invalid Git repository information or system Git is unavailable, **When** the user clicks "Save", **Then** an informative error message is displayed and the previous configuration is retained.

---

### User Story 2 - Git Status Indicator and Sync Dialog (Priority: P1)

Once Git sync is activated for a collection, the user needs constant visibility into synchronization status and straightforward controls to commit, push, and pull changes without leaving the application. A status indicator is placed in the bottom-left corner of the sidebar showing the active Git branch name and a change indicator dot or badge when uncommitted modifications exist. Clicking the indicator opens a Git Sync dialog listing changed files grouped by collection, a commit message input field, and buttons to "Commit", "Push", and "Pull". When pulling changes that result in a merge conflict, Clapi warns the user that conflicts must be resolved externally.

**Why this priority**: Users need visual feedback on dirty working states and immediate actions to commit, push, and pull their changes to collaborate with team members.

**Independent Test**: Make a change to a Git-synced collection (e.g., add or edit a request), verify the sidebar Git indicator shows uncommitted changes, click the indicator to open the Sync dialog, enter a commit message, click Commit, click Push, and test Pull functionality.

**Acceptance Scenarios**:

1. **Given** a collection with Git sync activated, **When** viewing the sidebar, **Then** a Git status indicator appears in the bottom-left corner displaying the current branch name.
2. **Given** modifications have been made to a Git-synced collection, **When** viewing the sidebar Git indicator, **Then** a change indicator (dot or badge) signifies uncommitted changes exist.
3. **Given** the Git indicator is clicked, **When** activated, **Then** the Git Sync dialog opens showing the affected collections and modified files.
4. **Given** uncommitted changes in the Git Sync dialog, **When** the user enters a commit message and clicks "Commit", **Then** changes are committed to the local Git repository using system Git.
5. **Given** committed local changes, **When** the user clicks "Push", **Then** changes are pushed to the remote repository.
6. **Given** remote changes, **When** the user clicks "Pull", **Then** changes are fetched and merged into the local repository and the collection reloads.
7. **Given** a pull operation that results in a merge conflict, **When** the conflict occurs, **Then** Clapi alerts the user that conflicts occurred and must be resolved externally using an external Git client.

---

### User Story 3 - Selective Protobuf File Sync with Dual-Path Resolution (Priority: P2)

When working with gRPC requests and collections containing Protocol Buffer files, users may want some `.proto` files to be committed into the Git repository (shared with teammates) while keeping other `.proto` files local to their workstation (e.g., machine-specific development paths). Each collection-level proto file, collection-level import path, and request-level proto file provides a "Sync to Git" toggle when Git sync is active. When toggled on, the file is copied into the collection's `protobuf/` subfolder in the Git repository and referenced via relative path (`./protobuf/...`). When toggled off, the file retains its absolute local filesystem path. Clapi transparently resolves both relative paths and absolute local paths when parsing schemas or executing gRPC requests.

**Why this priority**: Essential for gRPC API workflows to allow selective sharing of `.proto` schemas across team members while preserving local machine flexibility.

**Independent Test**: Enable Git sync for a collection with gRPC requests, toggle "Sync to Git" for a proto file, verify that the file is copied into the repository's `protobuf/` directory and referenced relatively in YAML. Execute a gRPC request to verify path resolution succeeds for both synced relative files and un-synced absolute files.

**Acceptance Scenarios**:

1. **Given** a collection with Git sync enabled, **When** viewing collection-level proto files, import paths, or request-level proto files, **Then** each item displays a "Sync to Git" toggle switch.
2. **Given** the "Sync to Git" toggle is enabled for a proto file, **When** saved, **Then** the file is copied into the repository under `<collection-folder>/protobuf/` and stored in the collection document with a relative path starting with `./protobuf/`.
3. **Given** the "Sync to Git" toggle is disabled for a proto file, **When** saved, **Then** the file remains stored with its absolute local filesystem path.
4. **Given** an executed gRPC request, **When** resolving proto files or import paths, **Then** relative paths are resolved against the collection's Git directory and absolute paths are resolved against the system filesystem.
5. **Given** a collection without Git sync (storage type "local"), **When** viewing proto files, **Then** the "Sync to Git" toggle is hidden or disabled.

---

### User Story 4 - Multi-Collection Git Repository Organization (Priority: P3)

Multiple collections within a workspace can share the same remote Git repository or link to independent repositories. Within a shared Git repository, each collection is stored in its own distinct root subfolder. When navigating between collections in the sidebar or editor tabs, the sidebar Git indicator updates to reflect the active collection's repository and branch. When opening the Git Sync dialog, changed files from all collections belonging to that repository are clearly presented with their respective collection names.

**Why this priority**: Enables organizing related microservices or APIs in a single monorepo repository while keeping individual collections isolated and clean.

**Independent Test**: Configure two collections to point to the same remote Git repository. Make changes to both collections. Open the Git Sync dialog and verify that changes for both collections are displayed and committed together.

**Acceptance Scenarios**:

1. **Given** two collections configured with the same remote Git repository and branch, **When** stored, **Then** both collections reside in separate subfolders within the same cloned repository directory in `~/.clapi/storage/`.
2. **Given** changes in multiple collections within the same repository, **When** the Git Sync dialog opens, **Then** changes are grouped by collection name and can be committed and pushed in a single action.
3. **Given** multiple collections where some are Git-synced and some are local, **When** switching focus between collections in the UI, **Then** the sidebar Git indicator shows the status of the currently active collection or remains visible if any open collection is Git-synced.

---

### Edge Cases

- **System Git Not Installed or Accessible**: If the `git` command is not available in the system PATH, Clapi surfaces an actionable error notifying the user that Git is required and must be installed on their system.
- **Git Authentication Failure**: When cloning, pushing, or pulling using system credentials (SSH keys or credential helpers) fails, Clapi captures the error output from Git and displays a clear message without leaking sensitive data.
- **Empty or Non-Existent Remote Branch**: If the specified branch does not yet exist on the remote repository, Clapi handles branch creation and initial push gracefully.
- **Merge Conflicts on Pull**: If `git pull` results in conflicting files, Clapi immediately aborts or preserves the conflict markers, informs the user via dialog that a merge conflict occurred, and prompts the user to resolve the conflict externally using their Git client before syncing again.
- **Offline / Network Disconnection**: If network access is lost while pushing or pulling, Clapi displays a network timeout/connectivity error and allows local commits to remain safely queued in the local Git clone.
- **Renaming a Git-Synced Collection**: When a user renames a collection, the corresponding folder in the Git repository and the OpenCollection document info are updated, and the file move/rename is tracked in the uncommitted changes list.
- **External Git Repository Modifications**: When files in the cloned Git directory are modified or pulled externally, Clapi reloads the updated collection document without crashing or discarding unsaved in-memory edits.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST support configuring collection storage type as either `"local"` or `"git"` in the Collection Editor under a dedicated "Storage" tab.
- **FR-002**: System MUST persist storage configuration in `OpenCollectionDocument.extensions.storage` using the specified format (`type: "local"` or `type: "git"` with `remoteUrl`, `branch`, and `credentials: { type: "system" }`).
- **FR-003**: System MUST execute the host system's `git` binary directly for all Git operations without third-party Git libraries, relying on system-configured SSH keys and credential helpers.
- **FR-004**: System MUST clone Git repositories into a subfolder named `storage` inside Clapi's default application storage directory (`~/.clapi/storage/`).
- **FR-005**: System MUST maintain an isolated directory per collection within the Git repository root containing the collection's OpenCollection document in YAML format (`collection.opencollection.yaml`).
- **FR-006**: System MUST provide an uncommitted changes indicator and branch name display in the bottom-left corner of the sidebar whenever a Git-synced collection is active.
- **FR-007**: System MUST provide a Git Sync dialog accessible by clicking the sidebar Git indicator, displaying modified collections, changed files, commit message input, "Commit" button, "Push" button, and "Pull" button.
- **FR-008**: System MUST support pulling remote updates into the local repository clone and reloading the modified collection into the workspace.
- **FR-009**: System MUST notify the user when a pull operation encounters merge conflicts and instruct them to resolve conflicts externally with a Git client.
- **FR-010**: System MUST provide a "Sync to Git" toggle for collection-level proto files, collection import paths, and request-level proto files when Git sync is active.
- **FR-011**: System MUST copy proto files marked for Git sync into a `protobuf` subfolder inside the collection's repository directory and reference them via relative paths (`./protobuf/...`) in the OpenCollection document.
- **FR-012**: System MUST preserve absolute local filesystem paths for proto files and import paths where "Sync to Git" is toggled off.
- **FR-013**: System MUST transparently resolve both relative repository paths and absolute local filesystem paths when loading proto files for gRPC request execution and schema parsing.
- **FR-014**: System MUST support multiple collections mapped to the same Git repository and branch, grouping them by collection in the Git Sync dialog.
- **FR-015**: System MUST display meaningful error messages when Git operations fail (e.g., authentication failed, host unreachable, branch not found, Git binary missing).

### Key Entities

- **StorageConfig**: Configuration stored in `OpenCollectionDocument.extensions.storage`. Defines storage type (`"local"` or `"git"`) and Git metadata (`remoteUrl`, `branch`, `credentials`).
- **GitRepositoryClone**: Represents a local Git working copy cloned into `~/.clapi/storage/<repo-identifier>/`, containing one or more collection root folders.
- **CollectionGitState**: Tracks working directory status for a collection, including branch name, count of modified files, uncommitted changes list, and sync state (ahead, behind, clean, conflicted).
- **GitSyncProtoFile**: Protocol Buffer file definition associated with a collection or request, with an attribute indicating whether it is synchronized to the Git repository or referenced locally.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can configure and initialize Git sync for an existing collection in less than 30 seconds given an accessible Git repository URL.
- **SC-002**: 100% of collection modifications (folders, requests, environments, settings) made in a Git-synced collection are accurately reflected in the collection's YAML document inside the Git repository.
- **SC-003**: Users can commit and push changes directly from the Clapi sidebar interface in a single interaction flow taking under 10 seconds (excluding remote network transfer time).
- **SC-004**: gRPC requests referencing both Git-synced proto files (relative paths) and local computer proto files (absolute paths) execute without path resolution errors across 100% of test cases.
- **SC-005**: Pull operations with merge conflicts cleanly alert the user without crashing, corrupting the collection document, or losing uncommitted local edits.

## Assumptions

- The host system has a functional Git command-line tool (`git`) installed and reachable in the environment `PATH`.
- Git authentication (such as SSH agents, SSH keys in `~/.ssh/`, or Git credential managers for HTTPS) is configured at the system level; Clapi does not prompt for or manage passwords/tokens directly in this version.
- OpenCollection documents in Git repositories are stored in standard YAML format with the filename `collection.opencollection.yaml` within the collection's directory.
- For collections sharing a repository, collection directories are named using the sanitized collection name or collection identifier to prevent naming collisions.
- Conflict resolution is performed outside of Clapi using external tools or editors; Clapi detects conflict status and pauses synchronization until the user completes conflict resolution.
