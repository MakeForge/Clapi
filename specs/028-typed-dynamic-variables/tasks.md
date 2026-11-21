# Tasks: Typed Dynamic Variables & Variable Editor UI Modernization

**Input**: Design documents from `specs/028-typed-dynamic-variables/` (`spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`)

**Prerequisites**: `plan.md` (required), `spec.md` (required for user stories), `data-model.md`, `contracts/`

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `- [ ] [ID] [P?] [Story] Description with file path`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., [US1], [US2], [US3], [US4])
- Exact file paths are included in all task descriptions.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify clean build and test baseline before making changes

- [X] T001 Verify project baseline and test suite passing via `rtk gradlew :core-model:desktopTest :editor-core:desktopTest :ui-shared:desktopTest`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core data model and serialization contracts in `:core-model` that MUST be complete before UI and editor features can be implemented.

- [X] T002 Refactor `VariableType` enum in `core-model/src/commonMain/kotlin/io/github/makeforge/clapi/core/model/opencollection/OpenCollectionEnvironment.kt` to contain only primitive data types (`STRING("string")`, `NUMBER("number")`, `BOOLEAN("boolean")`, `NULL("null")`, `OBJECT("object")`) and remove `DYNAMIC("dynamic")`.
- [X] T003 Create `VariableKind` enum (`REGULAR("regular")`, `SECRET("secret")`, `DYNAMIC("dynamic")`) in `core-model/src/commonMain/kotlin/io/github/makeforge/clapi/core/model/opencollection/OpenCollectionEnvironment.kt`.
- [X] T004 Update `DynamicVariable` (`val type: VariableType = VariableType.STRING`), `RegularVariable` (`val type: VariableType = VariableType.STRING`), and `SecretVariable` (`val type: VariableType = VariableType.STRING`) in `core-model/src/commonMain/kotlin/io/github/makeforge/clapi/core/model/opencollection/OpenCollectionEnvironment.kt` per `contracts/variable-data-model-contract.md`.
- [X] T005 Update `VariableEntrySerializer` in `core-model/src/commonMain/kotlin/io/github/makeforge/clapi/core/model/opencollection/OpenCollectionEnvironment.kt` to polymorphically deserialize based on `secret == true`, presence of `script` or legacy `type == "dynamic"`, and map legacy `type == "dynamic"` to `VariableType.STRING`.
- [X] T006 [P] Update model serialization tests in `core-model/src/commonTest/kotlin/io/github/makeforge/clapi/core/model/opencollection/DynamicVariableSerializationTest.kt` to assert `DynamicVariable` serializes with `type: VariableType` (number, boolean, object, string) and deserializes legacy `"type": "dynamic"` losslessly.
- [X] T007 [P] Update `OpenCollectionModelTest.kt` in `core-model/src/commonTest/kotlin/io/github/makeforge/clapi/core/model/opencollection/OpenCollectionModelTest.kt` and `DynamicVariableStorageTest.kt` in `core-storage/src/commonTest/kotlin/io/github/makeforge/clapi/core/storage/DynamicVariableStorageTest.kt` to match updated `VariableType` entries.

**Checkpoint**: Core data model and OpenCollection serialization clean. Foundational phase complete.

---

## Phase 3: User Story 1 - Define Variable Kind & Data Type via Selection Boxes (Priority: P1) 🎯 MVP

**Goal**: Users can explicitly configure variable kind (Regular, Secret, Dynamic) and variable data type (String, Number, Boolean, Object) via explicit selection boxes in variable tables and the dynamic variable editor dialog.

**Independent Test**: Open Environment Editor, create a variable, select kind "Dynamic" and type "Number", edit script to return `42`, and verify the variable persists with kind=Dynamic, type=Number and displays appropriate UI controls.

### Implementation for User Story 1

- [X] T008 [US1] Update `MutableKeyValue` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/EditorRowModels.kt` to add `var type: VariableType by mutableStateOf(VariableType.STRING)` and `var kind: VariableKind` getter/setter mapping to `secret` and `isDynamic`.
- [X] T009 [US1] Update `toDynamicVariable()`, `toKeyValueEntry()`, and mapping functions in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/persistence/OpenCollectionModelMapping.kt` to map `type` between `DynamicVariable`, `RegularVariable`, `SecretVariable`, and `MutableKeyValue`.
- [X] T010 [US1] Refactor `EnvVariableTable.kt` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/dialogs/env/EnvVariableTable.kt` to replace the cycling badge with two explicit selection dropdowns: Variable Kind (`Regular`, `Secret`, `Dynamic`) and Variable Type (`String`, `Number`, `Boolean`, `Object`) per `contracts/variable-editor-ui-contract.md`.
- [X] T011 [US1] Update `DynamicVariableEditorDialog.kt` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/dialogs/dynamicvar/DynamicVariableEditorDialog.kt` to include a Return Type dropdown selector (`String`, `Number`, `Boolean`, `Object`), dynamic template preview banner (`{ "key": {{var}} }` vs `{ "key": "{{var}}" }`), and test run result type mismatch warning.
- [X] T012 [P] [US1] Add string resources in `ui-shared/src/commonMain/resources/i18n/` (`en.json`, `de.json`, `es.json`, `fr.json`) for variable kind labels (`regular`, `secret`, `dynamic`) and data type labels (`type_string`, `type_number`, `type_boolean`, `type_object`).
- [X] T013 [P] [US1] Update UI tests in `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/DynamicVariableEditorDialogTest.kt` to verify return type selection, persistence into `MutableKeyValue`, and template preview updates.

**Checkpoint**: At this point, User Story 1 is fully functional and testable independently.

---

## Phase 4: User Story 2 - Template-Aware JSON Request Body Validation in Code Editor (Priority: P1)

**Goal**: The code editor's live JSON/JSON5 linter accepts unquoted template variables in JSON value positions without reporting false-positive `"Expected property name at offset 7"` syntax errors, and provides actionable diagnostics for unquoted string variables.

**Independent Test**: Enter `{"x": {{numberVar}} }` or `{"ts": {{$timestamp}} }` in the JSON request body editor; verify 0 syntax errors are reported. Enter `{"str": {{stringVar}} }`; verify an actionable recommendation is displayed advising double quotes.

### Implementation for User Story 2

- [X] T014 [US2] Implement offset-preserving template masking algorithm in `editor-core/src/commonMain/kotlin/io/github/makeforge/clapi/editor/core/Json5EditorSupport.kt` per `contracts/editor-template-validation-contract.md`, replacing unquoted `{{...}}` tokens in value positions with whitespace-padded dummy JSON literals (`"0".padEnd(len, ' ')`) before passing to `Json5.parseToJsonElement`.
- [X] T015 [US2] Implement offset-preserving template masking algorithm in `editor-core/src/commonMain/kotlin/io/github/makeforge/clapi/editor/core/JsonMode.kt` for strict JSON mode validation.
- [X] T016 [US2] Enhance `editor-core/src/commonMain/kotlin/io/github/makeforge/clapi/editor/core/LanguageMode.kt` and `EditorEngine.kt` to accept optional variable definitions or variable type resolver callback during document validation.
- [X] T017 [US2] Add diagnostic check in `editor-core/src/commonMain/kotlin/io/github/makeforge/clapi/editor/core/Json5EditorSupport.kt` and `JsonMode.kt` to detect unquoted variable tokens whose resolved type is `VariableType.STRING`, emitting an `InlineEditorError` at the exact token offset advising double quotes.
- [X] T018 [US2] Wire active variable layers and dynamic variable types from `RequestTabState` into `EditorViewModel.kt` in `editor-ui/src/commonMain/kotlin/io/github/makeforge/clapi/editor/ui/EditorViewModel.kt` during background diagnostics scheduling.
- [X] T019 [P] [US2] Create unit tests in `editor-core/src/commonTest/kotlin/io/github/makeforge/clapi/editor/core/Json5EditorSupportTest.kt` and `editor-core/src/commonTest/kotlin/io/github/makeforge/clapi/editor/core/JsonModeTest.kt` verifying:
  - `{"x": {{num}} }` reports 0 syntax errors
  - `{"ts": {{$timestamp}} }` reports 0 syntax errors
  - `{"b": {{active}}, "obj": {{meta}} }` reports 0 syntax errors
  - `{"s": {{unquotedString}} }` reports diagnostic recommending double quotes
  - Genuine JSON syntax errors (unclosed brace, missing colon) still report at exact line and column.
- [X] T020 [P] [US2] Update `EditorEngineIntegrationTest.kt` in `ui-shared/src/commonTest/kotlin/io/github/makeforge/clapi/ui/shared/components/EditorEngineIntegrationTest.kt` to verify template-aware validation across body editor tabs.

**Checkpoint**: At this point, User Stories 1 AND 2 are complete. Editor syntax error on unquoted numeric/boolean variables is completely resolved.

---

## Phase 5: User Story 3 - Valid Wire Transmission of Typed Variable Bodies (Priority: P2)

**Goal**: Outgoing HTTP requests with unquoted dynamic and static variables in JSON bodies send valid RFC 8259 JSON numbers, booleans, and objects to API servers.

**Independent Test**: Execute an HTTP request with body `{"count": {{numberVar}}, "active": {{boolVar}} }` against `DummyApiServer` and verify the server receives valid JSON with native numeric and boolean primitive fields.

### Implementation for User Story 3

- [X] T021 [US3] Add execution-time type validation in `core-network/src/commonMain/kotlin/io/github/makeforge/clapi/core/network/dynamicvar/UserScriptDynamicVariableProvider.kt` ensuring script return values match `dynamicVar.type` (`Number`, `Boolean`, `Object`) or throw `DynamicVariableResolutionException`.
- [X] T022 [US3] Verify `VariableResolver.kt` in `core-network/src/commonMain/kotlin/io/github/makeforge/clapi/core/network/VariableResolver.kt` correctly interpolates unquoted user-defined and built-in dynamic variables into request bodies.
- [X] T023 [P] [US3] Add unit tests in `core-network/src/commonTest/kotlin/io/github/makeforge/clapi/core/network/DynamicVariableExecutionTest.kt` verifying type enforcement on dynamic script evaluation (valid numbers pass, invalid non-numbers throw descriptive errors).
- [X] T024 [P] [US3] Add end-to-end integration test in `qa-tests/src/test/kotlin/io/github/makeforge/clapi/qa/DynamicVariablesE2ETest.kt` executing an HTTP request with unquoted dynamic number, boolean, and object variables against `DummyApiServer`, asserting the received wire body contains native JSON types.

**Checkpoint**: At this point, end-to-end execution of typed variables is verified.

---

## Phase 6: User Story 4 - Complete Documentation & Authoring Guidance (Priority: P3)

**Goal**: Documentation thoroughly and accurately reflects the Kind and Type selection boxes, JSON template authoring rules, and editor validation diagnostics.

**Independent Test**: Review updated documentation pages and verify all examples, schemas, and descriptions match the implemented behavior.

### Implementation for User Story 4

- [X] T025 [P] [US4] Update `docs/features/dynamic-variables.md` to document the Kind selection box (`Regular`, `Secret`, `Dynamic`), Type selection box (`String`, `Number`, `Boolean`, `Object`), OpenCollection schema updates, and JSON body quoting rules (when to quote vs unquote).
- [X] T026 [P] [US4] Update `../../docs/organization/environments` and related environment documentation pages with updated table column guides and template syntax examples.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Verification, cleanup, and quality gates across all modules

- [X] T027 [P] Execute full unit and integration test suite across all modules: `rtk gradlew test desktopTest`
- [X] T028 [P] Run quickstart validation scenarios defined in `specs/028-typed-dynamic-variables/quickstart.md`
- [X] T029 Clean up any unused imports or obsolete comments

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - baseline check.
- **Foundational (Phase 2)**: Depends on Phase 1 - BLOCKS all user stories because it refactors `VariableType` in `:core-model`.
- **User Story 1 (Phase 3)**: Depends on Phase 2 - Implements UI selection boxes and model mappings.
- **User Story 2 (Phase 4)**: Depends on Phase 2 - Implements template-aware editor validation. Can proceed in parallel with US1.
- **User Story 3 (Phase 5)**: Depends on Phase 2 - Adds execution type enforcement and E2E verification. Can proceed in parallel with US1/US2.
- **User Story 4 (Phase 6)**: Depends on US1/US2 design finalization.
- **Polish (Phase 7)**: Depends on all user stories being complete.

### Parallel Opportunities

- **Phase 2 (Foundational)**:
  - T006 (`DynamicVariableSerializationTest.kt`) and T007 (`OpenCollectionModelTest.kt`) can run in parallel once T002-T005 are written.
- **Phase 3 & Phase 4 (US1 & US2)**:
  - User Story 1 (UI selection boxes in `:ui-shared`) and User Story 2 (Editor validation in `:editor-core`) touch completely disjoint modules and can be implemented in parallel!
- **Phase 5 & Phase 6 (US3 & US4)**:
  - E2E tests in `:qa-tests` (T024) and documentation in `docs/` (T025, T026) can proceed in parallel.

---

## Implementation Strategy

### MVP First (User Story 1 & User Story 2)

1. **Step 1**: Complete Phase 2 Foundational (`VariableType` refactoring in `:core-model`).
2. **Step 2**: Implement User Story 1 (Variable Kind & Type dropdowns in variable tables and dialog).
3. **Step 3**: Implement User Story 2 (Template-aware JSON validation in `:editor-core`), directly solving the `"Expected property name at offset 7"` bug.
4. **Step 4**: Validate User Stories 1 and 2 independently via unit and UI tests.

### Incremental Delivery

1. Foundation ready (`core-model` cleanly typed).
2. UI updated with Kind and Type selection dropdowns (US1).
3. Editor live linter accepts unquoted numeric/boolean variables (US2).
4. Outgoing requests verified end-to-end with typed wire JSON (US3).
5. Documentation updated (US4).
