# Technical Research & Decisions: User-Defined Dynamic Variables

**Branch**: `027-dynamic-variables` | **Date**: 2026-11-15 | **Spec**: [spec.md](spec.md)

## Summary of Decisions

This document details the architectural investigations, technical decisions, and trade-offs for implementing scriptable, user-defined dynamic variables in Clapi.

---

### Research Topic 1: OpenCollection Data Model & Polymorphic Serialization

#### Decision
Introduce `DynamicVariable` as a concrete subtype of `VariableEntry` in `core-model`:
```kotlin
@Serializable
data class DynamicVariable(
    override val name: String,
    val script: String = "",
    val parameters: List<DynamicVariableParameter> = emptyList(),
    override val description: Description? = null,
    override val disabled: Boolean? = null,
) : VariableEntry
```
Extend `VariableType` with `DYNAMIC("dynamic")`.
Update `VariableEntrySerializer` (a `JsonContentPolymorphicSerializer<VariableEntry>`) to select `DynamicVariable.serializer()` when `element.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "dynamic"` or when `element.jsonObject.containsKey("script")`.
Ensure YAML serialization with `kaml` encodes and decodes `DynamicVariable` without requiring proprietary or breaking envelope tags.

#### Rationale
- Polymorphic deserialization in `core-model` already uses `VariableEntrySerializer` for `RegularVariable` and `SecretVariable`.
- Discriminating on either `"type": "dynamic"` or the presence of `"script"` preserves forward and backward compatibility with external OpenCollection documents and avoids schema regressions.
- Keeping `parameters` as a typed list of `DynamicVariableParameter` allows explicit signatures and documentation in tooltips.

#### Alternatives Considered
- *Store scripts inside `RegularVariable.value` as a special string prefix (e.g. `eval:function() ...`)*: Rejected. Pollutes the static value model, fails schema validation in strict parsers, and makes UI rendering fragile.
- *Separate collection/environment extension table for dynamic scripts*: Rejected. Separating dynamic variables from the environment's `variables` array creates synchronization issues and breaks hierarchical variable scoping.

---

### Research Topic 2: Template Syntax Grammar & Argument Parsing

#### Decision
Enhance the template variable token pattern in `VariableResolver` to match both standard tokens and function-call expressions:
```text
\{\{\s*([a-zA-Z0-9_\-$]+)(?:\((.*?)\))?\s*\}\}
```
When parentheses are present, an argument tokenizer splits arguments separated by commas while respecting quoted strings (single and double quotes), numeric literals (integers and floats), and boolean literals (`true`/`false`).

Examples:
- Standard: `{{secondsFromNow}}` -> Variable `secondsFromNow`, arguments: `[]`
- Parameterized: `{{secondsFromNow(10)}}` -> Variable `secondsFromNow`, arguments: `[10]`
- Multiple args: `{{hashHmac("sha256", "payload", true)}}` -> Variable `hashHmac`, arguments: `["sha256", "payload", true]`

#### Rationale
- Retains 100% backward compatibility with standard `{{variable}}` and built-in dynamic variables like `{{$timestamp}}` and `{{$randomInt(1, 10)}}`.
- Simple, familiar syntax for API engineers similar to template languages (Handlebars, Jinja) and Postman dynamic functions.
- Deterministic parsing without heavy parser generators.

#### Alternatives Considered
- *Full ECMAScript parser in template interpolation*: Rejected. Excessive performance overhead and complexity for variable resolution in URLs and headers.
- *Only zero-argument dynamic variables*: Rejected. User explicitly requested parameterized variables (e.g., `{{secondsFromNow(10)}}`).

---

### Research Topic 3: Script Execution Environment, Sandboxing & Request Context

#### Decision
Leverage `ClapiScriptEngine` and the underlying Javet V8 runtime to execute dynamic scripts.
Each dynamic variable invocation wraps the user script inside an isolated JavaScript function:
```javascript
(function(context, ...args) {
    // User script body
    {{USER_SCRIPT}}
})(requestContext, ...argumentList);
```
During request dispatch:
- `context.request` is populated with read-only properties:
  - `url`: String (the unresolved or currently resolved base URL)
  - `method`: String (HTTP verb or RPC type, e.g., "GET", "POST")
  - `headers`: Object (map of resolved header key-values)
  - `name`: String (request name)
- In the interactive test runner dialog, a default mock `context.request` is provided, editable by the user.
- Sandboxing enforces a strict 2,000ms timeout via `V8Guard`.
- Return values: If the script returns an object/array, it is converted via `JSON.stringify()`; primitives are stringified; `null`/`undefined` evaluate to an empty string.

#### Rationale
- Reuses existing Javet V8 infrastructure already proven in `ClapiScriptEngine` for pre-request and test scripts.
- Guarantees fast execution (~1-2ms on V8) and prevents hanging threads.
- Directly satisfies User Clarification Option A.

#### Alternatives Considered
- *Custom mini-interpreter or expression evaluator (e.g., MVEL / exp4j)*: Rejected. Restricts users from using full JavaScript logic, standard date functions (`Date.now()`, `toISOString()`), and math libraries.
- *Rhino / Nashorn*: Rejected. Clapi already standardizes on Javet V8 for desktop JVM scripting.

---

### Research Topic 4: UI Architecture in Variable Tables & Dynamic Script Dialog

#### Decision
1. **Variable Tables (`EnvVariableTable.kt`, `CollectionEditor.kt`, Quick-Add Rows)**:
   - Expand the variable type selector from a binary toggle (`Normal` / `Secret`) into a 3-way selector: `Normal`, `Secret`, `Dynamic`.
   - When a variable is set to `Dynamic`:
     - The static value text field is replaced by a compact pill button: `[Edit Script]` displaying a script icon and a status hint (e.g. `fn(seconds)` if parameterized, or `script configured`).
     - Clicking `[Edit Script]` opens `DynamicVariableEditorDialog`.
2. **`DynamicVariableEditorDialog` Subdialog Component**:
   - **Header**: Variable Name input, description input, and parameter chips.
   - **Parameter Bar**: Add, edit, and delete declared parameters (e.g., `seconds` with type hint).
   - **Left Pane (Script Editor)**: Monospace code editor (`CodeEditor.kt`) with syntax highlighting, line numbers, and keyboard shortcuts.
   - **Right Pane (Interactive Test Runner)**:
     - Argument inputs matching declared parameters.
     - Mock Request Context accordion (Mock URL, Method, Headers).
     - "Run Test" action button (with shortcut Ctrl+Enter / Cmd+Enter).
     - Execution Results panel: Result status badge (`Success` / `Error`), Return value display (with copy button), Duration badge (e.g. `2 ms`), Console logs output, and Stack trace/Error banner on failure.
   - **Footer**: Save and Cancel buttons.

#### Rationale
- Clean desktop Compose ergonomic layout consistent with existing Clapi dialogs (such as `EnvironmentEditDialog` and `OpenCollectionSettingsDialog`).
- Instant visual feedback during script authoring eliminates trial-and-error debugging during live API calls.

---

### Research Topic 5: Request Resolution Flow & Scoping Precedence

#### Decision
Variable resolution precedence order in `VariableResolver` & `RequestExecutionService`:
1. Request-level variables (highest precedence)
2. Active Environment variables (including overridden inherited variables)
3. Collection-level variables
4. User-defined Dynamic Variables (Environment / Collection scoped)
5. Built-in dynamic system variables (`$timestamp`, `$isoTimestamp`, `$guid`, `$uuid`, `$randomInt`) (lowest precedence)

Dynamic variables defined in the active environment take precedence over dynamic variables with the same name defined at collection level.
Parameterized calls execute freshly during each variable replacement pass of request dispatch.

#### Rationale
- Preserves Clapi's established hierarchical variable shadowing rules.
- Prevents user-defined variables from being accidentally shadowed by system built-ins while allowing explicit overrides.
