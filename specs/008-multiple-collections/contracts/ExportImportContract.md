# Contract: Selective Multi-Collection Export & Import

**Branch**: `008-multiple-collections` | **Date**: 2026-11-09

## 1. Overview

Specifies the contracts for exporting subsets of collections (1 file per collection) and importing OpenCollection documents as distinct collections in the workspace.

---

## 2. Export Collections Selection Dialog Contract

Triggering global export from the sidebar header options menu ("Export Collections...") displays `ExportCollectionsDialog`:

```text
┌────────────────────────────────────────────────────────┐
│ Export Collections                                 [X] │
├────────────────────────────────────────────────────────┤
│ Select collections to export (1 file per collection):  │
│                                                        │
│ [x] Select All                                         │
│ ────────────────────────────────────────────────────── │
│ [x] Payments API (3 folders, 12 requests)              │
│ [x] User Management (1 folder, 4 requests)             │
│ [ ] Analytics Service (0 folders, 2 requests)          │
│                                                        │
│ Format: (•) OpenCollection JSON   ( ) OpenCollection YAML│
├────────────────────────────────────────────────────────┤
│                             [ Cancel ]  [ Export (2) ] │
└────────────────────────────────────────────────────────┘
```

### Dialog Contract Rules:
1. **Selection**:
   - Checkbox next to each collection in `state.collections`.
   - "Select All" toggle selects or deselects all items.
   - "Export (N)" action button is enabled only when `N >= 1`.
2. **Directory Selection**:
   - Clicking "Export" opens the native directory picker.
   - The user selects a target destination folder.
3. **File Output**:
   - For each selected collection, the system writes a separate file:
     - Pattern: `<sanitized_collection_name>.opencollection.json` (or `.yaml`)
     - If a file with that name exists in the target directory, the system automatically appends an incremental numeric suffix (e.g., `<name> (1).opencollection.json`).
4. **Data Fidelity**:
   - Each exported file is a fully compliant standalone `OpenCollectionDocument` containing its `info`, `request` (request defaults), `config.protobuf`, and full hierarchy of `items`.

---

## 3. Collection Import Contract

When importing an OpenCollection file (JSON or YAML) via "Import...":
1. **Parsing & Validation**: The file content is validated against the OpenCollection v1.0.0 JSON schema.
2. **Name Disambiguation**: If a collection in `state.collections` already has the same name, the incoming collection is automatically renamed by appending an incremental index: `"${imported.info.name} (1)"`.
3. **State Integration**: The imported document is added as a new distinct root item in `state.collections`.
4. **Environment Merging**: Any environments declared in the imported collection's `config.environments` are merged into `state.environments` by environment name.
