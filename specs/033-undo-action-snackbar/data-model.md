# Data Model: Undoable Action Snackbar

**Feature**: `033-undo-action-snackbar` | **Date**: 2026-11-21

## Entities & State Models

### 1. `ActionSnackbar` (Transient Notification Model)

Represents the single active snackbar displayed in the bottom-right corner.

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | `Long` | Yes | Monotonically increasing unique identifier assigned on each `show(...)` call. Drives timer restart and race-safe dismissal/action matching. |
| `message` | `String` | Yes | Brief human-readable description of the completed action (e.g., `Deleted "Get Users"`). |
| `actionLabel` | `String?` | No | Interactive button label (e.g., `"Undo"`). When `null`, no action button is rendered. |
| `onAction` | `(() -> Boolean)?` | No | Callback invoked at most once when the action button is clicked. Returns `true` on success or `false` if the undo could not be applied. |
| `durationSecOverride` | `Int?` | No | Optional duration override in seconds; when `null`, uses `AppSettings.snackbarAutoDismissSec`. |
| `isError` | `Boolean` | No (default `false`) | Visual hint used when reporting an undo failure message. |

**Validation / Invariants**:
- `message` must be non-blank; blank messages are ignored.
- If `actionLabel != null`, `onAction` must be non-null (and vice versa).
- `onAction` is guarded so it executes **at most once** per `ActionSnackbar` instance.

---

### 2. `ActionSnackbarController` (Centralized Notification State Holder)

Owned by `AppState` (`val snackbar = ActionSnackbarController()`).

| Property / State | Type | Description |
|---|---|---|
| `current` | `ActionSnackbar?` (Compose `mutableStateOf`) | Currently displayed snackbar, or `null` when none is shown. |
| `nextId` | `Long` (internal counter) | Incremented on every `show` call to uniquely identify each snackbar instance. |
| `consumedIds` / `consumed` flag | `Boolean` (internal) | Tracks whether the current snackbar's action has already been executed. |

**State Transitions**:

```text
[Idle (current == null)]
   │
   ├─ show(...) / showUndoable(...) ──► [Active (current = Snackbar#N, consumed = false)]
   │                                       │
   │                                       ├─ show(...) ──► [Active (current = Snackbar#N+1, consumed = false)]
   │                                       │                (old snackbar immediately dismissed, timer resets)
   │                                       │
   │                                       ├─ triggerAction(N) (if !consumed) ──► consumed = true
   │                                       │     ├─ current = null (dismissed immediately)
   │                                       │     ├─ execute onAction()
   │                                       │     └─ if returns false or throws ──► show(errorMsg, isError = true)
   │                                       │
   │                                       ├─ dismiss(N) (manual close or auto-dismiss timeout) ──► [Idle (current == null)]
   │                                       └─ dismiss(staleId != N) ──► No-op (guards against stale timer race)
```

---

### 3. `NodePlacement` & `DeletedNodeSnapshot` (Undoable Workspace Records)

Lightweight snapshots used to revert tree delete and move operations cleanly.

#### `NodePlacement`
| Field | Type | Description |
|---|---|---|
| `nodeId` | `String` | Unique ID of the request, folder, or collection node. |
| `parentId` | `String?` | Parent folder/collection ID, or `null` if the node lives at the root `collections` list. |
| `index` | `Int` | Zero-based index within the parent's `children` list (or root `collections` list). |

#### `DeletedNodeSnapshot`
| Field | Type | Description |
|---|---|---|
| `node` | `CollectionNode` | The removed node (preserving full nested subtree for folders/collections). |
| `parentId` | `String?` | Original parent folder ID (`null` for root). |
| `index` | `Int` | Original zero-based index in the parent list. |

#### `DeletedEnvironmentSnapshot`
| Field | Type | Description |
|---|---|---|
| `environment` | `EnvState` | The removed environment and all its variables/secrets. |
| `index` | `Int` | Original index in `state.environments`. |
| `previousSelectedEnvIndex` | `Int` | Previously active `state.selectedEnvIndex`. |

#### `DeletedHistorySnapshot`
| Field | Type | Description |
|---|---|---|
| `item` | `HistoryItem` | The removed history entry. |
| `index` | `Int` | Original index in `state.historyItems`. |

---

### 4. `AppSettings.snackbarAutoDismissSec` (Persisted Global Preference)

Added to `AppSettings` and persisted via `SettingsRepository`.

| Field | Storage Key | Type | Default | Constraints |
|---|---|---|---|---|
| `snackbarAutoDismissSec` | `settings.snackbarAutoDismissSec` | `Int` | `5` | Inclusive range `1..60` (clamped on load/save; rejected in UI if out-of-range or non-integer). |
