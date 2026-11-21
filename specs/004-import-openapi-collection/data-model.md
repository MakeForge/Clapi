# Data Model: OpenAPI & OpenCollection Import Dialog

**Feature**: OpenAPI & OpenCollection Import Dialog
**Feature Directory**: `specs/004-import-openapi-collection`
**Date**: 2026-11-07

## 1. Entities

### 1.1 ImportSourceType
Enum indicating the origin of the import payload.

```kotlin
enum class ImportSourceType {
    FILE,
    CLIPBOARD
}
```

### 1.2 ImportSourceState
Encapsulates the staged source before scanning.

| Field | Type | Description |
|---|---|---|
| `type` | `ImportSourceType` | Active tab in source selection (`FILE` or `CLIPBOARD`). |
| `fileName` | `String?` | Display name of selected file (null if Clipboard or no file). |
| `rawContent` | `String?` | Text content read from file or clipboard. |
| `isValidScanSource` | `Boolean` | Computed property: true if `FILE` has loaded content or `CLIPBOARD` has non-empty text. |

### 1.3 ImportCandidateFolder
Represents a parsed folder candidate discovered during the scanning process.

| Field | Type | Description |
|---|---|---|
| `id` | `String` | Stable unique identifier for the candidate item in UI session. |
| `originalName` | `String` | Original folder name extracted from the specification. |
| `name` | `String` | Current name (mutable by user rename action). |
| `node` | `CollectionNode` | The converted `CollectionNode` containing requests and nested subfolders. |
| `requestCount` | `Int` | Total number of HTTP requests contained within this folder hierarchy. |
| `selected` | `Boolean` | Whether this folder is marked for import. |
| `hasConflict` | `Boolean` | Computed property: true if `name` matches an existing folder in `state.collections`. |

### 1.4 ImportCandidateEnvironment
Represents a parsed environment candidate discovered during scanning.

| Field | Type | Description |
|---|---|---|
| `id` | `String` | Stable unique identifier for the candidate item in UI session. |
| `originalName` | `String` | Original environment name extracted from the specification. |
| `name` | `String` | Current name (mutable by user rename action). |
| `env` | `EnvState` | The converted `EnvState` containing key-value variables. |
| `variableCount` | `Int` | Total number of variables defined in this environment. |
| `selected` | `Boolean` | Whether this environment is marked for import. |
| `hasConflict` | `Boolean` | Computed property: true if `name` matches an existing environment in `state.environments`. |

### 1.5 ImportScanResult
The structured result returned after parsing and validating the raw specification.

| Field | Type | Description |
|---|---|---|
| `specFormat` | `SpecFormat` | Identified format (`OPENAPI_V3`, `OPENCOLLECTION`). |
| `specTitle` | `String` | Title of the specification (e.g. `info.title`). |
| `folders` | `List<ImportCandidateFolder>` | Detected folder candidates with request hierarchies. |
| `environments` | `List<ImportCandidateEnvironment>` | Detected environment candidates. |

```kotlin
enum class SpecFormat {
    OPENAPI_V3,
    OPENCOLLECTION
}
```

### 1.6 ImportDialogStep
Controls the current view within the import dialog workflow.

```kotlin
enum class ImportDialogStep {
    SOURCE_SELECTION,
    ITEM_SELECTION
}
```

---

## 2. Validation Rules

1. **File Extension Filter**: Single file picker must only allow `.json`, `.yaml`, and `.yml` extensions.
2. **Scan Button State**:
   - In `FILE` mode: Enabled only when `rawContent != null && rawContent.isNotBlank()`.
   - In `CLIPBOARD` mode: Enabled only when system clipboard contains non-blank text.
3. **Specification Validation**:
   - OpenAPI: `openAPI != null` and `openAPI.openapi` starts with `3.`. If `2.0` (Swagger), validation fails with explicit message that OpenAPI v3+ is required.
   - OpenCollection: Must be valid OpenCollection YAML conforming to schema.
   - Other or malformed input: Validation fails with descriptive error message; dialog stays on `SOURCE_SELECTION`.
4. **Import Button State**:
   - In `ITEM_SELECTION` mode: Enabled only when at least one folder OR environment has `selected == true`.
5. **Rename Validation**:
   - New name must not be blank. If blank, rename reverts to previous valid name or displays inline validation error.
   - Conflict status recomputes immediately upon any name modification.

---

## 3. State Transitions

```
[Sidebar Import Click]
       │
       ▼
┌───────────────────────────────┐
│ ImportDialogStep:             │
│ SOURCE_SELECTION              │
│ (Tabs: File / Clipboard)      │
└──────┬────────────────────────┘
       │ [Scan Clicked]
       ▼
 ┌──────────────┐   Validation Error
 │ Validate &   ├──────────────────────┐
 │ Parse Spec   │                      │
 └──────┬───────┘                      ▼
        │ Success        ┌─────────────────────────┐
        ▼                │ Display Error Banner    │
┌────────────────────────┤ on SOURCE_SELECTION     │
│ ImportDialogStep:      └─────────────────────────┘
│ ITEM_SELECTION         │
│ (Folders & Envs List)  │
└──────┬────────────┬────┘
       │            │
 [Cancel]      [Import]
       │            │
       ▼            ▼
 [Close Dialog] [Overwrite Conflicting &
                 Add New Items to Workspace
                 -> Save & Close Dialog]
```
