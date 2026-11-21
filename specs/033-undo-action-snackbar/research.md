# Phase 0 Research: Undoable Action Snackbar

**Feature**: `033-undo-action-snackbar` | **Date**: 2026-11-21

## Research Summary

All technical unknowns were resolved by inspecting Clapi's `ui-shared`, `ui-desktop`, and `docs` modules. No external dependencies need to be added; everything builds on existing Kotlin Multiplatform, Compose Multiplatform (Material 3), Kotlinx Coroutines, and `PlatformStorage` primitives.

---

### 1. Centralized Snackbar Architecture & Single-Snackbar Guarantee

- **Decision**: Introduce a dedicated `ActionSnackbarController` owned by `AppState` (`state.snackbar`) holding a single Compose-observable `current: ActionSnackbar?` slot with a monotonically increasing `id: Long`, rendered by a single `ActionSnackbarHost` composable anchored at `Alignment.BottomEnd` inside a root `Box` wrapping `MainScreen`.
- **Rationale**:
  - `MainScreen` is the shared root shell for Desktop (and any future targets), so mounting `ActionSnackbarHost` once at the root ensures every screen/sidebar/tab action shares the exact same bottom-right overlay without duplicating UI hosts.
  - Using a single `current: ActionSnackbar?` state slot (instead of Material 3's queued `SnackbarHostState`) guarantees **immediate replacement** when a new snackbar arrives while one is visible (FR-004, SC-003), with zero queuing delay and zero possibility of two snackbars rendering simultaneously.
  - A monotonically increasing `id` ensures that even two consecutive identical messages cancel the previous `LaunchedEffect(active?.id, autoDismissSec)` timer and start a fresh countdown.
  - Avoiding crossfade exit animations between two distinct snackbars (switching immediately on `id` change) guarantees 0% overlap frames.
- **Alternatives considered**:
  - *Material 3 `SnackbarHostState`*: Designed around a FIFO queue (`showSnackbar` suspends until previous finishes or requires manual `currentSnackbarData?.dismiss()` from a coroutine scope). Harder to invoke synchronously from plain state/UI callbacks and awkward to test deterministically in `commonTest`. Rejected in favor of a lightweight, synchronous-API `ActionSnackbarController`.
  - *Window-level Swing/AWT popup*: Breaks Compose Multiplatform sharing and complicates UI testing with `createComposeRule()`. Rejected.

---

### 2. Once-Only Undo Execution & Failure Handling

- **Decision**: Encapsulate undo invocation inside `ActionSnackbarController.triggerAction(id)` with a per-snackbar `consumed` guard and `runCatching` error handling. When an undo callback throws or returns `false` (indicating target conflict/missing parent), display a non-undoable error snackbar (or log + error message) without mutating/corrupting state.
- **Rationale**:
  - Prevents rapid double-clicks on **Undo** or timer-expiration races from executing the inverse operation twice (FR-005, edge cases).
  - Keeps call sites clean: callers register a message and an `onUndo: () -> Boolean` (or `() -> Unit`) lambda via `state.snackbar.showUndoable(...)`.
- **Alternatives considered**:
  - *Per-call-site boolean guards*: Repetitive and error-prone across 10+ action sites. Rejected.

---

### 3. Tree Position Snapshotting for Delete & Move Undo

- **Decision**: Add lightweight tree-location helpers (`NodePlacement`, `captureNodePlacements`, `restoreNodePlacements`, `reinsertNodeAt`) alongside existing `SidebarCollectionHelpers.kt` (or a new `UndoableWorkspaceActions.kt` in `ui-shared/.../state/`).
  - **Delete Undo**: Before deleting a request, folder, collection, environment, or history item, capture its parent container ID (`null` for root collections), original index, and deep node reference (note: `CollectionNode` is already detached intact by `deleteFolderFromCollections` / `deleteCollection`; for requests we capture the removed `CollectionNode`). On Undo, re-insert into the parent container (clamped to `0..children.size`), call `notifyCollectionsChanged()`, and persist workspace asynchronously.
  - **Move Undo**: Before executing any move (`moveRequestInCollections`, `moveRequestsBeforeRequest`, `moveRequestsAfterRequest`, `moveRequestsToCollection`, `moveFoldersBeforeFolder`, `moveFoldersAfterFolder`), capture each moved node's `(nodeId, parentId, index)`. After the move, compare post-move locations to pre-move locations: if unchanged (no-op per FR-010), do **not** show a snackbar. Otherwise, show the snackbar whose Undo extracts the moved nodes and re-inserts them into their captured `(parentId, index)` positions in ascending index order.
  - **Rename Undo**: Capture `oldName` before renaming (collection, folder, request, or environment). If `actualNewName == oldName` (no-op), do not show a snackbar. Otherwise, show the snackbar whose Undo invokes the existing rename function back to `oldName` (without re-triggering a new snackbar—undo itself does not spawn another undo snackbar).
- **Rationale**:
  - Works uniformly for single-item and multi-selection deletes and moves (both requests and folders/collections).
  - Pure Kotlin helpers operating on `MutableList<CollectionNode>` are 100% unit-testable in `ui-shared:commonTest` without requiring a UI harness.
- **Alternatives considered**:
  - *Full deep-copy of the entire `state.collections` list before every edit*: High memory overhead on large workspaces and could silently revert unrelated concurrent changes. Targeted placement snapshots are minimal, fast, and precise.

---

### 4. Auto-Dismiss Setting Persistence & General Settings UI

- **Decision**:
  - Add `var snackbarAutoDismissSec by mutableStateOf(DEFAULT_SNACKBAR_AUTO_DISMISS_SEC)` to `AppSettings` with constants `DEFAULT_SNACKBAR_AUTO_DISMISS_SEC = 5`, `MIN_SNACKBAR_AUTO_DISMISS_SEC = 1`, `MAX_SNACKBAR_AUTO_DISMISS_SEC = 60`, and copy it in `AppSettings.withNetworkOverrides()`.
  - Persist under `"settings.snackbarAutoDismissSec"` in `SettingsRepository.load()` and `SettingsRepository.save()`, clamping with `.coerceIn(MIN_SNACKBAR_AUTO_DISMISS_SEC, MAX_SNACKBAR_AUTO_DISMISS_SEC)`.
  - Include `snackbarAutoDismissSec` in `MainScreen.kt`'s settings `snapshotFlow` fingerprint so edits persist automatically via debounced `SettingsRepository.save`.
  - Extend `SettingNumberField` in `SettingControls.kt` with optional `description: String = ""`, `minValue: Int? = null`, `maxValue: Int? = null`, and `tag: String = ""` parameters so invalid/out-of-range numbers show a red border and are not committed.
  - Add the control in `GeneralSettingsPane.kt` (`GeneralSettings`) with localized strings in `Strings.kt` and all 4 translation bundles (`en.json`, `de.json`, `es.json`, `fr.json`).
- **Rationale**:
  - Follows Clapi's exact existing patterns for `defaultTimeoutSec` and `mcpServerPort` (`coerceIn`), avoiding new persistence mechanisms or custom controls.
- **Alternatives considered**:
  - *Dropdown of preset durations (3s, 5s, 10s)*: Less flexible than a bounded numeric field (1–60s) and inconsistent with `default_request_timeout_seconds` in `GeneralSettingsPane`. Rejected.

---

### 5. Documentation Update (`docs/settings/settings.md`)

- **Decision**: Update `docs/settings/settings.md` under `### 1. General & Appearance` to document **Snackbar Auto-Dismiss Duration** (default: 5 seconds, range: 1–60 seconds), explaining that it controls how long bottom-right undo snackbars remain visible after delete, move, or rename actions.
- **Rationale**: Explicitly requested by the user and keeps the VitePress documentation accurate with the General Global Settings UI.
