# Implementation Plan: Typed Dynamic Variables & Variable Editor UI Modernization

**Branch**: `028-typed-dynamic-variables` | **Date**: 2026-11-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/028-typed-dynamic-variables/spec.md`

---

## Summary

This feature cleanly separates variable execution kind (`Regular`, `Secret`, `Dynamic`) from variable primitive data type (`STRING`, `NUMBER`, `BOOLEAN`, `OBJECT`), removes `DYNAMIC` from `VariableType`, introduces explicit selection dropdowns in the variable tables and dynamic variable editor, and upgrades the code editor's JSON linter to be template-aware—completely eliminating the false-positive `"Expected property name at offset 7"` syntax error when using unquoted numeric, boolean, or object variable tokens in JSON request bodies.

---

## Technical Context

**Language/Version**: Kotlin 2.1.21 (KMP jvm("desktop"))
**Primary Dependencies**: Compose Multiplatform 1.8.1, kotlinx.serialization 1.8.0, Ktor 3.1.2
**Storage**: OpenCollection YAML/JSON canonical files (Kaml / kotlinx.serialization)
**Testing**: kotlin.test, kotlinx.coroutines.test, Compose Desktop UI tests, DummyApiServer (Ktor) E2E tests
**Target Platform**: Desktop (macOS, Linux, Windows)
**Project Type**: Multi-module desktop application
**Performance Goals**: Zero latency regression on editor live diagnostics (<5ms per keystroke debounce)
**Constraints**: Zero false-positive diagnostics on valid template syntax; exact coordinate mapping for genuine syntax errors

---

## Constitution Check

*GATE: Passed before Phase 0 research. Re-evaluated after Phase 1 design.*

- [x] **I. Library-First & Multi-Module Isolation**: Models in `:core-model`, linter in `:editor-core`, UI components in `:ui-shared`, E2E tests in `:qa-tests`.
- [x] **II. Test-First & Verifiability**: Unit tests for serialization, editor diagnostics, UI state mutations, and server-side JSON deserialization.
- [x] **III. Simplicity & YAGNI**: Clean removal of `DYNAMIC` from `VariableType` without unnecessary compatibility shim layers (as Clapi is unreleased).
- [x] **IV. Documentation Integrity**: Dedicated tasks to update user-facing docs in `docs/features/dynamic-variables.md` and related files.

---

## Project Structure

### Documentation (this feature)

```text
specs/028-typed-dynamic-variables/
├── plan.md              # This file
├── research.md          # Technical research & architectural decisions
├── data-model.md        # Entities, enums, UI models, and OpenCollection schemas
├── quickstart.md        # Validation scenarios & test execution guide
└── contracts/           # Interface and serialization contracts
    ├── variable-data-model-contract.md
    ├── editor-template-validation-contract.md
    └── variable-editor-ui-contract.md
```

### Source Code Impact

```text
core-model/
├── src/commonMain/kotlin/.../opencollection/OpenCollectionEnvironment.kt   # VariableType & DynamicVariable.type
└── src/commonTest/kotlin/.../opencollection/DynamicVariableSerializationTest.kt

editor-core/
├── src/commonMain/kotlin/.../editor/core/Json5EditorSupport.kt            # Offset-preserving template masking & validation
├── src/commonMain/kotlin/.../editor/core/JsonMode.kt                      # Template-aware JSON validation
└── src/commonTest/kotlin/.../editor/core/Json5EditorSupportTest.kt        # Tests for unquoted variable templates

ui-shared/
├── src/commonMain/kotlin/.../state/EditorRowModels.kt                     # MutableKeyValue kind & type properties
├── src/commonMain/kotlin/.../dialogs/env/EnvVariableTable.kt              # Kind & Type dropdown selectors
├── src/commonMain/kotlin/.../dialogs/dynamicvar/DynamicVariableEditorDialog.kt # Return Type dropdown selector
└── src/commonTest/kotlin/.../                                             # UI state and rendering tests

qa-tests/
└── src/test/kotlin/.../DynamicVariablesE2ETest.kt                        # E2E test verifying wire JSON format

docs/
├── features/dynamic-variables.md                                          # Updated docs for Kind & Type selectors
└── environments/dynamic-variables.md                                      # Updated docs for variable templates
```

---

## Phase 0: Outline & Research

- [x] **Research Topic 1**: Clean separation of `VariableKind` and `VariableType` ([research.md](research.md#research-topic-1-clean-separation-of-variable-kind-and-variable-data-type)).
- [x] **Research Topic 2**: Table selection box controls replacing the cycle badge ([research.md](research.md#research-topic-2-variable-table-ui--selection-dropdown-controls)).
- [x] **Research Topic 3**: Offset-preserving template masking for JSON validation in the editor ([research.md](research.md#research-topic-3-template-aware-live-json-validation-in-code-editor)).
- [x] **Research Topic 4**: Execution-time type verification for dynamic scripts ([research.md](research.md#research-topic-4-execution-pipeline-type-enforcement)).

---

## Phase 1: Design & Contracts

- [x] **Data Model**: [data-model.md](data-model.md) created with class diagrams, properties, and OpenCollection JSON schemas.
- [x] **Contracts**:
  - [variable-data-model-contract.md](contracts/variable-data-model-contract.md)
  - [editor-template-validation-contract.md](contracts/editor-template-validation-contract.md)
  - [variable-editor-ui-contract.md](contracts/variable-editor-ui-contract.md)
- [x] **Quickstart Validation Guide**: [quickstart.md](quickstart.md) created with manual and automated verification instructions.
