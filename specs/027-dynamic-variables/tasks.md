# Tasks: User-Defined Scriptable Dynamic Variables

**Input**: Design documents from `/specs/027-dynamic-variables/` (`spec.md`, `plan.md`, `data-model.md`, `contracts/`, `research.md`, `quickstart.md`)

**Prerequisites**: `plan.md` (required), `spec.md` (required for user stories), `data-model.md`, `contracts/`

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`[US1]`, `[US2]`, `[US3]`, `[US4]`, `[US5]`, `[US6]`)
- Exact file paths are included in every description

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project preparation and baseline module setup for dynamic variables.

- [X] T001 Verify project build and test baseline via `./gradlew build -x test` from repository root
- [X] T002 [P] Create dynamic variable UI package directory `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/dialogs/dynamicvar`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core data models, polymorphic serialization, and scripting engine contracts required before any user story can proceed.

**⚠️ CRITICAL**: Must be completed before user story implementation begins.

- [X] T003 Add `DynamicVariable` (`name: String`, `script: String = ""`, `parameters: List<DynamicVariableParameter> = emptyList()`, `description: Description? = null`, `disabled: Boolean? = null`), `DynamicVariableParameter` (`name: String`, `description: String? = null`, `defaultValue: String? = null`), and `VariableType.DYNAMIC("dynamic")` to `core-model/src/commonMain/kotlin/io/github/makeforge/clapi/core/model/opencollection/OpenCollectionEnvironment.kt`
- [X] T004 Update `VariableEntrySerializer` polymorphic deserializer in `core-model/src/commonMain/kotlin/io/github/makeforge/clapi/core/model/opencollection/OpenCollectionEnvironment.kt` to route elements with `"type": "dynamic"` or `"script"` property to `DynamicVariable.serializer()`
- [X] T005 [P] Create model unit tests for `DynamicVariable` polymorphic serialization and deserialization in `core-model/src/commonTest/kotlin/io/github/makeforge/clapi/core/model/opencollection/DynamicVariableSerializationTest.kt`
- [X] T006 Add `DynamicScriptContext`, `RequestContext`, and `DynamicScriptExecutionResult` contracts to `core-scripting/src/commonMain/kotlin/io/github/makeforge/clapi/core/scripting/ScriptingContracts.kt` per `contracts/dynamic-variable-execution-contract.md`
- [X] T007 Implement `DynamicVariableEngine` using Javet V8 with IIFE sandboxing, 2000ms timeout guard, and console capture in `core-scripting/src/commonMain/kotlin/io/github/makeforge/clapi/core/scripting/DynamicVariableEngine.kt`

**Checkpoint**: Core data structures and execution engine contracts ready. User story implementation can now begin.

---

## Phase 3: User Story 1 - Defining & Executing Dynamic Variables (Priority: P1) 🎯 MVP

**Goal**: Users can define dynamic variables with custom scripts in environments and collections, and have them resolved freshly on every request execution.

**Independent Test**: Define a dynamic variable `currentEpochSec` with script `return Math.floor(Date.now() / 1000);` in an environment, reference `{{currentEpochSec}}` in a request URL, execute the request, and verify that the dynamically computed timestamp is injected into the outgoing request.

### Tests for User Story 1

- [X] T008 [P] [US1] Unit test for dynamic script evaluation and return value coercion in `core-scripting/src/commonTest/kotlin/io/github/makeforge/clapi/core/scripting/DynamicVariableEngineTest.kt`
- [X] T009 [P] [US1] Unit test for dynamic variable token resolution in `core-network/src/commonTest/kotlin/io/github/makeforge/clapi/core/network/DynamicVariableResolverTest.kt`

### Implementation for User Story 1

- [X] T010 [P] [US1] Expand `MutableKeyValue` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/EditorRowModels.kt` with observable properties `isDynamic: Boolean`, `dynamicScript: String`, and `dynamicParameters: SnapshotStateList<DynamicVariableParameter>`
- [X] T011 [US1] Update `OpenCollectionModelMapping.kt` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/persistence/OpenCollectionModelMapping.kt` to map `DynamicVariable` to/from `MutableKeyValue`
- [X] T012 [US1] Integrate `DynamicVariableEngine` into `core-network/src/commonMain/kotlin/io/github/makeforge/clapi/core/network/VariableResolver.kt` to resolve non-parameterized dynamic tokens `{{variableName}}` via dynamic script execution
- [X] T013 [US1] Wire dynamic variable resolution into request execution lifecycles in `core-network/src/commonMain/kotlin/io/github/makeforge/clapi/core/network/execution/RequestExecutionService.kt` and `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/executor/RequestExecutionPipeline.kt`
- [X] T014 [US1] Update `EnvVariableRow` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/dialogs/env/EnvVariableTable.kt` to support a 3-way variable type toggle (`Normal`, `Secret`, `Dynamic`) and replace the static value text field with an `[Edit Script]` trigger button when `isDynamic = true`
- [X] T015 [US1] Update the quick-add row in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/dialogs/env/EnvironmentEditDialog.kt` to allow adding dynamic variables directly from the top row

**Checkpoint**: User Story 1 is functional and testable as an MVP slice.

---

## Phase 4: User Story 2 - Dynamic Script Editor Dialog with Integrated Test Runner (Priority: P1)

**Goal**: Provide an interactive desktop subdialog featuring a code editor on the left and a test execution sandbox on the right for authoring, running, and debugging scripts.

**Independent Test**: Open `DynamicVariableEditorDialog`, write a JavaScript snippet (e.g. `return "VAL_" + (10 * 2);`), click "Run Test", and verify that the test panel displays `"VAL_20"`, duration in milliseconds, and a Success badge.

### Tests for User Story 2

- [X] T016 [P] [US2] Compose desktop UI test for `DynamicVariableEditorDialog` in `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/DynamicVariableEditorDialogTest.kt`

### Implementation for User Story 2

- [X] T017 [P] [US2] Implement `DynamicVariableTestPanel.kt` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/dialogs/dynamicvar/DynamicVariableTestPanel.kt` displaying parameter inputs, mock request context inputs, "Run Test" trigger, status badge, return value display, duration badge, logs, and error banner
- [X] T018 [US2] Implement `DynamicVariableEditorDialog.kt` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/dialogs/dynamicvar/DynamicVariableEditorDialog.kt` with a two-pane layout: `CodeEditor` on the left pane and `DynamicVariableTestPanel` on the right pane, with Save and Discard actions
- [X] T019 [US2] Connect `[Edit Script]` click handlers in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/dialogs/env/EnvVariableTable.kt` and `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/collection/CollectionEditor.kt` to open `DynamicVariableEditorDialog`

**Checkpoint**: User Stories 1 and 2 work together seamlessly.

---

## Phase 5: User Story 3 - Parameterized Dynamic Variables with Arguments (Priority: P2)

**Goal**: Users can declare primitive parameters for dynamic variables and invoke them with arguments in requests (e.g. `{{secondsFromNow(10)}}` or `{{formatPrefix("ID", 42)}}`).

**Independent Test**: Define a dynamic variable `secondsFromNow` with a parameter `seconds`, call it via `{{secondsFromNow(15)}}` in a request, and verify that the script receives `15` as an argument and outputs an ISO timestamp 15 seconds in the future.

### Tests for User Story 3

- [X] T020 [P] [US3] Unit test for argument tokenizer and function-call expression parsing per `contracts/dynamic-variable-template-syntax-contract.md` in `core-network/src/commonTest/kotlin/io/github/makeforge/clapi/core/network/DynamicVariableSyntaxTest.kt`

### Implementation for User Story 3

- [X] T021 [US3] Extend token regex and argument tokenizer in `core-network/src/commonMain/kotlin/io/github/makeforge/clapi/core/network/VariableResolver.kt` to parse `{{name(arg1, arg2)}}` expressions supporting string, number, boolean, and null literals
- [X] T022 [US3] Update `DynamicVariableEngine.kt` in `core-scripting/src/commonMain/kotlin/io/github/makeforge/clapi/core/scripting/DynamicVariableEngine.kt` to map parsed argument lists to declared parameter names in script execution context
- [X] T023 [US3] Add parameter declaration management (add, edit name, remove parameter chips) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/dialogs/dynamicvar/DynamicVariableEditorDialog.kt`
- [X] T024 [US3] Update variable autocomplete suggestions in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/variable/VariableDropdownList.kt` to show parameter signatures (e.g. `secondsFromNow(seconds)`) for parameterized dynamic variables

**Checkpoint**: Parameterized dynamic variables are fully functional.

---

## Phase 6: User Story 4 - Contextual Request Information in Dynamic Scripts (Priority: P2)

**Goal**: Provide dynamic variable scripts with read-only `context.request` details (`url`, `method`, `headers`, `name`) during request execution and customizable mock context during test runs.

**Independent Test**: Define a dynamic variable `signPath` with script `return context.request.method + ":" + context.request.url;`, invoke `{{signPath}}` in a request header, send `GET https://httpbin.org/get`, and verify the header resolves to `GET:https://httpbin.org/get`.

### Tests for User Story 4

- [X] T025 [P] [US4] Unit test for request context injection and read-only behavior in `core-scripting/src/commonTest/kotlin/io/github/makeforge/clapi/core/scripting/DynamicVariableRequestContextTest.kt`

### Implementation for User Story 4

- [X] T026 [US4] Capture and pass active request context (`url`, `method`, `headers`, `name`) into `VariableResolver` during request dispatch in `core-network/src/commonMain/kotlin/io/github/makeforge/clapi/core/network/execution/RequestExecutionService.kt`
- [X] T027 [US4] Add expandable mock request context inputs (Mock URL, Method, Headers) to `DynamicVariableTestPanel.kt` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/dialogs/dynamicvar/DynamicVariableTestPanel.kt`

**Checkpoint**: Request context is accessible in dynamic scripts and testable in the editor.

---

## Phase 7: User Story 5 - Persistence, Import/Export & OpenCollection Compatibility (Priority: P2)

**Goal**: Dynamic variables serialize and deserialize losslessly in OpenCollection JSON and YAML formats during collection export and import.

**Independent Test**: Export an environment with dynamic variables to OpenCollection YAML, re-import into a clean environment, and verify all scripts, parameters, and metadata are intact.

### Tests for User Story 5

- [X] T028 [P] [US5] Integration test for OpenCollection YAML & JSON export/import of dynamic variables in `core-storage/src/commonTest/kotlin/io/github/makeforge/clapi/core/storage/DynamicVariableStorageTest.kt`

### Implementation for User Story 5

- [X] T029 [US5] Ensure `kaml` YAML serializer in `core-storage/src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/OpenCollectionYaml.kt` encodes and decodes `DynamicVariable` entries per `contracts/dynamic-variable-serialization-contract.md`
- [X] T030 [US5] Update environment and collection import/export routines in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/persistence/OpenCollectionModelMapping.kt` to preserve dynamic variables across workspace imports

**Checkpoint**: Full persistence and export/import roundtrip verified.

---

## Phase 8: User Story 6 - Dedicated Documentation for Dynamic Variables (Priority: P3)

**Goal**: Provide comprehensive documentation in VitePress covering all built-in dynamic variables and user-defined scripting guides.

**Independent Test**: Build docs with `npm run docs:build` and verify the `/features/dynamic-variables` page renders with built-in tables, scripting guides, parameter instructions, and copyable code examples.

### Implementation for User Story 6

- [X] T031 [P] [US6] Create `docs/features/dynamic-variables.md` with Built-in Dynamic Variables reference (`$timestamp`, `$isoTimestamp`, `$randomInt`, `$guid`, etc.) and User-Defined Dynamic Variables scripting guide with example snippets
- [X] T032 [US6] Register `Dynamic Variables` under features navigation and sidebar in `docs/.vitepress/config.mts`

**Checkpoint**: Documentation is complete and published.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: End-to-end integration testing, quickstart verification, and codebase cleanup.

- [X] T033 Create end-to-end multiplatform integration test in `qa-tests/src/test/kotlin/io/github/makeforge/clapi/qa/DynamicVariablesE2ETest.kt` validating dynamic variable resolution in HTTP requests and roundtrip export/import
- [X] T034 Execute runnable validation scenarios in `specs/027-dynamic-variables/quickstart.md` and verify all tests pass
- [X] T035 [P] Code cleanup, import optimization, and formatting check across all modified modules

---

## Dependencies & Execution Order

### Phase Dependencies

```text
Phase 1: Setup
    │
    ▼
Phase 2: Foundational (T003 - T007) [CRITICAL BLOCKER]
    │
    ├───────────────────────┬───────────────────────┐
    ▼                       ▼                       ▼
Phase 3: US1 (P1)       Phase 4: US2 (P1)       Phase 8: US6 (P3)
(T008 - T015)           (T016 - T019)           (T031 - T032)
    │                       │
    ├───────────────────────┤
    ▼                       ▼
Phase 5: US3 (P2)       Phase 6: US4 (P2)
(T020 - T024)           (T025 - T027)
    │                       │
    └───────────┬───────────┘
                ▼
        Phase 7: US5 (P2)
        (T028 - T030)
                │
                ▼
        Phase 9: Polish
        (T033 - T035)
```

### User Story Dependencies

- **User Story 1 (P1)**: Depends on Phase 2 (Foundational). Delivers MVP.
- **User Story 2 (P1)**: Depends on Phase 2 (Foundational) and integrates with US1 UI components (`EnvVariableTable`).
- **User Story 3 (P2)**: Depends on US1 (resolver and model mapping) and US2 (dialog parameter management).
- **User Story 4 (P2)**: Depends on US1 (execution service) and US2 (test panel mock context).
- **User Story 5 (P2)**: Depends on US1 (model mapping) and Phase 2.
- **User Story 6 (P3)**: Can proceed independently in parallel once feature requirements are settled.

---

## Parallel Execution Examples

### Parallel Example: User Story 1
```bash
# Launch test tasks in parallel:
Task: T008 [P] [US1] Unit test for dynamic script evaluation in core-scripting/src/commonTest/.../DynamicVariableEngineTest.kt
Task: T009 [P] [US1] Unit test for dynamic variable token resolution in core-network/src/commonTest/.../DynamicVariableResolverTest.kt
```

### Parallel Example: User Story 2 & 3
```bash
# Work on test panel and syntax tests in parallel:
Task: T017 [P] [US2] Implement DynamicVariableTestPanel.kt in ui-shared/.../DynamicVariableTestPanel.kt
Task: T020 [P] [US3] Unit test for argument tokenizer in core-network/.../DynamicVariableSyntaxTest.kt
```

---

## Implementation Strategy

### MVP First (Phases 1-3)
1. Complete Setup and Foundational (Phases 1 & 2).
2. Implement User Story 1 (Phase 3).
3. **Validate MVP**: Dynamic variables can be created in an environment and execute during request dispatch.

### Incremental Delivery
1. Add User Story 2 (Editor Dialog with live test runner).
2. Add User Story 3 (Parameterized function invocations like `{{secondsFromNow(10)}}`).
3. Add User Story 4 (Request context access `context.request`).
4. Add User Story 5 (Export/Import persistence).
5. Add User Story 6 (Documentation page).
6. Run Phase 9 (Full QA verification and cleanup).
