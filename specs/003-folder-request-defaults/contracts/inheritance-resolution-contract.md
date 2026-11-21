# Interface Contract: Inheritance and Resolution Semantics

**Feature Branch**: `003-folder-request-defaults`
**Date**: 2026-11-07
**Status**: Completed

## 1. Authentication Inheritance

### 1.1 Resolution Rules
- When a request has `tab.authType == AuthType.INHERIT`:
  - Traverse ancestors up `tab.folderPath` from parent towards root.
  - The first ancestor folder with a concrete `authType != null && authType != AuthType.NONE && authType != AuthType.INHERIT` provides the effective credentials.
  - If no ancestor specifies concrete authentication, effective auth resolves to `AuthType.NONE`.
- When a request specifies an explicit auth type (`BASIC`, `BEARER`, `API_KEY`, `NONE`):
  - Request-level configuration overrides folder configuration completely.

### 1.2 Auth Tab UI Contract
- In `AuthEditor`:
  - If `tab.folderPath.isNotEmpty()` or `tab.collectionId != null`:
    - Display `INHERIT` chip in the auth selector list.
  - When `INHERIT` is active:
    - Display a banner stating: `Inheriting from folder [Name]` along with the resolved auth type and read-only configuration details.
    - If resolved auth is `NONE`: Display `Folder has no authentication configured`.

---

## 2. Header and Parameter Inheritance

### 2.1 Display Contract
- When a request tab loads or when folder defaults change:
  - Ancestor folder default headers that are enabled are marked with `kind = HeaderKind.INHERITED`.
  - In `KeyValueEditor`:
    - Rows with `HeaderKind.INHERITED` display an `"Inherited"` badge in the `Type` column.
    - Checkbox, key, and value fields on inherited rows are read-only to prevent ambiguous edits.
    - A tooltip explains: `"Inherited from folder defaults. Edit in folder settings."`

### 2.2 Wire Execution Contract
- During HTTP request dispatch in `RequestExecutor`:
  - Key matching is case-insensitive (e.g. `authorization` matches `Authorization`).
  - If a user-defined request header (`HeaderKind.USER`) matches an inherited header key:
    - The user-defined header value is sent.
    - The inherited header value is suppressed/overridden.
  - Inherited headers that are not overridden by the request are appended to the wire payload.

---

## 3. Script Chaining Contract

### 3.1 Pre-Request Script Order
- Hierarchical top-down order:
  1. Ancestor folder pre-request scripts (root-to-leaf order).
  2. Request pre-request script (`tab.preRequestScript`).
- Each script receives the updated variable state emitted by preceding scripts.
- If any script triggers `execution.skipRequest()`, dispatch terminates immediately.

### 3.2 Post-Response / Test Script Order
- Hierarchical inside-out order:
  1. Request post-response script (`tab.testScript`).
  2. Ancestor folder post-response scripts (leaf-to-root order).
- Any runtime execution concerns or asynchronous limitations are documented with explicit `// TODO:` annotations in `RequestExecutor.kt`.
