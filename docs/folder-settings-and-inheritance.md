# Folder Settings and Request Defaults Inheritance

This document describes the architectural design and runtime rules for folder settings and request defaults inheritance in Clapi.

---

## 1. Overview & Terminology

- **Folders in UI**: In the Clapi sidebar, menus, and item navigation, organizational containers are labeled as **Folders** (or **Folder Items**).
- **Dedicated Folder Settings Tab**: Clicking a folder's title in the sidebar opens a dedicated **Folder Settings** editor tab (represented internally as `RequestKind.FOLDER`), identified with a distinct folder icon in the editor tab bar.
- **Focus Preservation**: Clicking an already-open folder in the sidebar focuses its existing tab rather than creating a duplicate.
- **Configurable Defaults**: A folder's settings view allows developers to configure shared request defaults across five sub-tabs:
  1. **Params**: Query parameters automatically cascaded to child requests.
  2. **Headers**: Default HTTP headers cascaded to child requests.
  3. **Auth**: Inheritable authentication credentials (`Basic`, `Bearer`, `API Key`, `JWT`).
  4. **Pre-request Script**: Script executed prior to dispatching any descendant request.
  5. **Tests (Post-request Script)**: Assertions and post-response script executed after any descendant request receives a response.

---

## 2. Request Defaults Inheritance Model

### 2.1 Header and Query Parameter Inheritance

#### Resolution Order
In multi-level folder hierarchies (e.g., `Root Folder -> Child Folder -> Grandchild Folder -> Request`), default settings cascade from **root to leaf**:
1. Distant ancestor defaults are loaded first.
2. Closer child folder defaults override or supplement ancestor defaults if identical keys collide (case-insensitive comparison).
3. The request's own explicitly configured headers and query parameters take highest precedence on the wire.

#### Visual Distinction & Read-Only Locks
- In request tabs, inherited headers and query parameters are tagged with `HeaderKind.INHERITED`.
- In `KeyValueEditor`, inherited rows display an **"Inherited"** badge in the `Type` column.
- The checkbox, key, and value fields for inherited entries are locked as **read-only** in request views with contextual tooltips directing the user to edit the parent folder's settings.
- Requests do not persist inherited entries as their own; snapshot dirty tracking ignores inherited rows.

#### Wire Dispatch Overrides
- If a request defines a header or parameter matching an inherited key (case-insensitive), the request-level value is sent on the wire, and the inherited value is suppressed.
- Non-colliding inherited headers and parameters are automatically appended to the wire request.

---

## 2.2 Authentication Inheritance

- **`AuthType.INHERIT` Option**: Any request or nested folder can select `AuthType.INHERIT` ("Inherit from parent").
- **Ancestor Traversal**: During authentication resolution, the engine climbs the folder hierarchy from direct parent up towards the root. The closest ancestor configuring concrete authentication (`BASIC`, `BEARER`, `API_KEY`, `JWT`) provides the effective credentials.
- **Fallthrough**: If no ancestor configures authentication, effective auth resolves to `AuthType.NONE`.
- **Explicit Override**: Explicitly configuring a concrete auth method on a request or child folder completely overrides any ancestor folder authentication defaults.
- **UI Banner**: When `INHERIT` is active, `AuthEditor` displays an informational banner:
  - If resolved: `Inheriting from folder [FolderName]: [AuthType]` along with read-only credential indicators.
  - If unresolved: `Folder has no authentication configured`.

---

## 2.3 Script Chaining Semantics

### Pre-Request Script Chain (Root-to-Leaf)
1. Pre-request scripts run hierarchically from top to bottom:
   - Ancestor folder pre-request scripts execute first, in root-to-leaf order.
   - The leaf request's pre-request script executes last.
2. **Context Threading**: Each script receives the updated variable state emitted by preceding scripts (`variables`, `requestHeaders`, `requestQueryParams`, `requestBody`, and `url`).
3. **Early Exit**: If any script calls `clapi.execution.skipRequest()` (or `pm.execution.skipRequest()`), dispatch terminates immediately and no subsequent scripts or HTTP requests are issued.

### Post-Request / Test Script Chain (Leaf-to-Root)
1. Post-response scripts execute inside-out:
   - The leaf request's test script executes first.
   - Ancestor folder test scripts execute sequentially in leaf-to-root order.
2. Assertions across all executed scripts are aggregated into the test results summary and network event logs.

### Script Scoping & Error Handling Notes
Current implementations thread shared context sequentially. Future revisions may address:
- **Variable Isolation**: Preventing parent folder scripts from unintentionally clobbering request-private variables.
- **Error Boundaries**: Configurable behavior (e.g. `FAIL_FAST` vs `BEST_EFFORT`) when an ancestor folder script throws an exception.

---

## 3. Storage & OpenCollection Compatibility

Folder request defaults are stored in strict compliance with the **OpenCollection 1.0.0** specification:
- Folder headers, authentication, and scripts map directly to `FolderItem.request` (`RequestDefaults`).
- Folder query parameters and metadata map to `FolderItem.extensions["clapi"]`.
- When exporting a collection to OpenCollection JSON or YAML, folder request defaults roundtrip cleanly without data loss.
