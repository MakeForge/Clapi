# Contract: Dynamic Variable Template Syntax & Token Grammar

**Branch**: `027-dynamic-variables` | **Date**: 2026-11-15 | **Spec**: [spec.md](../spec.md)

## Purpose
Specifies the lexical grammar, argument tokenization rules, and evaluation semantics for referencing user-defined dynamic variables in requests.

---

## 1. Lexical Grammar

```ebnf
VariableToken       ::= "{{" Whitespace? VariableIdentifier ( "(" Whitespace? ArgumentList? Whitespace? ")" )? Whitespace? "}}"
VariableIdentifier  ::= [a-zA-Z0-9_\-$]+
ArgumentList        ::= Argument ( Whitespace? "," Whitespace? Argument )*
Argument            ::= StringLiteral | NumberLiteral | BooleanLiteral | NullLiteral
StringLiteral       ::= ('"' [^"\\]* '"') | ("'" [^'\\]* "'")
NumberLiteral       ::= "-"? [0-9]+ ( "." [0-9]+ )?
BooleanLiteral      ::= "true" | "false"
NullLiteral         ::= "null"
Whitespace          ::= [ \t\r\n]+
```

---

## 2. Invocation Examples & Parsing

| Expression | Extracted Name | Arguments Passed to Script |
|---|---|---|
| `{{secondsFromNow}}` | `secondsFromNow` | `[]` |
| `{{secondsFromNow(10)}}` | `secondsFromNow` | `[10]` (Number) |
| `{{secondsFromNow(-5)}}` | `secondsFromNow` | `[-5]` (Number) |
| `{{formatPrefix("ORDER-", 42)}}` | `formatPrefix` | `["ORDER-", 42]` (String, Number) |
| `{{generateId('v2', true)}}` | `generateId` | `["v2", true]` (String, Boolean) |
| `{{$randomInt(1, 100)}}` | `$randomInt` | Handled by built-in handler (`[1, 100]`) |

---

## 3. Parameter Binding Semantics

1. **Positional Mapping**: When a script declares parameters `[paramA, paramB]`:
   - `{{fn("hello", 123)}}`: `paramA = "hello"`, `paramB = 123`.
2. **Missing Arguments**:
   - `{{fn("hello")}}`: `paramA = "hello"`, `paramB = defaultValue` (if configured in parameter definition) or `undefined`.
3. **Surplus Arguments**:
   - Overage arguments are accessible via the `args` array in script.
4. **Escaping**: Quotes within string arguments can be escaped using standard backslash (`\"` or `\'`).
