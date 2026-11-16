# Tasks: OpenCollection Data Model

**Input**: Design documents from `/specs/001-opencollection-data-model/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/, quickstart.md

**Scope note**: User Story 3 (legacy ReqLab/Clapi → OpenCollection migration) is intentionally excluded. There are no existing users, so no legacy format must be read or migrated. Story IDs keep their original numbers (US1, US2, US4, US5, US6) for traceability to spec.md. Migration artifacts (`LegacyCollectionMigrator`, `MigrationReport`, `MigrationStatus`, `migration.contract.md`, `LegacyMigrationE2ETest`, quickstart Scenario 4) are not included. `legacyFields` (data-model §2.8) is also not included; unknown fields are kept only through the `extensions` bag (FR-007, FR-031).

**Tests**: Not explicitly requested in the spec. Validation is covered by the quickstart scenarios in the Polish phase.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2, US4, US5, US6)

## Path Conventions

Multi-module Kotlin project. Paths are relative to the repository root (see plan.md "Source Code").

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Dependencies and module scaffolding required by all stories

- [ ] T001 Add `com.networknt:json-schema-validator` (Draft-07) to the core-storage module dependencies in `core-storage/build.gradle.kts`
- [ ] T002 [P] Add `com.charleskorn.kaml:kaml` to the core-storage module dependencies in `core-storage/build.gradle.kts`
- [ ] T003 [P] Create the `opencollection` package directory `core-model/src/commonMain/kotlin/io/github/crustack/core/model/opencollection/`
- [ ] T004 [P] Create the `opencollection` package directory `core-storage/src/commonMain/kotlin/io/github/crustack/core/storage/opencollection/`
- [ ] T005 [P] Copy the official schema `docs/Clapi/todos/opencollection_v1.0.0.json` into a bundled resource at `core-storage/src/commonMain/resources/opencollection/opencollection_v1.0.0.json` (the resource is loaded by the schema validator)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core types and storage abstraction that every user story depends on. No story work starts until this phase is complete.

**CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T006 Define the root document model `OpenCollectionDocument` (with `opencollection` version field, `info`, `config`, `items`, `bundled`, `extensions`) in `core-model/src/commonMain/kotlin/io/github/crustack/core/model/opencollection/OpenCollectionDocument.kt`. Constraints: `opencollection` must be `"1.0.0"` (FR-002); `bundled` must be `true` for single-file collections (FR-034); `items` order is preserved at root and folder level (FR-004)
- [ ] T007 [P] Define `Info` (with `name`, `type`, and `"folder"` / `"http"` discrimination) in `core-model/src/commonMain/kotlin/io/github/crustack/core/model/opencollection/OpenCollectionDocument.kt`. Folder `info.type` is `"folder"`; request `info.type` is `"http"` (data-model §2.1)
- [ ] T008 Define the sealed `OpenCollectionItem` hierarchy with `FolderItem` and `HttpRequestItem` in `core-model/src/commonMain/kotlin/io/github/crustack/core/model/opencollection/OpenCollectionItem.kt`. A folder is never an HTTP request (FR-010)
- [ ] T009 [P] Define the HTTP request details model (`HttpRequestDetails`, headers, params, bodies) in `core-model/src/commonMain/kotlin/io/github/crustack/core/model/opencollection/OpenCollectionHttp.kt`
- [ ] T010 [P] Define the auth models (`AuthBasic`, `AuthBearer`, `AuthApiKey` with header|query placement, `OAuth2`, `AuthInherit`, and none→null) in `core-model/src/commonMain/kotlin/io/github/crustack/core/model/opencollection/OpenCollectionAuth.kt`. Mapping per research §6: JWT maps to bearer with `extensions.clapi.authType="jwt"`
- [ ] T011 [P] Define the environment models (`Environment` under `config.environments`, `Variable`, `SecretVariable` with `secret: true` and no `value` property) in `core-model/src/commonMain/kotlin/io/github/crustack/core/model/opencollection/OpenCollectionEnvironment.kt` (FR-017, SC-007)
- [ ] T012 [P] Define the script and assertion models (`before-request`, `after-response`, `tests`, `runtime.assertions`) in `core-model/src/commonMain/kotlin/io/github/crustack/core/model/opencollection/OpenCollectionScript.kt` (research §8)
- [ ] T013 [P] Define `ClapiExtensionData` and MCP config models (MCP config lives at `extensions.clapi.mcp`) in `core-model/src/commonMain/kotlin/io/github/crustack/core/model/opencollection/OpenCollectionExtensions.kt`. Unknown fields are kept in the extensions bag (FR-007, FR-031)
- [ ] T014 Mark the legacy models in `core-model/src/commonMain/kotlin/io/github/crustack/core/model/CollectionModels.kt` as `@Deprecated` and keep them only for the existing runtime until the repository switches over. Do not add migration logic
- [ ] T015 [P] Define the execution models consumed by the network engine in `core-model/src/commonMain/kotlin/io/github/crustack/core/model/RequestModels.kt`
- [ ] T016 Define `StorageLocation` as a sealed interface with `FilePath` and `MemoryKey` variants in `core-storage/src/commonMain/kotlin/io/github/crustack/core/storage/opencollection/CollectionStorageDriver.kt` (collection-repository contract)
- [ ] T017 Define the `CollectionStorageDriver` interface with `read`, `write`, and `delete`, throwing `CollectionValidationException`, `StorageReadException`, or `StorageWriteException`, in `core-storage/src/commonMain/kotlin/io/github/crustack/core/storage/opencollection/CollectionStorageDriver.kt`
- [ ] T018 Implement the schema validator `OpenCollectionSchemaValidator` (Draft-07, bundled schema from T005) in `core-storage/src/commonMain/kotlin/io/github/crustack/core/storage/opencollection/OpenCollectionSchemaValidator.kt`. Errors must identify the path and reason (FR-006, FR-009)
- [ ] T019 Implement `OpenCollectionSerializer` for JSON and YAML (kotlinx.serialization + kaml) with unknown-field capture into `extensions` in `core-storage/src/commonMain/kotlin/io/github/crustack/core/storage/opencollection/OpenCollectionSerializer.kt` (FR-007, FR-008)

**Checkpoint**: Core model, storage interface, validator, and serializer exist. User stories can start.

---

## Phase 3: User Story 1 - Open and save collections in OpenCollection format (Priority: P1) 🎯 MVP

**Goal**: A user can open and save a collection as an OpenCollection v1.0.0 bundled document, with folder and request hierarchy preserved.

**Independent Test**: Load a valid OpenCollection v1.0.0 file, save it unchanged, and confirm the output validates against the schema with the same items, order, and hierarchy.

**Contracts**: `opencollection-document.contract.md`, `collection-repository.contract.md`

- [ ] T020 [US1] Implement `BundledCollectionStorageDriver` (single-file read/write/delete for `FilePath`) in `core-storage/src/commonMain/kotlin/io/github/crustack/core/storage/opencollection/CollectionStorageDriver.kt`. Only the bundled layout is implemented (FR-034)
- [ ] T021 [US1] Implement `CanonicalCollectionRepository` with `observeCollections`, `getCollection`, `saveCollection`, and `deleteCollection` in `core-storage/src/commonMain/kotlin/io/github/crustack/core/storage/opencollection/CanonicalCollectionRepository.kt`. Validate before write; invalid documents are never written (FR-006); no runtime state (FR-024); no filesystem leakage (FR-033)
- [ ] T022 [US1] Implement `importCollection` and `exportCollection(ExportFormat JSON|YAML)` in `core-storage/src/commonMain/kotlin/io/github/crustack/core/storage/opencollection/CanonicalCollectionRepository.kt`
- [ ] T023 [US1] Wire the workspace to the canonical repository so collections load and save through it, in `ui-shared/src/commonMain/kotlin/io/github/crustack/ui/shared/persistence/WorkspaceRepository.kt`. Keep open tabs and runtime state out of the document (FR-024)
- [ ] T024 [US1] Update import and export to read and write OpenCollection documents in `ui-shared/src/commonMain/kotlin/io/github/crustack/ui/shared/persistence/ImportExportRepository.kt`

**Checkpoint**: US1 is independently testable. Open, edit, and save round-trips a collection.

---

## Phase 4: User Story 2 - HTTP execution behaves the same on the new model (Priority: P1)

**Goal**: Requests built from the OpenCollection model produce the same wire requests as before, with inheritance resolved correctly.

**Independent Test**: For a fixture collection with folder and collection-level auth, headers, and variables, the compiled request matches the expected wire request, including precedence (request overrides folder, folder overrides collection defaults).

**Contracts**: `http-execution-adapter.contract.md`

- [ ] T025 [US2] Implement `HttpExecutionAdapter.toExecutableRequest(requestItem, parentHierarchy, collectionRoot): RequestDefinition` in `feature-requests/src/commonMain/kotlin/io/github/crustack/feature/requests/HttpExecutionAdapter.kt`. Must produce identical wire requests (FR-035, SC-003)
- [ ] T026 [US2] Implement auth and header inheritance in `HttpExecutionAdapter.kt`. `AuthInherit` traverses parents up to the collection root. Precedence: request > folder > collection defaults (FR-035)
- [ ] T027 [US2] Implement variable resolution precedence in `HttpExecutionAdapter.kt`: request/runtime > environment > collection > global (research §7)
- [ ] T028 [US2] Keep the adapter free of Compose and platform dependencies in `feature-requests/src/commonMain/kotlin/io/github/crustack/feature/requests/HttpExecutionAdapter.kt`
- [ ] T029 [US2] Route the existing request execution path through the adapter in `feature-requests/src/commonMain/kotlin/io/github/crustack/feature/requests/HttpExecutionAdapter.kt` callers (search `feature-requests` for the current `RequestDefinition` construction and replace it)

**Checkpoint**: US2 is independently testable. Execution output is unchanged for equivalent collections.

---

## Phase 5: User Story 4 - MCP config lives outside the HTTP model (Priority: P2)

**Goal**: MCP server configuration is stored under `extensions.clapi.mcp` and never mixed into HTTP request definitions.

**Independent Test**: A collection with MCP config round-trips with the MCP block intact under `extensions.clapi.mcp`, and the HTTP request payloads contain no MCP fields.

- [ ] T030 [US4] Store MCP configuration under `extensions.clapi.mcp` in `core-model/src/commonMain/kotlin/io/github/crustack/core/model/opencollection/OpenCollectionExtensions.kt` (refines T013)
- [ ] T031 [US4] Ensure `HttpExecutionAdapter.toExecutableRequest` ignores the `extensions.clapi.mcp` block in `feature-requests/src/commonMain/kotlin/io/github/crustack/feature/requests/HttpExecutionAdapter.kt`
- [ ] T032 [US4] Preserve the `extensions.clapi.mcp` block on save and export in `core-storage/src/commonMain/kotlin/io/github/crustack/core/storage/opencollection/OpenCollectionSerializer.kt`

**Checkpoint**: US4 is independently testable.

---

## Phase 6: User Story 5 - Persist only collection data (Priority: P2)

**Goal**: The collection file contains only collection data. Secrets, tabs, history, and responses are stored elsewhere.

**Independent Test**: Save a collection that has an open tab, a response, a history entry, and a secret variable value. Confirm the file has none of the runtime state, and the secret `value` is absent.

**Contracts**: `collection-repository.contract.md`

- [ ] T033 [US5] Enforce secret variables as `secret: true` with no `value` property on write in `core-storage/src/commonMain/kotlin/io/github/crustack/core/storage/opencollection/OpenCollectionSerializer.kt` (FR-017, SC-007)
- [ ] T034 [US5] Store secret values in local secure storage, keyed by environment ID and variable name, in `core-storage/src/commonMain/kotlin/io/github/crustack/core/storage/opencollection/SecretValueStore.kt` (research §7, FR-015, FR-016)
- [ ] T035 [US5] Resolve secret values from secure storage at execution time in `feature-requests/src/commonMain/kotlin/io/github/crustack/feature/requests/HttpExecutionAdapter.kt`
- [ ] T036 [US5] Keep tabs, history, and responses out of `CanonicalCollectionRepository` writes in `core-storage/src/commonMain/kotlin/io/github/crustack/core/storage/opencollection/CanonicalCollectionRepository.kt` (FR-024)

**Checkpoint**: US5 is independently testable.

---

## Phase 7: User Story 6 - Clear validation and error reporting (Priority: P3)

**Goal**: Invalid documents are rejected with an error that identifies the path and the reason. Nothing is written.

**Independent Test**: Feed documents with a wrong `opencollection` version, a folder with an HTTP request type, and a bad auth type. Each is rejected, the error names the offending path, and the file on disk is unchanged.

- [ ] T037 [US6] Return path-and-reason detail in `CollectionValidationException` from `core-storage/src/commonMain/kotlin/io/github/crustack/core/storage/opencollection/OpenCollectionSchemaValidator.kt` (FR-006, FR-009)
- [ ] T038 [US6] Surface validation errors to the user in `ui-shared/src/commonMain/kotlin/io/github/crustack/ui/shared/persistence/WorkspaceRepository.kt` without writing the invalid document (FR-006)
- [ ] T039 [US6] Map `StorageReadException` and `StorageWriteException` to user-facing messages in `ui-shared/src/commonMain/kotlin/io/github/crustack/ui/shared/persistence/ImportExportRepository.kt`

**Checkpoint**: US6 is independently testable.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Validation, cleanup, and documentation across stories

- [ ] T040 [P] Add the schema conformance suite `qa-tests/src/test/kotlin/io/github/crustack/qa/OpenCollectionValidationE2ETest.kt` that validates written documents against `opencollection_v1.0.0.json` (quickstart Scenarios 1 and 2)
- [ ] T041 [P] Add the lossless round-trip suite `qa-tests/src/test/kotlin/io/github/crustack/qa/OpenCollectionRoundTripE2ETest.kt` (SC-002, FR-008)
- [ ] T042 Run `./gradlew :core-model:desktopTest :core-storage:desktopTest :qa-tests:test` and confirm all pass (quickstart)
- [ ] T043 [P] Update `docs/Clapi/todos/02_opencollection-adoption.md` to reflect the completed work
- [ ] T044 Run the quickstart scenarios that remain in scope (1, 2, 3, 5 as numbered in quickstart.md). Scenario 4 (migration) is excluded

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies
- **Foundational (Phase 2)**: Depends on Setup. Blocks all user stories
- **User Stories (Phases 3–7)**: Depend on Foundational
  - US1 and US2 are both P1 and can run in parallel after Phase 2
  - US4, US5, and US6 are P2/P3 and can start after Phase 2. US5 depends on US1 for the repository write path, and US6 depends on US1 for validation hooks
- **Polish (Phase 8)**: Depends on all in-scope stories

### User Story Dependencies

- **US1 (P1)**: Starts after Phase 2. No dependency on other stories. MVP
- **US2 (P1)**: Starts after Phase 2. Independent of US1 (works on the in-memory model)
- **US4 (P2)**: Starts after Phase 2. Refines T013 and touches the adapter from US2
- **US5 (P2)**: Depends on US1 (write path) and US2 (secret resolution)
- **US6 (P3)**: Depends on US1 (validation on write path)

### Within Each Story

- Models before services
- Services before UI wiring
- Story complete before moving to the next priority

### Parallel Opportunities

- Setup: T002, T003, T004, T005 can run in parallel after T001 (or in any order, since they touch different files)
- Foundational: T007, T009, T010, T011, T012, T013, T015 are marked `[P]` (different files). T006, T008, T014, T016–T019 are sequential where they share a file
- US1 and US2 can run in parallel with two developers after Phase 2
- Polish: T040, T041, T043 are `[P]`

---

## Parallel Example: User Story 2

```bash
# After Phase 2, these can start together:
Task: "Implement auth and header inheritance in HttpExecutionAdapter.kt"  # T026
Task: "Implement variable resolution precedence" # T027
```

---

## Implementation Strategy

### MVP First (User Stories 1 and 2)

1. Complete Phase 1 (Setup) and Phase 2 (Foundational)
2. Complete Phase 3 (US1): open and save OpenCollection documents
3. Complete Phase 4 (US2): HTTP execution is unchanged
4. **STOP and VALIDATE**: round-trip a collection and check execution output against the fixture
5. Deliver MVP

### Incremental Delivery

1. Setup + Foundational → core model and validator ready
2. US1 + US2 → MVP
3. US4 → MCP config isolated
4. US5 → secrets and runtime state kept out of the file
5. US6 → validation and error reporting polished
6. Polish → full test suite and quickstart

---

## Notes

- Use `[P]` only for tasks touching different files with no dependency on incomplete tasks
- Each user story phase is independently testable at its checkpoint
- Commit after each task or logical group
- Avoid adding migration code of any kind; the legacy format is not supported
