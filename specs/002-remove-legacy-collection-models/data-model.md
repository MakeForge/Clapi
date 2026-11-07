# Data Model: Remove Legacy Collection Models

## Entities kept (OpenCollection)

Source: `core-model/src/commonMain/kotlin/io/github/crustack/core/model/opencollection/`. Not changed by this feature.

| Entity | Key fields | Notes |
|---|---|---|
| `OpenCollectionDocument` | `opencollection`, `info: CollectionInfo`, `config: CollectionConfig?`, `items`, `request: RequestDefaults?`, `docs`, `bundled`, `extensions` | The single global workspace document. Each top-level folder is one Clapi collection. `config.environments` holds the global environments (FR-014, FR-015). |
| `CollectionInfo` | `name`, `summary`, `version`, `authors` | Global document name. Not shown to users. |
| `CollectionConfig` | `environments: List<Environment>`, `proxy`, `clientCertificates` | Global environments (FR-015). |
| `FolderItem` | `info: FolderInfo`, `items`, `request`, `docs`, `extensions` | A top-level folder is one Clapi collection. Nested folders are Clapi folders. The schema rejects an `extensions` key on the folder item, so the id is persisted in the document-level `extensions["folderIds"]` map and restored to in-memory `extensions["id"]` (FR-019). |
| `HttpRequestItem` | `info: HttpRequestInfo`, `http: HttpRequestDetails`, `runtime`, `settings`, `docs`, `examples`, `extensions` | Requests. Order is `items` list order. |
| `ClapiExtensionData` | `mcp: List<McpConnectionConfigDto>`, `authType` | Stored under the `clapi` key in `extensions`. Holds Clapi-only data such as MCP connections. |

## Entities removed

| Removed type | Location | Replacement |
|---|---|---|
| `WorkspaceDefinition` (`@Deprecated`) | `core-model/.../CollectionModels.kt` | `OpenCollectionDocument` (one global document; the collection is a top-level folder) |
| `CollectionDefinition` (`@Deprecated`) | same | `OpenCollectionDocument` |
| `FolderDefinition` (`@Deprecated`) | same | `FolderItem` |
| `CollectionRepository` (interface) | `core-storage/.../StorageContracts.kt` | `CanonicalCollectionRepository` |
| `ClapiCollectionDto`, `FolderDto`, `RequestDto`, `ClapiWorkspaceDto`, `WorkspaceImportResult` | `ui-shared/.../ImportExportRepository.kt` | None. Legacy format is rejected. |

## Internal persistence (no `clapi*` type)

Workspace metadata is saved through `WorkspaceRepository` (see research.md, item 3).

| Data | Where it is stored | Notes |
|---|---|---|
| Environments | `config.environments` of the global document | One list, not tied to any collection (FR-015). Merged by name on import (FR-016). |
| Global variables | Private internal store | Not a user-facing format. |
| Request history | Private internal store | Not a user-facing format. |
| Expanded tree state | Private internal store | Not a user-facing format. |
| Legacy `clapi.workspace` entry | Not read | Ignored, not migrated. |

## Validation rules

- An import whose root `type` is `clapiCollection`, or a load whose stored blob is `clapiWorkspace`, is rejected with `ImportExportException`. Nothing is written (FR-005).
- A collection is saved only if it passes `OpenCollectionSchemaValidator` (existing behavior).
- Environment names are unique in the global list. An imported environment with an existing name is merged: variables and fields missing from the existing environment are added, nothing is removed (FR-016).
- A collection's id survives save and reload (FR-019). It is stored in the document-level `extensions["folderIds"]` map (index path to id), because the OpenCollection schema rejects `extensions` on a folder item. After load it is on the folder's in-memory `extensions["id"]`.

## State transitions

- Export one collection: top-level folder -> `CollectionNode` -> standalone `OpenCollectionDocument` whose `items` are the collection's children and which has no `config` (FR-017). Output is JSON or YAML.
- Import one collection: JSON or YAML (or Postman converted first) -> `OpenCollectionDocument` -> top-level folder added to the global document with a unique name. Environments in the imported `config` are merged (FR-016).
- Load: the global document -> `CollectionNode` roots (`toCollectionNode`), one per top-level folder.
