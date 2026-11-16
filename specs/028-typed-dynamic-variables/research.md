# Technical Research & Decisions: Typed Dynamic Variables & Variable Editor UI Modernization

**Branch**: `028-typed-dynamic-variables` | **Date**: 2026-11-16 | **Spec**: [spec.md](spec.md)

---

## Technical Decisions

### Research Topic 1: Clean Separation of Variable Kind and Variable Data Type

#### Decision
1. Retain `VariableType` in `core-model` purely as a JSON primitive data type enum:
   ```kotlin
   @Serializable
   enum class VariableType(val value: String) {
       @SerialName("string")
       STRING("string"),
       @SerialName("number")
       NUMBER("number"),
       @SerialName("boolean")
       BOOLEAN("boolean"),
       @SerialName("null")
       NULL("null"),
       @SerialName("object")
       OBJECT("object");
   }
   ```
   Remove `DYNAMIC("dynamic")` from `VariableType`.
2. Define a clean domain enum `VariableKind` in `core-model` and UI models:
   ```kotlin
   enum class VariableKind(val value: String) {
       REGULAR("regular"),
       SECRET("secret"),
       DYNAMIC("dynamic");
   }
   ```
3. Every `VariableEntry` (`RegularVariable`, `SecretVariable`, `DynamicVariable`) owns a `val type: VariableType = VariableType.STRING`.
   - For `DynamicVariable`, `type` represents the expected return type of the generator script.
   - For `RegularVariable` and `SecretVariable`, `type` represents the data type of the value.
4. Polymorphic deserialization in `VariableEntrySerializer`:
   - If `element.jsonObject["secret"]?.jsonPrimitive?.booleanOrNull == true` $\rightarrow$ `SecretVariable`
   - If `element.jsonObject.containsKey("script")` or `element.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "dynamic"` $\rightarrow$ `DynamicVariable`
   - Otherwise $\rightarrow$ `RegularVariable`
   - If deserializing a legacy entry where `type == "dynamic"`, `DynamicVariable.type` defaults to `VariableType.STRING`.

#### Rationale
- Prevents conflating execution behavior (dynamic script vs secret vs static text) with data formatting.
- Eliminates the need for an extra `returnType` field on `DynamicVariable`.
- Maintains simple OpenCollection serialization format where `type` is always the standard data type.

#### Alternatives Considered
- *Add a separate `returnType` field on `DynamicVariable` while leaving `VariableType.DYNAMIC`*: Rejected. Clapi has no legacy external versions; keeping `DYNAMIC` inside `VariableType` perpetuates technical debt and conceptual confusion.

---

### Research Topic 2: Variable Table UI & Selection Dropdown Controls

#### Decision
Replace the cycling badge in [EnvVariableTable.kt](file:///home/user/IdeaProjects/Clapi/ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/dialogs/env/EnvVariableTable.kt) with two distinct selection controls:
1. **Variable Kind Dropdown**:
   - Options: `Regular` (or `Normal`), `Secret`, `Dynamic`.
   - Selecting `Dynamic` switches the value cell into an "Edit Script" action button and preserves any existing script or parameters.
   - Selecting `Secret` switches to masked password text field.
   - Selecting `Regular` displays normal editable text field.
2. **Variable Type Dropdown**:
   - Options: `String`, `Number`, `Boolean`, `Object`.
   - Accessible for all variable kinds.
3. **Dynamic Variable Editor Dialog**:
   - Add a prominent `Return Type` dropdown in the dialog header (`String`, `Number`, `Boolean`, `Object`).
   - When the user executes a test run in the dialog, compare the evaluated JavaScript type with the declared `returnType`. If mismatched (e.g. declared `Number` but returned `"foo"`), display a warning badge in the test results.

#### Rationale
- Explicit dropdowns prevent accidental clicks that mutate variable states when users attempt to inspect variables.
- Direct alignment between table controls and the subdialog controls ensures consistent mental models.

---

### Research Topic 3: Template-Aware Live JSON Validation in Code Editor

#### Decision
Upgrade the code editor's JSON validation in [Json5EditorSupport.kt](file:///home/user/IdeaProjects/Clapi/editor-core/src/commonMain/kotlin/io/github/makeforge/clapi/editor/core/Json5EditorSupport.kt) and [JsonMode.kt](file:///home/user/IdeaProjects/Clapi/editor-core/src/commonMain/kotlin/io/github/makeforge/clapi/editor/core/JsonMode.kt) to support unquoted template variables in value positions:
1. **Offset-Preserving Template Masking**:
   - Parse or scan for unquoted `\{\{\s*([^{}\r\n]+?)\s*\}\}` occurrences in the JSON body text.
   - When an unquoted variable token is in a JSON value position:
     - Replace the token with a valid dummy JSON literal of identical length (e.g., `0` followed by spaces: `"0".padEnd(tokenLength, ' ')`).
     - Because the masked string has the exact same byte length and line-break structure as the original text, any line numbers and column offsets of actual JSON syntax errors elsewhere in the document remain 100% exact.
2. **Type-Specific Diagnostics**:
   - When the variable's declared type is `VariableType.STRING`:
     - Emit an informative `InlineEditorError` at the token's line/col:
       `"Variable '$name' is typed as String; unquoted syntax will produce invalid JSON. Wrap in quotes: \"{{$rawToken}}\""`
   - When the variable is `NUMBER`, `BOOLEAN`, `OBJECT`, or built-in (`$timestamp`, `$randomInt`):
     - Accepted with zero syntax errors.
   - When the variable is unknown/unresolved:
     - Accepted without JSON parser crash, but highlighted using existing unresolved variable color.

#### Rationale
- Zero offset drift: masking tokens with whitespace-padded literals avoids complex AST coordinate transformations.
- Complete elimination of the false-positive `"Expected property name at offset 7"` bug.
- Helpful guidance when developers forget quotes on string variables.

---

### Research Topic 4: Execution Pipeline Type Enforcement

#### Decision
1. In `UserScriptDynamicVariableProvider`:
   - After evaluating a user dynamic variable script, validate that the returned string conforms to `dynamicVar.type`:
     - `NUMBER`: `retVal.toDoubleOrNull() != null`
     - `BOOLEAN`: `retVal == "true" || retVal == "false"`
     - `OBJECT`: `(retVal.startsWith("{") && retVal.endsWith("}")) || (retVal.startsWith("[") && retVal.endsWith("]"))`
     - `STRING`: Any string accepted.
   - If invalid, halt execution and throw `DynamicVariableResolutionException("Dynamic variable '${dynamicVar.name}' declared type Number but returned non-numeric value: '$retVal'")`.

#### Rationale
- Catches scripting logic bugs immediately before requests are dispatched over the network.
- Prevents malformed payloads from being sent to backend services.
