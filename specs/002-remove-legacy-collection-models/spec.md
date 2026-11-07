# Feature Specification: Remove Legacy Collection Models

**Feature Branch**: `002-remove-legacy-collection-models`

**Created**: 2026-11-07

**Status**: Draft

**Input**: User description: "Migrating away from the deprecated models in CollectionModels.kt to the new opencollection models. In the end all the marked deprecated models should be gone and working only with the opencollection models. There should be no legacy/migration bridges, and no compatibility with the old deprecated model or config files. Ensure integration and unit tests are properly adjusted to the opencollection model, especially qa-tests."

## Clarifications

### Session 2026-11-07

- Q: Should the legacy collection repository interface be deleted, or kept and switched to OpenCollection documents? → A: Delete the legacy `CollectionRepository` interface. Keep `InMemoryCollectionRepository` only because tests use it, and change it to hold OpenCollection documents.
- Q: Should the legacy collection repository interface be deleted, or kept and switched to OpenCollection documents? → A: Delete the legacy `CollectionRepository` interface. Keep `InMemoryCollectionRepository` only because tests use it, and change it to hold OpenCollection documents.
- Q: What should workspace export and import do once the legacy workspace format is removed? → A: Remove workspace-level export and import. Users export and import one collection at a time, as OpenCollection documents.
- Q: How should workspace metadata (environments, global variables, history, expanded state) be persisted once the `clapiWorkspace` blob is removed? → A: Superseded by the next entry. Originally: environments copied into each collection's `config.environments`.

### Session 2026-11-07 (data model revision)

- Q: Should Clapi keep one OpenCollection document per collection, with environments copied into each? → A: No. Clapi maintains one global OpenCollection document. Each Clapi collection is a top-level OpenCollection folder in that document. Environments live in the global document's `config.environments` and are not tied to any collection. Global variables, history, and expanded state stay in a private internal store.
- Q: When an imported collection carries an environment whose name already exists? → A: Merge. Add variables and fields that the existing environment lacks. Never remove or overwrite existing entries.
- Q: Should a single-collection export include environments? → A: No. Environments belong to the workspace, not to a collection.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Delete deprecated collection models (Priority: P1)

A maintainer opens the core model module and finds only the OpenCollection model types. The three deprecated types (`WorkspaceDefinition`, `CollectionDefinition`, `FolderDefinition`) no longer exist, and nothing in the codebase refers to them.

**Why this priority**: This is the core request. Every other story depends on the legacy types being gone.

**Independent Test**: Search the repository for the three deprecated type names and confirm there are no matches outside historical specification documents. Build all modules.

**Acceptance Scenarios**:

1. **Given** the repository after this change, **When** a search is run for `WorkspaceDefinition`, `CollectionDefinition`, or `FolderDefinition` in source code, **Then** there are no matches.
2. **Given** the repository after this change, **When** all modules are compiled, **Then** the build succeeds with no reference to the removed types.
3. **Given** the in-memory storage module, **When** a collection repository is used, **Then** it stores and returns OpenCollection documents, not the deprecated types.

---

### User Story 2 - Work only with the OpenCollection format (Priority: P2)

A user imports, exports, and persists collections only in the OpenCollection format. The legacy `clapiCollection` and `clapiWorkspace` formats are no longer read or written. A user who tries to import a legacy-format file gets a clear rejection message, not a silent conversion.

**Why this priority**: The legacy JSON path is the main remaining user-facing use of the deprecated model. Removing it completes the migration, but it changes existing import behavior and so comes after the type removal.

**Independent Test**: Export a collection, then import it again. The round trip succeeds and the file's `type` is an OpenCollection type. Import a legacy `clapiCollection` file and confirm the import fails with an unsupported-format error.

**Acceptance Scenarios**:

1. **Given** a collection exists in the app, **When** the user exports it, **Then** the output is an OpenCollection document and contains no `clapiCollection` or `clapiWorkspace` type marker.
2. **Given** an OpenCollection export file, **When** the user imports it, **Then** the collection is restored with its folders and requests intact.
3. **Given** a legacy `clapiCollection` file, **When** the user imports it, **Then** the import is rejected with an error that says the format is no longer supported. No partial data is written.
4. **Given** a legacy `clapiWorkspace` file, **When** the workspace loader runs, **Then** it is rejected the same way and no migration or conversion is attempted.
5. **Given** a Postman collection file, **When** the user imports it, **Then** it is still converted to OpenCollection as before.

---

### User Story 3 - Test suites target OpenCollection (Priority: P3)

A developer runs the unit, integration, and qa-test suites. All fixtures and assertions use OpenCollection documents. No test relies on the legacy `clapiCollection` or `clapiWorkspace` format, except for a test that asserts such files are rejected.

**Why this priority**: Tests are how the team verifies the migration. They are lower priority than the code change itself, but they must match it before the work is complete.

**Independent Test**: Run the unit tests in `core-storage`, `ui-shared`, `ui-desktop`, and the `qa-tests` module. All pass with no test using the legacy format as valid input.

**Acceptance Scenarios**:

1. **Given** the qa-tests module, **When** the sample collection fixture is loaded, **Then** it is an OpenCollection document and the suite passes.
2. **Given** the qa-tests module, **When** the round-trip and validation suites run, **Then** they pass with no legacy-format input.
3. **Given** the `ui-shared` and `ui-desktop` test suites, **When** they run, **Then** every import/export and persistence test uses OpenCollection input, and the MCP workspace backward-compatibility test is removed.
4. **Given** the documentation, **When** a reader looks up the collection model, **Then** the architecture and planning docs point to the OpenCollection models and no longer describe the removed files as current.

---

### Edge Cases

- A user has a `clapiCollection` file from an older version. The import is rejected with a clear message. No automatic conversion happens.
- A user has a `clapiWorkspace` file in their data directory. Startup does not crash. The file is reported as unsupported.
- The Postman importer receives a file that is almost but not fully Postman-shaped. Its existing error behavior is unchanged.
- An export is attempted on an empty collection. The output is a valid OpenCollection document with no items.
- An OpenCollection document references a folder or request by an ID that already exists in another collection. Import behavior stays as it is today.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The codebase MUST NOT contain the `WorkspaceDefinition`, `CollectionDefinition`, or `FolderDefinition` types.
- **FR-002**: The legacy `CollectionRepository` interface MUST be removed, and `CanonicalCollectionRepository` MUST be the only collection store used by production code. `InMemoryCollectionRepository` MUST be kept for tests only and MUST hold OpenCollection documents, not the deprecated types.
- **FR-003**: The system MUST NOT read the `clapiCollection` or `clapiWorkspace` JSON format for any purpose, including migration.
- **FR-004**: The system MUST NOT write the `clapiCollection` or `clapiWorkspace` JSON format.
- **FR-005**: When a user imports a file in a legacy format, the system MUST reject it with a user-visible error and write no data.
- **FR-006**: The system MUST NOT include any legacy-to-OpenCollection conversion or migration bridge.
- **FR-007**: The system MUST continue to import Postman collections and convert them to OpenCollection.
- **FR-008**: Export and import of OpenCollection documents MUST round-trip without losing folders, requests, or their order.
- **FR-009**: The qa-tests fixture `clapi-test-collection.json` MUST be converted to an OpenCollection document with the same logical content.
- **FR-010**: All unit, integration, and qa-test suites MUST pass after the change, and none may use a legacy format as valid input, except for tests that assert rejection.
- **FR-011**: The MCP workspace backward-compatibility test MUST be removed.
- **FR-012**: Documentation that references the removed models or the legacy format MUST be updated to describe the OpenCollection models.
- **FR-013**: Workspace-level export and import MUST be removed. Collections are exported and imported one at a time as OpenCollection documents, and no workspace bundle format is introduced.
- **FR-014**: Clapi MUST persist its collections in one global OpenCollection document. Each Clapi collection MUST be a top-level folder of that document, and its folders and requests MUST be nested under it.
- **FR-015**: Environments MUST be stored in the global document's `config.environments`. They are not tied to any collection, so they persist even when no collection exists.
- **FR-016**: Importing a collection that carries environments MUST merge them into the global environment list by name. For an environment whose name already exists, variables and fields missing from the existing environment MUST be added. Existing entries MUST NOT be removed or overwritten.
- **FR-017**: Exporting a single collection MUST NOT include environments.
- **FR-018**: Global variables, request history, and expanded tree state MUST be persisted in a private internal store that uses no `clapi*` type and is not a user-facing format. The legacy `clapi.workspace` storage entry MUST be ignored, not migrated.
- **FR-019**: A collection's identity (its id) MUST survive save and reload, so that expanded state and history still refer to the same collection.

### Key Entities

- **OpenCollection document**: The single supported collection format. It contains a collection, its folders, and its HTTP requests.
- **Global workspace document**: One OpenCollection document that holds every Clapi collection as a top-level folder, plus the environments in `config.environments`.
- **Deprecated collection models**: `WorkspaceDefinition`, `CollectionDefinition`, and `FolderDefinition`. These are removed.
- **Legacy collection file**: A `clapiCollection` or `clapiWorkspace` JSON file. These are rejected and are no longer produced.
- **Postman collection**: A third-party file format. It is imported and converted to an OpenCollection document, and is not affected by this change.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A repository-wide search finds zero references to the three deprecated type names in source code.
- **SC-002**: A repository-wide search finds zero production writes of the `clapiCollection` or `clapiWorkspace` format.
- **SC-003**: 100% of the existing unit, integration, and qa-test suites pass after the change.
- **SC-004**: Importing any legacy-format file produces a user-visible error and writes zero collections, in every test case that covers it.
- **SC-005**: An OpenCollection export followed by an import returns an identical collection, including folder and request order.
- **SC-006**: The qa-tests sample fixture loads and passes validation as an OpenCollection document.

## Assumptions

- "Config files" in the request means the `clapiCollection` and `clapiWorkspace` JSON files and the MCP backward-compatibility behavior that reads them. It does not mean application settings or environment files.
- Existing users are not relied on. The feature 001 scope note says there are no existing users, so no data migration is needed and rejection with an error is acceptable.
- `RequestDefinition`, `EnvironmentDefinition`, and `HistoryEntry` in `RequestModels.kt` are not deprecated and are out of scope.
- The Postman importer is third-party format support and stays unchanged apart from any internal references to the removed types.
- The OpenCollection models in `core-model/.../opencollection/` are the target and are not changed by this feature.
- Historical planning and todo documents under `specs/001-opencollection-data-model/` and `docs/Clapi/todos/` may keep references to the removed types as history. Only current architecture and usage docs are updated.
