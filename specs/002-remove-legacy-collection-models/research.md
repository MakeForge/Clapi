# Research: Remove Legacy Collection Models

All NEEDS CLARIFICATION items are resolved. Decisions 1 and 2 come from user answers during `/speckit-clarify` and `/speckit-plan`.

## 1. Legacy `CollectionRepository` interface

- **Decision**: Delete the interface in `core-storage/.../StorageContracts.kt`. Keep `InMemoryCollectionRepository` for tests and retype it to `OpenCollectionDocument`.
- **Rationale**: Production already uses `CanonicalCollectionRepository`. The in-memory class is used by `InMemoryRepositoriesTest.kt:65`.
- **Alternatives considered**: Keep the interface and retype it. Rejected because it would keep a second store abstraction with no production user.

## 2. Workspace-level export and import

- **Decision**: Remove `exportWorkspaceToString`, `importWorkspaceFromString`, `decodeWorkspace`, `encodeWorkspace`, and `mergeWorkspaceIntoState` in `ImportExportRepository.kt`. Export and import one collection at a time.
- **Rationale**: User decision (clarify Q2).
- **Alternatives considered**: Keep a workspace bundle in a new OpenCollection-based format. Rejected by the user.

## 3. Internal workspace-metadata persistence (found during planning)

- **Finding**: `WorkspaceRepository.save` writes environments, global variables, history, and expanded state by calling `exportWorkspaceToString` into `PlatformStorage` under key `clapi.workspace`. `load` reads it back via `decodeWorkspace`. Removing the `clapiWorkspace` format alone would break this persistence.
- **Decision** (revised after user feedback, supersedes the earlier copy-per-collection approach):
  - Clapi keeps one global OpenCollection document. Each Clapi collection is a top-level `FolderItem` in it.
  - Environments are stored in the global document's `config.environments`. They are not tied to any collection.
  - Global variables, history, and expanded tree state are stored in a private internal store with no `clapi*` type.
  - The old `clapi.workspace` entry is ignored, not migrated.
- **Rationale**: Keeps environments in the OpenCollection model without a legacy bridge.
- **Rationale**: Removes the legacy format from storage without a bridge. Keeps environments and collections in OpenCollection.
- **Benefits over the copy approach**: No duplicated environments. No data loss when there are zero collections.
- **Alternatives considered**:
  - One OpenCollection document per collection, with environments copied into each. Rejected after user feedback: duplicates data and loses environments when no collection exists.
  - A new internal blob under a renamed type. Rejected by the user.

## 4. Legacy file rejection

- **Decision**: In `importCollectionFromString`, when the root `type` is `clapiCollection`, throw `ImportExportException` with a message saying the format is no longer supported. Do not parse it. Reject `clapiWorkspace` the same way on any load path. No data is written.
- **Rationale**: FR-005 and the no-bridge rule. `ImportExportException` already exists (`ImportExportRepository.kt:41`).
- **Alternatives considered**: Silent conversion. Rejected by spec FR-006.
- **Open (low impact)**: Exact wording and placement in the import dialog. Default is the existing import error surface.

## 5. Environment import (`clapiEnvironment`)

- **Decision**: Keep `clapiEnvironment` import and export. The spec's assumptions exclude environment files from scope. It is not a deprecated model.
- **Rationale**: Spec assumption. The user did not ask to change it.
- **Alternatives considered**: Remove it too. Not chosen because it is outside the stated scope and needs confirmation.

## 6. qa fixture and Node validator

- **Finding**: `qa-tests/fixtures/clapi-test-collection.json` is legacy. It is read by `ui-desktop/.../ImportExportFixturesIntegrationTest.kt` and by `qa-tests/collection-validator.mjs`. The validator's `flattenRequests` walks `folders`/`requests`, which is the legacy shape.
- **Decision**: Convert the fixture to OpenCollection (`info`, `items`, `http`), keeping the same requests, folders, auth, bodies, scripts, environment variables, and MCP config. The MCP config goes in the `clapi` extension (`OpenCollectionExtensions.kt`). Update `collection-validator.mjs` to walk `items`, and regenerate `collection-validation-results.json` if its content changes.
- **Rationale**: FR-009 and SC-006.
- **Alternatives considered**: Delete the fixture and the validator. Rejected because qa coverage is required.
- **Scope addition**: The spec did not mention the validator. It is added to scope here under FR-010.

## 7. Postman importer

- **Decision**: Unchanged. It is third-party format support (FR-007). Only internal references to removed types change.

## 8. Test migration

- **Decision**: Legacy-format fixtures in `ui-shared` and `ui-desktop` tests move to OpenCollection documents. Legacy-only cases become rejection tests. `McpWorkspaceBackwardCompatTest.kt` is deleted (FR-011).
- **Rationale**: FR-010 and SC-003.

## 9. Folder id persistence (found during the data model revision)

- **Finding**: `FolderItem.extensions` is `@Transient` (`core-model/.../OpenCollectionItem.kt:50`). `OpenCollectionSerializer.enrichItemsForSerialization` (`core-storage/.../OpenCollectionSerializer.kt:~115-130`) writes nothing for folders. So a folder's `extensions["id"]` is lost on save. Reload then generates a new id.
- **Impact**: With one global document, each Clapi collection is a folder. Its id must survive reload (FR-019), or expanded state and history lose their target.
- **Decision**: Write folder extensions in `enrichItemsForSerialization`, and keep the deserialize path (`enrichItems`) that already reads extra folder keys.
- **Alternatives considered**: Store the id in the folder `info` or a new field. Rejected to avoid changing the public OpenCollection model.

## 10. Global document storage (found during the data model revision)

- **Finding**: `DefaultCanonicalCollectionRepository` stores one file per collection, keyed by `collectionId` (`CanonicalCollectionRepository.kt:25-60`). 13 call sites use `observeCollections`, `saveCollection`, `deleteCollection`, `getCollection`, `importCollection`, or `exportCollection` (grep, 2026-11-07).
- **Decision**: Replace the per-collection store with one workspace document. Collections are the top-level folders. Import adds a folder, with a unique name as today (`generateUniqueName`).
- **Rationale**: Matches the single global document. Environments then have one home.
- **Impact**: The API changes in `core-storage`, and the call sites in `ui-shared`, `ui-desktop`, `qa-tests`, and the storage tests change with it.
