# Contract: Collection Editor & Request Defaults

**Branch**: `008-multiple-collections` | **Date**: 2026-11-09

## 1. Overview

Specifies the layout, tabs, and editing behavior of the dedicated `CollectionEditor` for managing collection metadata and request defaults.

---

## 2. Tab Bar Layout & Ordering

The Collection Editor tab bar consists of 7 tabs in strict left-to-right order:

```text
[ Info ] [ Variables ] [ Headers ] [ Auth ] [ Pre-request ] [ Tests ] [ Protobuf ]
```

| Tab | Label | Content |
|---|---|---|
| `INFO` | `"Info"` | **First tab on the left**. Form fields for `CollectionInfo`: Name, Summary, Version (`authors` omitted). |
| `VARIABLES` | `"Variables"` | Key-value table for collection-level request default variables. |
| `HEADERS` | `"Headers"` | Key-value table for collection-level request default headers (key, value, enabled toggle). |
| `AUTH` | `"Auth"` | Authentication method selector (No Auth, Bearer, Basic, API Key, etc.) and configuration fields. |
| `PRE_REQUEST` | `"Pre-request"` | Script editor for JavaScript executed before requests in this collection. |
| `TESTS` | `"Tests"` | Script editor for JavaScript executed after requests in this collection. |
| `PROTOBUF` | `"Protobuf"` | Lists of linked `.proto` files (`protoFiles`) and import paths (`importPaths`) for gRPC services. |

---

## 3. "Info" Tab Fields Contract

| Field | Input Control | Rules |
|---|---|---|
| **Name** | Single-line TextField | Required, non-empty. Updating marks tab dirty. When committed or saved, updates sidebar collection label and open tab title. Disambiguates duplicate names via incremental index. |
| **Summary** | Multi-line TextField | Optional. Persisted to `CollectionInfo.summary`. |
| **Version** | Single-line TextField | Optional semantic version string (e.g., `1.0.0`). Persisted to `CollectionInfo.version`. |

---

## 4. Save & Sync Semantics

1. **Dirty Tracking**:
   - Any modification in `Info`, `Variables`, `Headers`, `Auth`, `Pre-request`, `Tests`, or `Protobuf` marks `tab.isDirty = true`.
2. **Save Action**:
   - Clicking "Save" (or pressing Ctrl/Cmd+S):
     1. Syncs all edited tab state back to the corresponding `CollectionNode` in `state.collections`.
     2. Calls `state.syncTabToCollectionNode(tab)`.
     3. Marks `tab.isDirty = false`.
     4. Triggers `persistWorkspaceAsync()`.
