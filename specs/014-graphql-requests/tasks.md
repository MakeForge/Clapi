# Tasks: GraphQL Requests

**Input**: Design documents from `specs/014-graphql-requests/` (`spec.md`, `plan.md`, `data-model.md`, `contracts/`, `research.md`, `quickstart.md`)

**Prerequisites**: `plan.md` (required), `spec.md` (required for user stories), `data-model.md`, `contracts/`, `research.md`, `quickstart.md`

**Tests**: Test tasks are included as explicitly requested in user requirements (QA tests, schema validation, and unit test suites).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `- [ ] [TaskID] [P?] [Story?] Description with file path`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., [US1], [US2], [US3]...)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Dependencies & Theme Color Configuration)

**Purpose**: Theme color additions and i18n string definitions for GraphQL requests

- [X] T001 Add `methodGraphQl` color property using `#e10098` (`Color(0xFFE10098)`) to `AppColorPalette` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/theme/AppColorPalette.kt`
- [X] T002 [P] Expose `ClapiColors.MethodGraphQl` accessor in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/theme/ClapiTheme.kt`
- [X] T003 [P] Add i18n localization keys (`new_graphql_request`, `graphql_query`, `graphql_variables`, `graphql_variants`, `graphql_prettify`) in `ui-shared/src/commonMain/resources/i18n/en.json`, `de.json`, `es.json`, and `fr.json`

---

## Phase 2: Foundational (Core Models & Schemas)

**Purpose**: Core data entities, OpenCollection schema types, and polymorphic serializers that block all user stories

**⚠️ CRITICAL**: No user story work can begin until this foundational phase is complete

- [X] T004 Extend `RequestKind` enum with `GRAPHQL` in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/McpModels.kt`
- [X] T005 [P] Define `GraphQLRequestInfo`, `GraphQLBody`, `GraphQLBodyVariant`, `GraphQLBodyPayload`, `GraphQLRequestDetails`, `GraphQLRequestSettings`, `GraphQLRequestRuntime`, and `GraphQLRequestItem` conforming to OpenCollection schema in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/OpenCollectionItem.kt`
- [X] T006 Implement `GraphQLBodyPayloadSerializer` handling polymorphic serialization of `GraphQLBodyPayload` (Single `GraphQLBody` vs Variants `List<GraphQLBodyVariant>`) in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/OpenCollectionItem.kt`
- [X] T007 Update `OpenCollectionItemSerializer` in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/OpenCollectionItem.kt` to handle polymorphic deserialization for `GraphQLRequestItem` when element has `graphql` or `info.type == "graphql"`
- [X] T008 [P] Add unit tests for `GraphQLRequestItem` serialization and polymorphic `GraphQLBodyPayload` in `core-model/src/commonTest/kotlin/io/github/crustack/clapi/core/model/opencollection/GraphQLSerializationTest.kt`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Create and Identify GraphQL Requests in Collection Sidebar (Priority: P1) 🎯 MVP

**Goal**: Enable users to create GraphQL requests via folder and collection "+" context menus and view them in sidebar tree and tab bar with a styled `GraphQLBadge` ("GQL").

**Independent Test**: Click "+" on any collection folder in sidebar, select "New GraphQL Request", and verify item is created and rendered with a "GQL" badge displaying `MethodGraphQl` (`#e10098`) accent color.

### Tests for User Story 1
- [X] T009 [P] [US1] Create UI unit test verifying `GraphQLBadge` rendering and folder "+" context menu in `ui-desktop/src/desktopTest/kotlin/io/github/crustack/clapi/ui/desktop/components/SidebarGraphQLBadgeTest.kt`

### Implementation for User Story 1
- [X] T010 [P] [US1] Create `GraphQLBadge` composable with `ClapiColors.MethodGraphQl` styling in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/Sidebar.kt`
- [X] T011 [US1] Update `CollectionTreeNode` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/Sidebar.kt` to display `GraphQLBadge` when `node.kind == RequestKind.GRAPHQL`
- [X] T012 [P] [US1] Update `RequestTabsBar` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/RequestTabsBar.kt` to render `GraphQLBadge` for tabs with `RequestKind.GRAPHQL`
- [X] T013 [US1] Update folder and collection "+" dropdown menus in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/Sidebar.kt` to include "New GraphQL Request" action
- [X] T014 [US1] Add handler `onAddGraphQLRequest` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/Sidebar.kt` and `AppState` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt` to create a `CollectionNode` with `kind = RequestKind.GRAPHQL`, method `HttpMethodType.POST`, and default URL `https://`

**Checkpoint**: User Story 1 is functional and testable independently (MVP ready).

---

## Phase 4: User Story 2 - Configure Endpoint, Method, Headers, Query Parameters, Auth, and Settings (Priority: P1)

**Goal**: Enable users to configure endpoint URL, HTTP method (POST/GET), HTTP headers, query parameters, authentication, execution settings, and documentation.

**Independent Test**: Open a GraphQL request, enter target URL with `{{variables}}`, configure custom headers and query params, select Bearer authentication, update timeout settings, and verify all values persist in tab state.

### Implementation for User Story 2
- [X] T015 [US2] Extend `RequestTabState` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt` with fields for `graphQlMethod`, `graphQlSettings`, and `graphQlDocs`
- [X] T016 [P] [US2] Implement top address bar in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/GraphQLRequestEditor.kt` with `GraphQLBadge`, HTTP method selector (POST/GET), URL input field with variable resolution preview, Send button, and Save button
- [X] T017 [US2] Implement "Headers", "Params", "Auth" (using `AuthEditor`), "Settings" (timeout, encodeUrl, followRedirects, forwardAuthHeader, maxRedirects), and "Docs" tabs in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/GraphQLRequestEditor.kt`
- [X] T018 [US2] Route `tab.kind == RequestKind.GRAPHQL` to `GraphQLRequestEditor` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/RequestEditor.kt`

**Checkpoint**: User Stories 1 and 2 operate together independently.

---

## Phase 5: User Story 3 - Author GraphQL Queries, Variables, and Manage Payload Variants (Priority: P1)

**Goal**: Provide a specialized "Body" tab with syntax-highlighted GraphQL query and JSON variables editors, query formatting, and body variants management.

**Independent Test**: In the Body tab of a GraphQL request, write a query and variables, format the query, add a second body variant with a different title and query, switch between variants, and verify content preserves correctly.

### Implementation for User Story 3
- [X] T019 [US3] Extend `RequestTabState` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/AppState.kt` with `graphQlQuery`, `graphQlVariables`, `graphQlVariants`, and `graphQlActiveVariantIndex`
- [X] T020 [P] [US3] Implement Body variant management strip (add variant, select active variant, rename variant, remove variant) in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/GraphQLRequestEditor.kt`
- [X] T021 [US3] Integrate GraphQL query editor with line numbers, `highlightGraphql` syntax highlighting, brace folding, and "Prettify" query formatting action in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/GraphQLRequestEditor.kt`
- [X] T022 [US3] Integrate Variables editor with line numbers, `highlightJson` syntax highlighting, JSON formatting, and dynamic variable interpolation in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/GraphQLRequestEditor.kt`

**Checkpoint**: User Stories 1, 2, and 3 provide a complete authoring experience.

---

## Phase 6: User Story 4 - Execute GraphQL Operations and Inspect Structured Responses (Priority: P1)

**Goal**: Execute GraphQL queries over HTTP (POST JSON envelope or GET query parameters), handle pre-request/test scripts, and display structured responses in the response viewer.

**Independent Test**: Target `DummyApiServer` at `http://localhost:<port>/graphql`, send a GraphQL query with variables, and verify that HTTP 200 is received with status timing and syntax-highlighted JSON response body in `ResponseViewer`.

### Implementation for User Story 4
- [X] T023 [US4] Implement `sendGraphQlRequestInternal` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/RequestExecutor.kt` that resolves variables, constructs POST JSON envelope via `encodeGraphQlEnvelope` or GET query parameters, and executes via network client
- [X] T024 [US4] Wire `tab.kind == RequestKind.GRAPHQL` execution handling in `sendRequestInternal` and hotkeys in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/RequestExecutor.kt`
- [X] T025 [US4] Integrate pre-request and post-request test scripts and assertions execution for GraphQL requests in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/RequestExecutor.kt`
- [X] T026 [US4] Display status badge, latency, response headers, and formatted JSON body using `ResponseViewer` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/GraphQLRequestEditor.kt`

**Checkpoint**: End-to-end execution and response inspection are fully functional.

---

## Phase 7: User Story 5 - OpenCollection v1.0.0 Round-Trip Persistence and Legacy Compatibility (Priority: P2)

**Goal**: Ensure GraphQL requests are serialized, saved, imported, and exported in full compliance with OpenCollection v1.0.0 JSON/YAML specifications, and support legacy HTTP requests with GraphQL body.

**Independent Test**: Save a collection with a GraphQL request to disk, export to OpenCollection JSON/YAML, re-import into a clean session, and assert that 100% of fields are preserved without schema errors.

### Implementation for User Story 7
- [X] T027 [P] [US5] Update `OpenCollectionSerializer.kt` in `core-storage/src/commonMain/kotlin/io/github/crustack/clapi/core/storage/opencollection/OpenCollectionSerializer.kt` to include `KNOWN_GRAPHQL_REQUEST_KEYS` and handle serialization/deserialization for `GraphQLRequestItem`
- [X] T028 [US5] Update `OpenCollectionModelMapping.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/OpenCollectionModelMapping.kt` to map between `CollectionNode`/`RequestTabState` and `GraphQLRequestItem`
- [X] T029 [P] [US5] Update `OpenApiExporter.kt` and `OpenCollectionImporter.kt` in `core-storage/src/commonMain/kotlin/io/github/crustack/clapi/core/storage/` to handle `GraphQLRequestItem`
- [X] T030 [P] [US5] Update `TabsRepository.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/TabsRepository.kt` to persist and restore `RequestKind.GRAPHQL` open tab states across app restarts
- [X] T031 [US5] Add backward compatibility check in `OpenCollectionModelMapping.kt` in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/OpenCollectionModelMapping.kt` ensuring legacy HTTP requests with `BodyType.GRAPHQL` continue to load and can be converted to first-class GraphQL requests

**Checkpoint**: Full round-trip persistence and legacy compatibility verified.

---

## Phase 8: QA Integration & Automated Verification

**Purpose**: Automated end-to-end regression testing against test server and collection round-trip validation

- [X] T032 [P] Create OpenCollection test fixture with single-body and multi-variant GraphQL requests in `qa-tests/fixtures/graphql-test-collection.json`
- [X] T033 Update `OpenCollectionRoundTripE2ETest.kt` in `qa-tests/src/test/kotlin/io/github/crustack/clapi/qa/OpenCollectionRoundTripE2ETest.kt` to verify `GraphQLRequestItem` survives save/load and YAML export/import round-trips
- [X] T034 [P] Create `GraphQLRequestE2ETest.kt` in `qa-tests/src/test/kotlin/io/github/crustack/clapi/qa/GraphQLRequestE2ETest.kt` testing query execution, variables interpolation, headers, and auth against `DummyApiServer` `/graphql` endpoint

**Checkpoint**: Automated regression suite verifies GraphQL requests against mock server and schema validator in CI.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Cleanup, formatting, documentation, and validation

- [X] T035 [P] Run code formatting and static checks across modified modules (`core-model`, `core-network`, `core-storage`, `ui-shared`, `qa-tests`)
- [X] T036 [P] Update `FEATURES.md` and `docs/architecture.md` to document GraphQL first-class request support
- [X] T037 Execute manual verification steps per `specs/014-graphql-requests/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3-7)**:
  - US1 (Sidebar & Creation) depends on Phase 2
  - US2 (URL, Headers, Params, Auth, Settings) depends on Phase 2
  - US3 (Query, Variables, Body Variants) depends on US2
  - US4 (Execution & Responses) depends on US2 and US3
  - US5 (Import/Export & Migration) depends on Phase 2
- **QA Integration (Phase 8)**: Depends on US4 and US5
- **Polish (Phase 9)**: Depends on all user stories and QA tests being complete

### User Story Completion Order

```text
Foundational (Phase 2)
      │
      ├──► US1: Sidebar & Folder Creation (P1) [MVP]
      │
      ├──► US2: URL, Method, Headers, Params, Auth, Settings (P1)
      │         │
      │         ▼
      │     US3: Query, Variables & Body Variants (P1)
      │         │
      │         ▼
      │     US4: Execution & Response Viewer (P1)
      │         │
      ├──► US5: OpenCollection Import/Export & Compatibility (P2)
      │         │
      └─────────┴──► QA Test Suite (Phase 8)
                           │
                           ▼
                     Polish (Phase 9)
```

---

## Parallel Execution Opportunities

- **Phase 1**: T001, T002, T003 can be prepared in parallel
- **Phase 2**: T004, T005, T007 (models) and T008 (tests) can proceed in parallel
- **Phase 3 (US1)**: T009 (test), T010 (badge composable), and T012 (tab bar) can be developed in parallel
- **Phase 4 (US2)**: T015 (tab state) and T016 (address bar) can run in parallel
- **Phase 5 (US3)**: T020 (variant manager) and T021 (query editor) can run in parallel
- **Phase 7 (US5)**: T027 (`OpenCollectionSerializer`) can run in parallel with T029 (`OpenApiExporter`) and T030 (`TabsRepository`)
- **Phase 8 (QA)**: T032 (fixture JSON) and T034 (E2E test) can run in parallel

---

## Implementation Strategy

### MVP First (User Story 1 Only)
1. Complete Phase 1: Theme color `#e10098` and localization
2. Complete Phase 2: Foundational models (`GraphQLRequestItem`, `RequestKind.GRAPHQL`)
3. Complete Phase 3: User Story 1 (Folder "+" menu, `GraphQLBadge`, creation)
4. **Validate**: Verify GraphQL requests can be created and displayed in the sidebar tree with the distinct "GQL" badge.

### Incremental Delivery
1. Add User Story 2: Method selector, URL with variables, headers, query params, auth, settings
2. Add User Story 3: GraphQL query editor with formatting, variables editor, and body variants
3. Add User Story 4: Execution via Ktor and response visualization in `ResponseViewer`
4. Add User Story 5: OpenCollection v1.0.0 import/export persistence and legacy HTTP compatibility
5. Add QA Test Suite: Automated regression tests against `DummyApiServer` in CI
