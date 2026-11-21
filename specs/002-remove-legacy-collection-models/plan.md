# Implementation Plan: Remove Legacy Collection Models

**Branch**: `002-remove-legacy-collection-models` | **Date**: 2026-11-07 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-remove-legacy-collection-models/spec.md`

## Summary

Delete the three `@Deprecated` types in `CollectionModels.kt` (`WorkspaceDefinition`, `CollectionDefinition`, `FolderDefinition`), the legacy `CollectionRepository` interface, and every read and write path for the `clapiCollection` and `clapiWorkspace` JSON formats. OpenCollection becomes the only collection format. Legacy files are rejected with a user-visible error, with no conversion. Workspace-level export and import are removed. Clapi keeps one global OpenCollection document: each collection is a top-level folder, and environments live in its `config.environments`. Importing a collection merges its environments by name without removing existing ones. Single-collection export omits environments. Global variables, history, and expanded state stay in a private store. The `clapi.workspace` entry is ignored. Folder ids are persisted so collection identity survives reload. The Postman importer stays. The qa-tests fixture, its Node validator, and the affected unit and integration tests move to OpenCollection. This is deletion and retyping only, with no compatibility or migration bridge.

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Kotlin 2.1.21 (Kotlin Multiplatform, JVM `desktop` target); Node.js for `qa-tests/collection-validator.mjs`

**Primary Dependencies**: kotlinx.serialization (JSON), Compose Multiplatform Desktop, Ktor (qa-tests embedded sample server and client)

**Storage**: JSON files on disk. Clapi persists one global OpenCollection document, with each collection as a top-level folder, through `CanonicalCollectionRepository` (`core-storage`). Its private store holds global variables, history, and expanded state.

**Testing**: kotlin.test in `commonTest`/`desktopTest`. qa-tests run with `rtk gradlew :qa-tests:jvmTest`. Node validator `qa-tests/collection-validator.mjs`.

**Target Platform**: Desktop JVM (Compose Desktop). Shared code stays multiplatform. `ui-web` is not affected.

**Project Type**: Multi-module Kotlin Multiplatform desktop application with an MCP server

**Performance Goals**: No change. Import and export of a single collection must not regress relative to current OpenCollection behavior.

**Constraints**: No legacy read, write, migration, or conversion code. Build of every module must succeed. Historical 001 planning docs are not edited.

**Scale/Scope**: 3 deprecated types, 1 legacy interface, 1 legacy DTO group (about 1,000 lines in `ImportExportRepository.kt`), about 10 test files, 1 fixture, 1 Node validator, and about 4 docs

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` is still the blank template with no principles, so there are no gates to evaluate. Result: PASS (no constraints). Re-checked after Phase 1 design: still PASS.

## Project Structure

### Documentation (this feature)

```text
specs/002-remove-legacy-collection-models/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── import-export.md # Phase 1 output: user-facing import/export contract
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

Single feature across existing modules. No new modules.

```text
core-model/src/commonMain/kotlin/io/github/crustack/core/model/
├── CollectionModels.kt            # DELETE the 3 @Deprecated types (file removed if empty)
└── opencollection/                # Unchanged. Only supported collection model.

core-storage/src/
├── commonMain/kotlin/io/github/crustack/core/storage/
│   ├── StorageContracts.kt        # DELETE legacy CollectionRepository interface
│   ├── InMemoryRepositories.kt    # InMemoryCollectionRepository holds OpenCollectionDocument
│   └── opencollection/CanonicalCollectionRepository.kt  # Reworked to one global workspace document. Only production store.
│                                                       # OpenCollectionSerializer.kt also writes folder extensions (FR-019).
└── commonTest/kotlin/io/github/crustack/core/storage/
    └── InMemoryRepositoriesTest.kt  # Retype to OpenCollection

ui-shared/src/
├── commonMain/kotlin/io/github/crustack/ui/shared/persistence/
│   ├── ImportExportRepository.kt  # REMOVE legacy DTOs, clapiCollection/clapiWorkspace read/write,
│   │                              #   workspace export/import; ADD legacy rejection
│   ├── WorkspaceRepository.kt     # Save/load of the global OpenCollection document (environments in config),
│   │                              #   global vars, history, expanded state in a private store (no clapi* type)
│   ├── PostmanImporter.kt         # Unchanged
│   └── OpenCollectionModelMapping.kt  # Unchanged. Maps runtime CollectionNode, not legacy.
└── commonTest/kotlin/io/github/crustack/ui/shared/
    ├── components/PreReleaseQaTest.kt        # Fixtures to OpenCollection
    ├── components/RequestReliabilityTest.kt  # Fixtures to OpenCollection
    ├── persistence/ImportExportFormDataTest.kt  # Fixtures to OpenCollection
    ├── persistence/PostmanImporterTest.kt      # Keep Postman cases; drop legacy cases
    ├── persistence/McpWorkspaceBackwardCompatTest.kt  # DELETE (FR-011)
    └── platform/FileChooserMemoryTest.kt     # Fixture reference check

ui-desktop/src/desktopTest/kotlin/io/github/crustack/ui/desktop/persistence/
├── ImportExportRepositoryTest.kt          # Legacy cases become rejection cases
├── ImportExportFixturesIntegrationTest.kt # Collection fixture asserts OpenCollection content
└── PostmanImportIntegrationTest.kt        # Keep Postman; drop legacy workspace cases

qa-tests/
├── fixtures/
│   ├── clapi-test-collection.json         # CONVERT to OpenCollection (FR-009)
│   ├── clapi-test-environment.json        # Unchanged (environment out of scope)
│   └── collection-validation-results.json # Regenerate if the validator output changes
├── collection-validator.mjs               # Read OpenCollection (items/info/http), not folders/requests
└── src/test/kotlin/io/github/crustack/qa/
    ├── OpenCollectionRoundTripE2ETest.kt     # Keep; must stay green
    ├── OpenCollectionValidationE2ETest.kt    # Keep; must stay green
    └── SampleCollectionE2ETest.kt            # Verify fixture is OpenCollection

docs/architecture.md        # Point collection model to opencollection/ (line ~80)
docs/tests.md, docs/mcp.md  # Update legacy fixture and format references
README.md                   # Lines ~66-67 reference the legacy fixture
```

**Structure Decision**: Modify existing modules in place. Delete legacy code rather than adding a new adapter layer. `OpenCollectionModelMapping.kt` stays because it maps the runtime `CollectionNode` model, not the legacy format. The 001 plan and todo docs stay as history.

## Complexity Tracking

None. No constitution violations. The change removes code and adds one rejection path.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | N/A | N/A |

