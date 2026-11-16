# Tasks: OpenAPI & OpenCollection Import Dialog

**Input**: Design documents from `specs/004-import-openapi-collection/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/import-dialog-contract.md`, `contracts/openapi-importer-contract.md`, `quickstart.md`
**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`[US1]`, `[US2]`, `[US3]`, `[US4]`)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic dependencies

- [X] T001 Add `swagger-parser` version `2.1.25` to gradle/libs.versions.toml and add `implementation("io.swagger.parser.v3:swagger-parser:2.1.25")` in core-storage/build.gradle.kts
- [X] T002 [P] Add import dialog localization strings (dialog title, tabs, scan, overwrite warning, cancel, import) to ui-shared/src/commonMain/resources/i18n/en.json, de.json, es.json, fr.json and update ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/i18n/Strings.kt

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core model entities, platform file picking, and parser contracts that MUST be complete before ANY user story can be implemented

⚠️ **CRITICAL**: No user story work can begin until this phase is complete.

- [X] T003 [P] Define data entities `ImportSourceType`, `ImportSourceState`, `ImportCandidateFolder`, `ImportCandidateEnvironment`, `ImportScanResult`, `SpecFormat`, and `ImportDialogStep` with verbatim validation constraints in core-model/src/commonMain/kotlin/io/github/makeforge/clapi/core/model/importers/ImportModels.kt
- [X] T004 [P] Update `pickFileForImport` in ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/platform/PlatformApi.kt and ui-shared/src/desktopMain/kotlin/io/github/makeforge/clapi/ui/shared/platform/PlatformApi.desktop.kt to support single file picking filtered to `.json`, `.yaml`, and `.yml` returning file name and text content
- [X] T005 Define `SpecificationImporter` interface and scan contract in core-storage/src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/importers/SpecificationImporter.kt
- [X] T006 Implement `OpenCollectionImporter` for OpenCollection YAML using `OpenCollectionSchemaValidator` and `OpenCollectionSerializer` in core-storage/src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/importers/OpenCollectionImporter.kt
- [X] T007 [P] Create test fixtures `petstore-openapi-v3.yaml`, `sample-opencollection.yaml`, and `swagger-v2.yaml` in core-storage/src/commonTest/resources/fixtures/

**Checkpoint**: Core data models, platform file picker extensions, and foundational parser interfaces ready. User story implementation can now begin.

---

## Phase 3: User Story 1 - Import from File via Drag-and-Drop or File Selection (Priority: P1) 🎯 MVP

**Goal**: User can click the import folder button in the sidebar, choose or drag-and-drop an OpenAPI v3 or OpenCollection file (JSON/YAML), scan the file, view parsed candidate items, and import them into the workspace.

**Independent Test**: Select a valid OpenAPI v3 YAML file in the dialog, click Scan, verify the candidate folders appear, click Import, and verify the folder and requests appear in the sidebar.

### Tests for User Story 1

- [X] T008 [P] [US1] Unit test for OpenAPI v3 and OpenCollection file scanning in core-storage/src/commonTest/kotlin/io/github/makeforge/clapi/core/storage/importers/SpecificationImporterTest.kt

### Implementation for User Story 1

- [X] T009 [US1] Implement `OpenApiImporter` in core-storage/src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/importers/OpenApiImporter.kt using `OpenAPIParser` to parse OpenAPI v3 (JSON/YAML) and reject Swagger 2.0 or invalid content with diagnostic messages
- [X] T010 [US1] Create `ImportSpecificationDialog.kt` with Step 1 (Source Selection) supporting File tab, dropzone drag-and-drop, "Choose Files" button, file clear action, and dynamic Scan button enablement in ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ImportSpecificationDialog.kt
- [X] T011 [US1] Update sidebar import folder button (`collection-import-button`) to open `ImportSpecificationDialog` in ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/Sidebar.kt
- [X] T012 [US1] Implement asynchronous scan execution, error banner display on validation failure, and transition to Step 2 (Item Selection) in ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ImportSpecificationDialog.kt
- [X] T013 [US1] Implement committing selected candidate folders to `state.collections` and saving workspace via `WorkspaceRepository.save(state)` on Import in ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ImportSpecificationDialog.kt

**Checkpoint**: User Story 1 is functional as an independent MVP. Files can be selected or dropped, scanned, and imported.

---

## Phase 4: User Story 3 - Selective Import, Renaming, and Conflict Resolution (Priority: P1)

**Goal**: Users can selectively toggle folders and environments, rename items before import, and observe overwrite warnings for colliding names.

**Independent Test**: Scan a file containing a folder or environment that matches an existing workspace name; verify the overwrite warning is displayed; rename the item inline and verify the warning disappears; uncheck an item and verify it is excluded from the final import.

### Tests for User Story 3

- [X] T014 [P] [US3] Unit test for conflict detection, real-time rename validation, and overwrite logic in ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/importers/ImportConflictResolutionTest.kt

### Implementation for User Story 3

- [X] T015 [US3] Implement Step 2 (Item Selection view) in `ImportSpecificationDialog.kt` rendering lists of candidate folders and environments with selection checkboxes (`testTag("import-folder-check-${id}")`, `testTag("import-env-check-${id}")`) in ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ImportSpecificationDialog.kt
- [X] T016 [US3] Implement conflict detection comparing candidate names against `state.collections` and `state.environments`, rendering overwrite warning badges (`testTag("import-folder-conflict-${id}")`, `testTag("import-env-conflict-${id}")`) in ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ImportSpecificationDialog.kt
- [X] T017 [US3] Implement inline renaming for candidate folders and environments (`testTag("import-folder-name-${id}")`, `testTag("import-env-name-${id}")`), recomputing conflict status dynamically on name changes in ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ImportSpecificationDialog.kt
- [X] T018 [US3] Wire "Cancel" button to abort without mutations and "Import" button to overwrite conflicting items and insert non-conflicting items into `state.collections` and `state.environments` in ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ImportSpecificationDialog.kt

**Checkpoint**: User Story 3 is complete. Users have full control over item selection, renaming, and conflict overwrite safety.

---

## Phase 5: User Story 2 - Import from Clipboard (Priority: P2)

**Goal**: Users can switch to the Clipboard tab in the dialog and scan OpenAPI or OpenCollection content copied to the system clipboard.

**Independent Test**: Copy an OpenAPI JSON or OpenCollection YAML payload to the clipboard; open the dialog; switch to Clipboard tab; verify Scan button is enabled; click Scan; verify items are detected and imported.

### Tests for User Story 2

- [X] T019 [P] [US2] Unit and UI test for clipboard reading, scan state validation, and content parsing in ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/importers/ImportClipboardUiTest.kt

### Implementation for User Story 2

- [X] T020 [US2] Implement Clipboard source tab in `ImportSpecificationDialog.kt` reading `PlatformApi.readFromClipboard()`, showing status/byte length, and toggling Scan button enablement in ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ImportSpecificationDialog.kt
- [X] T021 [US2] Connect Clipboard content to `SpecificationImporter.scanContent()` when Scan is triggered from Clipboard tab in ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ImportSpecificationDialog.kt

**Checkpoint**: User Story 2 is complete. Clipboard import works seamlessly alongside File import.

---

## Phase 6: User Story 4 - OpenAPI Specification Hierarchy Mapping (Priority: P2)

**Goal**: OpenAPI specifications are structured into a parent root folder named after the schema's `info.title`, subfolders for OpenAPI tags, and environment variables derived from OpenAPI `servers`.

**Independent Test**: Scan an OpenAPI v3 spec with title "Petstore API", server URL `https://api.petstore.com/v3`, and tagged operations; verify the root folder is "Petstore API", subfolders match the tags, and an environment with `baseUrl` is created.

### Tests for User Story 4

- [X] T022 [P] [US4] Unit test for OpenAPI schema title root folder, tag subfolders, untagged operations, and server environment generation in core-storage/src/commonTest/kotlin/io/github/makeforge/clapi/core/storage/importers/OpenApiHierarchyMappingTest.kt

### Implementation for User Story 4

- [X] T023 [US4] Implement OpenAPI tag-to-subfolder hierarchy mapping in `OpenApiImporter.kt` placing operations with tags into subfolders under the root parent folder and untagged operations directly under root in core-storage/src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/importers/OpenApiImporter.kt
- [X] T024 [US4] Map OpenAPI operation parameters (path templates `{{baseUrl}}/path`, query parameters, headers) and request bodies (JSON schema/examples) to collection requests in core-storage/src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/importers/OpenApiImporter.kt
- [X] T025 [US4] Extract OpenAPI `servers` and server variables into an `ImportCandidateEnvironment` with `baseUrl` variable in core-storage/src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/importers/OpenApiImporter.kt

**Checkpoint**: User Story 4 is complete. OpenAPI specs generate complete, structured hierarchies with environments.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: End-to-end integration testing, quickstart verification, and user documentation

- [X] T026 [P] Add end-to-end Compose UI test suite covering the full dialog workflow in ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/ImportSpecificationUiTest.kt
- [X] T027 [P] Update documentation for OpenAPI and OpenCollection importing in docs/Clapi/features/import-export.md
- [X] T028 Execute manual verification scenarios documented in specs/004-import-openapi-collection/quickstart.md

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup (Phase 1) — BLOCKS all user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational (Phase 2) — Delivers MVP.
- **User Story 3 (Phase 4)**: Depends on User Story 1 (Phase 3) for Item Selection view integration.
- **User Story 2 (Phase 5)**: Depends on Foundational (Phase 2) and User Story 1 (Phase 3) dialog structure.
- **User Story 4 (Phase 6)**: Depends on User Story 1 (Phase 3) `OpenApiImporter` base implementation.
- **Polish (Phase 7)**: Depends on completion of all user story phases.

### User Story Dependencies

```
Phase 1: Setup
     │
     ▼
Phase 2: Foundational
     │
     ▼
Phase 3: User Story 1 (File Import & Dialog MVP)
     │
     ├──────────────────────────┬──────────────────────────┐
     ▼                          ▼                          ▼
Phase 4: User Story 3      Phase 5: User Story 2      Phase 6: User Story 4
(Selective, Rename,        (Clipboard Import)         (OpenAPI Hierarchy &
 Overwrite Warning)                                    Environment Mapping)
     │                          │                          │
     └──────────────────────────┴──────────────────────────┘
                                │
                                ▼
                         Phase 7: Polish
```

---

## Parallel Execution Opportunities

- In Phase 1: T002 can run in parallel with T001.
- In Phase 2: T003, T004, and T007 can run in parallel.
- In Phase 3 (US1): Test T008 can run before/in parallel with T009.
- In Phase 4 (US3): Test T014 can run in parallel with UI implementation.
- In Phase 5 (US2): Test T019 can run in parallel with UI implementation.
- In Phase 6 (US4): Test T022 can run in parallel with hierarchy mapping implementation.
- In Phase 7: T026 and T027 can run in parallel.

---

## Implementation Strategy

### MVP First (User Story 1)
1. Complete Phase 1 (Setup: dependencies & strings)
2. Complete Phase 2 (Foundational: data models, file picker filter, importer interfaces)
3. Complete Phase 3 (User Story 1: File import, dialog Step 1, scanning, import execution)
4. **VALIDATE MVP**: Verify a file can be chosen or dragged in, scanned, and imported into the sidebar.

### Incremental Delivery
1. Add User Story 3 (Phase 4) -> Introduces preview selection checkboxes, inline rename, and conflict overwrite warnings.
2. Add User Story 2 (Phase 5) -> Extends input sources with system clipboard reading.
3. Add User Story 4 (Phase 6) -> Refines OpenAPI hierarchy (tag subfolders and server environments).
4. Run Polish & Validation (Phase 7).
