# Data Model: Support Multiple OpenCollection Collections

**Branch**: `008-multiple-collections` | **Date**: 2026-11-09

## Overview

This document specifies the core data models, state representations, and mapping transformations required to support multiple OpenCollection collections in Clapi.

---

## 1. Core Domain Models (`core-model`)

### 1.1 `OpenCollectionDocument`

Represents a standalone or persisted OpenCollection document.

| Field | Type | Description |
|---|---|---|
| `opencollection` | `String` | Specification version (default: `"1.0.0"`). |
| `info` | `CollectionInfo` | Collection metadata (name, summary, version, authors). |
| `config` | `CollectionConfig?` | Collection configuration including Protobuf and Environments. |
| `items` | `List<OpenCollectionItem>` | Tree of folders (`FolderItem`) and requests (`RequestItem`). |
| `request` | `RequestDefaults?` | Collection-wide baseline request defaults (variables, headers, auth, scripts). |
| `docs` | `Documentation?` | Optional documentation. |
| `bundled` | `Boolean?` | Bundled packaging flag (default: `true`). |
| `extensions` | `Map<String, JsonElement>` | Extensions including `"id"` and `"clapi"` metadata. |

### 1.2 `CollectionInfo`

Metadata associated with a collection.

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Name of the collection (non-empty string). |
| `summary` | `String?` | Short description/summary of the collection. |
| `version` | `String?` | Semantic version string (e.g., `"1.0.0"`). |
| `authors` | `List<Author>?` | Authors list (preserved in storage; omitted from the UI editor). |

### 1.3 `RequestDefaults`

Baseline request settings defined at collection or folder levels.

| Field | Type | Description |
|---|---|---|
| `headers` | `List<HttpRequestHeader>` | Default HTTP request headers. |
| `auth` | `AuthConfig?` | Default authentication configuration (Bearer, Basic, API Key, etc.). |
| `variables` | `List<VariableEntry>` | Default collection-level variables. |
| `scripts` | `List<Script>` | Pre-request and test/post-request scripts. |
| `settings` | `JsonElement?` | Additional settings element. |

---

## 2. Application State Models (`ui-shared`)

### 2.1 `CollectionNode` (Extended)

In `AppState`, `state.collections` holds root-level `CollectionNode` instances representing each collection (`kind = RequestKind.COLLECTION`).

```kotlin
data class CollectionNode(
    val id: String,
    val name: String,
    val isFolder: Boolean = false,
    val kind: RequestKind = RequestKind.HTTP,
    val method: HttpMethodType? = null,
    val url: String? = null,
    val children: MutableList<CollectionNode> = mutableStateListOf(),

    // Collection metadata
    val summary: String? = null,
    val version: String? = null,

    // Request defaults (Variables, Headers, Auth, Scripts)
    val variableEntries: List<KeyValueEntry>? = null,
    val headerEntries: List<KeyValueEntry>? = null,
    val userHeaders: List<Pair<String, String>> = emptyList(),
    val queryEntries: List<KeyValueEntry>? = null,
    val authType: AuthType? = null,
    val authUsername: String? = null,
    val authPassword: String? = null,
    val authToken: String? = null,
    val authApiKey: String? = null,
    val authApiValue: String? = null,
    val authApiPlacement: String? = null,
    val preRequestScript: String? = null,
    val testScript: String? = null,

    // Collection Protobuf Configuration
    val protoFiles: List<ProtoFileItemState> = emptyList(),
    val importPaths: List<ProtoImportPathState> = emptyList(),

    // Request / payload fields for leaf requests
    val bodyType: BodyType? = null,
    val bodyContent: String? = null,
    val bodyContents: Map<String, String> = emptyMap(),
    val formDataEntries: List<FormDataEntryState> = emptyList(),
    val urlencodedEntries: List<FormDataEntryState> = emptyList(),
    val requestRef: String? = null,
    val mcpConfig: McpConnectionConfig? = null,
    val protoFilePath: String? = null,
    val grpcMethod: String? = null,
    val grpcMethodType: String? = "unary",
)
```

#### Node Classification:
- **Collection Root**: `kind == RequestKind.COLLECTION`. `children` contains folders and direct requests.
- **Folder**: `kind == RequestKind.FOLDER` or (`isFolder == true && kind != RequestKind.COLLECTION`). `children` contains subfolders and requests.
- **Request**: `kind in (RequestKind.HTTP, RequestKind.GRPC, RequestKind.MCP)`.

### 2.2 `RequestTabState` (Extended for Collection Tab)

Represents an open editor tab for an individual collection, folder, or request.

| Property | Type | Description |
|---|---|---|
| `kind` | `RequestKind` | `RequestKind.COLLECTION` when editing a collection. |
| `id` | `String` | ID matching the target collection (`collection.id`). |
| `name` | `String` | Collection name (mutable, synced bidirectionally with Info tab and sidebar). |
| `collectionSummary` | `String?` | Summary string edited in the "Info" tab. |
| `collectionVersion` | `String?` | Version string edited in the "Info" tab. |
| `selectedEditorTab` | `RequestEditorTab` | Active sub-tab in Collection Editor (`INFO`, `VARIABLES`, `HEADERS`, `AUTH`, `PRE_REQUEST`, `TESTS`, `PROTOBUF`). |
| `variables` | `SnapshotStateList<MutableKeyValue>` | Collection request default variables. |
| `headers` | `SnapshotStateList<MutableKeyValue>` | Collection request default headers. |
| `authType` | `AuthType` | Collection request default auth type. |
| `preRequestScript` | `String` | Collection request default pre-request script. |
| `testScript` | `String` | Collection request default test script. |
| `collectionProtoFiles` | `SnapshotStateList<ProtoFileItemState>` | Linked proto files for this collection. |
| `collectionImportPaths` | `SnapshotStateList<ProtoImportPathState>` | Import resolution paths for this collection. |

---

## 3. Inheritance Data Cascading Pipeline

The inheritance resolver in `AppState` establishes a unified hierarchical scope:

```
Collection (Root Scope)
    └── Parent Folder (Middle Scope)
            └── Child Folder (Sub-Scope)
                    └── Request (Execution Scope)
```

### 3.1 Inheritance Rules:
1. **Headers & Variables**:
   - Resolved root-to-leaf: `Collection` -> `Parent Folder` -> `Child Folder` -> `Request`.
   - More specific descendant entries override ancestor entries with identical keys (case-insensitive).
   - In Request & Folder tabs, non-overridden ancestor entries are populated with `kind = HeaderKind.INHERITED`.
2. **Authentication**:
   - When a folder or request has `authType == AuthType.INHERIT`, the resolver traverses upward:
     `Request` -> `Direct Parent Folder` -> `Ancestor Folders` -> `Enclosing Collection`.
   - The first ancestor specifying a concrete authentication method (`BEARER`, `BASIC`, `API_KEY`, `OAUTH2`) provides the resolved auth credentials.
   - If no ancestor specifies concrete authentication, resolves to `AuthType.NONE`.
3. **Scripts**:
   - Pre-request scripts execute sequentially: `Collection.preRequestScript` -> `Folder.preRequestScript` -> `Request.preRequestScript`.
   - Test scripts execute sequentially: `Request.testScript` -> `Folder.testScript` -> `Collection.testScript`.

---

## 4. Multi-Collection Export State

### `ExportCollectionsDialogState`

| Field | Type | Description |
|---|---|---|
| `visible` | `Boolean` | Whether the export selection dialog is open. |
| `format` | `ExportFormat` | Format selected: `ExportFormat.JSON` or `ExportFormat.YAML`. |
| `selectedCollectionIds` | `SnapshotStateSet<String>` | IDs of collections selected for export. |
| `targetDirectory` | `String?` | Path to selected output directory on the filesystem. |

---

## 5. Persistence & Migration Mapping

### Single-Collection to Multi-Collection Migration

```text
Legacy workspace.json:
{
  "info": { "name": "Workspace" },
  "items": [ FolderItem 1, FolderItem 2, RequestItem 1 ],
  "config": { "protobuf": { ... }, "environments": [ ... ] }
}

                ↓↓↓ Automated Migration ↓↓↓

Multi-Collection AppState:
state.collections = [
  CollectionNode(
    name = "Workspace",
    kind = RequestKind.COLLECTION,
    children = [ FolderItem 1, FolderItem 2, RequestItem 1 ],
    protoFiles = [ ... ],
    importPaths = [ ... ]
  )
]
```
All legacy items, request defaults, environments, and proto configurations are wrapped in a single root collection without data loss.
