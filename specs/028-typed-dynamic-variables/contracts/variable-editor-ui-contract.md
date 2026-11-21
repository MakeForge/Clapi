# Contract: Variable Editor UI & Selection Box Controls

**Branch**: `028-typed-dynamic-variables` | **Date**: 2026-11-16 | **Spec**: [spec.md](../spec.md)

---

## 1. Variable Table Row Layout (`EnvVariableTable.kt`)

Each row in the variable table contains:

| Column | Component | Behavior |
|---|---|---|
| **Enabled** | Checkbox | Toggles `kv.enabled` |
| **Key** | BasicTextField | Variable identifier |
| **Kind** | Dropdown Selector | Options: `Regular`, `Secret`, `Dynamic`. Mutates `kv.kind` without cycling. |
| **Type** | Dropdown Selector | Options: `String`, `Number`, `Boolean`, `Object`. Mutates `kv.type`. |
| **Value / Action** | Dynamic View | If `kind == DYNAMIC`: displays `"Edit Script"` button.<br>If `kind == SECRET`: displays masked password field.<br>If `kind == REGULAR`: displays standard text input. |
| **Description** | BasicTextField | Tooltip/documentation string |
| **Delete** | IconButton | Removes row |

---

## 2. Dynamic Variable Editor Dialog Layout (`DynamicVariableEditorDialog.kt`)

Header section includes:
1. **Variable Name**: Editable identifier field.
2. **Return Type Selector**:
   - Label: `Return Type`
   - Dropdown with: `String`, `Number`, `Boolean`, `Object`.
   - Mutates `workingType: VariableType`.
3. **Template Preview Banner**:
   - If `workingType == STRING`:
     - Displays `Preview: { "key": "{{name}}" }`
   - If `workingType in [NUMBER, BOOLEAN, OBJECT]`:
     - Displays `Preview: { "key": {{name}} }`
4. **Script Editor**: JavaScript code editor with parameter bindings.
5. **Test Console**:
   - Displays evaluation result.
   - If evaluated type does not match `workingType` (e.g. script returned string `"abc"` for `Number`), shows warning:
     `⚠️ Script returned string, but declared type is Number`
