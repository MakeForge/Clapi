# Contract: Collection Import and Export

This is the user-facing file contract after the change. The contract is the only supported collection format.

## Export one collection

- **Input**: one collection (a top-level folder of the global document).
- **Output**: a standalone OpenCollection document in JSON or YAML (`ExportFormat`). Its `items` are the collection's children, and it has no `config` (environments are not exported, FR-017).
- **Guarantees**:
  - The `opencollection` version field is present.
  - `info.name` is the collection name.
  - Folder and request order is kept in `items`.
  - The output contains no `type: "clapiCollection"` or `type: "clapiWorkspace"` marker.
  - An empty collection exports as a valid document with an empty `items` list.

## Import one collection

| Input | Result |
|---|---|
| OpenCollection JSON (`"opencollection"` key present) | Added to the global document as a top-level folder with a unique name. Folders, requests, and order are kept. Environments in `config` are merged by name (FR-016). |
| OpenCollection YAML (`opencollection:` present) | Same as JSON. |
| Postman v2/v2.1 collection | Converted through `PostmanImporter` into OpenCollection, then added as above. Behavior unchanged. |
| `type: "clapiCollection"` | Rejected. `ImportExportException` with a message that the format is no longer supported. No data written. |
| Any other content | Rejected with the existing "Unrecognized collection format" error. |

The unrecognized-format message is updated to list OpenCollection and Postman only. It no longer lists "Clapi".

## Workspace

- The global document is internal. Users export and import collections only (FR-013).
- Environments, global variables, and history persist with the workspace. No user-facing workspace bundle exists.
- A stored `clapiWorkspace` blob, under the old `clapi.workspace` key, is ignored (FR-018). The app starts with default state and writes no data from the old blob.

## Error contract

- Rejection surfaces as `ImportExportException` with a non-empty user-facing message.
- Rejection happens before any state change. A failed import leaves the global document unchanged.

