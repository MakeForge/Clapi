# Contract: Template-Aware Code Editor JSON Validation

**Branch**: `028-typed-dynamic-variables` | **Date**: 2026-11-16 | **Spec**: [spec.md](../spec.md)

---

## 1. Syntax Context Detection

The validator distinguishes between variable tokens located in:
1. **Quoted String Literals**:
   - e.g. `{ "name": "{{userName}}" }`
   - Treated as standard JSON string values. Validated by standard JSON rules.
2. **Unquoted JSON Value Positions**:
   - e.g. `{ "count": {{orderCount}} }`
   - The token occurs immediately following a property colon `:`, array opening bracket `[`, or comma `,` without surrounding quotes.

---

## 2. Offset-Preserving Masking Algorithm

For validation in `Json5EditorSupport` and `JsonMode`:
1. Find all `VARIABLE_REGEX` matches in the text.
2. Filter for matches that do not sit inside quotes.
3. For each unquoted match `{{ ... }}` spanning `[start, end)`:
   - Replace with `"0".padEnd(end - start, ' ')`.
   - Length of replacement == Length of original token.
4. Pass the masked text to the parser (`Json5.parseToJsonElement` / `Json.parseToJsonElement`).
5. Any syntax error reported by the parser has accurate `(line, col)` coordinates matching the user's document.

---

## 3. Semantic Diagnostics for Unquoted Variables

When variable definitions are available in the editor context:
- If an unquoted token resolves to a variable with `type == VariableType.STRING`:
  - Emit an `InlineEditorError`:
    ```text
    Variable '<name>' is typed as String; unquoted syntax generates invalid JSON. Wrap in quotes: "{{<rawToken>}}"
    ```
- If an unquoted token resolves to `NUMBER`, `BOOLEAN`, `OBJECT`, or is a built-in numeric dynamic variable (`$timestamp`, `$randomInt`):
  - Zero diagnostics emitted.
- If an unquoted token is not found in the environment:
  - Masked as valid dummy literal for JSON parsing, but highlighted as unresolved via `VariableHighlightColor` / `VariableUnresolvedColor`.
