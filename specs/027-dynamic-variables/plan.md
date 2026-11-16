# Implementation Plan: User-Defined Scriptable Dynamic Variables

**Branch**: `027-dynamic-variables` | **Date**: 2026-11-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/027-dynamic-variables/spec.md`

## Summary

This feature empowers users to define executable, script-backed dynamic variables that compute fresh values on every request execution (e.g. `{{secondsFromNow(10)}}`, custom cryptographic nonces, or HMAC signatures):
1. **Core Data Model (`core-model`)**: Introduce `DynamicVariable` as a subtype of `VariableEntry` with `VariableType.DYNAMIC`, script source, and parameter definitions, serialized via polymorphic `VariableEntrySerializer` for JSON and YAML.
2. **Scripting Engine Integration (`core-scripting`)**: Provide `DynamicVariableEngine` wrapping Javet V8 to execute dynamic variable scripts in a sandboxed IIFE with parameter bindings, read-only `context.request` inspection, execution timeouts (2,000ms), and console capture.
3. **Template Token Grammar & Resolution (`core-network`)**: Extend `VariableResolver` to tokenize function-style variable expressions `{{variableName(arg1, arg2)}}`, parse primitive arguments, and resolve dynamic variables across hierarchical layers (Request > Environment > Collection > System Built-ins).
4. **Interactive Editor Subdialog (`ui-shared` & `ui-desktop`)**: Add `Dynamic` variable type option to environment and collection variable tables. When selected, display an `[Edit Script]` trigger that launches a two-pane `DynamicVariableEditorDialog` (left: code editor; right: interactive test runner with parameter inputs, mock request context, run trigger, logs, and diagnostic error output).
5. **Persistence & OpenCollection Interoperability (`core-storage`)**: Guarantee roundtrip import and export of dynamic variables in OpenCollection JSON and YAML formats without data loss.
6. **Documentation & QA Suites (`docs` & `qa-tests`)**: Create a dedicated VitePress documentation subpage for built-in and user-defined dynamic variables, and deliver full QA end-to-end and unit test suites.

## Technical Context

**Language/Version**: Kotlin 2.1 (Multiplatform, JVM 21 / 17 target)

**Primary Dependencies**:
- Javet V8 (`com.caoccao.javet:javet`) for sandboxed JavaScript execution
- Compose Multiplatform 1.8.x (Desktop)
- Material 3 (`androidx.compose.material3`)
- `kotlinx.serialization` (`kotlinx-serialization-json`)
- `kaml` (`com.charleskorn.kaml:kaml`) for OpenCollection YAML
- `kotlinx.coroutines`

**Storage**:
- Canonical OpenCollection format (`core-storage`)
- Workspace JSON persistence (`WorkspaceRepository`)

**Testing**:
- `kotlin.test`
- Desktop Compose UI testing (`createComposeRule` in `ui-desktop`)
- End-to-end multiplatform tests in `qa-tests`

**Target Platform**: Desktop (Linux, macOS, Windows)

**Project Type**: Desktop Multiplatform Application

**Performance Goals**:
- Dynamic variable script resolution under 10ms per token occurrence.
- Interactive test runner response under 20ms for standard scripts.
- Subdialog launch latency under 50ms.
- Enforced sandbox execution timeout of 2,000ms for runaway loops.

**Constraints**:
- Strict OpenCollection backward compatibility: existing collections without dynamic variables parse unchanged.
- Read-only request context during evaluation to prevent side-effects on the executing request.
- Graceful failure: syntax/runtime errors in dynamic scripts abort request dispatch with clean diagnostics and never crash the application.

**Scale/Scope**:
- Support dozens of dynamic variables per environment and collection with multiple invocations per request.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

No constitutional violations exist:
1. **Modularity & Separation**: Core models (`core-model`), scripting engine (`core-scripting`), network resolution (`core-network`), and UI components (`ui-shared`, `ui-desktop`) maintain strict boundary separation.
2. **Data Safety & Sandbox Security**: Dynamic scripts execute in isolated V8 runtimes with strict time limits and no access to host filesystem, network, or process APIs.
3. **Test-First Compliance**: Complete test coverage across unit tests (serialization, token parsing, script execution), Compose UI desktop tests, and end-to-end QA tests.
4. **Resilience & Robustness**: Malformed syntax, uncaught exceptions, and infinite loops are trapped gracefully, returning clear diagnostic messages without leaking corrupt state or hanging the UI thread.

## Project Structure

### Documentation (this feature)

```text
specs/027-dynamic-variables/
├── spec.md                     # Feature specification
├── plan.md                     # Implementation plan
├── research.md                 # Technical research & decisions (Phase 0)
├── data-model.md               # Data entities & state transitions (Phase 1)
├── quickstart.md               # Runnable validation guide (Phase 1)
├── contracts/                  # Interface contracts (Phase 1)
│   ├── dynamic-variable-execution-contract.md
│   ├── dynamic-variable-serialization-contract.md
│   └── dynamic-variable-template-syntax-contract.md
└── checklists/
    └── requirements.md         # Specification quality checklist
```

### Source Code (repository root)

```text
core-model/
└── src/commonMain/kotlin/io/github/makeforge/clapi/core/model/opencollection/
    └── OpenCollectionEnvironment.kt             # Add DynamicVariable, DynamicVariableParameter, VariableType.DYNAMIC

core-scripting/
└── src/commonMain/kotlin/io/github/makeforge/clapi/core/scripting/
    ├── DynamicVariableEngine.kt                 # Script sandboxing, parameter binding & context injection
    └── ScriptingContracts.kt                    # DynamicScriptContext, RequestContext, DynamicScriptExecutionResult

core-network/
└── src/commonMain/kotlin/io/github/makeforge/clapi/core/network/
    ├── VariableResolver.kt                      # Function-call token grammar {{fn(args)}} & layer resolution
    └── execution/
        └── RequestExecutionService.kt           # Request context provisioning & dynamic variable execution

ui-shared/
└── src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/
    ├── state/
    │   └── EditorRowModels.kt                   # Expand MutableKeyValue with isDynamic, dynamicScript, dynamicParameters
    ├── persistence/
    │   └── OpenCollectionModelMapping.kt        # Map DynamicVariable to/from MutableKeyValue
    └── components/
        └── dialogs/
            ├── env/
            │   ├── EnvVariableTable.kt          # 3-way type toggle (Normal / Secret / Dynamic) & [Edit Script] pill
            │   └── EnvironmentEditDialog.kt     # Dynamic variable support in quick-add row and tables
            └── dynamicvar/
                ├── DynamicVariableEditorDialog.kt # Two-pane subdialog: Script editor (left) + Test runner (right)
                └── DynamicVariableTestPanel.kt   # Parameter inputs, mock context, test trigger & log results

ui-desktop/
└── src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/
    └── DynamicVariableEditorDialogTest.kt       # Compose desktop UI test for editor subdialog

docs/
├── features/
│   └── dynamic-variables.md                     # Comprehensive documentation (built-ins & user-defined scripting)
└── .vitepress/
    └── config.mts                               # Navigation and sidebar registration

qa-tests/
└── src/test/kotlin/io/github/makeforge/clapi/qa/
    └── DynamicVariablesE2ETest.kt               # End-to-end integration and export/import test
```
